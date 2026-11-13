package com.helltar.fosshine

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.helltar.fosshine.link.StationLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the station link alive while the app is closed: holds it in the foreground, mirrors the station in
 * an ongoing notification and raises an alert when the battery runs low.
 */
class StationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var link: StationLink
    private lateinit var notifications: StationNotifications

    override fun onCreate() {
        super.onCreate()
        link = StationLink.get(this)
        notifications = StationNotifications(this)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notifications.status(link.server.state.value, null, stale = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        link.start()
        scope.launch { watch() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            link.stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (link.running.value) link.stop()
        // the watch loop may have re-posted the status just before the cancel, which would outlive the service
        notifications.cancelStatus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // a poll rather than collecting every frame: the station sends one a second, the notification needs less
    private suspend fun watch() {
        var lastShown: String? = null
        var lowAlerted = false

        while (scope.isActive) {
            val telemetry = link.server.telemetry.value
            val stale = SystemClock.elapsedRealtime() - link.server.lastFrameAt.value > STALE_AFTER_MS
            val notification = notifications.status(link.server.state.value, telemetry, stale)

            val shown = "${notification.extras.getCharSequence(Notification.EXTRA_TITLE)}|" +
                "${notification.extras.getCharSequence(Notification.EXTRA_TEXT)}"
            if (shown != lastShown) {
                notifications.update(notification)
                lastShown = shown
            }

            val soc = telemetry?.soc
            if (soc != null && !stale) {
                if (soc <= LOW_BATTERY && !lowAlerted && telemetry.charging != true) {
                    notifications.lowBattery(soc)
                    lowAlerted = true
                } else if (soc >= LOW_BATTERY + LOW_BATTERY_HYSTERESIS) {
                    lowAlerted = false
                }
            }

            delay(REFRESH)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.helltar.fosshine.STOP"

        private val REFRESH = 3.seconds
        private const val STALE_AFTER_MS = 10_000L
        private const val LOW_BATTERY = 20
        private const val LOW_BATTERY_HYSTERESIS = 5

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, StationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StationService::class.java))
        }
    }
}
