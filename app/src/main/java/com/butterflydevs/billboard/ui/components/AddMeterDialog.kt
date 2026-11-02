package com.butterflydevs.billboard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.butterflydevs.billboard.data.MeterProvider
import com.butterflydevs.billboard.data.MeterType

private fun forcedMeterType(provider: MeterProvider): MeterType? = when (provider) {
    MeterProvider.DESCO -> MeterType.PREPAID
    MeterProvider.PGCL -> MeterType.POSTPAID
    MeterProvider.NESCO -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeterDialog(
    isSubmitting: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onConfirm: (meterNo: String, nickname: String, provider: MeterProvider, meterType: MeterType) -> Unit
) {
    var provider by remember { mutableStateOf(MeterProvider.NESCO) }
    var meterType by remember { mutableStateOf(MeterType.PREPAID) }
    var meterNo by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val error = validationError ?: serverError
    val nicknameFocus = remember { FocusRequester() }

    val forcedType = forcedMeterType(provider)
    val effectiveMeterType = forcedType ?: meterType

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Add Meter") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MeterProvider.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = provider == option,
                            onClick = { provider = option; validationError = null },
                            enabled = !isSubmitting,
                            shape = SegmentedButtonDefaults.itemShape(index, MeterProvider.entries.size),
                            label = { Text(option.name) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (forcedType != null) 0.5f else 1f)
                ) {
                    MeterType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = effectiveMeterType == option,
                            onClick = { meterType = option; validationError = null },
                            enabled = !isSubmitting && forcedType == null,
                            shape = SegmentedButtonDefaults.itemShape(index, MeterType.entries.size),
                            label = { Text(if (option == MeterType.PREPAID) "Prepaid" else "Postpaid") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = meterNo,
                    onValueChange = { meterNo = it; validationError = null },
                    label = { Text(provider.hint) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { nicknameFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname (optional)") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nicknameFocus)
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    val trimmed = meterNo.trim()
                    if (trimmed.isEmpty()) {
                        validationError = "Please enter ${provider.hint.lowercase()}."
                    } else {
                        onConfirm(trimmed, nickname.trim(), provider, effectiveMeterType)
                    }
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add Meter")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
