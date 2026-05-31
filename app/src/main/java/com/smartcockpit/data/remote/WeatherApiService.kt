package com.smartcockpit.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo API Service
 * Fetches real-time, hourly, and daily forecast data.
 */
interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code",
        @Query("hourly") hourly: String = "temperature_2m,weather_code",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,weather_code",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}
