@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smartcockpit.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcockpit.R
import com.smartcockpit.ui.theme.AccentColor
import com.smartcockpit.ui.theme.Typography
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// GESTURE ENUM
// ─────────────────────────────────────────────────────────────────────────────

enum class OnboardingGesture { NONE, SWIPE_LEFT, TAP_CENTER, TAP_SETTINGS_ICON, SWIPE_UP }

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODEL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a single onboarding slide.
 * To swap placeholder images, replace [imageRes] with the actual drawable resource ID
 * (e.g. R.drawable.img_page1) for each page in [tutorialPages].
 */
data class TutorialPage(
    val imageRes: Int,
    val title: String,
    val description: String,
    val gesture: OnboardingGesture = OnboardingGesture.NONE
)

// ─────────────────────────────────────────────────────────────────────────────
// PAGE DEFINITIONS  —  imageRes placeholders, swap when real mockups are ready
// ─────────────────────────────────────────────────────────────────────────────

private val tutorialPages = listOf(
    TutorialPage(
        imageRes    = R.drawable.img2,
        title       = "Smart Dashboard",
        description = "Daily NASA imagery, C1 English phrases, and real-time weather at a glance. Swipe left to enter Ambient Mode",
        gesture     = OnboardingGesture.SWIPE_LEFT
    ),
    TutorialPage(
        imageRes    = R.drawable.img3,
        title       = "Ambient Gallery",
        description = "Tap the screen to manage your personal photo gallery and settings.",
        gesture     = OnboardingGesture.TAP_CENTER
    ),
    TutorialPage(
        imageRes    = R.drawable.img4,
        title       = "System Settings",
        description = "Tap the gear icon in Ambient Mode to configure Wake/Sleep schedules and GPS. Click on exit button to return to main page.",
        gesture     = OnboardingGesture.TAP_SETTINGS_ICON
    ),
    TutorialPage(
        imageRes    = R.drawable.img5,
        title       = "System configuration",
        description = "Configure the system to adapt to you, enter your Nasa api key which you can take from https://api.nasa.gov/ for free for uninterrupted access.",
        gesture     = OnboardingGesture.NONE
    ),
    TutorialPage(
        imageRes    = R.drawable.img6,
        title       = "OLED Sleep Mode",
        description = "Swipe up from the dashboard to enter deep sleep. Recommended to prevent pixel burn-in.",
        gesture     = OnboardingGesture.SWIPE_UP
    ),
    TutorialPage(
        imageRes    = R.drawable.img7,
        title       = "Exit OLED Sleep Mode",
        description = "Tap the screen to exit sleep mode.",
        gesture     = OnboardingGesture.TAP_CENTER
    ),
    TutorialPage(
        imageRes    = R.drawable.img_onboarding_placeholder,
        title       = "Critical Kiosk Setup",
        description = "1. Grant 'Display over other apps' permission in Android Settings for auto-wake.\n2. Use a smart plug schedule (e.g., 1h ON / 3h OFF) to keep battery between 20-80% and prevent swelling.",
        gesture     = OnboardingGesture.NONE
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// ROOT COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tutorialPages.size })
    val isLastPage = pagerState.currentPage == tutorialPages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C12)) // Deep space black canvas
    ) {
        // ── PAGER ────────────────────────────────────────────────────────────
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            OnboardingPage(
                page     = tutorialPages[pageIndex],
                isActive = pageIndex == pagerState.currentPage
            )
        }

        // ── BOTTOM CHROME (dot indicator + button) ────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF080C12))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
                .padding(bottom = 40.dp, top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Dot Indicator
            DotIndicator(
                totalPages  = tutorialPages.size,
                currentPage = pagerState.currentPage
            )

            // Navigation Button
            Button(
                onClick = {
                    if (isLastPage) {
                        // Persist the flag, then navigate — the NavHost recomposes instantly
                        settingsViewModel.completeTutorial()
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor   = Color(0xFF1A1009)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text          = if (isLastPage) "Get Started" else "Next",
                    style         = Typography.bodyLarge,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SINGLE PAGE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPage(
    page: TutorialPage,
    isActive: Boolean
) {
    // ── Infinite transition for gesture animations ────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "GestureAnim")

    // Swipe translation: -10f → 10f, smooth back-and-forth
    val translationOffset by infiniteTransition.animateFloat(
        initialValue   = -10f,
        targetValue    = 10f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TranslationOffset"
    )

    // Tap scale pulse: 1f → 1.1f
    val scalePulse by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScalePulse"
    )

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── TOP 65%: image + gesture overlay ─────────────────────────────────
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .statusBarsPadding()
                .padding(top = 24.dp, start = 28.dp, end = 28.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier        = Modifier.fillMaxSize(),
                color           = Color(0xFF0D1117),
                shape           = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp
            ) {
                // Stack the mockup image and the gesture overlay inside a Box
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter            = painterResource(id = page.imageRes),
                        contentDescription = page.title,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    )

                    // ── Gesture overlay ───────────────────────────────────────
                    if (page.gesture != OnboardingGesture.NONE) {
                        GestureOverlay(
                            gesture          = page.gesture,
                            translationOffset = translationOffset,
                            scalePulse       = scalePulse,
                            modifier         = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        // ── BOTTOM 35%: text content ──────────────────────────────────────────
        val contentAlpha by animateFloatAsState(
            targetValue   = if (isActive) 1f else 0f,
            animationSpec = tween(durationMillis = 350, delayMillis = 80),
            label         = "PageContentAlpha"
        )
        val contentSlide by animateDpAsState(
            targetValue   = if (isActive) 0.dp else 16.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label         = "PageContentSlide"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .padding(top = 24.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Accent pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(AccentColor.copy(alpha = 0.6f))
            )

            Text(
                text          = page.title,
                style         = Typography.headlineMedium,
                fontWeight    = FontWeight.SemiBold,
                color         = Color.White,
                textAlign     = TextAlign.Center,
                letterSpacing = 0.3.sp
            )
            Text(
                text       = page.description,
                style      = Typography.bodyMedium,
                color      = Color.White.copy(alpha = 0.55f),
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GESTURE OVERLAY
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders the animated gesture hint centered over the mockup image.
 * A semi-transparent dark scrim pill behind the icon+label ensures readability
 * against both bright and dark areas of any mockup image.
 */
@Composable
private fun GestureOverlay(
    gesture: OnboardingGesture,
    translationOffset: Float,
    scalePulse: Float,
    modifier: Modifier = Modifier
) {
    val iconColor  = Color.White.copy(alpha = 0.85f)
    val labelColor = Color.White.copy(alpha = 0.85f)
    val scrimColor = Color.Black.copy(alpha = 0.35f)

    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scrimColor)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (gesture) {
                OnboardingGesture.SWIPE_LEFT -> {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Swipe Left",
                        tint               = iconColor,
                        modifier           = Modifier
                            .size(64.dp)
                            .offset { IntOffset(translationOffset.roundToInt(), 0) }
                            .graphicsLayer {
                                shadowElevation = 8.dp.toPx()
                            }
                    )
                    Text(
                        text      = "SWIPE LEFT",
                        color     = labelColor,
                        fontSize  = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier  = Modifier.graphicsLayer { shadowElevation = 8.dp.toPx() }
                    )
                }

                OnboardingGesture.TAP_CENTER -> {
                    Icon(
                        imageVector        = Icons.Default.TouchApp,
                        contentDescription = "Tap",
                        tint               = iconColor,
                        modifier           = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX          = scalePulse
                                scaleY          = scalePulse
                                shadowElevation = 8.dp.toPx()
                            }
                    )
                    Text(
                        text          = "TAP TO INTERACT",
                        color         = labelColor,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier      = Modifier.graphicsLayer { shadowElevation = 8.dp.toPx() }
                    )
                }

                OnboardingGesture.TAP_SETTINGS_ICON -> {
                    Icon(
                        imageVector        = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint               = iconColor,
                        modifier           = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX          = scalePulse
                                scaleY          = scalePulse
                                shadowElevation = 8.dp.toPx()
                            }
                    )
                    Text(
                        text          = "TAP THE GEAR ICON TO CONFIGURATE",
                        color         = labelColor,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier      = Modifier.graphicsLayer { shadowElevation = 8.dp.toPx() }
                    )
                }

                OnboardingGesture.SWIPE_UP -> {
                    Icon(
                        imageVector        = Icons.Default.ArrowUpward,
                        contentDescription = "Swipe Up",
                        tint               = iconColor,
                        modifier           = Modifier
                            .size(64.dp)
                            .offset { IntOffset(0, translationOffset.roundToInt()) }
                            .graphicsLayer {
                                shadowElevation = 8.dp.toPx()
                            }
                    )
                    Text(
                        text          = "SWIPE UP",
                        color         = labelColor,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier      = Modifier.graphicsLayer { shadowElevation = 8.dp.toPx() }
                    )
                }

                OnboardingGesture.NONE -> { /* no overlay */ }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DOT INDICATOR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DotIndicator(
    totalPages: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isSelected = index == currentPage
            val dotWidth by animateDpAsState(
                targetValue   = if (isSelected) 24.dp else 6.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ),
                label = "DotWidth"
            )
            val dotAlpha by animateFloatAsState(
                targetValue   = if (isSelected) 1f else 0.3f,
                animationSpec = tween(200),
                label         = "DotAlpha"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(AccentColor.copy(alpha = dotAlpha))
            )
        }
    }
}
