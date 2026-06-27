package com.smartcockpit.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.smartcockpit.data.local.dao.NasaDao
import com.smartcockpit.data.remote.DailyUpdateWorker
import com.smartcockpit.os.DashboardImageSource
import com.smartcockpit.os.KioskManager
import com.smartcockpit.os.KioskSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

// ─── GPS status (existing) ────────────────────────────────────────────────────
enum class GpsStatus { IDLE, FETCHING, SUCCESS, DENIED, ERROR, DISABLED }

// ─── Manual geocoding status (new) ───────────────────────────────────────────
sealed class ManualGeoStatus {
    object Idle    : ManualGeoStatus()
    object Loading : ManualGeoStatus()
    data class Success(val displayName: String) : ManualGeoStatus()
    data class Error(val message: String)       : ManualGeoStatus()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val kioskManager: KioskManager,
    private val nasaDao: NasaDao,
    @ApplicationContext private val appContext: Context   // For Geocoder — no Activity needed
) : ViewModel() {

    /**
     * Nullable so the UI can distinguish "DataStore not yet read" (null) from
     * "DataStore read and tutorial NOT completed" (KioskSettings with isTutorialCompleted=false).
     * The initial value of null acts as a loading sentinel — the NavHost must not be
     * initialized until a non-null emission arrives, preventing the onboarding flash.
     */
    val settings: StateFlow<KioskSettings?> = kioskManager.settings
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = null  // null = DataStore disk I/O still in-flight
        )

    // ── GPS flow ──────────────────────────────────────────────────────────────
    private val _gpsStatus = MutableStateFlow(GpsStatus.IDLE)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    // ── Manual geocoding flow ─────────────────────────────────────────────────
    private val _manualGeoStatus = MutableStateFlow<ManualGeoStatus>(ManualGeoStatus.Idle)
    val manualGeoStatus: StateFlow<ManualGeoStatus> = _manualGeoStatus.asStateFlow()

    fun updateAutoLocation(isAuto: Boolean) {
        viewModelScope.launch {
            kioskManager.updateSettings { it.copy(isAutoLocation = isAuto) }
        }
    }

    /**
     * Phase 1: One-time high-efficiency GPS fetch using FusedLocationProviderClient.
     * Permission must be verified by the caller before invoking this function.
     * Persists numeric lat/lon to DataStore. Geocoder resolves a display name for the UI.
     * Now features native hardware fallbacks for unstable Android 12 tablets.
     */
    @SuppressLint("MissingPermission")
    fun fetchGpsLocation(context: Context) {
        viewModelScope.launch {
            _gpsStatus.value = GpsStatus.FETCHING
            
            // 1. GLOBAL LOCATION SERVICES CHECK
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetworkEnabled) {
                _gpsStatus.value = GpsStatus.DISABLED
                return@launch
            }

            try {
                // 2. FusedLocationProviderClient (Primary)
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                
                val cancellationTokenSource = CancellationTokenSource()
                val locationRequest = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .build()

                var location = try {
                    fusedClient.getCurrentLocation(locationRequest, cancellationTokenSource.token).await()
                } catch (e: Exception) {
                    null
                }
                
                if (location == null) {
                    location = try {
                        fusedClient.lastLocation.await()
                    } catch (e: Exception) {
                        null
                    }
                }

                // 3. Native Hardware Fallback (LocationManager API 30+)
                if (location == null) {
                    location = withContext(Dispatchers.IO) {
                        suspendCancellableCoroutine { continuation ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val provider = if (isNetworkEnabled) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                                val cancellationSignal = CancellationSignal()
                                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                                
                                try {
                                    locationManager.getCurrentLocation(
                                        provider,
                                        cancellationSignal,
                                        ContextCompat.getMainExecutor(context)
                                    ) { loc ->
                                        if (continuation.isActive) {
                                            continuation.resume(loc)
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            } else {
                                // Fallback for older APIs via lastKnownLocation
                                try {
                                    val provider = if (isNetworkEnabled) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                                    val loc = locationManager.getLastKnownLocation(provider)
                                    if (continuation.isActive) continuation.resume(loc)
                                } catch (e: Exception) {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            }
                        }
                    }
                }

                // 4. Commit or Error
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val displayName = resolveDisplayName(context, lat, lon)
                    // Bug #1 fix: single atomic edit{} — no more two-write race
                    kioskManager.updateLocationAndMode(
                        lat         = lat,
                        lon         = lon,
                        isAuto      = true,
                        displayName = displayName
                    )
                    _gpsStatus.value = GpsStatus.SUCCESS
                } else {
                    _gpsStatus.value = GpsStatus.ERROR
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _gpsStatus.value = GpsStatus.ERROR
            }
        }
    }

    /**
     * Dual-Engine Manual Geocoding.
     *
     * Engine 1 — Android native Geocoder (fast, no network if Google Play Data is cached).
     * Engine 2 — Open-Meteo free geocoding API (reliable internet fallback, zero API key).
     *
     * CONTRACT: DataStore is NEVER overwritten unless both lat AND lon are resolved.
     * On failure, [_manualGeoStatus] is set to Error and existing coordinates are preserved.
     */
    fun saveManualLocation(query: String) {
        if (query.isBlank()) {
            _manualGeoStatus.value = ManualGeoStatus.Error("Please enter a city name.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _manualGeoStatus.value = ManualGeoStatus.Loading

            var resolvedLat: Double? = null
            var resolvedLon: Double? = null
            var resolvedName = ""

            // ── ENGINE 1: Android Native Geocoder ─────────────────────────────
            try {
                val geocoder = Geocoder(appContext, Locale.ENGLISH)
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(query.trim(), 1)
                if (!results.isNullOrEmpty()) {
                    val address = results[0]
                    resolvedLat = address.latitude
                    resolvedLon = address.longitude
                    resolvedName = buildString {
                        address.locality?.let { append(it) }
                            ?: address.adminArea?.let { append(it) }
                        address.countryName?.let {
                            if (isNotEmpty()) append(", ")
                            append(it)
                        }
                    }.ifBlank { "%.4f, %.4f".format(resolvedLat, resolvedLon) }
                }
            } catch (_: Exception) {
                // Engine 1 failed silently — try Engine 2
            }

            // ── ENGINE 2: Open-Meteo Geocoding API ────────────────────────────
            if (resolvedLat == null || resolvedLon == null) {
                try {
                    val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
                    val url = java.net.URL(
                        "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&format=json"
                    )
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8_000
                    conn.readTimeout    = 8_000
                    conn.requestMethod  = "GET"

                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(body)
                        val arr  = json.optJSONArray("results")
                        if (arr != null && arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            resolvedLat  = first.getDouble("latitude")
                            resolvedLon  = first.getDouble("longitude")
                            val name     = first.optString("name", "")
                            val country  = first.optString("country", "")
                            resolvedName = when {
                                name.isNotBlank() && country.isNotBlank() -> "$name, $country"
                                name.isNotBlank() -> name
                                else -> "%.4f, %.4f".format(resolvedLat, resolvedLon)
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                    // Engine 2 also failed
                }
            }

            // ── COMMIT OR REPORT ERROR ─────────────────────────────────────────
            if (resolvedLat != null && resolvedLon != null) {
                // At least one engine succeeded — commit atomically (Bug #1 fix)
                kioskManager.updateLocationAndMode(
                    lat         = resolvedLat,
                    lon         = resolvedLon,
                    isAuto      = false,
                    displayName = resolvedName
                )
                _manualGeoStatus.value = ManualGeoStatus.Success(resolvedName)
            } else {
                _manualGeoStatus.value = ManualGeoStatus.Error(
                    "Location not found. Check the spelling or your connection."
                )
            }
        }
    }

    fun resetManualGeoStatus() {
        _manualGeoStatus.value = ManualGeoStatus.Idle
    }

    private fun resolveDisplayName(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.ENGLISH)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val address = addresses?.firstOrNull()
            when {
                address?.locality  != null -> "${address.locality}, ${address.countryName}"
                address?.adminArea != null -> "${address.adminArea}, ${address.countryName}"
                else -> "%.4f, %.4f".format(lat, lon)
            }
        } catch (e: Exception) {
            "%.4f, %.4f".format(lat, lon)
        }
    }

    fun resetGpsStatus() { _gpsStatus.value = GpsStatus.IDLE }

    fun updateWakeTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            kioskManager.updateSettings { it.copy(wakeHour = hour, wakeMinute = minute) }
        }
    }

    fun updateSleepTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            kioskManager.updateSettings { it.copy(sleepHour = hour, sleepMinute = minute) }
        }
    }

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            kioskManager.updateSettings { it.copy(themeMode = mode) }
        }
    }

    /** Marks the onboarding flow as permanently done. */
    fun completeTutorial() {
        viewModelScope.launch {
            kioskManager.completeTutorial()
        }
    }

    fun updateNasaApiKey(newKey: String) {
        viewModelScope.launch {
            kioskManager.updateNasaApiKey(newKey)
            
            val latest = nasaDao.getLatestApod().firstOrNull()
            if (latest == null || latest.url.isBlank()) {
                DailyUpdateWorker.enqueueImmediateWork(appContext)
            }
        }
    }

    fun clearNasaApiKey() {
        viewModelScope.launch {
            kioskManager.clearNasaApiKey()
        }
    }

    fun updateDashboardImageSource(source: DashboardImageSource) {
        viewModelScope.launch {
            kioskManager.updateDashboardImageSource(source)
        }
    }
}
