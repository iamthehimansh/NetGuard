package com.netguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netguard.data.entity.TrafficLogEntity
import com.netguard.ui.viewmodel.TrafficLogViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficLogScreen(
    onBack: () -> Unit,
    viewModel: TrafficLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val filter by viewModel.filterAction.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traffic Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filter == "BLOCKED",
                    onClick = { viewModel.setFilter(if (filter == "BLOCKED") null else "BLOCKED") },
                    label = { Text("Blocked") }
                )
                FilterChip(
                    selected = filter == "ALLOWED",
                    onClick = { viewModel.setFilter(if (filter == "ALLOWED") null else "ALLOWED") },
                    label = { Text("Allowed") }
                )
            }

            if (logs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No traffic logged yet", style = MaterialTheme.typography.bodyLarge)
                    Text("Start the firewall to begin logging", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: TrafficLogEntity) {
    val isBlocked = log.action == "BLOCKED"
    val bgColor = if (isBlocked) Color(0x20FF0000) else Color(0x2000FF00)
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Build the display text — show whatever info we have
    val target = log.domain ?: log.destIp ?: "unknown"
    val source = log.packageName ?: ""
    val displayText = if (source.isNotEmpty()) "$source -> $target" else target

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (isBlocked) Icons.Default.Block else Icons.Default.Check,
            log.action,
            modifier = Modifier.size(18.dp),
            tint = if (isBlocked) Color(0xFFD32F2F) else Color(0xFF388E3C)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (log.protocol != null) {
                    Text(
                        log.protocol,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (log.destPort != null && log.destPort > 0) {
                    Text(
                        ":${log.destPort}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    timeFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            if (isBlocked) "BLOCKED" else "ALLOWED",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isBlocked) Color(0xFFD32F2F) else Color(0xFF388E3C)
        )
    }
}
