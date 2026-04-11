package com.netguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netguard.data.dao.DomainRuleDao
import com.netguard.data.entity.DomainRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DomainRulesViewModel @Inject constructor(
    private val domainRuleDao: DomainRuleDao
) : ViewModel() {

    val rules: StateFlow<List<DomainRuleEntity>> = domainRuleDao.getUserRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(pattern: String, blocked: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val isWildcard = pattern.startsWith("*.")
            domainRuleDao.upsert(
                DomainRuleEntity(
                    domainPattern = pattern,
                    isBlocked = blocked,
                    isWildcard = isWildcard,
                    source = "user"
                )
            )
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            domainRuleDao.deleteById(id)
        }
    }

    fun toggleRule(rule: DomainRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            domainRuleDao.upsert(rule.copy(isBlocked = !rule.isBlocked))
        }
    }
}
