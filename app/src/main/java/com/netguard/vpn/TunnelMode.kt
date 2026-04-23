package com.netguard.vpn

enum class TunnelMode {
    DNS_FILTER_ONLY,  // Current NetGuard behavior: DNS interception only
    DIRECT,           // All traffic tunneled to VPN server, no local filtering
    MANAGED           // Local DNS filter + per-app rules, then tunnel allowed traffic
}
