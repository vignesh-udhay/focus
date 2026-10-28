package app.tellcat.ui.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.ui.component.rememberTimeFormat
import app.tellcat.ui.component.scheduledDateLabel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The alarm screen, as `system/Alarm full` draws it, in 1.0's colours (D-091).
 *
 * **The reminder's time, not the clock's.** Todoist's full screen showed 7:10
 * for a 7:08 task, which tells the person nothing about what they asked for.
 * The day is named under it, so an alarm snoozed past midnight still says so.
 *
 * **Two answers, held, never tapped.** Snooze 10 min and Stop, and nothing that
 * completes the task (R-020). Both are press-and-hold: a half-asleep tap does
 * nothing, and the fill across the control shows how long is left. TalkBack's
 * double tap answers at once, because that gesture is already deliberate.
 */
@Composable
fun AlarmScreen(
    title: String,
    notes: String?,
    ringsAt: LocalDateTime,
    today: LocalDate,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = TellcatSpacing.lg, vertical = TellcatSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.alarm_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = ringsAt.toLocalTime().format(rememberTimeFormat()),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = TellcatSpacing.xs)
            )

            Text(
                text = scheduledDateLabel(ringsAt.toLocalDate(), today),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = TitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )

            notes?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = NotesMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = TellcatSpacing.xs)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.widthIn(max = TellcatDimensions.FocusColumnWidth),
                verticalArrangement = Arrangement.spacedBy(TellcatSpacing.sm)
            ) {
                HoldToAnswer(
                    label = stringResource(R.string.alarm_snooze),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    onAnswered = onSnooze
                )
                HoldToAnswer(
                    label = stringResource(R.string.alarm_stop),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onAnswered = onStop
                )
            }
        }
    }
}

/**
 * A control answered by holding it for [HoldMillis].
 *
 * The fill runs across the control for as long as it is held, linearly,
 * because it measures time rather than expressing a state change, and snaps
 * back if the finger lifts early. `expressive-motion.md` lists it.
 */
@Composable
private fun HoldToAnswer(
    label: String,
    container: Color,
    content: Color,
    onAnswered: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hint = stringResource(R.string.alarm_hold_hint)
    val fill = content.copy(alpha = HoldFillAlpha)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TellcatDimensions.ActionHeight)
            .clip(CircleShape)
            .background(container)
            .drawBehind {
                drawRect(color = fill, size = size.copy(width = size.width * progress.value))
            }
            .pointerInput(onAnswered) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                    val hold = scope.launch {
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = HoldMillis, easing = LinearEasing)
                        )
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnswered()
                    }

                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        hold.cancel()
                        scope.launch { progress.snapTo(0f) }
                    }
                }
            }
            // One node for TalkBack: the label, a button, and the answer on
            // double tap.
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) {
                    onAnswered()
                    true
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = content)
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

/** Long enough that a tap cannot do it, short enough not to feel like a test. */
private const val HoldMillis = 800

/** How strongly the fill shows over the control's own colour. */
private const val HoldFillAlpha = 0.24f

private const val TitleMaxLines = 3
private const val NotesMaxLines = 3
