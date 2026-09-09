package com.vignesh.focuslist.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.text.recurrenceSummary
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.PlanRow
import com.vignesh.focuslist.ui.component.PlanRowGroup
import com.vignesh.focuslist.ui.component.SectionLabel
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.component.durationLabel
import com.vignesh.focuslist.ui.component.scheduledDateLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Task Details: one task, edited by picking.
 *
 * `docs/decisions.md` D-018. A full screen reached from a task row, not a
 * bottom sheet. Three regions top to bottom: the identity, the plan, and one
 * action.
 *
 * **There is no Save. Every control writes when it is chosen.** D-018 records
 * what that cost: the draft it replaced meant dismissing left the task exactly
 * as it was, and immediate commit has no equivalent. What stands in for it is
 * that each write is one field, made by one deliberate choice, and reversible by
 * reopening the same row.
 *
 * Completion is the exception and is not a field. The checkbox goes through the
 * ordinary `toggleComplete` and raises the same single undo offer every list
 * raises.
 *
 * It is a room: a back arrow, no navigation bar, and back returns to the list it
 * was opened from. `navigation.md` holds that rule.
 */
@Composable
fun TaskDetailsScreen(
    taskId: String,
    viewModel: TaskListViewModel,
    onBack: () -> Unit,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    val task = tasks.firstOrNull { it.id == taskId }
    var taskWasShown by rememberSaveable(taskId) { mutableStateOf(false) }

    // The task was deleted from under the screen, or completed from a
    // notification. There is nothing left to edit, so the screen leaves rather
    // than drawing an empty one.
    //
    // Guarded on the list having loaded at all: every exposed flow begins on a
    // placeholder before storage answers, and reading that placeholder as
    // "gone" would pop the screen on the way in. That is the same trap Focus
    // fell into and `focus.md` warns about.
    // A non-empty list is still useful for rejecting a stale id on first load.
    // Once this particular task has been shown, though, its disappearance is
    // conclusive even when it was the last live task and the list is now empty.
    LaunchedEffect(task, tasks.isEmpty()) {
        when {
            task != null -> taskWasShown = true
            taskWasShown || tasks.isNotEmpty() -> onBack()
        }
    }

    val current = task ?: return

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    TaskDetailsContent(
        task = current,
        today = today,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleComplete = { viewModel.toggleComplete(current.id) },
        // One write per change, and every one of them carries the whole task.
        // `editTask` takes notes and reminderAt as required parameters with no
        // default precisely so a caller changing one field cannot silently
        // erase another it never asked about.
        onEdit = { edited ->
            viewModel.editTask(
                id = edited.id,
                title = edited.title,
                notes = edited.notes,
                scheduledDate = edited.scheduledDate,
                dueDate = edited.dueDate,
                estimatedDurationMinutes = edited.estimatedDurationMinutes,
                recurrence = edited.recurrence,
                reminderAt = edited.reminderAt
            )
        },
        onStartFocus = {
            viewModel.beginFocus(current.id)
            onOpenFocus()
        },
        // A soft delete raising the same single undo offer every list raises.
        // The screen does not navigate here: the task disappearing from
        // `allTasks` is what pops it, through the effect above, so deletion has
        // one exit rather than two that could disagree.
        onDelete = { viewModel.deleteTask(current.id) },
        modifier = modifier
    )
}

/**
 * The layout, stateless and the preview seam.
 *
 * [onEdit] receives the whole task with one field changed, which is what keeps
 * every row's write identical in shape and stops any of them dropping a field
 * by omission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailsContent(
    task: Task,
    today: LocalDate,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleComplete: () -> Unit,
    onEdit: (Task) -> Unit,
    onStartFocus: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var openSheet by rememberSaveable { mutableStateOf<PlanSheet?>(null) }
    val paneTitleText = stringResource(R.string.task_details_heading)
    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier.semantics { paneTitle = paneTitleText },
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        topBar = {
            // A back arrow and nothing else. **No title**: the task's own title
            // is directly beneath it, and two stacked headings with the upper
            // one naming the app is the thing removed from Focus and from
            // Today. The screen name is published as `paneTitle` above, which a
            // screen reader announces and nothing draws.
            //
            // **No actions.** D-037 moved Delete to the floating toolbar, so
            // the overflow that held it and nothing else is gone. A menu whose
            // only item is one action promises options it does not have, and
            // the trailing slot now matches the three list screens by being
            // empty here rather than by holding a different control.
            FocuslistTopAppBar(
                title = null,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.task_details_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        // **A Box, so the toolbar floats over the content rather than ending
        // it.** The column below scrolls; the toolbar does not move with it.
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FocuslistSpacing.md + gutter)
                    // Clearance beneath the last Plan row, on the same terms the
                    // lists reserve it under their FAB: a floating control that
                    // covers the thing it acts on is worse than one that scrolls.
                    .padding(bottom = FocuslistDimensions.FabClearance),
                verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.md)
            ) {
                IdentityRegion(task = task, onToggleComplete = onToggleComplete, onEdit = onEdit)

                PlanRegion(
                    task = task,
                    today = today,
                    onOpenSheet = { sheet -> openSheet = sheet }
                )
            }

            TaskDetailsToolbar(
                onStartFocus = onStartFocus,
                onDelete = onDelete,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FocuslistSpacing.md)
            )
        }
    }

    PlanSheetHost(
        open = openSheet,
        task = task,
        today = today,
        onDismiss = { openSheet = null },
        onEdit = { edited ->
            openSheet = null
            onEdit(edited)
        }
    )
}

/**
 * The checkbox, the title and the notes.
 *
 * **No card around them, and that is the fix rather than the omission.** A
 * tinted container read as a summary, which is exactly why the two most-edited
 * fields on the screen looked read-only. A Material text field brings its own
 * container, so putting real fields inside a card nests one container in
 * another.
 *
 * Both fields commit on blur rather than on every keystroke. A write per
 * character would be a write per character, and the field is the draft until
 * focus leaves it.
 */
@Composable
private fun IdentityRegion(task: Task, onToggleComplete: () -> Unit, onEdit: (Task) -> Unit) {
    Column(modifier = Modifier.padding(top = FocuslistSpacing.xs)) {
        // The same description a task row's checkbox carries, because it is the
        // same act on the same task. A screen reader hearing two different
        // phrasings would have to work out that they mean one thing.
        val toggleDescription = stringResource(
            if (task.isCompleted) R.string.task_row_mark_incomplete
            else R.string.task_row_mark_complete,
            task.title
        )

        // **The checkbox marks the title's first line, so the two centre on
        // each other.** The glyph sits at the middle of its 48dp target; the
        // title's first line is one `headlineSmall` line tall. Top-aligning the
        // two lines up the boxes rather than the things inside them, and the
        // title sat 8dp low against the mark that refers to it.
        //
        // Measured rather than nudged by a constant, because which of the two
        // is taller changes with the font scale: at 100% the line is 32dp and
        // the title needs pushing down, at 200% it is 64dp and the checkbox
        // does. A hardcoded 8dp is right once and wrong after that.
        val lineCentre = with(LocalDensity.current) {
            MaterialTheme.typography.headlineSmall.lineHeight.toDp() / 2
        }

        // Where the title's first line actually sits: the field's own vertical
        // inset, then half a line. The inset is Material's and stays, because
        // this `TextField` overload takes no `contentPadding` to override it.
        val titleCentre = TextFieldVerticalInset + lineCentre
        val checkboxTop = (titleCentre - FocuslistDimensions.TouchTargetMin / 2)
            .coerceAtLeast(0.dp)

        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() },
                modifier = Modifier
                    .padding(top = checkboxTop)
                    .sizeIn(
                        minWidth = FocuslistDimensions.TouchTargetMin,
                        minHeight = FocuslistDimensions.TouchTargetMin
                    )
                    .semantics { contentDescription = toggleDescription }
            )

            TitleField(task = task, onEdit = onEdit)
        }

        // Indented to the title rather than the screen edge: the notes belong
        // to the task the title names, and starting them further left made the
        // two read as separate blocks with the checkbox pointing at neither.
        NotesField(
            task = task,
            onEdit = onEdit,
            modifier = Modifier.padding(start = FocuslistDimensions.TouchTargetMin)
        )
    }
}

/** Material's own vertical inset on a `TextField` drawn without a label. */
private val TextFieldVerticalInset = 16.dp

/**
 * The title: the screen's heading and its primary input at once.
 *
 * Headline Small on `onSurface`, borderless, capped at four lines. The cap is
 * the same rule Focus's title follows: a title that overruns is cut through the
 * middle of a line and reads as broken rather than as shortened.
 *
 * A blank title is not written. `editTask` refuses one, so committing a cleared
 * field would drop the write silently; restoring the stored title on blur says
 * what happened instead.
 */
@Composable
private fun TitleField(task: Task, onEdit: (Task) -> Unit) {
    var draft by rememberSaveable(task.id) { mutableStateOf(task.title) }

    TextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = MaterialTheme.typography.headlineSmall,
        colors = borderlessFieldColors(),
        placeholder = {
            Text(
                text = stringResource(R.string.task_details_title_label),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        maxLines = TitleMaxLines,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .onFocusChanged { state ->
                if (state.isFocused) return@onFocusChanged

                if (draft.isBlank()) {
                    // Nothing to write, so put back what is stored rather than
                    // leaving the field disagreeing with the task.
                    draft = task.title
                } else if (draft.trim() != task.title) {
                    onEdit(task.copy(title = draft.trim()))
                }
            }
    )
}

/**
 * The notes, under the title.
 *
 * Body Large on `onSurfaceVariant`, borderless, several lines.
 *
 * **Null and blank both mean no notes**, and the field holds the blank because a
 * text field has to hold something. `editTask` maps a blank or whitespace-only
 * value back to null, so "no notes" has one representation in storage rather
 * than two that look identical on screen.
 */
@Composable
private fun NotesField(task: Task, onEdit: (Task) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable(task.id) { mutableStateOf(task.notes.orEmpty()) }

    TextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        colors = borderlessFieldColors(),
        placeholder = {
            Text(
                text = stringResource(R.string.task_details_notes_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) return@onFocusChanged

                // Compared against the stored value the same way it will be
                // stored, so re-entering a field and leaving it writes nothing.
                val next = draft.trim().takeIf { it.isNotEmpty() }
                if (next != task.notes) onEdit(task.copy(notes = next))
            }
    )
}

/**
 * A text field with no container and no indicator.
 *
 * The identity region is plain editable text, so the field has to stop looking
 * like a field without stopping being one. Every colour here is transparent
 * except the cursor, which is what says the text can be typed into.
 */
@Composable
private fun borderlessFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
)

/** The five rows, and the label above them. */
@Composable
private fun PlanRegion(task: Task, today: LocalDate, onOpenSheet: (PlanSheet) -> Unit) {
    val rows = PlanSheet.entries

    Column {
        SectionLabel(text = stringResource(R.string.task_plan))

        PlanRowGroup {
            rows.forEachIndexed { index, row ->
                PlanRow(
                    label = stringResource(row.labelRes),
                    value = row.value(task, today),
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = rows.size
                    ),
                    onClick = { onOpenSheet(row) }
                )
            }
        }
    }
}

/**
 * The five Plan rows, each of which opens one sheet.
 *
 * One type rather than five booleans, so exactly one sheet can be open and the
 * row and the sheet it opens cannot drift apart.
 */
internal enum class PlanSheet {

    SCHEDULED,
    DUE,
    REMINDER,
    DURATION,
    REPEAT;

    val labelRes: Int
        get() = when (this) {
            SCHEDULED -> R.string.task_scheduled
            DUE -> R.string.task_due_date
            REMINDER -> R.string.task_reminder
            DURATION -> R.string.task_duration
            REPEAT -> R.string.task_repeat
        }

    /**
     * What the row shows.
     *
     * Every unset value reads `None`, except Repeat, whose absence has a name of
     * its own. Neither is styled differently from a set one; `task-details.md`
     * carries that argument in full.
     */
    @Composable
    fun value(task: Task, today: LocalDate): String = when (this) {
        SCHEDULED -> task.scheduledDate?.let { date -> scheduledDateLabel(date, today) }
            ?: stringResource(R.string.task_value_none)

        DUE -> task.dueDate?.let { date -> scheduledDateLabel(date, today) }
            ?: stringResource(R.string.task_value_none)

        REMINDER -> task.reminderAt?.let { at -> reminderValue(at, today) }
            ?: stringResource(R.string.task_value_none)

        // Through `durationLabel`, so this reads 45m, 1h, 1h 30m like every
        // other duration in the app. Never "45 min".
        DURATION -> task.estimatedDurationMinutes?.let { minutes -> durationLabel(minutes).text }
            ?: stringResource(R.string.task_value_none)

        // The rule written out, which since D-027 can be "Every 2 weeks" or
        // "Mon, Wed, Fri" and not only one of four adjectives. When it stops is
        // deliberately absent; board frame 18 leaves it out and the sheet is
        // where a horizon belongs.
        REPEAT -> task.recurrence?.let { rule -> recurrenceSummary(rule, today) }
            ?: stringResource(R.string.task_repeat_never)
    }
}

/** A reminder as a day and a time, in the reader's own locale. */
@Composable
private fun reminderValue(at: LocalDateTime, today: LocalDate): String {
    val time = at.toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    return stringResource(
        R.string.task_reminder_when,
        scheduledDateLabel(at.toLocalDate(), today),
        time
    )
}

/** What the column holds at the largest system font scale, as on Focus. */
private const val TitleMaxLines = 4

/** How long a reminder defaults to when the task has none yet. */
internal val DefaultReminderTime: java.time.LocalTime = java.time.LocalTime.of(9, 0)

/** Opens whichever sheet the pressed row asked for, and writes what it returns. */
@Composable
private fun PlanSheetHost(
    open: PlanSheet?,
    task: Task,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: (Task) -> Unit
) {
    when (open) {
        null -> Unit

        PlanSheet.SCHEDULED -> DateSheet(
            kind = DateSheetKind.SCHEDULED,
            selected = task.scheduledDate,
            today = today,
            onPick = { date -> onEdit(task.copy(scheduledDate = date)) },
            onDismiss = onDismiss
        )

        PlanSheet.DUE -> DateSheet(
            kind = DateSheetKind.DUE,
            selected = task.dueDate,
            today = today,
            onPick = { date -> onEdit(task.copy(dueDate = date)) },
            onDismiss = onDismiss
        )

        PlanSheet.REMINDER -> ReminderSheet(
            reminderAt = task.reminderAt,
            // Where the dialog opens, not where it is stuck. D-030 gave it a
            // day of its own, so this is a first suggestion rather than the
            // answer, and `PRODUCT.md`'s independence of a scheduled date is
            // something the control can finally express.
            //
            // A scheduled date still ahead is the useful default: a task
            // planned for Friday usually wants announcing on Friday. One
            // already past is not, and it is dropped rather than resolved
            // forward, so the dialog opens on a day the user recognises
            // instead of on an arithmetic result. That fallback is what forced
            // every reminder on an overdue task into the past.
            defaultDay = task.reminderAt?.toLocalDate()
                ?: task.scheduledDate?.takeIf { date -> !date.isBefore(today) }
                ?: today,
            today = today,
            onSet = { at -> onEdit(task.copy(reminderAt = at)) },
            onClear = { onEdit(task.copy(reminderAt = null)) },
            onDismiss = onDismiss
        )

        PlanSheet.DURATION -> DurationSheet(
            selectedMinutes = task.estimatedDurationMinutes,
            onPick = { minutes -> onEdit(task.copy(estimatedDurationMinutes = minutes)) },
            onDismiss = onDismiss
        )

        PlanSheet.REPEAT -> RepeatSheet(
            selected = task.recurrence,
            today = today,
            onPick = { recurrence -> onEdit(task.copy(recurrence = recurrence)) },
            onDismiss = onDismiss
        )
    }
}

/**
 * The screen's two actions, floating over the content.
 *
 * `docs/decisions.md` D-037. Start focus and Delete, in one
 * `HorizontalFloatingToolbar`, replacing a full-width button at the foot of the
 * scroll and an app-bar overflow whose only item was Delete.
 *
 * **Delete leads and Focus trails, which is the opposite of the menu rule and
 * the same reasoning.** `expressive-components.md` orders the row menu
 * "constructive before destructive, so the thumb does not land on Delete". In a
 * vertical menu the thumb lands nearest the bottom; in a horizontal bar it lands
 * nearest the reaching side. Putting Focus at the trailing end is what keeps the
 * thumb off Delete here, and it is also where Material puts a toolbar's
 * prominent action.
 *
 * **Focus is filled and Delete is not**, which answers D-022's objection to
 * pairing them: a rare one-way action must not carry the same weight as the
 * screen's payoff. The fill is the weight, since neither can carry a label.
 *
 * **Both lose their words, and that is the cost D-037 accepts.** D-022 called an
 * unlabelled trash icon "the least legible form of the most destructive action",
 * and that is still true. What is different is that deletion here is a soft
 * delete raising the same undo offer every list raises, and that a one-item
 * overflow was hiding the action behind a control promising options it did not
 * have. The content descriptions carry the words for a screen reader.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TaskDetailsToolbar(
    onStartFocus: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        // **Focus is the attached FAB, which is Material's own arrangement for
        // a toolbar with one action that outranks the rest.** It carries the
        // prominence the full-width button used to, without a label and without
        // competing with the bar it sits on. `FilledIconButton` was the first
        // build of this and only approximated it: same fill, none of the size
        // or the separation, so the two actions read as a pair of equals with
        // one tinted differently.
        floatingActionButton = {
            FloatingToolbarDefaults.StandardFloatingActionButton(onClick = onStartFocus) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    // The only place the words survive, so they are the action
                    // rather than the glyph: "Start focus", never "play".
                    contentDescription = stringResource(R.string.task_start_focus)
                )
            }
        }
    ) {
        IconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.iconButtonColors(
                // The word is gone, so the colour is the only cue left that this
                // one is different in kind. It was `error` on the menu item too.
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.task_delete)
            )
        }
    }
}
