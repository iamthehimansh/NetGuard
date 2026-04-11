package com.netguard.proxy

import android.net.VpnService
import android.util.Log
import com.netguard.rules.RuleEngine
import com.netguard.uid.UidResolver
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Local SOCKS5 proxy server (RFC 1928).
 * Receives TCP connections from tun2socks, resolves UID, extracts SNI,
 * evaluates rules, and relays or blocks traffic.
 */
class Socks5Server(
    private val vpnService: VpnService,
    private val uidResolver: UidResolver,
    private val ruleEngine: RuleEngine,
    private val onConnection: ((String?, String?, Int, String, String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "Socks5Server"
        const val PORT = 1080
    }

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    fun start() {
        running = true
        Thread({
            try {
                val ss = ServerSocket(PORT, 128, InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                Log.i(TAG, "SOCKS5 proxy listening on 127.0.0.1:$PORT")

                while (running) {
                    try {
                        val client = ss.accept()
                        executor.execute { handleConnection(client) }
                    } catch (_: SocketException) {
                        if (!running) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SOCKS5 server error", e)
            }
        }, "Socks5Server").start()
    }

    fun stop() {
        running = false
        serverSocket?.close()
        executor.shutdownNow()
    }

    private fun handleConnection(client: Socket) {
        try {
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()

            // 1. SOCKS5 Greeting
            if (!handleGreeting(input, output)) {
                client.close()
                return
            }

            // 2. Parse CONNECT request
            val connectInfo = parseConnectRequest(input) ?: run {
                sendConnectResponse(output, 0x01)  // general failure
                client.close()
                return
            }

            // 3. Resolve UID
            val localAddr = InetSocketAddress(client.localAddress, client.localPort)
            val remoteAddr = InetSocketAddress(connectInfo.address, connectInfo.port)
            val uid = uidResolver.resolve(6, localAddr, remoteAddr)
            val packageName = if (uid >= 0) uidResolver.getPackageName(uid) else null

            // 4. Extract SNI if HTTPS (port 443)
            var domain: String? = null
            if (connectInfo.port == 443) {
                domain = SniExtractor.extract(input)
            }

            // 5. Evaluate rules
            val request = RuleEngine.ConnectionRequest(
                packageName = packageName,
                uid = uid,
                domain = domain,
                destIp = connectInfo.address,
                destPort = connectInfo.port,
                protocol = "TCP"
            )
            val verdict = ruleEngine.evaluate(request)

            if (verdict == RuleEngine.Verdict.DENY) {
                sendConnectResponse(output, 0x02)  // not allowed by ruleset
                Log.d(TAG, "BLOCKED: $packageName -> ${domain ?: connectInfo.address}:${connectInfo.port}")
                onConnection?.invoke(packageName, domain ?: connectInfo.address, connectInfo.port, "TCP", "BLOCKED")
                client.close()
                return
            }

            // 6. Connect to real destination
            val upstream = Socket()
            vpnService.protect(upstream)  // CRITICAL: prevent VPN loop
            try {
                upstream.connect(InetSocketAddress(connectInfo.address, connectInfo.port), 10000)
            } catch (e: Exception) {
                sendConnectResponse(output, 0x04)  // host unreachable
                client.close()
                return
            }

            sendConnectResponse(output, 0x00)  // success
            Log.d(TAG, "RELAY: $packageName -> ${domain ?: connectInfo.address}:${connectInfo.port}")
            onConnection?.invoke(packageName, domain ?: connectInfo.address, connectInfo.port, "TCP", "ALLOWED")

            // 7. Bidirectional relay
            relay(client, upstream)
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleGreeting(input: InputStream, output: OutputStream): Boolean {
        val ver = input.read()
        if (ver != 0x05) return false

        val nMethods = input.read()
        if (nMethods <= 0) return false
        val methods = ByteArray(nMethods)
        input.read(methods)

        // Respond: SOCKS5, NO_AUTH
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()
        return true
    }

    data class ConnectInfo(val address: String, val port: Int)

    private fun parseConnectRequest(input: InputStream): ConnectInfo? {
        val ver = input.read()
        val cmd = input.read()
        input.read()  // RSV
        val atyp = input.read()

        if (ver != 0x05 || cmd != 0x01) return null  // only CONNECT supported

        val address = when (atyp) {
            0x01 -> {  // IPv4
                val addr = ByteArray(4)
                input.read(addr)
                InetAddress.getByAddress(addr).hostAddress ?: return null
            }
            0x03 -> {  // Domain
                val len = input.read()
                val domain = ByteArray(len)
                input.read(domain)
                String(domain, Charsets.US_ASCII)
            }
            0x04 -> {  // IPv6
                val addr = ByteArray(16)
                input.read(addr)
                InetAddress.getByAddress(addr).hostAddress ?: return null
            }
            else -> return null
        }

        val portHigh = input.read()
        val portLow = input.read()
        val port = (portHigh shl 8) or portLow

        return ConnectInfo(address, port)
    }

    private fun sendConnectResponse(output: OutputStream, status: Int) {
        output.write(byteArrayOf(
            0x05,
            status.toByte(),
            0x00,                  // RSV
            0x01,                  // ATYP = IPv4
            0x00, 0x00, 0x00, 0x00, // BND.ADDR = 0.0.0.0
            0x00, 0x00             // BND.PORT = 0
        ))
        output.flush()
    }

    private fun relay(client: Socket, upstream: Socket) {
        val t1 = Thread({
            try {
                copyStream(client.getInputStream(), upstream.getOutputStream())
            } catch (_: Exception) {}
            finally {
                try { upstream.shutdownOutput() } catch (_: Exception) {}
            }
        }, "relay-c2s")

        val t2 = Thread({
            try {
                copyStream(upstream.getInputStream(), client.getOutputStream())
            } catch (_: Exception) {}
            finally {
                try { client.shutdownOutput() } catch (_: Exception) {}
            }
        }, "relay-s2c")

        t1.start()
        t2.start()
        t1.join()
        t2.join()

        try { upstream.close() } catch (_: Exception) {}
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            output.flush()
        }
    }
}
