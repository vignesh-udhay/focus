package app.tellcat.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tellcat.TellcatApplication
import app.tellcat.core.domain.SnoozeOption
import app.tellcat.core.domain.TaskCompletion
import app.tellcat.core.domain.snoozedUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * What the buttons on a reminder do.
 *
 * Done completes the task through the same [TaskCompletion] the checkbox in
 * the app uses, so a recurring task ticked off from the shade starts its next
 * occurrence exactly as it would have on screen. That shared path is the
 * reason completion moved out of the view model.
 *
 * Snooze is two taps rather than one, because a single snooze duration would
 * be a guess about which of them the user wanted. The first tap replaces the
 * reminder with the choice, the second sets the new time.
 *
 * Every branch ends with the notification gone. A reminder that stays on
 * screen after being dealt with is a reminder the user has to dismiss twice.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val application = context.applicationContext as? TellcatApplication ?: return
        val finish = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> {
                        TaskCompletion(application.taskRepository, application.currentDay)
                            .complete(taskId)
                        // Before the receiver finishes, so a recurring task's
                        // next reminder is set even if the process is frozen
                        // the moment this returns. D-078.
                        application.reconcileReminders()
                        context.cancelReminder(taskId)
                    }

                    ACTION_SNOOZE -> {
                        // Not a snooze yet. This only swaps the reminder for
                        // the choice, so the notification stays put.
                        val task = application.taskRepository.observeTasks().first()
                            .firstOrNull { it.id == taskId } ?: return@launch

                        context.postSnoozeOptions(task, LocalDateTime.now())
                    }

                    ACTION_SNOOZE_UNTIL -> {
                        val option = intent.getStringExtra(EXTRA_SNOOZE_OPTION)
                            ?.let { name -> SnoozeOption.entries.firstOrNull { it.name == name } }
                            ?: return@launch

                        // Null means the option expired between the
                        // notification being drawn and the user tapping it,
                        // which "this evening" can do at six. Doing nothing is
                        // right: the reminder is still owed and still on
                        // screen, so nothing is lost.
                        val until = snoozedUntil(option, LocalDateTime.now()) ?: return@launch

                        application.taskRepository.snoozeReminder(taskId, until)
                        // The same reason as Done: the snooze is only a promise
                        // once its alarm is set. D-078.
                        application.reconcileReminders()
                        context.cancelReminder(taskId)
                    }
                }
            } finally {
                finish.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "app.tellcat.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.tellcat.action.REMINDER_SNOOZE"
        const val ACTION_SNOOZE_UNTIL = "app.tellcat.action.REMINDER_SNOOZE_UNTIL"

        const val EXTRA_TASK_ID = "tellcat.extra.taskId"
        const val EXTRA_SNOOZE_OPTION = "tellcat.extra.snoozeOption"
    }
}
