# NetGuard ProGuard Rules

# Keep VpnService
-keep class com.netguard.vpn.NetGuardVpnService { *; }

# Keep Room entities and DAOs
-keep class com.netguard.data.entity.** { *; }
-keep class com.netguard.data.dao.** { *; }
-keep class com.netguard.data.AppDatabase { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep BroadcastReceivers
-keep class com.netguard.receiver.BootReceiver { *; }
-keep class com.netguard.apps.AppInstallReceiver { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Room
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
