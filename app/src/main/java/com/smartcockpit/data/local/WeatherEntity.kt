package com.smartcockpit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.smartcockpit.data.local.converters.WeatherConverters

@Entity(tableName = "weather_cache")
@TypeConverters(WeatherConverters::class)
data class WeatherEntity(
    @PrimaryKey
    val id: Int = 0,
    val temperature: Float,
    val weatherCode: Int,
    val maxTemp: Float,
    val minTemp: Float,
    // Store daily/hourly as simple lists for simplicity in this local-first dashboard
    val dailyTempsMax: List<Float>,
    val dailyTempsMin: List<Float>,
    val dailyWeatherCodes: List<Int>,
    val hourlyTemps: List<Float>,
    val hourlyWeatherCodes: List<Int>,
    val lastUpdated: Long = System.currentTimeMillis()
)
