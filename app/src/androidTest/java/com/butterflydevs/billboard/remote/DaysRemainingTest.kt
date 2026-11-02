package com.butterflydevs.billboard.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.butterflydevs.billboard.data.MonthlyUsageRow
import com.butterflydevs.billboard.ui.components.formatDaysRemaining
import com.butterflydevs.billboard.viewmodel.estimateDaysRemaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Estimated to last" used to fall back to "Checking…" for any balance at or below zero, so the
 * meters that had actually run out looked identical to ones still waiting on usage history.
 */
@RunWith(AndroidJUnit4::class)
class DaysRemainingTest {

    private fun month(total: Double) = MonthlyUsageRow(
        year = "2026", month = "Jul", totalRecharge = null, rebate = null,
        electricityUsedTaka = null, meterRent = null, demandCharge = null, pfcCharge = null,
        paidArrears = null, vat = null, totalUsage = total, endOfMonthBalance = null,
        unitsUsedKwh = null
    )

    private val history = listOf(month(900.0), month(900.0), month(900.0))

    @Test fun negativeBalanceIsExhaustedNotCalculating() {
        assertEquals(0.0, estimateDaysRemaining(-250.0, history))
        assertEquals(0.0, estimateDaysRemaining(-0.01, emptyList()))
        assertEquals("No balance left", formatDaysRemaining(estimateDaysRemaining(-250.0, history)))
    }

    @Test fun zeroBalanceIsExhausted() {
        assertEquals(0.0, estimateDaysRemaining(0.0, history))
        assertEquals("No balance left", formatDaysRemaining(estimateDaysRemaining(0.0, emptyList())))
    }

    @Test fun unknownBalanceStillReadsAsCalculating() {
        assertNull(estimateDaysRemaining(null, history))
        assertEquals("Checking…", formatDaysRemaining(null))
    }

    @Test fun positiveBalanceWithoutHistoryStaysCalculating() {
        // Genuinely "not enough data yet" — this is the case the placeholder is meant for.
        assertNull(estimateDaysRemaining(500.0, emptyList()))
        assertNull(estimateDaysRemaining(500.0, listOf(month(0.0))))
    }

    @Test fun positiveBalanceWithHistoryEstimatesDays() {
        // 900/month over 30.44 days ≈ 29.57/day; 543.68 / 29.57 ≈ 18.4 days.
        val days = estimateDaysRemaining(543.68, history)!!
        assertEquals(18.4, days, 0.2)
        assertEquals("~18 days", formatDaysRemaining(days))
    }

    @Test fun formattingCoversTheShortTailEnd() {
        assertEquals("No balance left", formatDaysRemaining(0.0))
        assertEquals("Less than a day", formatDaysRemaining(0.4))
        assertEquals("~1 day", formatDaysRemaining(1.2))
        assertEquals("~2 days", formatDaysRemaining(1.6))
    }
}
