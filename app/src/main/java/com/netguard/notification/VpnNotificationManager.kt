package com.netguard.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.netguard.MainActivity
import com.netguard.NetGuardApp
import com.netguard.vpn.NetGuardVpnService

class VpnNotificationManager(private val context: Context) {

    companion object {
        const val NOTIF_ID = 1
    }

    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun buildInitialNotification(): Notification {
        return buildNotification("NetGuard Active", "Monitoring network traffic")
    }

    fun updateStats(blocked: Int, allowed: Int) {
        val notification = buildNotification(
            "NetGuard Active",
            "Blocked: $blocked | Allowed: $allowed"
        )
        notificationManager.notify(NOTIF_ID, notification)
    }

    fun updateText(title: String, text: String = "") {
        notificationManager.notify(NOTIF_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context, 1,
            Intent(context, NetGuardVpnService::class.java).apply {
                action = NetGuardVpnService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NetGuardApp.VPN_NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }
}
