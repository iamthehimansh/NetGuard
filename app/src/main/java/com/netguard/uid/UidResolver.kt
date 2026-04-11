package com.netguard.uid

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves network connections to originating app UID.
 * Uses ConnectivityManager API on Android 10+, falls back to /proc/net parsing.
 */
class UidResolver(private val context: Context) {

    data class ConnectionKey(
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int,
        val protocol: Int  // 6=TCP, 17=UDP
    )

    private val cache = ConcurrentHashMap<ConnectionKey, Int>()

    fun resolve(
        protocol: Int,
        localAddr: InetSocketAddress,
        remoteAddr: InetSocketAddress
    ): Int {
        val key = ConnectionKey(localAddr.port, remoteAddr.address.hostAddress ?: "", remoteAddr.port, protocol)
        cache[key]?.let { return it }

        val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolveViaConnectivityManager(protocol, localAddr, remoteAddr)
        } else {
            resolveViaProcNet(localAddr.port, protocol)
        }

        if (uid >= 0) {
            cache[key] = uid
        }
        return uid
    }

    fun getPackageName(uid: Int): String? {
        return context.packageManager.getPackagesForUid(uid)?.firstOrNull()
    }

    fun clearCache() {
        cache.clear()
    }

    private fun resolveViaConnectivityManager(
        protocol: Int,
        local: InetSocketAddress,
        remote: InetSocketAddress
    ): Int {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cm.getConnectionOwnerUid(protocol, local, remote)
            } else -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun resolveViaProcNet(localPort: Int, protocol: Int): Int {
        val procFile = when (protocol) {
            6 -> "/proc/net/tcp"
            17 -> "/proc/net/udp"
            else -> return -1
        }

        val hexPort = String.format("%04X", localPort)

        try {
            BufferedReader(FileReader(procFile)).use { reader ->
                reader.readLine()  // skip header
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 10) {
                        val localAddress = parts[1]
                        val port = localAddress.substringAfter(':')
                        if (port.equals(hexPort, ignoreCase = true)) {
                            return parts[7].toIntOrNull() ?: -1  // UID is at index 7
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {}

        // Also try tcp6/udp6
        val proc6File = "${procFile}6"
        try {
            BufferedReader(FileReader(proc6File)).use { reader ->
                reader.readLine()
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 10) {
                        val localAddress = parts[1]
                        val port = localAddress.substringAfter(':')
                        if (port.equals(hexPort, ignoreCase = true)) {
                            return parts[7].toIntOrNull() ?: -1
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {}

        return -1
    }
}
