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
            // 1. Fetch NASA APOD with Video Fallback
            var apodResponse = try {
                nasaApi.getApod()
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
                    apodResponse = try { nasaApi.getApod(date = yesterday) } catch (e: Exception) { null }
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

            // 4. Refresh Weather
            try {
                weatherRepository.refreshWeather(38.375, 27.125)
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
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            
            val delay = target.timeInMillis - now.timeInMillis

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
    }
}
