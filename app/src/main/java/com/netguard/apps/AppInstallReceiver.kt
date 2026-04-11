package com.netguard.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netguard.data.AppDatabase
import com.netguard.data.entity.AppRuleEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppInstallReceiver : BroadcastReceiver() {

    @Inject lateinit var database: AppDatabase
    @Inject lateinit var appEnumerator: AppEnumerator

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        CoroutineScope(Dispatchers.IO).launch {
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> {
                    val label = appEnumerator.getAppLabel(packageName)
                    database.appRuleDao().upsert(
                        AppRuleEntity(
                            packageName = packageName,
                            appLabel = label,
                            isBlocked = false
                        )
                    )
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    database.appRuleDao().delete(packageName)
                }
            }
        }
    }
}
