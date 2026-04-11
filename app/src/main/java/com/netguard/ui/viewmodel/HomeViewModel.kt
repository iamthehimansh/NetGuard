package com.netguard.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguard.data.dao.AppRuleDao
import com.netguard.data.dao.DomainRuleDao
import com.netguard.data.dao.TrafficStatsDao
import com.netguard.dns.AdBlockManager
import com.netguard.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VpnStatus(
    val isActive: Boolean = false,
    val blockedApps: Int = 0,
    val allowedApps: Int = 0,
    val blockedRequests: Long = 0,
    val allowedRequests: Long = 0,
    val adBlockEnabled: Boolean = false,
    val adBlockLoading: Boolean = false,
    val adBlockDomains: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val trafficStatsDao: TrafficStatsDao,
    private val appRuleDao: AppRuleDao,
    private val domainRuleDao: DomainRuleDao
) : AndroidViewModel(application) {

    private val _vpnStatus = MutableStateFlow(VpnStatus())
    val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

    private val adBlockManager = AdBlockManager(application, domainRuleDao)

    init {
        // Load initial ad block state
        _vpnStatus.value = _vpnStatus.value.copy(
            adBlockEnabled = adBlockManager.isEnabled()
        )

        // Watch app rules reactively
        viewModelScope.launch {
            appRuleDao.getAllFlow().collect { rules ->
                val blocked = rules.count { it.isBlocked }
                _vpnStatus.value = _vpnStatus.value.copy(
                    blockedApps = blocked,
                    allowedApps = rules.size - blocked
                )
            }
        }

        // Poll permanent stats + adblock count every 3 seconds
        viewModelScope.launch {
            while (true) {
                try {
                    val blocked = trafficStatsDao.totalBlocked() ?: 0
                    val allowed = trafficStatsDao.totalAllowed() ?: 0
                    val adCount = domainRuleDao.countBySource("adblock")
                    _vpnStatus.value = _vpnStatus.value.copy(
                        blockedRequests = blocked,
                        allowedRequests = allowed,
                        adBlockDomains = adCount
                    )
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    fun setVpnActive(active: Boolean) {
        _vpnStatus.value = _vpnStatus.value.copy(isActive = active)
    }

    fun toggleAdBlock(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _vpnStatus.value = _vpnStatus.value.copy(adBlockLoading = true)

            if (enabled) {
                adBlockManager.enable()
            } else {
                adBlockManager.disable()
            }

            val count = domainRuleDao.countBySource("adblock")
            _vpnStatus.value = _vpnStatus.value.copy(
                adBlockEnabled = enabled,
                adBlockLoading = false,
                adBlockDomains = count
            )

            // Reload VPN to pick up new domain rules
            val ctx: Context = getApplication()
            VpnConnectionManager.reload(ctx)
        }
    }
}
