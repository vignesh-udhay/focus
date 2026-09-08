package com.vignesh.focuslist.ui.task

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vignesh.focuslist.R
import com.vignesh.focuslist.ui.component.focuslistFieldColors
import com.vignesh.focuslist.ui.component.FocuslistFieldShape
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.RecurrenceEnd
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import com.vignesh.focuslist.core.text.RecurrenceStyle
import com.vignesh.focuslist.core.text.chipLabel
import com.vignesh.focuslist.core.text.fullName
import com.vignesh.focuslist.core.text.recurrenceEndDate
import com.vignesh.focuslist.core.text.recurrenceIntervalValue
import com.vignesh.focuslist.core.text.recurrenceSummary
import com.vignesh.focuslist.ui.component.PlanRow
import com.vignesh.focuslist.ui.component.PlanRowGroup
import com.vignesh.focuslist.ui.component.TaskDatePickerDialog
import com.vignesh.focuslist.ui.component.planRowColors
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How often the task comes back.
 *
 * Built under `docs/decisions.md` D-027, which ends D-019's deferral. Three
 * panes in one `ModalBottomSheet`: the rule, the Every substate and the Ends
 * substate. The board names its frames "SAME ModalBottomSheet" and draws a back
 * arrow on each substate, which is the shape D-026 already settled for Duration
 * and for the same reason: a dialog raised over a sheet is two windows, two
 * scrims, and two things back could mean.
 *
 * **This is the one sheet on the screen with a Save**, and D-018's
 * commit-as-you-go rule is not being quietly dropped. Every other row sets one
 * field from one choice, so writing immediately is the whole interaction. A
 * recurrence rule is four fields that only mean something together: writing the
 * unit the moment it is tapped would make "every 2 weeks on Monday" pass through
 * "every 1 week" and "every 2 weeks" as real saved states, each of which
 * reschedules the series. Save is what keeps a half-built rule out of the task.
 *
 * Nothing is written until it is pressed, so leaving by the scrim or by back
 * needs no Cancel, exactly as the Custom duration pane argues.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatSheet(
    selected: Recurrence?,
    today: LocalDate,
    onPick: (Recurrence?) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(stateSaver = RepeatDraftSaver) {
        mutableStateOf(RepeatDraft.of(selected))
    }
    var pane by rememberSaveable { mutableStateOf(RepeatPane.RULE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        // Back returns to the rule rather than closing the sheet, which is what
        // the arrow in the corner promises. Only while a substate is showing;
        // otherwise the sheet's own dismissal stands.
        BackHandler(enabled = pane != RepeatPane.RULE) { pane = RepeatPane.RULE }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.md)
                .padding(bottom = FocuslistSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
        ) {
            when (pane) {
                RepeatPane.RULE -> RulePane(
                    draft = draft,
                    today = today,
                    canClear = selected != null,
                    onDraftChange = { draft = it },
                    onOpen = { pane = it },
                    onSave = { onPick(draft.toRecurrence()) },
                    onClear = { onPick(null) }
                )

                RepeatPane.EVERY -> EveryPane(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onDone = { pane = RepeatPane.RULE }
                )

                RepeatPane.ENDS -> EndsPane(
                    draft = draft,
                    today = today,
                    onDraftChange = { draft = it },
                    onDone = { pane = RepeatPane.RULE }
                )
            }
        }
    }
}

/** Which of the sheet's three faces is showing. */
private enum class RepeatPane { RULE, EVERY, ENDS }

/**
 * The rule itself: how often, which days, and when it stops.
 *
 * Every and Ends are `PlanRow`s rather than a bespoke row, so the sheet opens
 * its own substates with the same control Task Details opens its sheets with.
 * Two single-row groups rather than one group of two, because the weekday chips
 * sit between them when the unit is a week and a connected group broken in half
 * by something else is not a group.
 */
@Composable
private fun RulePane(
    draft: RepeatDraft,
    today: LocalDate,
    canClear: Boolean,
    onDraftChange: (RepeatDraft) -> Unit,
    onOpen: (RepeatPane) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Text(
        text = stringResource(R.string.task_repeat),
        style = MaterialTheme.typography.titleLarge
    )

    // **The rule as one sentence, above the three controls that make it.**
    //
    // Not a restatement, which is the objection D-026 upheld against Duration's
    // hero readout. That was one number at display size, said again one gesture
    // after it was set. This composes four values the controls only show
    // separately, and the composition is where the ambiguity is: "Every 2 weeks"
    // with three days lit does not say, from the controls alone, whether it
    // means every other Monday or Mon, Wed and Fri in alternating weeks.
    //
    // It reads the draft rather than the task, because this is the one sheet on
    // the screen that does not commit as you go. The line is a preview of what
    // Save will write.
    //
    // Shown on every rule, including the simple ones it tells you nothing about.
    // A summary that only appears when things get complicated is one nobody
    // reads at the moment it appears, which is the moment it matters most. Same
    // reasoning D-021 used about a health screen that is always red.
    //
    // `bodyMedium` on `onSurfaceVariant`, matching the Due date sheet's
    // supporting line, which is the same register in the same position. The
    // footnote above Save is `bodySmall`, so the line about this rule sits above
    // the line that says the same thing on every rule forever.
    Text(
        text = recurrenceSummary(draft.toRecurrence(), today, RecurrenceStyle.Full),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    PlanRowGroup {
        PlanRow(
            label = stringResource(R.string.task_repeat_every),
            value = recurrenceIntervalValue(draft.unit, draft.intervalOrOne),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            onClick = { onOpen(RepeatPane.EVERY) }
        )
    }

    // Only a week has days inside it. The board hides the section for the other
    // three units rather than disabling it, and a control that cannot apply is
    // better absent than present and inert.
    if (draft.unit == RecurrenceUnit.WEEKLY) {
        Text(
            text = stringResource(R.string.task_repeat_days),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = FocuslistSpacing.xs)
        )

        WeekdayChips(
            selected = draft.weekdays,
            onToggle = { day ->
                onDraftChange(draft.copy(weekdays = draft.weekdays.toggled(day)))
            },
            onlySelected = draft.onlySelectedWeekday
        )
    }

    PlanRowGroup(modifier = Modifier.padding(top = FocuslistSpacing.xs)) {
        PlanRow(
            label = stringResource(R.string.task_repeat_ends),
            value = endsValue(draft, today),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            onClick = { onOpen(RepeatPane.ENDS) }
        )
    }

    Text(
        text = stringResource(R.string.task_repeat_footnote),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(
        onClick = onSave,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FocuslistDimensions.ActionHeight)
            .padding(top = FocuslistSpacing.xs)
    ) {
        Text(stringResource(R.string.task_repeat_save))
    }

    // **The board draws no way to stop a task repeating**, which D-027 records
    // as the one thing its frames do not answer. The date sheets spend their
    // first cell on "No date" for the same reason a rule needs this: without it
    // the sheet is a one-way door.
    //
    // Offered only when there is a rule to clear. A control that can do nothing
    // is one the user cannot tell worked, which is the rule `DatePresets.kt`
    // argues from when it refuses to let a preset resolve to today.
    if (canClear) {
        TextButton(
            onClick = onClear,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FocuslistDimensions.ActionHeight)
        ) {
            Text(stringResource(R.string.task_repeat_clear))
        }
    }
}

/**
 * The seven weekday chips.
 *
 * `FilterChip` because that is Material's control for picking several out of a
 * set, and its selected state is the component's rather than a colour this file
 * chooses. The board draws them as circles, which is the shape passed here.
 *
 * **Sized by [FocuslistDimensions.WeekdayChipSize] rather than by the label.**
 * At their natural width the seven came to 1002px inside a 992px column on a
 * 1080px screen, so the last day wrapped to a line of its own at the default
 * font scale. The fixed width is what makes them a row of seven.
 *
 * **`FlowRow` rather than a `Row`.** `expressive-components.md` says a row of
 * choices "must not overflow at large font scales, and must not truncate a label
 * to avoid doing so". The height is a floor rather than a fixed size for the
 * same reason, so a letter at 200% makes its chip taller instead of being cut,
 * and if a locale's narrow names ever run wider than one glyph the row wraps
 * rather than clipping. That is the same answer `ButtonGroup`'s overflow menu
 * gives the duration presets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    onlySelected: DayOfWeek?
) {
    val context = LocalContext.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        // Spread rather than spaced by a step, which is how the board arranges
        // them: seven 48dp targets across 364dp, the remainder spent as the six
        // gaps. That leaves no number here to be wrong, and it stays right on a
        // narrower sheet.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs)
    ) {
        // Monday first, matching the week the rule counts in. Iterating the enum
        // gives that order without a list to keep in step with it.
        DayOfWeek.entries.forEach { day ->
            // The narrow name is one letter and two pairs of days share theirs,
            // so the chip is announced by its full name instead. Sighted users
            // have position to tell T from T by; a screen reader user has not.
            val spoken = day.fullName(context)

            FilterChip(
                selected = day in selected,
                // The board's frame is called "require >=1 selected day", so the
                // last one standing does not come off. Ignored rather than
                // disabled or refused out loud: a greyed-out chip would say the
                // day was unavailable when it is the one that is chosen, and
                // there is no error to report, only a state the rule cannot be
                // in. Nothing happening is the whole message.
                onClick = { if (day != onlySelected) onToggle(day) },
                label = { Text(day.chipLabel(context)) },
                // **Centred, which a chip does not do for itself.** Material's
                // `ChipArrangement` places the first child at x = 0 and spends
                // every spare pixel on the right, because a chip is normally as
                // wide as its content and there is no slack to place. Forcing
                // the width creates the slack, and the letter sat left of the
                // circle it was drawn inside, most visibly on the narrow ones.
                horizontalArrangement = Arrangement.Center,
                shape = CircleShape,
                modifier = Modifier
                    .width(FocuslistDimensions.WeekdayChipSize)
                    .heightIn(min = FocuslistDimensions.WeekdayChipSize)
                    .semantics { contentDescription = spoken }
            )
        }
    }
}

/**
 * How many periods a step covers, and which period.
 *
 * A field and four rows, as the board draws it. Done returns to the rule rather
 * than writing, because nothing on this sheet is written until Save.
 */
@Composable
private fun EveryPane(
    draft: RepeatDraft,
    onDraftChange: (RepeatDraft) -> Unit,
    onDone: () -> Unit
) {
    PaneHeader(
        title = stringResource(R.string.task_repeat_every),
        backDescription = stringResource(R.string.task_repeat_every_back),
        onBack = onDone
    )

    TextField(
        value = draft.interval,
        onValueChange = { typed ->
            onDraftChange(draft.copy(interval = typed.filter(Char::isDigit).take(3)))
        },
        label = { Text(stringResource(R.string.task_repeat_interval)) },
        colors = focuslistFieldColors(),
        shape = FocuslistFieldShape,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    val units = RecurrenceUnit.entries

    PlanRowGroup(modifier = Modifier.padding(top = FocuslistSpacing.xs)) {
        units.forEachIndexed { index, unit ->
            ChoiceRow(
                label = stringResource(unit.labelRes),
                selected = unit == draft.unit,
                shapesIndex = index,
                shapesCount = units.size,
                onClick = { onDraftChange(draft.copy(unit = unit)) }
            )
        }
    }

    DonePaneButton(enabled = draft.interval.toIntOrNull()?.let { it >= 1 } == true, onDone = onDone)
}

/**
 * When the series stops.
 *
 * Three choices, and the two that carry a value show it underneath rather than
 * inside the row: an end date opens the same Material picker every other date on
 * this screen opens, and a count is a typed number.
 */
@Composable
private fun EndsPane(
    draft: RepeatDraft,
    today: LocalDate,
    onDraftChange: (RepeatDraft) -> Unit,
    onDone: () -> Unit
) {
    var isPickerOpen by rememberSaveable { mutableStateOf(false) }

    if (isPickerOpen) {
        TaskDatePickerDialog(
            initialDate = draft.endDate ?: today,
            onDismiss = { isPickerOpen = false },
            onPicked = { picked ->
                isPickerOpen = false
                onDraftChange(draft.copy(endKind = EndKind.ON_DATE, endDate = picked))
            }
        )
    }

    PaneHeader(
        title = stringResource(R.string.task_repeat_ends),
        backDescription = stringResource(R.string.task_repeat_ends_back),
        onBack = onDone
    )

    val kinds = EndKind.entries

    PlanRowGroup {
        kinds.forEachIndexed { index, kind ->
            ChoiceRow(
                label = stringResource(kind.labelRes),
                selected = kind == draft.endKind,
                shapesIndex = index,
                shapesCount = kinds.size,
                onClick = {
                    onDraftChange(draft.copy(endKind = kind))
                    // Choosing "On date" with no date yet has nothing to mean,
                    // so it asks for one rather than sitting on a blank value.
                    if (kind == EndKind.ON_DATE && draft.endDate == null) isPickerOpen = true
                }
            )
        }
    }

    when (draft.endKind) {
        EndKind.NEVER -> HelpText(stringResource(R.string.task_repeat_ends_help))

        EndKind.ON_DATE -> {
            PlanRowGroup(modifier = Modifier.padding(top = FocuslistSpacing.xs)) {
                PlanRow(
                    label = stringResource(R.string.task_date_choose),
                    value = draft.endDate?.let { recurrenceEndDate(it, today) }.orEmpty(),
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    onClick = { isPickerOpen = true }
                )
            }
            HelpText(stringResource(R.string.task_repeat_ends_help))
        }

        EndKind.AFTER -> {
            TextField(
                value = draft.endCount,
                onValueChange = { typed ->
                    onDraftChange(draft.copy(endCount = typed.filter(Char::isDigit).take(3)))
                },
                label = { Text(stringResource(R.string.task_repeat_occurrences)) },
                colors = focuslistFieldColors(),
                shape = FocuslistFieldShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FocuslistSpacing.xs)
            )
            HelpText(stringResource(R.string.task_repeat_ends_after_help))
        }
    }

    DonePaneButton(enabled = draft.endIsComplete, onDone = onDone)
}

/** The back arrow and the title, which both substates wear. */
@Composable
private fun PaneHeader(title: String, backDescription: String, onBack: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = backDescription
            )
        }

        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

/** The supporting line under a substate's choices. */
@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * A substate's Done.
 *
 * Disabled rather than silently corrected when the pane holds something that is
 * not a rule, on the same reasoning the Custom duration pane refuses zero: a
 * value dropped quietly is worse than a button that says nothing happened.
 */
@Composable
private fun DonePaneButton(enabled: Boolean, onDone: () -> Unit) {
    Button(
        onClick = onDone,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FocuslistDimensions.ActionHeight)
            .padding(top = FocuslistSpacing.xs)
    ) {
        Text(stringResource(R.string.task_repeat_done))
    }
}

/**
 * One row of a single-choice group.
 *
 * `SegmentedListItem`'s selectable overload, so the radio semantics and the
 * connected shapes both come from Material rather than from a `when` here. The
 * `RadioButton` takes no click of its own: the row is the target, and two
 * clickable things in one row is two nodes for a screen reader to find.
 */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    shapesIndex: Int,
    shapesCount: Int,
    onClick: () -> Unit
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = shapesIndex, count = shapesCount),
        colors = planRowColors(),
        modifier = Modifier.heightIn(min = ChoiceRowMinHeight),
        leadingContent = { RadioButton(selected = selected, onClick = null) }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** The same floor `PlanRow` uses, so the two row families line up. */
private val ChoiceRowMinHeight = 56.dp

/** The Ends row's value on the rule pane. */
@Composable
private fun endsValue(draft: RepeatDraft, today: LocalDate): String = when (draft.endKind) {
    EndKind.NEVER -> stringResource(R.string.task_repeat_ends_never)
    EndKind.ON_DATE -> draft.endDate?.let { recurrenceEndDate(it, today) }
        ?: stringResource(R.string.task_repeat_ends_on_date)

    EndKind.AFTER -> stringResource(
        R.string.task_repeat_ends_after_value,
        draft.endCount.toIntOrNull() ?: 0
    )
}

/** The three ways a series can end, in the order the board lists them. */
private enum class EndKind {

    NEVER,
    ON_DATE,
    AFTER;

    val labelRes: Int
        get() = when (this) {
            NEVER -> R.string.task_repeat_ends_never
            ON_DATE -> R.string.task_repeat_ends_on_date
            AFTER -> R.string.task_repeat_ends_after
        }
}

/** What the Every pane calls each period. */
private val RecurrenceUnit.labelRes: Int
    get() = when (this) {
        RecurrenceUnit.DAILY -> R.string.task_repeat_unit_day
        RecurrenceUnit.WEEKLY -> R.string.task_repeat_unit_week
        RecurrenceUnit.MONTHLY -> R.string.task_repeat_unit_month
        RecurrenceUnit.YEARLY -> R.string.task_repeat_unit_year
    }

/** Adding a day that is there removes it, which is what a chip does. */
private fun Set<DayOfWeek>.toggled(day: DayOfWeek): Set<DayOfWeek> =
    if (day in this) this - day else this + day

/**
 * The rule being built, before Save writes it.
 *
 * The two numbers are held as text rather than as `Int`s because a field the
 * user is halfway through clearing holds "" and not zero, and coercing that to a
 * number on every keystroke would put a 1 back under the cursor.
 *
 * The end condition is kept flat, as a kind and both its possible values, so
 * switching between On date and After occurrences and back does not throw away
 * what was typed into the other one.
 */
private data class RepeatDraft(
    val unit: RecurrenceUnit,
    val interval: String,
    val weekdays: Set<DayOfWeek>,
    val endKind: EndKind,
    val endDate: LocalDate?,
    val endCount: String
) {

    val intervalOrOne: Int
        get() = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1

    /**
     * The day that cannot be deselected, or null when none is at risk.
     *
     * Null while no day is chosen, which is not a state the sheet can be talked
     * into but is one it can be opened on: every weekly rule written before
     * D-027 has an empty set and means the day the task is anchored to.
     * Selecting a first day is allowed from there; going back to none is not.
     */
    val onlySelectedWeekday: DayOfWeek?
        get() = weekdays.singleOrNull()

    /** Whether the chosen end has the value it needs. */
    val endIsComplete: Boolean
        get() = when (endKind) {
            EndKind.NEVER -> true
            EndKind.ON_DATE -> endDate != null
            EndKind.AFTER -> (endCount.toIntOrNull() ?: 0) >= 1
        }

    /**
     * The rule this draft describes.
     *
     * Weekdays are dropped for the three units that have no week inside them, so
     * a rule cannot carry days it will never read. Choosing Week, tapping three
     * days and then changing to Month leaves a monthly rule and nothing else.
     */
    fun toRecurrence(): Recurrence = Recurrence(
        unit = unit,
        interval = intervalOrOne,
        weekdays = if (unit == RecurrenceUnit.WEEKLY) weekdays else emptySet(),
        end = when (endKind) {
            EndKind.NEVER -> RecurrenceEnd.Never
            EndKind.ON_DATE -> endDate?.let(RecurrenceEnd::OnDate) ?: RecurrenceEnd.Never
            EndKind.AFTER -> endCount.toIntOrNull()
                ?.takeIf { it >= 1 }
                ?.let(RecurrenceEnd::AfterOccurrences)
                ?: RecurrenceEnd.Never
        }
    )

    companion object {

        /**
         * The draft a task's current rule starts from.
         *
         * A task that does not repeat opens on weekly, which is the commonest
         * rule and the one the board draws its clean slate with. It is a
         * starting point rather than a value: nothing is written until Save.
         */
        fun of(recurrence: Recurrence?): RepeatDraft = RepeatDraft(
            unit = recurrence?.unit ?: RecurrenceUnit.WEEKLY,
            interval = (recurrence?.interval ?: 1).toString(),
            weekdays = recurrence?.weekdays.orEmpty(),
            endKind = when (recurrence?.end) {
                is RecurrenceEnd.OnDate -> EndKind.ON_DATE
                is RecurrenceEnd.AfterOccurrences -> EndKind.AFTER
                else -> EndKind.NEVER
            },
            endDate = (recurrence?.end as? RecurrenceEnd.OnDate)?.date,
            endCount = ((recurrence?.end as? RecurrenceEnd.AfterOccurrences)?.count ?: DefaultCount)
                .toString()
        )

        /** What the board's After occurrences field starts on. */
        private const val DefaultCount = 10
    }
}

/**
 * Saves the draft across a rotation.
 *
 * Flattened to strings and one number, because a `listSaver` can only hold what
 * a `Bundle` can, and neither a nested list nor a sealed type is that. Losing a
 * half-built rule to a screen turning would be a small bug with an unpleasant
 * shape: the user would have no way to tell it had happened until they saved
 * something they had not chosen.
 */
private val RepeatDraftSaver = listSaver<RepeatDraft, Any>(
    save = { draft ->
        listOf(
            draft.unit.name,
            draft.interval,
            draft.weekdays.joinToString(",", transform = DayOfWeek::name),
            draft.endKind.name,
            draft.endDate?.toEpochDay() ?: NoDate,
            draft.endCount
        )
    },
    restore = { saved ->
        val epochDay = saved[4] as Long

        RepeatDraft(
            unit = RecurrenceUnit.valueOf(saved[0] as String),
            interval = saved[1] as String,
            weekdays = (saved[2] as String)
                .split(",")
                .filter(String::isNotBlank)
                .map(DayOfWeek::valueOf)
                .toSet(),
            endKind = EndKind.valueOf(saved[3] as String),
            endDate = epochDay.takeIf { it != NoDate }?.let(LocalDate::ofEpochDay),
            endCount = saved[5] as String
        )
    }
)

/** A sentinel epoch day, because a `listSaver` cell cannot hold null. */
private const val NoDate = Long.MIN_VALUE
