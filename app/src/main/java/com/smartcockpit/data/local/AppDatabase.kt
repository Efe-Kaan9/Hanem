package com.smartcockpit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smartcockpit.data.local.dao.*
import com.smartcockpit.data.local.converters.WeatherConverters

@Database(
    entities = [
        WeatherEntity::class,
        GalleryEntity::class,
        NasaApodEntity::class,
        PhraseEntity::class,
        PrayerEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(WeatherConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun galleryDao(): GalleryDao
    abstract fun nasaDao(): NasaDao
    abstract fun phraseDao(): PhraseDao
    abstract fun prayerDao(): PrayerDao
}
