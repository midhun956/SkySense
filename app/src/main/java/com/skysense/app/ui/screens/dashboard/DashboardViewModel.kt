package com.skysense.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.skysense.app.data.model.EnvironmentData
import com.skysense.app.data.model.GnssSnapshot
import com.skysense.app.data.model.SatelliteInfo
import com.skysense.app.data.repository.EnvironmentRepository
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.domain.LocalInterpretationEngine
import com.skysense.app.domain.OverviewCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DashboardUiState(
    val snapshot: GnssSnapshot = GnssSnapshot(),
    val satellites: List<SatelliteInfo> = emptyList(),
    val overviewCard: OverviewCard = LocalInterpretationEngine.buildOverviewCard(GnssSnapshot()),
    val isReceivingUpdates: Boolean = false,
    val isLocationEnabled: Boolean = true,
    val environmentData: EnvironmentData? = null,
    val isRefreshingEnvironment: Boolean = false
)

class DashboardViewModel(
    private val repository: GnssRepository,
    private val environmentRepository: EnvironmentRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.liveSnapshot,
        repository.liveSatellites,
        repository.isReceivingUpdates,
        repository.isLocationEnabled,
        environmentRepository.environmentData
    ) { snapshot, satellites, receiving, locationEnabled, envData ->
        DashboardUiState(
            snapshot = snapshot,
            satellites = satellites,
            overviewCard = LocalInterpretationEngine.buildOverviewCard(snapshot),
            isReceivingUpdates = receiving,
            isLocationEnabled = locationEnabled,
            environmentData = envData
        )
    }.combine(environmentRepository.isLoading) { state, envLoading ->
        state.copy(isRefreshingEnvironment = envLoading)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    init {
        repository.liveSnapshot.onEach { snapshot ->
            if (snapshot.hasValidFix && environmentRepository.environmentData.value == null) {
                environmentRepository.fetchContext(snapshot.latitude, snapshot.longitude)
            }
        }.launchIn(viewModelScope)
    }

    fun refreshEnvironment() {
        viewModelScope.launch {
            val currentSnapshot = repository.liveSnapshot.value
            if (currentSnapshot.hasValidFix) {
                environmentRepository.fetchContext(currentSnapshot.latitude, currentSnapshot.longitude)
            }
        }
    }

    class Factory(
        private val repository: GnssRepository,
        private val environmentRepository: EnvironmentRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository, environmentRepository) as T
        }
    }
}
