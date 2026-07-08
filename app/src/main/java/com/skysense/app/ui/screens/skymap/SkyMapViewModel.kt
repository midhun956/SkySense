package com.skysense.app.ui.screens.skymap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.skysense.app.data.model.SatelliteInfo
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.data.sensor.CompassManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

data class SkyMapUiState(
    val satellites: List<SatelliteInfo> = emptyList(),
    val selectedSatellite: SatelliteInfo? = null,
    val isReceivingUpdates: Boolean = false
)

class SkyMapViewModel(
    repository: GnssRepository,
    compassManager: CompassManager
) : ViewModel() {

    private val _selectedSatellite = MutableStateFlow<SatelliteInfo?>(null)

    val satellites: StateFlow<List<SatelliteInfo>> = repository.liveSatellites.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val selectedSatellite: StateFlow<SatelliteInfo?> = _selectedSatellite.asStateFlow()

    val isReceivingUpdates: StateFlow<Boolean> = repository.isReceivingUpdates.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val heading: StateFlow<Float> = compassManager.heading.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0f
    )

    fun selectSatellite(sat: SatelliteInfo?) { _selectedSatellite.value = sat }

    class Factory(
        private val repository: GnssRepository,
        private val compassManager: CompassManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SkyMapViewModel(repository, compassManager) as T
        }
    }
}
