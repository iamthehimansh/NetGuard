package com.netguard.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "combo_rules",
    indices = [Index(value = ["package_name", "domain_pattern"], unique = true)]
)
data class ComboRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "domain_pattern")
    val domainPattern: String,

    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
