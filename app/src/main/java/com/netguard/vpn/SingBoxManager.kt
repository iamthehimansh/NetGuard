package com.netguard.vpn

import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

class SingBoxManager(
    private val service: NetGuardVpnService
) : CommandServerHandler, PlatformInterface {

    companion object {
        private const val TAG = "SingBoxManager"
    }

    private var commandServer: CommandServer? = null

    @Volatile
    var isRunning = false
        private set

    var onStatusChange: ((Boolean, String) -> Unit)? = null

    fun start(config: String, excludePackages: Set<String> = emptySet()) {
        if (isRunning) stop()
        try {
            Log.i(TAG, "Starting sing-box...")
            commandServer = CommandServer(this, this).also { server ->
                server.start()
                val options = OverrideOptions()
                if (excludePackages.isNotEmpty()) {
                    options.excludePackage = toStringIterator(excludePackages)
                }
                server.startOrReloadService(config, options)
            }
            isRunning = true
            onStatusChange?.invoke(true, "Connected")
            Log.i(TAG, "sing-box started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sing-box", e)
            isRunning = false
            onStatusChange?.invoke(false, "Error: ${e.message}")
        }
    }

    fun stop() {
        try { commandServer?.closeService() } catch (e: Exception) { Log.w(TAG, "closeService: ${e.message}") }
        try { commandServer?.close() } catch (e: Exception) { Log.w(TAG, "close: ${e.message}") }
        commandServer = null
        isRunning = false
        onStatusChange?.invoke(false, "Disconnected")
        Log.i(TAG, "sing-box stopped")
    }

    // ===== CommandServerHandler =====

    override fun getSystemProxyStatus(): SystemProxyStatus? = null
    override fun serviceReload() { Log.i(TAG, "Reload requested") }
    override fun serviceStop() {
        isRunning = false
        onStatusChange?.invoke(false, "Stopped")
    }
    override fun setSystemProxyEnabled(enabled: Boolean) {}
    override fun writeDebugMessage(message: String) { Log.d(TAG, message) }

    // ===== PlatformInterface =====

    /**
     * Called by libbox when it needs a TUN fd.
     * We delegate to NetGuardVpnService which can create a Builder directly.
     */
    override fun openTun(options: TunOptions): Int {
        Log.i(TAG, "openTun called by libbox, MTU=${options.mtu}")
        return service.createTunFd(options)
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        service.protect(fd)
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun useProcFS(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun underNetworkExtension(): Boolean = false
    override fun findConnectionOwner(p: Int, sa: String, sp: Int, da: String, dp: Int): ConnectionOwner? = null
    override fun clearDNSCache() {}
    override fun readWIFIState(): WIFIState? = null
    override fun startDefaultInterfaceMonitor(l: InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(l: InterfaceUpdateListener) {}
    override fun getInterfaces(): NetworkInterfaceIterator? = null
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun systemCertificates(): StringIterator? = null
    override fun sendNotification(n: Notification) {
        Log.d(TAG, "Notification from libbox")
    }

    private fun toStringIterator(set: Set<String>): StringIterator {
        val list = set.toList()
        return object : StringIterator {
            private var i = 0
            override fun hasNext(): Boolean = i < list.size
            override fun next(): String = list[i++]
            override fun len(): Int = list.size
        }
    }
}
