package com.netguard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netguard.data.dao.TrafficLogDao
import com.netguard.data.entity.TrafficLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrafficLogViewModel @Inject constructor(
    private val trafficLogDao: TrafficLogDao
) : ViewModel() {

    val filterAction = MutableStateFlow<String?>(null)

    val logs: StateFlow<List<TrafficLogEntity>> = filterAction.flatMapLatest { action ->
        if (action != null) {
            trafficLogDao.getByActionFlow(action)
        } else {
            trafficLogDao.getRecentFlow()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(action: String?) {
        filterAction.value = action
    }

    fun clearLog() {
        viewModelScope.launch(Dispatchers.IO) {
            trafficLogDao.deleteAll()
        }
    }
}
