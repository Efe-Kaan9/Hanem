package com.smartcockpit.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartcockpit.data.local.NasaApodEntity
import com.smartcockpit.data.local.PhraseEntity
import com.smartcockpit.data.local.PrayerEntity
import com.smartcockpit.data.local.dao.NasaDao
import com.smartcockpit.data.local.dao.PhraseDao
import com.smartcockpit.data.local.dao.PrayerDao
import com.smartcockpit.os.KioskManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val nasaApi: NasaApiService,
    private val prayerApi: PrayerApiService,
    private val nasaDao: NasaDao,
    private val phraseDao: PhraseDao,
    private val prayerDao: PrayerDao,
    private val weatherRepository: WeatherRepository,
    private val kioskManager: KioskManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val settings = kioskManager.settings.first()
            val apiKeyToUse = if (settings.nasaApiKey.isNotBlank()) settings.nasaApiKey else "DEMO_KEY"

            // 1. Fetch NASA APOD with Video Fallback
            var apodResponse = try {
                nasaApi.getApod(apiKey = apiKeyToUse)
            } catch (e: Exception) {
                null
            }

            // CRITICAL: Check mediaType of the fresh fetch
            if (apodResponse?.mediaType == "video") {
                // If today is a video, check if we already have a valid image in cache.
                val cachedApod = nasaDao.getLatestApod().first()
                if (cachedApod != null && cachedApod.mediaType == "image") {
                    // Cache is an image: Log and retain (Do NOT overwrite)
                    println("NASA APOD: Today is a video. Retaining cached image: ${cachedApod.title}")
                    apodResponse = null
                } else {
                    // Cache is also missing/video: Try fetching YESTERDAY as final fallback
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val yesterday = dateFormat.format(calendar.time)
                    apodResponse = try { nasaApi.getApod(apiKey = apiKeyToUse, date = yesterday) } catch (e: Exception) { null }
                }
            }

            apodResponse?.let { resp ->
                // Final safeguard: Only write to DB if we actually have an image
                if (resp.mediaType == "image") {
                    nasaDao.insertApod(
                        NasaApodEntity(
                            date = resp.date,
                            title = resp.title,
                            explanation = resp.explanation,
                            url = resp.url,
                            hdUrl = resp.hdUrl,
                            mediaType = resp.mediaType
                        )
                    )
                }
            }

            // 2. Fetch Prayer Times
            try {
                val prayerResponse = prayerApi.getPrayerTimes()
                val timings = prayerResponse.data.timings
                prayerDao.updatePrayerTimes(
                    PrayerEntity(
                        dawn = timings.fajr,
                        sunrise = timings.sunrise,
                        noon = timings.dhuhr,
                        afternoon = timings.asr,
                        sunset = timings.maghrib,
                        night = timings.isha
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Select next English Phrase
            try {
                val currentPhrase = phraseDao.getDailyPhrase().first()
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfToday = calendar.timeInMillis

                // Only pick a new phrase if the current one is from a previous day
                if (currentPhrase == null || currentPhrase.lastUpdated < startOfToday) {
                    val persistedIndex = kioskManager.phraseIndex.first()
                    val nextIndex = (currentPhrase?.index?.plus(1) ?: persistedIndex + 1)

                    val jsonString = applicationContext.assets.open("c1_phrases.json").bufferedReader().use { it.readText() }
                    val listType = object : TypeToken<List<Map<String, String>>>() {}.type
                    val allPhrases: List<Map<String, String>> = Gson().fromJson(jsonString, listType)

                    if (allPhrases.isNotEmpty()) {
                        val selected = allPhrases[nextIndex % allPhrases.size]
                        phraseDao.updatePhrase(
                            PhraseEntity(
                                en = selected["en"] ?: "",
                                tr = selected["tr"] ?: "",
                                index = nextIndex,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                        kioskManager.savePhraseIndex(nextIndex)
                        println("Lexicon: Updated to new phrase at index $nextIndex")
                    }
                } else {
                    println("Lexicon: Phrase is already fresh for today. Skipping update.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. Phase 1 fix: Refresh Weather with DataStore-sourced coordinates.
            //    No longer uses hardcoded 38.375, 27.125 — reads from KioskManager
            //    which provides safe defaults (38.375 / 27.125) if DataStore is empty.
            try {
                weatherRepository.refreshWeather(settings.latitude, settings.longitude)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        /**
         * Phase 2 fix: The companion object can only accept a Context (no suspend scope).
         * The wake hour for the initial delay is passed explicitly from HanemApp, which
         * reads it from DataStore before calling schedule(). Falls back to 08:00.
         */
        fun schedule(context: Context, wakeHour: Int = 8) {
            val safeHour = wakeHour.takeIf { it in 0..23 } ?: 8
            val nowMillis = System.currentTimeMillis() // Tek ve kesin zaman referansı

            val target = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, safeHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0) // Milisaniyeyi kesinlikle sıfırla
            }

            // KioskManager'da kullandığımız o kesin koşul (Geçmiş Zaman Tuzağı Koruması)
            if (target.timeInMillis <= nowMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = target.timeInMillis - nowMillis

            val workRequest = PeriodicWorkRequestBuilder<DailyUpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_8am_update",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun enqueueImmediateWork(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<DailyUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateNasaFetch",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
