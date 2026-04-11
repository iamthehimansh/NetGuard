package com.netguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netguard.vpn.VpnConnectionManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("netguard_settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_start", false)) {
                VpnConnectionManager.start(context)
            }
        }
    }
}
