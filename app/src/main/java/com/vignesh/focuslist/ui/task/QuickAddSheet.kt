package com.vignesh.focuslist.ui.task

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.vignesh.focuslist.R
import com.vignesh.focuslist.ui.component.focuslistFieldColors
import com.vignesh.focuslist.ui.component.FocuslistFieldShape
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.CapturedTask
import com.vignesh.focuslist.core.domain.splitTrailingCapture
import com.vignesh.focuslist.ui.component.scheduledDateLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Quick Add.
 *
 * One field and one action, because capturing a task should require almost no
 * decisions. Everything else about the task is decided later.
 *
 * The field reads a trailing day and, since `docs/decisions.md` D-011, a
 * trailing time as well. A day sets the scheduled date. A time sets a reminder.
 *
 * **The reminder is applied rather than offered**, and D-011 ranks the two
 * mistakes to explain why. A reminder set when it was not wanted costs one
 * interruption, and it is visible and fixable in seconds. A reminder not set
 * when it was expected costs the thing being missed, silently. `PRODUCT.md`'s
 * core promise is "if you write it down here, you will be told", so the default
 * has to be to be told.
 *
 * **Only the reminder gets a control.** A wrong day is quiet and cheap: the task
 * turns up on the wrong day and gets moved, by typing. A wrong reminder is a
 * broken promise in either direction. Giving both a dismiss control would spend
 * interface on the low-stakes half and imply the two are the same kind of thing.
 *
 * The chip is not a second way to set anything, which is what keeps
 * `expressive-components.md`'s objection to date chips intact. Nothing else in
 * the sheet sets a reminder, and dismissing it unmarks the same run the field
 * already owns, so there is one mechanism rather than two competing ones.
 *
 * The colour is the quieter half of the signal and never the only one. The
 * supporting line carries the same fact in text, which is what a screen reader
 * announces and what survives a colour-blind reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    today: LocalDate,
    // **Where a capture goes when the title names no day**, per D-053. Today
    // passes its own day, because it saves `parsed.date ?: today`. Inbox passes
    // null, because it saves `parsed.date` and an undated task staying undated
    // is the decision Inbox exists to defer.
    //
    // The fallback date rather than a destination flag, so this is the same
    // value the host acts on at save time and cannot drift from it. The line
    // under the field used to work it out for itself and got Inbox wrong.
    fallbackDate: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (CapturedTask) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf("") }

    // Whether the user has dismissed the reminder for what is currently typed.
    // Reset by typing, because the next keystroke makes a new parse and a
    // dismissal cannot outlive the text it was about.
    var reminderDismissed by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // Recomputed as the user types: it decides what the field marks, what the
    // supporting line says, and what Save hands over, so the three can never
    // disagree.
    val parsed = splitTrailingCapture(title, today).let { capture ->
        if (reminderDismissed) capture.withoutReminder() else capture
    }

    val markStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)
    val markTheRun = remember(parsed.markRange, markStyle) {
        VisualTransformation { text ->
            val range = parsed.markRange
            val marked = if (range == null || range.last >= text.length) {
                AnnotatedString(text.text)
            } else {
                buildAnnotatedString {
                    append(text.text.substring(0, range.first))
                    withStyle(markStyle) {
                        append(text.text.substring(range.first, range.last + 1))
                    }
                    append(text.text.substring(range.last + 1))
                }
            }

            // Nothing is added or removed, so positions are unchanged and the
            // cursor lands where the user put it.
            TransformedText(marked, OffsetMapping.Identity)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            // Expanded, not Hidden, so there is no entrance slide and the
            // keyboard carries the sheet up instead. D-073. A slide running
            // under a rising keyboard animates toward the anchor as it was
            // when the slide began, then jumps the keyboard's height once it
            // lands. Placed at its anchor from the first frame, the sheet
            // snaps to the new anchor on every keyboard frame and the two
            // read as one motion. Dismissal still slides.
            initialValue = SheetValue.Expanded,
            // One field: there is no half-height state worth stopping at.
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg)
        ) {
            // **The sheet's name, and the field's, per D-052.** It was the
            // field's floating label, where it repeated what the sheet was for
            // on every keystroke and cost a row of the input to do it. As a
            // heading it is drawn once and the field keeps its whole container
            // for what the user is typing.
            //
            // `titleLarge` and a `heading()`, which is what RepeatSheet and the
            // Task Details sheets already use, so this reads as the same kind of
            // sheet rather than a new one.
            //
            // It also names the field. A `contentDescription` on an editable
            // node can replace the announcement of what has been typed, so the
            // heading above it is the safer way to say what the box is.
            Text(
                text = stringResource(R.string.quick_add_title_label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(bottom = FocuslistSpacing.sm)
                    .semantics { heading() }
            )

            // Filled, per D-025. Every other surface in the app is a tinted
            // container on a plain page, and the outlined field was the one
            // component asking to be read by its border instead.
            //
            // The indicator and the two-corner container are dropped in
            // `focuslistFieldColors`, which says why.
            TextField(
                value = title,
                colors = focuslistFieldColors(),
                shape = FocuslistFieldShape,
                onValueChange = { typed ->
                    title = typed
                    // A new parse is a new reminder, so an old dismissal has
                    // nothing left to be about. Without this, typing a second
                    // time after dismissing would silently suppress a reminder
                    // the user never declined.
                    reminderDismissed = false
                },
                // No label. The heading above carries the name, and a filled
                // field is 56dp with or without one, so dropping it leaves the
                // container where it was and returns the top row to the text.
                // D-052.
                placeholder = {
                    // The one place the field's own trick is taught. A day and a
                    // time written on the end of the title are taken as the
                    // task's date and its reminder, and nothing else on this
                    // sheet says so: without an example, capture looks like a
                    // plain text box and the feature is found by accident or
                    // not at all.
                    //
                    // Capped to one line, like every placeholder in the app. The
                    // field is single-line but a placeholder is not held to
                    // that, and an empty field taller than a filled one is what
                    // happens at large font scales otherwise.
                    Text(
                        text = stringResource(R.string.quick_add_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                visualTransformation = markTheRun,
                // Null rather than an empty slot until there is something to
                // describe, so the field does not reserve a line for a sentence
                // about a task that does not exist. Tied to the same test the
                // Add button uses, so the line and the button cannot disagree
                // about whether a capture is real. D-053.
                supportingText = if (parsed.title.isBlank()) null else {
                    {
                        QuickAddSupportingText(
                            parsed = parsed,
                            today = today,
                            fallbackDate = fallbackDate
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                // **Done saves.** It declared the action and then handled none
                // of it, so the key that looks like it commits only dismissed
                // the keyboard, and capture ended with reaching past it for the
                // button. Guarded by the button's own rule, so the two commit
                // paths agree about what is saveable.
                keyboardActions = KeyboardActions(
                    onDone = { if (parsed.title.isNotBlank()) onSave(parsed) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            // The one control on the sheet besides Save, and it appears only
            // when there is a reminder to be dismissed.
            parsed.time?.let {
                ReminderChip(
                    parsed = parsed,
                    today = today,
                    onDismiss = { reminderDismissed = true },
                    modifier = Modifier.padding(top = FocuslistSpacing.xs)
                )
            }

            Button(
                onClick = { onSave(parsed) },
                // The title is the one thing a task cannot do without, and it is
                // the title left after the day and the time are taken that has
                // to exist.
                enabled = parsed.title.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = FocuslistSpacing.md)
            ) {
                Text(stringResource(R.string.quick_add_save))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * The line under the field, which always carries the outcome in text.
 *
 * D-011's three states, and it says something in all of them. Colour cannot be
 * announced to a screen reader, and a rewrite the user cannot see is one they
 * cannot correct, so this is the channel that has to be complete.
 *
 * With nothing understood it names where the task will be saved, which is the
 * state that used to say nothing at all.
 */
@Composable
private fun QuickAddSupportingText(
    parsed: CapturedTask,
    today: LocalDate,
    fallbackDate: LocalDate?
) {
    val day = parsed.date

    Text(
        text = when {
            // The reminder is named by the chip, so the line stays about the day.
            //
            // The date and not the list, even though a future day means the task
            // appears in Upcoming. This is the value the user typed and wants
            // confirmed, and the list follows from the date rather than the
            // other way round. D-053.
            day != null -> stringResource(
                R.string.quick_add_scheduled_for,
                scheduledDateLabel(day, today)
            )

            // No day typed, so the destination is the surprising part and this
            // is the only branch that names one. Which it is depends on the
            // host: Today dates an undated capture, Inbox leaves it undated.
            fallbackDate != null -> stringResource(R.string.quick_add_saved_to_today)
            else -> stringResource(R.string.quick_add_saved_to_inbox)
        }
    )
}

/**
 * The Reminder chip, and the only thing on the sheet that can be dismissed.
 *
 * An `InputChip` rather than an `AssistChip`, because Material's input chip is
 * the one that represents a piece of information the user supplied and offers to
 * remove it. That is exactly what this is: the time came out of what they typed.
 *
 * It names the moment in words. The chip is the reminder's only presence in
 * text, so a screen reader has to get the whole fact from it.
 */
@Composable
private fun ReminderChip(
    parsed: CapturedTask,
    today: LocalDate,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The clock rather than a parameter, because this label has to say where
    // the reminder actually lands: D-030 resolves a time that has gone by to
    // tomorrow, and a chip still reading "Today" would be the disagreement
    // between the chip and the saved value that D-011 exists to prevent.
    val at = parsed.reminderAt(today, LocalDateTime.now()) ?: return
    val time = at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val label = stringResource(
        R.string.quick_add_reminder,
        scheduledDateLabel(at.toLocalDate(), today),
        time
    )

    InputChip(
        selected = true,
        onClick = onDismiss,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                // The chip's own label already names the reminder, so this
                // names the action instead of describing the glyph.
                contentDescription = stringResource(R.string.quick_add_reminder_dismiss),
                modifier = Modifier.size(InputChipDefaults.IconSize)
            )
        },
        modifier = modifier
    )
}
