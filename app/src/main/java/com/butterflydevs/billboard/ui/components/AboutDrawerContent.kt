package com.butterflydevs.billboard.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butterflydevs.billboard.BuildConfig
import com.butterflydevs.billboard.R
import com.butterflydevs.billboard.ui.theme.BrandPrimary

private const val FACEBOOK_URL = "https://www.facebook.com/iamakifislam/"
private const val LINKEDIN_URL = "https://www.linkedin.com/in/akifislam"
private const val EMAIL_ADDRESS = "iamakifislam@gmail.com"

private const val BUTTERFLY_FACEBOOK_URL = "https://www.facebook.com/butterflydevs"
private const val BUTTERFLY_EMAIL_ADDRESS = "butterflydev7@gmail.com"
private const val BUTTERFLY_WEBSITE_URL = "https://butterflydevs.web.app"
private const val BUTTERFLY_WHATSAPP_URL = "https://wa.me/8801580302857"

private val FacebookBlue = Color(0xFF1877F2)
private val LinkedInBlue = Color(0xFF0A66C2)
private val WhatsAppGreen = Color(0xFF25D366)

@Composable
fun AboutDrawerContent() {
    val context = LocalContext.current
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    fun openEmail(address: String) {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "About the App",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(id = R.drawable.billboard_app),
            contentDescription = "BillBoard",
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "BillBoard",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BrandPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Developed by Akif Islam",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SocialLink(
                label = "f",
                background = FacebookBlue,
                onClick = { openUrl(FACEBOOK_URL) }
            )
            SocialLink(
                label = "in",
                background = LinkedInBlue,
                onClick = { openUrl(LINKEDIN_URL) }
            )
            IconCircle(
                icon = Icons.Default.Email,
                background = BrandPrimary,
                contentDescription = "Email",
                onClick = { openEmail(EMAIL_ADDRESS) }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = EMAIL_ADDRESS,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "A product of",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Butterfly Devs",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = BrandPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SocialLink(
                label = "f",
                background = FacebookBlue,
                onClick = { openUrl(BUTTERFLY_FACEBOOK_URL) }
            )
            IconCircle(
                icon = Icons.Default.Email,
                background = BrandPrimary,
                contentDescription = "Email",
                onClick = { openEmail(BUTTERFLY_EMAIL_ADDRESS) }
            )
            IconCircle(
                icon = Icons.Default.Language,
                background = BrandPrimary,
                contentDescription = "Website",
                onClick = { openUrl(BUTTERFLY_WEBSITE_URL) }
            )
            IconCircle(
                icon = Icons.Default.Chat,
                background = WhatsAppGreen,
                contentDescription = "WhatsApp",
                onClick = { openUrl(BUTTERFLY_WHATSAPP_URL) }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = BUTTERFLY_EMAIL_ADDRESS,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SocialLink(label: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IconCircle(icon: ImageVector, background: Color, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}
