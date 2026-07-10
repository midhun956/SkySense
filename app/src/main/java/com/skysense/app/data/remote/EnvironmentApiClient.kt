package com.skysense.app.data.remote

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.skysense.app.data.model.EnvironmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

class EnvironmentApiClient(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun fetchContext(lat: Double, lon: Double): Result<EnvironmentData> = withContext(Dispatchers.IO) {
        try {
            var locationName: String? = null
            try {
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    locationName = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.countryName
                }
            } catch (e: Exception) {
                Log.w("EnvironmentApiClient", "Geocoder failed", e)
            }

            // Fetch weather and elevation from Open-Meteo
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&elevation=nan"
            val request = Request.Builder().url(url).build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.success(EnvironmentData(locationName = locationName, isOfflineMode = true))
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty body")
            val json = gson.fromJson(bodyString, JsonObject::class.java)

            val elevation = json.get("elevation")?.asDouble
            val current = json.getAsJsonObject("current")
            val temp = current?.get("temperature_2m")?.asDouble
            val weatherCode = current?.get("weather_code")?.asInt

            val weatherCondition = weatherCode?.let { mapWeatherCode(it) }

            Result.success(
                EnvironmentData(
                    locationName = locationName,
                    temperatureC = temp,
                    weatherCondition = weatherCondition,
                    elevationMeters = elevation,
                    isOfflineMode = false
                )
            )
        } catch (e: Exception) {
            Log.e("EnvironmentApiClient", "Failed to fetch environment context", e)
            var locationName: String? = null
            try {
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    locationName = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.countryName
                }
            } catch (ge: Exception) {
                // geocoder also failed
            }
            if (locationName != null) {
                Result.success(EnvironmentData(locationName = locationName, isOfflineMode = true))
            } else {
                Result.success(EnvironmentData(isOfflineMode = true))
            }
        }
    }

    private fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing Rain"
            71, 73, 75 -> "Snow fall"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> "Unknown"
        }
    }
}
