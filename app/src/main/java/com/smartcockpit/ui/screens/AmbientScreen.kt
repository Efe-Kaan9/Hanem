@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smartcockpit.ui.screens


import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import com.smartcockpit.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientScreen(
    onExit: () -> Unit,
    viewModel: AmbientViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val images by viewModel.images.collectAsState()
    val currentImageIndex by viewModel.currentImageIndex.collectAsState()
    val weather by dashboardViewModel.weather.collectAsState(initial = null)
    val prayerTimes by dashboardViewModel.prayerTimes.collectAsState(initial = null)
    val settings = settingsViewModel.settings.collectAsState().value
        ?: return // AmbientScreen is only shown after the loading gate; null is unreachable in practice
    val gpsStatus by settingsViewModel.gpsStatus.collectAsState()
    val manualGeoStatus by settingsViewModel.manualGeoStatus.collectAsState()

    var showGalleryManagement by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // 1. TOP-LEVEL SYSTEM CLOCK TICKER
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    // 2. HOISTED DAY/NIGHT STATE
    val isDayGlobal = remember(prayerTimes, currentTime.longValue, settings.themeMode) {
        when (settings.themeMode) {
            1 -> true // Force Light
            2 -> false // Force Dark
            else -> { // Auto logic
                fun parseMin(time: String?): Int {
                    if (time == null || time == "--:--") return 0
                    val parts = time.split(":")
                    return if (parts.size >= 2) parts[0].toInt() * 60 + parts[1].toInt() else 0
                }
                val cal = Calendar.getInstance().apply { timeInMillis = currentTime.longValue }
                val currentTotalMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val sunriseMin = parseMin(prayerTimes?.sunrise)
                val sunsetMin = parseMin(prayerTimes?.sunset)

                if (sunriseMin > 0 && sunsetMin > 0) {
                    currentTotalMin in sunriseMin until sunsetMin
                } else {
                    cal.get(Calendar.HOUR_OF_DAY) in 6..20 // Fallback
                }
            }
        }
    }

    // 3. SCREEN PROTECTION STATES (45-second loop)
    var shiftIndex by remember { mutableIntStateOf(0) }
    val offsets = listOf(
        DpOffset(1.dp, 1.dp),
        DpOffset((-1).dp, 1.dp),
        DpOffset((-1).dp, (-1).dp),
        DpOffset(1.dp, (-1).dp)
    )
    var pixelOffset by remember { mutableStateOf(offsets[0]) }
    var textAlpha by remember { mutableFloatStateOf(0.55f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(45000) // 45 seconds
            shiftIndex = (shiftIndex + 1) % offsets.size
            pixelOffset = offsets[shiftIndex]
            textAlpha = (40..55).random() / 100f
        }
    }

    // 4. Location permission launcher — triggers GPS fetch in SettingsViewModel
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            settingsViewModel.fetchGpsLocation(context)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.let { list ->
            list.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModel.addImages(list.map { it.toString() })
        }
    }

    LaunchedEffect(images) {
        if (images.isNotEmpty()) {
            while (true) {
                delay(30000) // 30 seconds for image rotation
                val nextIndex = if (images.isNotEmpty()) (currentImageIndex + 1) % images.size else 0
                viewModel.updateImageIndex(nextIndex)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val blurRadius = if (showGalleryManagement || showSettings) 16.dp else 0.dp

        Box(modifier = Modifier.fillMaxSize().blur(blurRadius)) {
            if (images.isNotEmpty()) {
                Crossfade(targetState = currentImageIndex, animationSpec = tween(2000)) { index ->
                    if (index < images.size) {
                        KenBurnsImage(images[index].imageUri)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No images selected for Ambient Mode", color = Color.Gray)
                }
            }
        }

        // Overlay to show gallery management panel on tap
        Box(
            Modifier
                .fillMaxSize()
                .clickable { showGalleryManagement = true }
        )

        FloatingHUD(weather)

        if (showGalleryManagement) {
            GalleryManagementPanel(
                images = images,
                isDayGlobal = isDayGlobal,
                pixelOffset = pixelOffset,
                textAlpha = textAlpha,
                onAddImage = { imageLauncher.launch("image/*") },
                onRemoveImage = viewModel::removeImage,
                onOpenSettings = { showSettings = true },
                onClose = { showGalleryManagement = false },
                onExitAmbient = onExit
            )
        }

        // Premium Settings ModalBottomSheet
        if (showSettings) {
            val isDark = when (settings.themeMode) {
                1    -> false      // Force Light
                2    -> true       // Force Dark
                else -> !isDayGlobal  // Auto: dark when it is NOT daytime
            }
            KioskSettingsSheet(
                settings = settings,
                gpsStatus = gpsStatus,
                manualGeoStatus = manualGeoStatus,
                isDark = isDark,
                onRequestGps = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onFetchGps = { settingsViewModel.fetchGpsLocation(context) },
                onSaveManualLocation = settingsViewModel::saveManualLocation,
                onResetManualGeoStatus = settingsViewModel::resetManualGeoStatus,
                onUpdateAutoLocation = settingsViewModel::updateAutoLocation,
                onUpdateWakeTime = settingsViewModel::updateWakeTime,
                onUpdateSleepTime = { h, m -> settingsViewModel.updateSleepTime(h, m) },
                onUpdateThemeMode = { mode -> settingsViewModel.updateThemeMode(mode) },
                onUpdateNasaApiKey = { key -> settingsViewModel.updateNasaApiKey(key) },
                onClearNasaApiKey = { settingsViewModel.clearNasaApiKey() },
                onResetGpsStatus = { settingsViewModel.resetGpsStatus() },
                onUpdateDashboardImageSource = { source -> settingsViewModel.updateDashboardImageSource(source) },
                onClose = { showSettings = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREMIUM SETTINGS BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskSettingsSheet(
    settings: com.smartcockpit.os.KioskSettings,
    gpsStatus: GpsStatus,
    manualGeoStatus: ManualGeoStatus,
    isDark: Boolean,
    onRequestGps: () -> Unit,
    onFetchGps: () -> Unit,
    onSaveManualLocation: (String) -> Unit,
    onResetManualGeoStatus: () -> Unit,
    onUpdateAutoLocation: (Boolean) -> Unit,
    onUpdateWakeTime: (Int, Int) -> Unit,
    onUpdateSleepTime: (Int, Int) -> Unit,
    onUpdateThemeMode: (Int) -> Unit,
    onUpdateNasaApiKey: (String) -> Unit,
    onClearNasaApiKey: () -> Unit,
    onResetGpsStatus: () -> Unit,
    onUpdateDashboardImageSource: (com.smartcockpit.os.DashboardImageSource) -> Unit,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Dynamic theme palette ─────────────────────────────────────────────────
    val sheetBgTop    = if (isDark) Color(0xFF0D1117)       else Color(0xFFF8F5EE)
    val sheetBgBot    = if (isDark) Color(0xFF131820)       else Color(0xFFF0EBE1)
    val onSurface     = if (isDark) Color.White             else Color(0xFF1A1209)
    val subtleText    = if (isDark) Color.White.copy(0.35f) else Color(0xFF1A1209).copy(0.4f)
    val closeBtnBg    = if (isDark) Color.White.copy(0.08f) else Color(0xFF1A1209).copy(0.07f)
    val separatorClr  = if (isDark) Color.White.copy(0.06f) else Color(0xFF1A1209).copy(0.08f)
    val handleClr     = if (isDark) Color.White.copy(0.20f) else Color(0xFF1A1209).copy(0.15f)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    brush = Brush.verticalGradient(colors = listOf(sheetBgTop, sheetBgBot)),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 52.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(handleClr)
                )

                Spacer(Modifier.height(4.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "COCKPIT SETTINGS",
                            style = Typography.labelSmall,
                            color = subtleText,
                            letterSpacing = 3.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "System Configuration",
                            style = Typography.headlineSmall,
                            fontWeight = FontWeight.Light,
                            color = onSurface.copy(alpha = 0.9f)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .background(closeBtnBg, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = onSurface.copy(0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Thin separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(separatorClr)
                )

                // ── A. VISUAL THEME ────────────────────────────────────────────
                PremiumSettingsSection(
                    title = "Visual Theme",
                    subtitle = "Display rendering mode",
                    onSurface = onSurface,
                    subtleText = subtleText
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumThemeCard(
                            label = "Auto",
                            icon = Icons.Rounded.AutoAwesome,
                            mode = 0,
                            selectedMode = settings.themeMode,
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onSelected = onUpdateThemeMode
                        )
                        PremiumThemeCard(
                            label = "Light",
                            icon = Icons.Rounded.WbSunny,
                            mode = 1,
                            selectedMode = settings.themeMode,
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onSelected = onUpdateThemeMode
                        )
                        PremiumThemeCard(
                            label = "Dark",
                            icon = Icons.Rounded.Bedtime,
                            mode = 2,
                            selectedMode = settings.themeMode,
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onSelected = onUpdateThemeMode
                        )
                    }
                }

                // ── B. KIOSK CYCLE HOURS ───────────────────────────────────────
                PremiumSettingsSection(
                    title = "Active Hours",
                    subtitle = "Kiosk wake & sleep schedule",
                    onSurface = onSurface,
                    subtleText = subtleText
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PremiumTimeCard(
                            label = "WAKE UP",
                            hour = settings.wakeHour,
                            minute = settings.wakeMinute,
                            accentColor = Color(0xFF34D399),
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onTimeSelected = onUpdateWakeTime
                        )
                        PremiumTimeCard(
                            label = "SLEEP",
                            hour = settings.sleepHour,
                            minute = settings.sleepMinute,
                            accentColor = Color(0xFF818CF8),
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onTimeSelected = onUpdateSleepTime
                        )
                    }
                }

                // ── C. LOCATION ENGINE ────────────────────────────────────────
                PremiumSettingsSection(
                    title = "Location Engine",
                    subtitle = "Set your city to get accurate weather & day times",
                    onSurface = onSurface,
                    subtleText = subtleText
                ) {
                    PremiumLocationPanel(
                        settings = settings,
                        gpsStatus = gpsStatus,
                        manualGeoStatus = manualGeoStatus,
                        isDark = isDark,
                        onSurface = onSurface,
                        onRequestGps = onRequestGps,
                        onFetchGps = onFetchGps,
                        onSaveManualLocation = onSaveManualLocation,
                        onResetManualGeoStatus = onResetManualGeoStatus,
                        onUpdateAutoLocation = onUpdateAutoLocation,
                        onResetGpsStatus = onResetGpsStatus
                    )
                }

                // ── D. NASA API CONFIGURATION ─────────────────────────────────
                PremiumSettingsSection(
                    title = "NASA API Configuration",
                    subtitle = "Astronomy Picture of the Day settings",
                    onSurface = onSurface,
                    subtleText = subtleText
                ) {
                    PremiumNasaApiPanel(
                        settings = settings,
                        isDark = isDark,
                        onSurface = onSurface,
                        subtleText = subtleText,
                        onSaveApiKey = onUpdateNasaApiKey,
                        onClearApiKey = onClearNasaApiKey
                    )
                }

                // ── E. DASHBOARD IMAGE SOURCE ──────────────────────────────────
                PremiumSettingsSection(
                    title = "Dashboard Image Source",
                    subtitle = "Choose what appears in the art card",
                    onSurface = onSurface,
                    subtleText = subtleText
                ) {
                    DashboardImageSourcePanel(
                        selected = settings.dashboardImageSource,
                        isDark = isDark,
                        onSurface = onSurface,
                        subtleText = subtleText,
                        separatorClr = separatorClr,
                        onSelect = onUpdateDashboardImageSource
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumNasaApiPanel(
    settings: com.smartcockpit.os.KioskSettings,
    isDark: Boolean,
    onSurface: Color,
    subtleText: Color,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    val isCustomKeyActive = settings.nasaApiKey.isNotBlank()
    val panelBg = if (isDark) Color(0xFF131820) else Color(0xFFF0EBE1)
    val inputBg = if (isDark) Color(0xFF1A1F2B) else Color.White
    val borderColor = if (isDark) Color.White.copy(0.1f) else Color(0xFF1A1209).copy(0.1f)
    
    // Clear input field when custom key becomes active or inactive externally
    LaunchedEffect(isCustomKeyActive) {
        if (isCustomKeyActive) apiKeyInput = ""
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = panelBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isCustomKeyActive) {
                // Active Custom Key State
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Custom Key Active", style = Typography.bodyMedium, color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(2.dp))
                        val maskedKey = if (settings.nasaApiKey.length > 8) {
                            "${settings.nasaApiKey.take(4)}••••••••${settings.nasaApiKey.takeLast(4)}"
                        } else "••••••••"
                        Text(maskedKey, style = Typography.labelSmall, color = subtleText, letterSpacing = 1.sp)
                    }
                    Button(
                        onClick = onClearApiKey,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171).copy(0.15f), contentColor = Color(0xFFF87171)),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Reset to Demo", style = Typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Demo Key / Input State
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Public, contentDescription = null, tint = AccentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Using Default Demo Key", style = Typography.bodyMedium, color = AccentColor, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter NASA API Key", color = subtleText, style = Typography.bodyMedium) },
                        singleLine = true,
                        textStyle = Typography.bodyMedium.copy(color = onSurface),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = borderColor,
                            cursorColor = AccentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Button(
                        onClick = { if (apiKeyInput.isNotBlank()) onSaveApiKey(apiKeyInput) },
                        enabled = apiKeyInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentColor, 
                            contentColor = Color(0xFF1A1209),
                            disabledContainerColor = AccentColor.copy(0.3f),
                            disabledContentColor = Color(0xFF1A1209).copy(0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardImageSourcePanel(
    selected: com.smartcockpit.os.DashboardImageSource,
    isDark: Boolean,
    onSurface: Color,
    subtleText: Color,
    separatorClr: Color,
    onSelect: (com.smartcockpit.os.DashboardImageSource) -> Unit
) {
    val panelBg     = if (isDark) Color(0xFF131820) else Color(0xFFF0EBE1)
    val borderColor = if (isDark) Color.White.copy(0.1f) else Color(0xFF1A1209).copy(0.1f)
    val activeAccent = AccentColor

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = panelBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            // ── NASA APOD option ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(com.smartcockpit.os.DashboardImageSource.NASA_APOD) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == com.smartcockpit.os.DashboardImageSource.NASA_APOD,
                    onClick = { onSelect(com.smartcockpit.os.DashboardImageSource.NASA_APOD) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = activeAccent,
                        unselectedColor = subtleText
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "NASA APOD",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected == com.smartcockpit.os.DashboardImageSource.NASA_APOD) activeAccent else onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Fetches the Astronomy Picture of the Day. Adding your personal API Key is recommended to avoid rate limits.",
                        style = Typography.labelSmall,
                        color = subtleText,
                        lineHeight = 16.sp
                    )
                }
            }

            // Thin divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(1.dp)
                    .background(separatorClr)
            )

            // ── LOCAL GALLERY option ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(com.smartcockpit.os.DashboardImageSource.LOCAL_GALLERY) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == com.smartcockpit.os.DashboardImageSource.LOCAL_GALLERY,
                    onClick = { onSelect(com.smartcockpit.os.DashboardImageSource.LOCAL_GALLERY) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = activeAccent,
                        unselectedColor = subtleText
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Local Gallery",
                        style = Typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected == com.smartcockpit.os.DashboardImageSource.LOCAL_GALLERY) activeAccent else onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Rotates your personal Ambient Mode images in the dashboard widget at regular intervals. Fully offline.",
                        style = Typography.labelSmall,
                        color = subtleText,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumSettingsSection(
    title: String,
    subtitle: String,
    onSurface: Color,
    subtleText: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(
                title,
                style = Typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = onSurface.copy(alpha = 0.9f)
            )
            Text(
                subtitle,
                style = Typography.labelSmall,
                color = subtleText,
                letterSpacing = 0.5.sp
            )
        }
        content()
    }
}

@Composable
fun PremiumThemeCard(
    label: String,
    icon: ImageVector,
    mode: Int,
    selectedMode: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    val isSelected = mode == selectedMode
    val unselectedContent = if (isDark) Color.White.copy(0.35f) else Color(0xFF1A1209).copy(0.35f)
    val bgAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250),
        label = "ThemeBg"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.08f,
        animationSpec = tween(250),
        label = "ThemeBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ThemeScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable { onSelected(mode) },
        color = AccentColor.copy(alpha = bgAlpha * 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccentColor.copy(alpha = borderAlpha))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) AccentColor else unselectedContent,
                modifier = Modifier.size(26.dp)
            )
            Text(
                label,
                style = Typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) AccentColor else unselectedContent,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun PremiumTimeCard(
    label: String,
    hour: Int,
    minute: Int,
    accentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onTimeSelected: (Int, Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    val tapHint  = if (isDark) Color.White.copy(0.2f) else Color(0xFF1A1209).copy(0.25f)
    val dialogBg = if (isDark) Color(0xFF1A1F2B)     else Color(0xFFF8F5EE)
    val dialBg   = if (isDark) Color(0xFF222836)     else Color(0xFFEDE7DB)
    val cancelClr = if (isDark) Color.White.copy(0.4f) else Color(0xFF1A1209).copy(0.4f)

    Surface(
        modifier = modifier.clickable { showDialog = true },
        color = accentColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, style = Typography.labelSmall, color = accentColor.copy(alpha = 0.7f), letterSpacing = 1.5.sp)
            Text("%02d:%02d".format(hour, minute), style = Typography.headlineMedium, fontWeight = FontWeight.Light, fontSize = 36.sp, color = accentColor.copy(alpha = 0.95f))
            Text("Tap to edit", style = Typography.labelSmall, color = tapHint, letterSpacing = 0.5.sp)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { onTimeSelected(state.hour, state.minute); showDialog = false }) {
                    Text("SET", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCEL", color = cancelClr)
                }
            },
            containerColor = dialogBg,
            shape = RoundedCornerShape(24.dp),
            title = { Text(label, style = Typography.titleMedium, color = accentColor.copy(0.8f)) },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = dialBg,
                        selectorColor = accentColor,
                        timeSelectorSelectedContainerColor = accentColor.copy(0.2f),
                        timeSelectorSelectedContentColor = accentColor
                    )
                )
            }
        )
    }
}

@Composable
fun PremiumLocationPanel(
    settings: com.smartcockpit.os.KioskSettings,
    gpsStatus: GpsStatus,
    manualGeoStatus: ManualGeoStatus,
    isDark: Boolean,
    onSurface: Color,
    onRequestGps: () -> Unit,
    onFetchGps: () -> Unit,
    onSaveManualLocation: (String) -> Unit,
    onResetManualGeoStatus: () -> Unit,
    onUpdateAutoLocation: (Boolean) -> Unit,
    onResetGpsStatus: () -> Unit
) {
    val context = LocalContext.current

    // Permission-aware GPS trigger:
    // If already granted → fetch immediately. Otherwise → launch system dialog.
    val launchGps: () -> Unit = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) onFetchGps() else onRequestGps()
    }

    // ── Dynamic palette ───────────────────────────────────────────────────────
    val cardSurface   = if (isDark) Color.White.copy(0.04f)  else Color(0xFF1A1209).copy(0.04f)
    val cardBorder    = if (isDark) Color.White.copy(0.07f)  else Color(0xFF1A1209).copy(0.08f)
    val labelClr      = if (isDark) Color.White.copy(0.3f)   else Color(0xFF1A1209).copy(0.35f)
    val segBg         = if (isDark) Color.White.copy(0.05f)  else Color(0xFF1A1209).copy(0.06f)
    val segUnsel      = if (isDark) Color.White.copy(0.4f)   else Color(0xFF1A1209).copy(0.4f)
    val tfText        = if (isDark) Color.White               else Color(0xFF1A1209)
    val tfBorderUn    = if (isDark) Color.White.copy(0.15f)  else Color(0xFF1A1209).copy(0.2f)
    val tfContUn      = if (isDark) Color.Transparent        else Color(0xFF1A1209).copy(0.03f)
    val tfContFoc     = if (isDark) Color.White.copy(0.04f)  else Color(0xFF1A1209).copy(0.04f)

    // Auto-reset GPS status after a brief success display
    LaunchedEffect(gpsStatus) {
        if (gpsStatus == GpsStatus.SUCCESS || gpsStatus == GpsStatus.ERROR) {
            delay(3000)
            onResetGpsStatus()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Segmented toggle: Auto GPS ↔ Manual
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(segBg, RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            listOf(true to "Auto (GPS)", false to "Manual").forEach { (isAuto, label) ->
                val isSelected = settings.isAutoLocation == isAuto
                val bgAnim by animateColorAsState(
                    targetValue = if (isSelected) AccentColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "SegBg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgAnim)
                        .clickable {
                            onUpdateAutoLocation(isAuto)
                            // When tapping Auto (GPS), also trigger a fetch immediately
                            if (isAuto) launchGps()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isAuto) Icons.Rounded.GpsFixed else Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else segUnsel,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            label,
                            style = Typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else segUnsel
                        )
                    }
                }
            }
        }

        // GPS Panel
        if (settings.isAutoLocation) {
            Surface(
                color = cardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CURRENT POSITION", style = Typography.labelSmall, color = labelClr, letterSpacing = 1.sp)
                            Spacer(Modifier.height(6.dp))
                            if (settings.locationDisplayName.isNotBlank()) {
                                Text(settings.locationDisplayName, style = Typography.titleMedium, fontWeight = FontWeight.Medium, color = onSurface.copy(0.9f))
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                "%.4f°N  %.4f°E".format(settings.latitude, settings.longitude),
                                style = Typography.bodySmall,
                                color = AccentColor.copy(0.7f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        when (gpsStatus) {
                            GpsStatus.FETCHING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AccentColor)
                            GpsStatus.SUCCESS  -> Text("✓", color = Color(0xFF34D399), fontSize = 20.sp)
                            GpsStatus.ERROR, GpsStatus.DENIED, GpsStatus.DISABLED -> Text("✕", color = Color(0xFFF87171), fontSize = 20.sp)
                            GpsStatus.IDLE -> {}
                        }
                    }

                    AnimatedVisibility(visible = gpsStatus == GpsStatus.ERROR || gpsStatus == GpsStatus.DENIED || gpsStatus == GpsStatus.DISABLED) {
                        Text(
                            when (gpsStatus) {
                                GpsStatus.ERROR  -> "Location fetch failed. Retaining last known position."
                                GpsStatus.DENIED -> "Location permission denied. Please grant in system settings."
                                GpsStatus.DISABLED -> "System Location is disabled. Please turn it on in device settings."
                                else -> ""
                            },
                            style = Typography.bodySmall, color = Color(0xFFF87171).copy(0.8f)
                        )
                    }

                    // Refresh button — permission-aware
                    OutlinedButton(
                        onClick = launchGps,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentColor.copy(0.4f)),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        enabled = gpsStatus != GpsStatus.FETCHING
                    ) {
                        Icon(Icons.Rounded.GpsFixed, contentDescription = null, tint = AccentColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (gpsStatus == GpsStatus.FETCHING) "Acquiring Signal..." else "Refresh GPS Location",
                            color = AccentColor, style = Typography.labelSmall
                        )
                    }
                }
            }
        } else {
            // ── MANUAL MODE — Dual-Engine Geocoding Search Panel ──────────────
            var queryText by remember { mutableStateOf("") }

            LaunchedEffect(manualGeoStatus) {
                if (manualGeoStatus is ManualGeoStatus.Success) {
                    delay(3_000)
                    onResetManualGeoStatus()
                }
            }

            Surface(
                color = cardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("MANUAL LOCATION", style = Typography.labelSmall, color = labelClr, letterSpacing = 1.sp)
                    if (settings.locationDisplayName.isNotBlank()) {
                        Text(settings.locationDisplayName, style = Typography.titleSmall, fontWeight = FontWeight.Medium, color = onSurface.copy(0.8f))
                    }
                    Text(
                        "Lat %.4f  ·  Lon %.4f".format(settings.latitude, settings.longitude),
                        style = Typography.bodySmall,
                        color = AccentColor.copy(0.6f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("e.g. Istanbul, Turkey", style = Typography.bodySmall, color = labelClr) },
                            singleLine = true,
                            enabled = manualGeoStatus !is ManualGeoStatus.Loading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { if (manualGeoStatus !is ManualGeoStatus.Loading) onSaveManualLocation(queryText) }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor        = tfText,
                                unfocusedTextColor      = tfText.copy(0.8f),
                                focusedBorderColor      = AccentColor.copy(0.6f),
                                unfocusedBorderColor    = tfBorderUn,
                                cursorColor             = AccentColor,
                                focusedContainerColor   = tfContFoc,
                                unfocusedContainerColor = tfContUn
                            ),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = Typography.bodySmall
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (manualGeoStatus is ManualGeoStatus.Loading) cardSurface else AccentColor.copy(0.15f))
                                .clickable(enabled = manualGeoStatus !is ManualGeoStatus.Loading) { onSaveManualLocation(queryText) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (manualGeoStatus is ManualGeoStatus.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentColor)
                            } else {
                                Icon(Icons.Rounded.LocationOn, contentDescription = "Search location", tint = AccentColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = manualGeoStatus is ManualGeoStatus.Success || manualGeoStatus is ManualGeoStatus.Error
                    ) {
                        val isError = manualGeoStatus is ManualGeoStatus.Error
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isError) "✕" else "✓", color = if (isError) Color(0xFFF87171) else Color(0xFF34D399), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                when (manualGeoStatus) {
                                    is ManualGeoStatus.Success -> "Saved: ${(manualGeoStatus as ManualGeoStatus.Success).displayName}"
                                    is ManualGeoStatus.Error   -> (manualGeoStatus as ManualGeoStatus.Error).message
                                    else -> ""
                                },
                                style = Typography.bodySmall,
                                color = if (isError) Color(0xFFF87171).copy(0.85f) else Color(0xFF34D399).copy(0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LEGACY COMPOSABLES (preserved — used by GalleryManagementPanel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = Typography.titleSmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ThemeOptionCard(label: String, icon: String, mode: Int, selectedMode: Int, modifier: Modifier, onSelected: (Int) -> Unit) {
    val isSelected = mode == selectedMode
    Surface(
        modifier = modifier.clickable { onSelected(mode) },
        color = if (isSelected) AccentColor.copy(0.15f) else Color.White.copy(0.05f),
        shape = Shapes.medium,
        border = BorderStroke(1.dp, if (isSelected) AccentColor else Color.White.copy(0.05f))
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, fontSize = 24.sp)
            Text(label, style = Typography.bodySmall, color = if (isSelected) AccentColor else Color.Gray)
        }
    }
}

@Composable
fun KenBurnsImage(uri: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "KenBurns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Zoom"
    )

    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale)
    )
}

@Composable
fun GalleryManagementPanel(
    images: List<com.smartcockpit.data.local.GalleryEntity>,
    isDayGlobal: Boolean,
    pixelOffset: DpOffset,
    textAlpha: Float,
    onAddImage: () -> Unit,
    onRemoveImage: (com.smartcockpit.data.local.GalleryEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onExitAmbient: () -> Unit
) {
    // 1. 5-Minute Oscillation Engine (Background & Surface)
    val colorTransition = rememberInfiniteTransition(label = "GalleryOscillation")
    val animatedBackgroundColor by colorTransition.animateColor(
        initialValue = if (isDayGlobal) Color(0xFFFDFBF7) else Color(0xFF0A0E17),
        targetValue = if (isDayGlobal) Color(0xFFFAF5EC) else Color(0xFF111622),
        animationSpec = infiniteRepeatable(
            animation = tween(300000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgOscillation"
    )

    val animatedSurfaceColor by colorTransition.animateColor(
        initialValue = if (isDayGlobal) Surface else Color(0xFF161B26),
        targetValue = if (isDayGlobal) Color(0xFFF8F5EE) else Color(0xFF1C2230),
        animationSpec = infiniteRepeatable(
            animation = tween(300000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SurfaceOscillation"
    )

    // 2. Dynamic Color Tokens
    val dynamicPrimaryText = if (isDayGlobal) PrimaryText else Color(0xFFF3F4F6)
    val dynamicBorder = if (isDayGlobal) BorderColor else Color(0xFF2D3748)

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = animatedBackgroundColor.copy(alpha = 0.98f)
    ) {
        Column(modifier = Modifier.padding(32.dp).offset(pixelOffset.x, pixelOffset.y)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ambient Gallery",
                    style = Typography.headlineMedium,
                    color = dynamicPrimaryText.copy(alpha = textAlpha)
                )
                Row {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = dynamicPrimaryText.copy(0.6f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onAddImage, colors = ButtonDefaults.buttonColors(containerColor = AccentColor)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Image")
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(
                        onClick = onExitAmbient,
                        border = BorderStroke(1.dp, dynamicBorder)
                    ) {
                        Text("Exit Ambient", color = dynamicPrimaryText)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = dynamicPrimaryText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(images) { image ->
                    Box {
                        androidx.compose.material3.Surface(
                            modifier = Modifier.size(150.dp),
                            shape = Shapes.medium,
                            color = animatedSurfaceColor,
                            border = BorderStroke(1.dp, dynamicBorder)
                        ) {
                            AsyncImage(
                                model = image.imageUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onError = { state ->
                                    println("Gallery Image Load Error: ${state.result.throwable}")
                                }
                            )
                        }
                        IconButton(
                            onClick = { onRemoveImage(image) },
                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingHUD(weather: com.smartcockpit.data.local.WeatherEntity?) {
    // 1. Live Clock State (1-second ticker)
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.ENGLISH) }

    // 2. Weather Synchronization (Matching Dashboard "Now" logic)
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val currentTemp = remember(weather, currentHour) {
        weather?.let {
            val startIndex = currentHour.coerceIn(0, it.hourlyTemps.size - 1)
            it.hourlyTemps[startIndex].toInt().toString()
        } ?: "--"
    }
    val currentIcon = remember(weather, currentHour) {
        weather?.let {
            val startIndex = currentHour.coerceIn(0, it.hourlyWeatherCodes.size - 1)
            val cal = Calendar.getInstance()
            val isDayHUD = cal.get(Calendar.HOUR_OF_DAY) in 6..20
            getWeatherDescription(it.hourlyWeatherCodes[startIndex], isDayHUD).icon
        } ?: "☁️"
    }

    // 3. Movement (Anti-Burn-In)
    val infiniteTransition = rememberInfiniteTransition(label = "HudMovement")

    val offsetX by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "X_Shift"
    )

    val offsetY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Y_Shift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        ) {
            Text(
                text = timeFormat.format(Date(currentTime.longValue)),
                style = Typography.headlineLarge,
                color = Color.White.copy(alpha = 0.6f)
            )

            Text(
                "${currentTemp}°C $currentIcon",
                style = Typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
