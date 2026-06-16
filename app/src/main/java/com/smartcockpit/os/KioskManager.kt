package com.smartcockpit.os

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kiosk_prefs")

// Default safe fallback coordinates — İzmir, Turkey
private const val DEFAULT_LATITUDE = 38.375
private const val DEFAULT_LONGITUDE = 27.125

data class KioskSettings(
    val isAutoLocation: Boolean,
    val latitude: Double,
    val longitude: Double,
    val locationDisplayName: String, // Geocoder-resolved city name for UI only
    val locationUpdatedAt: Long,     // Timestamp of the last successful location commit
    val wakeHour: Int,
    val wakeMinute: Int,
    val sleepHour: Int,
    val sleepMinute: Int,
    val themeMode: Int // 0: Auto, 1: Light, 2: Dark
)

@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val PHRASE_INDEX = intPreferencesKey("phrase_index")
    private val AMBIENT_IMAGE_INDEX = intPreferencesKey("ambient_image_index")

    private val IS_AUTO_LOCATION = booleanPreferencesKey("is_auto_location")
    // Phase 1: Replaced manualLocation (String) with numeric lat/lon (Double)
    private val LATITUDE = doublePreferencesKey("location_latitude")
    private val LONGITUDE = doublePreferencesKey("location_longitude")
    private val LOCATION_DISPLAY_NAME = androidx.datastore.preferences.core.stringPreferencesKey("location_display_name")
    // Bug #2 fix: timestamp written on every successful GPS/manual commit so
    // distinctUntilChanged() always sees a new Triple even when coords are identical.
    private val LOCATION_UPDATED_AT = androidx.datastore.preferences.core.longPreferencesKey("location_updated_at")

    private val WAKE_HOUR = intPreferencesKey("wake_hour")
    private val WAKE_MINUTE = intPreferencesKey("wake_minute")
    private val SLEEP_HOUR = intPreferencesKey("sleep_hour")
    private val SLEEP_MINUTE = intPreferencesKey("sleep_minute")
    private val THEME_MODE = intPreferencesKey("theme_mode")

    val phraseIndex: Flow<Int> = context.dataStore.data.map { it[PHRASE_INDEX] ?: 0 }
    val ambientImageIndex: Flow<Int> = context.dataStore.data.map { it[AMBIENT_IMAGE_INDEX] ?: 0 }

    val settings: Flow<KioskSettings> = context.dataStore.data.map { prefs ->
        KioskSettings(
            isAutoLocation      = prefs[IS_AUTO_LOCATION]      ?: true,
            latitude            = prefs[LATITUDE]              ?: DEFAULT_LATITUDE,
            longitude           = prefs[LONGITUDE]             ?: DEFAULT_LONGITUDE,
            locationDisplayName = prefs[LOCATION_DISPLAY_NAME] ?: "",
            locationUpdatedAt   = prefs[LOCATION_UPDATED_AT]   ?: 0L,
            wakeHour            = prefs[WAKE_HOUR]             ?: 8,
            wakeMinute          = prefs[WAKE_MINUTE]           ?: 0,
            sleepHour           = prefs[SLEEP_HOUR]            ?: 23,
            sleepMinute         = prefs[SLEEP_MINUTE]          ?: 0,
            themeMode           = prefs[THEME_MODE]            ?: 0
        )
    }

    suspend fun savePhraseIndex(index: Int) {
        context.dataStore.edit { it[PHRASE_INDEX] = index }
    }

    suspend fun saveAmbientImageIndex(index: Int) {
        context.dataStore.edit { it[AMBIENT_IMAGE_INDEX] = index }
    }

    /**
     * Bug #1 fix: Atomic single-transaction location commit.
     * Writes lat, lon, isAutoLocation, displayName, AND a fresh timestamp in exactly
     * ONE DataStore.edit{} block — eliminating the read-then-write race that existed
     * when saveLocation() and updateSettings() were called in two separate transactions.
     * Also serves as the trigger signal for the DashboardViewModel reactive watcher
     * (LOCATION_UPDATED_AT changes on every commit, ensuring distinctUntilChanged fires).
     */
    suspend fun updateLocationAndMode(
        lat: Double,
        lon: Double,
        isAuto: Boolean,
        displayName: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[LATITUDE]              = lat
            prefs[LONGITUDE]             = lon
            prefs[IS_AUTO_LOCATION]      = isAuto
            prefs[LOCATION_DISPLAY_NAME] = displayName
            prefs[LOCATION_UPDATED_AT]   = System.currentTimeMillis()
        }
    }

    suspend fun updateSettings(update: (KioskSettings) -> KioskSettings) {
        val current = settings.first()
        val new = update(current)
        context.dataStore.edit { prefs ->
            prefs[IS_AUTO_LOCATION]      = new.isAutoLocation
            prefs[LATITUDE]              = new.latitude
            prefs[LONGITUDE]             = new.longitude
            prefs[LOCATION_DISPLAY_NAME] = new.locationDisplayName
            prefs[LOCATION_UPDATED_AT]   = new.locationUpdatedAt // preserve timestamp
            prefs[WAKE_HOUR]             = new.wakeHour
            prefs[WAKE_MINUTE]           = new.wakeMinute
            prefs[SLEEP_HOUR]            = new.sleepHour
            prefs[SLEEP_MINUTE]          = new.sleepMinute
            prefs[THEME_MODE]            = new.themeMode
        }

        // Re-schedule alarm with live values if wake time changed
        if (current.wakeHour != new.wakeHour || current.wakeMinute != new.wakeMinute) {
            scheduleMorningWakeup(new.wakeHour, new.wakeMinute)
        }
    }

    /**
     * Phase 2: Reads wakeHour/wakeMinute from the DataStore flow and schedules the
     * morning alarm with those live values. Safe default: 08:00.
     * Use this from MainActivity.onCreate() instead of the zero-arg static call.
     */
    suspend fun scheduleMorningWakeupFromDataStore() {
        val current = settings.first()
        val hour = current.wakeHour.takeIf { it in 0..23 } ?: 8
        val minute = current.wakeMinute.takeIf { it in 0..59 } ?: 0
        scheduleMorningWakeup(hour, minute)
    }

    fun scheduleMorningWakeup(hour: Int = 8, minute: Int = 0) {
        val safeHour = hour.takeIf { it in 0..23 } ?: 8
        val safeMinute = minute.takeIf { it in 0..59 } ?: 0

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, KioskWakeupReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}
