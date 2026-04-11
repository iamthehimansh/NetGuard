package com.netguard.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val uid: Int,
    val isSystemApp: Boolean
)

@Singleton
class AppEnumerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getAllInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { hasInternetPermission(pm, it.packageName) }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = try { pm.getApplicationIcon(info) } catch (_: Exception) { null },
                    uid = info.uid,
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun getAppLabel(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun getPackageForUid(uid: Int): String? {
        return context.packageManager.getPackagesForUid(uid)?.firstOrNull()
    }

    private fun hasInternetPermission(pm: PackageManager, packageName: String): Boolean {
        return try {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            info.requestedPermissions?.contains("android.permission.INTERNET") == true
        } catch (_: Exception) {
            false
        }
    }
}
