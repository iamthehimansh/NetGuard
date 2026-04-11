package com.netguard.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object VpnConnectionManager {

    fun start(context: Context) {
        val intent = Intent(context, NetGuardVpnService::class.java).apply {
            action = NetGuardVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, NetGuardVpnService::class.java).apply {
            action = NetGuardVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Reload VPN rules without fully restarting.
     * Called when user toggles an app's block status.
     * Rebuilds the TUN with updated addDisallowedApplication() list.
     */
    fun reload(context: Context) {
        val intent = Intent(context, NetGuardVpnService::class.java).apply {
            action = NetGuardVpnService.ACTION_RELOAD
        }
        context.startService(intent)
    }
}
