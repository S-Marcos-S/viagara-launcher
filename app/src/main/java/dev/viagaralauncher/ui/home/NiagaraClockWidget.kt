// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.viagaralauncher.R
import dev.viagaralauncher.data.ClockStyle
import dev.viagaralauncher.data.DailyQuote
import dev.viagaralauncher.data.DailyQuoteManager
import dev.viagaralauncher.service.SystemStats
import dev.viagaralauncher.service.rememberSystemStats
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Distance from the top of the screen: ~2.5 cm (25 mm = ~158 dp at 160 dpi). */
val CLOCK_TOP_PADDING_DP = 158.dp

fun isCenteredClockStyle(style: ClockStyle): Boolean = when (style) {
    ClockStyle.NOTHING_DOTS,
    ClockStyle.CENTERED_PILL,
    ClockStyle.DUAL_TONE_STACK,
    ClockStyle.CALLIGRAPHY_LARGE,
    ClockStyle.CALLIGRAPHY_STACKED,
    ClockStyle.CALLIGRAPHY_MINIMAL,
    ClockStyle.HANDWRITTEN_CANVAS,
    ClockStyle.HANDWRITTEN_CANVAS_STATS -> true
    else -> false
}

/**
 * Niagara-style clock and date widget with multiple style options.
 * Sits on the home screen above favorite apps.
 * - Tapping the time launches the system Clock/Alarm app.
 * - Tapping the date launches the system Calendar app.
 */
@Composable
fun NiagaraClockWidget(
    clockStyle: ClockStyle = ClockStyle.CLASSIC,
    contentColor: Color,
    sidePaddingDp: Int,
    alignRight: Boolean,
    modifier: Modifier = Modifier,
    startPaddingDp: Int = sidePaddingDp,
    endPaddingDp: Int = sidePaddingDp,
    widthScale: Float = 1.0f,
    heightScale: Float = 1.0f,
    onLongClick: ((DpOffset) -> Unit)? = null,
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Date()) }

    // Listen for system time broadcast ticks to update the clock efficiently without polling loops
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                currentTime = Date()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Receiver might not have been registered or already unregistered
            }
        }
    }

    val is24Hour = DateFormat.is24HourFormat(context)
    val timePattern = if (is24Hour) "HH:mm" else "h:mm"
    val timeString = remember(currentTime, is24Hour) {
        SimpleDateFormat(timePattern, Locale.getDefault()).format(currentTime)
    }

    val hoursPattern = if (is24Hour) "HH" else "h"
    val hoursString = remember(currentTime, is24Hour) {
        SimpleDateFormat(hoursPattern, Locale.getDefault()).format(currentTime)
    }
    val minutesString = remember(currentTime) {
        SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
    }

    val datePattern = if (Locale.getDefault().language == "pt") {
        "EEEE, d 'de' MMMM"
    } else {
        "EEEE, MMMM d"
    }
    val rawDateString = remember(currentTime) {
        SimpleDateFormat(datePattern, Locale.getDefault()).format(currentTime)
    }
    val dateString = remember(rawDateString) {
        rawDateString.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    val weekdayShortPattern = if (Locale.getDefault().language == "pt") "EEEE, d" else "EEEE, MMM d"
    val dayFocusHeader = remember(currentTime) {
        SimpleDateFormat(weekdayShortPattern, Locale.getDefault()).format(currentTime).uppercase()
    }

    val shortDatePattern = if (Locale.getDefault().language == "pt") "EEE, d 'de' MMM" else "EEE, MMM d"
    val shortDateString = remember(currentTime) {
        SimpleDateFormat(shortDatePattern, Locale.getDefault()).format(currentTime).replace(".", "")
    }

    val isCentered = isCenteredClockStyle(clockStyle)
    val horizontalAlignment = if (isCentered) Alignment.CenterHorizontally else if (alignRight) Alignment.End else Alignment.Start
    val symmetricPadding = if (isCentered) maxOf(startPaddingDp, endPaddingDp) else null
    val effectiveStartPadding = symmetricPadding ?: startPaddingDp
    val effectiveEndPadding = symmetricPadding ?: endPaddingDp

    val viewConfig = LocalViewConfiguration.current
    val density = LocalDensity.current

    val transformOrigin = remember(isCentered, alignRight) {
        val xOrigin = if (isCentered) 0.5f else if (alignRight) 1.0f else 0.0f
        TransformOrigin(xOrigin, 0.5f)
    }

    val gestureModifier = if (onLongClick != null) {
        Modifier.pointerInput(onLongClick) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                val downPos = down.position
                var isLongPress = false
                val timeout = viewConfig.longPressTimeoutMillis
                val change = withTimeoutOrNull(timeout) {
                    val upOrCancel = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    upOrCancel
                }
                if (change == null) {
                    // Timeout elapsed while touch is still held -> Long Press triggered!
                    isLongPress = true
                    with(density) {
                        onLongClick(DpOffset(downPos.x.toDp(), downPos.y.toDp()))
                    }
                    // Consume current event to avoid unwanted clicks
                    down.consume()
                }
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = effectiveStartPadding.dp, end = effectiveEndPadding.dp),
        horizontalAlignment = horizontalAlignment,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = widthScale
                    scaleY = heightScale
                    this.transformOrigin = transformOrigin
                }
                .then(gestureModifier)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val widthFactor = (availableWidth / 340.dp).coerceIn(0.65f, 1.0f)
                val isCompact = availableWidth < 315.dp
                val isUltraCompact = availableWidth < 250.dp

            when (clockStyle) {
                ClockStyle.CLASSIC -> {
                    ClassicClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.STACKED -> {
                    StackedClockContent(
                        hoursString = hoursString,
                        minutesString = minutesString,
                        dateString = dateString,
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.MINIMAL -> {
                    MinimalClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.ANALOG -> {
                    AnalogClockContent(
                        currentTime = currentTime,
                        timeString = timeString,
                        dateString = dateString,
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.DIGITAL_CARD -> {
                    DigitalCardClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.DAY_FOCUS -> {
                    DayFocusClockContent(
                        headerString = dayFocusHeader,
                        timeString = timeString,
                        dateString = dateString,
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.TECH_HUD -> {
                    TechHudClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        isCompact = isCompact,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onStatsClick = { launchBatterySettings(context) },
                    )
                }
                ClockStyle.TECH_HUD_PRO -> {
                    TechHudProClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        isCompact = isCompact,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onBatteryStatsClick = { launchBatterySettings(context) },
                        onStorageClick = { launchStorageSettings(context) },
                    )
                }
                ClockStyle.SYSTEM_MONITOR -> {
                    SystemMonitorClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        isCompact = isCompact,
                        isUltraCompact = isUltraCompact,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onStatsClick = { launchBatterySettings(context) },
                    )
                }
                ClockStyle.MINIMAL_SPECS -> {
                    MinimalSpecsClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        isCompact = isCompact,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onStatsClick = { launchBatterySettings(context) },
                    )
                }
                ClockStyle.RETRO_TERMINAL -> {
                    RetroTerminalClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        horizontalAlignment = horizontalAlignment,
                        widthFactor = widthFactor,
                        isCompact = isCompact,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onStatsClick = { launchBatterySettings(context) },
                    )
                }
                ClockStyle.DAILY_REFLECTION -> {
                    val dailyQuote = remember(currentTime) { DailyQuoteManager.getQuoteForToday() }
                    val compactDatePattern = if (Locale.getDefault().language == "pt") {
                        "EEE, d 'de' MMM"
                    } else {
                        "EEE, MMM d"
                    }
                    val compactDateString = remember(currentTime) {
                        SimpleDateFormat(compactDatePattern, Locale.getDefault())
                            .format(currentTime)
                            .replace(".", "")
                            .uppercase()
                    }
                    DailyReflectionClockContent(
                        timeString = timeString,
                        compactDateString = compactDateString,
                        quote = dailyQuote,
                        contentColor = contentColor,
                        alignRight = alignRight,
                        widthFactor = widthFactor,
                        availableWidth = availableWidth,
                        isCompact = isCompact,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onQuoteClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Reflexão do dia", "\"${dailyQuote.quote}\"\n— ${dailyQuote.author}"))
                            Toast.makeText(context, context.getString(R.string.quote_copied), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                ClockStyle.DAILY_REFLECTION_STATS -> {
                    val dailyQuote = remember(currentTime) { DailyQuoteManager.getQuoteForToday() }
                    val compactDatePattern = if (Locale.getDefault().language == "pt") {
                        "EEE, d 'de' MMM"
                    } else {
                        "EEE, MMM d"
                    }
                    val compactDateString = remember(currentTime) {
                        SimpleDateFormat(compactDatePattern, Locale.getDefault())
                            .format(currentTime)
                            .replace(".", "")
                            .uppercase()
                    }
                    DailyReflectionStatsClockContent(
                        timeString = timeString,
                        compactDateString = compactDateString,
                        quote = dailyQuote,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        alignRight = alignRight,
                        widthFactor = widthFactor,
                        availableWidth = availableWidth,
                        isCompact = isCompact,
                        isUltraCompact = isUltraCompact,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onQuoteClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Reflexão do dia", "\"${dailyQuote.quote}\"\n— ${dailyQuote.author}"))
                            Toast.makeText(context, context.getString(R.string.quote_copied), Toast.LENGTH_SHORT).show()
                        },
                        onRamClick = { launchMemorySettings(context) },
                        onBatteryClick = { launchBatterySettings(context) },
                        onStorageClick = { launchStorageSettings(context) },
                    )
                }
                ClockStyle.NOTHING_DOTS -> {
                    NothingDotsClockContent(
                        hoursString = hoursString,
                        minutesString = minutesString,
                        dateString = shortDateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onBatteryClick = { launchBatterySettings(context) },
                    )
                }
                ClockStyle.CENTERED_PILL -> {
                    CenteredPillClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onBatteryClick = { launchBatterySettings(context) },
                    )
                }
                ClockStyle.DUAL_TONE_STACK -> {
                    DualToneStackClockContent(
                        hoursString = hoursString,
                        minutesString = minutesString,
                        shortDateString = shortDateString,
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.CALLIGRAPHY_LARGE -> {
                    CalligraphyLargeClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.CALLIGRAPHY_STACKED -> {
                    CalligraphyStackedClockContent(
                        hoursString = hoursString,
                        minutesString = minutesString,
                        dateString = dateString,
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.CALLIGRAPHY_MINIMAL -> {
                    CalligraphyMinimalClockContent(
                        timeString = timeString,
                        dateString = dateString,
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.HANDWRITTEN_CANVAS -> {
                    HandwrittenCanvasClockContent(
                        hoursString = hoursString,
                        minutesString = minutesString,
                        dateString = dateString,
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                    )
                }
                ClockStyle.HANDWRITTEN_CANVAS_STATS -> {
                    HandwrittenCanvasStatsClockContent(
                        hoursString = hoursString,
                        minutesString = minutesString,
                        dateString = dateString,
                        stats = rememberSystemStats(currentTime),
                        contentColor = contentColor,
                        widthFactor = widthFactor,
                        onClockClick = { launchClockApp(context) },
                        onDateClick = { launchCalendarApp(context) },
                        onBatteryClick = { launchBatterySettings(context) },
                        onMemoryClick = { launchMemorySettings(context) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicClockContent(
    timeString: String,
    dateString: String,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        AnimatedCanvasTimeRow(
            timeString = timeString,
            style = ClockStyle.CLASSIC,
            contentColor = contentColor,
            digitWidth = (34 * widthFactor).dp.coerceAtLeast(20.dp),
            digitHeight = (54 * widthFactor).dp.coerceAtLeast(32.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
        Spacer(modifier = Modifier.height((6 * widthFactor).dp.coerceAtLeast(3.dp)))
        Text(
            text = dateString,
            color = contentColor.copy(alpha = 0.85f),
            fontSize = (17 * widthFactor).sp,
            fontWeight = FontWeight.Normal,
            lineHeight = (22 * widthFactor).sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
    }
}

@Composable
private fun StackedClockContent(
    hoursString: String,
    minutesString: String,
    dateString: String,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        AnimatedCanvasStackedTime(
            hoursString = hoursString,
            minutesString = minutesString,
            style = ClockStyle.STACKED,
            contentColor = contentColor,
            digitWidth = (32 * widthFactor).dp.coerceAtLeast(18.dp),
            digitHeight = (48 * widthFactor).dp.coerceAtLeast(28.dp),
            minutesAlpha = 0.85f,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
        Spacer(modifier = Modifier.height((6 * widthFactor).dp.coerceAtLeast(3.dp)))
        Text(
            text = dateString,
            color = contentColor.copy(alpha = 0.85f),
            fontSize = (15 * widthFactor).sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
    }
}

@Composable
private fun MinimalClockContent(
    timeString: String,
    dateString: String,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        AnimatedCanvasTimeRow(
            timeString = timeString,
            style = ClockStyle.MINIMAL,
            contentColor = contentColor,
            digitWidth = (28 * widthFactor).dp.coerceAtLeast(16.dp),
            digitHeight = (46 * widthFactor).dp.coerceAtLeast(26.dp),
            strokeWidthDp = (2.2 * widthFactor).dp.coerceIn(1.6.dp, 3.0.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
        Spacer(modifier = Modifier.height((4 * widthFactor).dp.coerceAtLeast(2.dp)))
        Text(
            text = dateString,
            color = contentColor.copy(alpha = 0.75f),
            fontSize = (14 * widthFactor).sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
    }
}

@Composable
private fun AnalogClockContent(
    currentTime: Date,
    timeString: String,
    dateString: String,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    val dialSize = (84 * widthFactor).roundToInt().coerceAtLeast(54)
    Column(horizontalAlignment = horizontalAlignment) {
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClockClick,
                )
                .padding(vertical = 4.dp),
        ) {
            AnalogDial(
                time = currentTime,
                tint = contentColor,
                sizeDp = dialSize,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        ) {
            Text(
                text = timeString,
                color = contentColor,
                fontSize = (16 * widthFactor).sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "  ·  $dateString",
                color = contentColor.copy(alpha = 0.8f),
                fontSize = (15 * widthFactor).sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DigitalCardClockContent(
    timeString: String,
    dateString: String,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape((18 * widthFactor).dp.coerceAtLeast(12.dp)))
                .background(contentColor.copy(alpha = 0.08f))
                .border(1.dp, contentColor.copy(alpha = 0.16f), RoundedCornerShape((18 * widthFactor).dp.coerceAtLeast(12.dp)))
                .padding(
                    horizontal = (18 * widthFactor).dp.coerceAtLeast(10.dp),
                    vertical = (12 * widthFactor).dp.coerceAtLeast(8.dp),
                ),
        ) {
            Column(horizontalAlignment = horizontalAlignment) {
                AnimatedCanvasTimeRow(
                    timeString = timeString,
                    style = ClockStyle.DIGITAL_CARD,
                    contentColor = contentColor,
                    digitWidth = (26 * widthFactor).dp.coerceAtLeast(16.dp),
                    digitHeight = (42 * widthFactor).dp.coerceAtLeast(24.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClockClick,
                    ),
                )
                Spacer(modifier = Modifier.height((6 * widthFactor).dp.coerceAtLeast(3.dp)))
                Text(
                    text = dateString,
                    color = contentColor.copy(alpha = 0.85f),
                    fontSize = (14 * widthFactor).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDateClick,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DayFocusClockContent(
    headerString: String,
    timeString: String,
    dateString: String,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = headerString,
            color = contentColor.copy(alpha = 0.7f),
            fontSize = (13 * widthFactor).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (1.8 * widthFactor).sp,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        AnimatedCanvasTimeRow(
            timeString = timeString,
            style = ClockStyle.DAY_FOCUS,
            contentColor = contentColor,
            digitWidth = (32 * widthFactor).dp.coerceAtLeast(18.dp),
            digitHeight = (52 * widthFactor).dp.coerceAtLeast(30.dp),
            strokeWidthDp = (2.4 * widthFactor).dp.coerceIn(1.8.dp, 3.2.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
    }
}

@Composable
fun AnalogDial(
    time: Date,
    tint: Color,
    sizeDp: Int = 84,
    modifier: Modifier = Modifier,
) {
    val cal = remember(time) {
        Calendar.getInstance().apply { this.time = time }
    }
    val hours = cal.get(Calendar.HOUR)
    val minutes = cal.get(Calendar.MINUTE)
    val seconds = cal.get(Calendar.SECOND)

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        // Draw subtle outer dial circle
        drawCircle(
            color = tint.copy(alpha = 0.2f),
            radius = radius * 0.95f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )

        // Draw hour markers at 12, 3, 6, 9
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30.0) - 90.0)
            val isMajor = i % 3 == 0
            val markerLength = if (isMajor) radius * 0.16f else radius * 0.08f
            val startDist = radius * 0.92f - markerLength
            val endDist = radius * 0.92f

            val startX = center.x + (startDist * cos(angle)).toFloat()
            val startY = center.y + (startDist * sin(angle)).toFloat()
            val endX = center.x + (endDist * cos(angle)).toFloat()
            val endY = center.y + (endDist * sin(angle)).toFloat()

            drawLine(
                color = if (isMajor) tint.copy(alpha = 0.7f) else tint.copy(alpha = 0.3f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = (if (isMajor) 2.dp else 1.2.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Hour Hand
        val hourAngle = Math.toRadians(((hours + minutes / 60f) * 30.0) - 90.0)
        val hourLength = radius * 0.52f
        val hourEnd = Offset(
            x = center.x + (hourLength * cos(hourAngle)).toFloat(),
            y = center.y + (hourLength * sin(hourAngle)).toFloat(),
        )
        drawLine(
            color = tint,
            start = center,
            end = hourEnd,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Minute Hand
        val minuteAngle = Math.toRadians(((minutes + seconds / 60f) * 6.0) - 90.0)
        val minuteLength = radius * 0.75f
        val minuteEnd = Offset(
            x = center.x + (minuteLength * cos(minuteAngle)).toFloat(),
            y = center.y + (minuteLength * sin(minuteAngle)).toFloat(),
        )
        drawLine(
            color = tint.copy(alpha = 0.9f),
            start = center,
            end = minuteEnd,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Center Pivot
        drawCircle(
            color = tint,
            radius = 3.5.dp.toPx(),
        )
    }
}

@Composable
private fun TechHudClockContent(
    timeString: String,
    dateString: String,
    stats: SystemStats,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    isCompact: Boolean = false,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onStatsClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        // Tag / Sys header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(contentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "SYS // HUD",
                    color = contentColor,
                    fontSize = (10 * widthFactor).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = dateString.uppercase(),
                color = contentColor.copy(alpha = 0.7f),
                fontSize = (12 * widthFactor).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Big HUD Time
        AnimatedCanvasTimeRow(
            timeString = timeString,
            style = ClockStyle.TECH_HUD,
            contentColor = contentColor,
            digitWidth = (32 * widthFactor).dp.coerceAtLeast(18.dp),
            digitHeight = (52 * widthFactor).dp.coerceAtLeast(30.dp),
            strokeWidthDp = (2.6 * widthFactor).dp.coerceIn(2.0.dp, 3.6.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )

        Spacer(Modifier.height(8.dp))

        // Telemetry Chips Row
        // Quando o espaço é pouco, removemos o prefixo "RAM " ao invés de reduzir a fonte
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onStatsClick,
            ),
        ) {
            val ramLabel = if (isCompact) {
                "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G"
            } else {
                "RAM ${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G"
            }
            val chipHPadding = if (isCompact) 4.dp else 8.dp

            TechChip(
                icon = "💾",
                label = ramLabel,
                contentColor = contentColor,
                horizontalPadding = chipHPadding,
            )
            TechChip(
                icon = "🌡️",
                label = "${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                contentColor = contentColor,
                horizontalPadding = chipHPadding,
            )
            TechChip(
                icon = if (stats.isCharging) "⚡" else "🔋",
                label = "${stats.batteryPercent}%",
                contentColor = contentColor,
                horizontalPadding = chipHPadding,
            )
        }
    }
}

@Composable
private fun TechChip(
    icon: String,
    label: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.5.sp,
    iconSize: TextUnit = 11.sp,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 6.dp,
    shapeRadius: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius))
            .background(contentColor.copy(alpha = 0.08f))
            .border(1.dp, contentColor.copy(alpha = 0.16f), RoundedCornerShape(shapeRadius))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = icon, fontSize = iconSize)
            Spacer(Modifier.width(2.dp))
            Text(
                text = label,
                color = contentColor.copy(alpha = 0.9f),
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechHudProClockContent(
    timeString: String,
    dateString: String,
    stats: SystemStats,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    isCompact: Boolean = false,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onBatteryStatsClick: () -> Unit,
    onStorageClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        // Tag / Sys header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(contentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "SYS // HUD PRO",
                    color = contentColor,
                    fontSize = (10 * widthFactor).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = dateString.uppercase(),
                color = contentColor.copy(alpha = 0.7f),
                fontSize = (12 * widthFactor).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Big HUD Time + Compact CPU Core Telemetry Badge beside the clock
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((10 * widthFactor).dp.coerceAtLeast(6.dp)),
        ) {
            AnimatedCanvasTimeRow(
                timeString = timeString,
                style = ClockStyle.TECH_HUD_PRO,
                contentColor = contentColor,
                digitWidth = (32 * widthFactor).dp.coerceAtLeast(18.dp),
                digitHeight = (52 * widthFactor).dp.coerceAtLeast(30.dp),
                strokeWidthDp = (2.6 * widthFactor).dp.coerceIn(2.0.dp, 3.6.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClockClick,
                ),
            )

            // Compact CPU Cores Telemetry Badge (Static/zero battery drain)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(contentColor.copy(alpha = 0.08f))
                    .border(1.dp, contentColor.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    .padding(
                        horizontal = (7 * widthFactor).dp.coerceAtLeast(4.dp),
                        vertical = (5 * widthFactor).dp.coerceAtLeast(3.dp),
                    ),
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.8f)),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "CPU",
                            color = contentColor.copy(alpha = 0.6f),
                            fontSize = (8 * widthFactor).sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "${Runtime.getRuntime().availableProcessors()} CORES",
                        color = contentColor,
                        fontSize = (10 * widthFactor).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Telemetry Chips Row: RAM, Storage (ROM), Temperature, Battery
        // Quando o espaço é pouco, removemos os prefixos "RAM " e "ROM " sem reduzir a fonte!
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val ramLabel = if (isCompact) {
                "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G"
            } else {
                "RAM ${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G"
            }
            val romLabel = if (isCompact) {
                "${String.format(Locale.US, "%.0f", stats.storageAvailableGb)}G"
            } else {
                "ROM ${String.format(Locale.US, "%.0f", stats.storageAvailableGb)}G"
            }
            val chipHPadding = if (isCompact) 4.dp else 8.dp

            TechChip(
                icon = "💾",
                label = ramLabel,
                contentColor = contentColor,
                horizontalPadding = chipHPadding,
                onClick = onBatteryStatsClick,
            )
            TechChip(
                icon = "💽",
                label = romLabel,
                contentColor = contentColor,
                horizontalPadding = chipHPadding,
                onClick = onStorageClick,
            )
            TechChip(
                icon = "🌡️",
                label = "${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                contentColor = contentColor,
                horizontalPadding = chipHPadding,
                onClick = onBatteryStatsClick,
            )
            TechChip(
                icon = if (stats.isCharging) "⚡" else "🔋",
                label = "${stats.batteryPercent}%",
                contentColor = contentColor,
                horizontalPadding = chipHPadding,
                onClick = onBatteryStatsClick,
            )
        }
    }
}

@Composable
private fun SystemMonitorClockContent(
    timeString: String,
    dateString: String,
    stats: SystemStats,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    isCompact: Boolean = false,
    isUltraCompact: Boolean = false,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onStatsClick: () -> Unit,
) {
    val cornerRadius = (20 * widthFactor).dp.coerceAtLeast(12.dp)
    val cardPadding = (16 * widthFactor).dp.coerceAtLeast(8.dp)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(contentColor.copy(alpha = 0.08f))
            .border(1.dp, contentColor.copy(alpha = 0.16f), RoundedCornerShape(cornerRadius))
            .padding(cardPadding),
    ) {
        Column(horizontalAlignment = horizontalAlignment) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AnimatedCanvasTimeRow(
                    timeString = timeString,
                    style = ClockStyle.SYSTEM_MONITOR,
                    contentColor = contentColor,
                    digitWidth = (24 * widthFactor).dp.coerceAtLeast(14.dp),
                    digitHeight = (38 * widthFactor).dp.coerceAtLeast(22.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClockClick,
                    ),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateString,
                        color = contentColor.copy(alpha = 0.85f),
                        fontSize = (13 * widthFactor).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDateClick,
                        ),
                    )
                    Text(
                        text = "${stats.batteryPercent}% ${if (stats.isCharging) "⚡" else ""} · ${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                        color = contentColor.copy(alpha = 0.65f),
                        fontSize = (12 * widthFactor).sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }

            Spacer(Modifier.height((10 * widthFactor).dp.coerceAtLeast(6.dp)))
            HorizontalDivider(color = contentColor.copy(alpha = 0.12f), thickness = 1.dp)
            Spacer(Modifier.height((10 * widthFactor).dp.coerceAtLeast(6.dp)))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStatsClick,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isCompact) "RAM" else "MEMÓRIA RAM",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = (10 * widthFactor).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    val ramInfoText = if (isUltraCompact) {
                        "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)} GB · ${stats.ramUsedPercent}%"
                    } else if (isCompact) {
                        "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)} GB livre (${stats.ramUsedPercent}%)"
                    } else {
                        "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)} GB livres (${stats.ramUsedPercent}% em uso)"
                    }
                    Text(
                        text = ramInfoText,
                        color = contentColor.copy(alpha = 0.85f),
                        fontSize = (11 * widthFactor).sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height((6 * widthFactor).dp.coerceAtLeast(4.dp)))
                LinearProgressIndicator(
                    progress = { (stats.ramUsedPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.15f),
                )
            }
        }
    }
}

@Composable
private fun MinimalSpecsClockContent(
    timeString: String,
    dateString: String,
    stats: SystemStats,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    isCompact: Boolean = false,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onStatsClick: () -> Unit,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        AnimatedCanvasTimeRow(
            timeString = timeString,
            style = ClockStyle.MINIMAL_SPECS,
            contentColor = contentColor,
            digitWidth = (32 * widthFactor).dp.coerceAtLeast(18.dp),
            digitHeight = (52 * widthFactor).dp.coerceAtLeast(30.dp),
            strokeWidthDp = (2.4 * widthFactor).dp.coerceIn(1.8.dp, 3.2.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = dateString,
            color = contentColor.copy(alpha = 0.85f),
            fontSize = (16 * widthFactor).sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onStatsClick,
            ),
        ) {
            val specsText = if (isCompact) {
                "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G  ·  ${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C  ·  ${if (stats.isCharging) "⚡" else "🔋"} ${stats.batteryPercent}%"
            } else {
                "RAM ${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G livres  ·  ${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C  ·  ${if (stats.isCharging) "⚡" else "🔋"} ${stats.batteryPercent}%"
            }
            Text(
                text = specsText,
                color = contentColor.copy(alpha = 0.65f),
                fontSize = (12 * widthFactor).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RetroTerminalClockContent(
    timeString: String,
    dateString: String,
    stats: SystemStats,
    contentColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    widthFactor: Float = 1.0f,
    isCompact: Boolean = false,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onStatsClick: () -> Unit,
) {
    val totalBlocks = if (isCompact) 5 else 8
    val filledBlocks = ((stats.ramUsedPercent / 100f) * totalBlocks).roundToInt().coerceIn(0, totalBlocks)
    val asciiBar = "█".repeat(filledBlocks) + "░".repeat(totalBlocks - filledBlocks)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(contentColor.copy(alpha = 0.08f))
            .border(1.dp, contentColor.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(
                horizontal = (14 * widthFactor).dp.coerceAtLeast(8.dp),
                vertical = (12 * widthFactor).dp.coerceAtLeast(8.dp),
            ),
    ) {
        Column(horizontalAlignment = horizontalAlignment) {
            Text(
                text = "> system.telemetry",
                color = contentColor.copy(alpha = 0.5f),
                fontSize = (11 * widthFactor).sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClockClick,
                ),
            ) {
                Text(
                    text = "TIME: ",
                    color = contentColor.copy(alpha = 0.85f),
                    fontSize = (14 * widthFactor).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                AnimatedCanvasTimeRow(
                    timeString = timeString,
                    style = ClockStyle.RETRO_TERMINAL,
                    contentColor = contentColor,
                    digitWidth = (18 * widthFactor).dp.coerceAtLeast(12.dp),
                    digitHeight = (28 * widthFactor).dp.coerceAtLeast(18.dp),
                    strokeWidthDp = (2.2 * widthFactor).dp.coerceIn(1.5.dp, 3.0.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "DATE: $dateString",
                color = contentColor.copy(alpha = 0.8f),
                fontSize = (12 * widthFactor).sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDateClick,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStatsClick,
                ),
            ) {
                val ramLabel = if (isCompact) {
                    "RAM : [$asciiBar] ${stats.ramUsedPercent}% (${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G)"
                } else {
                    "RAM : [$asciiBar] ${stats.ramUsedPercent}% (${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G free)"
                }
                Text(
                    text = ramLabel,
                    color = contentColor.copy(alpha = 0.9f),
                    fontSize = (11 * widthFactor).sp,
                    fontFamily = FontFamily.Monospace,
                )
                val battCharging = if (isCompact) {
                    if (stats.isCharging) "[CHG]" else "[DIS]"
                } else {
                    if (stats.isCharging) "[CHARGING]" else "[DISCHARGING]"
                }
                Text(
                    text = "BATT: ${stats.batteryPercent}% $battCharging · ${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                    color = contentColor.copy(alpha = 0.9f),
                    fontSize = (11 * widthFactor).sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun DailyReflectionClockContent(
    timeString: String,
    compactDateString: String,
    quote: DailyQuote,
    contentColor: Color,
    alignRight: Boolean,
    widthFactor: Float = 1.0f,
    availableWidth: Dp = 0.dp,
    isCompact: Boolean = false,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onQuoteClick: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val targetClockWidth = (134f * widthFactor * fontScale).dp
    val clockWidth = if (availableWidth > 0.dp && targetClockWidth > availableWidth * 0.46f) {
        availableWidth * 0.46f
    } else {
        targetClockWidth
    }

    val clockBlock: @Composable () -> Unit = {
        Column(
            modifier = Modifier.width(clockWidth),
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = compactDateString,
                color = contentColor.copy(alpha = 0.7f),
                fontSize = (11 * widthFactor).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDateClick,
                    ),
                textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
            )

            Spacer(Modifier.height(2.dp))

            AnimatedCanvasTimeRow(
                timeString = timeString,
                style = ClockStyle.DAILY_REFLECTION,
                contentColor = contentColor,
                digitWidth = (28 * widthFactor).dp.coerceAtLeast(16.dp),
                digitHeight = (46 * widthFactor).dp.coerceAtLeast(26.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClockClick,
                ),
            )
        }
    }

    val quoteBlock: @Composable (Modifier) -> Unit = { mod ->
        AutoSizedDailyQuoteView(
            quote = quote,
            contentColor = contentColor,
            alignRight = alignRight,
            widthFactor = widthFactor,
            baseQuoteFontSizeSp = 13f,
            minQuoteFontSizeSp = 8.5f,
            baseAuthorFontSizeSp = 10.5f,
            minAuthorFontSizeSp = 7.5f,
            targetMaxLines = 4,
            maxAllowedLines = 5,
            authorFontWeight = FontWeight.SemiBold,
            authorAlpha = 0.65f,
            modifier = mod.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onQuoteClick,
            ),
        )
    }

    val dividerSpacing = if (isCompact) 8.dp else 12.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (alignRight) {
            quoteBlock(Modifier.weight(1f))
            Spacer(Modifier.width(dividerSpacing))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height((52 * widthFactor).dp.coerceAtLeast(36.dp))
                    .background(contentColor.copy(alpha = 0.18f))
            )
            Spacer(Modifier.width(dividerSpacing))
            clockBlock()
        } else {
            clockBlock()
            Spacer(Modifier.width(dividerSpacing))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height((52 * widthFactor).dp.coerceAtLeast(36.dp))
                    .background(contentColor.copy(alpha = 0.18f))
            )
            Spacer(Modifier.width(dividerSpacing))
            quoteBlock(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DailyReflectionStatsClockContent(
    timeString: String,
    compactDateString: String,
    quote: DailyQuote,
    stats: SystemStats,
    contentColor: Color,
    alignRight: Boolean,
    widthFactor: Float = 1.0f,
    availableWidth: Dp = 0.dp,
    isCompact: Boolean = false,
    isUltraCompact: Boolean = false,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onQuoteClick: () -> Unit,
    onRamClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onStorageClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DailyReflectionClockContent(
            timeString = timeString,
            compactDateString = compactDateString,
            quote = quote,
            contentColor = contentColor,
            alignRight = alignRight,
            widthFactor = widthFactor,
            availableWidth = availableWidth,
            isCompact = isCompact,
            onClockClick = onClockClick,
            onDateClick = onDateClick,
            onQuoteClick = onQuoteClick,
        )

        Spacer(Modifier.height(10.dp))

        // 4 Telemetry chips compactos na mesma linha, reaproveitando os mesmos chips do HUD Futurista
        // Quando o espaço é pouco, removemos os prefixos "RAM" e "ROM" ao invés de reduzir a fonte (mantém 10.sp)!
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.5.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ramLabel = if (isCompact) {
                "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G"
            } else {
                "RAM ${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G"
            }
            val romLabel = if (isCompact) {
                "${String.format(Locale.US, "%.0f", stats.storageAvailableGb)}G"
            } else {
                "ROM ${String.format(Locale.US, "%.0f", stats.storageAvailableGb)}G"
            }
            val chipHPadding = if (isUltraCompact) 1.5.dp else if (isCompact) 2.5.dp else 4.dp

            TechChip(
                icon = "💾",
                label = ramLabel,
                contentColor = contentColor,
                fontSize = 10.sp,
                iconSize = 9.5.sp,
                horizontalPadding = chipHPadding,
                verticalPadding = 4.dp,
                shapeRadius = 6.dp,
                modifier = Modifier.weight(1f),
                onClick = onRamClick,
            )
            TechChip(
                icon = "🌡️",
                label = "${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                contentColor = contentColor,
                fontSize = 10.sp,
                iconSize = 9.5.sp,
                horizontalPadding = chipHPadding,
                verticalPadding = 4.dp,
                shapeRadius = 6.dp,
                modifier = Modifier.weight(1f),
                onClick = onBatteryClick,
            )
            TechChip(
                icon = if (stats.isCharging) "⚡" else "🔋",
                label = "${stats.batteryPercent}%",
                contentColor = contentColor,
                fontSize = 10.sp,
                iconSize = 9.5.sp,
                horizontalPadding = chipHPadding,
                verticalPadding = 4.dp,
                shapeRadius = 6.dp,
                modifier = Modifier.weight(1f),
                onClick = onBatteryClick,
            )
            TechChip(
                icon = "💽",
                label = romLabel,
                contentColor = contentColor,
                fontSize = 10.sp,
                iconSize = 9.5.sp,
                horizontalPadding = chipHPadding,
                verticalPadding = 4.dp,
                shapeRadius = 6.dp,
                modifier = Modifier.weight(1f),
                onClick = onStorageClick,
            )
        }
    }
}

@Composable
private fun NothingDotsClockContent(
    hoursString: String,
    minutesString: String,
    dateString: String,
    stats: SystemStats,
    contentColor: Color,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onBatteryClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val hChars = hoursString.toList()
        val mChars = minutesString.toList()
        val digitW = (28 * widthFactor).dp.coerceAtLeast(18.dp)
        val digitH = (44 * widthFactor).dp.coerceAtLeast(26.dp)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        ) {
            hChars.forEachIndexed { idx, ch ->
                ThemedAnimatedCanvasDigit(
                    char = ch,
                    style = ClockStyle.NOTHING_DOTS,
                    staggerDelayMs = idx * 90,
                    contentColor = contentColor,
                    modifier = Modifier.size(digitW, digitH),
                )
                if (idx < hChars.lastIndex) Spacer(Modifier.width(3.dp))
            }

            Column(
                modifier = Modifier.padding(horizontal = (6 * widthFactor).dp),
                verticalArrangement = Arrangement.spacedBy((6 * widthFactor).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size((5 * widthFactor).dp.coerceAtLeast(3.dp))
                        .background(contentColor.copy(alpha = 0.85f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size((5 * widthFactor).dp.coerceAtLeast(3.dp))
                        .background(Color(0xFFE53935), CircleShape)
                )
            }

            mChars.forEachIndexed { idx, ch ->
                ThemedAnimatedCanvasDigit(
                    char = ch,
                    style = ClockStyle.NOTHING_DOTS,
                    staggerDelayMs = (hChars.size + idx) * 90,
                    contentColor = contentColor,
                    modifier = Modifier.size(digitW, digitH),
                )
                if (idx < mChars.lastIndex) Spacer(Modifier.width(3.dp))
            }
        }

        Spacer(modifier = Modifier.height((8 * widthFactor).dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(contentColor.copy(alpha = 0.08f))
                .border(1.dp, contentColor.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = dateString.uppercase(Locale.getDefault()),
                    color = contentColor.copy(alpha = 0.85f),
                    fontSize = (11 * widthFactor).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDateClick,
                    ),
                )

                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(Color(0xFFE53935), CircleShape)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBatteryClick,
                    ),
                ) {
                    Text(
                        text = "${stats.batteryPercent}%",
                        color = contentColor.copy(alpha = 0.9f),
                        fontSize = (11 * widthFactor).sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    if (stats.isCharging) {
                        Text(
                            text = "⚡",
                            color = Color(0xFFE53935),
                            fontSize = (10 * widthFactor).sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredPillClockContent(
    timeString: String,
    dateString: String,
    stats: SystemStats,
    contentColor: Color,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    onBatteryClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape((28 * widthFactor).dp.coerceAtLeast(20.dp)))
                .background(contentColor.copy(alpha = 0.08f))
                .border(1.dp, contentColor.copy(alpha = 0.16f), RoundedCornerShape((28 * widthFactor).dp.coerceAtLeast(20.dp)))
                .padding(
                    horizontal = (24 * widthFactor).dp.coerceAtLeast(16.dp),
                    vertical = (12 * widthFactor).dp.coerceAtLeast(8.dp),
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedCanvasTimeRow(
                    timeString = timeString,
                    style = ClockStyle.CENTERED_PILL,
                    contentColor = contentColor,
                    digitWidth = (28 * widthFactor).dp.coerceAtLeast(16.dp),
                    digitHeight = (44 * widthFactor).dp.coerceAtLeast(26.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClockClick,
                    ),
                )

                Spacer(modifier = Modifier.height((6 * widthFactor).dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = dateString,
                        color = contentColor.copy(alpha = 0.85f),
                        fontSize = (12.5 * widthFactor).sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDateClick,
                        ),
                    )

                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(contentColor.copy(alpha = 0.4f), CircleShape)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBatteryClick,
                        ),
                    ) {
                        Text(
                            text = "${stats.batteryPercent}%",
                            color = contentColor.copy(alpha = 0.85f),
                            fontSize = (12 * widthFactor).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (stats.isCharging) {
                            Text(
                                text = "⚡",
                                color = contentColor,
                                fontSize = (10 * widthFactor).sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DualToneStackClockContent(
    hoursString: String,
    minutesString: String,
    shortDateString: String,
    contentColor: Color,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedCanvasStackedTime(
            hoursString = hoursString,
            minutesString = minutesString,
            style = ClockStyle.DUAL_TONE_STACK,
            contentColor = contentColor,
            digitWidth = (38 * widthFactor).dp.coerceAtLeast(22.dp),
            digitHeight = (56 * widthFactor).dp.coerceAtLeast(32.dp),
            minutesAlpha = 0.45f,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )

        Spacer(modifier = Modifier.height((10 * widthFactor).dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(contentColor.copy(alpha = 0.07f))
                .border(1.dp, contentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDateClick,
                ),
        ) {
            Text(
                text = shortDateString.uppercase(Locale.getDefault()),
                color = contentColor.copy(alpha = 0.85f),
                fontSize = (11 * widthFactor).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalligraphyLargeClockContent(
    timeString: String,
    dateString: String,
    contentColor: Color,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedCanvasTimeRow(
            timeString = timeString,
            style = ClockStyle.CALLIGRAPHY_LARGE,
            contentColor = contentColor,
            digitWidth = (44 * widthFactor).dp.coerceAtLeast(24.dp),
            digitHeight = (72 * widthFactor).dp.coerceAtLeast(40.dp),
            strokeWidthDp = (3.2 * widthFactor).dp.coerceIn(2.2.dp, 4.2.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
        Box(
            modifier = Modifier
                .padding(vertical = (6 * widthFactor).dp.coerceAtLeast(3.dp))
                .width((72 * widthFactor).dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.35f)),
        )
        Text(
            text = dateString,
            color = contentColor.copy(alpha = 0.88f),
            fontSize = (16 * widthFactor).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (0.8 * widthFactor).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
    }
}

@Composable
private fun CalligraphyStackedClockContent(
    hoursString: String,
    minutesString: String,
    dateString: String,
    contentColor: Color,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedCanvasStackedTime(
            hoursString = hoursString,
            minutesString = minutesString,
            style = ClockStyle.CALLIGRAPHY_STACKED,
            contentColor = contentColor,
            digitWidth = (40 * widthFactor).dp.coerceAtLeast(22.dp),
            digitHeight = (64 * widthFactor).dp.coerceAtLeast(34.dp),
            strokeWidthDp = (3.0 * widthFactor).dp.coerceIn(2.0.dp, 4.0.dp),
            minutesAlpha = 0.78f,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
        Spacer(modifier = Modifier.height((8 * widthFactor).dp.coerceAtLeast(4.dp)))
        Text(
            text = dateString,
            color = contentColor.copy(alpha = 0.85f),
            fontSize = (15 * widthFactor).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (1.0 * widthFactor).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
    }
}

@Composable
private fun CalligraphyMinimalClockContent(
    timeString: String,
    dateString: String,
    contentColor: Color,
    widthFactor: Float = 1.0f,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedCanvasTimeRow(
            timeString = timeString,
            style = ClockStyle.CALLIGRAPHY_MINIMAL,
            contentColor = contentColor,
            digitWidth = (42 * widthFactor).dp.coerceAtLeast(22.dp),
            digitHeight = (68 * widthFactor).dp.coerceAtLeast(36.dp),
            strokeWidthDp = (2.6 * widthFactor).dp.coerceIn(1.8.dp, 3.6.dp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClockClick,
            ),
        )
        Spacer(modifier = Modifier.height((4 * widthFactor).dp.coerceAtLeast(2.dp)))
        Text(
            text = "—  $dateString  —",
            color = contentColor.copy(alpha = 0.80f),
            fontSize = (15 * widthFactor).sp,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            letterSpacing = (0.5 * widthFactor).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDateClick,
            ),
        )
    }
}

/**
 * Scaled mini preview of a clock style used in the 2-column grid picker.
 */
@Composable
fun ClockStylePreview(
    style: ClockStyle,
    currentTime: Date,
    is24Hour: Boolean,
    tint: Color,
    stats: SystemStats = rememberSystemStats(currentTime),
    modifier: Modifier = Modifier,
) {
    val timePattern = if (is24Hour) "HH:mm" else "h:mm"
    val timeString = remember(currentTime, is24Hour) {
        SimpleDateFormat(timePattern, Locale.getDefault()).format(currentTime)
    }
    val hoursPattern = if (is24Hour) "HH" else "h"
    val hoursString = remember(currentTime, is24Hour) {
        SimpleDateFormat(hoursPattern, Locale.getDefault()).format(currentTime)
    }
    val minutesString = remember(currentTime) {
        SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
    }
    val weekdayPattern = if (Locale.getDefault().language == "pt") "EEE, d MMM" else "EEE, MMM d"
    val shortDateString = remember(currentTime) {
        SimpleDateFormat(weekdayPattern, Locale.getDefault()).format(currentTime)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            ClockStyle.CLASSIC -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        color = tint,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = shortDateString,
                        color = tint.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                    )
                }
            }
            ClockStyle.STACKED -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = hoursString,
                        color = tint,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 20.sp,
                    )
                    Text(
                        text = minutesString,
                        color = tint.copy(alpha = 0.85f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = shortDateString,
                        color = tint.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                }
            }
            ClockStyle.MINIMAL -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        color = tint,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = shortDateString,
                        color = tint.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                    )
                }
            }
            ClockStyle.ANALOG -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnalogDial(
                        time = currentTime,
                        tint = tint,
                        sizeDp = 48,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timeString,
                        color = tint.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            ClockStyle.DIGITAL_CARD -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.08f))
                        .border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeString,
                            color = tint,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = shortDateString,
                            color = tint.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            ClockStyle.DAY_FOCUS -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = shortDateString.uppercase(),
                        color = tint.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = timeString,
                        color = tint,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            ClockStyle.TECH_HUD -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SYS // HUD",
                        color = tint.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = timeString,
                        color = tint,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "RAM ${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G · ${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                        color = tint.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            ClockStyle.TECH_HUD_PRO -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SYS // HUD PRO",
                        color = tint.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = timeString,
                            color = tint,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(tint.copy(alpha = 0.12f))
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "${Runtime.getRuntime().availableProcessors()}C",
                                color = tint,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "RAM ${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G · ROM ${String.format(Locale.US, "%.0f", stats.storageAvailableGb)}G · ${stats.batteryPercent}%",
                        color = tint.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            ClockStyle.SYSTEM_MONITOR -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.08f))
                        .border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeString,
                            color = tint,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "RAM ${stats.ramUsedPercent}% · BAT ${stats.batteryPercent}%",
                            color = tint.copy(alpha = 0.8f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { (stats.ramUsedPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = tint,
                            trackColor = tint.copy(alpha = 0.15f),
                        )
                    }
                }
            }
            ClockStyle.MINIMAL_SPECS -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        color = tint,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = shortDateString,
                        color = tint.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", stats.ramAvailableGb)}G · ${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                        color = tint.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                    )
                }
            }
            ClockStyle.RETRO_TERMINAL -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tint.copy(alpha = 0.08f))
                        .border(1.dp, tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "> $timeString",
                            color = tint,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "RAM ${stats.ramUsedPercent}% | ${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                            color = tint.copy(alpha = 0.8f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            ClockStyle.DAILY_REFLECTION -> {
                Row(
                    modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.width(62.dp),
                    ) {
                        Text(
                            text = "SEX, 18 SET",
                            color = tint.copy(alpha = 0.7f),
                            fontSize = 6.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = timeString,
                            color = tint,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1).sp,
                            lineHeight = 24.sp,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(tint.copy(alpha = 0.25f))
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "“O segredo de progredir é começar.”",
                            color = tint.copy(alpha = 0.85f),
                            fontSize = 7.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 9.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "— Mark Twain",
                            color = tint.copy(alpha = 0.6f),
                            fontSize = 6.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            ClockStyle.DAILY_REFLECTION_STATS -> {
                Column(
                    modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.width(52.dp),
                        ) {
                            Text(
                                text = "SEX, 18 SET",
                                color = tint.copy(alpha = 0.7f),
                                fontSize = 5.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = timeString,
                                color = tint,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-1).sp,
                                lineHeight = 20.sp,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(tint.copy(alpha = 0.25f))
                        )
                        Spacer(Modifier.width(5.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "“O segredo de progredir é começar.”",
                                color = tint.copy(alpha = 0.85f),
                                fontSize = 6.sp,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 7.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "— Mark Twain",
                                color = tint.copy(alpha = 0.6f),
                                fontSize = 5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        listOf(
                            "RAM ${stats.ramUsedPercent}%",
                            "${String.format(Locale.US, "%.0f", stats.batteryTempCelsius)}°C",
                            "${stats.batteryPercent}%",
                            "ROM ${stats.storageUsedPercent}%",
                        ).forEach { label ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(tint.copy(alpha = 0.08f))
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = tint.copy(alpha = 0.8f),
                                    fontSize = 4.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            ClockStyle.NOTHING_DOTS -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = hoursString,
                            color = tint,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(modifier = Modifier.size(3.dp).background(tint.copy(alpha = 0.8f), CircleShape))
                            Box(modifier = Modifier.size(3.dp).background(Color(0xFFE53935), CircleShape))
                        }
                        Text(
                            text = minutesString,
                            color = tint,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint.copy(alpha = 0.08f))
                            .border(1.dp, tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${shortDateString.uppercase()} · ${stats.batteryPercent}%",
                            color = tint.copy(alpha = 0.8f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            ClockStyle.CENTERED_PILL -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(tint.copy(alpha = 0.08f))
                        .border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeString,
                            color = tint,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "$shortDateString • ${stats.batteryPercent}%",
                            color = tint.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            ClockStyle.DUAL_TONE_STACK -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = hoursString,
                        color = tint,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 20.sp,
                    )
                    Text(
                        text = minutesString,
                        color = tint.copy(alpha = 0.45f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = shortDateString.uppercase(),
                        color = tint.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
            ClockStyle.CALLIGRAPHY_LARGE -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        color = tint,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Cursive,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = shortDateString,
                        color = tint.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ClockStyle.CALLIGRAPHY_STACKED -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = hoursString,
                        color = tint,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = minutesString,
                        color = tint.copy(alpha = 0.7f),
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = shortDateString.uppercase(),
                        color = tint.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ClockStyle.CALLIGRAPHY_MINIMAL -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeString,
                        color = tint,
                        fontSize = 34.sp,
                        fontFamily = FontFamily.Cursive,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "— $shortDateString —",
                        color = tint.copy(alpha = 0.75f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ClockStyle.HANDWRITTEN_CANVAS -> {
                HandwrittenCanvasClockPreview(
                    hoursString = hoursString,
                    minutesString = minutesString,
                    shortDateString = shortDateString,
                    tint = tint,
                )
            }
            ClockStyle.HANDWRITTEN_CANVAS_STATS -> {
                HandwrittenCanvasStatsClockPreview(
                    hoursString = hoursString,
                    minutesString = minutesString,
                    shortDateString = shortDateString,
                    stats = stats,
                    tint = tint,
                )
            }
        }
    }
}

/** Opens the device battery / system power usage settings */
private fun launchBatterySettings(context: Context) {
    val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/** Opens the device storage settings */
private fun launchStorageSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        Intent(Settings.ACTION_STORAGE_VOLUME_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
}

/** Opens the device apps / memory settings */
private fun launchMemorySettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
}

/** Attempts to launch the device clock or alarms activity. */
private fun launchClockApp(context: Context) {
    val candidates = listOf(
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        Intent(AlarmClock.ACTION_SET_ALARM),
    )
    for (intent in candidates) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
    val clockPkgs = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
    )
    for (pkg in clockPkgs) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return
            } catch (_: Exception) {}
        }
    }
}

/** Attempts to launch the device calendar activity. */
private fun launchCalendarApp(context: Context) {
    val candidates = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
        Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time")),
    )
    for (intent in candidates) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
}
