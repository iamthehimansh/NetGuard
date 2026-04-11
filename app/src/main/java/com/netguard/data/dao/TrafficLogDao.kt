package com.netguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.netguard.data.entity.TrafficLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficLogDao {

    @Query("SELECT * FROM traffic_log ORDER BY id DESC LIMIT :limit")
    fun getRecentFlow(limit: Int = 500): Flow<List<TrafficLogEntity>>

    @Query("SELECT * FROM traffic_log WHERE package_name = :packageName ORDER BY id DESC LIMIT :limit")
    fun getByPackageFlow(packageName: String, limit: Int = 200): Flow<List<TrafficLogEntity>>

    @Query("SELECT * FROM traffic_log WHERE action = :action ORDER BY id DESC LIMIT :limit")
    fun getByActionFlow(action: String, limit: Int = 500): Flow<List<TrafficLogEntity>>

    @Insert
    suspend fun insert(log: TrafficLogEntity)

    @Transaction
    suspend fun batchInsert(logs: List<TrafficLogEntity>) {
        logs.forEach { insert(it) }
    }

    @Query("DELETE FROM traffic_log WHERE timestamp < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long)

    @Query("DELETE FROM traffic_log")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM traffic_log")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM traffic_log WHERE action = 'BLOCKED'")
    suspend fun blockedCount(): Int

    @Query("SELECT COUNT(*) FROM traffic_log WHERE action = 'ALLOWED'")
    suspend fun allowedCount(): Int
}
