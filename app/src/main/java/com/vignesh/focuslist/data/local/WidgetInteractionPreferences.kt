package com.vignesh.focuslist.data.local

import android.content.Context
import androidx.core.content.edit
import com.vignesh.focuslist.core.domain.Task
import java.time.LocalDate

data class WidgetCompletion(
    val taskId: String,
    val previousIndex: Int
)

/** The one transient interaction the widget must keep visible: completion. */
class WidgetInteractionPreferences(context: Context) {

    private val preferences = context.getSharedPreferences(Name, Context.MODE_PRIVATE)

    fun recordCompletion(taskId: String, previousIndex: Int, tasks: List<Task>, today: LocalDate) {
        preferences.edit {
            putString(TaskIdKey, taskId)
            putInt(IndexKey, previousIndex)
            putInt(SnapshotKey, widgetSnapshotHash(tasks, today))
        }
    }

    /**
     * Returns the completion only while storage still matches the interaction.
     * The first later task or day change is the next refresh and retires it.
     */
    fun completionFor(tasks: List<Task>, today: LocalDate): WidgetCompletion? {
        val taskId = preferences.getString(TaskIdKey, null) ?: return null
        val matches = preferences.getInt(SnapshotKey, Int.MIN_VALUE) ==
            widgetSnapshotHash(tasks, today)
        val taskStillCompleted = tasks.any { task ->
            task.id == taskId && task.isCompleted && !task.isDeleted
        }

        if (!matches || !taskStillCompleted) {
            clearCompletion()
            return null
        }

        return WidgetCompletion(
            taskId = taskId,
            previousIndex = preferences.getInt(IndexKey, 0)
        )
    }

    fun clearCompletion() {
        preferences.edit { clear() }
    }

    private companion object {
        const val Name = "widget_interaction"
        const val TaskIdKey = "task_id"
        const val IndexKey = "previous_index"
        const val SnapshotKey = "snapshot"
    }
}

/** Stable enough to tell the interaction snapshot from the next data change. */
internal fun widgetSnapshotHash(tasks: List<Task>, today: LocalDate): Int =
    31 * tasks.hashCode() + today.hashCode()
