package com.helltar.fosshine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.helltar.fosshine.link.StationServer
import com.helltar.fosshine.protocol.Telemetry

/** The service's ongoing status notification and the alerts it raises. */
class StationNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun status(link: StationServer.State, telemetry: Telemetry?, stale: Boolean): Notification {
        val (title, text) = when {
            link !is StationServer.State.Connected -> context.getString(R.string.notif_waiting) to null
            telemetry == null || stale -> context.getString(R.string.notif_no_data) to null
            else -> statusLines(telemetry)
        }

        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.notif_stop), stopService())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun update(notification: Notification) = post(StationService.NOTIFICATION_ID, notification)

    fun cancelStatus() = manager.cancel(StationService.NOTIFICATION_ID)

    fun lowBattery(soc: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alert_low_title, soc))
            .setContentText(context.getString(R.string.alert_low_text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        post(LOW_BATTERY_ID, notification)
    }

    private fun statusLines(t: Telemetry): Pair<String, String> {
        val state = context.getString(if (t.charging == true) R.string.charging else R.string.discharging)
        val title = "${t.soc ?: "–"}% · $state"

        val power = context.getString(R.string.notif_power, t.totalInputW ?: 0, t.totalOutputW ?: 0)
        val remaining = t.remainingMinutes?.let {
            if (it >= Telemetry.REMAINING_MAX) context.getString(R.string.remaining_max, it / 60)
            else context.getString(R.string.remaining, it / 60, it % 60)
        }
        return title to listOfNotNull(power, remaining).joinToString(" · ")
    }

    private fun post(id: Int, notification: Notification) {
        // without the permission (Android 13+) the system drops it anyway; the check keeps lint honest
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) manager.notify(id, notification)
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopService(): PendingIntent =
        PendingIntent.getService(
            context, 0,
            Intent(context, StationService::class.java).setAction(StationService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_ALERTS = "alerts"
        const val LOW_BATTERY_ID = 2
    }
}
