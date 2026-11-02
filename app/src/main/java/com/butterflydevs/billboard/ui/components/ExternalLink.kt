package com.butterflydevs.billboard.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.butterflydevs.billboard.data.remote.safeHttpsUrlOrNull

/**
 * Hands a remote-supplied link to the browser, but only after re-validating it as https here at
 * the point of use. Remote JSON must never be able to launch arbitrary components, so the intent
 * is restricted to ACTION_VIEW on an https URI — a `market:`, `intent:` or `file:` value is
 * dropped rather than dispatched. Failing to find a browser is a no-op, never a crash.
 */
fun Context.openExternalLink(url: String) {
    val safeUrl = safeHttpsUrlOrNull(url) ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No browser on the device: nothing sensible to do, and certainly not worth crashing over.
    } catch (e: SecurityException) {
        // A handler exists but refused the launch.
    }
}
