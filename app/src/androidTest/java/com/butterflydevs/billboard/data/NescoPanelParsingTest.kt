package com.butterflydevs.billboard.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Parser regression coverage built entirely from synthetic customer data. */
@RunWith(AndroidJUnit4::class)
class NescoPanelParsingTest {

    private fun field(label: String, value: String): String =
        "<label>$label</label>" + "<span aria-hidden=\"true\">padding</span>".repeat(45) +
            "<input value=\"$value\">"

    private fun panel(balance: String, meter: String = "TEST-METER-001"): String = """
        <section>
          ${field("গ্রাহকের নাম", "Test Customer")}
          ${field("মিটার নম্বর", meter)}
          ${field("অনুমোদিত লোড", "2")}
          ${field("অনুমোদিত ট্যারিফ", "LT-A")}
          ${field("মিটারের ধরণ", "Single-Phase Meter")}
          ${field("মিটার স্ট্যাটাস", "Active")}
          ${field("মিটার স্থাপনের তারিখ", "01-Jan-2026 12:00 PM")}
          ${field("মিনিমাম রিচার্জের পরিমাণ", "50")}
          ${field("অবশিষ্ট ব্যালেন্স", balance)}
        </section>
    """.trimIndent()

    @Test fun parsesAThreeDigitNegativeBalance() {
        val data = NescoScraper.parsePanel(panel("-183.231"))
        assertEquals(-183.231, data.balance!!, 0.0001)
        assertEquals("TEST-METER-001", data.meterNo)
    }

    @Test fun parsesATwoDigitNegativeBalance() {
        val data = NescoScraper.parsePanel(panel("-22.387", "TEST-METER-002"))
        assertEquals(-22.387, data.balance!!, 0.0001)
        assertEquals("TEST-METER-002", data.meterNo)
    }

    @Test fun parsesTheSurroundingDetailFields() {
        val data = NescoScraper.parsePanel(panel("1248.50"))
        assertEquals("LT-A", data.tariff)
        assertEquals("2", data.approvedLoadKw)
        assertEquals("Single-Phase Meter", data.meterType)
        assertEquals("Active", data.meterStatus)
        assertEquals("01-Jan-2026 12:00 PM", data.installDate)
        assertEquals(50.0, data.minRechargeAmount!!, 0.0001)
        assertNotNull(data.name)
    }

    @Test fun doesNotReadTheMeterNumberOffTheSearchBox() {
        val search = """
            <label>প্রি-পেইড গ্রাহক নম্বর/মিটার নম্বর টাইপ করুন</label>
            <input type="text" name="cust_no" value="SEARCH-VALUE">
        """.trimIndent()
        assertEquals("TEST-METER-001", NescoScraper.parsePanel(search + panel("1248.50")).meterNo)
    }
}
