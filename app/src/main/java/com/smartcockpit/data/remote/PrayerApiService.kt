package com.smartcockpit.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface PrayerApiService {
    @GET("v1/timingsByCity")
    suspend fun getPrayerTimes(
        @Query("city") city: String = "Izmir",
        @Query("country") country: String = "Turkey",
        @Query("method") method: Int = 13
    ): PrayerResponse
}

data class PrayerResponse(
    val data: PrayerData
)

data class PrayerData(
    val timings: PrayerTimings
)

data class PrayerTimings(
    @SerializedName("Fajr") val fajr: String,
    @SerializedName("Sunrise") val sunrise: String,
    @SerializedName("Dhuhr") val dhuhr: String,
    @SerializedName("Asr") val asr: String,
    @SerializedName("Maghrib") val maghrib: String,
    @SerializedName("Isha") val isha: String
)
