package com.helltar.fosshine.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helltar.fosshine.R
import com.helltar.fosshine.StationViewModel
import com.helltar.fosshine.link.StationServer
import com.helltar.fosshine.protocol.Model
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val STALE_AFTER_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(viewModel: StationViewModel, onConnect: () -> Unit) {
    val link by viewModel.server.state.collectAsStateWithLifecycle()
    val telemetry by viewModel.server.telemetry.collectAsStateWithLifecycle()
    val lastFrameAt by viewModel.server.lastFrameAt.collectAsStateWithLifecycle()
    val hotspot by viewModel.hotspot.state.collectAsStateWithLifecycle()
    val serial by viewModel.hotspot.serial.collectAsStateWithLifecycle()
    val beacon by viewModel.beacon.phase.collectAsStateWithLifecycle()
    val startingSince by viewModel.beacon.startingSince.collectAsStateWithLifecycle()
    val joinHeld by viewModel.joinHeld.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            now = SystemClock.elapsedRealtime()
        }
    }

    val connected = link is StationServer.State.Connected
    val stale = lastFrameAt == 0L || now - lastFrameAt > STALE_AFTER_MS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(modelTitle(serial))) },
                // before the station connects the connect card tells the story; a port failure still shows
                actions = { if (connected || link is StationServer.State.Failed) LinkBadge(link, stale) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!connected) {
                // listens only while the card is visible and the app is in front
                LifecycleStartEffect(Unit) {
                    viewModel.watchBeacon()
                    onStopOrDispose { viewModel.stopBeacon() }
                }
                ConnectCard(hotspot, beacon, startingSince, joinHeld, running, onConnect = onConnect, onCancel = viewModel::disconnect)
            }

            // the last frames of a link that has gone would read as the station now, so they go with it
            val t = telemetry
            if (t != null && connected) {
                val controls = Controls(pending, enabled = !stale, write = viewModel::write)
                BatteryCard(t)
                PowerRow(t)
                OutputsCard(t, controls)
                LightCard(t, controls)
                ChargingCard(t, controls, serial)
                TimersCard(t, controls)
                DeviceCard(t, controls, serial, viewModel::setKeySound)
            }

            // until the station connects there is nothing to disconnect from, only a wait the connect card cancels
            if (running && connected) {
                OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.disconnect))
                }
            }
        }
    }
}

// the model only shows when the serial names it; without one the ranges are the F1800's, the name is not
@StringRes
private fun modelTitle(serial: String?): Int = when {
    Model.isF3000(serial) -> R.string.title_f3000
    Model.isF1800(serial) -> R.string.title_f1800
    else -> R.string.title
}

/** What every control needs: whether it may write now, and which registers are mid-write. */
internal class Controls(val pending: Set<Int>, val enabled: Boolean, val write: (Int, Int) -> Unit) {
    fun enabled(key: Int): Boolean = enabled && key !in pending
}

@Composable
private fun LinkBadge(link: StationServer.State, stale: Boolean) {
    val (color, text) = when (link) {
        is StationServer.State.Connected ->
            if (stale) Color(0xFFE0A030) to stringResource(R.string.link_stale)
            else Color(0xFF3DBA6F) to stringResource(R.string.link_connected)
        StationServer.State.Listening -> Color(0xFF9E9E9E) to stringResource(R.string.link_waiting)
        StationServer.State.Stopped -> Color(0xFF9E9E9E) to stringResource(R.string.link_stopped)
        is StationServer.State.Failed -> Color(0xFFE05050) to stringResource(R.string.link_failed)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
