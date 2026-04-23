package com.netguard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netguard.data.entity.VpnServerConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnServerConfigDao {

    @Query("SELECT * FROM vpn_server_config WHERE id = 1")
    fun getConfigFlow(): Flow<VpnServerConfigEntity?>

    @Query("SELECT * FROM vpn_server_config WHERE id = 1")
    suspend fun getConfig(): VpnServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: VpnServerConfigEntity)

    @Query("DELETE FROM vpn_server_config")
    suspend fun delete()
}
