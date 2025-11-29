package dev.abdus.apps.immich.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersComponent(
    createdAfter: String?,
    createdBefore: String?,
    onCreatedAfterChanged: (String?) -> Unit,
    onCreatedBeforeChanged: (String?) -> Unit
) {
    var createdAfterInput by remember { mutableStateOf(createdAfter ?: "") }
    var createdBeforeInput by remember { mutableStateOf(createdBefore ?: "") }

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    var showAfterPicker by remember { mutableStateOf(false) }
    var showBeforePicker by remember { mutableStateOf(false) }

    fun millisToDateString(ms: Long): String = sdf.format(Date(ms))

    val initialAfterMillis = try {
        sdf.parse(createdAfterInput)?.time
    } catch (_: Exception) { null }

    val initialBeforeMillis = try {
        sdf.parse(createdBeforeInput)?.time
    } catch (_: Exception) { null }

    val afterPickerState = rememberDatePickerState(initialSelectedDateMillis = initialAfterMillis)
    val beforePickerState = rememberDatePickerState(initialSelectedDateMillis = initialBeforeMillis)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Advanced filters")
            Text(text = "Taken at")

            OutlinedTextField(
                value = createdAfterInput,
                onValueChange = { /* read-only */ },
                label = { Text("After (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showAfterPicker = true }) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "Pick after date")
                    }
                }
            )

            OutlinedTextField(
                value = createdBeforeInput,
                onValueChange = { /* read-only */ },
                label = { Text("Before (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showBeforePicker = true }) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "Pick before date")
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val after = if (createdAfterInput.isBlank()) null else createdAfterInput
                    val before = if (createdBeforeInput.isBlank()) null else createdBeforeInput
                    onCreatedAfterChanged(after)
                    onCreatedBeforeChanged(before)
                }) {
                    Text("Apply")
                }
                Button(onClick = {
                    createdAfterInput = ""
                    createdBeforeInput = ""
                    onCreatedAfterChanged(null)
                    onCreatedBeforeChanged(null)
                }) {
                    Text("Clear")
                }
            }

            if (showAfterPicker) {
                DatePickerDialog(
                    onDismissRequest = { showAfterPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val ms = afterPickerState.selectedDateMillis
                            if (ms != null) {
                                createdAfterInput = millisToDateString(ms)
                            }
                            showAfterPicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAfterPicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = afterPickerState)
                }
            }

            if (showBeforePicker) {
                DatePickerDialog(
                    onDismissRequest = { showBeforePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val ms = beforePickerState.selectedDateMillis
                            if (ms != null) {
                                createdBeforeInput = millisToDateString(ms)
                            }
                            showBeforePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBeforePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = beforePickerState)
                }
            }
        }
    }
}
