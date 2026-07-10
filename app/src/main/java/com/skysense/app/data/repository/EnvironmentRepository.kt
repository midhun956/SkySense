package com.skysense.app.data.repository

import com.skysense.app.data.model.EnvironmentData
import com.skysense.app.data.remote.EnvironmentApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EnvironmentRepository(private val apiClient: EnvironmentApiClient) {

    private val _environmentData = MutableStateFlow<EnvironmentData?>(null)
    val environmentData: StateFlow<EnvironmentData?> = _environmentData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun fetchContext(lat: Double, lon: Double) {
        if (_isLoading.value) return
        _isLoading.value = true
        val result = apiClient.fetchContext(lat, lon)
        result.onSuccess { data ->
            _environmentData.value = data
        }
        _isLoading.value = false
    }
}
