package com.vignesh.focuslist.ui.today

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.FocusNow
import com.vignesh.focuslist.core.domain.FocusNowReason
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.ui.component.durationLabel
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Focus now card, from `docs/decisions.md` D-012 as amended by D-035.
 *
 * `PRODUCT.md` opens Today with "What should I do now?" and asks that the screen
 * make the answer obvious within seconds. This is the first thing in the app
 * that answers it.
 *
 * Four things, and nothing else: the label saying what the card is, a checkbox
 * so the task can be finished from here, the task, and one line saying **why
 * this task**. The reason line is the whole difference between this and the
 * Focus queue D-004 removed. The queue was a ranking with nothing to say why its
 * head was its head; this states its grounds on screen, and completing its task
 * re-runs the rule rather than advancing to a next one.
 *
 * **It appears for an event, so on many days it does not appear at all.** D-035
 * cut the reason that fired for any task scheduled today without a time, which
 * was most of them, on the grounds that its reason line restated the label of
 * the band directly below it. What is left is a paused session or a reminder
 * that passed. The screen has to read well without this card, because most of
 * the time there will not be one.
 *
 * It sits on `primaryContainer`, which is the one tinted surface on Today. That
 * is what makes it read as an assertion rather than as another row: everything
 * below it is the list, and this is the app saying something about the list.
 */
@Composable
fun FocusNowCard(
    focusNow: FocusNow,
    today: LocalDate,
    onToggleComplete: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    // What is left of a paused session, for the one reason that has a session
    // behind it. Null for a passed reminder, which has nothing running.
    pausedRemainingMinutes: Long? = null
) {
    val toggleDescription =
        stringResource(R.string.task_row_mark_complete, focusNow.task.title)
    val openDescription = stringResource(R.string.task_open)

    Card(
        // **The card opens the task, and it used to not.** The comment here read
        // "the card is not itself a button ... a clickable container behind two
        // controls is a third target the user cannot see the edges of", which is
        // a fair worry and answers the wrong question.
        //
        // D-012 takes the promoted task out of the bands below, so it is on this
        // screen exactly once. A card that could not be opened made that one
        // task's details unreachable from Today altogether: not in a band, not
        // in Upcoming, which holds later days, not in Inbox, which holds undated
        // work. The task the screen is built around was the only one that could
        // not be edited without first completing or rescheduling it.
        //
        // The overlapping-target worry is answered by the arrangement every task
        // row already uses: the children take their own clicks and the body
        // takes the rest. This is that, with a button on the end of it.
        onClick = onOpen,
        // **The same corner the bands below round to.** `CardDefaults.shape` is
        // `medium`, and `ListItemDefaults.segmentedShapes` rounds a band's outer
        // corners to `large`, so the card sat at 12dp directly above rows at
        // 16dp, on the same left edge and the same inset. Measured on a Pixel at
        // 420dpi: 31.4px against 41.4px.
        //
        // `expressive-design-system.md` says shape "communicates component
        // identity", not hierarchy, and by that rule a card and a list item are
        // allowed to differ. They are not allowed to differ here: D-012 takes
        // this task out of the bands below so it is on Today exactly once, which
        // makes the card a promoted task rather than a different kind of thing.
        // Two shapes for one identity is the drift the rule exists to stop.
        //
        // Named as the token rather than a number, so this and the rows move
        // together if the scale ever changes. That is what let them drift apart
        // in the first place: neither had chosen a corner, so each inherited a
        // different default.
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            // Named, because "what should I do now" answered by an unlabelled
            // clickable card tells a screen reader nothing about what tapping it
            // does. The same string the task rows use, since it is the same act.
            .semantics { onClick(label = openDescription, action = null) }
    ) {
        Column(modifier = Modifier.padding(FocuslistSpacing.md)) {
            Text(
                // Sentence case, not letterspaced capitals. `expressive-components.md`
                // makes this the rule for every label in the app, on the grounds
                // that shouting a word the user is not reading is the thing worth
                // losing.
                text = stringResource(R.string.today_focus_now),
                style = MaterialTheme.typography.labelSmallEmphasized
            )

            Row(
                modifier = Modifier.padding(top = FocuslistSpacing.xs),
                verticalAlignment = Alignment.Top
            ) {
                // The same control the rows carry, including its 48dp target
                // and its own description, because completing from the card has
                // to be the same act as completing from the list. The card only
                // ever holds an outstanding task, so it is never checked.
                Checkbox(
                    checked = false,
                    onCheckedChange = { onToggleComplete() },
                    modifier = Modifier
                        .sizeIn(
                            minWidth = FocuslistDimensions.TouchTargetMin,
                            minHeight = FocuslistDimensions.TouchTargetMin
                        )
                        .semantics { contentDescription = toggleDescription }
                )

                Column(modifier = Modifier.padding(start = FocuslistSpacing.xs)) {
                    Text(
                        text = focusNow.task.title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        // The card is an assertion about one task, so the task
                        // is the heading a screen reader should land on.
                        modifier = Modifier.semantics { heading() }
                    )

                    Text(
                        text = focusNowReasonText(focusNow, today, pausedRemainingMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = FocuslistSpacing.xxs)
                    )
                }
            }

            Button(
                onClick = onOpenFocus,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.padding(top = FocuslistSpacing.sm)
            ) {
                Text(
                    text = stringResource(
                        // Labelled for the state it opens, which is what stops
                        // the user pressing Start focus twice. A paused session
                        // resumes; anything else lands on Ready, where Start
                        // focus is the control.
                        if (focusNow.reason == FocusNowReason.ResumePaused) {
                            R.string.today_focus_now_resume
                        } else {
                            R.string.today_focus_now_focus
                        }
                    )
                )
            }
        }
    }
}

/**
 * The reason line, which is the card's justification for itself.
 *
 * One of the two reasons D-035 left standing, in words, with the estimate on the
 * end of the same line. The estimate moved here from its own row in the design
 * review: it is the same kind of fact as the reason, a thing known about the
 * task, and a row of its own gave it a weight it does not carry.
 *
 * Never a colour or an icon alone. A screen reader cannot announce either, and
 * an assertion the user cannot check is one they cannot disagree with.
 */
@Composable
private fun focusNowReasonText(
    focusNow: FocusNow,
    today: LocalDate,
    pausedRemainingMinutes: Long?
): String {
    val task = focusNow.task

    // A paused session names what is left rather than the estimate, and the
    // difference is the point: a session paused two thirds of the way through a
    // forty-five minute task has fifteen minutes left, and saying "45m" would
    // overstate the work by three times. It is also the number someone deciding
    // whether to resume is actually asking about.
    if (focusNow.reason == FocusNowReason.ResumePaused) {
        val left = pausedRemainingMinutes
            // Rounded up, for the same reason Focus's own status line is: nought
            // minutes left on a clock that has not run out is a lie the floor
            // would tell through the whole of the last minute.
            ?.let { minutes -> (minutes + 1L).toInt() }
            ?: return stringResource(R.string.today_focus_now_paused)

        return stringResource(R.string.today_focus_now_paused_remaining, left)
    }

    val reason = when (focusNow.reason) {
        // Unreachable: the paused case returned above. Kept so the `when` stays
        // exhaustive over the enum rather than needing an else.
        FocusNowReason.ResumePaused -> stringResource(R.string.today_focus_now_paused)

        FocusNowReason.ReminderPassed -> {
            val at = task.reminderAt
            if (at == null) {
                // Not reachable: the reason is only assigned to a task with a
                // reminder. Named rather than forced, because a crash on the
                // screen the app opens to is the worst place for one. "Due now"
                // rather than a time, since a time is exactly what is missing
                // here, and it stays true of every path that could reach it.
                stringResource(R.string.today_focus_now_due)
            } else {
                stringResource(
                    if (at.toLocalDate() == today) {
                        R.string.today_focus_now_scheduled_at
                    } else {
                        // A reminder that passed on an earlier day says so, or
                        // "Scheduled for 9:00 AM" would read as this morning.
                        R.string.today_focus_now_was_due_at
                    },
                    at.format(rememberTimeFormat())
                )
            }
        }
    }

    val minutes = task.estimatedDurationMinutes ?: return reason

    return stringResource(R.string.today_focus_now_reason_and_estimate, reason, durationLabel(minutes).text)
}

/**
 * The reminder time, in the reader's own locale.
 *
 * `SHORT` rather than a pattern, so a device set to 24-hour time gets 09:00 and
 * one set to 12-hour gets 9:00 AM. Hardcoding either would be wrong somewhere.
 */
@Composable
private fun rememberTimeFormat(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private val SampleTask = Task(
    id = "sample",
    title = "Refine landing page hero",
    createdAt = SampleTimestamp,
    scheduledDate = LocalDate.of(2026, 1, 1),
    estimatedDurationMinutes = 30
)

@Preview(name = "Focus now paused", showBackground = true)
@Preview(
    name = "Focus now paused dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun FocusNowPausedPreview() {
    FocuslistTheme(dynamicColor = false) {
        FocusNowCard(
            focusNow = FocusNow(SampleTask, FocusNowReason.ResumePaused),
            today = LocalDate.of(2026, 1, 1),
            onToggleComplete = {},
            onOpenFocus = {},
            onOpen = {},
            modifier = Modifier.padding(FocuslistSpacing.md),
            pausedRemainingMinutes = 17
        )
    }
}

@Preview(name = "Focus now reminder passed", showBackground = true)
@Composable
private fun FocusNowReminderPreview() {
    FocuslistTheme(dynamicColor = false) {
        FocusNowCard(
            focusNow = FocusNow(
                SampleTask.copy(
                    title = "Review client sync notes",
                    reminderAt = LocalDateTime.of(2026, 1, 1, 9, 0)
                ),
                FocusNowReason.ReminderPassed
            ),
            today = LocalDate.of(2026, 1, 1),
            onToggleComplete = {},
            onOpenFocus = {},
            onOpen = {},
            modifier = Modifier.padding(FocuslistSpacing.md)
        )
    }
}

/**
 * A long title, and the same card at twice the font scale.
 *
 * It used to preview `NoTimeToday`, which D-035 removed. The reason it exists is
 * the title length rather than the reason, so it moved to a reminder that passed
 * on an earlier day: the longest reason line the card can draw, under the longest
 * title, which is where this card wraps if it is going to.
 */
@Preview(name = "Focus now long title", showBackground = true)
@Preview(name = "Focus now long title large font", showBackground = true, fontScale = 2f)
@Composable
private fun FocusNowLongTitlePreview() {
    FocuslistTheme(dynamicColor = false) {
        FocusNowCard(
            focusNow = FocusNow(
                SampleTask.copy(
                    title = "Prepare launch checklist for the release, including the " +
                        "changelog and the store screenshots",
                    scheduledDate = LocalDate.of(2025, 12, 31),
                    reminderAt = LocalDateTime.of(2025, 12, 31, 17, 30),
                    estimatedDurationMinutes = 35
                ),
                FocusNowReason.ReminderPassed
            ),
            today = LocalDate.of(2026, 1, 1),
            onToggleComplete = {},
            onOpenFocus = {},
            onOpen = {},
            modifier = Modifier.padding(FocuslistSpacing.md)
        )
    }
}
