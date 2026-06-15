package com.smartcockpit.os

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kiosk_prefs")

@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val PHRASE_INDEX = intPreferencesKey("phrase_index")
    private val AMBIENT_IMAGE_INDEX = intPreferencesKey("ambient_image_index")

    val phraseIndex: Flow<Int> = context.dataStore.data.map { it[PHRASE_INDEX] ?: 0 }
    val ambientImageIndex: Flow<Int> = context.dataStore.data.map { it[AMBIENT_IMAGE_INDEX] ?: 0 }

    suspend fun savePhraseIndex(index: Int) {
        context.dataStore.edit { it[PHRASE_INDEX] = index }
    }

    suspend fun saveAmbientImageIndex(index: Int) {
        context.dataStore.edit { it[AMBIENT_IMAGE_INDEX] = index }
    }

    fun scheduleMorningWakeup() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, KioskWakeupReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
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
