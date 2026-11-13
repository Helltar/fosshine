package com.helltar.fosshine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helltar.fosshine.R
import com.helltar.fosshine.protocol.Telemetry

@Composable
internal fun BatteryCard(t: Telemetry) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (t.soc ?: 0) / 100f },
                    modifier = Modifier.size(112.dp),
                    color = socColor(t.soc ?: 0),
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("${t.soc ?: "–"}%", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val charging = t.charging == true
                Text(
                    stringResource(if (charging) R.string.charging else R.string.discharging),
                    style = MaterialTheme.typography.titleMedium
                )
                t.remainingMinutes?.let {
                    val text =
                        if (it >= Telemetry.REMAINING_MAX) stringResource(R.string.remaining_max, it / 60)
                        else stringResource(R.string.remaining, it / 60, it % 60)
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
                t.batteryTempMax?.let {
                    Text(stringResource(R.string.battery_temp, it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun PowerRow(t: Telemetry) {
    // intrinsic height lets the shorter input card stretch to the output card's height
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
        PowerCard(
            title = stringResource(R.string.input),
            total = t.totalInputW,
            details = listOf(
                stringResource(R.string.source_ac) to "${t.acInputW ?: 0} W",
                stringResource(R.string.source_pv) to "${t.pvInputW ?: 0} W"
            ),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        PowerCard(
            title = stringResource(R.string.output),
            total = t.totalOutputW,
            details = listOf(
                stringResource(R.string.out_ac) to "${t.acOutputW ?: 0} W",
                stringResource(R.string.out_dc) to "${t.dcOutputW ?: 0} W",
                stringResource(R.string.out_usb) to "${t.usbOutputW ?: 0} W"
            ),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun PowerCard(title: String, total: Int?, details: List<Pair<String, String>>, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text("${total ?: "–"} W", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            details.forEach { (name, value) ->
                Row {
                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun socColor(soc: Int): Color = when {
    soc >= 60 -> Color(0xFF3DBA6F)
    soc >= 30 -> Color(0xFFE6C229)
    soc >= 15 -> Color(0xFFF08A24)
    else -> Color(0xFFE5484D)
}
