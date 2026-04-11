package com.netguard.ui.viewmodel

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netguard.apps.AppEnumerator
import com.netguard.apps.InstalledApp
import com.netguard.data.dao.AppRuleDao
import com.netguard.data.entity.AppRuleEntity
import com.netguard.vpn.VpnConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRuleUi(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isBlocked: Boolean,
    val isSystemApp: Boolean
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    application: Application,
    private val appRuleDao: AppRuleDao,
    private val appEnumerator: AppEnumerator
) : AndroidViewModel(application) {

    val searchQuery = MutableStateFlow("")

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Cache installed apps — loaded once on init
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())

    val apps: StateFlow<List<AppRuleUi>> = combine(
        _installedApps,
        appRuleDao.getAllFlow(),
        searchQuery
    ) { installed, rules, query ->
        if (installed.isEmpty()) return@combine emptyList()

        val ruleMap = rules.associateBy { it.packageName }
        installed
            .map { app ->
                val rule = ruleMap[app.packageName]
                AppRuleUi(
                    packageName = app.packageName,
                    label = app.label,
                    icon = app.icon,
                    isBlocked = rule?.isBlocked ?: false,
                    isSystemApp = app.isSystemApp
                )
            }
            .filter { app ->
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            val installed = appEnumerator.getAllInstalledApps()

            // Sync to database on first run
            val existing = appRuleDao.count()
            if (existing == 0) {
                val rules = installed.map { app ->
                    AppRuleEntity(
                        packageName = app.packageName,
                        appLabel = app.label,
                        isBlocked = false,
                        isSystemApp = app.isSystemApp
                    )
                }
                appRuleDao.upsertAll(rules)
            }

            _installedApps.value = installed
            _isLoading.value = false
        }
    }

    fun toggleBlock(packageName: String, blocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = appRuleDao.getByPackageName(packageName)
            if (existing != null) {
                appRuleDao.setBlocked(packageName, blocked)
            } else {
                val label = appEnumerator.getAppLabel(packageName)
                appRuleDao.upsert(
                    AppRuleEntity(
                        packageName = packageName,
                        appLabel = label,
                        isBlocked = blocked
                    )
                )
            }

            // Reload VPN rules so the blocked/unblocked app takes effect immediately
            VpnConnectionManager.reload(getApplication())
        }
    }

    fun updateSearch(query: String) {
        searchQuery.value = query
    }
}
