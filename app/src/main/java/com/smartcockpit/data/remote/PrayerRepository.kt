package com.smartcockpit.data.remote

import com.smartcockpit.data.local.PrayerEntity
import com.smartcockpit.data.local.dao.PrayerDao
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Prayer Times data.
 * Mirrors the WeatherRepository pattern: wraps the remote API + local DAO.
 *
 * NOTE: The DailyUpdateWorker still injects PrayerApiService and PrayerDao
 * directly (city/country path). This repository is the NEW coordinate-based
 * path used exclusively for reactive cache invalidation on location change.
 */
@Singleton
class PrayerRepository @Inject constructor(
    private val api: PrayerApiService,
    private val dao: PrayerDao
) {
    /**
     * Force-fetches prayer times for [lat]/[lon] and atomically overwrites
     * the local Room cache. Uses the Aladhan v1/timings/{date} endpoint so
     * times are accurate for the new geographic position.
     *
     * Throws on network / API failure — callers should wrap in try/catch.
     */
    suspend fun refreshPrayerTimes(lat: Double, lon: Double) {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val response = api.getPrayerTimesByCoords(today, lat, lon)
        val timings = response.data.timings
        dao.updatePrayerTimes(
            PrayerEntity(
                dawn      = timings.fajr,
                sunrise   = timings.sunrise,
                noon      = timings.dhuhr,
                afternoon = timings.asr,
                sunset    = timings.maghrib,
                night     = timings.isha
            )
        )
    }
}
