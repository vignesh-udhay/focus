package com.vignesh.focuslist.ui.task

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.MorningHour
import com.vignesh.focuslist.core.domain.ParsedDate
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.parseDate
import com.vignesh.focuslist.ui.component.TaskDatePickerDialog
import com.vignesh.focuslist.ui.component.scheduledDateLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/**
 * Task details.
 *
 * Edits the seven fields a task carries about itself: what it is, anything
 * more that needs saying about it, how far it has been triaged, when it is
 * meant to be worked on, when it is due, how long it should take, and how
 * often it comes back.
 *
 * Completion and deletion are deliberately absent. They have their own
 * interactions, and editing a task must not quietly finish or remove it.
 *
 * The sheet holds a draft. Nothing is written until Save, so dismissing leaves
 * the task exactly as it was.
 *
 * The two date fields are typed as well as picked. [today] is what the typed
 * relative phrases resolve against, passed in from the same `CurrentDay` the
 * rest of the app reads rather than from a clock of this screen's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailsSheet(
    task: Task,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    // Null and blank both mean no notes, so the draft holds the blank and the
    // view model maps it back on save.
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes.orEmpty()) }
    var placement by rememberSaveable(task.id) { mutableStateOf(task.placement) }
    // The scheduled day is picked rather than typed, so it is held as a date
    // and needs no parsing. The due date is still typed, and is held as the
    // text of its field for the reason it always was: the field is where the
    // value is edited.
    var scheduled by rememberSaveable(task.id) { mutableStateOf(task.scheduledDate) }
    var dueText by rememberSaveable(task.id) { mutableStateOf(task.dueDate.toEntryText()) }
    var duration by rememberSaveable(task.id) {
        mutableStateOf(task.estimatedDurationMinutes?.toString().orEmpty())
    }
    var recurrence by rememberSaveable(task.id) { mutableStateOf(task.recurrence) }
    // Held as a value rather than as the picker's state, because the picker
    // cannot say "no reminder" and that is the value most tasks have.
    var reminderAt by rememberSaveable(task.id, stateSaver = ReminderSaver) {
        mutableStateOf(task.reminderAt)
    }

    // Which of the sheet's three pages is showing.
    //
    // One sheet rather than several stacked. A ModalBottomSheet is a dialog
    // with its own Window, so stacking means one of them per page: the scrim
    // darkens twice and back has to be dispatched across the pair. A page is
    // also nearly the height of the screen, so a stacked sheet would cover the
    // one beneath it anyway and the context it was meant to preserve would not
    // be visible.
    var page by rememberSaveable(task.id) { mutableStateOf(SheetPage.Details) }

    // Blank means no estimate. Anything else has to be a real number of minutes.
    val minutes = duration.trim().toIntOrNull()
    val isDurationValid = duration.isBlank() || (minutes != null && minutes > 0)

    // Blank means no date. Anything the parser cannot read is an error rather
    // than a guess, on the same terms as the duration above.
    val due = parseDate(dueText, today)
    val isDueValid = due !is ParsedDate.Unrecognized

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        modifier = modifier
    ) {
        // Back belongs to the page, not the sheet. Without this the system
        // would close the whole sheet from an inner page, throwing away the
        // draft rather than returning to the details it came from.
        BackHandler(enabled = page != SheetPage.Details) { page = SheetPage.Details }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Fields several lines tall do not fit at large font scales.
                // Without this the confirming button is simply off the bottom
                // of the sheet with no way to reach it. The sheet's own nested
                // scrolling still takes over at the top, so dragging it closed
                // keeps working.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.md)
        ) {
            when (page) {
                SheetPage.Reminder -> ReminderPage(
                    title = title,
                    scheduled = scheduled,
                    today = today,
                    reminderAt = reminderAt,
                    onSet = {
                        reminderAt = it
                        page = SheetPage.Details
                    },
                    onClear = {
                        reminderAt = null
                        page = SheetPage.Details
                    },
                    onBack = { page = SheetPage.Details }
                )

                SheetPage.Schedule -> SchedulePage(
                    scheduled = scheduled,
                    onScheduledChange = { scheduled = it },
                    dueText = dueText,
                    due = due,
                    onDueTextChange = { dueText = it },
                    duration = duration,
                    isDurationValid = isDurationValid,
                    onDurationChange = { duration = it },
                    recurrence = recurrence,
                    onRecurrenceChange = { recurrence = it },
                    isValid = isDurationValid && isDueValid,
                    onBack = { page = SheetPage.Details }
                )

                SheetPage.Details -> DetailsPage(
                    title = title,
                    onTitleChange = { title = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    placement = placement,
                    onPlacementChange = { placement = it },
                    scheduleSummary = scheduleSummary(
                        scheduled = scheduled,
                        today = today,
                        minutes = if (duration.isBlank()) null else minutes,
                        recurrence = recurrence
                    ),
                    onOpenSchedule = { page = SheetPage.Schedule },
                    reminderSummary = reminderSummary(reminderAt, today),
                    onOpenReminder = { page = SheetPage.Reminder },
                    isValid = title.isNotBlank() && isDurationValid && isDueValid,
                    onDismiss = onDismiss,
                    onSave = {
                        onSave(
                            task.copy(
                                title = title.trim(),
                                notes = notes.trim().takeIf { it.isNotEmpty() },
                                placement = placement,
                                scheduledDate = scheduled,
                                dueDate = (due as? ParsedDate.Recognized)?.date,
                                estimatedDurationMinutes =
                                    if (duration.isBlank()) null else minutes,
                                recurrence = recurrence,
                                reminderAt = reminderAt
                            )
                        )
                    }
                )
            }
        }
    }
}

/**
 * What the task is: its name, anything more that needs saying, and how far it
 * has been triaged.
 *
 * When the work happens is not here. It is one row, summarising what has been
 * set, that opens the page which sets it. `PRODUCT.md` asks the app to avoid
 * exposing every property at once, and seven controls in one sheet is what
 * that rule exists to prevent.
 *
 * The reminder is a second such row rather than a field on the Schedule page,
 * for two reasons. `PRODUCT.md` says a reminder is independent of a scheduled
 * date and of a due date, so filing it under Schedule would state the opposite.
 * And it is the feature the app is named for: one level down is far enough.
 */
@Composable
private fun DetailsPage(
    title: String,
    onTitleChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    placement: TaskPlacement,
    onPlacementChange: (TaskPlacement) -> Unit,
    scheduleSummary: String,
    onOpenSchedule: () -> Unit,
    reminderSummary: String,
    onOpenReminder: () -> Unit,
    isValid: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Text(
        text = stringResource(R.string.task_details_heading),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = FocuslistSpacing.xs)
            .semantics { heading() }
    )

    ScheduleSummaryRow(summary = scheduleSummary, onClick = onOpenSchedule)

    ScheduleSummaryRow(summary = reminderSummary, onClick = onOpenReminder)

    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text(stringResource(R.string.task_details_title_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences
        ),
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = { Text(stringResource(R.string.task_details_notes_label)) },
        maxLines = NotesMaxLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences
        ),
        modifier = Modifier.fillMaxWidth()
    )

    PlacementField(placement = placement, onChange = onPlacementChange)

    Row(
        horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.weight(1f))

        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.task_details_cancel))
        }

        // The title is the one thing a task cannot do without. The other two
        // conditions belong to fields on the Schedule page, which is part of
        // why the summary row states what is set: a Save disabled by something
        // the user cannot see would be unexplainable.
        Button(onClick = onSave, enabled = isValid) {
            Text(stringResource(R.string.task_details_save))
        }
    }
}

/**
 * When the work happens, and how big it is.
 *
 * The scheduled day is picked from a calendar. Typing it, and the phrases the
 * parser reads, stay in Quick Add, where capture happens and speed is the
 * point; this is the organise-later step, where a specific day is usually what
 * is wanted and a calendar is the surer way to it.
 *
 * The due date is still typed, because a deadline is more often described than
 * located: "next friday" is a useful thing to be able to write.
 *
 * There is no Time row. The design draws one, and a task still carries no time
 * of day: a scheduled date says which day the work belongs to, not when it
 * starts. The reminder is the thing that has a time, and it has its own page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulePage(
    scheduled: LocalDate?,
    onScheduledChange: (LocalDate?) -> Unit,
    dueText: String,
    due: ParsedDate,
    onDueTextChange: (String) -> Unit,
    duration: String,
    isDurationValid: Boolean,
    onDurationChange: (String) -> Unit,
    recurrence: Recurrence?,
    onRecurrenceChange: (Recurrence?) -> Unit,
    isValid: Boolean,
    onBack: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = scheduled?.toEpochMillis()
    )

    // The picker owns its selection, so the draft follows it rather than the
    // other way round. Reading it here keeps the two from disagreeing without
    // a second source of truth.
    LaunchedEffect(state.selectedDateMillis) {
        onScheduledChange(state.selectedDateMillis?.toLocalDate())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = FocuslistSpacing.xs)
    ) {
        // The visible half of the back affordance. The BackHandler above covers
        // the gesture; this covers everyone who does not use it, and says on
        // screen that this is a page within something rather than the sheet.
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.task_schedule_back)
            )
        }

        Text(
            text = stringResource(R.string.task_schedule_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
    }

    DatePicker(
        state = state,
        // The page already carries a heading, and the picker's own title and
        // headline would restate it twice over one calendar.
        title = null,
        headline = null,
        showModeToggle = false,
        // The picker defaults to a container of its own, which on a sheet
        // draws a grey panel around the calendar and reads as a second surface
        // floating inside the first. It is part of the page, not a card on it.
        colors = DatePickerDefaults.colors(containerColor = Color.Transparent)
    )

    // Revealed rather than always shown. Most tasks have no deadline, and a
    // field that is nearly always blank is a decision asked of everyone to
    // serve a few. It opens already expanded for a task that has one, so the
    // value is never hidden from whoever set it.
    //
    // Not tied to Repeats. The one place the app touches a due date is the
    // recurrence roll-forward, but that is where the code happens to use it,
    // not where a user needs it: a deadline is most natural on a one-off, and
    // "every Monday" needs none at all.
    var isDueVisible by rememberSaveable { mutableStateOf(dueText.isNotEmpty()) }

    if (isDueVisible) {
        DateField(
            label = stringResource(R.string.task_details_due_label),
            text = dueText,
            parsed = due,
            clearDescription = stringResource(R.string.task_details_clear_due),
            onTextChange = onDueTextChange
        )
    } else {
        TextButton(onClick = { isDueVisible = true }) {
            Text(stringResource(R.string.task_schedule_add_due))
        }
    }

    OutlinedTextField(
        value = duration,
        onValueChange = onDurationChange,
        label = { Text(stringResource(R.string.task_details_duration_label)) },
        suffix = { Text(stringResource(R.string.task_details_duration_suffix)) },
        singleLine = true,
        isError = !isDurationValid,
        supportingText = if (isDurationValid) {
            null
        } else {
            { Text(stringResource(R.string.task_details_duration_error)) }
        },
        trailingIcon = {
            ClearButton(
                enabled = duration.isNotEmpty(),
                description = stringResource(R.string.task_details_clear_duration),
                onClick = { onDurationChange("") }
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth()
    )

    RecurrenceField(recurrence = recurrence, onChange = onRecurrenceChange)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.weight(1f))

        // Done returns to the details, it does not save. Nothing is written
        // until Save there, so backing out of the whole sheet still leaves the
        // task exactly as it was.
        Button(onClick = onBack, enabled = isValid) {
            Text(stringResource(R.string.task_schedule_done))
        }
    }
}

/**
 * When the app should speak up, as `reminder/Set Reminder` draws it.
 *
 * A day and a time, in that order, over a card restating which task is about
 * to start interrupting. The card is not decoration: this page is two levels
 * down from a list, and setting a reminder on the wrong task is the kind of
 * mistake that is only discovered when it goes off.
 *
 * The day is a chip rather than a calendar on the page, because a reminder is
 * nearly always today or tomorrow and a month grid for that is a lot of screen
 * spent on a decision already made. The calendar is one tap away for the rest.
 *
 * The day defaults to the task's scheduled day, or to today when it has none.
 * A default, not a constraint: `PRODUCT.md` says a reminder is independent of
 * the scheduled date, so the chip moves freely.
 *
 * Unlike the Schedule page, this one does not write into the draft as it goes.
 * The picker has no empty position, so a page that updated live would set a
 * reminder on every task whose details anyone happened to open. Set and Clear
 * say what they do; Cancel leaves the task exactly as it was found.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPage(
    title: String,
    scheduled: LocalDate?,
    today: LocalDate,
    reminderAt: LocalDateTime?,
    onSet: (LocalDateTime) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var day by rememberSaveable {
        mutableStateOf(reminderAt?.toLocalDate() ?: scheduled ?: today)
    }
    var isCalendarOpen by rememberSaveable { mutableStateOf(false) }

    val time = reminderAt?.toLocalTime() ?: DefaultReminderTime
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = FocuslistSpacing.xs)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.task_reminder_back)
            )
        }

        Text(
            text = stringResource(R.string.task_reminder_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = FocuslistSpacing.md,
                vertical = FocuslistSpacing.xs
            )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = SummaryTitleMaxLines,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = listOf(
                    scheduled?.let { scheduledDateLabel(it, today) }
                        ?: stringResource(R.string.task_schedule_no_date),
                    reminderSummary(reminderAt, today)
                ).joinToString(SummarySeparator),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    AssistChip(
        onClick = { isCalendarOpen = true },
        label = { Text(scheduledDateLabel(day, today)) }
    )

    if (isCalendarOpen) {
        TaskDatePickerDialog(
            initialDate = day,
            onDismiss = { isCalendarOpen = false },
            onPicked = { day = it }
        )
    }

    // The label belongs to the picker rather than beside it, which is how the
    // frame draws it and which keeps the page's one primary action in reach
    // without scrolling. The dial has a fixed height, so the room for that has
    // to come from the gaps around it.
    Column(verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)) {
        Text(
            text = stringResource(R.string.task_reminder_at),
            style = MaterialTheme.typography.labelLarge
        )

        TimePicker(state = state)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Only offered where there is something to clear, so the page does not
        // present undoing a thing that has not been done.
        if (reminderAt != null) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.task_reminder_clear))
            }
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.task_details_cancel))
        }

        Button(
            onClick = { onSet(day.atTime(state.hour, state.minute)) }
        ) {
            Text(stringResource(R.string.task_reminder_set))
        }
    }
}

/**
 * What the reminder row says, on the details page and on the card above the
 * picker.
 *
 * The day is always named, even when it is the same day the task is scheduled
 * for, because the two are allowed to differ and a bare time would not say
 * which of them it belonged to.
 */
@Composable
private fun reminderSummary(reminderAt: LocalDateTime?, today: LocalDate): String {
    if (reminderAt == null) return stringResource(R.string.task_reminder_none)

    val moment = stringResource(
        R.string.task_reminder_when,
        scheduledDateLabel(reminderAt.toLocalDate(), today),
        timeLabel(reminderAt.toLocalTime())
    )

    return stringResource(R.string.task_reminder_summary, moment)
}

/**
 * A time of day, in whichever of 12 and 24 hours the device is set to.
 *
 * The device's own preference rather than a pattern of ours, for the same
 * reason the notification uses it: an app that says 3:30 PM to someone whose
 * phone says 15:30 everywhere else reads as somebody else's app.
 */
@Composable
private fun timeLabel(time: LocalTime): String {
    val context = LocalContext.current
    val moment = LocalDate.now().atTime(time).atZone(ZoneId.systemDefault()).toInstant()
    return DateFormat.getTimeFormat(context).format(Date.from(moment))
}

/** The three things the sheet can be showing. */
private enum class SheetPage { Details, Schedule, Reminder }

/**
 * The reminder draft, across a process death.
 *
 * ISO-8601 text, which is the same shape the column holds, and the empty
 * string for no reminder. A [Saver] that returned null for the absent case
 * could not be told apart from one that failed to restore.
 */
private val ReminderSaver: Saver<LocalDateTime?, String> = Saver(
    save = { it?.toString().orEmpty() },
    restore = { text -> text.takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse) }
)

/**
 * Where the time picker opens on a task that has no reminder yet.
 *
 * Nine in the morning, the same hour a snooze means by "tomorrow morning". An
 * arbitrary hour either way, but two arbitrary hours would be worse.
 */
private val DefaultReminderTime: LocalTime = MorningHour

private const val SummaryTitleMaxLines = 2

/**
 * The row that stands in for everything on the Schedule page.
 *
 * It states what is set rather than naming the page, because a row that only
 * ever read "Schedule" would hide its own contents: someone looking for the
 * duration would have no reason to think it lives behind a date.
 */
@Composable
private fun ScheduleSummaryRow(
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = FocuslistSpacing.md,
                vertical = FocuslistSpacing.sm
            )
        ) {
            Text(text = summary, style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.weight(1f))

            Icon(
                painter = painterResource(R.drawable.ic_chevron_forward),
                // The row's own text already says what it opens.
                contentDescription = null
            )
        }
    }
}

/**
 * What the summary row says.
 *
 * The day first, then the size, then how often it comes back: the same order
 * and the same separator a task row uses, so the sheet and the list describe a
 * task the same way.
 */
@Composable
private fun scheduleSummary(
    scheduled: LocalDate?,
    today: LocalDate,
    minutes: Int?,
    recurrence: Recurrence?
): String {
    val parts = mutableListOf<String>()

    parts += scheduled?.let { scheduledDateLabel(it, today) }
        ?: stringResource(R.string.task_schedule_no_date)

    minutes?.let { parts += stringResource(R.string.task_duration_minutes, it) }
    recurrence?.let { parts += stringResource(it.labelRes) }

    return parts.joinToString(SummarySeparator)
}

private const val SummarySeparator = " \u00b7 "

/** Midnight UTC, which is how the Material date picker carries a day. */
private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * How far the task has been triaged. Three mutually exclusive states.
 */
@Composable
private fun PlacementField(
    placement: TaskPlacement,
    onChange: (TaskPlacement) -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectedChoiceGroup(
        label = stringResource(R.string.task_details_placement_label),
        options = TaskPlacement.entries,
        selectedOption = placement,
        labelOf = { stringResource(it.labelRes) },
        onSelect = onChange,
        modifier = modifier
    )
}

/**
 * How often the task comes back. Never, and the four periods a rule can be.
 *
 * Never is an option in the group rather than a separate switch, because it is
 * one of the five answers to one question and a task that does not repeat is
 * the ordinary case, not a feature turned off. It maps to a null rule, so
 * nothing downstream has to read a constant meaning "no constant".
 */
@Composable
private fun RecurrenceField(
    recurrence: Recurrence?,
    onChange: (Recurrence?) -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectedChoiceGroup(
        label = stringResource(R.string.task_details_recurrence_label),
        // Null first, so the answer for most tasks is where a reader starts.
        options = listOf(null) + Recurrence.entries,
        selectedOption = recurrence,
        labelOf = { stringResource(it.labelRes) },
        onSelect = onChange,
        modifier = modifier
    )
}

/**
 * One choice out of several, as a connected button group.
 *
 * Material's connected button group is the variant meant for making a
 * selection: buttons that keep their own bounds but share a shape, with the
 * outer corners rounded and the inner ones squared, so the run reads as one
 * control without any of them being a segment of a single container.
 *
 * The overflow problem is what this is really for. A `SingleChoiceSegmented‐
 * ButtonRow` divides its width evenly and neither wraps nor scrolls, so at
 * large font scales the last label was pushed outside its own segment and off
 * the field. `ButtonGroup.md` names two answers, a menu and a wrap, and wrap
 * is the right one here: hiding an option behind a menu would hide the
 * selected one along with it, and a selection the user cannot see is worse
 * than one that takes two lines. `FlowRow` is what wraps in Compose, and it is
 * what Material's own single-select connected sample uses.
 *
 * So nothing is ever cut off, at any font scale: the row grows downward
 * instead of running off the side, and every option stays on screen and
 * reachable. The labels themselves are never truncated.
 *
 * Each button reports itself as a radio button, so a screen reader announces a
 * choice among several rather than four unrelated toggles.
 *
 * @param options every choice, in the order they are shown. A field whose
 * answer can be "none" makes [T] itself nullable and offers null as one of
 * the options, rather than carrying a separate control for it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoiceGroup(
    label: String,
    options: List<T>,
    selectedOption: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        FieldLabel(label)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option == selectedOption,
                    // Choosing the option already chosen is not a way to
                    // choose nothing, so an unchecking tap is ignored. One of
                    // these is always the answer.
                    onCheckedChange = { if (option != selectedOption) onSelect(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    // A toggle button reports a checked state, which is what
                    // it is on its own. In a group where exactly one is the
                    // answer it is a radio button, so it says so, and
                    // publishes the selection rather than a check. Without
                    // this a screen reader offers several independent toggles
                    // and never says which one the task actually is.
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        selected = option == selectedOption
                    }
                ) {
                    Text(labelOf(option))
                }
            }
        }
    }
}

/**
 * A date, typed or picked.
 *
 * The field is the value, so an existing date is shown in it as text and an
 * empty field means the task has no such date. Typing is the quick path and
 * the calendar is the certain one; both write the same field, so neither is a
 * mode the user has to be in.
 *
 * What the text was understood to mean is shown underneath rather than
 * silently applied on save, because "next friday" is only useful if the user
 * can see which day it landed on. Text the parser cannot read is an error and
 * blocks Save, exactly as an unusable duration does, rather than being quietly
 * dropped.
 */
@Composable
private fun DateField(
    label: String,
    text: String,
    parsed: ParsedDate,
    clearDescription: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }
    val recognised = (parsed as? ParsedDate.Recognized)?.date
    val errorText = stringResource(R.string.task_details_date_error)

    Column(modifier) {
        FieldLabel(label)

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                // The field itself is one line, but a placeholder is not
                // held to that, and this hint wraps to three at large font
                // scales, making an empty field taller than a filled one.
                Text(
                    text = stringResource(R.string.task_details_date_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            isError = parsed is ParsedDate.Unrecognized,
            supportingText = when {
                parsed is ParsedDate.Unrecognized -> {
                    { Text(errorText) }
                }
                // Spelled out, with the weekday, so a relative phrase can
                // be checked before it is saved.
                recognised != null -> {
                    { Text(recognised.format(ResolvedDateFormatter)) }
                }
                else -> null
            },
            // Both controls sit in the field rather than beside it. The
            // component centres its trailing content on the input line, so
            // they stay put at any font scale and do not move when the
            // supporting line appears; a button in a Row outside had to be
            // aligned against a height that changes with both.
            trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClearButton(
                    enabled = text.isNotEmpty(),
                    description = clearDescription,
                    onClick = { onTextChange("") }
                )

                IconButton(onClick = { isPickerOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_today),
                        contentDescription = stringResource(R.string.task_details_pick_date)
                    )
                }
            }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isPickerOpen) {
        TaskDatePickerDialog(
            initialDate = recognised,
            onDismiss = { isPickerOpen = false },
            onPicked = { picked -> onTextChange(picked.toEntryText()) }
        )
    }
}

@Composable
private fun ClearButton(
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // An icon rather than the word, because it lives in the field's trailing
    // slot now. The description carries the meaning a label used to, and says
    // which field it clears, which "Clear" three times over never did.
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = description
        )
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = FocuslistSpacing.xs)
    )
}

/** Null is the answer for a task that happens once, and reads as "Never". */
private val Recurrence?.labelRes: Int
    get() = when (this) {
        null -> R.string.task_recurrence_none
        Recurrence.DAILY -> R.string.task_recurrence_daily
        Recurrence.WEEKLY -> R.string.task_recurrence_weekly
        Recurrence.MONTHLY -> R.string.task_recurrence_monthly
        Recurrence.YEARLY -> R.string.task_recurrence_yearly
    }

private val TaskPlacement.labelRes: Int
    get() = when (this) {
        TaskPlacement.INBOX -> R.string.task_placement_inbox
        TaskPlacement.ANYTIME -> R.string.task_placement_anytime
        TaskPlacement.SOMEDAY -> R.string.task_placement_someday
    }

/**
 * How tall the notes field is before it has anything in it, and how far it is
 * allowed to grow before it scrolls. Three lines is enough to look like a
 * place for a sentence or two without dominating the sheet.
 */
private const val NotesMinLines = 3
private const val NotesMaxLines = 8

/**
 * How a date is written into a date field.
 *
 * Fixed and English, because it has to be text the parser reads back, and the
 * parser is English by design. The confirmation underneath the field is the
 * localised one.
 */
private val DateEntryFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)

/** The resolved day, spelled out in the reader's own locale. */
private val ResolvedDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private fun LocalDate?.toEntryText(): String = this?.format(DateEntryFormatter).orEmpty()
