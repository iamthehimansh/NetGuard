package com.netguard.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.netguard.data.AppDatabase
import com.netguard.data.TrafficLogWriter
import com.netguard.dns.BlocklistManager
import com.netguard.dns.DnsPacketParser
import com.netguard.dns.DomainTrie
import com.netguard.notification.VpnNotificationManager
import com.netguard.uid.UidResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class NetGuardVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.netguard.START_VPN"
        const val ACTION_STOP = "com.netguard.STOP_VPN"
        const val ACTION_RELOAD = "com.netguard.RELOAD_VPN"
        private const val TAG = "NetGuardVpnService"

        private val DNS_ROUTES = listOf(
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var trafficLogWriter: TrafficLogWriter? = null
    private var notificationManager: VpnNotificationManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var uidResolver: UidResolver? = null
    private var singBoxManager: SingBoxManager? = null

    private val domainTrie = DomainTrie()
    private var blockedCount = 0
    private var allowedCount = 0
    private var blockedAppUids = setOf<Int>()
    private var blockedPackageNames = setOf<String>()
    private var upstreamDns = "8.8.8.8"
    private var hasBlockedApps = false
    private var currentMode = TunnelMode.DNS_FILTER_ONLY

    private val domainUidCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()

    @Volatile
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                serviceScope.launch { reloadAndRestart() }
                return START_STICKY
            }
            else -> {
                notificationManager = VpnNotificationManager(this)
                startForeground(
                    VpnNotificationManager.NOTIF_ID,
                    notificationManager!!.buildInitialNotification()
                )
                serviceScope.launch { startVpn() }
            }
        }
        return START_STICKY
    }

    private suspend fun startVpn() {
        if (running) return
        running = true

        try {
            val db = AppDatabase.getInstance(this)
            val prefs = getSharedPreferences("netguard_settings", Context.MODE_PRIVATE)
            upstreamDns = prefs.getString("dns_server", "8.8.8.8") ?: "8.8.8.8"
            currentMode = try {
                TunnelMode.valueOf(prefs.getString("tunnel_mode", TunnelMode.DNS_FILTER_ONLY.name)!!)
            } catch (_: Exception) { TunnelMode.DNS_FILTER_ONLY }

            trafficLogWriter = TrafficLogWriter(db.trafficLogDao(), db.trafficStatsDao(), serviceScope)
            uidResolver = UidResolver(this)

            val blocklistManager = BlocklistManager(this, db.domainRuleDao(), domainTrie)
            blocklistManager.loadBundledBlocklist()
            loadBlockedApps()

            when (currentMode) {
                TunnelMode.DNS_FILTER_ONLY -> {
                    Log.i(TAG, "Starting in DNS_FILTER_ONLY mode")
                    establishDnsOnlyTunnel()
                    startDnsPacketLoop()
                    notificationManager?.updateText("NetGuard — DNS Filter Active")
                }
                TunnelMode.DIRECT -> {
                    Log.i(TAG, "Starting in DIRECT mode (VPN tunnel)")
                    startSingBoxDirect()
                    notificationManager?.updateText("NetGuard — VPN to ${getServerAddress()}")
                }
                TunnelMode.MANAGED -> {
                    Log.i(TAG, "Starting in MANAGED mode (filter + VPN)")
                    startSingBoxManaged()
                    notificationManager?.updateText("NetGuard — Managed VPN to ${getServerAddress()}")
                }
            }

            registerNetworkCallback()

        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed", e)
            stopVpn()
        }
    }

    // ==================== DNS_FILTER_ONLY MODE ====================
    // (existing NetGuard behavior — unchanged)

    private suspend fun loadBlockedApps() {
        val db = AppDatabase.getInstance(this)
        val blockedApps = db.appRuleDao().getAllBlocked()
        val pm = packageManager
        blockedPackageNames = blockedApps.map { it.packageName }.toSet()
        blockedAppUids = blockedApps.mapNotNull { rule ->
            try { pm.getApplicationInfo(rule.packageName, 0).uid } catch (_: Exception) { null }
        }.toSet()
        hasBlockedApps = blockedPackageNames.isNotEmpty()
        domainTrie.clear()
        BlocklistManager(this, AppDatabase.getInstance(this).domainRuleDao(), domainTrie).reloadTrieFromDb()
        Log.i(TAG, "Rules: ${blockedAppUids.size} blocked apps, ${domainTrie.size()} blocked domains")
    }

    private fun establishDnsOnlyTunnel() {
        tunInterface?.close()
        tunInterface = null

        val builder = Builder()
            .setSession("NetGuard")
            .addAddress("10.1.10.1", 32)
            .addDnsServer(upstreamDns)
            .setMtu(1500)
            .setBlocking(true)

        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        if (hasBlockedApps) {
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            builder.addAddress("fd00::1", 128)
            val installedApps = packageManager.getInstalledApplications(0)
            for (app in installedApps) {
                if (app.packageName == packageName) continue
                if (app.packageName in blockedPackageNames) continue
                try { builder.addDisallowedApplication(app.packageName) } catch (_: Exception) {}
            }
        } else {
            for (dns in DNS_ROUTES) {
                try { builder.addRoute(dns, 32) } catch (_: Exception) {}
            }
            if (upstreamDns !in DNS_ROUTES) {
                try { builder.addRoute(upstreamDns, 32) } catch (_: Exception) {}
            }
        }

        tunInterface = builder.establish()
        if (tunInterface == null) { Log.e(TAG, "Failed to establish VPN"); stopVpn() }
    }

    private fun startDnsPacketLoop() {
        Thread({
            try {
                while (running) {
                    val tun = tunInterface ?: break
                    val input = FileInputStream(tun.fileDescriptor)
                    val output = FileOutputStream(tun.fileDescriptor)
                    val buffer = ByteArray(32767)
                    try {
                        while (running) {
                            val length = input.read(buffer)
                            if (length <= 0) { Thread.sleep(10); continue }
                            if (length < 28) continue
                            val version = (buffer[0].toInt() shr 4) and 0xF
                            if (version != 4) continue
                            val protocol = buffer[9].toInt() and 0xFF
                            val ihl = (buffer[0].toInt() and 0xF) * 4
                            if (protocol == 17 && length > ihl + 8) {
                                val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
                                if (dstPort == 53) {
                                    val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
                                    val udpOff = ihl + 8
                                    val udpLen = length - udpOff
                                    if (udpLen >= 12) handleDnsQuery(buffer, udpOff, udpLen, buffer, ihl, length, output, srcPort)
                                    continue
                                }
                            }
                            if (hasBlockedApps) blockedCount++
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) { if (running) Log.e(TAG, "Packet loop error", e) }
        }, "DnsLoop").start()
    }

    // ==================== DIRECT MODE ====================
    // All traffic tunneled via sing-box, no local filtering.
    // libbox creates the TUN itself via PlatformInterface.openTun()

    private suspend fun startSingBoxDirect() {
        val db = AppDatabase.getInstance(this)
        val serverConfig = db.vpnServerConfigDao().getConfig()
        if (serverConfig == null || serverConfig.uuid.isBlank()) {
            Log.e(TAG, "No VPN server configured. Falling back to DNS_FILTER_ONLY")
            currentMode = TunnelMode.DNS_FILTER_ONLY
            establishDnsOnlyTunnel()
            startDnsPacketLoop()
            return
        }

        val config = SingBoxConfigGenerator.generateDirectConfig(serverConfig)
        singBoxManager = SingBoxManager(this, this)
        singBoxManager?.start(config)
        startHealthProbe()
    }

    // ==================== MANAGED MODE ====================
    // Local DNS filter first, then tunnel allowed traffic via sing-box

    private suspend fun startSingBoxManaged() {
        val db = AppDatabase.getInstance(this)
        val serverConfig = db.vpnServerConfigDao().getConfig()
        if (serverConfig == null || serverConfig.uuid.isBlank()) {
            Log.e(TAG, "No VPN server configured. Falling back to DNS_FILTER_ONLY")
            currentMode = TunnelMode.DNS_FILTER_ONLY
            establishDnsOnlyTunnel()
            startDnsPacketLoop()
            return
        }

        val config = SingBoxConfigGenerator.generateManagedConfig(serverConfig)

        // Collect non-blocked packages to exclude from tunnel
        val pm = packageManager
        val allApps = pm.getInstalledApplications(0)
        val excludeFromTunnel = allApps
            .map { it.packageName }
            .filter { it !in blockedPackageNames && it != packageName }
            .toSet()

        singBoxManager = SingBoxManager(this, this)
        singBoxManager?.start(config, excludeFromTunnel)
        startHealthProbe()
    }

    // Managed mode uses libbox with excludePackage for blocked apps.
    // DNS goes through Pi-hole on the server. Per-app blocking via
    // OverrideOptions.excludePackage passed to sing-box start.

    // ==================== HEALTH PROBE ====================

    private fun startHealthProbe() {
        serviceScope.launch {
            var failures = 0
            while (running && currentMode != TunnelMode.DNS_FILTER_ONLY) {
                delay(30_000)
                // TODO: HTTP GET to https://www.gstatic.com/generate_204 through tunnel
                // For now, just check if sing-box is alive
                if (singBoxManager?.isRunning != true) {
                    failures++
                    if (failures >= 3) {
                        Log.w(TAG, "Health probe: 3 consecutive failures, reconnecting")
                        reloadAndRestart()
                        failures = 0
                    }
                } else {
                    failures = 0
                }
            }
        }
    }

    // ==================== SHARED HELPERS ====================

    private suspend fun reloadAndRestart() {
        Log.i(TAG, "Reloading and restarting...")
        singBoxManager?.stop()
        running = false
        loadBlockedApps()
        running = true

        when (currentMode) {
            TunnelMode.DNS_FILTER_ONLY -> {
                val prefs = getSharedPreferences("netguard_settings", Context.MODE_PRIVATE)
                currentMode = try {
                    TunnelMode.valueOf(prefs.getString("tunnel_mode", TunnelMode.DNS_FILTER_ONLY.name)!!)
                } catch (_: Exception) { TunnelMode.DNS_FILTER_ONLY }
            }
            else -> {}
        }

        // Re-read mode in case it changed
        val prefs = getSharedPreferences("netguard_settings", Context.MODE_PRIVATE)
        currentMode = try {
            TunnelMode.valueOf(prefs.getString("tunnel_mode", TunnelMode.DNS_FILTER_ONLY.name)!!)
        } catch (_: Exception) { TunnelMode.DNS_FILTER_ONLY }

        when (currentMode) {
            TunnelMode.DNS_FILTER_ONLY -> {
                establishDnsOnlyTunnel()
                startDnsPacketLoop()
                notificationManager?.updateText("NetGuard — DNS Filter Active")
            }
            TunnelMode.DIRECT -> {
                startSingBoxDirect()
                notificationManager?.updateText("NetGuard — VPN to ${getServerAddress()}")
            }
            TunnelMode.MANAGED -> {
                startSingBoxManaged()
                notificationManager?.updateText("NetGuard — Managed VPN to ${getServerAddress()}")
            }
        }
    }

    private suspend fun getServerAddress(): String {
        return AppDatabase.getInstance(this).vpnServerConfigDao().getConfig()?.serverAddress ?: "not configured"
    }

    private fun handleDnsQuery(
        packet: ByteArray, dataOffset: Int, dataLen: Int,
        fullPacket: ByteArray, ihl: Int, fullLen: Int,
        tunOutput: FileOutputStream, srcPort: Int
    ) {
        try {
            val dnsData = packet.copyOfRange(dataOffset, dataOffset + dataLen)
            val query = DnsPacketParser.parseQuery(dnsData, dnsData.size) ?: return
            val domain = query.domain
            var appUid = resolveUidForDns(srcPort, fullPacket, ihl)
            var appName = if (appUid >= 0) uidResolver?.getPackageName(appUid) else null
            if (appUid >= 0 && appUid in blockedAppUids) domainUidCache[domain] = Pair(appUid, System.currentTimeMillis())
            if (appUid < 0) {
                val cached = domainUidCache[domain]
                if (cached != null && System.currentTimeMillis() - cached.second < 30_000) {
                    appUid = cached.first; appName = uidResolver?.getPackageName(appUid)
                }
            }
            if ((appUid >= 0 && appUid in blockedAppUids) || domainTrie.isBlocked(domain)) {
                val blocked = DnsPacketParser.buildBlockedResponse(query)
                val resp = buildDnsResponsePacket(fullPacket, ihl, blocked)
                if (resp != null) tunOutput.write(resp)
                logEvent(appName ?: domain, domain, "BLOCKED"); return
            }
            val response = forwardDnsUpstream(dnsData, dnsData.size) ?: return
            val ipResp = buildDnsResponsePacket(fullPacket, ihl, response)
            if (ipResp != null) tunOutput.write(ipResp)
            logEvent(appName, domain, "ALLOWED")
        } catch (e: Exception) { Log.w(TAG, "DNS error: ${e.message}") }
    }

    private fun logEvent(appName: String?, domain: String, action: String) {
        serviceScope.launch {
            if (action == "BLOCKED") blockedCount++ else allowedCount++
            trafficLogWriter?.log(appName, domain, null, 53, "DNS", action)
            if ((blockedCount + allowedCount) % 5 == 0) notificationManager?.updateStats(blockedCount, allowedCount)
        }
    }

    private fun resolveUidForDns(srcPort: Int, ipPacket: ByteArray, ihl: Int): Int {
        val srcIp = "${ipPacket[12].toInt() and 0xFF}.${ipPacket[13].toInt() and 0xFF}.${ipPacket[14].toInt() and 0xFF}.${ipPacket[15].toInt() and 0xFF}"
        val dstIp = "${ipPacket[16].toInt() and 0xFF}.${ipPacket[17].toInt() and 0xFF}.${ipPacket[18].toInt() and 0xFF}.${ipPacket[19].toInt() and 0xFF}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return getSystemService(ConnectivityManager::class.java).getConnectionOwnerUid(
                    17, InetSocketAddress(InetAddress.getByName(srcIp), srcPort),
                    InetSocketAddress(InetAddress.getByName(dstIp), 53))
            } catch (_: Exception) {}
        }
        return uidResolver?.resolve(17, InetSocketAddress(InetAddress.getByName(srcIp), srcPort),
            InetSocketAddress(InetAddress.getByName(dstIp), 53)) ?: -1
    }

    private fun forwardDnsUpstream(data: ByteArray, len: Int): ByteArray? {
        val sock = DatagramSocket(); protect(sock)
        try {
            sock.send(DatagramPacket(data, len, InetAddress.getByName(upstreamDns), 53))
            val buf = ByteArray(4096); val resp = DatagramPacket(buf, buf.size)
            sock.soTimeout = 5000; sock.receive(resp); return buf.copyOf(resp.length)
        } catch (e: Exception) { Log.w(TAG, "Upstream DNS: ${e.message}"); return null }
        finally { sock.close() }
    }

    private fun buildDnsResponsePacket(orig: ByteArray, ihl: Int, payload: ByteArray): ByteArray? {
        try {
            val udpLen = 8 + payload.size; val total = ihl + udpLen; val p = ByteArray(total)
            System.arraycopy(orig, 0, p, 0, ihl)
            System.arraycopy(orig, 16, p, 12, 4); System.arraycopy(orig, 12, p, 16, 4)
            p[2] = ((total shr 8) and 0xFF).toByte(); p[3] = (total and 0xFF).toByte()
            p[10] = 0; p[11] = 0
            p[ihl] = orig[ihl+2]; p[ihl+1] = orig[ihl+3]; p[ihl+2] = orig[ihl]; p[ihl+3] = orig[ihl+1]
            p[ihl+4] = ((udpLen shr 8) and 0xFF).toByte(); p[ihl+5] = (udpLen and 0xFF).toByte()
            p[ihl+6] = 0; p[ihl+7] = 0
            System.arraycopy(payload, 0, p, ihl+8, payload.size)
            var s = 0; for (i in 0 until ihl step 2) s += ((p[i].toInt() and 0xFF) shl 8) or (p[i+1].toInt() and 0xFF)
            while (s > 0xFFFF) s = (s and 0xFFFF) + (s shr 16)
            val cs = s.inv() and 0xFFFF; p[10] = ((cs shr 8) and 0xFF).toByte(); p[11] = (cs and 0xFF).toByte()
            return p
        } catch (_: Exception) { return null }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { Log.i(TAG, "Network available") }
            override fun onLost(network: Network) { Log.w(TAG, "Network lost") }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback!!)
    }

    private fun stopVpn() {
        running = false
        singBoxManager?.stop()
        tunInterface?.close(); tunInterface = null
        networkCallback?.let {
            try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    override fun onRevoke() { stopVpn() }
    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
