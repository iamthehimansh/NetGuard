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
            "8.8.8.8", "8.8.4.4",
            "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var trafficLogWriter: TrafficLogWriter? = null
    private var notificationManager: VpnNotificationManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var uidResolver: UidResolver? = null

    private val domainTrie = DomainTrie()
    private var blockedCount = 0
    private var allowedCount = 0

    private var blockedAppUids = setOf<Int>()
    private var blockedPackageNames = setOf<String>()
    private var upstreamDns = "8.8.8.8"
    private var hasBlockedApps = false

    // Cache domain→UID for race condition fix
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

            trafficLogWriter = TrafficLogWriter(db.trafficLogDao(), db.trafficStatsDao(), serviceScope)
            uidResolver = UidResolver(this)

            val blocklistManager = BlocklistManager(this, db.domainRuleDao(), domainTrie)
            blocklistManager.loadBundledBlocklist()
            loadBlockedApps()

            establishTunnel()
            registerNetworkCallback()
            startPacketLoop()

        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed", e)
            stopVpn()
        }
    }

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
        BlocklistManager(this, db.domainRuleDao(), domainTrie).reloadTrieFromDb()
        Log.i(TAG, "Rules: ${blockedAppUids.size} blocked apps, ${domainTrie.size()} blocked domains")
    }

    /**
     * Two modes:
     *
     * MODE 1 - Apps blocked: Full route (0.0.0.0/0)
     *   Route ALL traffic through TUN. Exclude non-blocked apps via addDisallowedApplication.
     *   Blocked apps' ALL traffic (DNS + TCP + UDP) goes to TUN → we DROP everything.
     *   This fully blocks apps even if they use cached/hardcoded IPs.
     *   Domain blocking works for blocked apps only.
     *
     * MODE 2 - No apps blocked, only domain rules: DNS-only route
     *   Route only DNS server IPs through TUN.
     *   ALL apps' DNS goes through TUN → domain blocking works for everyone.
     *   Non-DNS traffic flows normally.
     */
    private fun establishTunnel() {
        tunInterface?.close()
        tunInterface = null

        val builder = Builder()
            .setSession("NetGuard")
            .addAddress("10.1.10.1", 32)
            .addDnsServer(upstreamDns)
            .setMtu(1500)
            .setBlocking(true)

        // Exclude our own app always
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        if (hasBlockedApps) {
            // MODE 1: Full route — black hole for blocked apps
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            builder.addAddress("fd00::1", 128)

            // Exclude all NON-blocked apps so they have normal network
            val installedApps = packageManager.getInstalledApplications(0)
            var excluded = 0
            for (app in installedApps) {
                if (app.packageName == packageName) continue
                if (app.packageName in blockedPackageNames) continue
                try {
                    builder.addDisallowedApplication(app.packageName)
                    excluded++
                } catch (_: Exception) {}
            }
            Log.i(TAG, "MODE 1: Full route. Blocking ${blockedPackageNames.size} apps, excluded $excluded")
        } else {
            // MODE 2: DNS-only route — domain blocking for all apps
            for (dns in DNS_ROUTES) {
                try { builder.addRoute(dns, 32) } catch (_: Exception) {}
            }
            if (upstreamDns !in DNS_ROUTES) {
                try { builder.addRoute(upstreamDns, 32) } catch (_: Exception) {}
            }
            Log.i(TAG, "MODE 2: DNS-only route. Domain blocking for all apps.")
        }

        tunInterface = builder.establish()
        if (tunInterface == null) {
            Log.e(TAG, "Failed to establish VPN")
            stopVpn()
        }
    }

    private suspend fun reloadAndRestart() {
        Log.i(TAG, "Reloading rules and restarting tunnel...")
        loadBlockedApps()
        establishTunnel()
    }

    /**
     * Packet loop handles both modes:
     * - DNS (UDP:53) → intercept, check app block + domain block
     * - Non-DNS (only in MODE 1) → DROP silently (black hole for blocked apps)
     */
    private fun startPacketLoop() {
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
                            if (length < 20) continue

                            val version = (buffer[0].toInt() shr 4) and 0xF
                            if (version != 4) continue

                            val protocol = buffer[9].toInt() and 0xFF
                            val ihl = (buffer[0].toInt() and 0xF) * 4

                            // Handle DNS queries (UDP port 53)
                            if (protocol == 17 && length > ihl + 8) {
                                val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or
                                    (buffer[ihl + 3].toInt() and 0xFF)

                                if (dstPort == 53) {
                                    val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or
                                        (buffer[ihl + 1].toInt() and 0xFF)
                                    val udpDataOffset = ihl + 8
                                    val udpDataLen = length - udpDataOffset
                                    if (udpDataLen >= 12) {
                                        handleDnsQuery(
                                            buffer, udpDataOffset, udpDataLen,
                                            buffer, ihl, length, output, srcPort
                                        )
                                    }
                                    continue
                                }
                            }

                            // Non-DNS traffic — only arrives in MODE 1 (full route)
                            // All non-DNS from blocked apps → DROP (black hole)
                            // Log occasionally to avoid spam
                            if (hasBlockedApps && (protocol == 6 || protocol == 17)) {
                                val dstIp = "${buffer[16].toInt() and 0xFF}.${buffer[17].toInt() and 0xFF}.${buffer[18].toInt() and 0xFF}.${buffer[19].toInt() and 0xFF}"
                                val dstPort = if (length > ihl + 3) {
                                    ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
                                } else 0
                                val protoName = if (protocol == 6) "TCP" else "UDP"

                                if (blockedCount % 50 == 0) {
                                    serviceScope.launch {
                                        blockedCount++
                                        trafficLogWriter?.log(null, dstIp, dstIp, dstPort, protoName, "BLOCKED")
                                        notificationManager?.updateStats(blockedCount, allowedCount)
                                    }
                                } else {
                                    blockedCount++
                                }
                            }
                            // DROP — don't forward
                        }
                    } catch (_: Exception) {
                        // TUN closed during reload
                    }
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "Packet loop error", e)
            }
        }, "PacketLoop").start()
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

            // Cache domain→UID when we see a blocked app
            if (appUid >= 0 && appUid in blockedAppUids) {
                domainUidCache[domain] = Pair(appUid, System.currentTimeMillis())
            }

            // Fallback to cache if UID lookup failed
            if (appUid < 0) {
                val cached = domainUidCache[domain]
                if (cached != null && System.currentTimeMillis() - cached.second < 30_000) {
                    appUid = cached.first
                    appName = uidResolver?.getPackageName(appUid)
                }
            }

            // Check 1: App blocked
            if (appUid >= 0 && appUid in blockedAppUids) {
                writeBlockedDnsResponse(query, fullPacket, ihl, tunOutput)
                logEvent(appName, domain, "BLOCKED")
                return
            }

            // Check 2: Domain blocked
            if (domainTrie.isBlocked(domain)) {
                writeBlockedDnsResponse(query, fullPacket, ihl, tunOutput)
                logEvent(appName ?: domain, domain, "BLOCKED")
                return
            }

            // Allowed
            val response = forwardDnsUpstream(dnsData, dnsData.size) ?: return
            val ipResponse = buildDnsResponsePacket(fullPacket, ihl, response)
            if (ipResponse != null) tunOutput.write(ipResponse)
            logEvent(appName, domain, "ALLOWED")

        } catch (e: Exception) {
            Log.w(TAG, "DNS error: ${e.message}")
        }
    }

    private fun writeBlockedDnsResponse(
        query: DnsPacketParser.DnsQuery,
        fullPacket: ByteArray, ihl: Int,
        tunOutput: FileOutputStream
    ) {
        val blocked = DnsPacketParser.buildBlockedResponse(query)
        val ipResponse = buildDnsResponsePacket(fullPacket, ihl, blocked)
        if (ipResponse != null) tunOutput.write(ipResponse)
    }

    private fun logEvent(appName: String?, domain: String, action: String) {
        serviceScope.launch {
            if (action == "BLOCKED") blockedCount++ else allowedCount++
            trafficLogWriter?.log(appName, domain, null, 53, "DNS", action)
            if ((blockedCount + allowedCount) % 5 == 0) {
                notificationManager?.updateStats(blockedCount, allowedCount)
            }
        }
    }

    private fun resolveUidForDns(srcPort: Int, ipPacket: ByteArray, ihl: Int): Int {
        val srcIp = "${ipPacket[12].toInt() and 0xFF}.${ipPacket[13].toInt() and 0xFF}.${ipPacket[14].toInt() and 0xFF}.${ipPacket[15].toInt() and 0xFF}"
        val dstIp = "${ipPacket[16].toInt() and 0xFF}.${ipPacket[17].toInt() and 0xFF}.${ipPacket[18].toInt() and 0xFF}.${ipPacket[19].toInt() and 0xFF}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = getSystemService(ConnectivityManager::class.java)
                return cm.getConnectionOwnerUid(
                    17,
                    InetSocketAddress(InetAddress.getByName(srcIp), srcPort),
                    InetSocketAddress(InetAddress.getByName(dstIp), 53)
                )
            } catch (_: Exception) {}
        }
        return uidResolver?.resolve(
            17,
            InetSocketAddress(InetAddress.getByName(srcIp), srcPort),
            InetSocketAddress(InetAddress.getByName(dstIp), 53)
        ) ?: -1
    }

    private fun forwardDnsUpstream(data: ByteArray, len: Int): ByteArray? {
        val sock = DatagramSocket()
        protect(sock)
        try {
            sock.send(DatagramPacket(data, len, InetAddress.getByName(upstreamDns), 53))
            val buf = ByteArray(4096)
            val resp = DatagramPacket(buf, buf.size)
            sock.soTimeout = 5000
            sock.receive(resp)
            return buf.copyOf(resp.length)
        } catch (e: Exception) {
            Log.w(TAG, "Upstream DNS failed: ${e.message}")
            return null
        } finally { sock.close() }
    }

    private fun buildDnsResponsePacket(
        origPacket: ByteArray, ihl: Int, udpPayload: ByteArray
    ): ByteArray? {
        try {
            val udpLen = 8 + udpPayload.size
            val totalLen = ihl + udpLen
            val pkt = ByteArray(totalLen)
            System.arraycopy(origPacket, 0, pkt, 0, ihl)
            System.arraycopy(origPacket, 16, pkt, 12, 4)
            System.arraycopy(origPacket, 12, pkt, 16, 4)
            pkt[2] = ((totalLen shr 8) and 0xFF).toByte()
            pkt[3] = (totalLen and 0xFF).toByte()
            pkt[10] = 0; pkt[11] = 0
            pkt[ihl] = origPacket[ihl + 2]; pkt[ihl + 1] = origPacket[ihl + 3]
            pkt[ihl + 2] = origPacket[ihl]; pkt[ihl + 3] = origPacket[ihl + 1]
            pkt[ihl + 4] = ((udpLen shr 8) and 0xFF).toByte()
            pkt[ihl + 5] = (udpLen and 0xFF).toByte()
            pkt[ihl + 6] = 0; pkt[ihl + 7] = 0
            System.arraycopy(udpPayload, 0, pkt, ihl + 8, udpPayload.size)
            var sum = 0
            for (i in 0 until ihl step 2) {
                sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
            }
            while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum shr 16)
            val cs = sum.inv() and 0xFFFF
            pkt[10] = ((cs shr 8) and 0xFF).toByte()
            pkt[11] = (cs and 0xFF).toByte()
            return pkt
        } catch (_: Exception) { return null }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { Log.i(TAG, "Network available") }
            override fun onLost(network: Network) { Log.w(TAG, "Network lost") }
        }
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            networkCallback!!
        )
    }

    private fun stopVpn() {
        running = false
        tunInterface?.close()
        tunInterface = null
        networkCallback?.let {
            try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
            catch (_: Exception) {}
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    override fun onRevoke() { stopVpn() }
    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
