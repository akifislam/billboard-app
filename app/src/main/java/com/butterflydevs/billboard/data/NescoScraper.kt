package com.butterflydevs.billboard.data

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

object NescoScraper {

    private const val NESCO_URL = "https://customer.nesco.gov.bd/pre/panel"
    private const val AGENT =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val SUBMIT_RECHARGE_HISTORY = "রিচার্জ হিস্ট্রি"
    private const val SUBMIT_MONTHLY_USAGE = "মাসিক ব্যবহার"

    private val meterNumberPattern = Regex("^[A-Za-z0-9-]{4,30}$")
    private val csrfMetaPattern = Regex("name=\"csrf-token\"\\s+content=\"([^\"]+)\"")
    private val csrfInputPattern = Regex("name=\"_token\"\\s+value=\"([^\"]+)\"")
    private val balanceAsOfPattern = Regex(
        "(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{4}[^<)]*?[AP]M)"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val browserHeaders = Headers.Builder()
        .add("User-Agent", AGENT)
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9,bn;q=0.8")
        .add("Cache-Control", "no-cache")
        .add("Pragma", "no-cache")
        .build()

    suspend fun scrapeMeter(meterNo: String): ScrapedMeterData = withContext(Dispatchers.IO) {
        val data = parsePanel(postToPanel(meterNo, SUBMIT_RECHARGE_HISTORY))
        // NESCO answers an unknown meter number with HTTP 200 and the same page shell, just with
        // every detail field left blank — so a successful request is not proof the meter exists.
        // Without this check a made-up number is happily saved as an empty, permanently blank card.
        if (!data.identifiesAMeter()) {
            throw IOException("No meter found with this number")
        }
        data
    }

    /** True when the panel came back carrying at least one field that identifies a real meter. */
    private fun ScrapedMeterData.identifiesAMeter(): Boolean =
        !name.isNullOrBlank() ||
            !consumerNo.isNullOrBlank() ||
            !meterNo.isNullOrBlank() ||
            !address.isNullOrBlank() ||
            balance != null ||
            lastRecharge != null

    suspend fun scrapeMonthlyUsage(meterNo: String): List<MonthlyUsageRow> = withContext(Dispatchers.IO) {
        parseMonthlyTable(postToPanel(meterNo, SUBMIT_MONTHLY_USAGE))
    }

    private fun postToPanel(meterNo: String, submitValue: String): String {
        val trimmed = meterNo.trim()
        if (!meterNumberPattern.matches(trimmed)) {
            throw IOException("Invalid meter number")
        }

        val getRequest = Request.Builder().url(NESCO_URL).headers(browserHeaders).build()
        val getResponse = client.executeWithRetry(getRequest)
        val getHtml: String
        val cookies: String
        try {
            if (!getResponse.isSuccessful) {
                throw IOException(upstreamMessage("GET", getResponse.code))
            }
            getHtml = getResponse.body?.string() ?: ""
            cookies = getResponse.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") }
        } finally {
            getResponse.close()
        }

        val token = (csrfMetaPattern.find(getHtml) ?: csrfInputPattern.find(getHtml))?.groupValues?.get(1)
            ?: throw IOException("CSRF token not found on GET")

        val formBody = FormBody.Builder()
            .add("_token", token)
            .add("cust_no", trimmed)
            .add("submit", submitValue)
            .build()

        val postHeaders = browserHeaders.newBuilder()
            .add("Cookie", cookies)
            .add("Origin", "https://customer.nesco.gov.bd")
            .add("Referer", NESCO_URL)
            .build()

        val postRequest = Request.Builder().url(NESCO_URL).headers(postHeaders).post(formBody).build()
        val postResponse = client.executeWithRetry(postRequest)
        try {
            if (!postResponse.isSuccessful) {
                throw IOException(upstreamMessage("POST", postResponse.code))
            }
            return postResponse.body?.string() ?: ""
        } finally {
            postResponse.close()
        }
    }

    private fun upstreamMessage(phase: String, code: Int): String = when (code) {
        403 -> "NESCO blocked the $phase request (HTTP 403)"
        else -> "NESCO $phase request returned HTTP $code"
    }

    private fun toNum(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val cleaned = s.replace(Regex("[^\\d.\\-]"), "")
        return cleaned.toDoubleOrNull()
    }

    /**
     * Reads the panel's detail fields, each of which NESCO renders as `<label>KEY</label>`
     * followed by the `<div><input value="…"></div>` holding its value.
     *
     * This used to scan a fixed 600-character window of raw HTML after the label text, which
     * truncated silently. The balance input opens 583 characters past its label, so a balance of
     * "-183.231" ran one character past the window and parsed as null, while the shorter
     * "-22.387" on another meter's identically laid out page just fit — a balance disappearing
     * purely for having one more digit. Walking label/input pairs in document order has no cliff.
     */
    private class PanelFields(doc: Document) {

        private val nodes = doc.select("label, input")

        /**
         * Matched on the start of the label text because several labels carry a trailing unit or
         * timestamp ("অবশিষ্ট ব্যালেন্স (টাকা)(সময়ঃ …)"). The scan stops at the next label so a field
         * rendered without an input reports nothing instead of borrowing the next field's value.
         */
        fun value(labelPrefix: String): String? {
            val labelIndex = nodes.indexOfFirst {
                it.tagName() == "label" && it.text().trim().startsWith(labelPrefix)
            }
            if (labelIndex < 0) return null
            for (i in labelIndex + 1 until nodes.size) {
                val node = nodes[i]
                if (node.tagName() == "label") return null
                if (node.tagName() == "input") return node.attr("value").trim().ifBlank { null }
            }
            return null
        }
    }

    private fun firstRechargeRow(doc: Document): List<String> {
        for (table in doc.select("table")) {
            val rows = table.select("tr")
            val headerCells = rows.firstOrNull()?.select("th, td") ?: continue
            val headerText = headerCells.joinToString(" ") { it.text() }
            if (headerText.contains("টোকেন") || headerText.contains("রিচার্জের তারিখ")) {
                if (rows.size > 1) {
                    return rows[1].select("th, td").map { it.text().trim() }
                }
            }
        }
        return emptyList()
    }

    private fun toRechargeRecord(rr: List<String>): RechargeRecord? {
        if (rr.size < 15) return null
        return RechargeRecord(
            tokenNo = rr.getOrNull(2)?.ifBlank { null },
            meterRent = toNum(rr.getOrNull(3)),
            demandCharge = toNum(rr.getOrNull(4)),
            pfcCharge = toNum(rr.getOrNull(5)),
            vat = toNum(rr.getOrNull(6)),
            paidArrears = toNum(rr.getOrNull(7)),
            rebate = toNum(rr.getOrNull(8)),
            electricityCharge = toNum(rr.getOrNull(9)),
            rechargeAmount = toNum(rr.getOrNull(10)),
            possibleUnitsKwh = toNum(rr.getOrNull(11)),
            method = rr.getOrNull(12)?.ifBlank { null },
            date = rr.getOrNull(13)?.ifBlank { null },
            status = rr.getOrNull(14)?.ifBlank { null }
        )
    }

    /** Internal so the parsing regressions can run it against saved panel responses. */
    @VisibleForTesting
    internal fun parsePanel(html: String): ScrapedMeterData {
        val doc = Jsoup.parse(html)
        val fields = PanelFields(doc)
        val lastRecharge = toRechargeRecord(firstRechargeRow(doc))
        return ScrapedMeterData(
            name = fields.value("গ্রাহকের নাম"),
            fatherOrHusbandName = fields.value("পিতা/স্বামীর নাম"),
            address = fields.value("ঠিকানা"),
            mobile = fields.value("মোবাইল"),
            office = fields.value("সংশ্লিষ্ট বিদ্যুৎ অফিস"),
            feederName = fields.value("ফিডারের নাম"),
            consumerNo = fields.value("কনজ্যুমার নম্বর"),
            // The search box near the top of the page is labelled "প্রি-পেইড গ্রাহক নম্বর/মিটার নম্বর
            // টাইপ করুন", so prefix matching reaches the detail field without extra disambiguation.
            meterNo = fields.value("মিটার নম্বর"),
            approvedLoadKw = fields.value("অনুমোদিত লোড"),
            tariff = fields.value("অনুমোদিত ট্যারিফ"),
            meterType = fields.value("মিটারের ধরণ"),
            meterStatus = fields.value("মিটার স্ট্যাটাস"),
            installDate = fields.value("মিটার স্থাপনের তারিখ"),
            minRechargeAmount = toNum(fields.value("মিনিমাম রিচার্জের পরিমাণ")),
            balance = toNum(fields.value("অবশিষ্ট ব্যালেন্স")),
            balanceAsOf = balanceAsOfPattern.find(html)?.groupValues?.get(1),
            lastRecharge = lastRecharge
        )
    }

    private fun parseMonthlyTable(html: String): List<MonthlyUsageRow> {
        val doc = Jsoup.parse(html)
        for (table in doc.select("table")) {
            val headerText = table.select("thead th").joinToString(" ") { it.text() }
            if (!headerText.contains("বছর") || !headerText.contains("সর্বমোট রিচার্জ")) continue
            return table.select("tbody tr").mapNotNull { row ->
                val cells = row.select("td").map { it.text().trim() }
                if (cells.size < 13) return@mapNotNull null
                MonthlyUsageRow(
                    year = cells[0],
                    month = cells[1],
                    totalRecharge = toNum(cells[2]),
                    rebate = toNum(cells[3]),
                    electricityUsedTaka = toNum(cells[4]),
                    meterRent = toNum(cells[5]),
                    demandCharge = toNum(cells[6]),
                    pfcCharge = toNum(cells[7]),
                    paidArrears = toNum(cells[8]),
                    vat = toNum(cells[9]),
                    totalUsage = toNum(cells[10]),
                    endOfMonthBalance = toNum(cells[11]),
                    unitsUsedKwh = toNum(cells[12])
                )
            }
        }
        return emptyList()
    }
}
