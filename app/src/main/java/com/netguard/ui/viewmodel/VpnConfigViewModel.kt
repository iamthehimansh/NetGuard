package com.netguard.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguard.data.dao.VpnServerConfigDao
import com.netguard.data.entity.VpnServerConfigEntity
import com.netguard.vpn.SingBoxConfigGenerator
import com.netguard.vpn.TunnelMode
import com.netguard.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VpnConfigState(
    val serverAddress: String = "vpn.himansh.in",
    val serverPort: String = "443",
    val uuid: String = "",
    val wsPath: String = "/netguard",
    val sni: String = "vpn.himansh.in",
    val alpn: String = "http/1.1",
    val fingerprint: String = "chrome",
    val saved: Boolean = false,
    val importError: String? = null
)

@HiltViewModel
class VpnConfigViewModel @Inject constructor(
    application: Application,
    private val vpnServerConfigDao: VpnServerConfigDao
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(VpnConfigState())
    val state: StateFlow<VpnConfigState> = _state.asStateFlow()

    private val _tunnelMode = MutableStateFlow(loadTunnelMode())
    val tunnelMode: StateFlow<TunnelMode> = _tunnelMode.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = vpnServerConfigDao.getConfig()
            if (config != null) {
                _state.value = VpnConfigState(
                    serverAddress = config.serverAddress,
                    serverPort = config.serverPort.toString(),
                    uuid = config.uuid,
                    wsPath = config.wsPath,
                    sni = config.sni,
                    alpn = config.alpn,
                    fingerprint = config.fingerprint,
                    saved = true
                )
            }
        }
    }

    fun updateField(field: String, value: String) {
        _state.value = when (field) {
            "serverAddress" -> _state.value.copy(serverAddress = value, saved = false)
            "serverPort" -> _state.value.copy(serverPort = value, saved = false)
            "uuid" -> _state.value.copy(uuid = value, saved = false)
            "wsPath" -> _state.value.copy(wsPath = value, saved = false)
            "sni" -> _state.value.copy(sni = value, saved = false)
            "alpn" -> _state.value.copy(alpn = value, saved = false)
            "fingerprint" -> _state.value.copy(fingerprint = value, saved = false)
            else -> _state.value
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value
            vpnServerConfigDao.upsert(
                VpnServerConfigEntity(
                    serverAddress = s.serverAddress,
                    serverPort = s.serverPort.toIntOrNull() ?: 443,
                    uuid = s.uuid,
                    wsPath = s.wsPath,
                    sni = s.sni,
                    alpn = s.alpn,
                    fingerprint = s.fingerprint
                )
            )
            _state.value = s.copy(saved = true)
        }
    }

    fun importVlessLink(link: String) {
        val parsed = SingBoxConfigGenerator.parseVlessLink(link)
        if (parsed != null) {
            _state.value = VpnConfigState(
                serverAddress = parsed.serverAddress,
                serverPort = parsed.serverPort.toString(),
                uuid = parsed.uuid,
                wsPath = parsed.wsPath,
                sni = parsed.sni,
                alpn = parsed.alpn,
                fingerprint = parsed.fingerprint,
                saved = false,
                importError = null
            )
        } else {
            _state.value = _state.value.copy(importError = "Invalid VLESS link")
        }
    }

    fun setTunnelMode(mode: TunnelMode) {
        _tunnelMode.value = mode
        val ctx: Context = getApplication()
        ctx.getSharedPreferences("netguard_settings", Context.MODE_PRIVATE)
            .edit().putString("tunnel_mode", mode.name).apply()

        // Reload VPN with new mode
        VpnConnectionManager.reload(ctx)
    }

    private fun loadTunnelMode(): TunnelMode {
        val ctx: Context = getApplication()
        val name = ctx.getSharedPreferences("netguard_settings", Context.MODE_PRIVATE)
            .getString("tunnel_mode", TunnelMode.DNS_FILTER_ONLY.name)
        return try { TunnelMode.valueOf(name!!) } catch (_: Exception) { TunnelMode.DNS_FILTER_ONLY }
    }
}
