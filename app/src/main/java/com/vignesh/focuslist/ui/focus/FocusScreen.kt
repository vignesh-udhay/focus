package com.vignesh.focuslist.ui.focus

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.FocusSession
import com.vignesh.focuslist.core.domain.FocusState
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.focusReadout
import com.vignesh.focuslist.core.domain.focusStateOf
import com.vignesh.focuslist.core.domain.isClockRunning
import com.vignesh.focuslist.core.notification.FocusSessionVisibility
import com.vignesh.focuslist.core.notification.canPostNotifications
import com.vignesh.focuslist.ui.component.FocusMascot
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Focus, the execution mode.
 *
 * One surface with six states, opened as a sheet over the screen that asked for
 * it. `focus.md` holds the design; `docs/decisions.md` D-013, D-015 and D-046
 * hold the decisions behind it.
 *
 * Top to bottom: the task title, the cat, one status line carrying the clock
 * and the budget, and one row of two controls.
 *
 * **The shape is gone and D-046 took it.** The Cookie said whether the clock
 * was running and nothing else, which is a thing the app's own mascot says
 * better and warmer; the digits came out of it and joined the status line at
 * body size, which finally makes the task title the largest object on a screen
 * built to stop clock-watching.
 *
 * **Leaving never destroys anything.** D-015: the drag, the scrim and the back
 * gesture all pause. None of them can tell "I am finished with this" from "I
 * need to look at something else for a minute", and of the two mistakes,
 * stopping when the user meant to pause is the unrecoverable and invisible one.
 *
 * There is no dismiss button. D-032 removed it: Material's drag handle sits
 * directly above where it was and says the same thing, and two collapse
 * affordances stacked is one more than the sheet needs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusSheet(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier
) {
    val task by viewModel.focusedTask.collectAsStateWithLifecycle()
    val session by viewModel.focusSession.collectAsStateWithLifecycle()

    // The sheet carries its own, because the screen it opened over is behind a
    // scrim and a bar shown down there would be invisible. Both effects watch
    // the one app-wide offer, so the copy still running underneath cannot
    // disagree with this one.
    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    // Nothing to draw yet, or nothing left to draw. Either way this composable
    // does not decide which: **every exposed flow begins on a placeholder before
    // storage has answered**, and a screen reading that placeholder cannot tell
    // "the task is gone" from "not loaded yet". Ending Focus here closed the
    // sheet on the way in, which broke Ready, the one entry that has nothing
    // else to hold it open. `FocusSessionSemanticsTest` caught it.
    //
    // The task actually being gone is watched in `TaskListViewModel` against
    // `repository.observeTasks()`, which only emits once it has really read.
    // `focus.md` names that as the rule and this is the mistake it warns about.
    val current = task ?: return

    // **And a session, since D-054.** Ready was what a null session drew, and it
    // has no entry point left: the sheet is opened by starting a clock or by
    // resuming one, and `_isFocusSheetOpen` is not persisted, so a restart cannot
    // put the sheet back without one. Returning is the same answer the missing
    // task gets above, and for the same reason: this composable does not decide
    // that Focus is over, `TaskListViewModel` does.
    val active = session ?: return

    ModalBottomSheet(
        // Pauses. Covers the drag handle, the drag, the scrim and the back
        // gesture, because ModalBottomSheet routes all of them here, and D-015
        // wants the same non-destructive answer from every one.
        onDismissRequest = viewModel::leaveFocusSheet,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            // Full height or gone. A half-open Focus would be one task peeking
            // over the screen it was trying to replace.
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        modifier = modifier
    ) {
        FocusSheetContent(
            task = current,
            session = active,
            onComplete = { viewModel.completeFromFocus(current.id) },
            onPause = viewModel::pauseFocusSession,
            onResume = viewModel::resumeFocusSession,
            onExtend = viewModel::extendFocusSession,
            snackbarHostState = snackbarHostState
        )
    }
}

/**
 * What the sheet holds: one task, being worked on.
 *
 * Stateless, and the preview and semantics seam.
 *
 * The column is centred in the content area rather than pinned under the app
 * bar. This is a single-purpose mode screen with one column on it, and hanging
 * that column from the top left the lower half of the screen empty for no
 * reason.
 */
@Composable
private fun FocusSheetContent(
    task: Task,
    session: FocusSession,
    onComplete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtend: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val paneTitleText = stringResource(R.string.focus_title)

    // Where the session has got to, resampled while the clock runs. One sample
    // feeds the digits, the status line and the shape, so they cannot disagree
    // about which second it is: a countdown reaching zero and the state becoming
    // EstimateReached are the same event, and sampling them apart is how a
    // screen ends up offering Pause under a readout of 00:00.
    val reading = rememberFocusReading(session, task.estimatedDurationMinutes)

    Box(
        modifier = modifier
            .fillMaxSize()
            // The screen names itself to a screen reader and to nothing else.
            // While the app bar carried a centred "Focus" the screen had two
            // centred headings stacked, and the upper one named the app instead
            // of the work.
            .semantics { paneTitle = paneTitleText }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = FocuslistDimensions.FocusColumnWidth)
                .padding(horizontal = FocuslistSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FocuslistDimensions.FocusColumnGap)
        ) {
            FocusTaskTitle(title = task.title)

            FocusMascot(running = reading.state.isClockRunning)

            FocusStatusLine(reading = reading, estimateMinutes = task.estimatedDurationMinutes)

            FocusActions(
                state = reading.state,
                onComplete = onComplete,
                onPause = onPause,
                onResume = onResume,
                onExtend = onExtend
            )
        }

        UndoSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Only a running session is on screen for the notification's purposes, and
    // only a running one has a moment to announce.
    if (reading.state.isClockRunning) {
        TrackSessionVisibility()
    }

    // Asked once the sheet has arrived rather than as it composes. The
    // permission dialog is a system window and opens over whatever is on screen,
    // so requesting it on the way in put a prompt over the sheet's own entrance
    // on the very first session a user ever ran.
    AskToNotifyOnce(
        hasEstimate = task.estimatedDurationMinutes != null,
        enabled = reading.state.isClockRunning
    )
}

/**
 * The task, and the largest thing on the screen.
 *
 * Above the shape rather than inside it, which D-014 settles with arithmetic
 * rather than taste: a cookie yields about 70% of its box as usable area, so
 * four lines at 200% font scale would need a 514dp square on a 412dp screen.
 * Outside it the title has the full column width and the four-line cap holds at
 * every font scale.
 *
 * `onSurface`, not `onPrimaryContainer`. It sits on the background now that it
 * is outside the shape, and only the digits take the container's role. The two
 * are close in the fallback palette, so getting this wrong stays invisible until
 * a dynamic scheme pulls them apart.
 */
@Composable
private fun FocusTaskTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = TitleMaxLines,
        // Ellipsis rather than clipping: a title that ends in a marker says it
        // was shortened, where one that stops mid-word says the screen is
        // broken. The full title is one tap away in Task Details.
        overflow = TextOverflow.Ellipsis,
        // The work is what this screen is about, so it is the heading a screen
        // reader lands on, in every state.
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
    )
}

/**
 * The one line under the cat: the clock, then the budget.
 *
 * **The clock lives here now, and D-046 moved it.** It used to be Headline
 * Small inside the shape, which made the countdown the second largest thing on
 * a screen whose stated purpose is to stop clock-watching. At body size beside
 * the budget it is still a real readout, and three of the six states still
 * cannot be told apart without it, but it has stopped being a display object
 * and the task title is now unambiguously the largest thing here.
 *
 * **The budget carries only what the controls cannot say.** The button already
 * reads Pause or Resume, so putting the state in text would say it twice. What
 * is left is the budget, and the one moment that has no control to announce it.
 *
 * Ready and Running read the same budget, deliberately. What tells them apart is
 * the pose, the icon on the button, and the digits moving.
 */
@Composable
private fun FocusStatusLine(reading: FocusReading, estimateMinutes: Int?) {
    val budget = when (reading.state) {
        FocusState.Running ->
            stringResource(R.string.focus_status_estimate, estimateMinutes ?: 0)

        FocusState.Paused -> stringResource(
            R.string.focus_status_remaining,
            // Rounded up, so a session with forty seconds left says one minute
            // rather than nought. Nought minutes left on a clock that has not
            // reached its estimate is a lie the floor would tell every time the
            // last minute was paused in.
            ((reading.remainingMinutes ?: 0L) + 1L).toInt()
        )

        FocusState.EstimateReached -> stringResource(R.string.focus_status_estimate_reached)

        FocusState.OpenEnded, FocusState.OpenEndedPaused ->
            stringResource(R.string.focus_status_no_limit)
    }

    // **The digits get a spoken form, and until now they had none.** `strings.xml`
    // has carried `focus_readout_remaining` and `focus_readout_elapsed` with a
    // comment saying the readout "is digits, so it carries a spoken form for
    // TalkBack", and no Kotlin ever referenced either: the strings were written
    // and never wired. A screen reader was left announcing a bare "44:37", which
    // says nothing about whether that is time left or time spent, and those are
    // opposite readings of the same four digits.
    //
    // Which one it is follows `focusReadout`'s own branch. It shows `remaining`
    // whenever there is an estimate to measure against and falls back to
    // `elapsed` when there is not, and the two open-ended states are precisely
    // the states with no estimate.
    val spokenReadout = stringResource(
        when (reading.state) {
            FocusState.OpenEnded, FocusState.OpenEndedPaused -> R.string.focus_readout_elapsed
            else -> R.string.focus_readout_remaining
        },
        reading.readout
    )

    // The whole line, because a description replaces the text rather than adding
    // to it: describing only the clock would silence the budget beside it.
    // Joined with a comma rather than the middle dot the line is drawn with,
    // since a separator that reads as a pause in print does not read as one
    // aloud.
    val spokenLine = stringResource(R.string.focus_status_line_spoken, spokenReadout, budget)

    Text(
        text = stringResource(R.string.focus_status_line, reading.readout, budget),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { contentDescription = spokenLine }
    )
}

/**
 * The two controls, and which two they are.
 *
 * Complete is in all six states, because finishing is the thing this screen is
 * for and it must never be more than one tap away. What changes is the control
 * beside it.
 *
 * **Complete leads and the clock control trails.** D-046. Two reasons, and the
 * second is the stronger one. Complete now sits in the same place in all six
 * states, where before it trailed in five and led in Estimate reached, which is
 * the one state that has no clock control to trail. And the clock is the control
 * pressed repeatedly inside a session, start then pause then resume, where
 * Complete is pressed once at the end, so the repeated one belongs where the
 * thumb already is. `expressive-components.md` makes the same argument for the
 * Start focus pill on Task Details.
 *
 * **The clock control is an icon button, and that is a width decision.** Holding
 * no text it does not grow with the font scale: at 200% two worded buttons come
 * to roughly 223dp and 198dp and overflow the 364dp row, while a circle and one
 * word come to about 282dp.
 *
 * Estimate reached is the only state with no clock control, since there is no
 * clock left to control. It carries two worded buttons, and Complete is the
 * primary there: a timer running out is more often the moment work is finished
 * than the moment it needs extending, and the control that reads as the default
 * should be the likelier one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusActions(
    state: FocusState,
    onComplete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtend: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)) {
        if (state == FocusState.EstimateReached) {
            FocusWordedButton(
                text = stringResource(R.string.focus_complete),
                primary = true,
                onClick = onComplete
            )
            FocusWordedButton(
                text = stringResource(R.string.focus_extend, FocusSession.ExtensionMinutes),
                primary = false,
                onClick = onExtend
            )
            return@Row
        }

        val icon: Int
        val label: Int
        val onClock: () -> Unit

        // **No Start branch, since D-054.** Ready was the only state that offered
        // one, because it was the only state whose clock had not begun. Every way
        // into this screen now starts or resumes a clock, so the control beside
        // Complete is always Pause or Resume.
        when (state) {
            FocusState.Running, FocusState.OpenEnded -> {
                icon = R.drawable.ic_pause
                label = R.string.focus_pause
                onClock = onPause
            }

            else -> {
                icon = R.drawable.ic_play_arrow
                label = R.string.focus_resume
                onClock = onResume
            }
        }

        FocusWordedButton(
            text = stringResource(R.string.focus_complete),
            primary = false,
            onClick = onComplete
        )

        FilledIconButton(
            onClick = onClock,
            modifier = Modifier.size(FocuslistDimensions.FocusControlSize)
        ) {
            Icon(
                painter = painterResource(icon),
                // Named for the action, never for the glyph. A screen reader
                // announcing "play" would describe the drawing rather than what
                // pressing it does.
                contentDescription = stringResource(label)
            )
        }
    }
}

/**
 * One worded control in the action row.
 *
 * Complete is tonal in every state but Estimate reached, because it is the
 * second of two actions. A real [Button] rather than a drawn one, so the ripple,
 * the state layers, the focus indication and the button role all come from
 * Material.
 */
@Composable
private fun FocusWordedButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors =
            if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        // A floor rather than a fixed height. Pinned at exactly the control
        // height, a label at 200% font scale was cut through the middle of its
        // letters; the button is allowed to grow to hold its own text.
        modifier = Modifier.heightIn(min = FocuslistDimensions.ActionHeight)
    ) {
        Text(text = text, maxLines = 1)
    }
}

/** One sampling of the session's clock: what it reads, and what state that is. */
private data class FocusReading(
    val readout: String,
    val state: FocusState,
    val remainingMinutes: Long?
)

/**
 * The clock, resampled while it runs.
 *
 * Held in composition state rather than read in the draw phase, unlike the
 * shape. The shape is a path that can be redrawn for nothing; text has to be
 * measured and laid out, so it is recomposed once a second and no faster.
 *
 * A stopped clock has one reading and the first sample already took it, so
 * neither paused state ever ticks. Together with the mascot only changing pose on
 * a state change, that is what makes `focus.md`'s claim true that the screen goes
 * idle: nothing here spends a frame to say what it said last frame.
 */
@Composable
private fun rememberFocusReading(
    session: FocusSession,
    estimateMinutes: Int?
): FocusReading {
    fun sample(): FocusReading {
        val now = Instant.now()

        return FocusReading(
            readout = focusReadout(session, estimateMinutes, now),
            state = focusStateOf(session, estimateMinutes, now),
            remainingMinutes = session.remaining(now, estimateMinutes)?.toMinutes()
        )
    }

    var reading by remember(session, estimateMinutes) { mutableStateOf(sample()) }

    LaunchedEffect(session, estimateMinutes) {
        if (session.isPaused) return@LaunchedEffect

        while (true) {
            delay(ReadoutTickMillis)
            reading = sample()
        }
    }

    return reading
}

/**
 * Reports whether the session is actually in front of the user.
 *
 * Composition is not enough to answer that. Pressing home stops the activity but
 * leaves the composition standing, so a flag set on entering composition and
 * cleared on leaving it stays true the whole time the user is in another app,
 * which is precisely when the notification is supposed to fire. The lifecycle is
 * what knows the difference.
 *
 * Started rather than resumed, so a session sitting behind a permission dialog
 * still counts as on screen: the shape is visible, and the announcement would be
 * telling the user something they can see.
 */
@Composable
private fun TrackSessionVisibility() {
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> FocusSessionVisibility.isSessionOnScreen = true
                Lifecycle.Event.ON_STOP -> FocusSessionVisibility.isSessionOnScreen = false
                else -> Unit
            }
        }

        owner.lifecycle.addObserver(observer)

        onDispose {
            owner.lifecycle.removeObserver(observer)
            FocusSessionVisibility.isSessionOnScreen = false
        }
    }
}

/**
 * Asks for notification permission the first time it could matter.
 *
 * The first session on a task that has an estimate is the first moment the app
 * has anything to notify about, which is the only context in which the question
 * can be answered well. Never at launch.
 */
@Composable
private fun AskToNotifyOnce(hasEstimate: Boolean, enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(hasEstimate, enabled, asked) {
        if (!enabled || !hasEstimate || asked) return@LaunchedEffect
        if (context.canPostNotifications()) return@LaunchedEffect

        asked = true
        request.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** How often the readout moves. A second, which `mm:ss` needs and no more. */
private const val ReadoutTickMillis = 1_000L

/** What the column holds at the largest system font scale. D-014 has the working. */
private const val TitleMaxLines = 4

private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private val SampleTask = Task(
    id = "sample-focus",
    title = "Refine landing page hero",
    createdAt = SampleTimestamp,
    scheduledDate = LocalDate.of(2026, 1, 1),
    estimatedDurationMinutes = 45
)

/** Thirty seconds in, so the running preview reads 44:30 rather than 45:00. */
private val PreviewStart: Instant = Instant.now().minusSeconds(30)

@Composable
private fun FocusStatePreview(task: Task, session: FocusSession) {
    FocuslistTheme(dynamicColor = false) {
        FocusSheetContent(
            task = task,
            session = session,
            onComplete = {},
            onPause = {},
            onResume = {},
            onExtend = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(name = "Focus running", showBackground = true, heightDp = 720)
@Preview(
    name = "Focus running dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun FocusRunningPreview() =
    FocusStatePreview(SampleTask, FocusSession(startedAt = PreviewStart))

@Preview(name = "Focus paused", showBackground = true, heightDp = 720)
@Composable
private fun FocusPausedPreview() = FocusStatePreview(
    SampleTask,
    FocusSession(
        startedAt = PreviewStart.minus(Duration.ofMinutes(12).plusSeconds(12)),
        pausedAt = PreviewStart
    )
)

@Preview(name = "Focus estimate reached", showBackground = true, heightDp = 720)
@Composable
private fun FocusEstimateReachedPreview() = FocusStatePreview(
    SampleTask,
    FocusSession(startedAt = Instant.now().minus(Duration.ofMinutes(60)))
)

@Preview(name = "Focus open-ended", showBackground = true, heightDp = 720)
@Composable
private fun FocusOpenEndedPreview() = FocusStatePreview(
    SampleTask.copy(estimatedDurationMinutes = null),
    FocusSession(startedAt = Instant.now().minus(Duration.ofMinutes(12).plusSeconds(43)))
)

@Preview(name = "Focus open-ended paused", showBackground = true, heightDp = 720)
@Composable
private fun FocusOpenEndedPausedPreview() = FocusStatePreview(
    SampleTask.copy(estimatedDurationMinutes = null),
    FocusSession(
        startedAt = PreviewStart.minus(Duration.ofMinutes(12).plusSeconds(43)),
        pausedAt = PreviewStart
    )
)

/** 200%, with a four-line title, which is where the row and the cap are tested. */
@Preview(name = "Focus running large font", showBackground = true, heightDp = 900, fontScale = 2f)
@Composable
private fun FocusLargeFontPreview() = FocusStatePreview(
    SampleTask.copy(
        title = "Refine the landing page hero, the pricing table and the footer " +
            "before the review on Thursday morning"
    ),
    FocusSession(startedAt = PreviewStart)
)
