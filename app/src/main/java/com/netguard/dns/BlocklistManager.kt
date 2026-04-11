package com.netguard.dns

import android.content.Context
import android.util.Log
import com.netguard.data.dao.DomainRuleDao
import com.netguard.data.entity.DomainRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Downloads and parses external blocklists in hosts file format.
 * Loads domains into DomainTrie for runtime lookups and persists to Room.
 */
class BlocklistManager(
    private val context: Context,
    private val domainRuleDao: DomainRuleDao,
    private val domainTrie: DomainTrie
) {
    companion object {
        private const val TAG = "BlocklistManager"
        private const val BUNDLED_BLOCKLIST = "blocklist_default.txt"

        val DEFAULT_BLOCKLIST_URLS = listOf(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Load bundled blocklist from assets on first run.
     */
    suspend fun loadBundledBlocklist() = withContext(Dispatchers.IO) {
        try {
            val existing = domainRuleDao.blocklistCount()
            if (existing > 0) {
                Log.i(TAG, "Blocklist already loaded ($existing domains)")
                reloadTrieFromDb()
                return@withContext
            }

            val domains = parseBundledHosts()
            if (domains.isNotEmpty()) {
                val entities = domains.map { domain ->
                    DomainRuleEntity(
                        domainPattern = domain,
                        isBlocked = true,
                        isWildcard = false,
                        source = "blocklist:bundled"
                    )
                }
                domainRuleDao.insertAll(entities)
                domainTrie.insertAll(domains)
                Log.i(TAG, "Loaded ${domains.size} domains from bundled blocklist")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled blocklist", e)
        }
    }

    /**
     * Download and update blocklist from remote URL.
     */
    suspend fun updateFromRemote(url: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Blocklist download failed: ${response.code}")
                return@withContext
            }

            val domains = mutableListOf<String>()
            response.body?.byteStream()?.let { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        parseDomainFromHostsLine(line)?.let { domains.add(it) }
                    }
                }
            }

            if (domains.isNotEmpty()) {
                val sourceName = "blocklist:${url.hashCode()}"
                domainRuleDao.deleteBySource(sourceName)

                val entities = domains.map { domain ->
                    DomainRuleEntity(
                        domainPattern = domain,
                        isBlocked = true,
                        isWildcard = false,
                        source = sourceName
                    )
                }
                domainRuleDao.insertAll(entities)

                // Reload trie
                domainTrie.clear()
                reloadTrieFromDb()

                Log.i(TAG, "Updated blocklist: ${domains.size} domains from $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update blocklist from $url", e)
        }
    }

    suspend fun reloadTrieFromDb() = withContext(Dispatchers.IO) {
        val blocked = domainRuleDao.getAllBlocked()
        for (rule in blocked) {
            // domainPattern already contains "*.xxx.com" or "xxx.com" as entered by user
            domainTrie.insert(rule.domainPattern)
        }
        Log.i(TAG, "Trie loaded with ${domainTrie.size()} domains")
    }

    private fun parseBundledHosts(): List<String> {
        val domains = mutableListOf<String>()
        try {
            context.assets.open(BUNDLED_BLOCKLIST).bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    parseDomainFromHostsLine(line)?.let { domains.add(it) }
                }
            }
        } catch (_: Exception) {
            // No bundled file — that's fine, user can add rules manually
        }
        return domains
    }

    private fun parseDomainFromHostsLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) return null

        val parts = trimmed.split("\\s+".toRegex())
        if (parts.size < 2) return null

        val domain = parts[1].lowercase()
        if (domain == "localhost" || domain == "localhost.localdomain" ||
            domain == "broadcasthost" || domain == "local" ||
            !domain.contains('.')) return null

        return domain
    }
}
