package com.butterflydevs.billboard.remote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.butterflydevs.billboard.data.remote.AdImageLoader
import com.butterflydevs.billboard.data.remote.AdvertisementConfig
import com.butterflydevs.billboard.data.remote.MarqueeConfig
import com.butterflydevs.billboard.ui.components.AdBanner
import com.butterflydevs.billboard.ui.components.MarqueeNotice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteWidgetsUiTest {

    @get:Rule val compose = createComposeRule()

    private val loader by lazy {
        AdImageLoader(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private val bengali = "আপনার অভিযোগ টিউশন এখানে যোগ করে আয় করুন"

    @Test fun marqueeShowsBengaliTextWhenEnabled() {
        compose.setContent {
            MarqueeNotice(MarqueeConfig(enabled = true, text = bengali), onClickUrl = {})
        }
        compose.onNodeWithText(bengali).assertIsDisplayed()
    }

    @Test fun disabledMarqueeOccupiesNoSpaceAtAll() {
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                MarqueeNotice(MarqueeConfig(enabled = false, text = bengali), onClickUrl = {})
            }
        }
        compose.onNodeWithText(bengali).assertDoesNotExist()
    }

    @Test fun marqueeWithBlankTextIsNotRendered() {
        compose.setContent {
            MarqueeNotice(MarqueeConfig(enabled = true, text = "   "), onClickUrl = {})
        }
        compose.onNodeWithText("   ").assertDoesNotExist()
    }

    @Test fun nullConfigRendersNothing() {
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                MarqueeNotice(null, onClickUrl = {})
                AdBanner(null, loader, onClickUrl = {})
            }
        }
        compose.onNodeWithContentDescription("Advertisement").assertDoesNotExist()
    }

    @Test fun adBannerIsAbsentWhenDisabledExpiredOrUnusable() {
        val image = "https://example.com/a.webp"
        val cases = listOf(
            AdvertisementConfig(enabled = false, imageUrl = image),
            AdvertisementConfig(enabled = true, imageUrl = null),
            AdvertisementConfig(enabled = true, imageUrl = "http://example.com/a.webp"),
            AdvertisementConfig(enabled = true, imageUrl = "javascript:alert(1)"),
            AdvertisementConfig(true, null, image, null, "2020-01-01", "2020-02-01"),
            AdvertisementConfig(true, null, image, null, "garbage", null)
        )
        // setContent may only be called once per test, so the config is swapped via state instead.
        val current = mutableStateOf(cases.first())
        compose.setContent { AdBanner(current.value, loader, onClickUrl = {}) }
        cases.forEach { config ->
            current.value = config
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Advertisement").assertDoesNotExist()
            compose.onRoot().assertHeightIsEqualTo(0.dp)
        }
    }

    @Test fun adBannerStaysEmptyWhileTheImageCannotBeDownloaded() {
        // Enabled, in-window, https — but the image will never resolve. No placeholder, no space.
        val config = AdvertisementConfig(
            enabled = true,
            imageUrl = "https://billboard-nonexistent-host.invalid/ad.webp"
        )
        compose.setContent { AdBanner(config, loader, onClickUrl = {}) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Advertisement").assertDoesNotExist()
    }
}
