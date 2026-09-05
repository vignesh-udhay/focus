package com.vignesh.focuslist.ui.task

import androidx.compose.runtime.Composable
import com.vignesh.focuslist.core.domain.Task
import java.time.LocalDate

/**
 * Opens Task Details for [openTaskId], if that task is still on this list.
 *
 * Every list opens the same sheet over the same fields, so the mapping from an
 * edited [Task] back to the view model lives here rather than five times over.
 *
 * The screen tracks which row is open by id, and the task itself is read back
 * out of [tasks], so the sheet always shows what the repository currently
 * holds. It also means a task edited off this list, or deleted from under the
 * sheet, simply stops being found and the sheet closes with it. Deleted tasks
 * appear on no list, so none of them can be opened here.
 *
 * [today] is threaded through because the sheet's date fields accept typed
 * relative phrases. It is the same day the screens already hand to their rows.
 */
@Composable
fun TaskDetailsSheetHost(
    openTaskId: String?,
    tasks: List<Task>,
    today: LocalDate,
    viewModel: TaskListViewModel,
    onDismiss: () -> Unit
) {
    val task = tasks.firstOrNull { it.id == openTaskId } ?: return

    TaskDetailsSheet(
        task = task,
        // The day typed dates resolve against, from the app's one clock.
        today = today,
        onDismiss = onDismiss,
        onSave = { edited ->
            viewModel.editTask(
                id = edited.id,
                title = edited.title,
                // The sheet edits a copy of the real task, so a field it does
                // not change arrives here as whatever the task already held.
                notes = edited.notes,
                scheduledDate = edited.scheduledDate,
                dueDate = edited.dueDate,
                estimatedDurationMinutes = edited.estimatedDurationMinutes,
                recurrence = edited.recurrence,
                reminderAt = edited.reminderAt
            )
            onDismiss()
        }
    )
}
