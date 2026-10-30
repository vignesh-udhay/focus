package app.tellcat.ui.task

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ripple
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.domain.reminderTimeWords
import app.tellcat.core.domain.writeReminder
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.flow.first
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import app.tellcat.R
import app.tellcat.ui.component.tellcatFieldColors
import app.tellcat.ui.component.TellcatFieldShape
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.domain.CapturedTask
import app.tellcat.core.domain.splitTrailingCapture
import app.tellcat.core.domain.endsInBareHour
import app.tellcat.ui.component.scheduledDateLabel
import app.tellcat.ui.component.rememberTimeFormat
import java.time.LocalDate
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/**
 * Quick Add.
 *
 * One field and one action, because capturing a task should require almost no
 * decisions. Everything else about the task is decided later.
 *
 * The field reads a trailing day and, since `docs/decisions.md` D-011, a
 * trailing time as well. A day sets the scheduled date. A time sets a reminder,
 * and since D-075 the reminder's day is the scheduled date too.
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
 * **Since D-112 the chip is always there, and it types.** Unset it reads
 * "Reminder"; tapped, it opens Task Details' reminder sheet, and what is chosen
 * there is written into the field as the words that say it, " tomorrow at 9am",
 * marked as typed words are. The field is still the only setter: the chip is a
 * shortcut to typing, and the words it leaves teach the syntax.
 *
 * The colour is the quieter half of the signal and never the only one. The
 * supporting line carries the same fact in text, which is what a screen reader
 * announces and what survives a colour-blind reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    today: LocalDate,
    // **Where a capture goes when the title says nothing about when**, per
    // D-053. Today passes its own day and Inbox passes null, because an undated
    // task staying undated is the decision Inbox exists to defer. A title with a
    // day or a reminder does not use it: D-075.
    //
    // The fallback date rather than a destination flag, so this is the same
    // value the host acts on at save time and cannot drift from it. The line
    // under the field used to work it out for itself and got Inbox wrong.
    fallbackDate: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (CapturedTask) -> Unit,
    modifier: Modifier = Modifier,
    // A parameter for the reason `TaskDetailsSheets` takes one: the behaviour
    // worth proving happens when a minute ends, and only a test can make that
    // happen on demand.
    clock: () -> LocalDateTime = LocalDateTime::now
) {
    val now = rememberCurrentMinute(clock)

    // A value rather than a string, so the chip can put the cursor after the
    // words it writes. D-112.
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    // Whether the user has dismissed the reminder for what is currently typed.
    // Reset by typing, because the next keystroke makes a new parse and a
    // dismissal cannot outlive the text it was about.
    var reminderDismissed by rememberSaveable { mutableStateOf(false) }

    // The reminder sheet the chip opens, D-112, and a count of the times it
    // has closed, which is what hands focus back to the field.
    var isReminderOpen by rememberSaveable { mutableStateOf(false) }
    var reminderClosed by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    // Recomputed as the user types: it decides what the field marks, what the
    // supporting line says, and what Save hands over, so the three can never
    // disagree.
    val parsed = splitTrailingCapture(title.text, today).let { capture ->
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
        // This sheet's own window and keyboard, read inside it: Quick Add is a
        // window of its own, and the reminder sheet is another over it.
        val keyboard = LocalSoftwareKeyboardController.current
        val windowInfo = LocalWindowInfo.current

        // **Focus and the keyboard come back to the field** when the reminder
        // sheet closes, D-112, with the cursor after the words it wrote. Once
        // this window has focus again: asked for while the reminder sheet's
        // window still held it, the field would take focus with no keyboard.
        LaunchedEffect(reminderClosed) {
            if (reminderClosed == 0) return@LaunchedEffect
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            focusRequester.requestFocus()
            keyboard?.show()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg)
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
                    .padding(bottom = TellcatSpacing.sm)
                    .semantics { heading() }
            )

            // Filled, per D-025. Every other surface in the app is a tinted
            // container on a plain page, and the outlined field was the one
            // component asking to be read by its border instead.
            //
            // The indicator and the two-corner container are dropped in
            // `tellcatFieldColors`, which says why.
            TextField(
                value = title,
                colors = tellcatFieldColors(),
                shape = TellcatFieldShape,
                onValueChange = { typed ->
                    // A new parse is a new reminder, so an old dismissal has
                    // nothing left to be about. Without this, typing a second
                    // time after dismissing would silently suppress a reminder
                    // the user never declined. Only a change to the text: a
                    // moved cursor is not a new parse.
                    if (typed.text != title.text) reminderDismissed = false
                    title = typed
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
                    //
                    // **It teaches a time as well as a day**, D-112, written the
                    // way this phone writes times.
                    QuickAddPlaceholder(time = reminderTimeWords(ExampleTime, DateFormat.is24HourFormat(context)))
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
                            now = now,
                            fallbackDate = fallbackDate,
                            // D-101: "at six" is refused, D-070, and used to
                            // save without a word. Never alongside a reminder,
                            // so this cannot contradict the chip.
                            noMeridiem = !parsed.hasReminder && endsInBareHour(parsed.title)
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

            // The one control on the sheet besides Save. An offset is a
            // reminder too: keyed on the clock time alone, "in 2 hours" set a
            // reminder this sheet never showed and could not take back. D-075.
            //
            // **Always drawn since D-112**, so Add no longer moves down the
            // moment a time is read. Live only once there is a title, the test
            // Add itself uses: a reminder about nothing is not a capture.
            ReminderChip(
                parsed = parsed,
                today = today,
                now = now,
                enabled = parsed.title.isNotBlank(),
                onOpen = {
                    // The keyboard goes down so the sheet has the screen, and
                    // comes back when it closes.
                    keyboard?.hide()
                    isReminderOpen = true
                },
                onDismiss = { reminderDismissed = true },
                modifier = Modifier.padding(top = TellcatSpacing.xs)
            )

            Button(
                onClick = { onSave(parsed) },
                // The title is the one thing a task cannot do without, and it is
                // the title left after the day and the time are taken that has
                // to exist.
                enabled = parsed.title.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = TellcatSpacing.md)
            ) {
                Text(stringResource(R.string.quick_add_save))
            }
        }
    }

    // **Task Details' reminder sheet, writing into the field.** D-112. It opens
    // on the reminder the field holds, or on the day the field names, so
    // choosing again edits the run already there. Its Save writes the words and
    // its Clear does what the chip's cross does, unmark the run; it sets nothing
    // itself. No "Ring like an alarm": the field has no words for it, and the
    // switch is a tap away in Task Details once the task exists.
    if (isReminderOpen) {
        val held = parsed.reminderAt(today, now)
        ReminderSheet(
            reminderAt = held,
            defaultDay = held?.toLocalDate() ?: parsed.date ?: today,
            today = today,
            now = clock(),
            offersAlarm = false,
            onSet = { at, _ ->
                writeReminder(
                    text = title.text,
                    marked = parsed.markRange,
                    at = at,
                    today = today,
                    now = clock(),
                    twentyFourHour = DateFormat.is24HourFormat(context),
                    dayFirst = writesDayFirst(locale)
                )?.let { written ->
                    title = TextFieldValue(written.text, TextRange(written.text.length))
                    reminderDismissed = false
                }
                isReminderOpen = false
                reminderClosed++
            },
            onClear = {
                reminderDismissed = true
                isReminderOpen = false
                reminderClosed++
            },
            onDismiss = {
                isReminderOpen = false
                reminderClosed++
            }
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/** The time the placeholder's example is set for. */
private val ExampleTime: LocalTime = LocalTime.of(9, 0)

/**
 * Whether this phone writes a date day first, "6 November", or month first,
 * "November 6", so the words the chip writes look like the phone's own dates.
 * The parser reads both.
 */
private fun writesDayFirst(locale: Locale): Boolean {
    val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMd")
    return pattern.indexOf('d') < pattern.indexOf('M')
}

/**
 * The example in the empty field, D-112: a day and a time on the end of a title.
 *
 * Two lengths, and the longer one whenever it fits on a line. At large font
 * scales it does not, and an example cut off at "tomo…" teaches nothing, so the
 * shorter one keeps the time, which is the half that was never taught.
 *
 * **Decided by the line as it was laid out**, not measured in advance. Measured
 * against the width the field offered its placeholder, the long example always
 * appeared to fit, and on a 360dp phone at the default font size it was cut off.
 */
@Composable
private fun QuickAddPlaceholder(time: String) {
    val examples = listOf(
        stringResource(R.string.quick_add_placeholder, time),
        stringResource(R.string.quick_add_placeholder_short, time)
    )
    // Which example is showing. Steps to the shorter one when the longer runs
    // off the line, and starts again when the examples change.
    var shown by remember(examples) { mutableIntStateOf(0) }

    Text(
        text = examples[shown],
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && shown < examples.lastIndex) shown++
        }
    )
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
    now: LocalDateTime,
    fallbackDate: LocalDate?,
    noMeridiem: Boolean
) {
    // The day the save will use, worked out the way the save works it out. A
    // reminder schedules the task for the day it rings, D-075, so "call mom
    // 5pm" from Inbox reads "Scheduled for Today" here, and the line cannot
    // name a destination the task is not going to. The clock is read here for
    // the reason the chip reads it: D-030 can move the day.
    val day = if (parsed.isPlain) null else parsed.scheduledDate(fallbackDate, today, now)

    // Stacked: the supporting slot does not lay out more than one child.
    Column {
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

                // Nothing typed about when, so the destination is the surprising
                // part and this is the only branch that names one. Which it is
                // depends on the host: Today dates an undated capture, Inbox leaves
                // it undated.
                fallbackDate != null -> stringResource(R.string.quick_add_saved_to_today)
                else -> stringResource(R.string.quick_add_saved_to_inbox)
            }
        )

        // **What did not happen, and how to make it.** D-101. An hour with no am or
        // pm is refused rather than guessed, D-070, and the destination line alone
        // read as if the time had been taken. Below it rather than instead of it,
        // because where the task goes is still true.
        //
        // A polite live region, so TalkBack says it when it appears rather than
        // only when the user goes looking. Not the error colour, which the design
        // system keeps for destroying actions: nothing is wrong with the task.
        if (noMeridiem) {
            Text(
                text = stringResource(R.string.quick_add_no_meridiem),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

/**
 * The Reminder chip. D-011, and since D-112 always on the sheet.
 *
 * An `InputChip` rather than an `AssistChip`, because Material's input chip is
 * the one that represents a piece of information the user supplied and offers to
 * remove it. That is exactly what this is: the time came out of what they typed,
 * or was typed for them by this chip.
 *
 * **Unset, it reads "Reminder"** and opens the reminder sheet. **Set, it names
 * the moment in words**, opens the sheet to change it, and its cross unmarks the
 * run, as it always has. The chip is the reminder's only presence in text, so a
 * screen reader has to get the whole fact from it.
 *
 * One chip in both states rather than two, so it is one node whose words change:
 * a polite live region, and TalkBack says the new reminder when the field reads
 * one, typed or written.
 */
@Composable
private fun ReminderChip(
    parsed: CapturedTask,
    today: LocalDate,
    now: LocalDateTime,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The clock rather than a parameter, because this label has to say where
    // the reminder actually lands: D-030 resolves a time that has gone by to
    // tomorrow, and a chip still reading "Today" would be the disagreement
    // between the chip and the saved value that D-011 exists to prevent.
    val at = parsed.reminderAt(today, now)
    val time = at?.toLocalTime()?.format(rememberTimeFormat())
    val label = if (at == null) {
        stringResource(R.string.quick_add_reminder_unset)
    } else {
        stringResource(R.string.quick_add_reminder, scheduledDateLabel(at.toLocalDate(), today), time.orEmpty())
    }
    // What a screen reader hears: unset, the action; set, the reminder.
    val spoken = if (at == null) stringResource(R.string.quick_add_reminder_add) else label
    val openLabel = stringResource(
        if (at == null) R.string.quick_add_reminder_add else R.string.quick_add_reminder_change
    )
    val dismissLabel = stringResource(R.string.quick_add_reminder_dismiss)

    Box(modifier = modifier) {
        InputChip(
            selected = at != null,
            enabled = enabled,
            onClick = onOpen,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_alarm),
                    contentDescription = null,
                    modifier = Modifier.size(InputChipDefaults.IconSize)
                )
            },
            trailingIcon = if (at == null) null else {
                {
                    // Drawn here, taken by the target laid over it below.
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            },
            // **A button, read as one, never a checked control.** An input chip
            // drawn selected announced itself as a checked checkbox, so TalkBack
            // said "checked" and a double-tap silently took the reminder away.
            // It reads its words and names what a double-tap does.
            modifier = Modifier.clearAndSetSemantics {
                text = AnnotatedString(spoken)
                role = Role.Button
                liveRegion = LiveRegionMode.Polite
                if (enabled) {
                    onClick(label = openLabel) {
                        onOpen()
                        true
                    }
                } else {
                    disabled()
                }
            }
        )

        // **The cross is its own target**, so a tap on the chip changes the
        // reminder and a tap on the cross removes it. A full 48dp square laid
        // over the chip's end, because the cross is drawn at 18dp and the
        // touch target rule is 48.
        if (at != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(TellcatDimensions.TouchTargetMin)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = TellcatDimensions.TouchTargetMin / 2),
                        role = Role.Button,
                        onClickLabel = dismissLabel,
                        onClick = onDismiss
                    )
                    .semantics { contentDescription = dismissLabel }
            )
        }
    }
}

/**
 * The time, refreshed as each minute starts, so what the sheet says about a
 * reminder stays true while it is open.
 *
 * The chip and the line under the field read the clock to resolve a time of
 * day, D-030, and they used to read it only when typing redrew them. Typed at
 * 2:38:50 and saved at 2:39:05, "at 2:38" showed "Today at 2:38" and saved
 * for 2:38 tomorrow, because the save reads the clock again and by then the
 * minute was over: the sheet said one thing and stored another, which is what
 * D-011 forbids. Now the chip turns to "Tomorrow" as the minute ends, before
 * anything is saved.
 *
 * The minute is what matters because a reminder is chosen to the minute:
 * `nextReminderOccurrence` keeps the current one and moves anything older.
 */
@Composable
private fun rememberCurrentMinute(clock: () -> LocalDateTime): LocalDateTime {
    val minute by produceState(clock()) {
        while (true) {
            val current = clock()
            val nextMinute = current.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            // A millisecond past the boundary, so the read lands in the new
            // minute rather than on the last instant of the old one.
            delay(Duration.between(current, nextMinute).toMillis() + 1)
            value = clock()
        }
    }
    return minute
}
