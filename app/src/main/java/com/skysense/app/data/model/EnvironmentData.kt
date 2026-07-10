package com.skysense.app.data.model

data class EnvironmentData(
    val locationName: String? = null,
    val temperatureC: Double? = null,
    val weatherCondition: String? = null,
    val elevationMeters: Double? = null,
    val isOfflineMode: Boolean = false
)
