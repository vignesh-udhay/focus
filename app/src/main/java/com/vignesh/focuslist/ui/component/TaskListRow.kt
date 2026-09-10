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
    TaskRow(
        title = task.title,
        isCompleted = task.isCompleted,
        shapes = shapes,
        onToggleComplete = { onToggleComplete() },
        onClick = onOpen,
        onClickLabel = stringResource(R.string.task_open),
        modifier = modifier,
        colors = colors,
        metadata = taskMetadata(task = task, today = today, showDate = showDate),
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
): List<String> {
    val segments = mutableListOf<String>()

    // **First, because it is the one that will interrupt you.** `PRODUCT.md`
    // principle 1 makes the reminder the product, and `TodayBand.LATER_TODAY`
    // promises a task there "will announce itself" without saying when. The row
    // is where that gets answered.
    //
    // Suppressed on an overdue task, whose reminder has already fired: "6:00 PM"
    // on a row from last Tuesday describes nothing that is going to happen. It
    // also keeps the date first on exactly the rows where being overdue is the
    // point, which is what `metadataText` colours.
    if (!task.isPastScheduled(today)) {
        task.reminderAt?.let { at ->
            segments += at.toLocalTime().format(rememberTimeFormat())
        }
    }

    // Omitted where a section heading already names the day. Upcoming groups by
    // date and Today by band, so repeating it on every row would say the same
    // thing twice.
    if (showDate) {
        task.scheduledDate?.let { segments += scheduledDateLabel(it, today) }
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
        segments += dueDateLabel(date = due, today = today, isCompleted = task.isCompleted)
    }

    // Last, because it says something about the task's future rather than
    // about the occurrence in front of the user. It earns a place at all
    // because completing a repeating task does something a one-off does not,
    // and the row is the only warning before the tap.
    task.recurrence?.let { segments += recurrenceSummary(it, today) }

    return segments
}

