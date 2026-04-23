package com.netguard.di

import android.content.Context
import com.netguard.data.AppDatabase
import com.netguard.data.dao.AppRuleDao
import com.netguard.data.dao.ComboRuleDao
import com.netguard.data.dao.DomainRuleDao
import com.netguard.data.dao.TrafficLogDao
import com.netguard.data.dao.TrafficStatsDao
import com.netguard.data.dao.VpnServerConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides fun provideAppRuleDao(db: AppDatabase): AppRuleDao = db.appRuleDao()
    @Provides fun provideDomainRuleDao(db: AppDatabase): DomainRuleDao = db.domainRuleDao()
    @Provides fun provideComboRuleDao(db: AppDatabase): ComboRuleDao = db.comboRuleDao()
    @Provides fun provideTrafficLogDao(db: AppDatabase): TrafficLogDao = db.trafficLogDao()
    @Provides fun provideTrafficStatsDao(db: AppDatabase): TrafficStatsDao = db.trafficStatsDao()
    @Provides fun provideVpnServerConfigDao(db: AppDatabase): VpnServerConfigDao = db.vpnServerConfigDao()
}
