# NetGuard - Android Network Firewall

A no-root Android firewall app that intercepts DNS requests to block apps and domains from accessing the network.

## Features

- **Per-App Blocking** — Toggle any app's network access on/off. Blocked apps get their DNS queries returned as `0.0.0.0`, effectively cutting off their internet.
- **Per-Domain Blocking** — Block specific domains (e.g., `youtube.com`) or use wildcards (`*.youtube.com`) to block a domain and all its subdomains across all apps.
- **DNS Interception** — Routes all DNS traffic through a local VPN tunnel, filtering queries before they reach the upstream DNS server.
- **Traffic Logging** — Real-time log of all DNS queries showing which app requested which domain, with BLOCKED/ALLOWED status.
- **Aggregate Statistics** — Permanent per-app and per-domain traffic counts that persist across log clears.
- **Auto-start on Boot** — Optionally start the firewall automatically when the device boots.
- **No Root Required** — Uses Android's `VpnService` API to create a local VPN that only captures DNS traffic.

## How It Works

```
App makes DNS query → Android sends to VPN DNS (8.8.8.8)
→ VPN routes 8.8.8.8/32 through TUN interface
→ NetGuard reads DNS packet from TUN
→ Parses domain name from DNS query (RFC 1035)
→ Resolves originating app via UID lookup

→ App blocked? → Return 0.0.0.0 (no network for that app)
→ Domain blocked? → Return 0.0.0.0 (domain unreachable)
→ Allowed? → Forward to upstream DNS → Return real IP

Non-DNS traffic → bypasses VPN entirely → normal network
```

**Key design choice:** Only DNS traffic is routed through the VPN (via `addRoute` for known DNS server IPs). All other traffic flows through the real network with zero performance impact.

## Architecture

```
com.netguard/
├── vpn/
│   ├── NetGuardVpnService.kt      — VPN service with DNS packet loop
│   └── VpnConnectionManager.kt    — Start/stop/reload helper
├── dns/
│   ├── DnsPacketParser.kt          — RFC 1035 wire format parser
│   ├── DomainTrie.kt               — Fast domain lookup with wildcards
│   ├── DnsProxy.kt                 — Local DNS proxy server
│   └── BlocklistManager.kt         — Hosts file importer
├── proxy/
│   ├── Socks5Server.kt             — RFC 1928 SOCKS5 proxy (for tun2socks)
│   └── SniExtractor.kt             — TLS ClientHello SNI parser
├── uid/
│   └── UidResolver.kt              — Map connections to app UIDs
├── rules/
│   └── RuleEngine.kt               — Priority-ordered rule evaluation
├── data/
│   ├── AppDatabase.kt              — Room database (5 tables)
│   ├── TrafficLogWriter.kt         — Buffered batch log writer
│   ├── entity/                     — Room entities
│   └── dao/                        — Room DAOs with Flow queries
├── ui/screens/
│   ├── HomeScreen.kt               — Dashboard with VPN toggle + stats
│   ├── AppListScreen.kt            — App list with block toggles
│   ├── DomainRulesScreen.kt        — Domain rule editor
│   ├── TrafficLogScreen.kt         — Real-time traffic log
│   └── SettingsScreen.kt           — DNS, policy, about
└── notification/
    └── VpnNotificationManager.kt   — Foreground service notification
```

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Hilt DI
- **Database:** Room (reactive Flow queries)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

## Building

```bash
# Clone
git clone https://github.com/iamthehimansh/NetGuard.git
cd NetGuard

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Screenshots

| Home | App Rules | Domain Rules | Traffic Log |
|------|-----------|--------------|-------------|
| VPN toggle, blocked/allowed counts | Per-app block toggles with search | Wildcard domain rules | Real-time DNS query log |

## Future Roadmap

- **tun2socks Integration** — Full TCP/UDP proxying via SOCKS5 for connection-level filtering (infrastructure already built)
- **TLS SNI Inspection** — Block specific domains at the connection level, not just DNS
- **Scheduled Rules** — Time-based blocking (e.g., block social media 9am-5pm)
- **Blocklist Subscriptions** — Auto-update from StevenBlack, Energized, AdGuard lists
- **PCAP Export** — Export traffic data for analysis

## Author

**Himansh** — [himansh.in](https://himansh.in) — [GitHub](https://github.com/iamthehimansh)

## License

MIT License
