package com.butterflydevs.billboard.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * When the app sits backgrounded for a long stretch, the pooled connection these scraper
 * clients reused can go stale (the carrier/router silently drops the idle socket), so the first
 * request after resuming fails with an IOException even though the network itself is fine.
 * Retrying once on a fresh connection recovers from exactly that case instead of surfacing
 * "Failed to fetch data" to the user.
 */
fun OkHttpClient.executeWithRetry(request: Request): Response {
    return try {
        newCall(request).execute()
    } catch (e: IOException) {
        newCall(request).execute()
    }
}
