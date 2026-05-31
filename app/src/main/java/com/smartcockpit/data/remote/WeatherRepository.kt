package com.smartcockpit.data.remote

import com.smartcockpit.data.local.WeatherEntity
import com.smartcockpit.data.local.dao.WeatherDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Weather data.
 * Coordinates between the remote API and the local cache (Room).
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val api: WeatherApiService,
    private val dao: WeatherDao
) {
    val weather: Flow<WeatherEntity?> = dao.getCachedWeather()

    suspend fun refreshWeather(lat: Double = 38.375, lon: Double = 27.125) {
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
