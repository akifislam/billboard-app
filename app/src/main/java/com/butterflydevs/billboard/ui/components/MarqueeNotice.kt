package com.butterflydevs.billboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.butterflydevs.billboard.data.remote.MarqueeConfig
import com.butterflydevs.billboard.ui.theme.NoticeBg
import com.butterflydevs.billboard.ui.theme.NoticeText

/**
 * Remotely controlled notice strip that sits directly under the app bar.
 *
 * Renders nothing at all (not an empty box) when the remote config disables it or supplies no
 * text, so the meter list keeps the full screen. `basicMarquee` only animates when the text is
 * genuinely wider than the strip, and it needs no focus, so it never steals input from the list
 * or the FAB.
 */
@Composable
fun MarqueeNotice(
    config: MarqueeConfig?,
    onClickUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (config == null || !config.shouldShow()) return
    val text = config.text ?: return
    val clickUrl = config.safeClickUrl

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = NoticeText,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NoticeBg)
            .then(
                // Only clickable when the remote config supplied a usable https link.
                if (clickUrl != null) Modifier.clickable { onClickUrl(clickUrl) } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .basicMarquee(iterations = Int.MAX_VALUE)
    )
}
