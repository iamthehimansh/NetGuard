package com.netguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.netguard.data.entity.AppRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {

    @Query("SELECT * FROM app_rules ORDER BY app_label ASC")
    fun getAllFlow(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE is_blocked = 1")
    fun getBlockedFlow(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): AppRuleEntity?

    @Query("SELECT is_blocked FROM app_rules WHERE package_name = :packageName")
    suspend fun isBlocked(packageName: String): Boolean?

    @Query("SELECT * FROM app_rules WHERE is_blocked = 1")
    suspend fun getAllBlocked(): List<AppRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AppRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<AppRuleEntity>)

    @Query("UPDATE app_rules SET is_blocked = :blocked, updated_at = :now WHERE package_name = :packageName")
    suspend fun setBlocked(packageName: String, blocked: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM app_rules WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM app_rules")
    suspend fun count(): Int
}
