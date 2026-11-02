package com.butterflydevs.billboard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.butterflydevs.billboard.data.remote.AdImageLoader
import com.butterflydevs.billboard.data.remote.AdvertisementConfig

private val MAX_BANNER_HEIGHT = 96.dp

/**
 * Remotely controlled banner pinned to the bottom of the dashboard.
 *
 * Occupies zero height until an image has actually decoded, so a disabled ad, an expired campaign,
 * a dead image URL, or a slow first launch all leave the layout exactly as it was — Scaffold sees
 * an empty bottom bar and gives the meter list the full screen back. There is deliberately no
 * placeholder or spinner: a failed ad should be invisible, not a broken box.
 */
@Composable
fun AdBanner(
    config: AdvertisementConfig?,
    imageLoader: AdImageLoader,
    onClickUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (config == null || !config.shouldShow()) return
    val imageUrl = config.safeImageUrl ?: return
    val clickUrl = config.safeClickUrl

    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }

    // Keyed on the URL, so swapping campaigns remotely reloads but recomposition alone does not.
    // produceState is cancelled with the composition, so leaving the screen leaks no work.
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, imageUrl, screenWidthPx, imageLoader) {
        value = imageLoader.load(imageUrl, screenWidthPx)
    }

    val image = bitmap ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = image,
            contentDescription = "Advertisement",
            // Fit plus an explicit ratio: the banner spans the width it is given and its height
            // follows from the source dimensions, capped so it never eats into the meter list.
            // The bitmap is never stretched to fill.
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_BANNER_HEIGHT)
                .aspectRatioWithin(image.width.toFloat() / image.height.toFloat())
                .clip(RoundedCornerShape(12.dp))
                .then(if (clickUrl != null) Modifier.clickable { onClickUrl(clickUrl) } else Modifier)
        )
    }
}

/**
 * Sizes to the incoming max width at [ratio], shrinking the width instead if that would exceed the
 * incoming max height. Equivalent to `Modifier.aspectRatio`, spelled out here so the cap from
 * `heightIn` always wins and the bitmap is never distorted.
 */
private fun Modifier.aspectRatioWithin(ratio: Float): Modifier = layout { measurable, constraints ->
    val safeRatio = if (ratio.isFinite() && ratio > 0f) ratio else 1f
    var width = constraints.maxWidth
    var height = (width / safeRatio).toInt()
    if (constraints.hasBoundedHeight && height > constraints.maxHeight) {
        height = constraints.maxHeight
        width = (height * safeRatio).toInt().coerceAtMost(constraints.maxWidth)
    }
    val placeable = measurable.measure(
        Constraints.fixed(width.coerceAtLeast(0), height.coerceAtLeast(0))
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
