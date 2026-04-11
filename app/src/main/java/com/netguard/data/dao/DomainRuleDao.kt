package com.netguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netguard.data.entity.DomainRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainRuleDao {

    @Query("SELECT * FROM domain_rules WHERE source = 'user' ORDER BY domain_pattern ASC")
    fun getUserRulesFlow(): Flow<List<DomainRuleEntity>>

    @Query("SELECT * FROM domain_rules ORDER BY domain_pattern ASC")
    fun getAllFlow(): Flow<List<DomainRuleEntity>>

    @Query("SELECT * FROM domain_rules WHERE is_blocked = 1")
    suspend fun getAllBlocked(): List<DomainRuleEntity>

    @Query("SELECT * FROM domain_rules WHERE domain_pattern = :pattern")
    suspend fun getByPattern(pattern: String): DomainRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: DomainRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<DomainRuleEntity>)

    @Query("DELETE FROM domain_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM domain_rules WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT COUNT(*) FROM domain_rules")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM domain_rules WHERE source != 'user'")
    suspend fun blocklistCount(): Int

    @Query("SELECT COUNT(*) FROM domain_rules WHERE source = :source")
    suspend fun countBySource(source: String): Int
}
