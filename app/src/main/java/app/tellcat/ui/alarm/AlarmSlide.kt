package app.tellcat.ui.alarm

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatHaptics
import app.tellcat.core.design.TellcatMotion
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.design.tellcatMotionEnabled
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The alarm's slide, which always means Stop. `docs/decisions.md` D-102, amended
 * by D-130.
 *
 * **One direction, one meaning.** The handle starts at the left and is slid
 * right; "Slide to stop" sits centred in the track. It only ever stops, so the
 * gesture a half-asleep hand learns always stops the alarm. The other answer,
 * Snooze, is a plain tap button above it: a snooze rings again, so it is the
 * forgiving one, and Stop, which cannot be undone, keeps the deliberate gesture.
 *
 * **It acts only past [ActThreshold] of the way, decided by position alone.**
 * Let go before that and the handle springs back on `reveal`, or snaps back
 * under reduced motion. A tap does nothing. Built on `draggable` rather than
 * `anchoredDraggable`: the latter settles a fling by its velocity, so a quick
 * flick would stop the alarm long before the threshold, which is exactly the
 * half-asleep gesture this exists to refuse.
 *
 * **What is about to happen stays readable.** The fill grows behind the handle,
 * and once past the threshold the word Stop shows in it, with one haptic as the
 * threshold is crossed.
 *
 * **TalkBack** gets one node with Stop as a custom action, answered at once.
 * **Large text**: where the label no longer fits beside the handle, it moves to
 * a line above the track rather than being clipped.
 */
@Composable
fun AlarmSlide(
    label: String,
    stopLabel: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val motionOn = tellcatMotionEnabled()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val springBack = TellcatMotion.reveal<Float>()

    // Pixels from the handle's start, 0 at rest to [travel] at the far end.
    var position by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }

    fun settle() {
        if (settling) return
        settling = true
        scope.launch {
            // Read here, from where the handle is now, not from the last frame.
            val reached = (position / travel).coerceIn(0f, 1f) >= ActThreshold
            val target = if (reached) travel else 0f
            if (motionOn) {
                animate(position, target, animationSpec = springBack) { value, _ -> position = value }
            } else {
                position = target
            }
            if (reached) onStop()
            // Back to the start after acting, so a screen that stays can be
            // answered again.
            position = 0f
            armed = false
            settling = false
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (settling) return@rememberDraggableState
        position = (position + delta).coerceIn(0f, travel)
        val nowArmed = position / travel >= ActThreshold
        if (nowArmed && !armed) TellcatHaptics.alarmSlideThreshold(haptics)
        armed = nowArmed
    }

    val fraction = (position / travel).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val lit = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = FillAlpha)
    // The handle is quiet at rest and lights as it nears the end.
    val handleColor = lerp(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primary, fraction)
    val onHandle = lerp(MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onPrimary, fraction)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val handleSize = TellcatDimensions.AlarmSlideHeight - TellcatSpacing.sm
        val inset = TellcatSpacing.sm / 2
        val widthPx = with(density) { maxWidth.toPx() }
        val handlePx = with(density) { handleSize.toPx() }
        val insetPx = with(density) { inset.toPx() }
        val measuredTravel = (widthPx - handlePx - 2 * insetPx).coerceAtLeast(1f)
        SideEffect { travel = measuredTravel }

        // Whether the label, centred on the whole track, still clears the
        // handle at rest. The same room is kept at the far end so it stays
        // centred.
        val room = widthPx - (handlePx + with(density) { (inset + TellcatSpacing.lg).toPx() }) * 2
        val labelInside = measurer.measure(label, labelStyle).size.width <= room

        Column(verticalArrangement = Arrangement.spacedBy(TellcatSpacing.xs)) {
            if (!labelInside) {
                Text(
                    text = label,
                    style = labelStyle,
                    color = quiet,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TellcatDimensions.AlarmSlideHeight)
                    .clip(CircleShape)
                    .background(track)
                    // The fill grows from the start to the handle, so how far it
                    // has gone reads at a glance.
                    .drawBehind {
                        val reach = insetPx + handlePx + position
                        if (position > 0f) {
                            drawRoundRect(
                                color = fill,
                                size = Size(reach + insetPx, size.height),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                        }
                    }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { settle() }
                    )
                    .clearAndSetSemantics {
                        contentDescription = label
                        customActions = listOf(CustomAccessibilityAction(stopLabel) { onStop(); true })
                    }
            ) {
                if (labelInside) {
                    Text(
                        text = label,
                        style = labelStyle,
                        color = lerp(quiet, quiet.copy(alpha = 0f), fraction),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = handleSize + inset)
                    )
                }

                // Past the threshold the word shows in the fill, behind the
                // handle that is about to act.
                if (armed) {
                    val wordWidth = measurer.measure(stopLabel, labelStyle).size.width
                    val gap = with(density) { TellcatSpacing.sm.toPx() }
                    Text(
                        text = stopLabel,
                        style = labelStyle,
                        color = lit,
                        modifier = Modifier.offset {
                            IntOffset((insetPx + position - gap - wordWidth).roundToInt().coerceAtLeast(0), 0)
                        }
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset((insetPx + position).roundToInt(), 0) }
                        .size(handleSize)
                        .clip(CircleShape)
                        .background(handleColor)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_forward),
                        contentDescription = null,
                        tint = onHandle
                    )
                }
            }
        }
    }
}

/** How far along the handle must be when let go for the alarm to stop. */
internal const val ActThreshold = 0.7f

/** How strongly the fill shows over the track. */
private const val FillAlpha = 0.16f
