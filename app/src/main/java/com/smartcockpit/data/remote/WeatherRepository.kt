package com.smartcockpit.data.remote

import com.smartcockpit.data.local.WeatherEntity
import com.smartcockpit.data.local.dao.WeatherDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Weather data.
 * Coordinates between the remote API and the local cache (Room).
 *
 * Phase 1 change: Removed default parameter values from refreshWeather() to force
 * all callers to explicitly pass DataStore-sourced coordinates. There is no longer
 * a silent fallback to hardcoded lat/lon values at this layer.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val api: WeatherApiService,
    private val dao: WeatherDao
) {
    val weather: Flow<WeatherEntity?> = dao.getCachedWeather()

    suspend fun refreshWeather(lat: Double, lon: Double) {
        try {
            val response = api.getForecast(lat, lon)
            val entity = WeatherEntity(
                temperature = response.current.temperature,
                weatherCode = response.current.weatherCode,
                maxTemp = response.daily.maxTemperatures.firstOrNull() ?: 0f,
                minTemp = response.daily.minTemperatures.firstOrNull() ?: 0f,
                dailyTempsMax = response.daily.maxTemperatures,
                dailyTempsMin = response.daily.minTemperatures,
                dailyWeatherCodes = response.daily.weatherCodes,
                hourlyTemps = response.hourly.temperatures.take(48), // 48h to support sliding window
                hourlyWeatherCodes = response.hourly.weatherCodes.take(48)
            )
            dao.updateCache(entity)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
