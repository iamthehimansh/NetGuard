package com.netguard.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("netguard_settings", Context.MODE_PRIVATE)

    private val _dnsServer = MutableStateFlow(prefs.getString("dns_server", "8.8.8.8") ?: "8.8.8.8")
    val dnsServer: StateFlow<String> = _dnsServer

    private val _defaultBlock = MutableStateFlow(prefs.getBoolean("default_block", false))
    val defaultBlock: StateFlow<Boolean> = _defaultBlock

    private val _autoStart = MutableStateFlow(prefs.getBoolean("auto_start", false))
    val autoStart: StateFlow<Boolean> = _autoStart

    fun setDnsServer(server: String) {
        _dnsServer.value = server
        prefs.edit().putString("dns_server", server).apply()
    }

    fun setDefaultBlock(block: Boolean) {
        _defaultBlock.value = block
        prefs.edit().putBoolean("default_block", block).apply()
    }

    fun setAutoStart(enabled: Boolean) {
        _autoStart.value = enabled
        prefs.edit().putBoolean("auto_start", enabled).apply()
    }
}
