package com.netguard.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores aggregate traffic counts per app and per domain.
 * Persists across log clears — this is the permanent record.
 */
@Entity(
    tableName = "traffic_stats",
    indices = [Index(value = ["key_type", "key_value"], unique = true)]
)
data class TrafficStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "key_type")
    val keyType: String,          // "app" or "domain"

    @ColumnInfo(name = "key_value")
    val keyValue: String,         // package name or domain

    @ColumnInfo(name = "blocked_count")
    val blockedCount: Long = 0,

    @ColumnInfo(name = "allowed_count")
    val allowedCount: Long = 0,

    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = System.currentTimeMillis()
)
