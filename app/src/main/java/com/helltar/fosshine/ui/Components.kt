package com.helltar.fosshine.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.helltar.fosshine.R
import kotlin.math.roundToInt

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled && !busy)
    }
}

@Composable
internal fun Choice(labels: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { if (index != selected) onSelect(index) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, labels.size)
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ValueSlider(
    title: String,
    unit: String,
    value: Int?,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    onCommit: (Int) -> Unit
) {
    // follows the station until the user grabs it, then keeps the dragged value until it is committed
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (dragging.isNaN()) (value ?: range.first).coerceIn(range).toFloat() else dragging

    Column {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            // the station's own number while it is not being dragged, even one outside the range the slider can show
            val label = if (dragging.isNaN()) value?.toString() ?: "–" else shown.roundToInt().toString()
            Text("$label $unit", style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                // a tap that lands on the current value finishes a gesture that never changed anything
                if (dragging.isNaN()) return@Slider
                val picked = snap(dragging, range, step)
                dragging = Float.NaN
                if (picked != value) onCommit(picked)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            enabled = enabled,
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
        )
    }
}

/**
 * A setting picked from a list, whose register value is the chosen index. Picking "never" asks first where
 * the Fossibot+ app does, since it leaves the battery to drain.
 */
@Composable
internal fun OptionRow(
    title: String,
    hint: String?,
    options: List<Int>,
    @StringRes never: Int,
    selected: Int?,
    @StringRes confirmNever: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val labels = options.map { formatDuration(it, never) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { picking = true }
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            selected?.let { labels.getOrNull(it) } ?: "–",
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(title) },
            text = {
                Column {
                    labels.forEachIndexed { index, label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picking = false
                                    when {
                                        index == selected -> Unit
                                        index == 0 && confirmNever != null -> confirming = true
                                        else -> onSelect(index)
                                    }
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(selected = index == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (confirming && confirmNever != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(title) },
            text = { Text(stringResource(confirmNever)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onSelect(0)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
internal fun InfoRow(name: String, value: String?) {
    Row {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value ?: "–", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun formatDuration(seconds: Int, @StringRes never: Int): String = when {
    seconds == 0 -> stringResource(never)
    seconds < 60 -> stringResource(R.string.duration_seconds, seconds)
    seconds < 3600 -> stringResource(R.string.duration_minutes, seconds / 60)
    else -> stringResource(R.string.duration_hours, seconds / 3600)
}

private fun snap(value: Float, range: IntRange, step: Int): Int =
    (range.first + ((value - range.first) / step).roundToInt() * step).coerceIn(range)

// the system locale picks the decimal separator, the same for every reading on the screen
internal fun oneDecimal(value: Double?): String = "%.1f".format(value ?: 0.0)
