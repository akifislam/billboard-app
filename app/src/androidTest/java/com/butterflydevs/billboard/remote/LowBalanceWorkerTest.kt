package com.butterflydevs.billboard.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.butterflydevs.billboard.data.MeterInfo
import com.butterflydevs.billboard.data.MeterProvider
import com.butterflydevs.billboard.data.MeterStore
import com.butterflydevs.billboard.data.MeterType
import com.butterflydevs.billboard.notifications.LowBalanceWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the background worker's body directly rather than waiting on WorkManager's 15-minute
 * schedule. This is the code path that fires low-balance alerts while BillBoard is closed.
 */
@RunWith(AndroidJUnit4::class)
class LowBalanceWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = MeterStore(context)
    private val added = mutableListOf<String>()

    private suspend fun add(meter: String, provider: MeterProvider, type: MeterType) {
        if (store.getAll().none { it.meter == meter }) {
            store.addMeter(MeterInfo(meter, "test-$meter", provider, type))
            added += meter
        }
    }

    @Before fun setUp() = runBlocking {
        // One of each shape, so the provider routing is genuinely exercised: only the first
        // carries a balance; the other three must be skipped rather than scraped as NESCO prepaid.
        add("90000000001", MeterProvider.NESCO, MeterType.PREPAID)
        add("90000000002", MeterProvider.NESCO, MeterType.POSTPAID)
        add("90000000003", MeterProvider.PGCL, MeterType.POSTPAID)
        add("88888888888", MeterProvider.NESCO, MeterType.PREPAID)   // does not exist upstream
        Unit
    }

    @After fun tearDown() = runBlocking {
        added.forEach { store.removeMeter(it) }
        Unit
    }

    @Test fun workerCompletesAcrossEveryProviderAndSurvivesABadMeter() = runBlocking {
        val worker = TestListenableWorkerBuilder<LowBalanceWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }

    @Test fun workerCompletesWithNoMetersAtAll() = runBlocking {
        store.getAll().forEach { store.removeMeter(it.meter) }
        val worker = TestListenableWorkerBuilder<LowBalanceWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
