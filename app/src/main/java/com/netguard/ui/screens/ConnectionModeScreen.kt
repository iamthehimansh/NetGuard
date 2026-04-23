package com.netguard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netguard.ui.viewmodel.VpnConfigViewModel
import com.netguard.vpn.TunnelMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionModeScreen(
    onBack: () -> Unit,
    viewModel: VpnConfigViewModel = hiltViewModel()
) {
    val currentMode by viewModel.tunnelMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Mode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select how NetGuard handles your traffic:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))

            ModeOption(
                title = "DNS Filter Only",
                description = "Block apps and domains via DNS interception. No VPN tunnel. Lightweight, low battery usage.",
                selected = currentMode == TunnelMode.DNS_FILTER_ONLY,
                onClick = { viewModel.setTunnelMode(TunnelMode.DNS_FILTER_ONLY) }
            )

            ModeOption(
                title = "Direct VPN",
                description = "All traffic tunneled to vpn.himansh.in via VLESS. No local filtering. Fast, full tunnel. DNS via Pi-hole on server.",
                selected = currentMode == TunnelMode.DIRECT,
                onClick = { viewModel.setTunnelMode(TunnelMode.DIRECT) }
            )

            ModeOption(
                title = "Managed VPN",
                description = "Local DNS filter + per-app rules applied first, then allowed traffic tunneled to server. Best of both worlds.",
                selected = currentMode == TunnelMode.MANAGED,
                onClick = { viewModel.setTunnelMode(TunnelMode.MANAGED) }
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Direct and Managed modes require a VPN server configuration. " +
                            "Go to Settings > VPN Server Config to set up your server.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "For Always-on VPN, go to Android Settings > Network > VPN > NetGuard > toggle Always-on.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
