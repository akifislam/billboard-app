package com.butterflydevs.billboard.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * DESCO's certificate chain isn't trusted by Android's stricter default trust store (the
 * community DESCO client libraries disable SSL verification for the same reason), so this
 * client — only this one — trusts any certificate to get through the handshake.
 */
object DescoScraper {

    private const val BASE_URL = "https://prepaid.desco.org.bd/api/unified/customer"
    private val meterNumberPattern = Regex("^[A-Za-z0-9-]{4,30}$")

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustAllManager), SecureRandom())
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext.socketFactory, trustAllManager)
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .build()

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    suspend fun scrapeMeter(accountNo: String): DescoMeterData = withContext(Dispatchers.IO) {
        val trimmed = accountNo.trim()
        if (!meterNumberPattern.matches(trimmed)) {
            throw IOException("Invalid account number")
        }

        // DESCO's customer portal accepts either the account number or the meter number, but the
        // API only matches when the identifier is sent under the query param name matching what
        // it actually is (accountNo vs meterNo) — a mismatched pairing returns an empty "OK".
        // Since we don't know which kind of number the user entered, probe meterNo first (that's
        // what's printed on the meter and what most users have on hand), then fall back to
        // accountNo, and reuse whichever param name matched for the rest of the calls below.
        val (paramName, balanceData) = listOf("meterNo", "accountNo").firstNotNullOfOrNull { param ->
            val json = get("$BASE_URL/getBalance", mapOf(param to trimmed))
            dataObjectOf(json)?.let { param to it }
        } ?: throw IOException("DESCO account/meter number not found")

        val infoJson = get("$BASE_URL/getCustomerInfo", mapOf(paramName to trimmed))
        val infoData = dataObjectOf(infoJson)

        val now = Calendar.getInstance()
        val yearAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -365) }
        val rechargeJson = get(
            "$BASE_URL/getRechargeHistory",
            mapOf(
                paramName to trimmed,
                "dateFrom" to dayFormat.format(yearAgo.time),
                "dateTo" to dayFormat.format(now.time)
            )
        )
        val rechargeHistory = dataArrayOf(rechargeJson)?.map { it.asJsonObject }?.map { toRechargeRow(it) }
            ?: emptyList()

        val twelveMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -11) }
        val monthlyJson = get(
            "$BASE_URL/getCustomerMonthlyConsumption",
            mapOf(
                paramName to trimmed,
                "monthFrom" to monthFormat.format(twelveMonthsAgo.time),
                "monthTo" to monthFormat.format(now.time)
            )
        )
        val monthlyConsumption = dataArrayOf(monthlyJson)?.map { it.asJsonObject }?.map { toMonthlyRow(it) }
            ?: emptyList()

        val sortedRecharges = rechargeHistory.sortedByDescending { it.rechargeDate }
        val lastRecharge = sortedRecharges.firstOrNull()

        val currentMonthKey = monthFormat.format(now.time)
        val currentYearPrefix = SimpleDateFormat("yyyy", Locale.US).format(now.time)

        val rechargedThisMonth = sortedRecharges
            .filter { it.rechargeDate?.startsWith(currentMonthKey) == true }
            .sumOf { it.totalAmount ?: 0.0 }
        val rechargedThisYear = sortedRecharges
            .filter { it.rechargeDate?.startsWith(currentYearPrefix) == true }
            .sumOf { it.totalAmount ?: 0.0 }

        val currentMonthRow = monthlyConsumption.find { it.month == currentMonthKey }
        val maxLoadLastYear = monthlyConsumption
            .filter { it.month.startsWith(currentYearPrefix) }
            .mapNotNull { it.maximumDemand }
            .maxOrNull()

        DescoMeterData(
            accountNo = getString(balanceData, "accountNo") ?: trimmed,
            meterNo = getString(balanceData, "meterNo") ?: getString(infoData, "meterNo"),
            balance = getDouble(balanceData, "balance"),
            currentMonthConsumptionTaka = getDouble(balanceData, "currentMonthConsumption"),
            readingTime = getString(balanceData, "readingTime"),
            customerName = getString(infoData, "customerName"),
            contactNo = getString(infoData, "contactNo"),
            feederName = getString(infoData, "feederName"),
            installationAddress = getString(infoData, "installationAddress"),
            installationDate = getString(infoData, "installationDate"),
            phaseType = getString(infoData, "phaseType"),
            registerDate = getString(infoData, "registerDate"),
            sanctionLoad = getString(infoData, "sanctionLoad"),
            tariffSolution = getString(infoData, "tariffSolution"),
            meterModel = getString(infoData, "meterModel"),
            transformer = getString(infoData, "transformer"),
            sdName = getString(infoData, "SDName") ?: getString(infoData, "sdName"),
            lastRecharge = lastRecharge,
            rechargedThisMonth = rechargedThisMonth,
            rechargedThisYear = rechargedThisYear,
            usedThisMonthKwh = currentMonthRow?.consumedUnit,
            maxLoadLastMonth = currentMonthRow?.maximumDemand,
            maxLoadLastYear = maxLoadLastYear,
            monthlyConsumption = monthlyConsumption.sortedByDescending { it.month },
            rechargeHistory = sortedRecharges
        )
    }

    private fun toRechargeRow(obj: JsonObject): DescoRechargeRow = DescoRechargeRow(
        rechargeDate = getString(obj, "rechargeDate"),
        totalAmount = getDouble(obj, "totalAmount"),
        vat = getDouble(obj, "VAT") ?: getDouble(obj, "vat"),
        energyAmount = getDouble(obj, "energyAmount")
    )

    private fun toMonthlyRow(obj: JsonObject): DescoMonthlyConsumptionRow = DescoMonthlyConsumptionRow(
        month = getString(obj, "month") ?: "",
        consumedTaka = getDouble(obj, "consumedTaka"),
        consumedUnit = getDouble(obj, "consumedUnit"),
        maximumDemand = getDouble(obj, "maximumDemand")
    )

    private fun get(url: String, params: Map<String, String>): JsonObject {
        val urlBuilder = url.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val httpUrl = urlBuilder.build()
        val request = Request.Builder()
            .url(httpUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept", "application/json, text/plain, */*")
            .build()
        val response = client.executeWithRetry(request)
        try {
            if (!response.isSuccessful) {
                throw IOException("DESCO request returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: ""
            return JsonParser.parseString(body).asJsonObject
        } finally {
            response.close()
        }
    }

    private fun dataObjectOf(json: JsonObject): JsonObject? =
        json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    private fun dataArrayOf(json: JsonObject): JsonArray? =
        json.get("data")?.takeIf { it.isJsonArray }?.asJsonArray

    private fun getString(obj: JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        if (el.isJsonNull) return null
        return el.asString.takeIf { it.isNotBlank() }
    }

    private fun getDouble(obj: JsonObject?, key: String): Double? {
        val el = obj?.get(key) ?: return null
        if (el.isJsonNull) return null
        return el.asString.toDoubleOrNull()
    }
}
