package com.netguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netguard.data.entity.ComboRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComboRuleDao {

    @Query("SELECT * FROM combo_rules ORDER BY package_name ASC, domain_pattern ASC")
    fun getAllFlow(): Flow<List<ComboRuleEntity>>

    @Query("SELECT * FROM combo_rules WHERE package_name = :packageName")
    fun getByPackageFlow(packageName: String): Flow<List<ComboRuleEntity>>

    @Query("SELECT is_blocked FROM combo_rules WHERE package_name = :packageName AND domain_pattern = :domainPattern")
    suspend fun isBlocked(packageName: String, domainPattern: String): Boolean?

    @Query("SELECT * FROM combo_rules WHERE package_name = :packageName")
    suspend fun getByPackage(packageName: String): List<ComboRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ComboRuleEntity)

    @Query("DELETE FROM combo_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM combo_rules")
    suspend fun count(): Int
}
