package com.smartcockpit.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartcockpit.os.KioskManager
import com.smartcockpit.ui.screens.AmbientScreen
import com.smartcockpit.ui.screens.DashboardScreen
import com.smartcockpit.ui.theme.HanemTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var kioskManager: KioskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. WAKE THE PHYSICAL SCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        // FORCE TRUE IMMERSIVE FULLSCREEN
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())

        // 2. NATURAL SLEEP ENGINE — Phase 2 fix: proper reactive loop using flow.first()
        //    instead of the broken nested collect { while(isActive) {} } which never
        //    re-read updated settings after the first emission.
        startSleepEngine()

        // 3. SCHEDULE MORNING WAKEUP — Phase 2 fix: reads live DataStore values
        //    instead of the old no-arg call that silently fell back to hardcoded 08:00.
        lifecycleScope.launch {
            kioskManager.scheduleMorningWakeupFromDataStore()
        }

        setContent {
            HanemTheme {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToAmbient = { navController.navigate("ambient") }
                            )
                        }
                        composable("ambient") {
                            AmbientScreen(
                                onExit = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Phase 2 — Fixed Sleep Engine.
     *
     * Previous bug: settings.collect { while(isActive) { delay(60s) } }
     * The inner while loop ran forever on the first settings snapshot and never
     * picked up DataStore updates. A new coroutine was launched on each emission
     * but the previous inner while-loop was still spinning, creating a race.
     *
     * Fix: Use a flat while(isActive) loop that calls settings.first() on every
     * iteration. This always fetches the current DataStore value before each
     * window evaluation, guaranteeing the engine sees live wakeHour/sleepHour.
     * Safe defaults (8 / 23) are enforced in KioskManager's DataStore map block.
     */
    private fun startSleepEngine() {
        lifecycleScope.launch {
            while (isActive) {
                // Always read the freshest snapshot from DataStore before evaluating
                val settings = kioskManager.settings.first()

                val calendar = Calendar.getInstance()
                val currentTotalMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

                val wakeTotalMinutes = settings.wakeHour * 60 + settings.wakeMinute
                val sleepTotalMinutes = settings.sleepHour * 60 + settings.sleepMinute

                // ACTIVE CYCLE Logic — supports overnight schedules (e.g. 22:00 → 02:00)
                val isActiveWindow = if (wakeTotalMinutes < sleepTotalMinutes) {
                    currentTotalMinutes in wakeTotalMinutes until sleepTotalMinutes
                } else {
                    // Spans midnight (e.g. 08:00 → 01:00)
                    currentTotalMinutes >= wakeTotalMinutes || currentTotalMinutes < sleepTotalMinutes
                }

                if (isActiveWindow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                delay(60_000L) // Re-evaluate every minute
            }
        }
    }
}
