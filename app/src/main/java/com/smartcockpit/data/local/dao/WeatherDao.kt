package com.smartcockpit.data.local.dao

import androidx.room.*
import com.smartcockpit.data.local.WeatherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE id = 0")
    fun getCachedWeather(): Flow<WeatherEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCache(weather: WeatherEntity)
}
