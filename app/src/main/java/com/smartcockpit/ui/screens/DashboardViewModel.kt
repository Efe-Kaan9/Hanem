package com.smartcockpit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcockpit.data.remote.WeatherRepository
import com.smartcockpit.data.remote.PrayerRepository
import com.smartcockpit.os.DisplayController
import com.smartcockpit.data.local.dao.GalleryDao
import com.smartcockpit.data.local.dao.NasaDao
import com.smartcockpit.data.local.dao.PhraseDao
import com.smartcockpit.os.KioskManager
import com.smartcockpit.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val weatherRepository: WeatherRepository,
    private val prayerRepository: PrayerRepository,
    private val displayController: DisplayController,
    private val nasaDao: NasaDao,
    private val phraseDao: PhraseDao,
    private val galleryDao: GalleryDao,
    private val networkMonitor: NetworkMonitor,
    private val kioskManager: KioskManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isApodLoading = MutableStateFlow(false)
    val isApodLoading: StateFlow<Boolean> = _isApodLoading.asStateFlow()

    private val _isApodError = MutableStateFlow(false)
    val isApodError: StateFlow<Boolean> = _isApodError.asStateFlow()

    private var lastFetchAttemptTime: Long = 0

    val weather = weatherRepository.weather
    val latestApod = nasaDao.getLatestApod()
    val dailyPhrase = phraseDao.getDailyPhrase()
    // Mirrors the weather pipeline: reads from the repository's cached Flow,
    // not directly from the DAO, so the repository remains the single source of truth.
    val prayerTimes = prayerRepository.prayerTimes
    val settings = kioskManager.settings
    val galleryImages = galleryDao.getAllImages()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initial Freshness Check
        checkFreshness()

        // 1. Reactive Network Recovery Trigger
        viewModelScope.launch {
            networkMonitor.isOnline
                .drop(1) // Ignore initial state to only capture transitions
                .filter { it } // Only trigger on reconnection (Offline -> Online)
                .collect {
                    println("Dashboard: Network recovered. Re-evaluating freshness...")
                    checkFreshness()
                }
        }

        // 2. Midnight Crossing & Periodic Freshness Heartbeat (Every 30 minutes)
        viewModelScope.launch {
            while (true) {
                delay(30 * 60 * 1000)
                println("Dashboard: Periodic freshness heartbeat check...")
                checkFreshness()
            }
        }

        // 3. Reactive Location-Change Cache Invalidation
        //    Maps to Triple(lat, lon, locationUpdatedAt).
        //    - locationUpdatedAt is System.currentTimeMillis() written by the new atomic
        //      updateLocationAndMode(), so every real GPS/manual commit produces a distinct
        //      Triple even when the physical coordinates have not changed (Bug #2 fix).
        //    - drop(1) is intentionally REMOVED: on cold boot locationUpdatedAt = 0L,
        //      which is always distinct from real commit timestamps, so the first
        //      emission is never a false positive for a location change.
        viewModelScope.launch {
            kioskManager.settings
                .map { Triple(it.latitude, it.longitude, it.locationUpdatedAt) }
                .distinctUntilChanged()
                .collect { (lat, lon, _) ->
                    // Skip the initial DataStore snapshot (timestamp = 0 means no commit yet)
                    if (lat == 0.0 && lon == 0.0) return@collect
                    println("Dashboard: Location commit detected ($lat, $lon). Force-refreshing cache...")
                    try {
                        weatherRepository.refreshWeather(lat, lon)
                        println("Dashboard: Weather cache refreshed for new location.")
                    } catch (e: Exception) {
                        println("Dashboard: Weather refresh failed after location change: ${e.message}")
                    }
                    try {
                        prayerRepository.refreshPrayerTimes(lat, lon)
                        println("Dashboard: Prayer cache refreshed for new location.")
                    } catch (e: Exception) {
                        println("Dashboard: Prayer refresh failed after location change: ${e.message}")
                    }
                }
        }
    }

    private fun checkFreshness() {
        viewModelScope.launch {
            // API DEBOUNCE SHIELD: Minimum 15-minute gap between active catch-up attempts
            val now = System.currentTimeMillis()
            if (now - lastFetchAttemptTime < 15 * 60 * 1000) {
                println("Dashboard: Freshness check throttled. Last attempt was too recent.")
                return@launch
            }

            // TASK 1: "FRESHNESS-FIRST" CACHE INVALIDATION
            val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val currentWeather = weatherRepository.weather.first()
            val currentApod = nasaDao.getLatestApod().first()
            val currentPhrase = phraseDao.getDailyPhrase().first()
            val currentPrayer = prayerRepository.prayerTimes.first()

            // Strict Freshness Validation
            val isWeatherStale = currentWeather == null || currentWeather.lastUpdated < startOfToday
            val isApodStale = currentApod == null || currentApod.date != todayString
            val isPrayerStale = currentPrayer == null || currentPrayer.lastUpdated < startOfToday
            val isPhraseStale = currentPhrase == null || currentPhrase.lastUpdated < startOfToday

            if (isWeatherStale || isApodStale || isPrayerStale || isPhraseStale) {
                // DATA IS STALE: Commit to fetch and update attempt timer immediately
                lastFetchAttemptTime = now
                println("Dashboard: Data is stale. Triggering active catch-up...")
                
                _isApodLoading.value = true
                
                // 1. Phase 1 fix: Fetch Weather using DataStore-sourced lat/lon.
                //    KioskManager provides safe defaults (38.375 / 27.125) if empty.
                val settingsVal = kioskManager.settings.first()
                weatherRepository.refreshWeather(settingsVal.latitude, settingsVal.longitude)
                
                // 2. Trigger OneTime Sync for the rest (NASA, Prayer, Phrases)
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.smartcockpit.data.remote.DailyUpdateWorker>()
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "freshness_catchup_sync",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                // 3. Listen for result to handle UI feedback
                viewModelScope.launch {
                androidx.work.WorkManager.getInstance(context)
                    .getWorkInfoByIdFlow(workRequest.id)
                    .collect { workInfo ->
                        if (workInfo != null) {
                            when (workInfo.state) {
                                androidx.work.WorkInfo.State.SUCCEEDED -> {
                                    _isApodLoading.value = false
                                    _isApodError.value = false
                                }
                                androidx.work.WorkInfo.State.FAILED -> {
                                    _isApodLoading.value = false
                                    _isApodError.value = true
                                }
                                else -> {}
                            }
                        }
                    }
            }}
            else {
                // DB is 100% FRESH for Today
            }
        }
    }

    fun onSleep() {
        displayController.enterDeepSleep()
    }
}

data class DashboardUiState(
    val currentStatus: String = ""
)
