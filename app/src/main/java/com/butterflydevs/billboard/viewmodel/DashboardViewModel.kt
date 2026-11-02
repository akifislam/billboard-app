package com.butterflydevs.billboard.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.butterflydevs.billboard.data.DescoMeterData
import com.butterflydevs.billboard.data.DescoMonthlyConsumptionRow
import com.butterflydevs.billboard.data.DescoScraper
import com.butterflydevs.billboard.data.MeterInfo
import com.butterflydevs.billboard.data.MeterProvider
import com.butterflydevs.billboard.data.MeterSnapshotCache
import com.butterflydevs.billboard.data.MeterStore
import com.butterflydevs.billboard.data.MeterType
import com.butterflydevs.billboard.data.MonthlyUsageCache
import com.butterflydevs.billboard.data.MONTHLY_USAGE_TTL_MS
import com.butterflydevs.billboard.data.MonthlyUsageRow
import com.butterflydevs.billboard.data.NescoPostpaidMeterData
import com.butterflydevs.billboard.data.NescoPostpaidScraper
import com.butterflydevs.billboard.data.NescoScraper
import com.butterflydevs.billboard.data.PgclMeterData
import com.butterflydevs.billboard.data.PgclScraper
import com.butterflydevs.billboard.data.remote.AdImageLoader
import com.butterflydevs.billboard.data.remote.RemoteConfig
import com.butterflydevs.billboard.data.remote.RemoteConfigRepository
import com.butterflydevs.billboard.data.RechargeRecord
import com.butterflydevs.billboard.notifications.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AUTO_REFRESH_MS = 5 * 60 * 1000L

// Every foreground return calls refresh(); without a floor the app would re-download config.json
// on each app switch. The toolbar button bypasses this so a manual refresh always fetches.
private const val REMOTE_CONFIG_MIN_INTERVAL_MS = 5 * 60 * 1000L
private const val AVG_DAYS_PER_MONTH = 30.44

data class MeterCardState(
    val meter: String,
    val nickname: String,
    val provider: MeterProvider = MeterProvider.NESCO,
    val billingType: MeterType = MeterType.PREPAID,
    val isLoading: Boolean = true,
    // NESCO-specific full detail (also used for the summary shown on the card)
    val name: String? = null,
    val fatherOrHusbandName: String? = null,
    val address: String? = null,
    val mobile: String? = null,
    val office: String? = null,
    val feederName: String? = null,
    val consumerNo: String? = null,
    val meterSerialNo: String? = null,
    val approvedLoadKw: String? = null,
    val tariff: String? = null,
    val meterType: String? = null,
    val meterStatus: String? = null,
    val installDate: String? = null,
    val minRechargeAmount: Double? = null,
    val lastRecharge: RechargeRecord? = null,
    val monthlyUsage: List<MonthlyUsageRow> = emptyList(),
    // Shared between NESCO and DESCO (both prepaid electricity, balance-based)
    val balance: Double? = null,
    val balanceAsOf: String? = null,
    val lastRechargeDate: String? = null,
    val lastRechargeAmount: Double? = null,
    val predictionDaysRemaining: Double? = null,
    // DESCO / PGCL full detail
    val descoData: DescoMeterData? = null,
    val pgclData: PgclMeterData? = null,
    // NESCO postpaid full detail
    val nescoPostpaidData: NescoPostpaidMeterData? = null,
    val error: String? = null
)

/** True once a card holds anything worth showing, from a live fetch or a restored snapshot. */
fun MeterCardState.hasData(): Boolean =
    balance != null || name != null || descoData != null || pgclData != null || nescoPostpaidData != null

data class DashboardUiState(
    val meters: List<MeterCardState> = emptyList(),
    val isInitialLoading: Boolean = true,
    val statusText: String = ""
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val meterStore = MeterStore(application)
    private val monthlyUsageCache = MonthlyUsageCache(application)
    private val snapshotCache = MeterSnapshotCache(application)
    private val remoteConfigRepository = RemoteConfigRepository(application)

    /** Shared by the banner so a single OkHttp cache backs every image load. */
    val adImageLoader = AdImageLoader(application)

    var uiState by mutableStateOf(DashboardUiState())
        private set

    /**
     * Marquee/advertisement settings from GitHub Pages. Null until something is available, which
     * is what keeps both features invisible on a fresh offline install rather than half-drawn.
     */
    var remoteConfig by mutableStateOf<RemoteConfig?>(null)
        private set

    private var autoRefreshJob: Job? = null
    private var remoteConfigJob: Job? = null
    private var lastRemoteConfigFetchAt = 0L

    init {
        loadRemoteConfig(force = true)
        viewModelScope.launch {
            val saved = meterStore.getAll()
            // Seed each card from its last saved snapshot so the dashboard opens on real numbers
            // instead of empty placeholders — and still has something to show with no network.
            val snapshots = snapshotCache.getAll()
            uiState = uiState.copy(
                meters = saved.map { entry ->
                    val cached = snapshots[entry.meter]
                    cached?.copy(
                        nickname = entry.nickname,
                        provider = entry.resolvedProvider,
                        billingType = entry.resolvedMeterType,
                        isLoading = true,
                        error = null
                    ) ?: MeterCardState(
                        entry.meter,
                        entry.nickname,
                        provider = entry.resolvedProvider,
                        billingType = entry.resolvedMeterType,
                        isLoading = true
                    )
                },
                isInitialLoading = false
            )
            saved
                .filter { it.resolvedProvider == MeterProvider.NESCO && it.resolvedMeterType == MeterType.PREPAID }
                .forEach { entry ->
                    launch {
                        val cached = monthlyUsageCache.get(entry.meter)
                        if (cached != null) {
                            updateMeter(entry.meter) {
                                it.copy(
                                    monthlyUsage = cached.rows,
                                    predictionDaysRemaining = estimateDaysRemaining(it.balance, cached.rows)
                                )
                            }
                        }
                    }
                }
            startAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshAll()
                delay(AUTO_REFRESH_MS)
            }
        }
    }

    /**
     * [forceRemoteConfig] is set by the toolbar's refresh button so pressing it always picks up a
     * just-published config.json; the automatic lifecycle-driven calls leave it false and are rate
     * limited instead. Meter refreshing is unchanged either way.
     */
    fun refresh(forceRemoteConfig: Boolean = false) {
        startAutoRefresh()
        loadRemoteConfig(force = forceRemoteConfig)
    }

    /**
     * Never blocks the UI: the dashboard renders immediately and the marquee/banner appear later if
     * and when a config arrives. A failed fetch falls back to the last good cached copy, and a fresh
     * install with no cache simply shows neither feature.
     */
    private fun loadRemoteConfig(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRemoteConfigFetchAt < REMOTE_CONFIG_MIN_INTERVAL_MS) return
        if (remoteConfigJob?.isActive == true) return

        remoteConfigJob = viewModelScope.launch {
            if (remoteConfig == null) {
                // Show the last known good config straight away rather than waiting on the network.
                remoteConfigRepository.cached()?.let { remoteConfig = it }
            }
            val fetched = remoteConfigRepository.fetch()
            if (fetched != null) {
                remoteConfig = fetched
                lastRemoteConfigFetchAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun refreshAll() {
        val current = uiState.meters
        if (current.isEmpty()) return
        uiState = uiState.copy(meters = current.map { it.copy(isLoading = true, error = null) })

        kotlinx.coroutines.coroutineScope {
            current.forEach { entry ->
                launch { refreshOne(entry.meter, entry.nickname, entry.provider, entry.billingType) }
            }
        }
        uiState = uiState.copy(statusText = "Updated: " + timeNow())
    }

    private suspend fun refreshOne(meter: String, nickname: String, provider: MeterProvider, billingType: MeterType) {
        when {
            provider == MeterProvider.NESCO && billingType == MeterType.POSTPAID -> {
                try {
                    val data = NescoPostpaidScraper.scrapeMeter(meter)
                    updateMeter(meter) { it.copy(isLoading = false, nescoPostpaidData = data, error = null) }
                } catch (e: Exception) {
                    onRefreshFailed(meter, e.message ?: "Failed to fetch data")
                }
            }
            provider == MeterProvider.NESCO -> {
                try {
                    val data = NescoScraper.scrapeMeter(meter)
                    updateMeter(meter) {
                        it.copy(
                            isLoading = false,
                            name = data.name,
                            fatherOrHusbandName = data.fatherOrHusbandName,
                            address = data.address,
                            mobile = data.mobile,
                            office = data.office,
                            feederName = data.feederName,
                            consumerNo = data.consumerNo,
                            meterSerialNo = data.meterNo,
                            approvedLoadKw = data.approvedLoadKw,
                            tariff = data.tariff,
                            meterType = data.meterType,
                            meterStatus = data.meterStatus,
                            installDate = data.installDate,
                            minRechargeAmount = data.minRechargeAmount,
                            balance = data.balance,
                            balanceAsOf = data.balanceAsOf,
                            lastRecharge = data.lastRecharge,
                            lastRechargeDate = data.lastRecharge?.date,
                            lastRechargeAmount = data.lastRecharge?.rechargeAmount,
                            predictionDaysRemaining = estimateDaysRemaining(data.balance, it.monthlyUsage),
                            error = null
                        )
                    }
                    NotificationHelper.checkAndNotify(getApplication(), meter, nickname, data.balance)
                } catch (e: Exception) {
                    onRefreshFailed(meter, e.message ?: "Failed to fetch data")
                }
                refreshMonthlyUsageIfStale(meter)
            }
            provider == MeterProvider.DESCO -> {
                try {
                    val data = DescoScraper.scrapeMeter(meter)
                    updateMeter(meter) {
                        it.copy(
                            isLoading = false,
                            balance = data.balance,
                            balanceAsOf = data.readingTime,
                            lastRechargeDate = data.lastRecharge?.rechargeDate,
                            lastRechargeAmount = data.lastRecharge?.totalAmount,
                            predictionDaysRemaining = estimateDescoDaysRemaining(data.balance, data.monthlyConsumption),
                            descoData = data,
                            error = null
                        )
                    }
                    NotificationHelper.checkAndNotify(getApplication(), meter, nickname, data.balance)
                } catch (e: Exception) {
                    onRefreshFailed(meter, e.message ?: "Failed to fetch data")
                }
            }
            provider == MeterProvider.PGCL -> {
                try {
                    val data = PgclScraper.scrapeMeter(meter)
                    updateMeter(meter) { it.copy(isLoading = false, pgclData = data, error = null) }
                } catch (e: Exception) {
                    onRefreshFailed(meter, e.message ?: "Failed to fetch data")
                }
            }
        }
    }

    private suspend fun refreshMonthlyUsageIfStale(meterNo: String, force: Boolean = false) {
        val cached = monthlyUsageCache.get(meterNo)
        val isStale = cached == null || System.currentTimeMillis() - cached.fetchedAt > MONTHLY_USAGE_TTL_MS
        if (!force && !isStale) return
        try {
            val rows = NescoScraper.scrapeMonthlyUsage(meterNo)
            monthlyUsageCache.put(meterNo, rows, System.currentTimeMillis())
            updateMeter(meterNo) {
                it.copy(monthlyUsage = rows, predictionDaysRemaining = estimateDaysRemaining(it.balance, rows))
            }
        } catch (e: Exception) {
            // Leave the previously cached monthly usage (if any) in place; retried on the next cycle.
        }
    }

    private fun updateMeter(meterNo: String, transform: (MeterCardState) -> MeterCardState) {
        uiState = uiState.copy(
            meters = uiState.meters.map { if (it.meter == meterNo) transform(it) else it }
        )
        val updated = uiState.meters.firstOrNull { it.meter == meterNo } ?: return
        if (updated.error == null && updated.hasData()) {
            viewModelScope.launch { snapshotCache.put(updated) }
        }
    }

    /**
     * A refresh that fails is not a reason to blank a card that already has numbers on it — the
     * user keeps seeing the last known values (the offline notice explains why they are stale),
     * and the error is only surfaced when there is nothing else to show.
     */
    private fun onRefreshFailed(meterNo: String, message: String) {
        updateMeter(meterNo) {
            if (it.hasData()) it.copy(isLoading = false, error = null)
            else it.copy(isLoading = false, error = message)
        }
    }

    fun addMeter(
        meter: String,
        nickname: String,
        provider: MeterProvider,
        meterType: MeterType,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (meter.isBlank()) {
            onError("Please enter ${provider.hint.lowercase()}.")
            return
        }
        if (uiState.meters.any { it.meter == meter }) {
            onError("This meter is already in your list.")
            return
        }
        val resolvedType = when (provider) {
            MeterProvider.DESCO -> MeterType.PREPAID
            MeterProvider.PGCL -> MeterType.POSTPAID
            MeterProvider.NESCO -> meterType
        }
        viewModelScope.launch {
            try {
                val nick: String
                val newState: MeterCardState
                when {
                    provider == MeterProvider.NESCO && resolvedType == MeterType.POSTPAID -> {
                        val data = NescoPostpaidScraper.scrapeMeter(meter)
                        nick = nickname.ifBlank { data.name ?: meter }
                        newState = MeterCardState(
                            meter = meter,
                            nickname = nick,
                            provider = provider,
                            billingType = resolvedType,
                            isLoading = false,
                            nescoPostpaidData = data
                        )
                    }
                    provider == MeterProvider.NESCO -> {
                        val data = NescoScraper.scrapeMeter(meter)
                        nick = nickname.ifBlank { data.name ?: meter }
                        newState = MeterCardState(
                            meter = meter,
                            nickname = nick,
                            provider = provider,
                            billingType = resolvedType,
                            isLoading = false,
                            name = data.name,
                            fatherOrHusbandName = data.fatherOrHusbandName,
                            address = data.address,
                            mobile = data.mobile,
                            office = data.office,
                            feederName = data.feederName,
                            consumerNo = data.consumerNo,
                            meterSerialNo = data.meterNo,
                            approvedLoadKw = data.approvedLoadKw,
                            tariff = data.tariff,
                            meterType = data.meterType,
                            meterStatus = data.meterStatus,
                            installDate = data.installDate,
                            minRechargeAmount = data.minRechargeAmount,
                            balance = data.balance,
                            balanceAsOf = data.balanceAsOf,
                            lastRecharge = data.lastRecharge,
                            lastRechargeDate = data.lastRecharge?.date,
                            lastRechargeAmount = data.lastRecharge?.rechargeAmount
                        )
                    }
                    provider == MeterProvider.DESCO -> {
                        val data = DescoScraper.scrapeMeter(meter)
                        nick = nickname.ifBlank { data.customerName ?: meter }
                        newState = MeterCardState(
                            meter = meter,
                            nickname = nick,
                            provider = provider,
                            billingType = resolvedType,
                            isLoading = false,
                            balance = data.balance,
                            balanceAsOf = data.readingTime,
                            lastRechargeDate = data.lastRecharge?.rechargeDate,
                            lastRechargeAmount = data.lastRecharge?.totalAmount,
                            predictionDaysRemaining = estimateDescoDaysRemaining(data.balance, data.monthlyConsumption),
                            descoData = data
                        )
                    }
                    else -> {
                        val data = PgclScraper.scrapeMeter(meter)
                        nick = nickname.ifBlank { data.customerName ?: meter }
                        newState = MeterCardState(
                            meter = meter,
                            nickname = nick,
                            provider = provider,
                            billingType = resolvedType,
                            isLoading = false,
                            pgclData = data
                        )
                    }
                }
                meterStore.addMeter(MeterInfo(meter, nick, provider, resolvedType))
                uiState = uiState.copy(meters = uiState.meters + newState)
                snapshotCache.put(newState)
                onSuccess()
                if (provider == MeterProvider.NESCO && resolvedType == MeterType.PREPAID) {
                    refreshMonthlyUsageIfStale(meter, force = true)
                }
            } catch (e: Exception) {
                onError("Could not verify ${provider.hint.lowercase()}: " + (e.message ?: "unknown error"))
            }
        }
    }

    fun removeMeter(meterNo: String) {
        viewModelScope.launch {
            meterStore.removeMeter(meterNo)
            monthlyUsageCache.remove(meterNo)
            snapshotCache.remove(meterNo)
            uiState = uiState.copy(meters = uiState.meters.filterNot { it.meter == meterNo })
        }
    }

    private fun timeNow(): String =
        SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
}

/**
 * Averages the last few months' total usage/deduction to get a daily burn rate, then divides
 * the current balance by it. Falls back to null (shown as "not enough data yet") until at
 * least one month of usage history has been fetched.
 */
fun estimateDaysRemaining(balance: Double?, monthlyUsage: List<MonthlyUsageRow>): Double? {
    if (balance == null) return null
    // An empty or negative balance has already run out, and that is true regardless of how much
    // usage history has been fetched. Returning null here made those meters — the ones that most
    // need attention — sit on "Checking…" forever.
    if (balance <= 0.0) return 0.0
    val recentMonths = monthlyUsage.take(3).mapNotNull { it.totalUsage }.filter { it > 0.0 }
    if (recentMonths.isEmpty()) return null
    val avgDailyUsage = (recentMonths.sum() / recentMonths.size) / AVG_DAYS_PER_MONTH
    if (avgDailyUsage <= 0.0) return null
    return balance / avgDailyUsage
}

fun estimateDescoDaysRemaining(balance: Double?, monthlyConsumption: List<DescoMonthlyConsumptionRow>): Double? {
    if (balance == null) return null
    if (balance <= 0.0) return 0.0
    val recentMonths = monthlyConsumption.take(3).mapNotNull { it.consumedTaka }.filter { it > 0.0 }
    if (recentMonths.isEmpty()) return null
    val avgDailyUsage = (recentMonths.sum() / recentMonths.size) / AVG_DAYS_PER_MONTH
    if (avgDailyUsage <= 0.0) return null
    return balance / avgDailyUsage
}
