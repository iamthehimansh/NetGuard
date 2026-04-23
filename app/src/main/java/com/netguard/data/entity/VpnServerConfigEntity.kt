package com.netguard.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_server_config")
data class VpnServerConfigEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "server_address")
    val serverAddress: String = "vpn.himansh.in",

    @ColumnInfo(name = "server_port")
    val serverPort: Int = 443,

    @ColumnInfo(name = "uuid")
    val uuid: String = "",

    @ColumnInfo(name = "ws_path")
    val wsPath: String = "/netguard",

    @ColumnInfo(name = "sni")
    val sni: String = "vpn.himansh.in",

    @ColumnInfo(name = "alpn")
    val alpn: String = "http/1.1",

    @ColumnInfo(name = "fingerprint")
    val fingerprint: String = "chrome",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
