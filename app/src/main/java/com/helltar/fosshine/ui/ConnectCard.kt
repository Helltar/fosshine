package com.helltar.fosshine.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.helltar.fosshine.R
import com.helltar.fosshine.link.HotspotConnector
import com.helltar.fosshine.link.StationBeacon
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** Where the connection stands, as one thing to tell the person: what is happening, and which step it is. */
private enum class Stage(@param:StringRes val title: Int, @param:StringRes val hint: Int, val step: Int, val busy: Boolean) {
    Idle(R.string.connect_idle, R.string.connect_idle_hint, 0, false),
    Starting(R.string.connect_starting, R.string.connect_starting_hint, 2, true),
    Joining(R.string.connect_joining, R.string.connect_joining_hint, 2, true),
    Waiting(R.string.connect_waiting, R.string.connect_waiting_hint, 2, true),
    Ready(R.string.connect_ready, R.string.connect_ready_hint, 2, true),
    Failed(R.string.connect_failed, R.string.connect_failed_hint, 1, false)
}

private fun stageOf(hotspot: HotspotConnector.State, beacon: StationBeacon.Phase): Stage = when {
    hotspot == HotspotConnector.State.Unavailable -> Stage.Failed
    // the station drops the phone on its way into offline mode, and HotspotConnector joins again at once
    beacon == StationBeacon.Phase.Ready && hotspot != HotspotConnector.State.Idle -> Stage.Ready
    beacon == StationBeacon.Phase.Starting && hotspot != HotspotConnector.State.Requesting -> Stage.Starting
    hotspot == HotspotConnector.State.Requesting || hotspot == HotspotConnector.State.Lost -> Stage.Joining
    hotspot == HotspotConnector.State.Connected -> Stage.Waiting
    else -> Stage.Idle
}

@Composable
internal fun ConnectCard(
    hotspot: HotspotConnector.State,
    beacon: StationBeacon.Phase,
    startingSince: Long?,
    joinHeld: Boolean,
    running: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit
) {
    val stage = stageOf(hotspot, beacon)
    // heard starting up before any tap: the station is on, and Connect is next
    val untapped = stage == Stage.Starting && hotspot == HotspotConnector.State.Idle && !joinHeld
    val step = if (untapped) 1 else stage.step

    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // while the station starts up, what its own display shows says more than the app's mark
        Crossfade(stage == Stage.Starting || stage == Stage.Ready, label = "hero") { onStation ->
            Box(Modifier.size(width = 168.dp, height = 84.dp), contentAlignment = Alignment.Center) {
                if (onStation) {
                    StationDisplay(ready = stage == Stage.Ready)
                } else {
                    val waveColor by animateColorAsState(
                        if (stage == Stage.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        label = "wave color"
                    )
                    Wave(moving = stage.busy, color = waveColor, modifier = Modifier.matchParentSize())
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(stage.title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (untapped) R.string.connect_starting_tap else stage.hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // the count belongs to a wait the person asked for: before a tap, and after a cancel, there is none
        if (stage == Stage.Starting && startingSince != null && !untapped) {
            Spacer(Modifier.height(20.dp))
            StartupProgress(startingSince)
        }

        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(R.string.connect_step_1, R.string.connect_step_2, R.string.connect_step_3).forEachIndexed { i, text ->
                Step(i + 1, stringResource(text), done = i < step, current = i == step)
            }
        }

        Spacer(Modifier.height(32.dp))
        // while a join is held, under way or done another tap could only restart it, so cancel takes the button's
        // place; a join that failed shows both, since the service is still waiting behind it
        val joining = joinHeld || hotspot == HotspotConnector.State.Requesting || hotspot == HotspotConnector.State.Connected
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!joining) {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        stringResource(if (stage == Stage.Failed) R.string.connect_retry else R.string.connect_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (joining || running) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Help()
    }
}

/**
 * The two icons on the station's own display: Wi-Fi blinks while it starts up, Bluetooth once it is ready
 * for the phone. Shown as the station shows them, so the person knows what to look for there.
 */
@Composable
private fun StationDisplay(ready: Boolean) {
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    val colors = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            painterResource(R.drawable.ic_wifi),
            contentDescription = null,
            tint = if (ready) colors.outlineVariant else colors.primary,
            modifier = Modifier.size(48.dp).alpha(if (ready) 1f else blink)
        )
        Canvas(Modifier.size(width = 32.dp, height = 16.dp)) {
            val stroke = 2.dp.toPx()
            val y = size.height / 2
            drawLine(colors.outline, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            drawLine(colors.outline, Offset(size.width - y, 0f), Offset(size.width, y), stroke, StrokeCap.Round)
            drawLine(colors.outline, Offset(size.width - y, size.height), Offset(size.width, y), stroke, StrokeCap.Round)
        }
        Icon(
            painterResource(R.drawable.ic_bluetooth),
            contentDescription = null,
            tint = if (ready) colors.primary else colors.outlineVariant,
            modifier = Modifier.size(48.dp).alpha(if (ready) blink else 1f)
        )
    }
}

/**
 * How long the station has been starting up, against how long that usually takes, so a wait of a minute or
 * two reads as progress rather than as a hang. Past the usual time the bar stops claiming to know.
 */
@Composable
private fun StartupProgress(since: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1.seconds)
        }
    }

    val elapsed = ((now - since) / 1_000).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth(0.7f), horizontalAlignment = Alignment.CenterHorizontally) {
        if (elapsed < USUAL_STARTUP_S) {
            LinearProgressIndicator(progress = { elapsed.toFloat() / USUAL_STARTUP_S }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.connect_starting_elapsed, elapsed / 60, elapsed % 60),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// the setup phase took a little over two minutes on the F1800, counted from when the app first heard it
private const val USUAL_STARTUP_S = 150L

/** The app's mark, one and a half periods of alternating current, running while something is under way. */
@Composable
private fun Wave(moving: Boolean, color: Color, modifier: Modifier) {
    val phase = if (moving) {
        val transition = rememberInfiniteTransition(label = "wave")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
            label = "phase"
        ).value
    } else 0f
    val amplitude by animateFloatAsState(if (moving) 1f else 0.7f, label = "amplitude")

    Canvas(modifier) {
        val stroke = size.height * 0.09f
        val middle = size.height / 2
        val swing = (size.height / 2 - stroke) * amplitude
        val points = 120

        val path = Path()
        for (i in 0..points) {
            val x = stroke / 2 + (size.width - stroke) * i / points
            val y = middle - swing * sin(3f * PI.toFloat() * i / points - phase)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun Step(number: Int, text: String, done: Boolean, current: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        val marker = Modifier.size(32.dp)
        Box(
            when {
                done -> marker.background(colors.primary, CircleShape)
                current -> marker.background(colors.primaryContainer, CircleShape)
                else -> marker.border(1.5.dp, colors.outlineVariant, CircleShape)
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else "$number",
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    done -> colors.onPrimary
                    current -> colors.onPrimaryContainer
                    else -> colors.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (current) colors.onSurface else colors.onSurfaceVariant
        )
    }
}

/** What to try when it does not connect, and why the app asks for its permissions: there when asked for. */
@Composable
private fun Help() {
    var open by rememberSaveable { mutableStateOf(false) }

    TextButton(onClick = { open = !open }, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.connect_help))
    }

    AnimatedVisibility(open) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(R.string.connect_trouble, R.string.connect_permissions).forEach {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

