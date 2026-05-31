package com.smartcockpit.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class WeatherWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeatherRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        // Mock coordinates (e.g., London)
        repository.refreshWeather(38.375, 27.125)
        return ListenableWorker.Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WeatherWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "weather_update",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
