package com.butterflydevs.billboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

object NescoPostpaidScraper {

    private const val POSTPAID_URL = "https://customer.nesco.gov.bd/post/bill"
    private const val AGENT =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val SUBMIT_DUE_BILL = "বকেয়া বিল"
    private const val SUBMIT_ALL_BILLS = "সকল বিল"

    private val meterNumberPattern = Regex("^[A-Za-z0-9-]{4,30}$")
    private val csrfMetaPattern = Regex("name=\"csrf-token\"\\s+content=\"([^\"]+)\"")
    private val csrfInputPattern = Regex("name=\"_token\"\\s+value=\"([^\"]+)\"")
    // Unlike the prepaid panel, this page mixes single- and double-quoted value="" attributes.
    private val valuePattern = Regex("value=[\"']([^\"']*)[\"']")

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

    suspend fun scrapeMeter(meterNo: String): NescoPostpaidMeterData = withContext(Dispatchers.IO) {
        val allBillsHtml = postToPanel(meterNo, SUBMIT_ALL_BILLS)
        if (!allBillsHtml.contains("গ্রাহকের নাম")) {
            throw IOException("Postpaid meter/account number not found")
        }
        val allBills = parseBillTable(allBillsHtml)

        // The "বকেয়া বিল" (due bill) action reliably errors server-side on NESCO's end when
        // there's nothing due, instead of returning an empty table. Treat that failure mode
        // (and any other fetch failure for this secondary call) the same as "no due bills"
        // rather than surfacing it as an error — the primary "all bills" call above already
        // succeeded, so we have a meter to show.
        val dueBills = try {
            val dueHtml = postToPanel(meterNo, SUBMIT_DUE_BILL)
            if (dueHtml.contains("Could not process")) emptyList() else parseBillTable(dueHtml)
        } catch (e: Exception) {
            emptyList()
        }

        parseCustomerInfo(allBillsHtml, dueBills, allBills)
    }

    private fun postToPanel(meterNo: String, submitValue: String): String {
        val trimmed = meterNo.trim()
        if (!meterNumberPattern.matches(trimmed)) {
            throw IOException("Invalid meter number")
        }

        val getRequest = Request.Builder().url(POSTPAID_URL).headers(browserHeaders).build()
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
            .add("Referer", POSTPAID_URL)
            .build()

        val postRequest = Request.Builder().url(POSTPAID_URL).headers(postHeaders).post(formBody).build()
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

    private fun stripTags(s: String): String =
        org.jsoup.nodes.Entities.unescape(s.replace(Regex("<[^>]+>"), "")).trim()

    private fun toNum(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val cleaned = s.replace(Regex("[^\\d.\\-]"), "")
        return cleaned.toDoubleOrNull()
    }

    private fun grabAfter(html: String, label: String): String? {
        val i = html.indexOf(label)
        if (i < 0) return null
        val end = minOf(i + 600, html.length)
        val segment = html.substring(i, end)
        val m = valuePattern.find(segment) ?: return null
        return stripTags(m.groupValues[1]).ifBlank { null }
    }

    private fun parseCustomerInfo(
        html: String,
        dueBills: List<PostpaidBillRow>,
        allBills: List<PostpaidBillRow>
    ): NescoPostpaidMeterData = NescoPostpaidMeterData(
        name = grabAfter(html, "গ্রাহকের নাম"),
        fatherOrHusbandName = grabAfter(html, "পিতা/স্বামীর নাম"),
        address = grabAfter(html, "ঠিকানা"),
        mobile = grabAfter(html, "মোবাইল"),
        office = grabAfter(html, "সংশ্লিষ্ট বিদ্যুৎ অফিস"),
        feederName = grabAfter(html, "ফিডারের নাম"),
        consumerNo = grabAfter(html, "কনজ্যুমার নম্বর"),
        accountsOfficeNo = grabAfter(html, "দপ্তরের হিসাব নং"),
        previousConsumerNo = grabAfter(html, "পূর্বের কনজ্যুমার নম্বর"),
        meterNo = grabAfter(html, "মিটার নম্বর"),
        approvedLoadKw = grabAfter(html, "অনুমোদিত লোড"),
        tariff = grabAfter(html, "অনুমোদিত ট্যারিফ"),
        meterStatus = grabAfter(html, "মিটার স্ট্যাটাস"),
        connectionPhase = grabAfter(html, "কানেকশন ফেজ"),
        billGroup = grabAfter(html, "বিল গ্রুপ"),
        bookNo = grabAfter(html, "বই নং"),
        workOrder = grabAfter(html, "ওয়াক অর্ডার"),
        dueBills = dueBills,
        allBills = allBills
    )

    private fun parseBillTable(html: String): List<PostpaidBillRow> {
        val doc = Jsoup.parse(html)
        for (table in doc.select("table")) {
            val headerText = table.select("thead th").joinToString(" ") { it.text() }
            if (!headerText.contains("বিল নম্বর") || !headerText.contains("পেমেন্ট স্ট্যাটাস")) continue
            return table.select("tbody tr").mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 10) return@mapNotNull null
                PostpaidBillRow(
                    billNo = cells[0].text().trim(),
                    year = cells[1].text().trim(),
                    month = cells[2].text().trim(),
                    totalBillTaka = toNum(cells[3].text()),
                    lateFeeTaka = toNum(cells[4].text()),
                    dueDate = cells[5].text().trim().ifBlank { null },
                    paidAmountTaka = toNum(cells[6].text()),
                    paidDate = cells[7].text().trim().ifBlank { null },
                    paymentMethod = cells[8].text().trim().ifBlank { null },
                    paymentStatus = cells[9].text().trim().ifBlank { null }
                )
            }
        }
        return emptyList()
    }
}
