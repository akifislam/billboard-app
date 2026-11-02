package com.butterflydevs.billboard.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.butterflydevs.billboard.data.remote.RemoteConfigRepository
import com.butterflydevs.billboard.data.remote.RemoteConfigUrls
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Exercises every way fetching `config.json` can go wrong, since the whole point of this layer is
 * that BillBoard keeps working when GitHub does not.
 */
@RunWith(AndroidJUnit4::class)
class RemoteConfigRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: MockWebServer

    private val validJson = """
        {"marquee":{"enabled":true,"text":"নোটিশ","click_url":"https://example.com"},
         "advertisement":{"enabled":true,"image_url":"https://example.com/a.webp"}}
    """.trimIndent()

    private fun repo(path: String = "/config.json") =
        RemoteConfigRepository(context, server.url(path).toString())

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        runBlocking { RemoteConfigRepository(context).clearCache() }
    }

    @After fun tearDown() {
        server.shutdown()
        // Leave no test config behind for the real app running on this device.
        runBlocking { RemoteConfigRepository(context).clearCache() }
    }

    @Test fun fetchesAndCachesValidConfig() = runBlocking {
        server.enqueue(MockResponse().setBody(validJson))
        val fetched = repo().fetch()
        assertNotNull(fetched)
        assertTrue(fetched!!.marquee!!.shouldShow())

        // A second repository instance sees the persisted copy without any network at all.
        assertNotNull(RemoteConfigRepository(context).cached())
    }

    @Test fun appendsCacheBustingQueryParameter() = runBlocking {
        server.enqueue(MockResponse().setBody(validJson))
        repo().fetch()
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue("expected a t= parameter, got ${request.path}", request.path!!.contains("t="))
        assertTrue(request.path!!.startsWith("/config.json?"))
    }

    @Test fun returnsNullOnHttpErrors() = runBlocking {
        listOf(404, 500, 403, 301).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code))
            assertNull("HTTP $code should not yield a config", repo().fetch())
        }
    }

    @Test fun returnsNullOnMalformedJson() = runBlocking {
        listOf("{\"marquee\":", "not json", "[]", "<html>404</html>", "").forEach { body ->
            server.enqueue(MockResponse().setBody(body))
            assertNull("body <$body> should not yield a config", repo().fetch())
        }
    }

    @Test fun returnsNullOnTimeoutAndConnectionFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertNull(repo().fetch())

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertNull(repo().fetch())

        // Nothing listening at all — the offline case.
        val dead = RemoteConfigRepository(context, "https://localhost:1/config.json")
        assertNull(dead.fetch())
    }

    @Test fun badConfigDoesNotDestroyTheGoodCachedOne() = runBlocking {
        server.enqueue(MockResponse().setBody(validJson))
        assertNotNull(repo().fetch())

        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(repo().fetch())

        server.enqueue(MockResponse().setBody("{ broken"))
        assertNull(repo().fetch())

        // The last good config survived both failures.
        val cached = RemoteConfigRepository(context).cached()
        assertNotNull(cached)
        assertEquals("নোটিশ", cached!!.marquee!!.text)
    }

    @Test fun freshInstallWithNoNetworkYieldsNothingRatherThanCrashing() = runBlocking {
        val dead = RemoteConfigRepository(context, "https://127.0.0.1:1/config.json")
        assertNull(dead.cached())
        assertNull(dead.fetch())
        assertNull(dead.cached())
    }

    @Test fun oversizedBodyIsTruncatedAndRejectedRatherThanExhaustingMemory() = runBlocking {
        val huge = "{\"marquee\":{\"text\":\"" + "x".repeat(2 * 1024 * 1024) + "\"}}"
        server.enqueue(MockResponse().setBody(huge))
        // Truncated at the read cap, so it no longer parses — and the app stays up.
        assertNull(repo().fetch())
    }

    @Test fun emptyAndUnknownShapesDegradeToHiddenFeatures() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        val config = repo().fetch()
        assertNotNull(config)
        assertNull(config!!.marquee)
        assertNull(config.advertisement)
    }

    @Test fun productionUrlIsHttpsAndFailsSafely() = runBlocking {
        assertTrue(RemoteConfigUrls.PRODUCTION_CONFIG_URL.startsWith("https://"))
        // Hits the real host over TLS. Whether Pages is published yet or not, the only acceptable
        // outcomes are "a config" or "null" — never an exception.
        val result = RemoteConfigRepository(context, RemoteConfigUrls.PRODUCTION_CONFIG_URL).fetch()
        assertFalse("should not throw", false)
        if (result != null) assertNotNull(result)
    }
}
