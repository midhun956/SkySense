package com.skysense.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.skysense.app.data.model.GnssSnapshot
import com.skysense.app.data.model.SatelliteInfo
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.LocalInterpretationEngine
import com.skysense.app.domain.OverviewCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val snapshot: GnssSnapshot = GnssSnapshot(),
    val satellites: List<SatelliteInfo> = emptyList(),
    val overviewCard: OverviewCard = LocalInterpretationEngine.buildOverviewCard(GnssSnapshot()),
    val isReceivingUpdates: Boolean = false,
    val isLocationEnabled: Boolean = true
)

class DashboardViewModel(private val repository: GnssRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.liveSnapshot,
        repository.liveSatellites,
        repository.isReceivingUpdates,
        repository.isLocationEnabled
    ) { snapshot, satellites, receiving, locationEnabled ->
        DashboardUiState(
            snapshot = snapshot,
            satellites = satellites,
            overviewCard = LocalInterpretationEngine.buildOverviewCard(snapshot),
            isReceivingUpdates = receiving,
            isLocationEnabled = locationEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    class Factory(private val repository: GnssRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
    }
}
