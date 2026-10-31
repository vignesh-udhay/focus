package app.tellcat.ui.component

import androidx.annotation.StringRes
import app.tellcat.R
import app.tellcat.core.domain.EarlyReminder

/**
 * The words for an early reminder choice: "None", "15 min before", "1 hour
 * before", "1 day before". D-115. One place, so the reminder sheet, the row and
 * the widget say it the same way.
 */
@StringRes
internal fun earlyReminderLabel(early: EarlyReminder?): Int = when (early) {
    null -> R.string.task_reminder_early_none
    EarlyReminder.FifteenMinutes -> R.string.task_reminder_early_15_minutes
    EarlyReminder.OneHour -> R.string.task_reminder_early_1_hour
    EarlyReminder.OneDay -> R.string.task_reminder_early_1_day
}
