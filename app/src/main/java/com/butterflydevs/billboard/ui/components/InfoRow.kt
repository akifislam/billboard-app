package com.butterflydevs.billboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

fun formatTaka(amount: Double?): String {
    if (amount == null) return "—"
    return "৳ " + String.format(java.util.Locale.US, "%.2f", amount)
}

fun formatDaysRemaining(days: Double?): String {
    if (days == null) return "Checking…"
    // 0 means "nothing left to run down" (empty or negative balance), which is a known answer —
    // not the "we don't have enough history yet" case that "Checking…" is for.
    if (days <= 0.0) return "No balance left"
    if (days < 1) return "Less than a day"
    val rounded = Math.round(days)
    return "~$rounded day${if (rounded == 1L) "" else "s"}"
}
