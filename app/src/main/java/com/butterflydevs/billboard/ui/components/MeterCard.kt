package com.butterflydevs.billboard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.butterflydevs.billboard.data.MeterProvider
import com.butterflydevs.billboard.data.MeterType
import com.butterflydevs.billboard.data.NescoPostpaidMeterData
import com.butterflydevs.billboard.ui.theme.BalanceLow
import com.butterflydevs.billboard.ui.theme.BalanceOk
import com.butterflydevs.billboard.ui.theme.DescoAccent
import com.butterflydevs.billboard.ui.theme.DescoCardBg
import com.butterflydevs.billboard.ui.theme.NescoAccent
import com.butterflydevs.billboard.ui.theme.NescoCardBg
import com.butterflydevs.billboard.ui.theme.PgclAccent
import com.butterflydevs.billboard.ui.theme.PgclCardBg
import com.butterflydevs.billboard.viewmodel.MeterCardState
import kotlin.math.roundToInt

private const val LOW_THRESHOLD = 150.0
private const val SWIPE_REVEAL_DP = 72

private fun cardBg(provider: MeterProvider): Color = when (provider) {
    MeterProvider.NESCO -> NescoCardBg
    MeterProvider.DESCO -> DescoCardBg
    MeterProvider.PGCL -> PgclCardBg
}

private fun cardAccent(provider: MeterProvider): Color = when (provider) {
    MeterProvider.NESCO -> NescoAccent
    MeterProvider.DESCO -> DescoAccent
    MeterProvider.PGCL -> PgclAccent
}

@Composable
fun MeterCard(meter: MeterCardState, onRemove: () -> Unit, onOpenDetails: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val revealPx = with(LocalDensity.current) { SWIPE_REVEAL_DP.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "cardOffset")

    fun collapse() {
        offsetX = 0f
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.padding(end = 20.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete meter", tint = BalanceLow)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(meter.meter) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < -revealPx / 2) -revealPx else 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-revealPx, 0f)
                        }
                    )
                },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg(meter.provider)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = meter.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = meter.meter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meter.provider.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(cardAccent(meter.provider), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (meter.isLoading) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Fetching data…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
            } else if (meter.error != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = BalanceLow
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = meter.error,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (meter.provider == MeterProvider.NESCO && meter.billingType == MeterType.POSTPAID) {
                NescoPostpaidCardBody(meter)
            } else if (meter.provider == MeterProvider.PGCL) {
                PgclCardBody(meter)
            } else {
                ElectricityCardBody(meter)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) {
                Text("Meter Details")
            }
        }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Remove meter?") },
            text = {
                Text("Do you really want to delete \"${meter.nickname}\" (${meter.meter})? This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    collapse()
                    onRemove()
                }) {
                    Text("Delete", color = BalanceLow)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    collapse()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ElectricityCardBody(meter: MeterCardState) {
    Spacer(Modifier.height(14.dp))
    val isLow = meter.balance != null && meter.balance < LOW_THRESHOLD
    Text(
        text = formatTaka(meter.balance),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = if (isLow) BalanceLow else BalanceOk
    )
    if (isLow) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "LOW BALANCE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .background(BalanceLow, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
    if (!meter.balanceAsOf.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "as of ${meter.balanceAsOf}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))

    InfoRow("Last Recharge", meter.lastRechargeDate?.ifBlank { null } ?: "—")
    InfoRow("Amount", formatTaka(meter.lastRechargeAmount))
    val customerName = meter.name ?: meter.descoData?.customerName
    if (!customerName.isNullOrBlank()) {
        InfoRow("Customer", customerName)
    }
    InfoRow("Estimated to last", formatDaysRemaining(meter.predictionDaysRemaining))
}

/** Returns a due-status headline plus whether it should render as "due" (red) vs "clear" (green). */
fun postpaidDueSummary(data: NescoPostpaidMeterData?): Pair<String, Boolean> {
    val dueBills = data?.dueBills.orEmpty()
    if (dueBills.isEmpty()) return "No due bills" to false
    val total = dueBills.sumOf { it.totalBillTaka ?: 0.0 }
    val plural = if (dueBills.size == 1) "" else "s"
    return "${formatTaka(total)} due (${dueBills.size} bill$plural)" to true
}

@Composable
private fun NescoPostpaidCardBody(meter: MeterCardState) {
    val data = meter.nescoPostpaidData
    Spacer(Modifier.height(14.dp))
    val (message, isDue) = postpaidDueSummary(data)
    Text(
        text = message,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (isDue) BalanceLow else BalanceOk
    )

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))

    val lastBill = data?.allBills?.firstOrNull()
    InfoRow("Last Bill", lastBill?.let { "${it.month} ${it.year}" } ?: "—")
    InfoRow("Total Bill", formatTaka(lastBill?.totalBillTaka))
    InfoRow("Status", lastBill?.paymentStatus ?: "—")
    if (!data?.name.isNullOrBlank()) {
        InfoRow("Customer", data?.name ?: "—")
    }
}

@Composable
private fun PgclCardBody(meter: MeterCardState) {
    val data = meter.pgclData
    Spacer(Modifier.height(14.dp))
    val dueMessage = data?.dueStatusMessage ?: "—"
    val isDue = !dueMessage.contains("no due", ignoreCase = true)
    Text(
        text = dueMessage,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (isDue) BalanceLow else BalanceOk
    )

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))

    val lastPayment = data?.payments?.firstOrNull()
    InfoRow("Last Payment", formatTaka(lastPayment?.total))
    InfoRow("Bill Month", lastPayment?.billMonth ?: "—")
    InfoRow("Collection Date", lastPayment?.collectionDate ?: "—")
    if (!data?.customerName.isNullOrBlank()) {
        InfoRow("Customer", data?.customerName ?: "—")
    }
}
