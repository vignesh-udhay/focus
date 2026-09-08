package com.vignesh.focuslist.ui.component

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.Recurrence
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
        // A day that has already passed. The date text already says so on its
        // own; the colour is the second cue on top of it.
        isOverdue = task.scheduledDate?.isBefore(today) == true && !task.isCompleted
    )
}

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

    // Omitted where a section heading already names the day. Upcoming groups by
    // date, so repeating it on every row would say the same thing twice and
    // spend width the duration and the project need.
    if (showDate) {
        task.scheduledDate?.let { segments += scheduledDateLabel(it, today) }
    }

    task.estimatedDurationMinutes?.let { minutes ->
        segments += stringResource(R.string.task_duration_minutes, minutes)
    }

    // Last, because it says something about the task's future rather than
    // about the occurrence in front of the user. It earns a place at all
    // because completing a repeating task does something a one-off does not,
    // and the row is the only warning before the tap.
    task.recurrence?.let { segments += stringResource(it.labelRes) }

    return segments
}

private val Recurrence.labelRes: Int
    get() = when (this) {
        Recurrence.DAILY -> R.string.task_recurrence_daily
        Recurrence.WEEKLY -> R.string.task_recurrence_weekly
        Recurrence.MONTHLY -> R.string.task_recurrence_monthly
        Recurrence.YEARLY -> R.string.task_recurrence_yearly
    }
