package com.skysense.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.skysense.app.data.db.GnssHistoryEntity
import com.skysense.app.data.repository.GnssRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TimeRange(val label: String, val hours: Int) {
    ONE_HOUR("1h", 1),
    SIX_HOURS("6h", 6),
    ONE_DAY("24h", 24),
    ONE_WEEK("7d", 168)
}

data class HistoryUiState(
    val entries: List<GnssHistoryEntity> = emptyList(),
    val selectedRange: TimeRange = TimeRange.SIX_HOURS,
    val isLoading: Boolean = false,
    val avgAccuracy: Float = 0f,
    val bestAccuracy: Float = 0f,
    val maxSatellites: Int = 0,
    val avgSatellites: Float = 0f
)

class HistoryViewModel(private val repository: GnssRepository) : ViewModel() {

    private val _selectedRange = MutableStateFlow(TimeRange.SIX_HOURS)
    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<HistoryUiState> = _selectedRange.flatMapLatest { range ->
        val since = System.currentTimeMillis() - range.hours * 3_600_000L
        repository.getSnapshotsSince(since).map { entries ->
            val validEntries = entries.filter { it.horizontalAccuracy > 0f }
            HistoryUiState(
                entries = entries,
                selectedRange = range,
                isLoading = false,
                avgAccuracy = if (validEntries.isEmpty()) 0f
                              else validEntries.map { it.horizontalAccuracy }.average().toFloat(),
                bestAccuracy = if (validEntries.isEmpty()) 0f
                               else validEntries.minOf { it.horizontalAccuracy },
                maxSatellites = if (entries.isEmpty()) 0 else entries.maxOf { it.satellitesUsed },
                avgSatellites = if (entries.isEmpty()) 0f
                                else entries.map { it.satellitesUsed }.average().toFloat()
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun selectRange(range: TimeRange) { _selectedRange.value = range }

    fun clearHistory() {
        viewModelScope.launch { repository.clearAllHistory() }
    }

    class Factory(private val repository: GnssRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
    }
}
