package com.smartcockpit.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.smartcockpit.os.KioskManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Phase 1 fix: WeatherWorker now reads lat/lon from the DataStore via KioskManager
 * instead of using hardcoded coordinates (previously 38.375, 27.125 — İzmir).
 * The KioskManager.settings flow provides safe defaults if DataStore is empty.
 */
@HiltWorker
class WeatherWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeatherRepository,
    private val kioskManager: KioskManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = kioskManager.settings.first()
            repository.refreshWeather(settings.latitude, settings.longitude)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WeatherWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "weather_update",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
