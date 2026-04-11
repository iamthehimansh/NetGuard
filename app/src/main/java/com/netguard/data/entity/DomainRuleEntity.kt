package com.netguard.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "domain_rules",
    indices = [Index(value = ["domain_pattern"], unique = true)]
)
data class DomainRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "domain_pattern")
    val domainPattern: String,

    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = true,

    @ColumnInfo(name = "is_wildcard")
    val isWildcard: Boolean = false,

    @ColumnInfo(name = "source")
    val source: String = "user",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
