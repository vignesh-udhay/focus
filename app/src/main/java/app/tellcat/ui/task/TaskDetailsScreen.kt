package app.tellcat.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.lerp
import app.tellcat.core.design.TellcatMotion
import app.tellcat.ui.component.CompletionPressScale
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import app.tellcat.core.design.TellcatHaptics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tellcat.R
import app.tellcat.core.domain.pendingFollowUp
import app.tellcat.core.domain.hasLiveReminder
import app.tellcat.core.domain.canSnooze
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.design.tellcatContentGutter
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.FocusSession
import app.tellcat.core.domain.Recurrence
import app.tellcat.core.domain.Task
import app.tellcat.core.text.recurrenceSummary
import app.tellcat.ui.component.TellcatTopAppBar
import app.tellcat.ui.component.PlanRow
import app.tellcat.ui.component.PlanRowGroup
import app.tellcat.ui.component.SectionLabel
import app.tellcat.ui.component.TaskListEmptyState
import app.tellcat.ui.component.TaskListErrorState
import app.tellcat.ui.component.UndoSnackbarHost
import app.tellcat.ui.component.durationLabel
import app.tellcat.ui.component.rememberTimeFormat
import app.tellcat.ui.component.scheduledDateLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

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
 * Completion is the exception and is not a field. The toolbar's toggle goes
 * through the ordinary `toggleComplete`, with no undo offer: the toggle stays
 * under the thumb and is its own undo, D-094.
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

    // Whether the read has answered, and whether it failed. D-062: "the list is
    // empty" cannot tell those two apart from "there are no tasks", and this
    // screen is the one that has to.
    val tasksLoaded by viewModel.tasksLoaded.collectAsStateWithLifecycle()
    val readFailed by viewModel.readFailed.collectAsStateWithLifecycle()

    // For D-057's question, below. Collected here rather than beside the dialog
    // so the screen reads its inputs in one place.
    val focusSwitchTaskId by viewModel.focusSwitchTaskId.collectAsStateWithLifecycle()
    val focusedTaskId by viewModel.focusedTaskId.collectAsStateWithLifecycle()
    val focusSession by viewModel.focusSession.collectAsStateWithLifecycle()

    val task = tasks.firstOrNull { it.id == taskId }
    var taskWasShown by rememberSaveable(taskId) { mutableStateOf(false) }

    LaunchedEffect(task) { if (task != null) taskWasShown = true }

    // **A task that was on screen and has gone was completed or deleted**, from
    // here or from its notification, and the screen leaves rather than drawing
    // an empty one. Silently, because the list the user lands on carries the
    // undo offer for the thing they just did, and a second message explaining it
    // would be the app narrating the user's own tap back to them.
    LaunchedEffect(task, taskWasShown) {
        if (task == null && taskWasShown) onBack()
    }

    if (task == null) {
        // **The screen used to hang here, and `docs/decisions.md` D-062 is the
        // fix.** The condition above this was `taskWasShown || tasks.isNotEmpty()`,
        // where a non-empty list stood in for "the read has happened". On a
        // device holding no other tasks that proof never arrives, so a
        // notification pointing at a deleted task drew nothing, for ever, with
        // no bar and no way out.
        //
        // Drawn only when the task was never shown. One that was is already
        // leaving through the effect above, and flashing an explanation on the
        // way out would be a message about something the user just did.
        if (!taskWasShown) {
            TaskDetailsUnavailable(
                hasRead = tasksLoaded,
                readFailed = readFailed,
                onBack = onBack,
                onRetry = viewModel::retryRead,
                modifier = modifier
            )
        }

        return
    }

    val current = task

    // **Opening the task answers its reminder.** D-108. A follow-up still
    // pending is forgotten, here or from the notification that brought the user
    // in. Keyed on the pending moment, so one that comes due while the screen
    // is open is answered too: the user is looking at the task.
    val followUp = current.pendingFollowUp
    LaunchedEffect(current.id, followUp) {
        if (followUp != null) viewModel.acknowledgeFollowUp(current.id)
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    TaskDetailsContent(
        task = current,
        today = today,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleComplete = { viewModel.toggleComplete(current.id, offerUndo = false) },
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
                reminderAt = edited.reminderAt,
                ringsLikeAlarm = edited.ringsLikeAlarm
            )
        },
        // **Navigation follows the answer, not the tap.** `docs/decisions.md`
        // D-057: a Start focus that would discard a session on another task
        // writes nothing and raises a question instead, so leaving the screen
        // here would land the user in Focus on a session that had not changed.
        onStartFocus = { if (viewModel.beginFocus(current.id)) onOpenFocus() },
        // **The word follows the branch `beginFocus` takes.** D-068. A session
        // already open on this task resumes rather than restarting, per D-057,
        // and the control used to promise a fresh clock either way. This is the
        // same condition the view model tests, deliberately copied rather than
        // approximated: a label that decides for itself can drift from what the
        // tap does, which is the defect being fixed.
        //
        // A session on *another* task is not this, and stays Start focus. That
        // tap resumes nothing; D-057's dialog is what explains it.
        resumesSession = focusSession != null && focusedTaskId == current.id,
        // A soft delete raising the same single undo offer every list raises.
        // The screen does not navigate here: the task disappearing from
        // `allTasks` is what pops it, through the effect above, so deletion has
        // one exit rather than two that could disagree.
        onDelete = { viewModel.deleteTask(current.id) },
        onAddChecklistItem = { text -> viewModel.addChecklistItem(current.id, text) },
        onSetChecklistItemDone = { index, item, done ->
            viewModel.setChecklistItemDone(current.id, index, item.text, done)
        },
        onEditChecklistItem = { index, item, newText ->
            viewModel.editChecklistItem(current.id, index, item.text, newText)
        },
        onRemoveChecklistItem = { index, item ->
            viewModel.removeChecklistItem(current.id, index, item.text)
        },
        onShare = { shareTask(context, current) },
        onSnooze = { until -> viewModel.snoozeReminder(current.id, until) },
        modifier = modifier
    )

    // The Start focus above deferred to a question. D-057, and it only ever
    // concerns this screen's own task: another Task Details asking about its own
    // switch is a different id.
    if (focusSwitchTaskId != current.id) return

    // The session's task, resolved rather than assumed. Completing or deleting
    // it from outside the sheet leaves the pointer behind, and then there is
    // nothing to preserve and no name to put in the question, so the switch goes
    // straight through. Read from `tasks`, which this screen already trusts for
    // its own existence check, rather than from `focusedTask`, which begins on a
    // placeholder that would read as gone.
    val running = tasks.firstOrNull { candidate ->
        candidate.id == focusedTaskId && !candidate.isDeleted && !candidate.isCompleted
    }

    if (running == null) {
        LaunchedEffect(current.id) { if (viewModel.confirmFocusSwitch()) onOpenFocus() }
        return
    }

    FocusSwitchDialog(
        running = running,
        next = current,
        session = focusSession,
        onConfirm = { if (viewModel.confirmFocusSwitch()) onOpenFocus() },
        onCancel = viewModel::dismissFocusSwitch
    )
}

/**
 * Task Details with no task to show.
 *
 * `docs/decisions.md` D-062. Three reasons the screen can be asked for a task it
 * cannot draw, and the screen used to answer all three by drawing nothing at
 * all: no app bar, no message, no way back.
 *
 * - **The read has not answered.** Over in a frame, and the bar is the point:
 *   even a read that never returns leaves the user a way out.
 * - **The read failed.** The same state every list draws for the same failure,
 *   with the same Try again, because there is one read behind all of them and
 *   D-034 settled that it says so once, in one shape.
 * - **The read answered and the task is not in it.** The stale deep link: a
 *   notification or widget for a task that has since been deleted, or one a
 *   restore replaced. This is the case that had no state at all.
 *
 * **It does not navigate away by itself, which is a departure from what the
 * audit asked for.** Returning to the list automatically would flash a screen
 * the user tapped a notification to reach and then take it away, leaving them on
 * Today with no idea what happened. Back is one tap and it is theirs to make.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailsUnavailable(
    hasRead: Boolean,
    readFailed: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TellcatTopAppBar(
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                // Nothing drawn under the bar. A spinner for something that
                // resolves in a frame is a flicker, and `AGENTS.md` asks for
                // motion that communicates a state change rather than motion
                // because motion is possible.
                !hasRead -> Unit

                readFailed -> TaskListErrorState(
                    headline = stringResource(R.string.error_tasks_headline),
                    supporting = stringResource(R.string.error_tasks_supporting),
                    onRetry = onRetry
                )

                else -> TaskListEmptyState(
                    headline = stringResource(R.string.task_details_missing_headline),
                    supporting = stringResource(R.string.task_details_missing_supporting),
                    action = {
                        Button(onClick = onBack) {
                            Text(stringResource(R.string.task_details_missing_action))
                        }
                    }
                )
            }
        }
    }
}

/**
 * The question between Start focus and a session that is already running.
 *
 * `docs/decisions.md` D-057. `beginFocus` used to write a fresh `FocusSession`
 * over whatever was there, so starting Focus on a second task discarded a paused
 * one silently, with no undo and nothing on Today pointing at it. That is the
 * unrecoverable half of the pair D-015 ranked, and D-015 had only guarded the
 * exit.
 *
 * **Both titles are in the body and neither is in the title or on a button.** A
 * task title is user text of no fixed length; body text wraps and a dialog title
 * and a button label do not. So the title says what is being decided, the body
 * says which two tasks and what it costs, and the buttons stay readable.
 *
 * The elapsed time is what actually decides the answer, since two minutes and
 * forty are different questions. Under a minute there is nothing worth
 * reporting, so the sentence drops it rather than saying zero.
 *
 * The clock is read once, when the dialog appears, and does not tick. A number
 * counting up behind a question about whether to discard it would be movement
 * for its own sake, which `AGENTS.md` rules out.
 */
@Composable
private fun FocusSwitchDialog(
    running: Task,
    next: Task,
    session: FocusSession?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val elapsedMinutes = remember(session) {
        session?.elapsed(Instant.now())?.toMinutes()?.toInt() ?: 0
    }

    // The spoken form, not the compact one. This is a sentence, and "12m" in the
    // middle of prose is a label that wandered out of a row.
    val elapsed = durationLabel(elapsedMinutes).spoken

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.focus_switch_title)) },
        text = {
            Text(
                if (elapsedMinutes == 0) {
                    stringResource(
                        R.string.focus_switch_body_unstarted,
                        running.title,
                        next.title
                    )
                } else {
                    stringResource(
                        R.string.focus_switch_body,
                        running.title,
                        elapsed,
                        next.title
                    )
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.focus_switch_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.focus_switch_confirm))
            }
        }
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
    resumesSession: Boolean,
    onDelete: () -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onSetChecklistItemDone: (Int, ChecklistItem, Boolean) -> Unit,
    onEditChecklistItem: (Int, ChecklistItem, String) -> Unit,
    onRemoveChecklistItem: (Int, ChecklistItem) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    // The snooze inside the app. D-109.
    onSnooze: (LocalDateTime) -> Unit = {}
) {
    var openSheet by rememberSaveable { mutableStateOf<PlanSheet?>(null) }
    val paneTitleText = stringResource(R.string.task_details_heading)
    val gutter = tellcatContentGutter()

    Scaffold(
        modifier = modifier.semantics { paneTitle = paneTitleText },
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        // The toolbar takes the FAB slot so Scaffold lifts every snackbar above
        // it. Floating in the content, it was covered by them, D-094.
        floatingActionButton = {
            TaskDetailsActions(
                task = task,
                onToggleComplete = onToggleComplete,
                onStartFocus = onStartFocus,
                resumesSession = resumesSession,
                onDelete = onDelete,
                onShare = onShare
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            // A back arrow and nothing else. **No title**: the task's own title
            // is directly beneath it, and two stacked headings with the upper
            // one naming the app is the thing removed from Focus and from
            // Today. The screen name is published as `paneTitle` above, which a
            // screen reader announces and nothing draws.
            //
            // **No actions.** D-037 moved Delete out of the bar and into the
            // floating toolbar, where D-067 left it, so
            // the overflow that held it and nothing else is gone. A menu whose
            // only item is one action promises options it does not have, and
            // the trailing slot now matches the three list screens by being
            // empty here rather than by holding a different control.
            TellcatTopAppBar(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The scroll area ends at the keyboard. The app draws edge
                // to edge, so without this the viewport ran on underneath
                // it and a field low on the page could be typed into
                // unseen; nothing scrolls a field into a part of the
                // viewport the keyboard covers.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TellcatSpacing.md + gutter)
                // Clearance beneath the last Plan row, on the same terms the
                // lists reserve it under their FAB: a floating control that
                // covers the thing it acts on is worse than one that scrolls.
                .padding(bottom = TellcatDimensions.FabClearance),
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.md)
        ) {
            IdentityRegion(task = task, onEdit = onEdit)

            ChecklistRegion(
                taskId = task.id,
                checklist = task.checklist,
                onAdd = onAddChecklistItem,
                onSetDone = onSetChecklistItemDone,
                onEdit = onEditChecklistItem,
                onRemove = onRemoveChecklistItem
            )

            PlanRegion(
                task = task,
                today = today,
                onOpenSheet = { sheet -> openSheet = sheet }
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
        },
        onSnooze = { until ->
            openSheet = null
            onSnooze(until)
        }
    )
}

/**
 * The title and the notes, flush with the Plan rows' text. Completion is in the
 * toolbar since D-093, so nothing here looks like one more checklist item.
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
private fun IdentityRegion(task: Task, onEdit: (Task) -> Unit) {
    Column(modifier = Modifier.padding(top = TellcatSpacing.xs)) {
        TitleField(task = task, onEdit = onEdit)
        NotesField(task = task, onEdit = onEdit)
    }
}


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

    // A completed task's title reads as a completed row's does, struck through
    // on `onSurfaceVariant`, now that no checkbox above says so. Completing here
    // draws the line across it on the row's own spring, D-104 and D-111;
    // reopening takes it away at once. A title already complete when the screen
    // opens starts with the line drawn.
    val strike = remember(task.id) { Animatable(if (task.isCompleted) 1f else 0f) }
    val completionSpec = TellcatMotion.completion<Float>()
    LaunchedEffect(task.isCompleted) {
        if (task.isCompleted) strike.animateTo(1f, completionSpec) else strike.snapTo(0f)
    }
    val color by animateColorAsState(
        targetValue = if (task.isCompleted) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = TellcatMotion.stateColor(),
        label = "TitleColor"
    )
    // Struck a character at a time: the field draws its own text, so the line
    // is the font's own strikethrough over a growing run of it.
    val struck = ceil(draft.length * strike.value.coerceIn(0f, 1f)).toInt()

    TextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = color),
        visualTransformation = StruckThrough(struck),
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
internal fun borderlessFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
)

/**
 * A row for each field the task has set and a chip for each it has not, both in
 * the fields' one order. D-087.
 */
@Composable
private fun PlanRegion(task: Task, today: LocalDate, onOpenSheet: (PlanSheet) -> Unit) {
    val rows = PlanSheet.entries.mapNotNull { row ->
        row.value(task, today)?.let { value -> row to value }
    }
    val unset = PlanSheet.entries.filter { row -> rows.none { (set, _) -> set == row } }

    Column {
        SectionLabel(text = stringResource(R.string.task_plan))

        if (rows.isNotEmpty()) {
            PlanRowGroup {
                rows.forEachIndexed { index, (row, value) ->
                    PlanRow(
                        label = stringResource(row.labelRes),
                        value = value,
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = rows.size
                        ),
                        onClick = { onOpenSheet(row) }
                    )
                }
            }
        }

        if (unset.isNotEmpty()) {
            PlanAddChips(
                fields = unset,
                onOpenSheet = onOpenSheet,
                // A chip's 48dp touch target already leaves 8dp above its
                // outline, so under the rows this tops it up to `sm`.
                modifier = if (rows.isEmpty()) Modifier else Modifier.padding(top = TellcatSpacing.xxs)
            )
        }
    }
}

/** One assist chip per unset field, named as its row will be, read as "Not set". */
@Composable
private fun PlanAddChips(
    fields: List<PlanSheet>,
    onOpenSheet: (PlanSheet) -> Unit,
    modifier: Modifier = Modifier
) {
    val notSet = stringResource(R.string.task_plan_not_set)

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.xs)
    ) {
        fields.forEach { field ->
            AssistChip(
                onClick = { onOpenSheet(field) },
                label = { Text(stringResource(field.labelRes)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                },
                modifier = Modifier.semantics { stateDescription = notSet }
            )
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
     * What the row shows, or null when the field is not set and is offered as a
     * chip instead. D-087.
     */
    @Composable
    fun value(task: Task, today: LocalDate): String? = when (this) {
        SCHEDULED -> task.scheduledDate?.let { date -> scheduledDateLabel(date, today) }

        DUE -> task.dueDate?.let { date -> scheduledDateLabel(date, today) }

        // A snooze still to ring is what the reminder will do next, so the row
        // says it, in the row's own words. D-109.
        REMINDER -> task.reminderAt?.let { at ->
            val snoozed = task.snoozedUntil?.takeIf { task.hasLiveReminder() }
            reminderValue(snoozed ?: at, today, task.ringsLikeAlarm, snoozed = snoozed != null)
        }

        // Through `durationLabel`, so this reads 45m, 1h, 1h 30m like every
        // other duration in the app. Never "45 min".
        DURATION -> task.estimatedDurationMinutes?.let { minutes -> durationLabel(minutes).text }

        // The rule written out, which since D-027 can be "Every 2 weeks" or
        // "Mon, Wed, Fri" and not only one of four adjectives. When it stops is
        // deliberately absent; board frame 18 leaves it out and the sheet is
        // where a horizon belongs.
        REPEAT -> task.recurrence?.let { rule -> recurrenceSummary(rule, today) }
    }
}

/**
 * A reminder as a day and a time, in the reader's own locale, and whether it
 * rings like an alarm, as `editors/Reminder` draws the row. D-102.
 */
@Composable
private fun reminderValue(
    at: LocalDateTime,
    today: LocalDate,
    ringsLikeAlarm: Boolean,
    snoozed: Boolean = false
): String {
    // The phone's 12 or 24 hour setting, as the notification says it. D-079.
    val time = at.toLocalTime().format(rememberTimeFormat())

    val moment = stringResource(
        R.string.task_reminder_when,
        scheduledDateLabel(at.toLocalDate(), today),
        time
    )

    val said = if (snoozed) stringResource(R.string.task_reminder_snoozed, moment) else moment
    return if (ringsLikeAlarm) stringResource(R.string.task_reminder_when_alarm, said) else said
}

/** What the column holds at the largest system font scale, as on Focus. */
private const val TitleMaxLines = 4

/**
 * The hour a reminder falls back to when the clock cannot supply one: a day
 * other than today, or the last hour of one. See [suggestedReminderTime], D-071.
 */
internal val DefaultReminderTime: java.time.LocalTime = java.time.LocalTime.of(9, 0)

/** Opens whichever sheet the pressed row asked for, and writes what it returns. */
@Composable
private fun PlanSheetHost(
    open: PlanSheet?,
    task: Task,
    today: LocalDate,
    onDismiss: () -> Unit,
    onEdit: (Task) -> Unit,
    onSnooze: (LocalDateTime) -> Unit
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
            onSet = { at, alarm -> onEdit(task.copy(reminderAt = at, ringsLikeAlarm = alarm)) },
            // No reminder, nothing to ring. D-102.
            onClear = { onEdit(task.copy(reminderAt = null, ringsLikeAlarm = false)) },
            onDismiss = onDismiss,
            ringsLikeAlarm = task.ringsLikeAlarm,
            // Only while it has rung and waits for an answer, or is snoozed:
            // the moments a snooze means anything. D-109.
            canSnooze = task.canSnooze,
            onSnooze = onSnooze
        )

        PlanSheet.DURATION -> DurationSheet(
            selectedMinutes = task.estimatedDurationMinutes,
            onPick = { minutes -> onEdit(task.copy(estimatedDurationMinutes = minutes)) },
            onDismiss = onDismiss
        )

        PlanSheet.REPEAT -> RepeatSheet(
            selected = task.recurrence,
            today = today,
            anchorDay = task.scheduledDate ?: task.reminderAt?.toLocalDate() ?: today,
            onPick = { recurrence -> onEdit(task.copy(recurrence = recurrence)) },
            onDismiss = onDismiss
        )
    }
}

/**
 * The screen's actions, in one floating toolbar. D-093.
 *
 * Delete leads and Focus trails, D-037's order: in a horizontal group the thumb
 * lands nearest the reaching side, so the one-way action sits furthest from it.
 * Share sends the task as text, D-096. Complete is a toggle, and Focus leaves once
 * the task is done, since there is nothing left to work on.
 *
 * Focus is the one filled control, the screen's payoff, drawn with Tellcat's own
 * focus symbol rather than a play triangle, which D-064 found read as preview or
 * run. A tooltip names the two glyphs that are not universal.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailsActions(
    task: Task,
    onToggleComplete: () -> Unit,
    onStartFocus: () -> Unit,
    resumesSession: Boolean,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        IconButton(
            onClick = onDelete,
            // `error` is its second cue; the content description is its word.
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.task_delete)
            )
        }

        IconButton(onClick = onShare) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.task_share)
            )
        }

        CompleteAndFocus(
            task = task,
            onToggleComplete = { completed ->
                TellcatHaptics.taskCompletion(haptics, completed)
                onToggleComplete()
            },
            onStartFocus = onStartFocus,
            resumesSession = resumesSession
        )
    }
}

/**
 * Complete, and Focus beside it, as one piece of the toolbar. D-111.
 *
 * Completing grows Complete into the place Focus leaves, as a filled pill
 * reading "Completed", while Focus fades where it stands. Nothing slides: the
 * spot the thumb tapped stays inside Complete, so the second tap that takes the
 * task back lands on it (D-094), and Delete and Share only move if the word needs
 * more room than Focus gave up. Reopening plays it back on `reveal`, without the
 * spring, because a reversal is not a moment.
 *
 * The control presses in while the finger is down and springs back on release,
 * on `completion`. Each value follows its target from wherever it is, so a
 * second tap mid-gesture turns it round rather than restarting it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteAndFocus(
    task: Task,
    onToggleComplete: (Boolean) -> Unit,
    onStartFocus: () -> Unit,
    resumesSession: Boolean
) {
    val completed = task.isCompleted
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current

    // How far Complete has grown into Focus's place, 0 to 1. Clamped where it
    // sets widths, so the spring's overshoot never pushes the toolbar wider.
    val grown by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = if (completed) TellcatMotion.completion() else TellcatMotion.reveal(),
        label = "CompleteGrown"
    )
    val shown by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = TellcatMotion.stateColor(),
        label = "CompleteLabel"
    )
    val container by animateColorAsState(
        targetValue = if (completed) colors.secondaryContainer else Color.Transparent,
        animationSpec = TellcatMotion.stateColor(),
        label = "CompleteContainer"
    )
    val content by animateColorAsState(
        targetValue = if (completed) colors.onSecondaryContainer else colors.onSurfaceVariant,
        animationSpec = TellcatMotion.stateColor(),
        label = "CompleteContent"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        // Only on the way to complete: reopening is a reversal, not a moment,
        // and keeps Material's ripple alone.
        targetValue = if (pressed && !completed) CompletionPressScale else 1f,
        animationSpec = TellcatMotion.completion(),
        label = "CompletePress"
    )

    // The pill's full width: the word at the current font scale with the icon
    // and Material's button padding, and never less than the two places it
    // spans, so Focus's place is always enough to cover.
    val word = stringResource(R.string.task_details_completed)
    val wordStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val check = painterResource(R.drawable.ic_check)
    val wordWidth = with(density) { measurer.measure(word, wordStyle).size.width.toDp() }
    val iconWidth = with(density) { check.intrinsicSize.width.toDp() }
    val place = TellcatDimensions.TouchTargetMin
    val inset = (place - TellcatDimensions.ToolbarControlSize) / 2
    val full = maxOf(
        place * 2,
        TellcatSpacing.md * 2 + iconWidth + TellcatSpacing.xs + wordWidth + inset * 2
    )
    val g = grown.coerceIn(0f, 1f)
    val completeWidth = lerp(place, full, g)

    // The row checkbox's words, because it is the same act on the same task.
    val completeLabel = stringResource(
        if (completed) R.string.task_row_mark_incomplete else R.string.task_row_mark_complete,
        task.title
    )

    ToolbarTooltip(completeLabel) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(completeWidth)
                .height(place)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(width = completeWidth - inset * 2, height = TellcatDimensions.ToolbarControlSize)
                    .graphicsLayer {
                        scaleX = press
                        scaleY = press
                    }
                    .clip(CircleShape)
                    .background(container)
                    .toggleable(
                        value = completed,
                        role = Role.Checkbox,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onValueChange = onToggleComplete
                    )
                    .semantics { contentDescription = completeLabel }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics {}
                ) {
                    Icon(painter = check, contentDescription = null, tint = content)
                    Spacer(Modifier.width(TellcatSpacing.xs * g))
                    // Revealed as the pill grows, never squeezed onto two lines.
                    Text(
                        text = word,
                        style = wordStyle,
                        color = content,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                                layout((placeable.width * g).roundToInt(), placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                            .graphicsLayer { alpha = shown }
                    )
                }
            }
        }
    }

    // Focus's place narrows as Complete grows over it, and Focus fades where it
    // stands rather than moving. It is gone for a screen reader at once, since
    // a finished task has nothing to work on. D-093.
    if (g < 1f) {
        val focusLabel = stringResource(
            if (resumesSession) R.string.task_resume_focus else R.string.task_start_focus
        )
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .width(place * (1f - g))
                .height(place)
                .clipToBounds()
                .graphicsLayer { alpha = 1f - shown }
                .then(if (completed) Modifier.clearAndSetSemantics {} else Modifier)
        ) {
            Box(Modifier.wrapContentWidth(align = Alignment.End, unbounded = true)) {
                ToolbarTooltip(focusLabel) {
                    FilledIconButton(onClick = { if (!completed) onStartFocus() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_focus),
                            contentDescription = focusLabel
                        )
                    }
                }
            }
        }
    }
}

/** A long-press tooltip naming a toolbar glyph for anyone who has not met it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content
    )
}

/**
 * Strikes the first [chars] characters of a field's text through, so the line
 * can travel across a title the field lays out itself. D-111.
 */
private data class StruckThrough(val chars: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val end = chars.coerceIn(0, text.length)
        if (end == 0) return TransformedText(text, OffsetMapping.Identity)
        val struck = buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), 0, end)
        }
        return TransformedText(struck, OffsetMapping.Identity)
    }
}
