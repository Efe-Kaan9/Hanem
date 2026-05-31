package com.smartcockpit

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.smartcockpit.data.remote.DailyUpdateWorker
import com.smartcockpit.data.remote.WeatherWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HanemApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        WeatherWorker.schedule(this)
        DailyUpdateWorker.schedule(this)
    }
}
