package com.vignesh.focuslist.ui.component

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.text.recurrenceSummary
import com.vignesh.focuslist.core.domain.Task
import java.time.LocalDate

/**
 * A task in a list.
 *
 * Wraps [TaskRow] with the one thing every task collection needs on top of it:
 * metadata derived from the task's own fields. Screens differ in what they show
 * and how they order it, not in how a single row behaves.
 *
 * **It has no actions of its own.** `docs/decisions.md` D-023 removed the
 * trailing button and the long-press menu that used to carry Delete, Focus and
 * three reschedule actions. Tapping the row opens Task Details, and everything
 * the menu offered lives there: rescheduling through the Plan rows, Start focus
 * as the primary action, Delete in the overflow.
 *
 * The menu existed because Task Details excluded those actions, which D-018,
 * D-022 and the Plan rows each stopped being true without anyone revisiting the
 * button. It had also become a subset of the Scheduled sheet that could not
 * clear a date.
 *
 * What it costs is a tap: rescheduling from a list is three rather than two, and
 * rows no longer answer a long press. Nothing became unreachable. D-023 records
 * the trade and what would reverse it.
 *
 * @param today the date metadata is phrased against. Passed in rather than read
 * from the clock so the row stays deterministic.
 * @param showDate whether the metadata line names the scheduled day. False on a
 * list whose section headings already do, so the row does not repeat it.
 */
@Composable
internal fun TaskListRow(
    task: Task,
    today: LocalDate,
    shapes: ListItemShapes,
    colors: ListItemColors,
    onToggleComplete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true
) {
    val metadata = taskMetadata(task = task, today = today, showDate = showDate)

    TaskRow(
        title = task.title,
        isCompleted = task.isCompleted,
        shapes = shapes,
        onToggleComplete = { onToggleComplete() },
        onClick = onOpen,
        onClickLabel = stringResource(R.string.task_open),
        modifier = modifier,
        colors = colors,
        metadata = metadata.segments,
        metadataSpoken = metadata.spoken,
        // **The estimate sits at the end of the row, not in the metadata line.**
        // Date and repeat both answer *when*; a duration answers *how much*, and
        // folding a number into a comma-list of words means reading a sentence
        // to get it out again. In its own right-aligned column the estimates
        // form a scannable strip, which is how "I have twenty minutes, what
        // fits" gets answered, and that is `PRODUCT.md` principle 2's question
        // almost word for word.
        //
        // Absent on most tasks, and that costs nothing: an estimate is optional
        // per principle 3, and an empty trailing slot simply leaves the title
        // more room. It also shortens a supporting line that was carrying up to
        // three segments and their separators.
        trailingContent = task.estimatedDurationMinutes?.let { minutes ->
            {
                // Through `durationLabel`, so the row reads 45m and 1h 30m like
                // Task Details and the Duration sheet. It used to format its own
                // `%1$d min` and was the one place in the app wording a duration
                // differently, which is the thing that helper exists to stop:
                // a 90 minute task read "90 min" here and "1h 30m" everywhere.
                val label = durationLabel(minutes)
                Text(
                    text = label.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = label.spoken }
                )
            }
        },
        // A day that has already passed. The date text already says so on its
        // own; the colour is the second cue on top of it.
        //
        // Either day counts, per D-059: a deadline that has gone by is late
        // whether or not the task was also planned for a past day. Which list
        // the task sits in does not move, because no band or query reads a due
        // date.
        isOverdue = task.isPastScheduled(today) || task.isPastDue(today)
    )
}

/**
 * Whether the day the task was planned for has gone by.
 *
 * Kept apart from [isPastDue] rather than folded into one flag, and D-059 says
 * why: this one also suppresses the reminder time, and a task that is merely
 * late still has a reminder that has not fired.
 */
private fun Task.isPastScheduled(today: LocalDate): Boolean =
    scheduledDate?.isBefore(today) == true && !isCompleted

/** Whether the day the task was owed has gone by. */
private fun Task.isPastDue(today: LocalDate): Boolean =
    dueDate?.isBefore(today) == true && !isCompleted

/**
 * Turns the domain fields of a [Task] into the row's display metadata.
 *
 * This lives in the UI layer on purpose. [Task] carries dates and durations;
 * deciding that a date reads as "Today" is a presentation concern.
 */
@Composable
private fun taskMetadata(
    task: Task,
    today: LocalDate,
    showDate: Boolean
): RowMetadata {
    val segments = mutableListOf<String>()
    // The same line as TalkBack should say it. Only the reminder differs: read
    // aloud, "Tomorrow, 5:00 PM" beside a scheduled "Today" does not say which
    // of the two will interrupt you.
    val spoken = mutableListOf<String>()

    // **First, because it is the one that will interrupt you.** `PRODUCT.md`
    // principle 1 makes the reminder the product, and `TodayBand.LATER_TODAY`
    // promises a task there "will announce itself" without saying when. The row
    // is where that gets answered.
    //
    // Not first on an overdue task, where the date leads because being overdue
    // is the point and `metadataText` colours the first segment. A reminder
    // that fired on that past day is gone by the rule below: "6:00 PM" on a row
    // from last Tuesday describes nothing that is going to happen.
    //
    // **And it names its day when that is not the task's day.** D-075. The time
    // alone was written for Today, where the reminder is today's. On an Inbox
    // task "5:00 PM" could be any day, and under a Friday heading a Wednesday
    // reminder read as Friday's. A reminder whose own day is over is dropped,
    // which is the overdue rule above applied to the reminder's day: an Inbox
    // task has no scheduled day to pass, and went on showing a rung reminder
    // as though it were still to come. This morning's stays until tonight,
    // because it is plainly past and Today's band is explained by it.
    //
    // **An overdue task still shows a reminder that has not rung.** Moved to
    // tomorrow while the task stayed on a day gone by, it used to vanish from
    // the row because the rule above was written for reminders that fired on
    // that day. It now follows the date, so the date stays first and keeps the
    // overdue colour, and it names its day because that is not the task's.
    val reminder = task.ringsAt?.takeUnless { at -> at.toLocalDate().isBefore(today) }?.let { at ->
        val time = at.toLocalTime().format(rememberTimeFormat())
        if (at.toLocalDate() == task.scheduledDate) {
            time
        } else {
            stringResource(
                R.string.task_row_reminder_on_day,
                scheduledDateLabel(at.toLocalDate(), today),
                time
            )
        }
    }
    val reminderSpoken = reminder?.let { stringResource(R.string.task_row_reminder_spoken, it) }
    val overdue = task.isPastScheduled(today)

    if (!overdue && reminder != null) {
        segments += reminder
        spoken += reminderSpoken!!
    }

    // Omitted where a section heading already names the day. Upcoming groups by
    // date and Today by band, so repeating it on every row would say the same
    // thing twice.
    if (showDate) {
        task.scheduledDate?.let { date ->
            val label = scheduledDateLabel(date, today)
            segments += label
            spoken += label
        }
    }

    if (overdue && reminder != null) {
        segments += reminder
        spoken += reminderSpoken!!
    }

    // **After the scheduled date, because it answers the next question.** The
    // line reads when the task will announce itself, then when it is planned,
    // then when it is owed. `docs/decisions.md` D-059: Task Details could set a
    // due date and no list ever showed it again, so a user could record a
    // deadline and never see it a second time.
    //
    // Never omitted with `showDate`. A section heading names the day the task is
    // *scheduled* for, which is a different day and a different claim, so there
    // is nothing here for a heading to be repeating.
    task.dueDate?.let { due ->
        val label = dueDateLabel(date = due, today = today, isCompleted = task.isCompleted)
        segments += label
        spoken += label
    }

    // Last, because it says something about the task's future rather than
    // about the occurrence in front of the user. It earns a place at all
    // because completing a repeating task does something a one-off does not,
    // and the row is the only warning before the tap.
    task.recurrence?.let { rule ->
        val label = recurrenceSummary(rule, today)
        segments += label
        spoken += label
    }

    return RowMetadata(segments = segments, spoken = spoken.joinToString(", "))
}

/** The row's second line as drawn, and as TalkBack should read it. */
private data class RowMetadata(val segments: List<String>, val spoken: String)

