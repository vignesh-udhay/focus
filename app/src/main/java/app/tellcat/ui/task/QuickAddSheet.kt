package app.tellcat.ui.task

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import app.tellcat.core.domain.MonthlyWeekday
import app.tellcat.core.domain.Recurrence
import app.tellcat.core.domain.RecurrenceEnd
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.core.domain.WrittenReminder
import app.tellcat.core.domain.firstOccurrenceFrom
import app.tellcat.core.domain.writeDay
import app.tellcat.core.domain.readToolRun
import app.tellcat.core.domain.TitleStandIn
import app.tellcat.core.text.RecurrenceStyle
import app.tellcat.core.text.recurrenceSummary
import app.tellcat.ui.component.CheckBoxDrawing
import java.time.DayOfWeek
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
 *
 * **Since D-121 the sheet has three tools on the keyboard beside Add**: When,
 * Repeat and Checklist. When replaces the chip and still only types, a day or a
 * day and a time, so the field stays the one setter of both. Repeat and the
 * checklist have no typed form, so the sheet holds them and Add hands them over
 * with the title. Every tool is optional; typing stays the fast path.
 */
/**
 * What Quick Add hands its host: the parsed title, and since D-121 the repeat
 * and the checklist the sheet holds, which have no typed form.
 */
data class QuickAddCapture(
    val parsed: CapturedTask,
    val recurrence: Recurrence? = null,
    // The lines as typed, blank ones already dropped.
    val checklist: List<String> = emptyList()
)

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
    onSave: (QuickAddCapture) -> Unit,
    modifier: Modifier = Modifier,
    // A parameter for the reason `TaskDetailsSheets` takes one: the behaviour
    // worth proving happens when a minute ends, and only a test can make that
    // happen on demand.
    clock: () -> LocalDateTime = LocalDateTime::now
) {
    val now = rememberCurrentMinute(clock)

    // A value rather than a string, so a tool can put the cursor after the
    // words it writes. D-112.
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    // Whether the user has cleared the reminder for what is currently typed.
    // Reset by typing, because the next keystroke makes a new parse and a
    // dismissal cannot outlive the text it was about.
    var reminderDismissed by rememberSaveable { mutableStateOf(false) }

    // **What the sheet holds itself**, D-121: a repeat and a checklist have no
    // words in the title, so they are kept here and handed over with it.
    var recurrence by rememberSaveable(stateSaver = RecurrenceSaver) { mutableStateOf<Recurrence?>(null) }
    var items by rememberSaveable(stateSaver = ItemsSaver) { mutableStateOf(emptyList<String>()) }
    // **The Checklist tool turns the checklist on and off**, the owner's ask
    // after trying it. Off hides the lines rather than dropping them, so a stray
    // tap loses nothing, and only lines showing when Add is pressed are saved.
    var checklistOn by rememberSaveable { mutableStateOf(false) }
    // The checklist line to give focus to next, or -1 for none.
    var focusItem by remember { mutableIntStateOf(-1) }
    // **Words a tool wrote before any title was typed**, D-121 as amended: the
    // owner picks the day or the repeat first, then types the task in front.
    var toolRun by rememberSaveable { mutableStateOf<String?>(null) }

    // The sheets the tools open, and a count of the times one has closed,
    // which is what hands focus back to the field. D-112.
    var isWhenOpen by rememberSaveable { mutableStateOf(false) }
    var isRepeatOpen by rememberSaveable { mutableStateOf(false) }
    var sheetClosed by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    // Recomputed as the user types: it decides what the field marks, what the
    // supporting line says, and what Add hands over, so the three can never
    // disagree.
    val parsed = (readToolRun(title.text, toolRun, today) ?: splitTrailingCapture(title.text, today)).let { capture ->
        if (reminderDismissed) capture.withoutReminder() else capture
    }
    val hasTitle = parsed.title.isNotBlank()

    // **D-101's line waits for a pause**, D-101 as amended. Typing "at 6pm"
    // passes through "at 6", and the line came and went under the user's
    // fingers. It shows once the typing stops on an hour with no am or pm.
    val endsBare = !parsed.hasReminder && endsInBareHour(parsed.title)
    var noMeridiemShown by remember { mutableStateOf(false) }
    LaunchedEffect(title.text, endsBare) {
        noMeridiemShown = false
        if (endsBare) {
            delay(NoMeridiemPause)
            noMeridiemShown = true
        }
    }

    fun save() {
        if (!hasTitle) return
        val checklist = if (checklistOn) items.map(String::trim).filter(String::isNotEmpty) else emptyList()
        onSave(QuickAddCapture(parsed, recurrence, checklist))
    }

    // What the tools write against: the title, or with none yet a stand-in,
    // so the words are checked as they would be read once one is typed.
    val writeText = if (hasTitle) title.text else TitleStandIn
    val writeMark = if (hasTitle) parsed.markRange else null

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

    // Words a tool writes go in as typed words would, with the cursor after
    // them, and a reminder cleared earlier is no longer cleared. With no title
    // yet, the words go in alone with the cursor in front of them, so what is
    // typed next is the title: "Call Sam| tomorrow at 9am".
    fun writeIn(written: WrittenReminder?) {
        if (written == null) return
        if (hasTitle) {
            title = TextFieldValue(written.text, TextRange(written.text.length))
        } else {
            title = TextFieldValue(" " + written.phrase, TextRange(0))
            toolRun = written.phrase
        }
        reminderDismissed = false
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
        // window of its own, and each tool's sheet is another over it.
        val keyboard = LocalSoftwareKeyboardController.current
        val windowInfo = LocalWindowInfo.current

        // **Focus and the keyboard come back to the field** when a tool's sheet
        // closes, D-112, with the cursor after any words it wrote. Once this
        // window has focus again: asked for while the other sheet's window still
        // held it, the field would take focus with no keyboard.
        LaunchedEffect(sheetClosed) {
            if (sheetClosed == 0) return@LaunchedEffect
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            focusRequester.requestFocus()
            keyboard?.show()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.xs)
        ) {
            // **Everything above the tools scrolls; the tools and Add do not.**
            // D-121: at 200% text with a long checklist, the row on the keyboard
            // is what must stay reachable.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // **The sheet's name, and the field's, per D-052.** It was the
                // field's floating label, where it repeated what the sheet was
                // for on every keystroke and cost a row of the input to do it.
                // As a heading it is drawn once and the field keeps its whole
                // container for what the user is typing.
                //
                // It also names the field. A `contentDescription` on an editable
                // node can replace the announcement of what has been typed, so
                // the heading above it is the safer way to say what the box is.
                Text(
                    text = stringResource(R.string.quick_add_title_label),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(bottom = TellcatSpacing.sm)
                        .semantics { heading() }
                )

                // Filled, per D-025, with the indicator and the two-corner
                // container dropped in `tellcatFieldColors`, which says why.
                TextField(
                    value = title,
                    colors = tellcatFieldColors(),
                    shape = TellcatFieldShape,
                    onValueChange = { typed ->
                        // A new parse is a new reminder, so an old dismissal has
                        // nothing left to be about. Only a change to the text: a
                        // moved cursor is not a new parse.
                        if (typed.text != title.text) reminderDismissed = false
                        title = typed
                    },
                    // No label. The heading above carries the name. D-052.
                    placeholder = {
                        // The one place the field's own trick is taught, D-112:
                        // a day and a time written on the end of the title, the
                        // time written the way this phone writes times.
                        QuickAddPlaceholder(time = reminderTimeWords(ExampleTime, DateFormat.is24HourFormat(context)))
                    },
                    singleLine = true,
                    visualTransformation = markTheRun,
                    // Null rather than an empty slot until there is something to
                    // describe, tied to the test Add uses. D-053.
                    supportingText = if (!hasTitle) {
                        // Only a tool's words so far: say what comes next.
                        if (parsed.isPlain) null else {
                            { Text(stringResource(R.string.quick_add_title_first)) }
                        }
                    } else {
                        {
                            QuickAddSupportingText(
                                parsed = parsed,
                                today = today,
                                now = now,
                                fallbackDate = fallbackDate,
                                recurrence = recurrence,
                                // D-101: "at six" is refused, D-070, and used to
                                // save without a word.
                                noMeridiem = noMeridiemShown
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    // **Done saves**, guarded by Add's own rule, so the two
                    // commit paths agree about what is saveable.
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                // **The checklist, written inline**, D-121, under the title it
                // belongs to, while the tool has it on. Enter on a line adds the
                // next; its cross, or Backspace on an empty line, removes it.
                if (checklistOn) items.forEachIndexed { index, item ->
                    QuickAddChecklistLine(
                        text = item,
                        number = index + 1,
                        focus = focusItem == index,
                        onFocused = { if (focusItem == index) focusItem = -1 },
                        onTextChange = { typed ->
                            items = items.toMutableList().also { it[index] = typed }
                        },
                        onNext = {
                            items = items.toMutableList().also { it.add(index + 1, "") }
                            focusItem = index + 1
                        },
                        onRemove = {
                            items = items.toMutableList().also { it.removeAt(index) }
                            when {
                                // The last line gone is the checklist gone.
                                items.isEmpty() -> {
                                    checklistOn = false
                                    focusRequester.requestFocus()
                                }
                                index > 0 -> focusItem = index - 1
                                else -> focusItem = 0
                            }
                        }
                    )
                }
            }

            // **The tools, on the keyboard beside Add.** D-121, after TickTick:
            // When, Repeat, Checklist. A set tool shows it in place, so nothing
            // above grows as tools are set; the supporting line says it all in
            // words. Every tool is live from the start, D-121 as amended: the
            // owner picks a day or a repeat first and types the task after.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TellcatSpacing.xs)
            ) {
                // The tools take what Add leaves, and When what the other two
                // leave, so a long When value is cut short and never Add.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xxs),
                    modifier = Modifier.weight(1f)
                ) {
                    // The weight sits on a Box of its own: a TooltipBox does not
                    // hand it to the Row, which then measured When first, at full
                    // width, and left Checklist none. Found on the phone, D-121.
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        WhenTool(
                            parsed = parsed,
                            today = today,
                            now = now,
                            enabled = true,
                            onClick = {
                                // The keyboard goes down so the sheet has the screen, and
                                // comes back when it closes.
                                keyboard?.hide()
                                isWhenOpen = true
                            }
                        )
                    }
                    QuickAddTool(
                        icon = R.drawable.ic_repeat,
                        name = stringResource(R.string.quick_add_repeat),
                        value = recurrence?.let { recurrenceSummary(it, today, RecurrenceStyle.Sentence) },
                        enabled = true,
                        onClick = {
                            keyboard?.hide()
                            isRepeatOpen = true
                        }
                    )
                    QuickAddTool(
                        icon = R.drawable.ic_checklist,
                        name = stringResource(R.string.quick_add_checklist),
                        value = if (!checklistOn) {
                            null
                        } else {
                            val written = items.count(String::isNotBlank)
                            if (written == 0) {
                                stringResource(R.string.quick_add_checklist_on)
                            } else {
                                pluralStringResource(R.plurals.quick_add_checklist_items, written, written)
                            }
                        },
                        spokenValueIsWhole = true,
                        enabled = true,
                        onClick = {
                            if (checklistOn) {
                                // Off: hidden, kept, and not saved.
                                checklistOn = false
                                focusRequester.requestFocus()
                            } else {
                                // On: the lines as they were, or a first one,
                                // with the keyboard on the first empty line.
                                checklistOn = true
                                if (items.isEmpty()) items = listOf("")
                                focusItem = items.indexOfFirst(String::isBlank).takeIf { it >= 0 } ?: items.lastIndex
                            }
                        }
                    )
                }

                Button(
                    onClick = { save() },
                    // The title is the one thing a task cannot do without, and
                    // it is the title left after the day and the time are taken
                    // that has to exist.
                    enabled = hasTitle
                ) {
                    Text(stringResource(R.string.quick_add_save))
                }
            }
        }
    }

    // **When: Task Details' reminder sheet, writing into the field**, D-112 and
    // D-121. It opens on the reminder the field holds, or on the day it names,
    // with the time optional: a day alone is written as the day, a day and a
    // time as both. Its Clear does what clearing a reminder always did, unmark
    // the time. No "Ring like an alarm" and no early reminder: the field has no
    // words for them, and Task Details is the place to set them.
    if (isWhenOpen) {
        val held = parsed.reminderAt(today, now)
        ReminderSheet(
            reminderAt = held,
            defaultDay = held?.toLocalDate() ?: parsed.date ?: fallbackDate ?: today,
            today = today,
            now = clock(),
            offersAlarm = false,
            offersEarly = false,
            timeOptional = true,
            // A day the tool wrote before any title can be cleared too: there
            // is no reminder, but there are words to take back out.
            canClear = held != null || (!hasTitle && toolRun != null),
            onSet = { at, _, _ ->
                writeIn(
                    writeReminder(
                        text = writeText,
                        marked = writeMark,
                        at = at,
                        today = today,
                        now = clock(),
                        twentyFourHour = DateFormat.is24HourFormat(context),
                        dayFirst = writesDayFirst(locale)
                    )
                )
                isWhenOpen = false
                sheetClosed++
            },
            onSetDay = { day ->
                writeIn(writeDay(writeText, writeMark, day, today, writesDayFirst(locale)))
                isWhenOpen = false
                sheetClosed++
            },
            onClear = {
                if (hasTitle) {
                    reminderDismissed = true
                } else {
                    // Only the tool's words were there: clearing takes them.
                    title = TextFieldValue()
                    toolRun = null
                }
                isWhenOpen = false
                sheetClosed++
            },
            onDismiss = {
                isWhenOpen = false
                sheetClosed++
            }
        )
    }

    // **Repeat: the Repeat sheet**, D-121, its common rules read from the day
    // in the field, or the day the task would land on.
    if (isRepeatOpen) {
        val dayInField = parsed.reminderAt(today, now)?.toLocalDate() ?: parsed.date
        RepeatSheet(
            selected = recurrence,
            today = today,
            anchorDay = dayInField ?: fallbackDate ?: today,
            onPick = { picked ->
                recurrence = picked
                // **A repeating task always has a day, on its rule.** With none
                // in the field the rule's first day is written in as words,
                // unless the task lands on it anyway (Today, a rule that falls
                // today). With one that the rule does not fall on, D-123, it is
                // rewritten as the rule's first day from it, keeping any time:
                // "On the last day" on the 2nd of November writes the 30th.
                if (picked != null) {
                    val first = picked.firstOccurrenceFrom(dayInField ?: today)
                    val held = parsed.reminderAt(today, now)
                    when {
                        dayInField == null -> if (first != fallbackDate) {
                            writeIn(writeDay(writeText, writeMark, first, today, writesDayFirst(locale)))
                        }
                        first == dayInField -> Unit
                        held != null -> writeIn(
                            writeReminder(
                                text = writeText,
                                marked = writeMark,
                                at = first.atTime(held.toLocalTime()),
                                today = today,
                                now = clock(),
                                twentyFourHour = DateFormat.is24HourFormat(context),
                                dayFirst = writesDayFirst(locale)
                            )
                        )
                        else -> writeIn(writeDay(writeText, writeMark, first, today, writesDayFirst(locale)))
                    }
                }
                isRepeatOpen = false
                sheetClosed++
            },
            onDismiss = {
                isRepeatOpen = false
                sheetClosed++
            }
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * A tool in the row: its icon on the tonal container once set. D-121.
 *
 * One node, a button, that says the tool and, once set, what it holds:
 * "Repeat, every month on the last Friday". Its name is also a tooltip on a
 * long press, because an icon alone is not universal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddTool(
    @DrawableRes icon: Int,
    name: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    // When [value] already names the tool ("Checklist, 3 items").
    spokenValueIsWhole: Boolean = false,
    modifier: Modifier = Modifier
) {
    val spoken = when {
        value == null -> name
        spokenValueIsWhole -> value
        else -> stringResource(R.string.quick_add_tool_set, name, value)
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = if (value != null) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors(),
            modifier = Modifier.semantics { contentDescription = spoken }
        ) {
            Icon(painter = painterResource(icon), contentDescription = null)
        }
    }
}

/**
 * When, the one tool that shows its value in the row, D-121, the way TickTick's
 * date tool becomes "Fri, 9:00AM": a day, or a day and a time. At large text the
 * value no longer fits beside Add, so the tool is its icon on the tonal
 * container and the supporting line, which wraps, carries the words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenTool(
    parsed: CapturedTask,
    today: LocalDate,
    now: LocalDateTime,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val at = parsed.reminderAt(today, now)
    val day = at?.toLocalDate() ?: parsed.date
    val name = stringResource(R.string.quick_add_when)
    val value = when {
        at != null -> "${scheduledDateLabel(at.toLocalDate(), today)}, ${at.toLocalTime().format(rememberTimeFormat())}"
        day != null -> scheduledDateLabel(day, today)
        else -> null
    }
    val fits = LocalDensity.current.fontScale <= WhenValueMaxFontScale

    if (value == null || !fits) {
        QuickAddTool(icon = R.drawable.ic_event, name = name, value = value, enabled = enabled, onClick = onClick, modifier = modifier)
        return
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier
                .heightIn(min = TellcatDimensions.TouchTargetMin)
                .semantics { contentDescription = "$name, $value" }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_event),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
            )
        }
    }
}

/** How long the typing stops before D-101's line says an hour has no am or pm. */
private const val NoMeridiemPause = 1_000L

/** Beyond this the When value would crowd Add off the row at 360dp. */
private const val WhenValueMaxFontScale = 1.3f

/**
 * A checklist line in Quick Add, D-121: D-118's 16dp square check, drawn and not
 * pressable (nothing is done yet), and the item's text. Its hint, while empty,
 * teaches the gesture, as TickTick's does.
 */
@Composable
private fun QuickAddChecklistLine(
    text: String,
    number: Int,
    focus: Boolean,
    onFocused: () -> Unit,
    onTextChange: (String) -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit
) {
    val requester = remember { FocusRequester() }
    // Named by its words, as Task Details' checklist names it, or by its place
    // while it has none.
    val removeLabel = if (text.isNotBlank()) {
        stringResource(R.string.task_checklist_remove, text.trim())
    } else {
        stringResource(R.string.quick_add_checklist_remove_item, number)
    }
    LaunchedEffect(focus) {
        if (focus) {
            requester.requestFocus()
            onFocused()
        }
    }
    val label = stringResource(R.string.quick_add_checklist_item, number)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TellcatDimensions.TouchTargetMin)
            .padding(start = TellcatSpacing.md)
    ) {
        CheckBoxDrawing(checked = false, boxSize = ChecklistCheckSize, corner = ChecklistCheckCorner)
        Box(modifier = Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.quick_add_checklist_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { typed -> onTextChange(typed.replace("\n", "")) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .onPreviewKeyEvent { event ->
                        val removes = event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace && text.isEmpty()
                        if (removes) onRemove()
                        removes
                    }
                    .semantics { contentDescription = label }
            )
        }
        // **One tap removes a line**, the owner's ask: Backspace on an empty
        // line was the only way, and nobody would find it.
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = removeLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A repeat across a configuration change, as its fields. */
private val RecurrenceSaver = listSaver<Recurrence?, Any>(
    save = { rule ->
        if (rule == null) {
            emptyList()
        } else {
            val end = rule.end
            listOf(
                rule.unit.name,
                rule.interval,
                rule.weekdays.joinToString(",") { it.name },
                rule.monthlyWeekday?.ordinal ?: 0,
                rule.monthlyWeekday?.day?.name ?: "",
                when (end) {
                    is RecurrenceEnd.OnDate -> "date:" + end.date
                    is RecurrenceEnd.AfterOccurrences -> "count:" + end.count
                    else -> "never"
                }
            )
        }
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            null
        } else {
            val end = saved[5] as String
            Recurrence(
                unit = RecurrenceUnit.valueOf(saved[0] as String),
                interval = saved[1] as Int,
                weekdays = (saved[2] as String).split(",").filter(String::isNotEmpty).map(DayOfWeek::valueOf).toSet(),
                monthlyWeekday = (saved[4] as String).takeIf(String::isNotEmpty)?.let {
                    MonthlyWeekday(saved[3] as Int, DayOfWeek.valueOf(it))
                },
                end = when {
                    end.startsWith("date:") -> RecurrenceEnd.OnDate(LocalDate.parse(end.removePrefix("date:")))
                    end.startsWith("count:") -> RecurrenceEnd.AfterOccurrences(end.removePrefix("count:").toInt())
                    else -> RecurrenceEnd.Never
                }
            )
        }
    }
)

/** The checklist lines across a configuration change. */
private val ItemsSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

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
    noMeridiem: Boolean,
    // The repeat the sheet holds, said in words under the day. D-121.
    recurrence: Recurrence? = null
) {
    val at = parsed.reminderAt(today, now)
    val timeFormat = rememberTimeFormat()
    // The day the save will use, worked out the way the save works it out. A
    // reminder schedules the task for the day it rings, D-075, so "call mom
    // 5pm" from Inbox reads "Scheduled for Today" here, and the line cannot
    // name a destination the task is not going to. The clock is read here for
    // the reason the chip reads it: D-030 can move the day.
    val day = if (parsed.isPlain) null else parsed.scheduledDate(fallbackDate, today, now)

    // Stacked: the supporting slot does not lay out more than one child. A
    // polite live region, D-112's, moved here from the chip by D-121: this is
    // now where the whole capture is said in words.
    Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = when {
                // **What did not happen, and how to make it.** D-101. An hour with
                // no am or pm is refused rather than guessed, D-070. In this line's
                // place rather than under it, D-101 as amended: a line added and
                // taken away moved everything above it. Not the error colour,
                // which the design system keeps for destroying actions: nothing
                // is wrong with the task.
                noMeridiem -> stringResource(R.string.quick_add_no_meridiem)

                // The day and the time together, now that no chip names the
                // reminder. D-121.
                at != null -> stringResource(
                    R.string.quick_add_scheduled_for_at,
                    scheduledDateLabel(at.toLocalDate(), today),
                    at.toLocalTime().format(timeFormat)
                )

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

        recurrence?.let { rule ->
            Text(text = recurrenceSummary(rule, today, RecurrenceStyle.Sentence))
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
