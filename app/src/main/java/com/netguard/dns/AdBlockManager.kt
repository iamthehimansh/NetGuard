package com.netguard.dns

import android.content.Context
import android.content.SharedPreferences
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
 * Manages the ad/tracker blocklist.
 * Downloads StevenBlack unified hosts (~90K domains) and Anudeep adservers (~40K domains).
 * Stores as domain rules with source="adblock".
 */
class AdBlockManager(
    private val context: Context,
    private val domainRuleDao: DomainRuleDao
) {
    companion object {
        private const val TAG = "AdBlockManager"
        private const val PREF_NAME = "adblock_prefs"
        private const val KEY_ENABLED = "adblock_enabled"
        private const val KEY_LAST_UPDATE = "adblock_last_update"
        private const val SOURCE_TAG = "adblock"

        private val BLOCKLIST_URLS = listOf(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "https://raw.githubusercontent.com/anudeepND/blacklist/master/adservers.txt"
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /**
     * Enable ad blocking: download blocklists and insert into DB.
     */
    suspend fun enable() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()

        // Check if already loaded
        val existing = domainRuleDao.countBySource(SOURCE_TAG)
        if (existing > 0) {
            Log.i(TAG, "Ad blocklist already loaded ($existing domains)")
            return@withContext
        }

        Log.i(TAG, "Downloading ad blocklists...")
        val allDomains = mutableSetOf<String>()

        for (url in BLOCKLIST_URLS) {
            try {
                val domains = downloadHostsList(url)
                allDomains.addAll(domains)
                Log.i(TAG, "Downloaded ${domains.size} domains from $url")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $url: ${e.message}")
            }
        }

        if (allDomains.isEmpty()) {
            Log.w(TAG, "No domains downloaded, loading bundled fallback")
            allDomains.addAll(getBundledAdDomains())
        }

        // Batch insert — 500 at a time to avoid transaction too large
        val entities = allDomains.map { domain ->
            DomainRuleEntity(
                domainPattern = domain,
                isBlocked = true,
                isWildcard = false,
                source = SOURCE_TAG
            )
        }

        var inserted = 0
        entities.chunked(500).forEach { chunk ->
            domainRuleDao.insertAll(chunk)
            inserted += chunk.size
        }

        prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        Log.i(TAG, "Ad blocklist enabled: $inserted domains loaded")
    }

    /**
     * Disable ad blocking: remove all adblock domain rules.
     */
    suspend fun disable() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        domainRuleDao.deleteBySource(SOURCE_TAG)
        Log.i(TAG, "Ad blocklist disabled: all adblock rules removed")
    }

    fun getLoadedCount(): Int {
        return prefs.getInt("adblock_count", 0)
    }

    private suspend fun downloadHostsList(url: String): Set<String> = withContext(Dispatchers.IO) {
        val domains = mutableSetOf<String>()
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) return@withContext domains

        response.body?.byteStream()?.let { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.forEachLine { line ->
                    parseHostLine(line)?.let { domains.add(it) }
                }
            }
        }
        domains
    }

    private fun parseHostLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("!")) return null

        // Hosts format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
        // Or plain domain format: "domain.com"
        val parts = trimmed.split("\\s+".toRegex())

        val domain = when {
            parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
            parts.size == 1 && parts[0].contains('.') -> parts[0]
            else -> return null
        }.lowercase()

        // Filter out invalid entries
        if (domain == "localhost" || domain == "localhost.localdomain" ||
            domain == "broadcasthost" || domain == "local" ||
            domain == "0.0.0.0" || domain == "127.0.0.1" ||
            !domain.contains('.') || domain.length < 4) return null

        return domain
    }

    /**
     * Hardcoded fallback — top ad/tracker domains if download fails.
     */
    private fun getBundledAdDomains(): List<String> = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "pagead2.googlesyndication.com", "adservice.google.com",
        "facebook.net", "graph.facebook.com", "pixel.facebook.com",
        "ads.facebook.com", "an.facebook.com",
        "ads.yahoo.com", "analytics.yahoo.com",
        "advertising.com", "adnxs.com", "adsrvr.org",
        "amazon-adsystem.com", "aax.amazon.com",
        "crashlytics.com", "app-measurement.com", "firebase-settings.crashlytics.com",
        "scorecardresearch.com", "quantserve.com", "taboola.com", "outbrain.com",
        "moatads.com", "doubleverify.com", "adsafeprotected.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
        "criteo.com", "criteo.net", "smartadserver.com",
        "appsflyer.com", "adjust.com", "branch.io", "kochava.com",
        "mixpanel.com", "amplitude.com", "segment.io",
        "tracking.epicgames.com", "telemetry.microsoft.com",
        "ads.unity3d.com", "unityads.unity3d.com",
        "mopub.com", "supersonicads.com", "vungle.com", "applovin.com",
        "inmobi.com", "startapp.com", "chartboost.com"
    )
}
