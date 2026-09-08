package com.vignesh.focuslist.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.endOfWeek
import com.vignesh.focuslist.core.domain.thisWeekend
import com.vignesh.focuslist.ui.component.PlanRowGroup
import com.vignesh.focuslist.ui.component.TaskDatePickerDialog
import com.vignesh.focuslist.ui.component.durationLabel
import com.vignesh.focuslist.ui.component.PlanRow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The sheets the Plan rows open.
 *
 * Every one of them writes as soon as something is chosen and closes itself.
 * There is no Save on this screen, and `docs/decisions.md` D-018 records that
 * the draft this replaced was worth something: dismissing used to leave the task
 * exactly as it was. What stands in for it is that each write is one field, made
 * by one deliberate choice, and reversible by reopening the same row.
 */

/**
 * Which day a task is on, or when it is due.
 *
 * **Both date sheets are the same shape**, which D-018 fixes: the clear option
 * first, three day presets in a 2x2 grid, then a full-width Choose a date
 * opening the Material picker.
 *
 * The clear option leads because it is the one that was missing. Scheduled had
 * none at all, and a task with no scheduled date is an Inbox task, so its
 * absence was a dead end rather than a missing convenience: there was no way
 * back to the Inbox from this screen.
 *
 * There is no typed field here. Parsing is Quick Add's job, where speed is the
 * point and the user is already at a keyboard; this screen is the
 * organise-later step, where a preset or a calendar is faster and unambiguous.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateSheet(
    kind: DateSheetKind,
    selected: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }

    if (isPickerOpen) {
        TaskDatePickerDialog(
            initialDate = selected ?: today,
            onDismiss = { isPickerOpen = false },
            onPicked = { picked ->
                isPickerOpen = false
                onPick(picked)
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
        ) {
            Text(
                text = stringResource(kind.titleRes),
                style = MaterialTheme.typography.titleLarge
            )

            // Only the due date has one. It says what a due date is *for*,
            // which is the thing people get wrong about it: a deadline that
            // does not move the task off the day it is planned for.
            kind.supportingRes?.let { supporting ->
                Text(
                    text = stringResource(supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // **A 2x2 grid of four equal cells**, and the clear option is the
            // first of them rather than a full-width row above them.
            //
            // D-018 is explicit about the arithmetic: `Next week` was on the
            // board and was dropped because "its cell was needed for the clear
            // option". Four cells, one of which the clear option took. Drawing
            // it full width leaves the grid with three cells and an empty half,
            // which is what the first build of this did.
            val far = kind.farPreset(today)

            DateOptionRow(
                first = DateOption(stringResource(kind.clearRes), null),
                second = DateOption(stringResource(R.string.task_date_today), today),
                selected = selected,
                onPick = onPick
            )

            DateOptionRow(
                first = DateOption(stringResource(R.string.task_date_tomorrow), today.plusDays(1)),
                second = DateOption(stringResource(kind.farPresetRes), far),
                selected = selected,
                onPick = onPick
            )

            // Outlined, and the only outlined control in the sheet, because it
            // is the one that leaves it for a picker rather than answering the
            // question here. The calendar says which picker.
            OutlinedButton(
                onClick = { isPickerOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FocuslistDimensions.TouchTargetMin)
                    .padding(top = FocuslistSpacing.xs)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_today),
                    // The label beside it already names the action.
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.task_date_choose))
            }
        }
    }
}

/** One cell of the grid: what it says, and the day it sets. */
internal data class DateOption(val label: String, val date: LocalDate?)

/**
 * Two cells of the grid, equal width.
 *
 * A `Row` of two weighted children rather than a grid component, because the
 * grid is two by two and known: `LazyVerticalGrid` inside a sheet would bring
 * its own scrolling to a thing that never scrolls.
 */
@Composable
private fun DateOptionRow(
    first: DateOption,
    second: DateOption,
    selected: LocalDate?,
    onPick: (LocalDate?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)) {
        listOf(first, second).forEach { option ->
            PresetButton(
                label = option.label,
                selected = selected == option.date,
                onClick = { onPick(option.date) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One preset, in a date sheet or a duration sheet.
 *
 * **Filled tonal, which is what the board draws**, and filled primary when it is
 * the value the task currently holds. An earlier build made the unselected ones
 * outlined, which read as a row of disabled controls next to one live one.
 *
 * The selected state is the one place a value is styled differently on this
 * screen, and it is about *this control* rather than about whether the field is
 * filled in, which is the distinction `task-details.md` draws when it refuses to
 * dim an unset row.
 *
 * **The board is inconsistent about it and this follows the Duration frame.**
 * Frame 7 draws the current duration filled against three tonal siblings; frame
 * 2 draws all four date presets tonal on a task scheduled for today, so the
 * current value is not marked there. One of the two had to give, and a sheet
 * that cannot say what is already set is the worse of them.
 */
@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors =
        if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()

    Button(
        onClick = onClick,
        colors = colors,
        contentPadding = PaddingValues(horizontal = FocuslistSpacing.xs),
        modifier = modifier.heightIn(min = FocuslistDimensions.TouchTargetMin)
    ) {
        // One line, centred. A preset whose label wrapped would make its cell
        // taller than the one beside it and break the grid.
        Text(text = label, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * The two date rows, and the one thing that differs between them.
 *
 * Same shape, same grid, different words and a different far preset. Held as
 * one type so the two sheets cannot drift into being two designs.
 */
internal enum class DateSheetKind {

    SCHEDULED,
    DUE;

    val titleRes: Int
        get() = when (this) {
            SCHEDULED -> R.string.task_scheduled
            DUE -> R.string.task_due_date
        }

    /** Only the due date explains itself. */
    val supportingRes: Int?
        get() = when (this) {
            SCHEDULED -> null
            DUE -> R.string.task_due_date_supporting
        }

    val clearRes: Int
        get() = when (this) {
            SCHEDULED -> R.string.task_date_none
            DUE -> R.string.task_date_no_due
        }

    val farPresetRes: Int
        get() = when (this) {
            SCHEDULED -> R.string.task_date_this_weekend
            DUE -> R.string.task_date_end_of_week
        }

    /**
     * The third preset, which is the one that differs.
     *
     * Scheduled offers the coming Saturday, because moving work to the weekend
     * is the common escape. The due date offers the coming Friday, because a
     * deadline of "end of week" means the working week. D-018 fixes both.
     */
    fun farPreset(today: LocalDate): LocalDate = when (this) {
        SCHEDULED -> thisWeekend(today)
        DUE -> endOfWeek(today)
    }
}

/**
 * How long the task is reckoned to take.
 *
 * Four presets and a custom entry, as the board draws it. The value shown at the
 * top is the task's current estimate, which is what tells the user whether they
 * are changing something or setting it for the first time.
 *
 * Clearing is one of the presets rather than a separate control, because "no
 * estimate" is a real value this app designs for: `focus.md` gives an
 * unestimated task its own open-ended session, so it is a choice rather than an
 * omission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationSheet(
    selectedMinutes: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var isCustomOpen by rememberSaveable { mutableStateOf(false) }

    if (isCustomOpen) {
        CustomDurationDialog(
            initialMinutes = selectedMinutes,
            onDismiss = { isCustomOpen = false },
            onSet = { minutes ->
                isCustomOpen = false
                onPick(minutes)
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.task_duration),
                style = MaterialTheme.typography.titleLarge
            )

            Row(horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)) {
                PresetButton(
                    label = stringResource(R.string.task_duration_none),
                    selected = selectedMinutes == null,
                    onClick = { onPick(null) },
                    modifier = Modifier.weight(1f)
                )

                DurationPresets.forEach { minutes ->
                    PresetButton(
                        // Through `durationLabel`, so this reads 45m and 1h
                        // like every other duration in the app rather than
                        // "45 min", which nothing else says.
                        label = durationLabel(minutes).text,
                        selected = selectedMinutes == minutes,
                        onClick = { onPick(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedButton(
                onClick = { isCustomOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FocuslistDimensions.TouchTargetMin)
                    .padding(top = FocuslistSpacing.xs)
            ) {
                Text(stringResource(R.string.task_duration_custom))
            }
        }
    }
}

/** The four the board draws. Short enough that a fifth would not fit the row. */
private val DurationPresets = listOf(15, 30, 45, 60)

/**
 * How often the task comes back.
 *
 * **This is the row, not the editor**, and `docs/decisions.md` D-019 is why. The
 * board draws nine frames of a Repeat editor with an interval, a weekday set and
 * an end condition. `Recurrence.kt` does not merely lack those; it excludes them
 * in writing, and building them is a schema change and a rule engine arriving
 * through a Figma frame. D-019 puts the whole editor in Phase 4.
 *
 * So this offers the four periods `Recurrence` actually has, and none. When
 * Phase 4 arrives this sheet is what grows; nothing else on the screen changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatSheet(
    selected: Recurrence?,
    onPick: (Recurrence?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.task_repeat),
                style = MaterialTheme.typography.titleLarge
            )

            // Rows rather than a chip grid, because five options with words
            // this long do not fit two to a line, and because a list of
            // mutually exclusive choices is what a row group is for.
            val options: List<Recurrence?> = listOf(null) + Recurrence.entries

            PlanRowGroup {
                options.forEachIndexed { index, option ->
                    PlanRow(
                        label = stringResource(recurrenceLabel(option)),
                        value = if (option == selected) {
                            stringResource(R.string.task_repeat_selected)
                        } else {
                            ""
                        },
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = options.size
                        ),
                        onClick = { onPick(option) }
                    )
                }
            }
        }
    }
}

/** What a recurrence is called, or "Doesn't repeat" for none. */
internal fun recurrenceLabel(recurrence: Recurrence?): Int = when (recurrence) {
    null -> R.string.task_repeat_never
    Recurrence.DAILY -> R.string.task_recurrence_daily
    Recurrence.WEEKLY -> R.string.task_recurrence_weekly
    Recurrence.MONTHLY -> R.string.task_recurrence_monthly
    Recurrence.YEARLY -> R.string.task_recurrence_yearly
}

/**
 * A duration in hours and minutes, for the case the four presets do not cover.
 *
 * A dialog rather than a second sheet, because a modal sheet on Android is a
 * dialog with its own window: stacking one on another darkens the scrim twice
 * and makes back a question about which of the pair receives it. That lesson is
 * inherited from the screen this replaced, where it was learned the hard way.
 *
 * Two fields, because "90" is ambiguous between an hour and a half and an hour
 * and thirty of something. Both accept digits only.
 *
 * Zero is not a duration and Done refuses it. `editTask` refuses it too, so a
 * value that slipped through would be dropped silently, which is worse than a
 * disabled button that says nothing happened.
 */
@Composable
private fun CustomDurationDialog(
    initialMinutes: Int?,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hours by rememberSaveable { mutableStateOf(((initialMinutes ?: 0) / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf(((initialMinutes ?: 0) % 60).toString()) }

    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_duration_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)) {
                Text(
                    text = stringResource(R.string.task_duration_custom_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)) {
                    DurationField(
                        value = hours,
                        onValueChange = { hours = it },
                        label = stringResource(R.string.task_duration_hours),
                        modifier = Modifier.weight(1f)
                    )
                    DurationField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = stringResource(R.string.task_duration_minutes_label),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(total) }, enabled = total > 0) {
                Text(stringResource(R.string.task_duration_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/** One digits-only field. Filtering on input beats validating after it. */
@Composable
private fun DurationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        // Digits only, capped at three, so nothing has to be rejected later and
        // no error state is needed for text the field never accepted.
        onValueChange = { typed ->
            if (typed.all(Char::isDigit) && typed.length <= 3) onValueChange(typed)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/**
 * When the app should speak up, as board frame 6 draws it.
 *
 * A dialog over the details, not a page within them. A reminder is one value,
 * and a page for one value costs a journey the user then has to come back
 * from. The frame floats it over the task it belongs to, which also answers
 * "which task am I setting this on" without restating the task anywhere.
 *
 * The time only. The day comes from the task: the day it is scheduled for, or
 * today when it has none. Storage still holds a full date and time, and
 * `PRODUCT.md` still says a reminder is independent of the scheduled date, but
 * nothing on this dialog moves it off that day. See `ROADMAP.md`.
 *
 * Clear sits beside Cancel. The design draws no way to remove a reminder, and
 * a reminder that cannot be removed is a worse problem than a third button.
 *
 * Carried across from the sheet D-018 replaced, unchanged. The entry rewrote how
 * a reminder is reached, not what setting one is, and this is the one control on
 * the old screen the board still draws exactly as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderDialog(
    reminderAt: LocalDateTime?,
    day: LocalDate,
    onSet: (LocalDateTime) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val time = reminderAt?.toLocalTime() ?: DefaultReminderTime
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        // The device's own preference, so the dial the user is handed matches
        // the clock they read everywhere else.
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )

    // Not saved across a process death, deliberately. The time survives inside
    // the picker's own state; which of the two ways to enter it was showing is
    // not worth a saver.
    var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }

    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.task_reminder_at),
                style = MaterialTheme.typography.labelMedium
            )
        },
        // The keyboard toggle the frame draws. Material supplies both icons
        // and the tooltip, so this costs one line rather than a new asset.
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                displayMode = mode,
                onDisplayModeChange = {
                    mode = if (mode == TimePickerDisplayMode.Picker) {
                        TimePickerDisplayMode.Input
                    } else {
                        TimePickerDisplayMode.Picker
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSet(day.atTime(state.hour, state.minute)) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)) {
                // Only where there is something to clear, so the dialog does
                // not offer to undo a thing that has not been done.
                if (reminderAt != null) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.task_reminder_clear))
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    ) {
        if (mode == TimePickerDisplayMode.Input) {
            TimeInput(state = state)
        } else {
            TimePicker(state = state)
        }
    }
}
