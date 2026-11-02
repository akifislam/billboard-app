package com.butterflydevs.billboard.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.butterflydevs.billboard.data.DescoScraper
import com.butterflydevs.billboard.data.MeterProvider
import com.butterflydevs.billboard.data.MeterStore
import com.butterflydevs.billboard.data.MeterType
import com.butterflydevs.billboard.data.NescoScraper
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "low_balance_check"

/**
 * Periodic background balance check. Runs whether or not BillBoard is open — WorkManager keeps the
 * schedule across process death and reboots — so a meter can run low while the app is closed and
 * the user still hears about it.
 */
class LowBalanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val meters = MeterStore(applicationContext).getAll()
        for (m in meters) {
            try {
                // Only prepaid meters carry a running balance to fall below the threshold. Postpaid
                // NESCO and PGCL gas are billed, not topped up, and previously every one of them
                // was pointlessly (and incorrectly) run through the NESCO prepaid scraper.
                val balance = when {
                    m.resolvedProvider == MeterProvider.NESCO &&
                        m.resolvedMeterType == MeterType.PREPAID ->
                        NescoScraper.scrapeMeter(m.meter).balance

                    m.resolvedProvider == MeterProvider.DESCO ->
                        DescoScraper.scrapeMeter(m.meter).balance

                    else -> null
                } ?: continue

                NotificationHelper.checkAndNotify(applicationContext, m.meter, m.nickname, balance)
            } catch (e: Exception) {
                // Skip this meter this cycle; it'll be retried on the next periodic run.
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LowBalanceWorker>(15, TimeUnit.MINUTES)
                // Without a network there is nothing to check; let the system defer the run
                // instead of burning a cycle on guaranteed failures.
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                // REPLACE so an existing schedule picks up changed constraints on upgrade;
                // KEEP would leave older installs on the previous, unconstrained request.
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
