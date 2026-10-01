// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.media

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import dev.viagaralauncher.R
import androidx.compose.ui.res.stringResource

fun isListenerEnabled(context: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/**
 * Brings up whatever is playing, and reports whether anything could be.
 *
 * The launcher intent goes first even though a session may nominate its own activity, which
 * would land on the player's own screen. Sending that PendingIntent starts an activity on
 * behalf of an app that is in the background, which Android drops on sight from 12 onwards
 * unless the sender explicitly grants the privilege — and `send()` reports success either
 * way, so there is no way to notice it went nowhere and fall back. Starting the intent
 * ourselves has no such problem: we are the foreground app. In practice the launcher intent
 * resumes the player's existing task anyway, which is the same screen.
 */
fun openNowPlayingApp(context: Context): Boolean {
    val controller = NowPlayingBus.state.value?.controller ?: return false

    context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let { launch ->
        if (runCatching {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        ) {
            return true
        }
    }

    // Nothing launchable — a background service publishing a session, say. The session's own
    // activity is all that is left, so it is worth a try even knowing it may be dropped.
    val sessionActivity = controller.sessionActivity ?: return false
    return runCatching { sessionActivity.send() }.isSuccess
}

@Composable
fun NowPlayingWidget(
    heightDp: Int = 64,
    iconSizeDp: Int = 48,
    labelSizeSp: Int = 14,
    contentColor: Color = Color.White,
    alignRight: Boolean = false,
    editMode: Boolean = false,
    onDismissPermissionPrompt: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }

    // The permission is granted in a separate system Settings screen, so re-check whenever
    // this screen comes back into the foreground instead of only once at first composition.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!listenerEnabled && !editMode) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = heightDp.dp),
            color = contentColor.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (alignRight) {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.now_playing_prompt_grant),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (onDismissPermissionPrompt != null) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = onDismissPermissionPrompt,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.now_playing_prompt_deny),
                                color = contentColor.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.now_playing_prompt_title),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(contentColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(contentColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.now_playing_prompt_title),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    if (onDismissPermissionPrompt != null) {
                        TextButton(
                            onClick = onDismissPermissionPrompt,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.now_playing_prompt_deny),
                                color = contentColor.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.now_playing_prompt_grant),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        return
    }

    // Null unless something is actually playing, paused or buffering — see the listener
    // service. In edit mode, show a placeholder if nothing is currently playing.
    val nowPlaying by NowPlayingBus.state.collectAsState()
    val current = nowPlaying

    // Artwork size scales with card height, title and artist text scale with app label settings
    val artSize = (heightDp * 0.52f).coerceIn(28f, iconSizeDp.toFloat()).dp
    val titleSp = labelSizeSp.sp
    val artistSp = (labelSizeSp - 2).coerceAtLeast(10).sp
    val controlSize = (heightDp * 0.38f).coerceIn(18f, 48f).dp

    if (current == null) {
        if (!editMode) return
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = heightDp.dp),
            color = contentColor.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (alignRight) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((heightDp * 0.06f).coerceIn(4f, 16f).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TransportButton(Icons.Filled.SkipPrevious, stringResource(R.string.now_playing_previous), controlSize, contentColor) {}
                            TransportButton(Icons.Filled.PlayArrow, stringResource(R.string.now_playing_play_pause), controlSize, contentColor) {}
                            TransportButton(Icons.Filled.SkipNext, stringResource(R.string.now_playing_next), controlSize, contentColor) {}
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                stringResource(R.string.settings_section_now_playing),
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = titleSp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                            Text(
                                stringResource(R.string.settings_now_playing_show),
                                color = contentColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = artistSp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Box(
                            modifier = Modifier.size(artSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(artSize * 0.75f),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(artSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(artSize * 0.75f),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_section_now_playing),
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = titleSp,
                            )
                            Text(
                                stringResource(R.string.settings_now_playing_show),
                                color = contentColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = artistSp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((heightDp * 0.06f).coerceIn(4f, 16f).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TransportButton(Icons.Filled.SkipPrevious, stringResource(R.string.now_playing_previous), controlSize, contentColor) {}
                            TransportButton(Icons.Filled.PlayArrow, stringResource(R.string.now_playing_play_pause), controlSize, contentColor) {}
                            TransportButton(Icons.Filled.SkipNext, stringResource(R.string.now_playing_next), controlSize, contentColor) {}
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                MaterialExpressiveWavyProgressIndicator(
                    progress = 0.45f,
                    isPlaying = true,
                    isIndeterminate = false,
                    color = contentColor.copy(alpha = 0.85f),
                    trackColor = contentColor.copy(alpha = 0.20f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                )
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val dismissX = remember(current.controller.sessionToken) { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    val liveDuration = current.controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: current.durationMs

    val initialPosition = remember(current.controller.sessionToken, current.positionMs, current.lastUpdateTime) {
        val liveState = current.controller.playbackState
        val basePosition = liveState?.position ?: current.positionMs
        val baseUpdateTime = liveState?.lastPositionUpdateTime?.takeIf { it > 0L } ?: current.lastUpdateTime
        val speed = liveState?.playbackSpeed?.takeIf { it > 0f } ?: current.speed
        if (current.isPlaying && baseUpdateTime > 0L) {
            val now = if (baseUpdateTime > 1_000_000_000_000L) System.currentTimeMillis() else SystemClock.elapsedRealtime()
            val elapsed = (now - baseUpdateTime).coerceAtLeast(0L)
            (basePosition + (elapsed * speed).toLong()).coerceIn(0L, if (liveDuration > 0L) liveDuration else Long.MAX_VALUE)
        } else {
            basePosition
        }
    }

    var currentPosition by remember(current.controller.sessionToken, current.positionMs, current.lastUpdateTime) {
        mutableStateOf(initialPosition)
    }

    // Advance position locally while playing
    LaunchedEffect(current.isPlaying, current.positionMs, current.lastUpdateTime, current.speed, liveDuration) {
        if (!current.isPlaying || liveDuration <= 0L) return@LaunchedEffect
        while (true) {
            val liveState = current.controller.playbackState
            val basePosition = liveState?.position ?: current.positionMs
            val baseUpdateTime = liveState?.lastPositionUpdateTime?.takeIf { it > 0L } ?: current.lastUpdateTime
            val speed = liveState?.playbackSpeed?.takeIf { it > 0f } ?: current.speed

            val now = if (baseUpdateTime > 1_000_000_000_000L) System.currentTimeMillis() else SystemClock.elapsedRealtime()
            val elapsed = (now - baseUpdateTime).coerceAtLeast(0L)
            val calculated = basePosition + (elapsed * speed).toLong()
            currentPosition = calculated.coerceIn(0L, liveDuration)
            kotlinx.coroutines.delay(250L)
        }
    }

    val isIndeterminate = liveDuration <= 0L
    val progressFraction = if (!isIndeterminate) {
        (currentPosition.toFloat() / liveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = heightDp.dp)
            .offset { IntOffset(dismissX.value.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - (dismissX.value / (dismissThresholdPx * 2.5f))).coerceIn(0f, 1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch { dismissX.snapTo((dismissX.value + delta).coerceAtLeast(0f)) }
                },
                onDragStopped = { velocity ->
                    if (dismissX.value > dismissThresholdPx || velocity > 1200f) {
                        dismissX.animateTo(dismissThresholdPx * 6f, tween(180))
                        runCatching { current.controller.transportControls.stop() }
                        NowPlayingBus.update(null)
                    } else {
                        dismissX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                },
            ),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val artBox: @Composable () -> Unit = {
                Box(
                    modifier = Modifier.size(artSize),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = current.art
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(artSize * 0.75f),
                        )
                    }
                }
            }

            val textColumn: @Composable (Modifier) -> Unit = { mod ->
                Column(
                    modifier = mod,
                    horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
                ) {
                    Text(
                        current.title.ifBlank { stringResource(R.string.now_playing_unknown_title) },
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = titleSp,
                        textAlign = if (alignRight) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
                    )
                    Text(
                        current.artist,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = artistSp,
                        textAlign = if (alignRight) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
                    )
                }
            }

            val controlsRow: @Composable () -> Unit = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((heightDp * 0.06f).coerceIn(4f, 16f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportButton(
                        Icons.Filled.SkipPrevious,
                        stringResource(R.string.now_playing_previous),
                        controlSize,
                        contentColor,
                    ) {
                        current.controller.transportControls.skipToPrevious()
                    }
                    TransportButton(
                        if (current.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(R.string.now_playing_play_pause),
                        controlSize,
                        contentColor,
                    ) {
                        if (current.isPlaying) {
                            current.controller.transportControls.pause()
                        } else {
                            current.controller.transportControls.play()
                        }
                    }
                    TransportButton(
                        Icons.Filled.SkipNext,
                        stringResource(R.string.now_playing_next),
                        controlSize,
                        contentColor,
                    ) {
                        current.controller.transportControls.skipToNext()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (alignRight) {
                    controlsRow()
                    Spacer(Modifier.width(8.dp))
                    textColumn(Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    artBox()
                } else {
                    artBox()
                    Spacer(Modifier.width(16.dp))
                    textColumn(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    controlsRow()
                }
            }

            Spacer(Modifier.height(4.dp))
            MaterialExpressiveWavyProgressIndicator(
                progress = progressFraction,
                isPlaying = current.isPlaying,
                isIndeterminate = isIndeterminate,
                color = contentColor.copy(alpha = 0.85f),
                trackColor = contentColor.copy(alpha = 0.20f),
                onSeek = if (!isIndeterminate) { fraction ->
                    val targetMs = (fraction * liveDuration).toLong()
                    currentPosition = targetMs
                    runCatching { current.controller.transportControls.seekTo(targetMs) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
            )
        }
    }
}

@Composable
private fun RowScope.TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.7f))
    }
}

/**
 * Material Expressive / Android 13+ undulating wavy squiggly progress indicator.
 * Displays a smooth sine wave along the active track that flattens into a sleek line when paused or scrubbed,
 * and supports direct interactive seeking via tap and drag.
 */
@Composable
fun MaterialExpressiveWavyProgressIndicator(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isIndeterminate: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.20f),
    strokeWidth: Dp = 3.5.dp,
    waveLength: Dp = 26.dp,
    amplitude: Dp = 3.5.dp,
    onSeek: ((Float) -> Unit)? = null,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val effectiveFraction = (dragFraction ?: progress).coerceIn(0f, 1f)

    // Smooth transition: active wave when playing, straight line when paused or scrubbing
    val targetAmplitude = if (isPlaying && dragFraction == null) 1f else 0f
    val waveAmplitudeFactor by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "waveAmplitude",
    )

    // Continuous wave phase animation while playing
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhaseTransition")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }
    val maxAmplitudePx = with(density) { amplitude.toPx() }

    val gestureModifier = if (onSeek != null) {
        Modifier.pointerInput(onSeek) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val totalWidth = size.width.toFloat()
                if (totalWidth <= 0f) return@awaitEachGesture

                var currentFraction = (down.position.x / totalWidth).coerceIn(0f, 1f)
                dragFraction = currentFraction

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    change.consume()
                    currentFraction = (change.position.x / totalWidth).coerceIn(0f, 1f)
                    dragFraction = currentFraction
                }

                onSeek(currentFraction)
                dragFraction = null
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .then(gestureModifier)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val progressX = if (isIndeterminate) width else (width * effectiveFraction).coerceIn(0f, width)

        // 1. Unplayed background track (dimmed straight line)
        if (!isIndeterminate && progressX < width) {
            val trackStartX = if (progressX <= 0f) 0f else progressX
            drawLine(
                color = trackColor,
                start = Offset(trackStartX, midY),
                end = Offset(width, midY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        // 2. Played wavy track
        if (progressX > 0f) {
            val currentAmplitude = maxAmplitudePx * waveAmplitudeFactor

            if (currentAmplitude <= 0.1f) {
                // Flattened straight line when paused or scrubbing
                drawLine(
                    color = color,
                    start = Offset(0f, midY),
                    end = Offset(progressX, midY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            } else {
                val path = Path()
                path.moveTo(0f, midY)

                val step = 3f
                var x = 0f
                val k = (2 * PI / waveLengthPx).toFloat()

                while (x <= progressX) {
                    val distToEdge = min(x, progressX - x)
                    val envelope = (distToEdge / (waveLengthPx * 0.4f)).coerceIn(0f, 1f)
                    val y = midY + currentAmplitude * envelope * sin(k * x - animatedPhase)
                    path.lineTo(x, y)
                    x += step
                }
                path.lineTo(progressX, midY)

                drawPath(path = path, color = color, style = stroke)
            }

            // 3. Thumb indicator (Material Expressive rounded pill)
            if (!isIndeterminate) {
                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 12.dp.toPx()
                val thumbX = progressX.coerceIn(thumbWidth / 2f, (width - thumbWidth / 2f).coerceAtLeast(thumbWidth / 2f))
                drawRoundRect(
                    color = color,
                    topLeft = Offset(thumbX - thumbWidth / 2f, midY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f),
                )
            }
        }
    }
}
