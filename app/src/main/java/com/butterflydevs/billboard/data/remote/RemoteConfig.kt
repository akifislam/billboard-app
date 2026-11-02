package com.butterflydevs.billboard.data.remote

import android.net.Uri
import com.google.gson.annotations.SerializedName
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Root of the remotely hosted configuration (GitHub Pages `config.json`).
 *
 * Everything here is untrusted input: any field can be missing, null, or the wrong shape, and the
 * only acceptable failure mode is "the feature stays hidden" — never a crash. Gson silently
 * ignores unknown JSON keys, so new sections can be added remotely without breaking installs
 * that are already in users' hands.
 */
data class RemoteConfig(
    val marquee: MarqueeConfig? = null,
    val advertisement: AdvertisementConfig? = null
)

data class MarqueeConfig(
    val enabled: Boolean = false,
    val text: String? = null,
    @SerializedName("click_url") val clickUrl: String? = null
) {
    /** Null unless the remote value is a well-formed https URL, so the UI simply is not clickable. */
    val safeClickUrl: String? get() = clickUrl?.let { safeHttpsUrlOrNull(it) }

    fun shouldShow(): Boolean = enabled && !text.isNullOrBlank()
}

data class AdvertisementConfig(
    val enabled: Boolean = false,
    val id: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("click_url") val clickUrl: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null
) {
    val safeImageUrl: String? get() = imageUrl?.let { safeHttpsUrlOrNull(it) }
    val safeClickUrl: String? get() = clickUrl?.let { safeHttpsUrlOrNull(it) }

    /**
     * True only when the ad is enabled, has a usable https image, and [nowMillis] falls inside the
     * configured window. A missing boundary is unrestricted; a *malformed* boundary hides the ad
     * rather than guessing — showing a campaign outside its paid window is the worse failure.
     *
     * This is re-evaluated against the current clock on every recomposition, which is also what
     * stops a cached config from displaying an expired ad forever.
     */
    fun shouldShow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        if (safeImageUrl == null) return false

        if (!startDate.isNullOrBlank()) {
            val start = parseIso8601Millis(startDate) ?: return false
            if (nowMillis < start) return false
        }
        if (!endDate.isNullOrBlank()) {
            val end = parseIso8601Millis(endDate) ?: return false
            if (nowMillis > end) return false
        }
        return true
    }
}

/**
 * Accepts only absolute https URLs with a real host. This is the single gate every remote-supplied
 * link passes through before it can be loaded or handed to an Intent, which is what keeps
 * `javascript:`, `data:`, `intent:`, `file:` and plain-http values out of the app.
 */
fun safeHttpsUrlOrNull(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val uri = try {
        Uri.parse(trimmed)
    } catch (e: Exception) {
        return null
    }
    if (!uri.isAbsolute) return null
    if (!"https".equals(uri.scheme, ignoreCase = true)) return null
    if (uri.host.isNullOrBlank()) return null
    return trimmed
}

// Offset-bearing patterns first: a value that carries its own zone must not be reinterpreted as
// device-local time. `X` (ISO-8601 offset, also accepts a literal `Z`) is available from API 24.
private val ISO_8601_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm",
    "yyyy-MM-dd"
)

/**
 * Parses an ISO-8601 timestamp to epoch millis, or null if it is not a date this app understands.
 * Callers compare the returned numbers — never the raw strings, which would order "2026-1-5"
 * before "2026-01-06" and break as soon as a zone offset differs.
 */
fun parseIso8601Millis(value: String?): Long? {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return null
    for (pattern in ISO_8601_PATTERNS) {
        try {
            // SimpleDateFormat is not thread-safe, so it is built per call rather than shared.
            val format = SimpleDateFormat(pattern, Locale.US)
            format.isLenient = false
            val position = ParsePosition(0)
            val parsed: Date? = format.parse(text, position)
            // parse() happily stops at the first character it cannot use, so "2026-08-20junk"
            // would otherwise pass as a valid date. Require the whole value to be consumed.
            if (parsed != null && position.index == text.length) return parsed.time
        } catch (e: Exception) {
            // Try the next pattern; exhausting the list means the value is malformed.
        }
    }
    return null
}
