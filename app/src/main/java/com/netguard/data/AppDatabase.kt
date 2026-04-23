package com.netguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.netguard.data.dao.AppRuleDao
import com.netguard.data.dao.ComboRuleDao
import com.netguard.data.dao.DomainRuleDao
import com.netguard.data.dao.TrafficLogDao
import com.netguard.data.dao.TrafficStatsDao
import com.netguard.data.dao.VpnServerConfigDao
import com.netguard.data.entity.AppRuleEntity
import com.netguard.data.entity.ComboRuleEntity
import com.netguard.data.entity.DomainRuleEntity
import com.netguard.data.entity.TrafficLogEntity
import com.netguard.data.entity.TrafficStatsEntity
import com.netguard.data.entity.VpnServerConfigEntity

@Database(
    entities = [
        AppRuleEntity::class,
        DomainRuleEntity::class,
        ComboRuleEntity::class,
        TrafficLogEntity::class,
        TrafficStatsEntity::class,
        VpnServerConfigEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun domainRuleDao(): DomainRuleDao
    abstract fun comboRuleDao(): ComboRuleDao
    abstract fun trafficLogDao(): TrafficLogDao
    abstract fun trafficStatsDao(): TrafficStatsDao
    abstract fun vpnServerConfigDao(): VpnServerConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netguard-db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
