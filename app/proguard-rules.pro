# NetGuard ProGuard Rules

# Keep VpnService
-keep class com.netguard.vpn.NetGuardVpnService { *; }

# Keep Room entities
-keep class com.netguard.data.entity.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
