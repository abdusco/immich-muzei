package dev.abdus.apps.immich.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun FiltersComponent(
    // days-back value (e.g. 7 for last week). null means filter disabled.
    createdAfterDaysBack: Int?,
    onCreatedAfterChanged: (Int?) -> Unit
) {
    // Preset options and mapping to days back from today
    val presets = listOf("Today", "Last week", "2 weeks", "Last month", "2 months", "6 months")
    val daysBack = listOf(0, 7, 14, 30, 60, 180)

    // Try to infer initial index from createdAfterDaysBack if provided, otherwise default to Last week (index 1)
    val initialIndex = remember(createdAfterDaysBack) {
        try {
            if (createdAfterDaysBack != null) {
                val idx = daysBack.indexOf(createdAfterDaysBack)
                if (idx >= 0) idx else 1
            } else {
                1 // default: Last week
            }
        } catch (_: Exception) {
            1
        }
    }

    var sliderPosition by remember { mutableStateOf(initialIndex.toFloat()) }
    var pendingIndex by remember { mutableStateOf(initialIndex) }
    var enabled by remember { mutableStateOf(createdAfterDaysBack != null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Taken since", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Show photos that were created after this date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Toggle for enabling/disabling the slider
                Switch(checked = enabled, onCheckedChange = { checked ->
                    enabled = checked
                    if (!checked) {
                        // when disabling, clear the filter immediately
                        onCreatedAfterChanged(null)
                        sliderPosition = 1f
                        pendingIndex = 1
                    } else {
                        // when enabling, immediately apply the current pending selection
                        val days = daysBack.getOrElse(pendingIndex) { 7 }
                        onCreatedAfterChanged(days)
                    }
                })
            }

            Spacer(modifier = Modifier.width(4.dp))

            Slider(
                value = sliderPosition,
                onValueChange = { value ->
                    // determine nearest index and snap the slider to that exact position immediately
                    sliderPosition = value

                },
                onValueChangeFinished = {
                    if (enabled) {
                        pendingIndex = sliderPosition.toInt().coerceIn(0, presets.lastIndex)
                        val days = daysBack.getOrElse(pendingIndex) { 7 }
                        onCreatedAfterChanged(days)
                    }
                },
                // Make slider non-interactive when filter is disabled
                enabled = enabled,
                valueRange = 0f..(presets.lastIndex).toFloat(),
                steps = presets.size - 2 // discrete steps between endpoints

            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                presets.forEachIndexed { idx, label ->
                    val isSelected = idx == pendingIndex
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Left,
                        )
                    }
                }
            }

            // Removed Apply / Clear buttons: changes are applied immediately when enabled
        }
    }
}
