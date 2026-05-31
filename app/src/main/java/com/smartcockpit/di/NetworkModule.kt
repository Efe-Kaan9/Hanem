package com.smartcockpit.di

import com.smartcockpit.data.remote.NasaApiService
import com.smartcockpit.data.remote.PrayerApiService
import com.smartcockpit.data.remote.WeatherApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideWeatherApi(client: OkHttpClient): WeatherApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNasaApi(client: OkHttpClient): NasaApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.nasa.gov/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NasaApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePrayerApi(client: OkHttpClient): PrayerApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.aladhan.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PrayerApiService::class.java)
    }
}
