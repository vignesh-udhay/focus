package com.vignesh.focuslist.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vignesh.focuslist.MainActivity

/** A home-widget target, kept explicit instead of smuggling navigation strings. */
sealed interface WidgetLaunchCommand {
    data object Today : WidgetLaunchCommand
    data object Add : WidgetLaunchCommand
    data class TaskDetails(val taskId: String) : WidgetLaunchCommand
    data class ResumeFocus(val taskId: String) : WidgetLaunchCommand
}

internal fun widgetLaunchIntent(context: Context, command: WidgetLaunchCommand): Intent {
    val (kind, taskId) = when (command) {
        WidgetLaunchCommand.Today -> "today" to null
        WidgetLaunchCommand.Add -> "add" to null
        is WidgetLaunchCommand.TaskDetails -> "task" to command.taskId
        is WidgetLaunchCommand.ResumeFocus -> "resume" to command.taskId
    }

    return Intent(context, MainActivity::class.java).apply {
        action = Action
        data = Uri.Builder()
            .scheme("focuslist")
            .authority("widget")
            .appendPath(kind)
            .apply { taskId?.let(::appendPath) }
            .build()
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

fun Intent.widgetLaunchCommand(): WidgetLaunchCommand? {
    if (action != Action || data?.authority != "widget") return null

    val parts = data?.pathSegments.orEmpty()
    return when (parts.firstOrNull()) {
        "today" -> WidgetLaunchCommand.Today
        "add" -> WidgetLaunchCommand.Add
        "task" -> parts.getOrNull(1)?.let(WidgetLaunchCommand::TaskDetails)
        "resume" -> parts.getOrNull(1)?.let(WidgetLaunchCommand::ResumeFocus)
        else -> null
    }
}

private const val Action = "com.vignesh.focuslist.action.OPEN_FROM_WIDGET"
