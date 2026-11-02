package com.butterflydevs.billboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * PGCL's server presents an expired TLS certificate, so this client (only this one) trusts
 * any certificate to get through the handshake. Kept isolated from every other scraper.
 */
object PgclScraper {

    private const val URL = "https://rsm.pgcl.org.bd:8443/PGCL_WEB/getCustomerWebView"
    private val customerIdPattern = Regex("^[A-Za-z0-9-]{4,30}$")

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

    private val headers = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        )
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    suspend fun scrapeMeter(customerId: String): PgclMeterData = withContext(Dispatchers.IO) {
        val trimmed = customerId.trim()
        if (!customerIdPattern.matches(trimmed)) {
            throw IOException("Invalid customer code")
        }

        val formBody = FormBody.Builder().add("customer_id", trimmed).build()
        val request = Request.Builder().url(URL).headers(headers).post(formBody).build()
        val response = client.executeWithRetry(request)
        val html: String
        try {
            if (!response.isSuccessful) {
                throw IOException("PGCL request returned HTTP ${response.code}")
            }
            html = response.body?.string() ?: ""
        } finally {
            response.close()
        }

        if (html.isBlank() || !html.contains("Customer Code")) {
            throw IOException("Customer code not found")
        }
        parse(html)
    }

    private fun parse(html: String): PgclMeterData {
        val doc = Jsoup.parse(html)

        val info = mutableMapOf<String, String>()
        for (row in doc.select("table.table3 tr")) {
            val cells = row.select("td")
            if (cells.size >= 2) {
                info[cells[0].text().trim()] = cells[1].text().trim()
            }
        }

        val payments = mutableListOf<PgclPayment>()
        for (table in doc.select("table.table2")) {
            val headerText = table.select("thead").text()
            if (!headerText.contains("payment details")) continue
            for (row in table.select("tbody tr")) {
                val cells = row.select("td").map { it.text().trim() }
                if (cells.size < 7) continue
                payments.add(
                    PgclPayment(
                        sl = cells[0],
                        billMonth = cells[1],
                        bank = cells[2].ifBlank { null },
                        billedAmount = cells[3].toDoubleOrNull(),
                        surcharge = cells[4].toDoubleOrNull(),
                        total = cells[5].toDoubleOrNull(),
                        collectionDate = cells[6].ifBlank { null }
                    )
                )
            }
        }

        val dueStatusMessage = doc.select("#home .alert").firstOrNull()?.text()?.trim()

        fun field(label: String): String? = info[label]?.ifBlank { null }

        return PgclMeterData(
            customerCode = field("Customer Code"),
            customerName = field("Customer Name"),
            address = field("Address"),
            phone = field("Phone"),
            category = field("Category"),
            status = field("Status"),
            connectionDate = field("Connection Date"),
            meteredStatus = field("Metered Status"),
            maxLoad = field("Max load"),
            minLoad = field("Min load"),
            burnerCount = field("Burner count"),
            regionalOffice = field("Regional Office"),
            dueStatusMessage = dueStatusMessage,
            payments = payments
        )
    }
}
