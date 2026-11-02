package com.butterflydevs.billboard.data.remote

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal banner-image loader built on the OkHttp the project already ships, rather than pulling in
 * Coil/Glide/Picasso for a single image on one screen.
 *
 * It leans on OkHttp's HTTP cache, so GitHub Pages' ETag/Last-Modified headers turn repeat loads
 * into cheap 304s, and an offline launch can still be served the previously downloaded banner via
 * [CacheControl.FORCE_CACHE]. Every failure path returns null, which the UI renders as "no ad" —
 * there is deliberately no broken-image placeholder.
 */
class AdImageLoader(context: Context) {

    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, CACHE_DIR), CACHE_SIZE_BYTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads (or replays from cache) and decodes [url], downsampled to at most [reqWidthPx] so a
     * large banner cannot blow up the heap. Returns null if the URL is not https, the request
     * fails, or the bytes are not a decodable image.
     */
    suspend fun load(url: String, reqWidthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val safeUrl = safeHttpsUrlOrNull(url) ?: return@withContext null
        val bytes = readBytes(safeUrl) ?: return@withContext null
        decode(bytes, reqWidthPx)
    }

    private fun readBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        val fromNetwork = try {
            execute(request)
        } catch (e: Exception) {
            null
        }
        if (fromNetwork != null) return fromNetwork

        // Offline (or the host is down): fall back to whatever the HTTP cache still holds.
        return try {
            execute(request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build())
        } catch (e: Exception) {
            null
        }
    }

    private fun execute(request: Request): ByteArray? =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_IMAGE_BYTES) return null
            val bytes = body.byteStream().readAtMost(MAX_IMAGE_BYTES.toInt())
            if (bytes.isEmpty()) null else bytes
        }

    private fun decode(bytes: ByteArray, reqWidthPx: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null // Not an image the platform can read (HTML error page, truncated download, ...).
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, reqWidthPx)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun sampleSizeFor(sourceWidth: Int, reqWidthPx: Int): Int {
        if (reqWidthPx <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= reqWidthPx) sample *= 2
        return sample
    }

    private companion object {
        const val CACHE_DIR = "billboard_ads"
        const val CACHE_SIZE_BYTES = 8L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
    }
}

/** Reads at most [limit] bytes so an oversized or endless response cannot exhaust memory. */
private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (output.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (read < 0) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
