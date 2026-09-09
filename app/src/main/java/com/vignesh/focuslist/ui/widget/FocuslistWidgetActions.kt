package com.vignesh.focuslist.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.vignesh.focuslist.FocuslistApplication
import com.vignesh.focuslist.core.domain.TaskCompletion
import kotlinx.coroutines.flow.first

internal val WidgetTaskIdKey = ActionParameters.Key<String>("task_id")

class CompleteWidgetTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[WidgetTaskIdKey] ?: return
        val application = context.applicationContext as FocuslistApplication
        val before = application.taskRepository.observeTasks().first()
        val task = before.firstOrNull { it.id == taskId && !it.isCompleted && !it.isDeleted }
            ?: return
        val today = application.currentDay.today.value

        TaskCompletion(application.taskRepository, application.currentDay).complete(task.id)

        // No index is recorded. D-049 draws the just-completed task as though it
        // were still outstanding, which puts it back in its own band in its own
        // place, so there is nothing to remember about where it was.
        val after = application.taskRepository.observeTasks().first()
        application.widgetInteractions.recordCompletion(
            taskId = task.id,
            tasks = after,
            today = today
        )
        FocuslistWidget().updateAll(context)
    }
}

class UndoWidgetTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[WidgetTaskIdKey] ?: return
        val application = context.applicationContext as FocuslistApplication

        TaskCompletion(application.taskRepository, application.currentDay).reopen(taskId)
        application.widgetInteractions.clearCompletion()
        FocuslistWidget().updateAll(context)
    }
}
