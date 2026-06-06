package com.smartcockpit.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcockpit.data.local.NasaApodEntity
import com.smartcockpit.data.local.PhraseEntity
import com.smartcockpit.data.local.PrayerEntity
import android.util.Log
import com.smartcockpit.data.local.WeatherEntity
import com.smartcockpit.ui.theme.*
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToAmbient: () -> Unit
) {
    val weather by viewModel.weather.collectAsState(initial = null)
    val apod by viewModel.latestApod.collectAsState(initial = null)
    val phrase by viewModel.dailyPhrase.collectAsState(initial = null)
    val prayerTimes by viewModel.prayerTimes.collectAsState(initial = null)
    val isApodLoading by viewModel.isApodLoading.collectAsState()
    val isApodError by viewModel.isApodError.collectAsState()

    // 1. TOP-LEVEL SYSTEM CLOCK TICKER (1-second)
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    // --- HOISTED DAY/NIGHT STATE ---
    val isDayGlobal = remember(prayerTimes, currentTime.longValue) {
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

    // Reset loading state if data arrives
    LaunchedEffect(apod, phrase, prayerTimes) {
        if (apod != null && phrase != null && prayerTimes != null) {
            // Internal logic to signal complete readiness if needed
        }
    }

    var isSleeping by remember { mutableStateOf(false) }

    // --- SCREEN PROTECTION STATES ---
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
            
            // 1. Deterministic Pixel Rotation (4-point engine)
            shiftIndex = (shiftIndex + 1) % offsets.size
            pixelOffset = offsets[shiftIndex]
            
            // 2. Text Alpha (0.40f to 0.55f)
            textAlpha = (40..55).random() / 100f
        }
    }

    // Background Tone Oscillation Logic (5-minute cycle)
    val colorTransition = rememberInfiniteTransition(label = "DashboardOscillation")
    val animatedBackgroundColor by colorTransition.animateColor(
        initialValue = if (isDayGlobal) Color(0xFFFDFBF7) else Color(0xFF0A0E17),
        targetValue = if (isDayGlobal) Color(0xFFFAF5EC) else Color(0xFF111622),
        animationSpec = infiniteRepeatable(
            animation = tween(300000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BackgroundOscillation"
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

    // --- DYNAMIC COLOR TOKENS ---
    val dynamicSurface = animatedSurfaceColor
    val dynamicBorder = if (isDayGlobal) BorderColor else Color(0xFF2D3748)
    val dynamicPrimaryText = if (isDayGlobal) PrimaryText else Color(0xFFF3F4F6)
    val dynamicSecondaryText = if (isDayGlobal) SecondaryText else Color(0xFF9CA3AF)
    val dynamicAccent = if (isDayGlobal) AccentColor else Color(0xFF967B61) // Matte Antique Bronze
    val dynamicSleepText = if (isDayGlobal) PrimaryText else Color(0xFFE5E7EB)
    val dynamicSleepBorder = if (isDayGlobal) BorderColor else Color(0xFF4B5563)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize().background(animatedBackgroundColor)) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // LEFT COLUMN (65%) - Weather & Prayer
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                ) {
                    WeatherKingCard(
                        weather = weather, 
                        prayerTimes = prayerTimes,
                        currentTime = currentTime.longValue,
                        surfaceColor = dynamicSurface,
                        borderColor = dynamicBorder,
                        primaryTextColor = dynamicPrimaryText,
                        secondaryTextColor = dynamicSecondaryText,
                        dynamicAccent = dynamicAccent,
                        textAlpha = textAlpha,
                        modifier = Modifier
                            .weight(0.75f)
                            .offset(pixelOffset.x, pixelOffset.y)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    PrayerTimesRow(
                        prayerTimes = prayerTimes, 
                        pixelOffset = pixelOffset,
                        textAlpha = textAlpha,
                        surfaceColor = dynamicSurface,
                        borderColor = dynamicBorder,
                        primaryTextColor = dynamicPrimaryText,
                        secondaryTextColor = dynamicSecondaryText,
                        isDayGlobal = isDayGlobal,
                        modifier = Modifier.weight(0.25f).offset(pixelOffset.x, pixelOffset.y)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // RIGHT COLUMN (35%) - Art & Learning
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                ) {
                    FramedApodCard(apod, isApodLoading, isApodError, pixelOffset, textAlpha, surfaceColor = dynamicSurface, borderColor = dynamicBorder, secondaryTextColor = dynamicSecondaryText, modifier = Modifier.weight(1f).offset(pixelOffset.x, pixelOffset.y))

                    Spacer(modifier = Modifier.height(24.dp))

                    ElegantPhraseCard(phrase, pixelOffset, textAlpha, surfaceColor = dynamicSurface, borderColor = dynamicBorder, primaryTextColor = dynamicPrimaryText, secondaryTextColor = dynamicSecondaryText, accentColor = dynamicAccent, modifier = Modifier.wrapContentHeight().offset(pixelOffset.x, pixelOffset.y))

                    Spacer(modifier = Modifier.height(24.dp))

                    // Unified Bottom-Right Block: Stacked Buttons + Time/Date
                    Row(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .offset(pixelOffset.x, pixelOffset.y),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            modifier = Modifier.width(135.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isSleeping = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = Shapes.medium,
                                border = BorderStroke(1.dp, dynamicSleepBorder),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("SLEEP", style = Typography.labelSmall, color = dynamicSleepText, letterSpacing = 1.5.sp)
                            }

                            Button(
                                onClick = onNavigateToAmbient,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = Shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = dynamicAccent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("AMBIENT", style = Typography.labelSmall, color = Color.White, letterSpacing = 1.5.sp)
                            }
                        }

                        CompactClockArea(
                            currentTime = currentTime.longValue,
                            pixelOffset = pixelOffset,
                            textAlpha = textAlpha,
                            primaryTextColor = dynamicPrimaryText,
                            secondaryTextColor = dynamicSecondaryText,
                            modifier = Modifier
                        )
                    }
                }
            }
        } else {
            // PORTRAIT OPTIMIZATION
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // TOP: Weather King (High Impact)
                WeatherKingCard(
                    weather = weather, 
                    prayerTimes = prayerTimes,
                    currentTime = currentTime.longValue,
                    surfaceColor = dynamicSurface,
                    borderColor = dynamicBorder,
                    primaryTextColor = dynamicPrimaryText,
                    secondaryTextColor = dynamicSecondaryText,
                    dynamicAccent = dynamicAccent,
                    textAlpha = textAlpha,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .offset(pixelOffset.x, pixelOffset.y)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // MIDDLE: Art & Learning (Stacked)
                Row(modifier = Modifier.weight(0.30f).offset(pixelOffset.x, pixelOffset.y)) {
                    FramedApodCard(apod, isApodLoading, isApodError, pixelOffset, textAlpha, surfaceColor = dynamicSurface, borderColor = dynamicBorder, secondaryTextColor = dynamicSecondaryText, modifier = Modifier.weight(0.6f).fillMaxHeight())
                    Spacer(modifier = Modifier.width(16.dp))
                    ElegantPhraseCard(phrase, pixelOffset, textAlpha, surfaceColor = dynamicSurface, borderColor = dynamicBorder, primaryTextColor = dynamicPrimaryText, secondaryTextColor = dynamicSecondaryText, accentColor = dynamicAccent, modifier = Modifier.weight(0.4f).fillMaxHeight())
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Unified Bottom-Right Block for Portrait
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(pixelOffset.x, pixelOffset.y),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom // Anchored to bottom baseline
                ) {
                    Column(
                        modifier = Modifier.width(130.dp), // Unified fixed width
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Premium breathing room
                    ) {
                        OutlinedButton(
                            onClick = { isSleeping = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp), // Plumped up height
                            shape = Shapes.medium,
                            border = BorderStroke(1.dp, dynamicSleepBorder),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("SLEEP", style = Typography.labelSmall, color = dynamicSleepText)
                        }
                        Button(
                            onClick = onNavigateToAmbient,
                            modifier = Modifier.fillMaxWidth().height(48.dp), // Plumped up height
                            shape = Shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = dynamicAccent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("AMBIENT", style = Typography.labelSmall, color = Color.White)
                        }
                    }
                    
                    CompactClockArea(currentTime.longValue, pixelOffset, textAlpha, primaryTextColor = dynamicPrimaryText, secondaryTextColor = dynamicSecondaryText, modifier = Modifier)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // BOTTOM: Prayer
                PrayerTimesRow(
                    prayerTimes = prayerTimes, 
                    pixelOffset = pixelOffset,
                    textAlpha = textAlpha,
                    surfaceColor = dynamicSurface,
                    borderColor = dynamicBorder,
                    primaryTextColor = dynamicPrimaryText,
                    secondaryTextColor = dynamicSecondaryText,
                    isDayGlobal = isDayGlobal,
                    modifier = Modifier.weight(0.12f).offset(pixelOffset.x, pixelOffset.y)
                )
            }
        }

        // SLEEP OVERLAY
        AnimatedVisibility(
            visible = isSleeping,
            enter = fadeIn(tween(1000)),
            exit = fadeOut(tween(1000))
        ) {
            SleepOverlay(onWake = { isSleeping = false })
        }
    }
}

@Composable
fun WeatherKingCard(
    weather: WeatherEntity?, 
    prayerTimes: PrayerEntity?,
    currentTime: Long,
    surfaceColor: Color,
    borderColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    dynamicAccent: Color,
    textAlpha: Float,
    modifier: Modifier = Modifier
) {
    // 2. REACTIVE HOURLY FORECAST SLIDING WINDOW
    val cal = remember(currentTime) { Calendar.getInstance().apply { timeInMillis = currentTime } }
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    val currentTotalMin = currentHour * 60 + cal.get(Calendar.MINUTE)
    
    val filteredHourly = remember(weather, currentHour) {
        weather?.let {
            // Find index of current hour and take next 10 (Total 11)
            val startIndex = currentHour.coerceIn(0, it.hourlyTemps.size - 11)
            it.hourlyTemps.zip(it.hourlyWeatherCodes)
                .subList(startIndex, (startIndex + 11).coerceAtMost(it.hourlyTemps.size))
        } ?: emptyList()
    }

    // Weekly day names formatter - FORCED TO ENGLISH
    val dayFormatter = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }

    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Unified current hour data from sliding window
    val currentNowData = filteredHourly.firstOrNull()
    val displayWeatherCode = currentNowData?.second ?: weather?.weatherCode ?: 0
    val displayTemp = currentNowData?.first?.toInt()?.toString() ?: weather?.temperature?.toInt()?.toString() ?: "--"
    
    // Time-aware icon logic for Main Card
    fun parseMinLocal(time: String?): Int {
        if (time == null || time == "--:--") return 0
        val parts = time.split(":")
        return if (parts.size >= 2) parts[0].toInt() * 60 + parts[1].toInt() else 0
    }
    val sunriseMinLocal = parseMinLocal(prayerTimes?.sunrise)
    val sunsetMinLocal = parseMinLocal(prayerTimes?.sunset)
    
    val isDayNow = if (sunriseMinLocal > 0 && sunsetMinLocal > 0) {
        currentTotalMin in sunriseMinLocal until sunsetMinLocal
    } else {
        currentHour in 6..20
    }

    val displayCondition = weather?.let { getWeatherDescription(displayWeatherCode, isDayNow).condition.uppercase() } ?: "SYNCING..."

    // --- ADVANCED CONTRAST ENGINE ---
    val premiumSunColor = if (isDayNow) Color(0xFFF2C94C) else Color(0xFFF7F4EF)
    val premiumCloudColor = if (isDayNow) secondaryTextColor.copy(alpha = 0.2f) else Color(0xFF94A3B8).copy(alpha = 0.4f)
    val premiumRainColor = if (isDayNow) Color(0xFF3B82F6).copy(alpha = 0.6f) else Color(0xFF60A5FA).copy(alpha = 0.5f)

    Surface(
        modifier = modifier,
        color = surfaceColor,
        shape = Shapes.large,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp
    ) {
        Box(modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight()) {
            // Dynamic Background Animation (Constrained)
            Box(modifier = Modifier.matchParentSize().alpha(0.6f)) {
                WeatherAnimationLayer(
                    code = displayWeatherCode,
                    sunColor = premiumSunColor,
                    cloudColor = premiumCloudColor
                )
            }


            Column(
                modifier = if (isLandscape) Modifier.padding(32.dp).fillMaxSize() 
                           else Modifier.padding(16.dp).fillMaxWidth().wrapContentHeight(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    "ATMOSPHERIC CONDITIONS",
                    style = Typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.alpha(textAlpha)
                )
                
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 12.dp))

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.4f).alpha(textAlpha)) {
                            Text(
                                text = "${displayTemp}°",
                                style = Typography.headlineLarge,
                                fontSize = 160.sp, 
                                fontWeight = FontWeight.ExtraLight,
                                lineHeight = 160.sp,
                                color = primaryTextColor
                            )
                            Text(
                                text = displayCondition,
                                style = Typography.titleLarge,
                                color = primaryTextColor,
                                letterSpacing = 4.sp,
                                fontSize = 28.sp
                            )
                            if (weather != null) {
                                Text(
                                    "H: ${weather.maxTemp.toInt()}°  L: ${weather.minTemp.toInt()}°",
                                    style = Typography.titleMedium,
                                    color = secondaryTextColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }

                        // Vertical Weekly Forecast - SCALED UP
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .padding(horizontal = 24.dp)
                                .alpha(textAlpha),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            weather?.let { w ->
                                w.dailyTempsMax.take(5).forEachIndexed { index, max ->
                                    val dayName = if (index == 0) "Today" else {
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, index)
                                        dayFormatter.format(cal.time)
                                    }
                                    DailyForecastRowCompact(
                                        day = dayName,
                                        max = max.toInt(),
                                        min = w.dailyTempsMin[index].toInt(),
                                        code = w.dailyWeatherCodes[index],
                                        primaryTextColor = primaryTextColor,
                                        secondaryTextColor = secondaryTextColor
                                    )
                                }
                            }
                        }
                        
                        // Hero Animation Area - Strictly Constrained
                        Box(
                            modifier = Modifier
                                .weight(0.25f)
                                .aspectRatio(1f)
                                .padding(start = 16.dp)
                                .clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            DynamicWeatherScene(
                                code = displayWeatherCode,
                                isDay = isDayNow,
                                sunColor = premiumSunColor,
                                cloudColor = premiumCloudColor,
                                rainColor = premiumRainColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    // PORTRAIT INTERNAL LAYOUT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.alpha(textAlpha)) {
                            Text(
                                text = "${displayTemp}°",
                                style = Typography.headlineLarge,
                                fontSize = 100.sp,
                                fontWeight = FontWeight.ExtraLight,
                                color = primaryTextColor
                            )
                            Text(
                                text = displayCondition,
                                style = Typography.titleMedium,
                                color = primaryTextColor,
                                letterSpacing = 2.sp
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clipToBounds()
                        ) {
                            DynamicWeatherScene(
                                code = displayWeatherCode,
                                isDay = isDayNow,
                                sunColor = premiumSunColor,
                                cloudColor = premiumCloudColor,
                                rainColor = premiumRainColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Vertical Weekly for Portrait - Reverted and Scaled
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp).alpha(textAlpha),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        weather?.let { w ->
                            w.dailyTempsMax.take(4).forEachIndexed { index, max ->
                                val dayName = if (index == 0) "Today" else {
                                    val cal = Calendar.getInstance()
                                    cal.add(Calendar.DAY_OF_YEAR, index)
                                    dayFormatter.format(cal.time)
                                }
                                DailyForecastRowCompact(
                                    day = dayName,
                                    max = max.toInt(),
                                    min = w.dailyTempsMin[index].toInt(),
                                    code = w.dailyWeatherCodes[index],
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        }
                    }
                }


                if (isLandscape) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.height(24.dp)) // Tight margin to bring Hourly Forecast up
                }

                // Hourly Trend Title
                Text(
                    "HOURLY FORECAST",
                    style = Typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = if (isLandscape) 24.dp else 12.dp).alpha(textAlpha)
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth().alpha(textAlpha),
                    horizontalArrangement = Arrangement.SpaceBetween // Spread out to fill horizontal space
                ) {
                    itemsIndexed(filteredHourly) { index, (temp, code) ->
                        val itemHour = (currentHour + index) % 24
                        
                        // Dynamic Day/Night & Special Astro Icons per Hourly Item
                        val itemIsDay = if (sunriseMinLocal > 0 && sunsetMinLocal > 0) {
                            val itemMin = itemHour * 60
                            itemMin in sunriseMinLocal until sunsetMinLocal
                        } else {
                            itemHour in 6..20
                        }

                        val itemIcon = when (itemHour) {
                            sunriseMinLocal / 60 -> "🌅" // Transition: Sunrise
                            sunsetMinLocal / 60 -> "🌇"  // Transition: Sunset
                            else -> getWeatherDescription(code, itemIsDay).icon
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                if (index == 0) "Now" else "${itemHour}:00", 
                                style = Typography.bodyMedium, // Larger font
                                color = if (index == 0) dynamicAccent else secondaryTextColor,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(itemIcon, fontSize = 32.sp) // Dynamic Icon
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("${temp.toInt()}°", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primaryTextColor) // Larger temp
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicWeatherScene(
    code: Int,
    isDay: Boolean,
    sunColor: Color,
    cloudColor: Color,
    rainColor: Color,
    modifier: Modifier = Modifier
) {
    // 1. Localized Retry Engine (Breaks Cache Deadlock)
    var retryTrigger by remember { mutableIntStateOf(0) }

    // 2. Stabilize the URL composition spec
    val lottieSpec = remember(code, isDay, retryTrigger) {
        getMeteoconsUrl(code, isDay)?.let { LottieCompositionSpec.Url(it) }
    }
    
    // 3. Controlled Debug Logging
    LaunchedEffect(lottieSpec) {
        lottieSpec?.let {
            Log.d("WeatherLottie", "Fetching URL: ${(it as LottieCompositionSpec.Url).url} (Retry: $retryTrigger)")
        }
    }

    // 4. Optimized Composition Fetch
    val dummySpec = remember { LottieCompositionSpec.Url("") }
    val compositionResult = rememberLottieComposition(lottieSpec ?: dummySpec)
    val composition by compositionResult
    
    // 5. Auto-Retry Loop (ONLY fires on failure)
    LaunchedEffect(compositionResult.isFailure) {
        if (compositionResult.isFailure) {
            while (true) {
                delay(15000) // 15s back-off
                retryTrigger++
            }
        }
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (compositionResult.isFailure || lottieSpec == null) {
            // Log exact failure reason
            LaunchedEffect(compositionResult.isFailure) {
                if (compositionResult.isFailure) {
                    val targetUrl = (lottieSpec as? LottieCompositionSpec.Url)?.url ?: "Unknown"
                    Log.e("WeatherLottie", "Lottie Failed to load asset from URL: $targetUrl", compositionResult.error)
                }
            }

            // Error State: Visible Abstract Gradient (Enhanced for Obsidian)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(sunColor.copy(alpha = 0.25f), Color.Transparent),
                        center = center,
                        radius = size.width / 1.5f
                    )
                )
            }
        } else if (compositionResult.isLoading) {
            // Loading State: Pulsating Shimmer
            val infiniteTransition = rememberInfiniteTransition(label = "LoadingPulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.05f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                label = "Alpha"
            )
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
                label = "Scale"
            )

            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)) {
                drawCircle(
                    color = sunColor,
                    alpha = pulseAlpha,
                    center = center,
                    radius = size.width / 2
                )
            }
        } else {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun getMeteoconsUrl(code: Int, isDay: Boolean): String? {
    val baseUrl = "https://cdn.jsdelivr.net/npm/@meteocons/lottie/fill/"
    val icon = when (code) {
        0 -> if (isDay) "clear-day.json" else "clear-night.json"
        1, 2 -> if (isDay) "partly-cloudy-day.json" else "partly-cloudy-night.json"
        3 -> "overcast.json"
        45, 48 -> "fog.json"
        51, 53, 55 -> "drizzle.json"
        61, 63 -> "rain.json"
        65 -> "extreme-rain.json"
        71, 73, 75 -> "snow.json"
        77 -> "hail.json"
        80, 81, 82 -> "rain.json"
        85, 86 -> "snow.json"
        95 -> "thunderstorms-day.json"
        96, 99 -> "thunderstorms-extreme.json"
        else -> null
    }
    return icon?.let { baseUrl + it }
}


@Composable
fun DailyForecastRowCompact(
    day: String, 
    max: Int, 
    min: Int, 
    code: Int,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            day, 
            modifier = Modifier.width(60.dp), 
            style = Typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = primaryTextColor
        )
        Text(
            getWeatherDescription(code).icon, 
            fontSize = 22.sp
        )
        Text(
            "${max}° / ${min}°", 
            style = Typography.titleMedium, 
            color = secondaryTextColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
fun BoxScope.WeatherAnimationLayer(
    code: Int,
    sunColor: Color = AccentColor,
    cloudColor: Color = Color.Gray
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WeatherBackground")
    
    Box(modifier = Modifier.matchParentSize()) {
        when (code) {
            0 -> { // Clear Sky
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.04f,
                    targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse),
                    label = "GlowAlpha"
                )
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(sunColor.copy(alpha = glowAlpha), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.25f),
                            radius = size.width * 0.6f
                        )
                    )
                }
            }
            1, 2, 3 -> { // Cloudy Background
                val driftX by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1500f,
                    animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing)),
                    label = "BgDrift"
                )
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, cloudColor.copy(0.12f), Color.Transparent),
                        startX = driftX - 800f,
                        endX = driftX
                    )
                ))
            }
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> { // Rain
                val rainY by infiniteTransition.animateFloat(
                    initialValue = -100f,
                    targetValue = 2000f,
                    animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                    label = "BgRain"
                )
                Canvas(modifier = Modifier.fillMaxSize().alpha(0.08f)) {
                    for (i in 0..15) {
                        val x = (i * 120f) % size.width
                        val y = (rainY + (i * 80f)) % size.height
                        drawLine(
                            color = Color.Blue,
                            start = Offset(x, y),
                            end = Offset(x, y + 30f),
                            strokeWidth = 1.5f
                        )
                    }
                }
            }
            else -> { // Subtle Drift
                val driftX by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
                    label = "SubtleDrift"
                )
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, cloudColor.copy(0.05f), Color.Transparent),
                        startX = driftX,
                        endX = driftX + 600f
                    )
                ))
            }
        }
    }
}


@Composable
fun PrayerTimesRow(
    prayerTimes: PrayerEntity?, 
    pixelOffset: DpOffset,
    textAlpha: Float,
    surfaceColor: Color,
    borderColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isDayGlobal: Boolean,
    modifier: Modifier = Modifier
) {
    // 1. Time Mapping Logic
    val currentTime = Calendar.getInstance()
    val currentTotalMin = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)

    fun parseMin(time: String?): Int {
        if (time == null || time == "--:--") return 0
        val parts = time.split(":")
        return if (parts.size < 2) 0 else parts[0].toInt() * 60 + parts[1].toInt()
    }

    val sunriseMin = parseMin(prayerTimes?.sunrise)
    val sunsetMin = parseMin(prayerTimes?.sunset)
    
    val isDay = currentTotalMin in sunriseMin until sunsetMin
    
    // 2. Celestial Progress Engine (0.0 to 1.0)
    val progress = if (isDay) {
        val range = (sunsetMin - sunriseMin).coerceAtLeast(1)
        (currentTotalMin - sunriseMin).toFloat() / range.toFloat()
    } else {
        val range = (1440 - sunsetMin + sunriseMin).coerceAtLeast(1)
        val elapsed = if (currentTotalMin >= sunsetMin) currentTotalMin - sunsetMin else (1440 - sunsetMin) + currentTotalMin
        elapsed.toFloat() / range.toFloat()
    }.coerceIn(0f, 1f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isDay) Color(0xFFE5EFFB) else Color(0xFF1E293B), // Dynamic Sky Blue / Night Blue
        shape = Shapes.large,
        border = BorderStroke(1.dp, borderColor)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "DuneMotion")
        val duneShift by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(30000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "DuneShift"
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            
            // 3. Astro-Dune Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                // LAYER 1: Celestial Body Trajectory
                val x = size.width * progress
                val arcHeight = size.height * 0.4f
                val y = size.height * 0.8f - (Math.sin(Math.PI * progress).toFloat() * arcHeight)
                
                if (isDay) {
                    // Golden Sun
                    drawCircle(
                        color = Color(0xFFF2C94C).copy(alpha = 0.8f),
                        radius = 35f,
                        center = Offset(x, y)
                    )
                } else {
                    // Creamy Crescent Moon
                    drawCircle(
                        color = Color(0xFFF7F4EF).copy(alpha = 0.8f),
                        radius = 28f,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = 28f,
                        center = Offset(x - 12f, y - 6f)
                    )
                    
                    // Twinkling Stars (15 random dots)
                    val random = java.util.Random(42)
                    for (i in 0..14) {
                        val starAlpha = 0.2f + random.nextFloat() * 0.4f
                        drawCircle(
                            color = Color.White.copy(alpha = starAlpha),
                            radius = 2.5f,
                            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height * 0.6f)
                        )
                    }
                }

                // LAYER 2: Foreground Sand Dunes (Over the sun/moon)
                val sandColor = Color(0xFFE5C29B).copy(alpha = 0.45f)
                val dune1 = Path().apply {
                    moveTo(0f, size.height)
                    quadraticTo(size.width * (0.3f + duneShift/500f), size.height * (0.6f + duneShift/1000f), size.width * 0.6f, size.height * 0.85f)
                    lineTo(size.width, size.height)
                    close()
                }
                val dune2 = Path().apply {
                    moveTo(size.width * 0.4f, size.height)
                    quadraticTo(size.width * (0.75f - duneShift/500f), size.height * (0.5f - duneShift/1000f), size.width, size.height * 0.8f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(dune1, color = sandColor)
                drawPath(dune2, color = sandColor)
            }

            // LAYER 3: Prayer Text Labels
            Row(
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val times = listOf(
                    "Dawn" to (prayerTimes?.dawn ?: "--:--"),
                    "Sunrise" to (prayerTimes?.sunrise ?: "--:--"),
                    "Noon" to (prayerTimes?.noon ?: "--:--"),
                    "Afternoon" to (prayerTimes?.afternoon ?: "--:--"),
                    "Sunset" to (prayerTimes?.sunset ?: "--:--"),
                    "Night" to (prayerTimes?.night ?: "--:--")
                )

                times.forEach { (label, time) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            label,
                            style = Typography.labelSmall,
                            color = if (isDay) secondaryTextColor else Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp,
                            modifier = Modifier.offset(pixelOffset.x, pixelOffset.y).alpha(textAlpha)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            time,
                            style = Typography.titleLarge,
                            fontFamily = FontFamily.Serif,
                            color = if (isDay) primaryTextColor else Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.offset(pixelOffset.x, pixelOffset.y).alpha(textAlpha)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FramedApodCard(
    apod: NasaApodEntity?, 
    isLoading: Boolean, 
    isError: Boolean, 
    pixelOffset: DpOffset,
    textAlpha: Float,
    surfaceColor: Color,
    borderColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ApodMotion")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MicroScale"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = surfaceColor,
        shape = Shapes.large,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) { // Removed padding to let image fill to corners
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp)
                }
            } else if (isError) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🚀", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "NASA limits reached.\nCome again tomorrow.",
                        style = Typography.labelSmall,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else if (apod != null) {
                AsyncImage(
                    model = apod.hdUrl ?: apod.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        if (state is coil.compose.AsyncImagePainter.State.Error) {
                            state.result.throwable.printStackTrace()
                        }
                    }
                )
                Text(
                    apod.title.uppercase(),
                    style = Typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f), // Softened
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(pixelOffset.x, pixelOffset.y)
                        .alpha(textAlpha)
                        .padding(12.dp) // Padded from edges but no band
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Digital Art Frame", style = Typography.labelSmall, color = secondaryTextColor)
                }
            }
        }
    }
}

@Composable
fun ElegantPhraseCard(
    phrase: PhraseEntity?, 
    pixelOffset: DpOffset,
    textAlpha: Float,
    surfaceColor: Color,
    borderColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = surfaceColor,
        shape = Shapes.large,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp), // Slightly tighter
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "DAILY LEXICON", 
                style = Typography.labelSmall, 
                color = secondaryTextColor,
                modifier = Modifier.offset(pixelOffset.x, pixelOffset.y).alpha(textAlpha)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                phrase?.en ?: "Loading perspective...",
                style = Typography.headlineSmall, // Smaller for compression
                lineHeight = 28.sp,
                fontFamily = FontFamily.Serif,
                color = primaryTextColor,
                modifier = Modifier.offset(pixelOffset.x, pixelOffset.y).alpha(textAlpha)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                phrase?.tr ?: "",
                style = Typography.bodyMedium,
                color = accentColor,
                fontFamily = FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.offset(pixelOffset.x, pixelOffset.y).alpha(textAlpha)
            )
        }
    }
}

@Composable
fun CompactClockArea(
    currentTime: Long,
    pixelOffset: DpOffset,
    textAlpha: Float,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.ENGLISH) }
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val dateFormat = remember { SimpleDateFormat("dd", Locale.ENGLISH) }
    
    Row(
        modifier = modifier
            .offset(pixelOffset.x, pixelOffset.y)
            .alpha(textAlpha),
        horizontalArrangement = Arrangement.End, // Changed to End for extreme close proximity
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeFormat.format(Date(currentTime)),
            style = Typography.headlineMedium,
            fontWeight = FontWeight.Light,
            fontSize = 76.sp, // Boşluğu domine edecek heybetli boyut
            lineHeight = 76.sp, // Dikey hizalamayı bozmaması için
            color = primaryTextColor
        )
        
        Spacer(modifier = Modifier.width(20.dp)) // Rakamlar büyüdüğü için aradaki nefes payı hafif açıldı

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = dayFormat.format(Date(currentTime)).uppercase(Locale.ENGLISH),
                style = Typography.labelSmall,
                color = secondaryTextColor,
                letterSpacing = 2.sp,
                fontSize = 16.sp // Gün ismi belirginleştirildi (14 -> 16)
            )
            Text(
                text = dateFormat.format(Date(currentTime)),
                style = Typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp, // Gün rakamı devasa saate uyum sağladı (28 -> 36)
                color = primaryTextColor
            )
        }
    }
}

@Composable
fun SleepOverlay(onWake: () -> Unit) {
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.ENGLISH) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM dd", Locale.ENGLISH) }

    // Anti-Burn-In loop for Sleep Mode
    val infiniteTransition = rememberInfiniteTransition(label = "SleepMotion")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse)
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onWake() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(offsetX.dp, offsetY.dp)
        ) {
            Text(
                timeFormat.format(Date(currentTime.longValue)),
                style = Typography.headlineLarge,
                fontSize = 100.sp,
                color = Color.White.copy(alpha = 0.3f),
                fontWeight = FontWeight.ExtraLight
            )
            Text(
                dateFormat.format(Date(currentTime.longValue)).uppercase(Locale.ENGLISH),
                style = Typography.labelSmall,
                color = Color.White.copy(alpha = 0.2f),
                letterSpacing = 4.sp
            )
        }
    }
}

data class WeatherDescription(val condition: String, val icon: String)

fun getWeatherDescription(code: Int, isDay: Boolean = true): WeatherDescription {
    return when (code) {
        0 -> if (isDay) WeatherDescription("Clear Sky", "☀️") else WeatherDescription("Clear Sky", "🌙")
        1, 2, 3 -> if (isDay) WeatherDescription("Partly Cloudy", "🌤️") else WeatherDescription("Partly Cloudy", "☁️")
        45, 48 -> WeatherDescription("Foggy", "🌫️")
        51, 53, 55 -> WeatherDescription("Drizzle", "🌦️")
        61, 63, 65 -> WeatherDescription("Rainy", "🌧️")
        71, 73, 75 -> WeatherDescription("Snowy", "❄️")
        80, 81, 82 -> WeatherDescription("Showers", "🌧️")
        95, 96, 99 -> WeatherDescription("Thunderstorm", "⛈️")
        else -> WeatherDescription("Cloudy", "☁️")
    }
}
