package com.netguard.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val HOME = "home"
    const val APP_LIST = "app_list"
    const val DOMAIN_RULES = "domain_rules"
    const val TRAFFIC_LOG = "traffic_log"
    const val SETTINGS = "settings"
    const val VPN_CONFIG = "vpn_config"
    const val CONNECTION_MODE = "connection_mode"
}

@Composable
fun NetGuardNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToApps = { navController.navigate(Routes.APP_LIST) },
                onNavigateToDomains = { navController.navigate(Routes.DOMAIN_RULES) },
                onNavigateToLog = { navController.navigate(Routes.TRAFFIC_LOG) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToVpnConfig = { navController.navigate(Routes.VPN_CONFIG) },
                onNavigateToConnectionMode = { navController.navigate(Routes.CONNECTION_MODE) }
            )
        }
        composable(Routes.APP_LIST) {
            AppListScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOMAIN_RULES) {
            DomainRulesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TRAFFIC_LOG) {
            TrafficLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.VPN_CONFIG) {
            VpnConfigScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONNECTION_MODE) {
            ConnectionModeScreen(onBack = { navController.popBackStack() })
        }
    }
}
