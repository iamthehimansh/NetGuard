package com.netguard.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguard.data.dao.AppRuleDao
import com.netguard.data.dao.TrafficStatsDao
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val allowedRequests: Long = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val trafficStatsDao: TrafficStatsDao,
    private val appRuleDao: AppRuleDao
) : AndroidViewModel(application) {

    private val _vpnStatus = MutableStateFlow(VpnStatus())
    val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

    init {
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

        // Poll permanent stats every 3 seconds
        viewModelScope.launch {
            while (true) {
                try {
                    val blocked = trafficStatsDao.totalBlocked() ?: 0
                    val allowed = trafficStatsDao.totalAllowed() ?: 0
                    _vpnStatus.value = _vpnStatus.value.copy(
                        blockedRequests = blocked,
                        allowedRequests = allowed
                    )
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    fun setVpnActive(active: Boolean) {
        _vpnStatus.value = _vpnStatus.value.copy(isActive = active)
    }
}
