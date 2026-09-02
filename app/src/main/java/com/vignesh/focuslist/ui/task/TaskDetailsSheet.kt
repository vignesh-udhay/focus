package com.vignesh.focuslist.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.ParsedDate
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.parseDate
import com.vignesh.focuslist.ui.component.TaskDatePickerDialog
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    // The dates are held as the text of their fields, because the field is
    // where the value is edited. An existing date is written back out in a
    // form the parser reads, so opening a task and saving it unchanged stores
    // the same day it started with.
    var scheduledText by rememberSaveable(task.id) { mutableStateOf(task.scheduledDate.toEntryText()) }
    var dueText by rememberSaveable(task.id) { mutableStateOf(task.dueDate.toEntryText()) }
    var duration by rememberSaveable(task.id) {
        mutableStateOf(task.estimatedDurationMinutes?.toString().orEmpty())
    }
    var recurrence by rememberSaveable(task.id) { mutableStateOf(task.recurrence) }

    // Blank means no estimate. Anything else has to be a real number of minutes.
    val minutes = duration.trim().toIntOrNull()
    val isDurationValid = duration.isBlank() || (minutes != null && minutes > 0)

    // Blank means no date. Anything the parser cannot read is an error rather
    // than a guess, on the same terms as the duration above.
    val scheduled = parseDate(scheduledText, today)
    val due = parseDate(dueText, today)
    val areDatesValid = scheduled !is ParsedDate.Unrecognized && due !is ParsedDate.Unrecognized

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Seven fields, one of them several lines tall, do not fit at
                // large font scales. Without this the Save button is simply
                // off the bottom of the sheet with no way to reach it. The
                // sheet's own nested scrolling still takes over at the top,
                // so dragging it closed keeps working.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.task_details_heading),
                style = MaterialTheme.typography.titleMedium,
                // Marked as a heading so a screen reader announces what this
                // sheet is on open, and can jump to it by heading.
                modifier = Modifier.semantics { heading() }
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_details_title_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.task_details_notes_label)) },
                // Free text, so it wraps and grows rather than scrolling
                // sideways, and Enter inserts a line rather than submitting.
                minLines = NotesMinLines,
                maxLines = NotesMaxLines,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                modifier = Modifier.fillMaxWidth()
            )

            PlacementField(
                placement = placement,
                onChange = { placement = it }
            )

            DateField(
                label = stringResource(R.string.task_details_scheduled_label),
                text = scheduledText,
                parsed = scheduled,
                clearDescription = stringResource(R.string.task_details_clear_scheduled),
                onTextChange = { scheduledText = it }
            )

            DateField(
                label = stringResource(R.string.task_details_due_label),
                text = dueText,
                parsed = due,
                clearDescription = stringResource(R.string.task_details_clear_due),
                onTextChange = { dueText = it }
            )

            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text(stringResource(R.string.task_details_duration_label)) },
                    suffix = { Text(stringResource(R.string.task_details_duration_suffix)) },
                    singleLine = true,
                    isError = !isDurationValid,
                    supportingText = if (isDurationValid) {
                        null
                    } else {
                        { Text(stringResource(R.string.task_details_duration_error)) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )

                ClearButton(
                    enabled = duration.isNotEmpty(),
                    description = stringResource(R.string.task_details_clear_duration),
                    onClick = { duration = "" }
                )
            }

            RecurrenceField(
                recurrence = recurrence,
                onChange = { recurrence = it }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs),
                modifier = Modifier.align(Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.task_details_cancel))
                }

                Button(
                    onClick = {
                        onSave(
                            task.copy(
                                title = title.trim(),
                                notes = notes.trim().takeIf { it.isNotEmpty() },
                                placement = placement,
                                scheduledDate = (scheduled as? ParsedDate.Recognized)?.date,
                                dueDate = (due as? ParsedDate.Recognized)?.date,
                                estimatedDurationMinutes = if (duration.isBlank()) null else minutes,
                                recurrence = recurrence
                            )
                        )
                    },
                    // The title is the one thing a task cannot do without.
                    enabled = title.isNotBlank() && isDurationValid && areDatesValid
                ) {
                    Text(stringResource(R.string.task_details_save))
                }
            }
        }
    }
}

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

        Row(verticalAlignment = Alignment.Top) {
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
                trailingIcon = {
                    IconButton(onClick = { isPickerOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_today),
                            contentDescription = stringResource(R.string.task_details_pick_date)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )

            ClearButton(
                enabled = text.isNotEmpty(),
                description = clearDescription,
                onClick = { onTextChange("") }
            )
        }
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
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Text(stringResource(R.string.task_details_clear))
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
