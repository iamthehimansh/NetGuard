package com.netguard.dns

import android.net.VpnService
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Local DNS proxy that intercepts DNS queries, checks against the DomainTrie,
 * blocks matching domains (returns 0.0.0.0), and forwards allowed queries upstream.
 */
class DnsProxy(
    private val vpnService: VpnService,
    private val domainTrie: DomainTrie,
    private val upstreamDns: String = "8.8.8.8",
    private val onQuery: ((String, String, Boolean) -> Unit)? = null  // domain, action callback
) {
    companion object {
        private const val TAG = "DnsProxy"
        private const val LISTEN_PORT = 5353
    }

    @Volatile
    private var running = false
    private var socket: DatagramSocket? = null

    fun start() {
        running = true
        Thread({
            try {
                val sock = DatagramSocket(LISTEN_PORT, InetAddress.getByName("127.0.0.1"))
                socket = sock
                Log.i(TAG, "DNS proxy listening on 127.0.0.1:$LISTEN_PORT")

                val buf = ByteArray(1024)
                while (running) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(packet)
                    } catch (_: Exception) {
                        if (!running) break
                        continue
                    }

                    val query = DnsPacketParser.parseQuery(packet.data, packet.length) ?: continue
                    val domain = query.domain

                    if (domainTrie.isBlocked(domain)) {
                        // Blocked — return 0.0.0.0
                        val response = DnsPacketParser.buildBlockedResponse(query)
                        val reply = DatagramPacket(
                            response, response.size,
                            packet.address, packet.port
                        )
                        sock.send(reply)
                        Log.d(TAG, "BLOCKED: $domain")
                        onQuery?.invoke(domain, "BLOCKED", true)
                    } else {
                        // Allowed — forward to upstream
                        try {
                            val response = forwardToUpstream(query)
                            val reply = DatagramPacket(
                                response, response.size,
                                packet.address, packet.port
                            )
                            sock.send(reply)
                            Log.d(TAG, "ALLOWED: $domain")
                            onQuery?.invoke(domain, "ALLOWED", false)
                        } catch (e: Exception) {
                            Log.w(TAG, "Upstream DNS failed for $domain: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS proxy error", e)
            } finally {
                socket?.close()
            }
        }, "DnsProxy").start()
    }

    fun stop() {
        running = false
        socket?.close()
    }

    private fun forwardToUpstream(query: DnsPacketParser.DnsQuery): ByteArray {
        val sock = DatagramSocket()
        vpnService.protect(sock)  // CRITICAL: prevent routing loop

        try {
            val upstreamAddr = InetAddress.getByName(upstreamDns)
            val outPacket = DatagramPacket(query.rawData, query.rawLength, upstreamAddr, 53)
            sock.send(outPacket)

            val responseBuf = ByteArray(4096)
            val responsePacket = DatagramPacket(responseBuf, responseBuf.size)
            sock.soTimeout = 5000
            sock.receive(responsePacket)

            return responseBuf.copyOf(responsePacket.length)
        } finally {
            sock.close()
        }
    }
}
