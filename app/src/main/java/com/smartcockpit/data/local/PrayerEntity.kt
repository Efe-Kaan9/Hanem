package com.smartcockpit.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_times")
data class PrayerEntity(
    @PrimaryKey val id: Int = 0,
    val dawn: String,
    val sunrise: String,
    val noon: String,
    val afternoon: String,
    val sunset: String,
    val night: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
