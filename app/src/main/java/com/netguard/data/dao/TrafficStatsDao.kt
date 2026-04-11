package com.netguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netguard.data.entity.TrafficStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficStatsDao {

    @Query("SELECT * FROM traffic_stats WHERE key_type = 'app' ORDER BY blocked_count + allowed_count DESC")
    fun getAppStatsFlow(): Flow<List<TrafficStatsEntity>>

    @Query("SELECT * FROM traffic_stats WHERE key_type = 'domain' ORDER BY blocked_count + allowed_count DESC LIMIT 100")
    fun getTopDomainsFlow(): Flow<List<TrafficStatsEntity>>

    @Query("SELECT SUM(blocked_count) FROM traffic_stats")
    suspend fun totalBlocked(): Long?

    @Query("SELECT SUM(allowed_count) FROM traffic_stats")
    suspend fun totalAllowed(): Long?

    @Query("""
        INSERT INTO traffic_stats (key_type, key_value, blocked_count, allowed_count, last_seen)
        VALUES (:keyType, :keyValue, :blocked, :allowed, :now)
        ON CONFLICT(key_type, key_value) DO UPDATE SET
            blocked_count = blocked_count + :blocked,
            allowed_count = allowed_count + :allowed,
            last_seen = :now
    """)
    suspend fun increment(keyType: String, keyValue: String, blocked: Long, allowed: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM traffic_stats")
    suspend fun deleteAll()
}
