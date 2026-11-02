package com.butterflydevs.billboard.data.remote

import android.content.Context
import com.butterflydevs.billboard.BuildConfig
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val Context.remoteConfigDataStore by preferencesDataStore(name = "remote_config")

/**
 * Where the marquee/advertisement configuration is fetched from.
 *
 * ▸ To point the app at a different GitHub account, change [PRODUCTION_CONFIG_URL] — replacing the
 *   `akifislam` segment with your GitHub username is the only edit required, and this is the only
 *   place in the app that names it.
 *
 * A debug build can be pointed somewhere else for a dry run without touching this file:
 * `./gradlew installDebug -Pbillboard.configUrl=http://10.0.2.2:8787/config.json`. Release builds
 * ignore that entirely and always use the constant below.
 */
object RemoteConfigUrls {
    const val PRODUCTION_CONFIG_URL = "https://akifislam.github.io/BillBoard/config.json"

    val CONFIG_URL: String =
        BuildConfig.REMOTE_CONFIG_URL.ifBlank { PRODUCTION_CONFIG_URL }
}

/**
 * Fetches `config.json`, and remembers the last good copy so the features keep behaving sensibly
 * when GitHub is unreachable.
 *
 * The cache deliberately stores the raw *validated* JSON rather than a rendered decision: the
 * ad's date window is re-checked against the clock every time the config is read, so a cached
 * campaign still stops showing the moment it expires.
 */
class RemoteConfigRepository(
    private val context: Context,
    /** Overridable so tests can point at a local server; production always uses the real URL. */
    private val configUrl: String = RemoteConfigUrls.CONFIG_URL
) {

    private object Keys {
        val CONFIG_JSON = stringPreferencesKey("config_json")
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Last successfully parsed config, or null on a fresh install that has never reached GitHub. */
    suspend fun cached(): RemoteConfig? = withContext(Dispatchers.IO) {
        val json = context.remoteConfigDataStore.data.first()[Keys.CONFIG_JSON]
        parseOrNull(json)
    }

    /**
     * Downloads and parses the config. Returns null on *any* failure (offline, DNS failure, 404,
     * 500, timeout, truncated body, invalid JSON) — callers fall back to [cached]. Never throws.
     */
    suspend fun fetch(): RemoteConfig? = withContext(Dispatchers.IO) {
        val body = try {
            // GitHub's CDN can serve a stale config.json for minutes after an edit; a changing
            // query string sidesteps that without disabling the app's own offline cache.
            val url = configUrl +
                (if (configUrl.contains('?')) "&" else "?") +
                "t=" + System.currentTimeMillis()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                // Guard against a misconfigured host streaming something enormous at us.
                response.body?.byteStream()?.bufferedReader()?.use { it.readText(MAX_CONFIG_CHARS) }
            }
        } catch (e: Exception) {
            null
        } ?: return@withContext null

        val parsed = parseOrNull(body) ?: return@withContext null
        try {
            context.remoteConfigDataStore.edit { it[Keys.CONFIG_JSON] = body }
        } catch (e: Exception) {
            // Persisting is best-effort; the freshly fetched config is still returned.
        }
        parsed
    }

    /** Drops the stored config, so the next launch behaves like a fresh install. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            context.remoteConfigDataStore.edit { it.remove(Keys.CONFIG_JSON) }
        } catch (e: Exception) {
            // Nothing useful to do if the store cannot be written.
        }
        Unit
    }

    private fun parseOrNull(json: String?): RemoteConfig? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson(json, RemoteConfig::class.java)
        } catch (e: JsonSyntaxException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_CONFIG_CHARS = 256 * 1024
    }
}

/** Reads at most [limit] characters, so a hostile or broken response cannot exhaust memory. */
private fun java.io.Reader.readText(limit: Int): String {
    val buffer = CharArray(8 * 1024)
    val builder = StringBuilder()
    while (builder.length < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - builder.length))
        if (read < 0) break
        builder.appendRange(buffer, 0, read)
    }
    return builder.toString()
}
