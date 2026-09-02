package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.Task
import java.time.LocalDate

/**
 * A task in a list, with the actions menu its long press opens.
 *
 * Wraps [TaskRow] with the behaviour every task collection needs: metadata
 * derived from the task's own fields, and the actions a long press offers.
 * Screens differ in what they show and how they order it, not in how a single
 * row behaves.
 *
 * The menu is ordered by how often an action is wanted and how much it costs
 * to be wrong about. Moving the task to another day comes first, because
 * deciding a task is not for today is the most repeated decision a list asks
 * for and it should not cost a trip through Task Details. Delete comes last,
 * so it is never what the thumb lands on.
 *
 * Deletion is deliberate rather than a gesture away: long press, then choose
 * Delete. Recovery is the undo the caller offers, so a single stray gesture
 * should not be able to clear a task off the screen.
 *
 * Whether the menu is open, and whether the calendar is up, are transient
 * state of this one row. Nothing outside it needs to know, so they stay here
 * rather than in a view model.
 *
 * @param today the date metadata is phrased against, and the day the Today and
 * Tomorrow actions resolve to. Passed in rather than read from the clock so the
 * row stays deterministic.
 * @param onFocus starts Focus on this task. Null, the default, leaves the
 * action off the menu: the Focus queue is derived from Today, so only a Today
 * row can offer it. Anywhere else the action would either do nothing or have
 * to schedule the task for today, and neither is specified behaviour.
 * @param onReschedule moves this task to a day. Null, the default, leaves the
 * three move actions off the menu, which is what the Logbook wants: a
 * completed task is a record of when the work was done, and moving it would be
 * rewriting that rather than planning anything.
 */
@Composable
internal fun TaskListRow(
    task: Task,
    today: LocalDate,
    shapes: ListItemShapes,
    colors: ListItemColors,
    onToggleComplete: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
    onReschedule: ((LocalDate?) -> Unit)? = null
) {
    var areActionsVisible by remember { mutableStateOf(false) }
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }

    // The menu anchors to the row it acts on.
    Box(modifier = modifier) {
        TaskRow(
            title = task.title,
            isCompleted = task.isCompleted,
            shapes = shapes,
            onToggleComplete = { onToggleComplete() },
            onClick = onOpen,
            onLongClick = { areActionsVisible = true },
            onClickLabel = stringResource(R.string.task_open),
            onLongClickLabel = stringResource(R.string.task_actions),
            colors = colors,
            metadata = taskMetadata(task = task, today = today),
            // A day that has already passed. The date text already says so on
            // its own; the colour is the second cue on top of it.
            isOverdue = task.scheduledDate?.isBefore(today) == true && !task.isCompleted
        )

        DropdownMenu(
            expanded = areActionsVisible,
            onDismissRequest = { areActionsVisible = false }
        ) {
            if (onReschedule != null) {
                // A day the task is already on is left off rather than shown
                // and ignored. An action that visibly does nothing is worse
                // than one that is not there at all.
                if (task.scheduledDate != today) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_reschedule_today)) },
                        onClick = {
                            areActionsVisible = false
                            onReschedule(today)
                        }
                    )
                }

                val tomorrow = today.plusDays(1)
                if (task.scheduledDate != tomorrow) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_reschedule_tomorrow)) },
                        onClick = {
                            areActionsVisible = false
                            onReschedule(tomorrow)
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_reschedule_pick)) },
                    onClick = {
                        areActionsVisible = false
                        isPickerOpen = true
                    }
                )
            }

            if (onFocus != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_focus)) },
                    onClick = {
                        areActionsVisible = false
                        onFocus()
                    }
                )
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_delete)) },
                onClick = {
                    areActionsVisible = false
                    onDelete()
                }
            )
        }
    }

    if (isPickerOpen && onReschedule != null) {
        TaskDatePickerDialog(
            // Opens on the day the task is already on, so a small correction
            // starts from where the task is rather than from nothing.
            initialDate = task.scheduledDate,
            onDismiss = { isPickerOpen = false },
            onPicked = onReschedule
        )
    }
}

/**
 * Turns the domain fields of a [Task] into the row's display metadata.
 *
 * This lives in the UI layer on purpose. [Task] carries dates and durations;
 * deciding that a date reads as "Today" is a presentation concern.
 */
@Composable
private fun taskMetadata(task: Task, today: LocalDate): List<String> {
    val segments = mutableListOf<String>()

    task.scheduledDate?.let { segments += scheduledDateLabel(it, today) }

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
