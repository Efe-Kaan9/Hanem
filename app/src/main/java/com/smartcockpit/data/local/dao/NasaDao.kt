package com.smartcockpit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartcockpit.data.local.NasaApodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NasaDao {
    @Query("SELECT * FROM nasa_apod ORDER BY date DESC LIMIT 1")
    fun getLatestApod(): Flow<NasaApodEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApod(apod: NasaApodEntity)
}
