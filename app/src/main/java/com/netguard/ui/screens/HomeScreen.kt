package com.netguard.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DomainVerification
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netguard.ui.viewmodel.HomeViewModel
import com.netguard.vpn.VpnConnectionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToDomains: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val status by viewModel.vpnStatus.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VpnConnectionManager.start(context)
            viewModel.setVpnActive(true)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NetGuard") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // VPN Status Card
            Card(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.isActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Shield, "VPN Status",
                        modifier = Modifier.size(40.dp),
                        tint = if (status.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (status.isActive) "Firewall Active" else "Firewall Inactive",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Ad Blocker Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.adBlockEnabled)
                        Color(0xFF1B5E20).copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AdsClick,
                        "Ad Blocker",
                        modifier = Modifier.size(32.dp),
                        tint = if (status.adBlockEnabled) Color(0xFF66BB6A)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Block All Ads",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (status.adBlockLoading) {
                            Text(
                                "Downloading blocklist...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (status.adBlockEnabled && status.adBlockDomains > 0) {
                            Text(
                                "${status.adBlockDomains} ad domains blocked",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF66BB6A)
                            )
                        } else {
                            Text(
                                "Block ads, trackers & malware across all apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (status.adBlockLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = status.adBlockEnabled,
                            onCheckedChange = { viewModel.toggleAdBlock(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF66BB6A),
                                checkedTrackColor = Color(0xFF1B5E20)
                            )
                        )
                    }
                }
            }

            // Stats Cards
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x30FF0000))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Block, "Blocked",
                            tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                        Text("${status.blockedApps}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                        Text("Apps Blocked", style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f).height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x3000FF00))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, "Allowed",
                            tint = Color(0xFF388E3C), modifier = Modifier.size(20.dp))
                        Text("${status.allowedApps}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                        Text("Apps Allowed", style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center)
                    }
                }
            }

            // Request stats
            if (status.blockedRequests > 0 || status.allowedRequests > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("${status.blockedRequests} blocked  |  ${status.allowedRequests} allowed",
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Start / Stop Button
            Button(
                onClick = {
                    if (status.isActive) {
                        VpnConnectionManager.stop(context)
                        viewModel.setVpnActive(false)
                    } else {
                        val intent = VpnService.prepare(context)
                        if (intent != null) vpnLauncher.launch(intent)
                        else {
                            VpnConnectionManager.start(context)
                            viewModel.setVpnActive(true)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status.isActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (status.isActive) "Stop Firewall" else "Start Firewall",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Navigation Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavButton(Icons.Default.Apps, "Apps", onNavigateToApps, Modifier.weight(1f))
                NavButton(Icons.Default.DomainVerification, "Domains", onNavigateToDomains, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavButton(Icons.Default.History, "Traffic Log", onNavigateToLog, Modifier.weight(1f))
                NavButton(Icons.Default.Settings, "Settings", onNavigateToSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(60.dp), shape = RoundedCornerShape(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
