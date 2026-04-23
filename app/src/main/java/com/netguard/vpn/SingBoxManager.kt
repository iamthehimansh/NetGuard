package com.netguard.vpn

import android.net.VpnService
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
    private val service: NetGuardVpnService,
    private val vpnService: VpnService
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
            Log.i(TAG, "sing-box started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sing-box", e)
            isRunning = false
            onStatusChange?.invoke(false, "Error: ${e.message}")
        }
    }

    fun stop() {
        try { commandServer?.closeService() } catch (_: Exception) {}
        try { commandServer?.close() } catch (_: Exception) {}
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

    override fun openTun(options: TunOptions): Int {
        val builder = vpnService.javaClass.getMethod("newBuilder")
            .let { null } // VpnService.Builder is not directly accessible

        // Use reflection to call the protected Builder
        val builderObj = VpnService.Builder::class.java
            .getDeclaredConstructor(VpnService::class.java)
            .apply { isAccessible = true }
            .newInstance(vpnService)

        builderObj.setSession("NetGuard VPN")
        builderObj.setMtu(options.mtu)

        // IPv4 addresses
        val inet4Addr = options.inet4Address
        while (inet4Addr.hasNext()) {
            val prefix = inet4Addr.next()
            builderObj.addAddress(prefix.address(), prefix.prefix())
        }
        // IPv4 routes
        val inet4Route = options.inet4RouteAddress
        while (inet4Route.hasNext()) {
            val prefix = inet4Route.next()
            builderObj.addRoute(prefix.address(), prefix.prefix())
        }
        // IPv6 addresses
        val inet6Addr = options.inet6Address
        while (inet6Addr.hasNext()) {
            val prefix = inet6Addr.next()
            builderObj.addAddress(prefix.address(), prefix.prefix())
        }
        // IPv6 routes
        val inet6Route = options.inet6RouteAddress
        while (inet6Route.hasNext()) {
            val prefix = inet6Route.next()
            builderObj.addRoute(prefix.address(), prefix.prefix())
        }
        // DNS
        try {
            val dnsBox = options.dnsServerAddress
            val dnsAddr = dnsBox.value
            if (dnsAddr.isNotBlank()) builderObj.addDnsServer(dnsAddr)
        } catch (_: Exception) {}

        // Per-app routing from TunOptions
        try {
            val include = options.includePackage
            while (include.hasNext()) {
                try { builderObj.addAllowedApplication(include.next()) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        try {
            val exclude = options.excludePackage
            while (exclude.hasNext()) {
                try { builderObj.addDisallowedApplication(exclude.next()) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Always exclude ourselves
        try { builderObj.addDisallowedApplication(service.packageName) } catch (_: Exception) {}

        val tun = builderObj.establish()
            ?: throw Exception("Failed to establish VPN interface")

        return tun.detachFd()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        vpnService.protect(fd)
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
    override fun sendNotification(n: Notification) {}

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
