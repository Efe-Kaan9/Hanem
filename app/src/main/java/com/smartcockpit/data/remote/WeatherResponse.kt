package com.smartcockpit.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Data mapping for Open-Meteo API response.
 */
data class WeatherResponse(
    @SerializedName("current")
    val current: CurrentData,
    @SerializedName("hourly")
    val hourly: HourlyData,
    @SerializedName("daily")
    val daily: DailyData
)

data class CurrentData(
    @SerializedName("temperature_2m")
    val temperature: Float,
    @SerializedName("weather_code")
    val weatherCode: Int
)

data class HourlyData(
    @SerializedName("time")
    val times: List<String>,
    @SerializedName("temperature_2m")
    val temperatures: List<Float>,
    @SerializedName("weather_code")
    val weatherCodes: List<Int>
)

data class DailyData(
    @SerializedName("time")
    val times: List<String>,
    @SerializedName("temperature_2m_max")
    val maxTemperatures: List<Float>,
    @SerializedName("temperature_2m_min")
    val minTemperatures: List<Float>,
    @SerializedName("weather_code")
    val weatherCodes: List<Int>
)
