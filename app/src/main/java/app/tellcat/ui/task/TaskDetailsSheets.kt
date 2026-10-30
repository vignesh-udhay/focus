package app.tellcat.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tellcat.R
import app.tellcat.ui.component.tellcatFieldColors
import app.tellcat.ui.component.TellcatFieldShape
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.domain.nextReminderOccurrence
import app.tellcat.core.domain.endOfWeek
import app.tellcat.core.domain.thisWeekend
import app.tellcat.core.domain.snoozedUntil
import app.tellcat.core.domain.snoozeOptionsInApp
import app.tellcat.core.domain.SnoozeOption
import app.tellcat.core.domain.MorningHour
import app.tellcat.ui.component.PlanRowGroup
import app.tellcat.ui.component.TaskDatePickerDialog
import app.tellcat.ui.component.durationLabel
import app.tellcat.ui.component.scheduledDateLabel
import app.tellcat.ui.component.rememberTimeFormat
import app.tellcat.ui.component.PlanRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

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
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.sm)
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
            val options = listOf(
                DateOption(stringResource(kind.clearRes), null),
                DateOption(stringResource(R.string.task_date_today), today),
                DateOption(stringResource(R.string.task_date_tomorrow), today.plusDays(1)),
                DateOption(stringResource(kind.farPresetRes), kind.farPreset(today))
            )

            // **The first preset holding the date is the one that lights, and
            // only it.** Two cells can mean one day: `This weekend` is the
            // coming Saturday, so on a Friday it is also Tomorrow, and
            // `End of week` is the coming Friday, so on a Thursday it is too.
            // One day in seven, per sheet.
            //
            // Comparing each cell against the date lit both of them, which made
            // the sheet look broken on exactly the day it was most ordinary.
            // Taking the first match instead leaves the more precise label lit,
            // which is the one that describes the date better: on a Friday,
            // Saturday is better called Tomorrow than This weekend.
            //
            // The dates themselves are not the problem and are not touched.
            // `DatePresets.kt` keeps both strictly after today so a preset
            // always moves the task, and `DatePresetsTest` defends that. This
            // is a question about which button looks pressed, so it is answered
            // here rather than in the domain.
            val litIndex = options.indexOfFirst { option -> option.date == selected }

            options.chunked(2).forEachIndexed { rowIndex, pair ->
                DateOptionRow(
                    options = pair,
                    firstIndex = rowIndex * 2,
                    litIndex = litIndex,
                    onPick = onPick
                )
            }

            // A date the presets cannot express is held here, so this control
            // has to be able to say so. Otherwise the sheet reads "Choose a
            // date" over four unlit presets and cannot tell a task scheduled
            // three weeks out from one scheduled for nothing at all.
            val holdsValue = selected != null && litIndex == -1

            ChooseDateButton(
                label = if (holdsValue) {
                    scheduledDateLabel(selected, today)
                } else {
                    stringResource(R.string.task_date_choose)
                },
                holdsValue = holdsValue,
                onClick = { isPickerOpen = true }
            )
        }
    }
}

/**
 * The control that opens the Material date picker, and says what it holds.
 *
 * **Outlined while it is only an invitation, filled once it carries the value.**
 * It is the one control in the sheet that leaves for a picker rather than
 * answering the question here, and the calendar says which picker; that is why
 * it is the only outlined thing on the sheet. But a date none of the four
 * presets can express is held nowhere else, so when it holds one it takes the
 * same filled treatment a chosen preset takes.
 *
 * The rule that falls out is worth stating, because it is what makes the sheet
 * readable: **exactly one control here is ever lit.** Nothing set lights the
 * clear preset, a preset date lights that preset, and anything else lights this.
 *
 * The wording comes from `scheduledDateLabel`, the same helper the Plan row
 * behind the sheet reads, so the row and the sheet cannot describe one date two
 * ways.
 *
 * This is the defect D-026 fixed in the Duration sheet, left standing in its
 * sibling. There it was a custom estimate that lit no segment and appeared
 * nowhere; here it was a custom date that lit no preset and appeared nowhere.
 * Fixing one and not looking at the other is how a pair of sheets built from one
 * design drift into two.
 */
@Composable
private fun ChooseDateButton(
    label: String,
    holdsValue: Boolean,
    onClick: () -> Unit,
    // The calendar, or the clock for the snooze pane's picked time. D-109.
    icon: Int = R.drawable.ic_today
) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = TellcatDimensions.ActionHeight)
        .padding(top = TellcatSpacing.xs)

    val content: @Composable RowScope.() -> Unit = {
        Icon(
            painter = painterResource(icon),
            // The label beside it already names the action.
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        // One line, because a long date wrapping would grow the button past the
        // height every other action on the screen shares.
        Text(text = label, maxLines = 1)
    }

    if (holdsValue) {
        Button(onClick = onClick, modifier = modifier, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, content = content)
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
 *
 * Takes [litIndex] rather than the selected date, so a cell cannot decide on its
 * own whether it is lit. Two cells can hold the same day, and each answering
 * that question separately is what lit both.
 */
@Composable
private fun DateOptionRow(
    options: List<DateOption>,
    firstIndex: Int,
    litIndex: Int,
    onPick: (LocalDate?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xs)) {
        options.forEachIndexed { offset, option ->
            PresetButton(
                label = option.label,
                selected = firstIndex + offset == litIndex,
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
        contentPadding = PaddingValues(horizontal = TellcatSpacing.xs),
        modifier = modifier.heightIn(min = TellcatDimensions.ActionHeight)
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
 * Four presets and a custom entry, as the board draws it, plus `None`.
 *
 * Clearing is one of the presets rather than a separate control, because "no
 * estimate" is a real value this app designs for: `focus.md` gives an
 * unestimated task its own open-ended session, so it is a choice rather than an
 * omission. The board offers no way to clear a duration at all, which is the
 * same gap D-018 found in the Scheduled sheet and fixed the same way.
 *
 * **Custom is a second state of this sheet, not a dialog over it.** The board
 * names its frames "SAME ModalBottomSheet" and draws a back arrow, and one
 * window is what the earlier build's own comment was reaching for when it
 * warned against stacking: an `AlertDialog` over a `ModalBottomSheet` is two
 * windows, two scrims, and two things back could mean. Here back is one
 * [BackHandler] on one sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationSheet(
    selectedMinutes: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var isCustomOpen by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        // Back returns to the presets rather than closing the sheet, which is
        // what the arrow in the corner promises. Only while that state is
        // showing; otherwise the sheet's own dismissal stands.
        BackHandler(enabled = isCustomOpen) { isCustomOpen = false }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.sm)
        ) {
            if (isCustomOpen) {
                CustomDurationPane(
                    initialMinutes = selectedMinutes,
                    onBack = { isCustomOpen = false },
                    onSet = onPick
                )
            } else {
                DurationPresetPane(
                    selectedMinutes = selectedMinutes,
                    onPick = onPick,
                    onOpenCustom = { isCustomOpen = true }
                )
            }
        }
    }
}

/**
 * The preset state: a connected group of five, then the Custom row.
 */
@Composable
private fun DurationPresetPane(
    selectedMinutes: Int?,
    onPick: (Int?) -> Unit,
    onOpenCustom: () -> Unit
) {
    Text(
        text = stringResource(R.string.task_duration),
        style = MaterialTheme.typography.titleLarge
    )

    DurationPresetGroup(selectedMinutes = selectedMinutes, onPick = onPick)

    // A row rather than a button, so the sheet can say what a custom estimate
    // currently is. The presets cannot: 1h 20m lights none of them, and the
    // sheet used to show five unselected buttons and no sign of the value the
    // task actually held.
    PlanRowGroup(modifier = Modifier.padding(top = TellcatSpacing.xs)) {
        PlanRow(
            label = stringResource(R.string.task_duration_custom),
            value = customDurationValue(selectedMinutes),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            onClick = onOpenCustom
        )
    }
}

/**
 * The five choices, as one connected control.
 *
 * **`ButtonGroup` rather than a row of buttons**, which is the component the
 * board draws and the one Material means for a small set of mutually exclusive
 * choices. The hand-rolled version it replaced put five equal-weight `Button`s
 * in a `Row` with `maxLines = 1` and no overflow behaviour, so running out of
 * room could only clip a label.
 *
 * `expressive-components.md` forbids exactly that: a row of choices "must not
 * overflow at large font scales, and must not truncate a label to avoid doing
 * so". `ButtonGroup` answers it by moving what does not fit into a menu behind
 * an indicator, so every option stays reachable and none is cut.
 *
 * **In practice it never has to.** Measured on an emulator at every font scale
 * from 100% to 200%, all five stay laid out inside the sheet's 380dp. The menu
 * is insurance against a sixth preset, not something a user meets. That was a
 * one-off measurement with nothing guarding it; D-026 says how to retake it,
 * and why counting nodes is the wrong way.
 *
 * The buttons themselves are `toggleableItem`'s own default content, so the
 * leading, middle and trailing shapes that make five buttons read as one
 * control come from Material rather than from a `when` on the index here.
 */
@Composable
private fun DurationPresetGroup(selectedMinutes: Int?, onPick: (Int?) -> Unit) {
    val options: List<Int?> = listOf(null) + DurationPresets
    val noneLabel = stringResource(R.string.task_duration_none)

    // Resolved here rather than inside the group: `ButtonGroupScope` is a
    // builder like `LazyListScope`, not a composable scope, so `durationLabel`
    // cannot be called from it.
    //
    // Through `durationLabel`, so this reads 45m and 1h like every other
    // duration in the app rather than "45 min", which nothing else says.
    val labels = options.map { minutes ->
        if (minutes == null) noneLabel else durationLabel(minutes).text
    }

    // **These buttons announce "45m" where the task rows announce "45 minutes",
    // and it is not fixable through this API.** `DurationLabel` says in its own
    // KDoc that the spoken form has to be attached as a content description, and
    // `TaskListRow` does attach it. `ButtonGroupScope.toggleableItem` takes
    // `checked`, `label`, `onCheckedChange`, `icon`, `weight` and `enabled`, and
    // no modifier, so there is nowhere to hang one.
    //
    // The only opening is `customItem`, which takes a composable and would mean
    // building the connected toggles by hand. That is exactly what adopting
    // `ButtonGroup` was for, per the note above: the shape and the control come
    // from Material rather than from a `when` on the index. Trading that back for
    // "45 minutes" over "45m" is the wrong way round, so this is recorded rather
    // than fixed. Found in the Phase 5 accessibility pass.

    // **No `fillMaxWidth`, and that is load-bearing rather than tidying.** It
    // makes the width constraint tight, `minWidth == maxWidth`, and when the
    // items do not fit `ButtonGroup` takes its overflow branch and copies the
    // constraints with a smaller `maxWidth` while leaving `minWidth` alone. That
    // is `maxWidth < minWidth`, which `Constraints` throws on, so the group
    // crashed in exactly the case it was adopted to handle.
    //
    // Without it the group inherits the column's own constraints, which are
    // `minWidth = 0` and `maxWidth` the column's width: a maximum to fit inside
    // and no minimum to violate.
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, minutes ->
            toggleableItem(
                checked = selectedMinutes == minutes,
                label = labels[index],
                onCheckedChange = { onPick(minutes) }
            )
        }
    }
}

/**
 * What the Custom row shows on its right.
 *
 * The estimate, but only when no preset holds it. A task set to 45m would
 * otherwise read "Custom  45m" one line under a lit 45m button, which says the
 * value was typed when it was pressed.
 */
@Composable
private fun customDurationValue(selectedMinutes: Int?): String =
    if (selectedMinutes != null && selectedMinutes !in DurationPresets) {
        durationLabel(selectedMinutes).text
    } else {
        ""
    }

/** The four the board draws. Short enough that a fifth would not fit the row. */
private val DurationPresets = listOf(15, 30, 45, 60)

/**
 * A duration in hours and minutes, for the case the four presets do not cover.
 *
 * **The same sheet, showing something else**, which is how the board draws it:
 * a back arrow, the title, two fields and Done. The version this replaces was
 * an `AlertDialog` raised over the open sheet, and its own comment gave the
 * reason not to do that: a modal sheet on Android is a dialog with its own
 * window, so stacking darkens the scrim twice and leaves back ambiguous. That
 * is an argument against a second window, and a dialog over a sheet is two
 * windows. Swapping the content is one.
 *
 * It also drops a confirmation step from a screen D-018 made commit-as-you-go.
 * Done writes and the sheet closes, like every preset beside it; there is no
 * Cancel, because leaving is the back arrow or the scrim, and nothing has been
 * written until Done.
 *
 * Two fields, because "90" is ambiguous between an hour and a half and an hour
 * and thirty of something. Both accept digits only.
 *
 * Zero is not a duration and Done refuses it. `editTask` refuses it too, so a
 * value that slipped through would be dropped silently, which is worse than a
 * disabled button that says nothing happened.
 */
@Composable
private fun CustomDurationPane(
    initialMinutes: Int?,
    onSet: (Int) -> Unit,
    onBack: () -> Unit
) {
    var hours by rememberSaveable { mutableStateOf(((initialMinutes ?: 0) / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf(((initialMinutes ?: 0) % 60).toString()) }

    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)

    Row(
        horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.task_duration_custom_back)
            )
        }

        Text(
            text = stringResource(R.string.task_duration_custom_title),
            style = MaterialTheme.typography.titleLarge
        )
    }

    Text(
        text = stringResource(R.string.task_duration_custom_supporting),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xs)) {
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

    Button(
        onClick = { onSet(total) },
        enabled = total > 0,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TellcatDimensions.ActionHeight)
            .padding(top = TellcatSpacing.xs)
    ) {
        Text(stringResource(R.string.task_duration_done))
    }
}

/** One digits-only field. Filtering on input beats validating after it. */
@Composable
private fun DurationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        // Digits only, capped at three, so nothing has to be rejected later and
        // no error state is needed for text the field never accepted.
        onValueChange = { typed ->
            if (typed.all(Char::isDigit) && typed.length <= 3) onValueChange(typed)
        },
        label = { Text(label) },
        colors = tellcatFieldColors(),
        shape = TellcatFieldShape,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/**
 * When to interrupt the user: a day and a time, as two rows.
 *
 * **A sheet of rows rather than a dialog holding two pickers.** D-030. The
 * first build of this put day presets above the clock inside `TimePickerDialog`
 * and the day controls took the top third of a window that exists to pick a
 * time, with the least-used option, the calendar, drawn as its heaviest
 * control. Two values want two rows; a picker opens when a row is tapped, which
 * is how every other Plan row on this screen already behaves.
 *
 * **The day is the fix, not a convenience.** This control used to select a time
 * alone and take its day from the task, so a task scheduled for a day already
 * gone could only take a reminder on that day. `reminderTrigger` clamped the
 * result to now and it rang immediately: two reminders on a OnePlus 8T were
 * recorded as placed at 03:20 and 11:00 and delivered five seconds later.
 * `PRODUCT.md` also says a reminder is independent of a scheduled date, and a
 * control with no date of its own cannot say that.
 *
 * **Two behaviours, and the difference between them is who chose the day.**
 * Until the user opens the Day row, the day follows the time: pick 9am at 6pm
 * and the row reads Tomorrow. That is a shown correction rather than a silent
 * one, sitting in the row it is about, and undone by opening the row and
 * tapping Today. Once the user has chosen, the day is honoured exactly, and a
 * moment already gone disables Save and says why rather than storing a promise
 * the app has already decided it cannot keep.
 *
 * **Where it opens is read off the clock, not held as a constant.** D-071.
 * [suggestedReminderTime] seeds the Time row with the next whole hour while the
 * day on offer is today, so an ordinary open reads Today at a time the user can
 * still use, and the correction above is left to the cases it was written for.
 *
 * **A Save, for the reason `RepeatSheet` has one.** D-018 commits as you go
 * because every other row is one field set by one choice. A day and a time only
 * mean something together, and writing each as it is tapped would push the task
 * through real saved states nobody asked for, each rescheduling an alarm.
 *
 * No summary line above the rows. The two rows say the whole thing between
 * them, and a line reading "Tomorrow, 9:00 AM" over rows reading Tomorrow and
 * 9:00 AM is the restatement D-026 refused for Duration's hero readout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderSheet(
    reminderAt: LocalDateTime?,
    defaultDay: LocalDate,
    today: LocalDate,
    // The moment, and whether it rings like an alarm. D-102: the switch is
    // part of the reminder and saved with it, for the reason this sheet has a
    // Save at all.
    onSet: (LocalDateTime, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    // Defaulted rather than threaded from the screen, because every caller
    // wants the clock and only a test wants anything else. It is a parameter at
    // all for the reason `Reminders.kt` and `TaskQueries.kt` take one: the
    // behaviour worth proving here is what this does at six in the evening, and
    // a sheet that reads a clock it owns can only be tested by waiting.
    now: LocalDateTime = LocalDateTime.now(),
    // Whether the reminder rings like an alarm now. Off for a new one. D-102.
    ringsLikeAlarm: Boolean = false,
    // Whether "Snooze…" is offered: the reminder has rung and waits for an
    // answer, or is snoozed. D-109.
    canSnooze: Boolean = false,
    onSnooze: (LocalDateTime) -> Unit = {},
    // Whether "Ring like an alarm" is offered. Off when Quick Add opens the
    // sheet, D-112: what is chosen there is written into the field as words,
    // and there are no words for ringing like an alarm.
    offersAlarm: Boolean = true
) {
    var ringsAsAlarm by rememberSaveable { mutableStateOf(ringsLikeAlarm) }

    // Held as primitives so `rememberSaveable` needs no saver of its own, and
    // because the pair is the whole draft: a chosen day, or [NoDayChosen] while
    // the time is still deciding it, and the time itself.
    var chosenDay by rememberSaveable { mutableStateOf(NoDayChosen) }
    var timeOfDay by rememberSaveable {
        mutableStateOf(
            (reminderAt?.toLocalTime() ?: suggestedReminderTime(defaultDay, now)).toSecondOfDay()
        )
    }

    // **The time follows the day until the user picks one.** D-113. A new
    // reminder opens on the next whole hour, D-071, and choosing Tomorrow used
    // to keep it: noon tomorrow, which nobody asks for, so the morning case
    // cost three more taps every time. Now a day chosen for a new reminder
    // takes that day's suggestion, nine in the morning on any day but today.
    // A reminder that already has a time, or a time picked from the clock,
    // is the user's and stays where it is.
    var timePicked by rememberSaveable { mutableStateOf(reminderAt != null) }

    fun chooseDay(day: LocalDate) {
        chosenDay = day.toEpochDay()
        if (!timePicked) timeOfDay = suggestedReminderTime(day, now).toSecondOfDay()
    }

    var pane by rememberSaveable { mutableStateOf(ReminderPane.MAIN) }
    var isTimeOpen by rememberSaveable { mutableStateOf(false) }
    var isDatePickerOpen by rememberSaveable { mutableStateOf(false) }

    val time = LocalTime.ofSecondOfDay(timeOfDay.toLong())

    // Not chosen: the time picks the day, and the answer is never in the past.
    // Chosen: the day is taken as given, and it is allowed to be wrong.
    val at = if (chosenDay == NoDayChosen) {
        nextReminderOccurrence(defaultDay.atTime(time), now)
    } else {
        LocalDate.ofEpochDay(chosenDay).atTime(time)
    }

    // To the minute, as `nextReminderOccurrence` counts it: the minute the user
    // is in has not passed, and a reminder chosen for it rings at once.
    val hasPassed = at.isBefore(now.truncatedTo(ChronoUnit.MINUTES))

    if (isTimeOpen) {
        ReminderTimePickerDialog(
            time = time,
            onDismiss = { isTimeOpen = false },
            onPicked = { picked ->
                isTimeOpen = false
                timeOfDay = picked.toSecondOfDay()
                timePicked = true
            }
        )
    }

    if (isDatePickerOpen) {
        TaskDatePickerDialog(
            initialDate = at.toLocalDate(),
            onDismiss = { isDatePickerOpen = false },
            onPicked = { picked ->
                isDatePickerOpen = false
                chooseDay(picked)
                pane = ReminderPane.MAIN
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
        // Back returns to the rows rather than closing the sheet, matching the
        // arrow the day pane draws, exactly as `RepeatSheet` handles its own
        // substates.
        BackHandler(enabled = pane != ReminderPane.MAIN) { pane = ReminderPane.MAIN }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TellcatSpacing.md)
                .padding(bottom = TellcatSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.sm)
        ) {
            when (pane) {
                ReminderPane.MAIN -> {
                    Text(
                        text = stringResource(R.string.task_reminder),
                        style = MaterialTheme.typography.titleLarge
                    )

                    PlanRowGroup {
                        PlanRow(
                            label = stringResource(R.string.task_reminder_day),
                            value = scheduledDateLabel(at.toLocalDate(), today),
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                            onClick = { pane = ReminderPane.DAY }
                        )
                        PlanRow(
                            label = stringResource(R.string.task_reminder_time),
                            value = time.format(rememberTimeFormat()),
                            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                            onClick = { isTimeOpen = true }
                        )
                    }

                    if (offersAlarm) {
                        ReminderAlarmSwitch(
                            ringsLikeAlarm = ringsAsAlarm,
                            onRingsLikeAlarmChange = { on -> ringsAsAlarm = on }
                        )
                    }

                    // Says why Save is dead. Reached only when the user picked
                    // the day themselves; a day this sheet worked out has
                    // already been moved forward.
                    if (hasPassed) {
                        Text(
                            text = stringResource(R.string.task_reminder_past),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = { onSet(at, ringsAsAlarm) },
                        enabled = !hasPassed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = TellcatDimensions.ActionHeight)
                            .padding(top = TellcatSpacing.xs)
                    ) {
                        Text(stringResource(R.string.task_reminder_save))
                    }

                    // **A snooze, once the reminder has spoken.** D-109. A
                    // text action like Clear, because it is a second thing the
                    // sheet can do rather than the sheet's purpose; Save still
                    // sets the reminder's own time, and a snooze leaves that
                    // alone (D-078).
                    if (canSnooze) {
                        TextButton(
                            onClick = { pane = ReminderPane.SNOOZE },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = TellcatDimensions.ActionHeight)
                        ) {
                            Text(stringResource(R.string.task_reminder_snooze))
                        }
                    }

                    // Offered only where there is something to clear, so the
                    // sheet does not propose undoing a thing not done.
                    if (reminderAt != null) {
                        TextButton(
                            onClick = onClear,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = TellcatDimensions.ActionHeight)
                        ) {
                            Text(stringResource(R.string.task_reminder_clear))
                        }
                    }
                }

                ReminderPane.SNOOZE -> ReminderSnoozePane(
                    today = today,
                    now = now,
                    onSnooze = onSnooze,
                    onBack = { pane = ReminderPane.MAIN }
                )

                ReminderPane.DAY -> ReminderDayPane(
                    selected = at.toLocalDate(),
                    today = today,
                    onPick = { picked ->
                        chooseDay(picked)
                        pane = ReminderPane.MAIN
                    },
                    onChooseDate = { isDatePickerOpen = true },
                    onBack = { pane = ReminderPane.MAIN }
                )
            }
        }
    }
}

/**
 * Where the Time row opens on a task that has no reminder yet. D-071.
 *
 * The next whole hour, while the day being offered is the one we are in, and
 * [DefaultReminderTime] otherwise. A reminder is an interruption asked for in
 * the middle of the thing it interrupts, so the useful suggestion for today is
 * soon; nine in the morning is the useful suggestion for a day not yet reached.
 *
 * Rounding up rather than adding an hour, so the same tap at 2:37 and at 2:52
 * proposes the same 3:00 and the suggestion does not slide while the sheet is
 * being read.
 *
 * The next whole hour after eleven at night belongs to tomorrow, which is a day
 * the caller did not name. Nine takes over there, and [nextReminderOccurrence]
 * carries it to tomorrow morning, which is the one case where the old default
 * was the right answer all along.
 */
internal fun suggestedReminderTime(defaultDay: LocalDate, now: LocalDateTime): LocalTime {
    if (defaultDay != now.toLocalDate()) return DefaultReminderTime

    val nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)

    return if (nextHour.toLocalDate() == now.toLocalDate()) {
        nextHour.toLocalTime()
    } else {
        DefaultReminderTime
    }
}

/** Which of the sheet's faces is showing. */
private enum class ReminderPane { MAIN, DAY, SNOOZE }

/**
 * Snoozing from inside the app. D-109.
 *
 * **The Day pane's shape**: a back arrow and title, preset buttons, and a
 * full-width control that leaves for the platform picker and says what it
 * holds. Then the line saying where the snooze lands, and a Snooze button, for
 * the reason this sheet has a Save: a preset and a picked time are drafts until
 * the user says so, and a snooze written on the first tap could not be read
 * back before it took effect.
 *
 * **Every option that applies, not the notification's three.** A notification
 * is held to three actions; a sheet is not, so "This evening" and "Tomorrow"
 * sit together until the evening has gone, and then "This evening" is not
 * offered rather than quietly meaning tomorrow (`reminders.md`).
 *
 * **"Pick a time…" lands on the next time the clock reads it**, today while it
 * is still ahead and otherwise tomorrow, and the line says which. A snooze is
 * about the next while, not a day of the user's choosing; moving the reminder
 * to another day is what Save on the main pane is for.
 */
@Composable
private fun ReminderSnoozePane(
    today: LocalDate,
    now: LocalDateTime,
    onSnooze: (LocalDateTime) -> Unit,
    onBack: () -> Unit
) {
    // The draft, as a preset's name or a picked time of day, so it is saved
    // across a rotation without a saver and recomputed against the clock.
    var preset by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedSecond by rememberSaveable { mutableStateOf(NoTimePicked) }
    var isTimeOpen by rememberSaveable { mutableStateOf(false) }

    val options = snoozeOptionsInApp(now)
    val until: LocalDateTime? = when {
        pickedSecond != NoTimePicked ->
            snoozedUntil(LocalTime.ofSecondOfDay(pickedSecond.toLong()), now)
        else -> options.firstOrNull { it.name == preset }?.let { option -> snoozedUntil(option, now) }
    }

    if (isTimeOpen) {
        ReminderTimePickerDialog(
            time = until?.toLocalTime() ?: suggestedReminderTime(today, now),
            onDismiss = { isTimeOpen = false },
            onPicked = { picked ->
                isTimeOpen = false
                pickedSecond = picked.toSecondOfDay()
                preset = null
            }
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.task_reminder_snooze_back)
            )
        }
        Text(
            text = stringResource(R.string.task_reminder_snooze_title),
            style = MaterialTheme.typography.titleLarge
        )
    }

    options.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xs)) {
            pair.forEach { option ->
                PresetButton(
                    label = snoozeOptionLabel(option),
                    selected = pickedSecond == NoTimePicked && preset == option.name,
                    onClick = {
                        preset = option.name
                        pickedSecond = NoTimePicked
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Lit once it holds the answer, as Choose a date is in the Day pane.
    val holdsTime = pickedSecond != NoTimePicked
    val timeFormat = rememberTimeFormat()
    ChooseDateButton(
        label = if (holdsTime && until != null) {
            until.toLocalTime().format(timeFormat)
        } else {
            stringResource(R.string.task_reminder_snooze_pick)
        },
        holdsValue = holdsTime,
        onClick = { isTimeOpen = true },
        icon = R.drawable.ic_schedule
    )

    until?.let { landing ->
        val moment = if (landing.toLocalDate() == today) {
            landing.toLocalTime().format(timeFormat)
        } else {
            stringResource(
                R.string.task_reminder_when,
                scheduledDateLabel(landing.toLocalDate(), today),
                landing.toLocalTime().format(timeFormat)
            )
        }
        Text(
            text = stringResource(R.string.task_reminder_snooze_until, moment),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Button(
        onClick = { until?.let(onSnooze) },
        enabled = until != null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TellcatDimensions.ActionHeight)
            .padding(top = TellcatSpacing.xs)
    ) {
        Text(stringResource(R.string.task_reminder_snooze_confirm))
    }
}

/** A snooze preset's words, the notification's own. D-109. */
@Composable
private fun snoozeOptionLabel(option: SnoozeOption): String = when (option) {
    SnoozeOption.TenMinutes -> stringResource(R.string.reminder_snooze_ten_minutes)
    SnoozeOption.OneHour -> stringResource(R.string.reminder_snooze_one_hour)
    SnoozeOption.ThisEvening -> stringResource(R.string.reminder_snooze_this_evening)
    SnoozeOption.TomorrowMorning ->
        stringResource(R.string.reminder_snooze_tomorrow, MorningHour.format(rememberTimeFormat()))
}

/** No time picked yet in the snooze pane. */
private const val NoTimePicked = -1

/**
 * The day, as the two presets and the calendar.
 *
 * Today and Tomorrow rather than D-018's four cells. A reminder is an
 * interruption, and "this weekend at 3pm" is a vague thing to ask for in a way
 * "tomorrow at 3pm" is not, so the far preset that suits a scheduled date does
 * not follow it here. Clearing has its own control on the pane behind this one,
 * which is what frees both cells for days.
 */
@Composable
private fun ReminderDayPane(
    selected: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onChooseDate: () -> Unit,
    onBack: () -> Unit
) {
    val tomorrow = today.plusDays(1)

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.task_reminder_day_back)
            )
        }
        Text(
            text = stringResource(R.string.task_reminder_day),
            style = MaterialTheme.typography.titleLarge
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xs)) {
        PresetButton(
            label = stringResource(R.string.task_date_today),
            selected = selected == today,
            onClick = { onPick(today) },
            modifier = Modifier.weight(1f)
        )
        PresetButton(
            label = stringResource(R.string.task_date_tomorrow),
            selected = selected == tomorrow,
            onClick = { onPick(tomorrow) },
            modifier = Modifier.weight(1f)
        )
    }

    // Lit when it is holding the answer, exactly as in [DateSheet]: a day
    // neither preset can express has to be visible somewhere.
    val holdsValue = selected != today && selected != tomorrow

    ChooseDateButton(
        label = if (holdsValue) {
            scheduledDateLabel(selected, today)
        } else {
            stringResource(R.string.task_date_choose)
        },
        holdsValue = holdsValue,
        onClick = onChooseDate
    )
}

/**
 * The clock, raised over the sheet.
 *
 * A dialog rather than a third pane, on the same terms `RepeatSheet` opens the
 * calendar for its end date: the platform pickers are windows of their own and
 * reimplementing one as a pane would be building a time picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    time: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit
) {
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
                // The 20dp belongs to the component, not to us.
                // `TimePickerDialogDefaults.Title` carries it, and the dialog's
                // portrait layout places the dial at the title's own height
                // with no gap of its own, so swapping in our own words took the
                // spacing out with the default and left the label sitting on
                // the clock.
                modifier = Modifier.padding(bottom = 20.dp),
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
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
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

/** No day named yet, so the time decides it. Not a date anyone can select. */
private const val NoDayChosen: Long = Long.MIN_VALUE
