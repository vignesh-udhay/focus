package com.vignesh.focuslist.ui.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The calendar, for choosing one day.
 *
 * Shared because two places now ask for a date: the scheduled and due fields
 * in Task Details, and the actions menu on a row. They differ in what they do
 * with the answer, not in how the question is asked, so the dialog is here and
 * each caller keeps its own handling of the result.
 *
 * The picker works in UTC milliseconds, so the conversion happens at this
 * boundary and nowhere else. [LocalDate] is a calendar day with no time zone,
 * and treating it as UTC midnight keeps the day the user picked, whatever zone
 * they are in.
 *
 * Keyed on [initialDate] so reopening the dialog starts from the date now
 * held rather than the one it was first built with.
 *
 * @param initialDate the day to open on, or null to open on no selection.
 * @param onPicked the chosen day. Not called when the dialog is dismissed, and
 * not called with null: cancelling is [onDismiss], and clearing a date is the
 * caller's own action rather than something the calendar can express.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit
) {
    key(initialDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate?.toUtcMillis()
        )

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onPicked(it.toLocalDate()) }
                        onDismiss()
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

internal fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
