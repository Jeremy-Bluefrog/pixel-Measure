package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logic.TranslationManager
import com.example.ui.components.BlurDirection
import com.example.ui.components.gradientBlurSource
import com.example.ui.components.hardwareBackdropBlur
import com.example.ui.components.progressiveBackdropBlur
import com.example.ui.components.progressiveGlass
import com.example.ui.components.rememberGradientBlurBackdropState
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.ExpressiveCookie8Shape
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.GoogleSansRounded
import com.example.ui.theme.LocalAppDarkTheme
import com.example.ui.theme.rememberExpressiveConnectedShape
import com.example.ui.viewmodel.HapticType
import com.example.ui.viewmodel.MeasureViewModel
import kotlinx.coroutines.launch

private data class WelcomeStepInfo(
    val stepIndex: Int,
    val eyebrow: String,
    val title: String,
    val description: String,
    val highlights: List<String>
)

@Composable
private fun rememberWelcomeSteps(): List<WelcomeStepInfo> {
    val lang = LocalAppLanguage.current
    return remember(lang) {
        listOf(
            WelcomeStepInfo(
                stepIndex = 0,
                eyebrow = TranslationManager.getString("wel_step1_eyebrow", lang),
                title = TranslationManager.getString("wel_step1_title", lang),
                description = TranslationManager.getString("wel_step1_desc", lang),
                highlights = listOf(
                    TranslationManager.getString("wel_step1_hl1", lang),
                    TranslationManager.getString("wel_step1_hl2", lang),
                    TranslationManager.getString("wel_step1_hl3", lang),
                    TranslationManager.getString("wel_step1_hl4", lang)
                )
            ),
            WelcomeStepInfo(
                stepIndex = 1,
                eyebrow = TranslationManager.getString("wel_step2_eyebrow", lang),
                title = TranslationManager.getString("wel_step2_title", lang),
                description = TranslationManager.getString("wel_step2_desc", lang),
                highlights = listOf(
                    TranslationManager.getString("wel_step2_hl1", lang),
                    TranslationManager.getString("wel_step2_hl2", lang),
                    TranslationManager.getString("wel_step2_hl3", lang),
                    TranslationManager.getString("wel_step2_hl4", lang)
                )
            ),
            WelcomeStepInfo(
                stepIndex = 2,
                eyebrow = TranslationManager.getString("wel_step3_eyebrow", lang),
                title = TranslationManager.getString("wel_step3_title", lang),
                description = TranslationManager.getString("wel_step3_desc", lang),
                highlights = listOf(
                    TranslationManager.getString("wel_step3_hl1", lang),
                    TranslationManager.getString("wel_step3_hl2", lang),
                    TranslationManager.getString("wel_step3_hl3", lang),
                    TranslationManager.getString("wel_step3_hl4", lang)
                )
            )
        )
    }
}

/**
 * Material 3 Expressive Redesigned First-Time User Onboarding Screen:
 * - 3-page interactive HorizontalPager with live animated showcases
 * - Morphing step pill indicators with 48dp accessible tap targets
 * - Clean Pixel consumer copy (GoogleSansRounded typography, zero technical clutter)
 * - Touch isolation preventing pointer pass-through to AR canvas underneath
 * - Connected asymmetric bottom CTA deck
 */
@Composable
fun WelcomeScreen(
    viewModel: MeasureViewModel,
    onStartMeasuring: (initialMode: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
        val welcomeSteps = rememberWelcomeSteps()
        val pagerState = rememberPagerState(pageCount = { welcomeSteps.size })
        val coroutineScope = rememberCoroutineScope()
        var dontShowAgain by remember { mutableStateOf(true) }

        val isDark = LocalAppDarkTheme.current
        val colorPrimary = MaterialTheme.colorScheme.primary
        val colorSecondary = MaterialTheme.colorScheme.secondary
        val colorTertiary = MaterialTheme.colorScheme.tertiary
        val colorSurface = MaterialTheme.colorScheme.surface
        val colorOnSurface = MaterialTheme.colorScheme.onSurface
        val colorOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        val welcomeBackdropState = rememberGradientBlurBackdropState()

    Surface(
        color = colorSurface,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .testTag("welcome_screen")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Ambient blurred Material You bokeh backdrop sampled by frosted glass cards
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .gradientBlurSource(welcomeBackdropState)
                    .background(colorSurface)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .hardwareBackdropBlur(enabled = true, blurRadius = 110f)
                ) {
                    drawCircle(
                        color = colorPrimary.copy(alpha = if (isDark) 0.24f else 0.16f),
                        radius = size.width * 0.56f,
                        center = Offset(size.width * 0.15f, size.height * 0.18f)
                    )
                    drawCircle(
                        color = colorTertiary.copy(alpha = if (isDark) 0.20f else 0.14f),
                        radius = size.width * 0.48f,
                        center = Offset(size.width * 0.88f, size.height * 0.44f)
                    )
                    drawCircle(
                        color = colorSecondary.copy(alpha = if (isDark) 0.18f else 0.11f),
                        radius = size.width * 0.52f,
                        center = Offset(size.width * 0.32f, size.height * 0.86f)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                val isExpanded = maxWidth > 600.dp
                val contentMaxWidth = if (isExpanded) 540.dp else maxWidth

                Column(
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top Brand Header + Skip Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = ExpressiveCookie8Shape,
                                color = colorPrimary,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Straighten,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = tr("brand_name"),
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = colorOnSurface
                                )
                                Text(
                                    text = tr("wel_header_title"),
                                    fontFamily = GoogleSansRounded,
                                    fontSize = 11.5.sp,
                                    color = colorOnSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                viewModel.triggerHapticFeedback(HapticType.CLICK)
                                viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                                onStartMeasuring(0)
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("welcome_skip_btn"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tr("wel_skip"),
                                fontFamily = GoogleSansRounded,
                                color = colorOnSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // 2. Interactive 3-Step HorizontalPager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("welcome_pager")
                    ) { page ->
                        val step = welcomeSteps[page]
                        val stepScroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(stepScroll)
                                .padding(top = 6.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (page) {
                                0 -> WelcomeArHeroShowcase(backdropState = welcomeBackdropState)
                                1 -> WelcomeCaptureShowcase(backdropState = welcomeBackdropState)
                                else -> WelcomeToolsShowcase(backdropState = welcomeBackdropState)
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            Surface(
                                shape = CircleShape,
                                color = colorPrimary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = step.eyebrow,
                                    fontFamily = GoogleSansRounded,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = step.title,
                                style = ExpressiveTypography.headlineEmphasized,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorOnSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded,
                                color = colorOnSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Expressive Highlight Chips Grid (2x2 clean symmetric tags)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                step.highlights.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { label ->
                                            val chipShape = RoundedCornerShape(16.dp)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(chipShape)
                                                    .progressiveGlass(
                                                        backdropState = welcomeBackdropState,
                                                        direction = BlurDirection.BOTTOM_TO_TOP,
                                                        maxBlurRadius = 16.dp,
                                                        tintColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                                            alpha = if (isDark) 0.62f else 0.74f
                                                        ),
                                                        shape = chipShape,
                                                        borderColor = if (isDark) {
                                                            Color.White.copy(alpha = 0.10f)
                                                        } else {
                                                            Color.White.copy(alpha = 0.55f)
                                                        }
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = colorPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = label,
                                                        fontFamily = GoogleSansRounded,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colorOnSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Morphing Step Indicator Pills (48dp accessible touch height)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(welcomeSteps.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            val pillWidth by animateDpAsState(
                                targetValue = if (isSelected) 28.dp else 9.dp,
                                animationSpec = ExpressiveMotion.spatialFast(),
                                label = "indicatorWidth_$index"
                            )
                            val pillColor by animateColorAsState(
                                targetValue = if (isSelected) colorPrimary else MaterialTheme.colorScheme.outlineVariant,
                                animationSpec = ExpressiveMotion.effectsDefault(),
                                label = "indicatorColor_$index"
                            )

                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                    .padding(horizontal = 6.dp)
                                    .testTag("welcome_indicator_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(pillWidth)
                                        .height(9.dp)
                                        .clip(CircleShape)
                                        .background(pillColor)
                                )
                            }
                        }
                    }

                    // 4. Bottom Don't-Show-Again + Frosted Progressive Glass CTA Deck
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .progressiveBackdropBlur(
                                backdropState = welcomeBackdropState,
                                direction = BlurDirection.TOP_TO_BOTTOM,
                                maxBlurRadius = 22.dp,
                                startFraction = 0.0f,
                                endFraction = 0.65f,
                                tintBrush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colorSurface.copy(alpha = 0.55f)
                                    )
                                )
                            )
                            .padding(bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    dontShowAgain = !dontShowAgain
                                    viewModel.triggerHapticFeedback(HapticType.CLICK)
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = {
                                    dontShowAgain = it
                                    viewModel.triggerHapticFeedback(HapticType.CLICK)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = colorPrimary)
                            )
                            Text(
                                text = tr("wel_dont_show_again"),
                                fontFamily = GoogleSansRounded,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorOnSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val isLastPage = pagerState.currentPage == welcomeSteps.lastIndex

                        AnimatedContent(
                            targetState = isLastPage,
                            transitionSpec = {
                                fadeIn(ExpressiveMotion.effectsFast()) togetherWith fadeOut(ExpressiveMotion.effectsFast())
                            },
                            label = "welcomeBottomActionsTransition"
                        ) { finalStep ->
                            if (finalStep) {
                                // Final Step: Connected Asymmetric Dual Launch Bar (Ruler | Start AR)
                                WelcomeFinalActionRow(
                                    onOpenRuler = {
                                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                                        onStartMeasuring(1)
                                    },
                                    onStartAr = {
                                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        viewModel.dismissWelcomeScreen(neverShowAgain = dontShowAgain)
                                        onStartMeasuring(0)
                                    }
                                )
                            } else {
                                // Steps 1–2: Previous (if step > 0) + Next Step CTA
                                WelcomePagerStepRow(
                                    canGoBack = pagerState.currentPage > 0,
                                    onPrev = {
                                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                        }
                                    },
                                    onNext = {
                                        viewModel.triggerHapticFeedback(HapticType.CLICK)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(welcomeSteps.lastIndex))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun WelcomePagerStepRow(
    canGoBack: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            val prevInteraction = remember { MutableInteractionSource() }
            val isPrevPressed by prevInteraction.collectIsPressedAsState()
            val prevShape = rememberExpressiveConnectedShape(
                index = 0,
                count = 2,
                isActive = false,
                isPressed = isPrevPressed,
                outerCorner = 24.dp,
                innerRestingCorner = 8.dp,
                innerActiveCorner = 18.dp
            )
            FilledTonalButton(
                onClick = onPrev,
                interactionSource = prevInteraction,
                shape = prevShape,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("welcome_prev_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("prev_step"), fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        val nextInteraction = remember { MutableInteractionSource() }
        val isNextPressed by nextInteraction.collectIsPressedAsState()
        val nextShape = if (canGoBack) {
            rememberExpressiveConnectedShape(
                index = 1,
                count = 2,
                isActive = true,
                isPressed = isNextPressed,
                outerCorner = 24.dp,
                innerRestingCorner = 8.dp,
                innerActiveCorner = 18.dp
            )
        } else {
            RoundedCornerShape(if (isNextPressed) 16.dp else 26.dp)
        }

        Button(
            onClick = onNext,
            interactionSource = nextInteraction,
            shape = nextShape,
            modifier = Modifier
                .weight(if (canGoBack) 1.4f else 1f)
                .height(54.dp)
                .testTag("welcome_next_btn")
        ) {
            Text(
                text = if (canGoBack) tr("next_step") else tr("wel_next_discover"),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WelcomeFinalActionRow(
    onOpenRuler: () -> Unit,
    onStartAr: () -> Unit
) {
    val rulerInteraction = remember { MutableInteractionSource() }
    val isRulerPressed by rulerInteraction.collectIsPressedAsState()
    val rulerShape = rememberExpressiveConnectedShape(
        index = 0,
        count = 2,
        isActive = false,
        isPressed = isRulerPressed,
        outerCorner = 24.dp,
        innerRestingCorner = 8.dp,
        innerActiveCorner = 18.dp
    )
    val rulerScale by animateFloatAsState(
        targetValue = if (isRulerPressed) 0.97f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "welcomeRulerScale"
    )

    val startInteraction = remember { MutableInteractionSource() }
    val isStartPressed by startInteraction.collectIsPressedAsState()
    val startShape = rememberExpressiveConnectedShape(
        index = 1,
        count = 2,
        isActive = true,
        isPressed = isStartPressed,
        outerCorner = 24.dp,
        innerRestingCorner = 8.dp,
        innerActiveCorner = 18.dp
    )
    val startScale by animateFloatAsState(
        targetValue = if (isStartPressed) 0.97f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "welcomeStartScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalButton(
            onClick = onOpenRuler,
            interactionSource = rulerInteraction,
            shape = rulerShape,
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .graphicsLayer {
                    scaleX = rulerScale
                    scaleY = rulerScale
                }
                .testTag("welcome_btn_ruler")
        ) {
            Icon(
                Icons.Rounded.Straighten,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tr("wel_btn_ruler"),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Button(
            onClick = onStartAr,
            interactionSource = startInteraction,
            shape = startShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .weight(1.35f)
                .height(54.dp)
                .graphicsLayer {
                    scaleX = startScale
                    scaleY = startScale
                }
                .testTag("welcome_btn_start_ar")
        ) {
            Icon(
                Icons.Rounded.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tr("wel_btn_start_ar"),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
