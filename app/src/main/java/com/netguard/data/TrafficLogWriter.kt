package com.netguard.data

import com.netguard.data.dao.TrafficLogDao
import com.netguard.data.dao.TrafficStatsDao
import com.netguard.data.entity.TrafficLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Buffered traffic log writer with:
 * - Batch inserts via Channel
 * - Auto-clear detailed logs every 20 minutes
 * - Permanent aggregate stats per app and domain
 */
class TrafficLogWriter(
    private val logDao: TrafficLogDao,
    private val statsDao: TrafficStatsDao,
    scope: CoroutineScope
) {
    private val channel = Channel<TrafficLogEntity>(capacity = 1000)

    init {
        // Batch insert loop
        scope.launch(Dispatchers.IO) {
            val batch = mutableListOf<TrafficLogEntity>()
            while (true) {
                val entry = channel.receive()
                batch.add(entry)
                while (true) {
                    val next = channel.tryReceive().getOrNull() ?: break
                    batch.add(next)
                    if (batch.size >= 100) break
                }
                try { logDao.batchInsert(batch) } catch (_: Exception) {}
                batch.clear()
                delay(500)
            }
        }

        // Auto-clear detailed logs every 20 minutes, keep stats
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(20 * 60 * 1000L)  // 20 minutes
                try {
                    logDao.deleteAll()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun log(
        packageName: String?,
        domain: String?,
        destIp: String?,
        destPort: Int?,
        protocol: String?,
        action: String
    ) {
        // Write to detailed log
        channel.send(
            TrafficLogEntity(
                packageName = packageName,
                domain = domain,
                destIp = destIp,
                destPort = destPort,
                protocol = protocol,
                action = action
            )
        )

        // Update permanent aggregate stats
        val isBlocked = if (action == "BLOCKED") 1L else 0L
        val isAllowed = if (action == "ALLOWED") 1L else 0L

        // Stats by app
        if (packageName != null) {
            try {
                statsDao.increment("app", packageName, isBlocked, isAllowed)
            } catch (_: Exception) {}
        }

        // Stats by domain
        if (domain != null) {
            try {
                statsDao.increment("domain", domain, isBlocked, isAllowed)
            } catch (_: Exception) {}
        }
    }
}
