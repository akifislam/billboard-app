package com.butterflydevs.billboard.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.butterflydevs.billboard.data.remote.AdvertisementConfig
import com.butterflydevs.billboard.data.remote.MarqueeConfig
import com.butterflydevs.billboard.data.remote.RemoteConfig
import com.butterflydevs.billboard.data.remote.parseIso8601Millis
import com.butterflydevs.billboard.data.remote.safeHttpsUrlOrNull
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented rather than local-JVM because the URL validation goes through android.net.Uri,
 * which is a stub outside a real device.
 */
@RunWith(AndroidJUnit4::class)
class RemoteConfigParsingTest {

    private val gson = Gson()

    // ---------- ISO-8601 parsing ----------

    @Test fun parsesOffsetTimestamps() {
        assertEquals(1787162400000L, parseIso8601Millis("2026-08-20T00:00:00+06:00"))
        assertEquals(1789927199000L, parseIso8601Millis("2026-09-20T23:59:59+06:00"))
        assertEquals(1767225600000L, parseIso8601Millis("2026-01-01T00:00:00Z"))
        assertEquals(1735711200500L, parseIso8601Millis("2025-01-01T12:00:00.500+06:00"))
    }

    @Test fun parsesZonelessAndDateOnlyForms() {
        assertNotNull(parseIso8601Millis("2026-08-20"))
        assertNotNull(parseIso8601Millis("2026-08-20T10:30"))
        assertNotNull(parseIso8601Millis("2026-08-20T10:30:15"))
        assertEquals(parseIso8601Millis("  2026-08-20  "), parseIso8601Millis("2026-08-20"))
    }

    @Test fun rejectsMalformedDates() {
        val bad = listOf(
            null, "", "   ", "not-a-date", "2026-13-45T00:00:00+06:00", "2026-02-30",
            "20-08-2026", "2026/08/20", "2026-08-20junk", "T00:00:00", "0", "null",
            "2026-08-20T25:00:00+06:00", "<script>alert(1)</script>"
        )
        bad.forEach { assertNull("expected null for <$it>", parseIso8601Millis(it)) }
    }

    @Test fun comparesChronologicallyNotLexically() {
        // "2026-1-05..." sorts before "2026-01-06..." as a string; as instants the order flips.
        val earlier = parseIso8601Millis("2026-01-06T00:00:00+06:00")!!
        val sameMomentDifferentZone = parseIso8601Millis("2026-01-05T19:00:00+01:00")!!
        assertEquals(earlier, sameMomentDifferentZone)
    }

    // ---------- URL validation ----------

    @Test fun acceptsOnlyHttpsUrls() {
        assertEquals("https://example.com/a", safeHttpsUrlOrNull("https://example.com/a"))
        assertEquals("https://example.com", safeHttpsUrlOrNull("  https://example.com  "))
        assertNotNull(safeHttpsUrlOrNull("HTTPS://Example.com/a"))
    }

    @Test fun rejectsUnsafeUrls() {
        val bad = listOf(
            "", "   ", "http://example.com", "javascript:alert(1)", "data:text/html,<b>x</b>",
            "intent://scan#Intent;scheme=zxing;end", "file:///etc/hosts", "content://settings",
            "market://details?id=x", "//example.com/a", "https://", "https:///path",
            "example.com", "ftp://example.com", "JavaScript:alert(1)"
        )
        bad.forEach { assertNull("expected null for <$it>", safeHttpsUrlOrNull(it)) }
    }

    // ---------- Marquee gating ----------

    @Test fun marqueeVisibilityRules() {
        assertFalse(MarqueeConfig(enabled = false, text = "hi").shouldShow())
        assertFalse(MarqueeConfig(enabled = true, text = null).shouldShow())
        assertFalse(MarqueeConfig(enabled = true, text = "   ").shouldShow())
        assertTrue(MarqueeConfig(enabled = true, text = "আপনার অভিযোগ").shouldShow())
    }

    @Test fun marqueeDropsUnsafeClickUrl() {
        assertNull(MarqueeConfig(true, "x", "http://a.com").safeClickUrl)
        assertNull(MarqueeConfig(true, "x", "javascript:alert(1)").safeClickUrl)
        assertNull(MarqueeConfig(true, "x", null).safeClickUrl)
        assertEquals("https://a.com", MarqueeConfig(true, "x", "https://a.com").safeClickUrl)
    }

    // ---------- Advertisement scheduling ----------

    private val image = "https://example.com/ad.webp"
    private val start = "2026-08-20T00:00:00+06:00"   // 1787162400000
    private val end = "2026-09-20T23:59:59+06:00"     // 1789927199000

    @Test fun adHiddenWhenDisabledOrUnusable() {
        assertFalse(AdvertisementConfig(enabled = false, imageUrl = image).shouldShow(1787162400001L))
        assertFalse(AdvertisementConfig(enabled = true, imageUrl = null).shouldShow(1787162400001L))
        assertFalse(AdvertisementConfig(enabled = true, imageUrl = "").shouldShow(1787162400001L))
        assertFalse(AdvertisementConfig(enabled = true, imageUrl = "http://e.com/a.png").shouldShow(1787162400001L))
        assertFalse(AdvertisementConfig(enabled = true, imageUrl = "javascript:x").shouldShow(1787162400001L))
    }

    @Test fun adRespectsWindowBoundaries() {
        val ad = AdvertisementConfig(true, "id", image, null, start, end)
        assertFalse("one ms before start", ad.shouldShow(1787162399999L))
        assertTrue("exactly at start", ad.shouldShow(1787162400000L))
        assertTrue("mid window", ad.shouldShow(1788000000000L))
        assertTrue("exactly at end", ad.shouldShow(1789927199000L))
        assertFalse("one ms after end", ad.shouldShow(1789927199001L))
    }

    @Test fun adTreatsMissingBoundariesAsUnrestricted() {
        assertTrue(AdvertisementConfig(true, null, image).shouldShow(0L))
        assertTrue(AdvertisementConfig(true, null, image).shouldShow(Long.MAX_VALUE))
        assertTrue(AdvertisementConfig(true, null, image, null, start, null).shouldShow(Long.MAX_VALUE))
        assertFalse(AdvertisementConfig(true, null, image, null, start, null).shouldShow(0L))
        assertTrue(AdvertisementConfig(true, null, image, null, null, end).shouldShow(0L))
        assertFalse(AdvertisementConfig(true, null, image, null, null, end).shouldShow(Long.MAX_VALUE))
        // Blank strings are treated the same as absent, not as malformed.
        assertTrue(AdvertisementConfig(true, null, image, null, "", "  ").shouldShow(1788000000000L))
    }

    @Test fun adHiddenWhenDateIsMalformed() {
        assertFalse(AdvertisementConfig(true, null, image, null, "yesterday", end).shouldShow(1788000000000L))
        assertFalse(AdvertisementConfig(true, null, image, null, start, "2026-99-99").shouldShow(1788000000000L))
    }

    @Test fun expiredCampaignStaysHiddenEvenThoughConfigIsCached() {
        // The cached JSON is still perfectly valid; only the clock has moved past the window.
        val cachedJson = """
            {"advertisement":{"enabled":true,"image_url":"$image",
             "start_date":"$start","end_date":"$end"}}
        """.trimIndent()
        val ad = gson.fromJson(cachedJson, RemoteConfig::class.java).advertisement!!
        assertTrue(ad.shouldShow(1788000000000L))
        assertFalse(ad.shouldShow(1789927199001L))
    }

    // ---------- JSON hardening ----------

    @Test fun parsesTheDocumentedConfigShape() {
        val json = """
            {
              "marquee": {
                "enabled": true,
                "text": "আপনার অভিযোগ টিউশন এখানে যোগ করে আয় করুন",
                "click_url": "https://example.com"
              },
              "advertisement": {
                "enabled": true,
                "id": "tutor_connect_001",
                "image_url": "https://u.github.io/BillBoard/ads/tutor_connect.webp",
                "click_url": "https://example.com/tutor-connect",
                "start_date": "$start",
                "end_date": "$end"
              }
            }
        """.trimIndent()
        val config = gson.fromJson(json, RemoteConfig::class.java)
        assertTrue(config.marquee!!.enabled)
        assertTrue(config.marquee!!.text!!.contains("অভিযোগ"))
        assertEquals("https://example.com", config.marquee!!.safeClickUrl)
        assertEquals("tutor_connect_001", config.advertisement!!.id)
        assertTrue(config.advertisement!!.shouldShow(1788000000000L))
    }

    @Test fun ignoresUnknownFieldsAndMissingSections() {
        val json = """
            {"marquee":{"enabled":true,"text":"hi","future_field":{"a":[1,2]}},
             "brand_new_section":{"x":1}}
        """.trimIndent()
        val config = gson.fromJson(json, RemoteConfig::class.java)
        assertTrue(config.marquee!!.shouldShow())
        assertNull(config.advertisement)

        val empty = gson.fromJson("{}", RemoteConfig::class.java)
        assertNull(empty.marquee)
        assertNull(empty.advertisement)
    }

    @Test fun toleratesNullsAndWrongScalarTypes() {
        val json = """
            {"marquee":{"enabled":null,"text":null,"click_url":null},
             "advertisement":{"enabled":"yes","image_url":null}}
        """.trimIndent()
        val config = gson.fromJson(json, RemoteConfig::class.java)
        assertFalse(config.marquee!!.shouldShow())
        assertFalse(config.advertisement!!.shouldShow(1788000000000L))
    }

    @Test fun structurallyInvalidJsonThrowsSoTheRepositoryCanFallBack() {
        val broken = listOf(
            "{\"marquee\":", "[]", "not json at all", "{\"marquee\": \"a string\"}", "<html>404</html>"
        )
        broken.forEach { bad ->
            val threw = try {
                gson.fromJson(bad, RemoteConfig::class.java)
                false
            } catch (e: Exception) {
                true
            }
            assertTrue("expected a parse failure for <$bad>", threw)
        }
    }
}
