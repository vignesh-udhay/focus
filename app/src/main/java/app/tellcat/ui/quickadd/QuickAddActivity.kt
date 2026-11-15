package app.tellcat.ui.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.tellcat.R
import app.tellcat.TellcatApplication
import app.tellcat.core.design.ThemePreference
import app.tellcat.core.domain.PrimaryList
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.captureScheduledDate
import app.tellcat.core.domain.newTask
import app.tellcat.core.domain.primaryListFor
import app.tellcat.ui.reminder.ReminderPermissionGate
import app.tellcat.ui.task.QuickAddCapture
import app.tellcat.ui.task.QuickAddSheet
import app.tellcat.ui.theme.TellcatTheme
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/**
 * Quick Add over the home screen, from the launcher's "New task" shortcut and
 * the widget's add button. D-132.
 *
 * A see-through window holding Quick Add itself, as "Add to Tellcat" holds its
 * sheet (D-097), so a task is added without Tellcat's screens coming up. Add
 * saves, a toast says where the task went, and the window closes. A toast,
 * because the home screen is not ours and a snackbar has nowhere to sit.
 *
 * **The task is written before the window closes**, in this activity's scope
 * and awaited. `TaskListViewModel` writes in its own scope without waiting,
 * which is right on a screen that stays open and would be a lost capture here:
 * finishing clears the view model and can cancel the write. So this saves
 * through [newTask], the builder the view model uses, rather than through the
 * view model.
 *
 * A reminder set here is checked exactly as one set in the app is: the
 * permission asks come up over the home screen before the window closes.
 */
class QuickAddActivity : ComponentActivity() {

    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val application = application as TellcatApplication

        enableEdgeToEdge()
        setContent {
            val preferences by application.preferences.state.collectAsStateWithLifecycle()
            val today by application.currentDay.today.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            // Saved, so a rotation while the permission is asked does not
            // bring the sheet back over a task already added.
            var isAskingPermission by rememberSaveable { mutableStateOf(false) }

            TellcatTheme(
                darkTheme = when (preferences.theme) {
                    ThemePreference.System -> systemDark
                    ThemePreference.Light -> false
                    ThemePreference.Dark -> true
                },
                dynamicColor = preferences.dynamicColor
            ) {
                QuickAddWindow(
                    today = today,
                    isAskingPermission = isAskingPermission,
                    onDismiss = ::finish,
                    onSave = { capture ->
                        save(capture) { isAskingPermission = true }
                    },
                    canScheduleExact = application.reminderAlarms::canScheduleExact,
                    onPermissionGranted = application::refreshReminders
                )
            }
        }
    }

    private fun save(capture: QuickAddCapture, onReminderSet: () -> Unit) {
        // One Add, one task, however quickly the button is pressed again.
        if (isSaving) return

        val application = application as TellcatApplication
        // Read at save time, so a capture made after midnight gets the new day.
        val save = quickAddSave(
            capture = capture,
            today = application.currentDay.today.value,
            now = LocalDateTime.now()
        ) ?: return

        isSaving = true
        lifecycleScope.launch {
            val added = runCatching { application.taskRepository.insert(save.task) }.isSuccess
            isSaving = false

            // On failure the sheet stays open with the text, so nothing is lost.
            if (!added) {
                Toast.makeText(this@QuickAddActivity, R.string.quick_add_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(this@QuickAddActivity, save.landed.savedTo(), Toast.LENGTH_SHORT).show()
            if (save.task.reminderAt != null) onReminderSet() else finish()
        }
    }

    companion object {
        /** What the widget's add button starts. The shortcut names this class in `shortcuts.xml`. */
        fun intent(context: Context): Intent = Intent(context, QuickAddActivity::class.java)
    }
}

/** The task a capture here makes, and the list it lands on. */
data class QuickAddSave(val task: Task, val landed: PrimaryList)

/**
 * What Add saves from the window over the home screen, or null for a blank
 * title, which saves nothing and leaves the sheet open.
 *
 * **A capture that names no day goes to Today**, as the widget's Add always
 * sent it and as Today's own Quick Add does: someone adding from the home
 * screen means to do it. A day or a reminder in the title goes where it says,
 * D-075. D-132.
 */
fun quickAddSave(capture: QuickAddCapture, today: LocalDate, now: LocalDateTime): QuickAddSave? {
    val scheduledDate = captureScheduledDate(
        parsed = capture.parsed,
        fallbackDate = today,
        today = today,
        now = now,
        recurrence = capture.recurrence
    )
    val task = newTask(
        title = capture.parsed.title,
        scheduledDate = scheduledDate,
        reminderAt = capture.parsed.reminderAt(today, now),
        recurrence = capture.recurrence,
        checklist = capture.checklist,
        ringsLikeAlarm = capture.ringsLikeAlarm,
        earlyReminder = capture.earlyReminder,
        now = now
    ) ?: return null

    return QuickAddSave(task = task, landed = primaryListFor(scheduledDate, today))
}

/** The landing notice's words, D-075, said by a toast. */
private fun PrimaryList.savedTo(): Int = when (this) {
    PrimaryList.TODAY -> R.string.quick_add_saved_to_today
    PrimaryList.INBOX -> R.string.quick_add_saved_to_inbox
    PrimaryList.UPCOMING -> R.string.quick_add_saved_to_upcoming
}

/**
 * The window's contents: Quick Add, then, once a task with a reminder is
 * saved, the reminder permission asks in its place.
 */
@Composable
fun QuickAddWindow(
    today: LocalDate,
    isAskingPermission: Boolean,
    onDismiss: () -> Unit,
    onSave: (QuickAddCapture) -> Unit,
    canScheduleExact: () -> Boolean,
    onPermissionGranted: () -> Unit
) {
    if (!isAskingPermission) {
        QuickAddSheet(
            today = today,
            // Today dates an undated capture, and the line under the field
            // says so before Add. D-053.
            fallbackDate = today,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }

    // Whatever the answer, the window closes after it: the task is saved.
    ReminderPermissionGate(
        isRequested = isAskingPermission,
        canScheduleExact = canScheduleExact,
        onPermissionGranted = onPermissionGranted,
        onDone = onDismiss
    )
}
