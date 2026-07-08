package com.skysense.app

import android.app.Application
import com.skysense.app.data.db.AppDatabase
import com.skysense.app.data.remote.GeminiApiClient
import com.skysense.app.data.repository.GnssRepository
import com.skysense.app.data.sensor.CompassManager
import com.skysense.app.data.store.SecurePreferencesManager
import com.skysense.app.service.GnssDataService

/**
 * Application class acting as simple manual DI container.
 * All singletons are created lazily and shared across the app.
 */
class SkySenseApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    val gnssDataService by lazy { GnssDataService(this) }

    val gnssRepository by lazy {
        GnssRepository(
            gnssDataService = gnssDataService,
            gnssHistoryDao = database.gnssHistoryDao(),
            satelliteHistoryDao = database.satelliteHistoryDao()
        )
    }

    val prefsManager by lazy { SecurePreferencesManager(this) }

    val compassManager by lazy { CompassManager(this) }

    val geminiClient by lazy { GeminiApiClient() }
}
