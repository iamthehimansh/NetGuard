package com.netguard.vpn

import com.netguard.data.entity.VpnServerConfigEntity
import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigGenerator {

    fun generateDirectConfig(server: VpnServerConfigEntity): String {
        return buildConfig(server, sniffEnabled = true)
    }

    fun generateManagedConfig(server: VpnServerConfigEntity): String {
        return buildConfig(server, sniffEnabled = true)
    }

    private fun buildConfig(server: VpnServerConfigEntity, sniffEnabled: Boolean): String {
        val config = JSONObject()

        // Log
        config.put("log", JSONObject().apply {
            put("level", "warn")
            put("timestamp", true)
        })

        // Inbounds — tun
        config.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "tun0")
                put("inet4_address", "172.19.0.1/28")
                put("mtu", 1420)
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "system")
                put("sniff", sniffEnabled)
            })
        })

        // Outbounds — VLESS + DNS
        config.put("outbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "vless")
                put("tag", "vless-out")
                put("server", server.serverAddress)
                put("server_port", server.serverPort)
                put("uuid", server.uuid)
                put("connect_timeout", "10s")
                put("tcp_fast_open", true)
                put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", server.sni.ifBlank { server.serverAddress })
                    if (server.alpn.isNotBlank()) {
                        put("alpn", JSONArray().apply { put(server.alpn) })
                    }
                    if (server.fingerprint.isNotBlank()) {
                        put("utls", JSONObject().apply {
                            put("enabled", true)
                            put("fingerprint", server.fingerprint)
                        })
                    }
                })
                put("transport", JSONObject().apply {
                    put("type", "ws")
                    put("path", server.wsPath)
                    put("headers", JSONObject().apply {
                        put("Host", server.sni.ifBlank { server.serverAddress })
                    })
                })
            })
            put(JSONObject().apply {
                put("type", "dns")
                put("tag", "dns-out")
            })
        })

        // Route
        config.put("route", JSONObject().apply {
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("protocol", "dns")
                    put("outbound", "dns-out")
                })
            })
            put("final", "vless-out")
        })

        // DNS — route through Pi-hole on the server
        config.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "remote-pihole")
                    put("address", "10.1.10.1")
                    put("detour", "vless-out")
                })
            })
            put("strategy", "ipv4_only")
            put("final", "remote-pihole")
        })

        // Experimental — cache
        config.put("experimental", JSONObject().apply {
            put("cache_file", JSONObject().apply {
                put("enabled", true)
                put("path", "cache.db")
            })
        })

        return config.toString(2)
    }

    /**
     * Parse a vless:// share link into a VpnServerConfigEntity.
     * Format: vless://UUID@host:port?type=ws&security=tls&sni=...&path=...#name
     */
    fun parseVlessLink(link: String): VpnServerConfigEntity? {
        try {
            val cleaned = link.trim()
            if (!cleaned.startsWith("vless://")) return null

            val withoutScheme = cleaned.removePrefix("vless://")
            val fragmentSplit = withoutScheme.split("#", limit = 2)
            val mainPart = fragmentSplit[0]

            val uuidAndRest = mainPart.split("@", limit = 2)
            if (uuidAndRest.size != 2) return null
            val uuid = uuidAndRest[0]

            val hostPortAndParams = uuidAndRest[1].split("?", limit = 2)
            val hostPort = hostPortAndParams[0]
            val params = if (hostPortAndParams.size > 1) parseQueryParams(hostPortAndParams[1]) else emptyMap()

            val hostPortSplit = hostPort.split(":", limit = 2)
            val host = hostPortSplit[0]
            val port = if (hostPortSplit.size > 1) hostPortSplit[1].toIntOrNull() ?: 443 else 443

            return VpnServerConfigEntity(
                serverAddress = host,
                serverPort = port,
                uuid = uuid,
                wsPath = java.net.URLDecoder.decode(params["path"] ?: "/netguard", "UTF-8"),
                sni = params["sni"] ?: host,
                alpn = java.net.URLDecoder.decode(params["alpn"] ?: "http/1.1", "UTF-8"),
                fingerprint = params["fp"] ?: "chrome"
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        return query.split("&").associate { param ->
            val kv = param.split("=", limit = 2)
            kv[0] to (if (kv.size > 1) kv[1] else "")
        }
    }
}
