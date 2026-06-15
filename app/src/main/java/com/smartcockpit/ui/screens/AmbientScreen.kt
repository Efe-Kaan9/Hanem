package com.smartcockpit.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcockpit.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun AmbientScreen(
    onExit: () -> Unit,
    viewModel: AmbientViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val images by viewModel.images.collectAsState()
    val currentImageIndex by viewModel.currentImageIndex.collectAsState()
    val weather by dashboardViewModel.weather.collectAsState(initial = null)
    val prayerTimes by dashboardViewModel.prayerTimes.collectAsState(initial = null)
    
    var showGalleryManagement by remember { mutableStateOf(false) }

    // 1. TOP-LEVEL SYSTEM CLOCK TICKER
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    // 2. HOISTED DAY/NIGHT STATE
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

    val launcher = rememberLauncherForActivityResult(
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

        // Overlay to exit or manage
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
                onAddImage = { launcher.launch("image/*") },
                onRemoveImage = viewModel::removeImage,
                onClose = { showGalleryManagement = false },
                onExitAmbient = onExit
            )
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
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
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

    Surface(
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
                        Surface(
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
            // Use time-aware icon for ambient
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
