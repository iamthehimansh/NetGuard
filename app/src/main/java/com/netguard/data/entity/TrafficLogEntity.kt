package com.netguard.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traffic_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["package_name"])
    ]
)
data class TrafficLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "package_name")
    val packageName: String? = null,

    @ColumnInfo(name = "domain")
    val domain: String? = null,

    @ColumnInfo(name = "dest_ip")
    val destIp: String? = null,

    @ColumnInfo(name = "dest_port")
    val destPort: Int? = null,

    @ColumnInfo(name = "protocol")
    val protocol: String? = null,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "bytes_out")
    val bytesOut: Long = 0,

    @ColumnInfo(name = "bytes_in")
    val bytesIn: Long = 0
)
