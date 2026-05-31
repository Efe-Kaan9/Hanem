package com.smartcockpit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartcockpit.data.local.PrayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_times WHERE id = 0")
    fun getPrayerTimes(): Flow<PrayerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePrayerTimes(prayerEntity: PrayerEntity)
}
