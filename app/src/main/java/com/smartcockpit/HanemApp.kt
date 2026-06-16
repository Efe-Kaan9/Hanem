package com.smartcockpit

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.smartcockpit.data.remote.DailyUpdateWorker
import com.smartcockpit.data.remote.WeatherWorker
import com.smartcockpit.os.KioskManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HanemApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var kioskManager: KioskManager

    // Application-level scope for one-shot DataStore reads at startup
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        WeatherWorker.schedule(this)

        // Phase 2 fix: Read wakeHour from DataStore before scheduling DailyUpdateWorker
        // so the initial delay targets the user's configured wake time, not hardcoded 08:00.
        appScope.launch {
            val settings = kioskManager.settings.first()
            val safeWakeHour = settings.wakeHour.takeIf { it in 0..23 } ?: 8
            DailyUpdateWorker.schedule(this@HanemApp, safeWakeHour)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1) // 10% of disk space
                    .build()
            }
            .respectCacheHeaders(false) // Force cache survival even if headers say otherwise
            .build()
    }
}
