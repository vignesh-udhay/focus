package app.tellcat.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.ui.component.rememberTimeFormat
import app.tellcat.ui.component.scheduledDateLabel
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The alarm screen, as `system/Alarm full` draws it, in 1.0's colours (D-091).
 *
 * **The reminder's time, not the clock's.** Todoist's full screen showed 7:10
 * for a 7:08 task, which tells the person nothing about what they asked for.
 * The day is named under it, so an alarm snoozed past midnight still says so.
 *
 * **Two answers: a tap that snoozes and a slide that stops.** Snooze 10 min is
 * the button above; Stop is the slide, so a half-asleep tap never stops it.
 * Nothing completes the task. D-102 as amended by D-130, replacing an 800ms
 * hold that the owner and a tester found slow on real alarms. See [AlarmSlide].
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

            // The tap above the slide, never touching it: `md` between them at
            // every font scale, since the button grows rather than truncating
            // and the column stacks it, so neither target can overlap the other.
            // One width and one 72dp floor for both, so they read as a pair.
            // The snooze is tonal, the softer answer in the softer colour.
            Column(
                modifier = Modifier.widthIn(max = TellcatDimensions.FocusColumnWidth),
                verticalArrangement = Arrangement.spacedBy(TellcatSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilledTonalButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TellcatDimensions.AlarmSlideHeight)
                ) {
                    Text(
                        text = stringResource(R.string.alarm_snooze),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
                AlarmSlide(
                    label = stringResource(R.string.alarm_slide_to_stop),
                    stopLabel = stringResource(R.string.alarm_stop),
                    onStop = onStop
                )
            }
        }
    }
}

private const val TitleMaxLines = 3
private const val NotesMaxLines = 3
