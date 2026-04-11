package com.netguard.dns

/**
 * Trie for fast domain blocklist lookup with wildcard support.
 * Domains stored in reverse-label order for prefix matching.
 *
 * "ads.google.com"     → blocks only ads.google.com
 * "*.youtube.com"      → blocks youtube.com AND all subdomains (m.youtube.com, www.youtube.com, etc.)
 * "youtube.com"        → blocks only youtube.com (not subdomains)
 */
class DomainTrie {

    private class Node {
        val children = HashMap<String, Node>()
        var isTerminal = false    // exact match (e.g., "youtube.com")
        var isWildcard = false    // matches this domain + all subdomains (e.g., "*.youtube.com")
    }

    private val root = Node()
    private var size = 0

    /**
     * Insert a domain rule.
     * "youtube.com"   → blocks youtube.com exactly
     * "*.youtube.com" → blocks youtube.com AND all subdomains
     */
    fun insert(domain: String) {
        val trimmed = domain.trim().lowercase()
        if (trimmed.isBlank()) return

        val isWildcard = trimmed.startsWith("*.")
        val cleaned = trimmed.removePrefix("*.")

        if (cleaned.isBlank() || !cleaned.contains('.')) return

        val labels = cleaned.split('.').reversed()
        var node = root
        for (label in labels) {
            node = node.children.getOrPut(label) { Node() }
        }

        if (isWildcard) {
            // *.youtube.com → block youtube.com AND all subdomains
            node.isWildcard = true
            node.isTerminal = true  // also block the base domain itself
        } else {
            node.isTerminal = true
        }
        size++
    }

    /**
     * Check if a domain is blocked.
     *
     * "youtube.com"     → matches "youtube.com" or "*.youtube.com"
     * "m.youtube.com"   → matches "*.youtube.com"
     * "www.youtube.com" → matches "*.youtube.com"
     * "google.com"      → only matches if "google.com" or "*.google.com" was inserted
     */
    fun isBlocked(domain: String): Boolean {
        val labels = domain.trim().lowercase().split('.').reversed()
        var node = root

        for ((index, label) in labels.withIndex()) {
            // If current node is wildcard, everything under it is blocked
            if (node.isWildcard) return true

            node = node.children[label] ?: return false
        }

        // Reached the exact node — check if it's a terminal or wildcard
        return node.isTerminal || node.isWildcard
    }

    fun clear() {
        root.children.clear()
        size = 0
    }

    fun size(): Int = size

    fun insertAll(domains: List<String>) {
        for (domain in domains) {
            insert(domain)
        }
    }
}
