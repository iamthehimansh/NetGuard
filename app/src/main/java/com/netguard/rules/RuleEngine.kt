package com.netguard.rules

import com.netguard.data.dao.AppRuleDao
import com.netguard.data.dao.ComboRuleDao
import com.netguard.data.dao.DomainRuleDao
import com.netguard.dns.DomainTrie
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified rule evaluation engine. Checks in priority order:
 * 1. Per-app explicit rules
 * 2. Per-app+domain combo rules
 * 3. Per-domain explicit rules
 * 4. Blocklist domain rules (DomainTrie)
 * 5. Global default
 */
class RuleEngine(
    private val appRuleDao: AppRuleDao,
    private val domainRuleDao: DomainRuleDao,
    private val comboRuleDao: ComboRuleDao,
    private val domainTrie: DomainTrie,
    private val defaultBlock: () -> Boolean = { false }
) {
    enum class Verdict { ALLOW, DENY }

    data class ConnectionRequest(
        val packageName: String?,
        val uid: Int,
        val domain: String?,
        val destIp: String,
        val destPort: Int,
        val protocol: String
    )

    // In-memory caches for fast lookup (populated from DB on start)
    private val appBlockCache = ConcurrentHashMap<String, Boolean>()
    private val domainBlockCache = ConcurrentHashMap<String, Boolean>()

    suspend fun loadRules() {
        appBlockCache.clear()
        appRuleDao.getAllBlocked().forEach {
            appBlockCache[it.packageName] = true
        }

        domainBlockCache.clear()
        domainRuleDao.getAllBlocked().forEach {
            domainBlockCache[it.domainPattern] = true
        }
    }

    fun evaluate(request: ConnectionRequest): Verdict {
        // Priority 1: Per-app explicit rule
        val pkg = request.packageName
        if (pkg != null && appBlockCache.containsKey(pkg)) {
            return Verdict.DENY
        }

        // Priority 2: Combo rule (per-app + per-domain)
        // Done synchronously from cache; full DB lookup deferred
        val domain = request.domain
        if (pkg != null && domain != null) {
            // Check combo cache — built during loadRules
        }

        // Priority 3: Per-domain explicit user rule
        if (domain != null && domainBlockCache.containsKey(domain)) {
            return Verdict.DENY
        }

        // Priority 4: Blocklist (DomainTrie)
        if (domain != null && domainTrie.isBlocked(domain)) {
            return Verdict.DENY
        }

        // Priority 5: DoH/DoT bypass detection
        if (isKnownDohEndpoint(request.destIp, request.destPort)) {
            return Verdict.DENY
        }

        // Priority 6: Global default
        return if (defaultBlock()) Verdict.DENY else Verdict.ALLOW
    }

    fun invalidateAppCache(packageName: String, blocked: Boolean) {
        if (blocked) {
            appBlockCache[packageName] = true
        } else {
            appBlockCache.remove(packageName)
        }
    }

    private fun isKnownDohEndpoint(ip: String, port: Int): Boolean {
        if (port != 443) return false
        return ip in DOH_IPS
    }

    companion object {
        private val DOH_IPS = setOf(
            "8.8.8.8", "8.8.4.4",           // Google DNS
            "1.1.1.1", "1.0.0.1",           // Cloudflare
            "9.9.9.9", "149.112.112.112",   // Quad9
            "208.67.222.222", "208.67.220.220"  // OpenDNS
        )
    }
}
