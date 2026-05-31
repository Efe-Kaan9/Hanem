package com.smartcockpit.di

import android.content.Context
import androidx.room.Room
import com.smartcockpit.data.local.AppDatabase
import com.smartcockpit.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hanem_db"
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }

    @Provides
    fun provideWeatherDao(db: AppDatabase): WeatherDao = db.weatherDao()

    @Provides
    fun provideGalleryDao(db: AppDatabase): GalleryDao = db.galleryDao()

    @Provides
    fun provideNasaDao(db: AppDatabase): NasaDao = db.nasaDao()

    @Provides
    fun providePhraseDao(db: AppDatabase): PhraseDao = db.phraseDao()

    @Provides
    fun providePrayerDao(db: AppDatabase): PrayerDao = db.prayerDao()
}
