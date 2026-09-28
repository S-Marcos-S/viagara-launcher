// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.EdgeSide
import dev.victorialauncher.data.ThemedIconStyle
import dev.victorialauncher.service.HapticUtil
import dev.victorialauncher.ui.common.ThemedAppIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Modern dynamic action button placed next to the alphabet at the bottom of the home screen.
 *
 * Supports three gestures:
 * 1. Tap: Opens [clickApp] (or settings if unassigned) with elastic press/release bounce.
 * 2. Swipe Up: Opens [swipeUpApp] with realistic rubber-band stretch and spring return.
 * 3. Swipe Down: Opens [swipeDownApp] with rubber-band stretch and spring return.
 * 4. Long press: Opens dynamic button settings to customize actions.
 */
@Composable
fun DynamicActionButton(
    clickApp: AppInfo?,
    swipeUpApp: AppInfo?,
    swipeDownApp: AppInfo?,
    side: EdgeSide,
    hapticsEnabled: Boolean,
    contentColor: Color,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    isInteractiveDemo: Boolean = false,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current

    val buttonSizeDp = 54.dp
    val maxStretchPx = with(density) { 80.dp.toPx() }
    val thresholdPx = with(density) { 38.dp.toPx() }

    // Physical rubber-band state holders
    val dragOffsetY = remember { Animatable(0f) }
    val stretchScaleX = remember { Animatable(1f) }
    val stretchScaleY = remember { Animatable(1f) }
    val pressScale = remember { Animatable(1f) }

    var isDragging by remember { mutableStateOf(false) }
    var thresholdCrossed by remember { mutableStateOf(false) }
    var currentGesture by remember { mutableStateOf<DynamicGesture>(DynamicGesture.NONE) }
    var buttonBounds by remember { mutableStateOf<Rect?>(null) }

    val transformOrigin = remember(dragOffsetY.value) {
        if (dragOffsetY.value < 0f) {
            TransformOrigin(0.5f, 0.92f) // Ancorado na base ao esticar para cima
        } else if (dragOffsetY.value > 0f) {
            TransformOrigin(0.5f, 0.08f) // Ancorado no topo ao esticar para baixo
        } else {
            TransformOrigin.Center
        }
    }

    // Spring specs for rubber release bounce
    val rubberSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
    }

    val pressSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    fun releaseRubber(fireAction: Boolean) {
        val gesture = currentGesture
        val crossed = thresholdCrossed

        scope.launch {
            // Coordinate rubber bounce return
            launch { dragOffsetY.animateTo(0f, rubberSpring) }
            launch { stretchScaleX.animateTo(1f, rubberSpring) }
            launch { stretchScaleY.animateTo(1f, rubberSpring) }
            launch { pressScale.animateTo(1f, pressSpring) }
        }

        if (fireAction && crossed) {
            HapticUtil.tick(view, hapticsEnabled)
            if (!isInteractiveDemo) {
                when (gesture) {
                    DynamicGesture.SWIPE_UP -> {
                        if (swipeUpApp != null) onLaunch(swipeUpApp, buttonBounds) else onOpenSettings()
                    }
                    DynamicGesture.SWIPE_DOWN -> {
                        if (swipeDownApp != null) onLaunch(swipeDownApp, buttonBounds) else onOpenSettings()
                    }
                    else -> Unit
                }
            }
        }

        isDragging = false
        thresholdCrossed = false
        currentGesture = DynamicGesture.NONE
    }

    // Outer container has fixed size to guarantee zero horizontal shift when drag starts/ends
    Box(
        modifier = modifier
            .size(buttonSizeDp)
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    val b = coords.boundsInWindow()
                    val r = Rect(
                        b.left.toInt(),
                        b.top.toInt(),
                        b.right.toInt(),
                        b.bottom.toInt(),
                    )
                    buttonBounds = r
                    clickApp?.let { dev.victorialauncher.ui.transition.AppLaunchTransitionManager.updateIconBounds(it.packageName, r) }
                    swipeUpApp?.let { dev.victorialauncher.ui.transition.AppLaunchTransitionManager.updateIconBounds(it.packageName, r) }
                    swipeDownApp?.let { dev.victorialauncher.ui.transition.AppLaunchTransitionManager.updateIconBounds(it.packageName, r) }
                }
            }
            .pointerInput(clickApp, swipeUpApp, swipeDownApp, hapticsEnabled) {
                val touchSlop = viewConfig.touchSlop.toFloat()
                awaitEachGesture {
                    val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    var dragged = false
                    var longPressTriggered = false
                    thresholdCrossed = false
                    currentGesture = DynamicGesture.NONE

                    // Animação de compressão elástica ao tocar
                    scope.launch {
                        pressScale.animateTo(0.88f, pressSpring)
                    }

                    // Long press detector job
                    var longPressJob: Job? = scope.launch {
                        delay(viewConfig.longPressTimeoutMillis)
                        if (!dragged) {
                            longPressTriggered = true
                            HapticUtil.tick(view, hapticsEnabled)
                            onOpenSettings()
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val deltaY = change.position.y - down.position.y

                        if (!dragged && abs(deltaY) > touchSlop) {
                            dragged = true
                            isDragging = true
                            longPressJob?.cancel()
                            longPressJob = null
                        }

                        if (dragged) {
                            change.consume()
                            // Resistência hiperbólica real de borracha (tanh dampening)
                            val dampedY = sign(deltaY) * maxStretchPx * tanh(abs(deltaY) / (maxStretchPx * 1.35f))
                            val ratio = (abs(dampedY) / maxStretchPx).coerceIn(0f, 1f)

                            // Deformação Squash & Stretch de volume conservado
                            val sy = 1.0f + 0.38f * ratio
                            val sx = 1.0f - 0.16f * ratio

                            val gesture = if (dampedY < -8f) {
                                DynamicGesture.SWIPE_UP
                            } else if (dampedY > 8f) {
                                DynamicGesture.SWIPE_DOWN
                            } else {
                                DynamicGesture.NONE
                            }
                            currentGesture = gesture

                            val reachedThreshold = abs(dampedY) >= thresholdPx
                            if (reachedThreshold && !thresholdCrossed) {
                                thresholdCrossed = true
                                HapticUtil.tick(view, hapticsEnabled)
                            } else if (!reachedThreshold && thresholdCrossed) {
                                thresholdCrossed = false
                            }

                            scope.launch {
                                dragOffsetY.snapTo(dampedY)
                                stretchScaleY.snapTo(sy)
                                stretchScaleX.snapTo(sx)
                            }
                        }
                    }

                    longPressJob?.cancel()
                    longPressJob = null

                    if (!dragged && !longPressTriggered) {
                        // Tap / Click rápido
                        HapticUtil.tick(view, hapticsEnabled)
                        scope.launch {
                            pressScale.animateTo(1.08f, pressSpring)
                            pressScale.animateTo(1.0f, pressSpring)
                        }
                        if (clickApp != null) {
                            onLaunch(clickApp, buttonBounds)
                        } else if (!isInteractiveDemo) {
                            onOpenSettings()
                        }
                        isDragging = false
                        thresholdCrossed = false
                        currentGesture = DynamicGesture.NONE
                    } else if (dragged) {
                        releaseRubber(fireAction = true)
                    } else {
                        // Long press já cuidou
                        scope.launch { pressScale.animateTo(1.0f, pressSpring) }
                        isDragging = false
                        thresholdCrossed = false
                        currentGesture = DynamicGesture.NONE
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Indicador flutuante para Swipe Up (arraste para cima)
        val showUpBubble = isDragging && dragOffsetY.value < -10f
        val upBubbleAlpha = (abs(dragOffsetY.value) / thresholdPx).coerceIn(0f, 1f)
        val upThresholdMet = thresholdCrossed && currentGesture == DynamicGesture.SWIPE_UP

        if (showUpBubble) {
            Box(
                modifier = Modifier
                    .wrapContentSize(align = Alignment.Center, unbounded = true)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (dragOffsetY.value - with(density) { 52.dp.toPx() }).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        alpha = upBubbleAlpha
                        scaleX = 0.8f + 0.2f * upBubbleAlpha
                        scaleY = 0.8f + 0.2f * upBubbleAlpha
                    },
            ) {
                ActionTeaserBubble(
                    app = swipeUpApp,
                    isThresholdMet = upThresholdMet,
                    contentColor = contentColor,
                    fallbackLabel = stringResource(R.string.dynamic_button_swipe_up_hint),
                )
            }
        }

        // Indicador flutuante para Swipe Down (arraste para baixo)
        val showDownBubble = isDragging && dragOffsetY.value > 10f
        val downBubbleAlpha = (abs(dragOffsetY.value) / thresholdPx).coerceIn(0f, 1f)
        val downThresholdMet = thresholdCrossed && currentGesture == DynamicGesture.SWIPE_DOWN

        if (showDownBubble) {
            Box(
                modifier = Modifier
                    .wrapContentSize(align = Alignment.Center, unbounded = true)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (dragOffsetY.value + with(density) { 52.dp.toPx() }).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        alpha = downBubbleAlpha
                        scaleX = 0.8f + 0.2f * downBubbleAlpha
                        scaleY = 0.8f + 0.2f * downBubbleAlpha
                    },
            ) {
                ActionTeaserBubble(
                    app = swipeDownApp,
                    isThresholdMet = downThresholdMet,
                    contentColor = contentColor,
                    fallbackLabel = stringResource(R.string.dynamic_button_swipe_down_hint),
                )
            }
        }

        // Botão Dinâmico Principal com Squash & Stretch e física elástica
        val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
        val activeBorderColor = if (thresholdCrossed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        }

        val activeTarget = when {
            isDragging && currentGesture == DynamicGesture.SWIPE_UP -> ActiveButtonTarget.SWIPE_UP
            isDragging && currentGesture == DynamicGesture.SWIPE_DOWN -> ActiveButtonTarget.SWIPE_DOWN
            else -> ActiveButtonTarget.CLICK
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = dragOffsetY.value
                    scaleX = stretchScaleX.value * pressScale.value
                    scaleY = stretchScaleY.value * pressScale.value
                    this.transformOrigin = transformOrigin
                }
                .size(buttonSizeDp)
                .shadow(
                    elevation = if (thresholdCrossed) 12.dp else 6.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = if (thresholdCrossed) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.35f),
                )
                .clip(RoundedCornerShape(22.dp))
                .background(surfaceColor)
                .border(
                    BorderStroke(
                        width = if (thresholdCrossed) 2.dp else 1.dp,
                        color = activeBorderColor,
                    ),
                    shape = RoundedCornerShape(22.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = activeTarget,
                transitionSpec = {
                    val isMovingTowardsUp = (targetState == ActiveButtonTarget.SWIPE_UP) ||
                            (initialState == ActiveButtonTarget.SWIPE_DOWN && targetState == ActiveButtonTarget.CLICK)

                    val animSpring = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                    val fadeSpring = spring<Float>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )

                    if (isMovingTowardsUp) {
                        (slideInVertically(animationSpec = animSpring) { height -> -height } + fadeIn(animationSpec = fadeSpring))
                            .togetherWith(
                                slideOutVertically(animationSpec = animSpring) { height -> height } + fadeOut(animationSpec = fadeSpring)
                            )
                    } else {
                        (slideInVertically(animationSpec = animSpring) { height -> height } + fadeIn(animationSpec = fadeSpring))
                            .togetherWith(
                                slideOutVertically(animationSpec = animSpring) { height -> -height } + fadeOut(animationSpec = fadeSpring)
                            )
                    }
                },
                label = "DynamicButtonIconTransition",
                contentAlignment = Alignment.Center,
            ) { target ->
                when (target) {
                    ActiveButtonTarget.CLICK -> {
                        if (clickApp != null) {
                            ThemedAppIcon(
                                app = clickApp,
                                sizeDp = 28,
                                style = ThemedIconStyle.MINIMALIST,
                                tintColor = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.TouchApp,
                                contentDescription = "Dynamic Button",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    ActiveButtonTarget.SWIPE_UP -> {
                        if (swipeUpApp != null) {
                            ThemedAppIcon(
                                app = swipeUpApp,
                                sizeDp = 28,
                                style = ThemedIconStyle.MINIMALIST,
                                tintColor = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Swipe Up Action",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    ActiveButtonTarget.SWIPE_DOWN -> {
                        if (swipeDownApp != null) {
                            ThemedAppIcon(
                                app = swipeDownApp,
                                sizeDp = 28,
                                style = ThemedIconStyle.MINIMALIST,
                                tintColor = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Swipe Down Action",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Balão flutuante elegante que exibe com clareza o nome do aplicativo de destino do gesto.
 */
@Composable
private fun ActionTeaserBubble(
    app: AppInfo?,
    isThresholdMet: Boolean,
    contentColor: Color,
    fallbackLabel: String,
) {
    val bubbleColor = if (isThresholdMet) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
    }

    val bubbleBorderColor = if (isThresholdMet) {
        MaterialTheme.colorScheme.primary
    } else {
        contentColor.copy(alpha = 0.20f)
    }

    Surface(
        shape = CircleShape,
        color = bubbleColor,
        border = BorderStroke(if (isThresholdMet) 1.5.dp else 1.dp, bubbleBorderColor),
        shadowElevation = if (isThresholdMet) 8.dp else 3.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app?.label ?: fallbackLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isThresholdMet) FontWeight.Bold else FontWeight.Medium,
                color = if (isThresholdMet) MaterialTheme.colorScheme.onPrimaryContainer else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class DynamicGesture {
    NONE,
    SWIPE_UP,
    SWIPE_DOWN,
}

private enum class ActiveButtonTarget {
    CLICK,
    SWIPE_UP,
    SWIPE_DOWN,
}
