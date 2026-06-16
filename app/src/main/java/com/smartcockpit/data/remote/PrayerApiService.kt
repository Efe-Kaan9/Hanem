package com.smartcockpit.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PrayerApiService {
    /** Existing endpoint — used by DailyUpdateWorker (do not remove). */
    @GET("v1/timingsByCity")
    suspend fun getPrayerTimes(
        @Query("city")   city:    String = "Izmir",
        @Query("country") country: String = "Turkey",
        @Query("method") method:  Int    = 13
    ): PrayerResponse

    /**
     * Coordinate-based endpoint — used by PrayerRepository.refreshPrayerTimes().
     * Aladhan API: GET /v1/timings/{DD-MM-YYYY}?latitude=&longitude=&method=
     * Returns an identical PrayerResponse structure.
     */
    @GET("v1/timings/{date}")
    suspend fun getPrayerTimesByCoords(
        @Path("date")      date:   String,
        @Query("latitude") lat:    Double,
        @Query("longitude") lon:   Double,
        @Query("method")   method: Int = 13
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
