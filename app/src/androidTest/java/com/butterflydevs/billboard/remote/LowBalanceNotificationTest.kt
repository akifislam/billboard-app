package com.butterflydevs.billboard.remote

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.butterflydevs.billboard.notifications.LOW_BALANCE_THRESHOLD
import com.butterflydevs.billboard.notifications.NotificationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The alert has to fire once when a balance drops, and not nag on every refresh after that. */
@RunWith(AndroidJUnit4::class)
class LowBalanceNotificationTest {

    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val meter = "TEST_METER_0001"

    private fun postedForTestMeter() =
        manager.activeNotifications.count { it.id == meter.hashCode() }

    @Before fun setUp() {
        NotificationHelper.ensureChannel(context)
        reset()
    }

    @After fun tearDown() = reset()

    private fun reset() {
        manager.cancel(meter.hashCode())
        context.getSharedPreferences("low_balance_state", Context.MODE_PRIVATE)
            .edit().remove("notified_$meter").commit()
    }

    @Test fun firesOnceWhenBalanceDropsBelowThreshold() {
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 42.5)
        Thread.sleep(500)
        assertEquals("expected exactly one alert", 1, postedForTestMeter())
    }

    @Test fun doesNotRepeatWhileStillLow() {
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 42.5)
        Thread.sleep(500)
        manager.cancel(meter.hashCode())      // user swipes it away
        Thread.sleep(300)
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 31.0)
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 12.0)
        Thread.sleep(500)
        assertEquals("should not re-notify until the balance recovers", 0, postedForTestMeter())
    }

    @Test fun rearmsAfterBalanceRecovers() {
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 42.5)
        Thread.sleep(400)
        manager.cancel(meter.hashCode())
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 900.0)  // recharged
        Thread.sleep(300)
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 20.0)   // drained again
        Thread.sleep(500)
        assertEquals("a fresh drop deserves a fresh alert", 1, postedForTestMeter())
    }

    @Test fun staysQuietForHealthyOrUnknownBalances() {
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", LOW_BALANCE_THRESHOLD)
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", 543.68)
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", null)
        Thread.sleep(500)
        assertEquals(0, postedForTestMeter())
    }

    @Test fun negativeBalanceAlsoAlerts() {
        NotificationHelper.checkAndNotify(context, meter, "Bedroom AC", -75.0)
        Thread.sleep(500)
        assertTrue(postedForTestMeter() == 1)
    }
}
