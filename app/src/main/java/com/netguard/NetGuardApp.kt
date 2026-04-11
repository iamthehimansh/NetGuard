package com.netguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NetGuardApp : Application() {

    companion object {
        const val VPN_NOTIFICATION_CHANNEL = "vpn_status"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            VPN_NOTIFICATION_CHANNEL,
            "VPN Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows VPN firewall status and blocked connection counts"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
