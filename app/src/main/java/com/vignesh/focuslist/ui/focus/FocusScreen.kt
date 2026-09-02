package com.vignesh.focuslist.ui.focus

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.graphics.shapes.Morph
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistMotionEnabled
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.focusProgress
import com.vignesh.focuslist.core.notification.FocusSessionVisibility
import com.vignesh.focuslist.core.notification.canPostNotifications
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/**
 * Focus, the execution mode.
 *
 * Two states over one destination, and the difference between them is the
 * whole design.
 *
 * Ready is a place: the task that is next, how long it was estimated at, and a
 * control to begin. The navigation stays, because the user only navigated
 * here and may want to leave the same way.
 *
 * Session is a mode: the task grows into the screen, the navigation goes, and
 * what is left is the task, the action that finishes it, and a quiet line
 * saying what follows. Taking the navigation away is only honest because the
 * user asked for the mode and can leave it by an on-screen control or by back.
 *
 * The queue behind it is unchanged. Completing a task takes it out, the next
 * appears without an advance step, and when none are left the session ends on
 * its own rather than stranding the user in a screen with no navigation.
 */
@Composable
fun FocusScreen(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val task by viewModel.focusedTask.collectAsStateWithLifecycle()
    val queue by viewModel.focusQueue.collectAsStateWithLifecycle()
    val startedAt by viewModel.focusSessionStartedAt.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    // What follows the one being worked on. Read from the same queue Focus
    // draws from, so it cannot disagree with what appears next.
    val nextTask = remember(queue, task) {
        val index = queue.indexOfFirst { it.id == task?.id }
        queue.getOrNull(index + 1).takeIf { index >= 0 }
    }

    FocusContent(
        task = task,
        nextTask = nextTask,
        startedAt = startedAt,
        onStart = viewModel::startFocusSession,
        onStop = viewModel::stopFocusSession,
        // The same write every list makes, so finishing a task here is as
        // undoable as finishing it anywhere else.
        onComplete = { id -> viewModel.toggleComplete(id) },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = bottomBar
    )
}

/**
 * The Focus layout.
 *
 * Stateless: it renders the task it is handed in whichever of the two states
 * it is told, and reports starting, stopping and completing.
 *
 * [bottomBar] is rendered only when the session is not running. The rail on a
 * wide window is not this screen's to hide and is dealt with above the graph.
 */
@Composable
private fun FocusContent(
    task: Task?,
    nextTask: Task?,
    startedAt: Instant?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {}
) {
    // Back leaves the session before it leaves the screen. Without this, back
    // would exit Focus altogether from a state the user cannot see their way
    // out of, which is the trap hiding the navigation risks in the first place.
    val isSessionActive = startedAt != null

    BackHandler(enabled = isSessionActive, onBack = onStop)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = { if (!isSessionActive) bottomBar() },
        topBar = {
            // Nothing above the task in a session. The title names a
            // destination, and in a session this is not one.
            if (!isSessionActive) {
                FocuslistTopAppBar(title = stringResource(R.string.focus_title))
            }
        }
    ) { innerPadding ->
        if (task == null) {
            TaskListEmptyState(
                headline = stringResource(R.string.focus_empty_headline),
                supporting = stringResource(R.string.focus_empty_supporting),
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            // Read here rather than inside transitionSpec, which is not a
            // composable scope. One spec drives both halves: the scale is the
            // spatial change and the fade rides along with it.
            val sessionSpec = FocuslistMotion.focusSession<Float>()

            // The transition is decoration: it says nothing the two states do
            // not already say, so a user who asked for no motion simply gets
            // the next state. The shape inside the session is not covered by
            // this, because how far the session has run is information and
            // holding it still would be withholding an answer.
            val animate = focuslistMotionEnabled()

            AnimatedContent(
                targetState = isSessionActive,
                transitionSpec = {
                    if (!animate) {
                        return@AnimatedContent EnterTransition.None togetherWith
                            ExitTransition.None
                    }

                    // Grows out of where the task already is. Both states
                    // centre it, so the centre is the task, and the screen
                    // appears to expand from the words rather than from a
                    // corner or an edge.
                    (scaleIn(
                        animationSpec = sessionSpec,
                        initialScale = SessionEnterScale,
                        transformOrigin = TransformOrigin.Center
                    ) + fadeIn(animationSpec = sessionSpec))
                        .togetherWith(
                            scaleOut(
                                animationSpec = sessionSpec,
                                targetScale = SessionExitScale,
                                transformOrigin = TransformOrigin.Center
                            ) + fadeOut(animationSpec = sessionSpec)
                        )
                },
                modifier = Modifier.padding(innerPadding),
                label = "focus session"
            ) { inSession ->
                if (inSession && startedAt != null) {
                    FocusSession(
                        task = task,
                        nextTask = nextTask,
                        startedAt = startedAt,
                        onStop = onStop,
                        onComplete = { onComplete(task.id) }
                    )
                } else {
                    FocusReady(
                        task = task,
                        onStart = onStart,
                        onComplete = { onComplete(task.id) }
                    )
                }
            }
        }
    }
}

/**
 * Focus before it has started: what is next, and the control to begin.
 *
 * Complete stays available. A task can turn out to be already done, or take
 * ten seconds, and making the user enter a session to tick it off would be
 * ceremony for its own sake.
 */
@Composable
private fun FocusReady(
    task: Task,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = focuslistContentGutter())
            .padding(horizontal = FocuslistSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FocusTaskTitle(task = task)

        Button(
            onClick = onStart,
            modifier = Modifier.padding(top = FocuslistSpacing.xl)
        ) {
            Text(stringResource(R.string.focus_start))
        }

        TextButton(
            onClick = onComplete,
            modifier = Modifier.padding(top = FocuslistSpacing.xs)
        ) {
            Text(stringResource(R.string.focus_complete))
        }
    }
}

/**
 * Focus while it is running.
 *
 * The shape is the only thing on this screen that is not text, and in this
 * phase it does not move: it is the ground the task sits on. Phase two gives
 * it a job, morphing it across the estimate so that the passage of time is
 * visible without a number to check. A shape that merely animated would be
 * decoration, which `PRODUCT.md` rules out.
 *
 * Stop is an on-screen control and not only a gesture, because gesture
 * navigation leaves no visible back affordance and this screen has hidden the
 * navigation bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusSession(
    task: Task,
    nextTask: Task?,
    startedAt: Instant,
    onStop: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sampled, not accumulated: each tick asks the clock what time it is and
    // works the fraction out again, so a session that was frozen while the
    // user was in another app comes back where it actually is.
    val progress = rememberFocusProgress(startedAt, task.estimatedDurationMinutes)

    AskToNotifyOnce(hasEstimate = task.estimatedDurationMinutes != null)

    TrackSessionVisibility()

    Box(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(FocuslistSpacing.xs)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.focus_stop)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = focuslistContentGutter())
                .padding(horizontal = FocuslistSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // Capped, so a tablet gets a shape and not a billboard.
                    .sizeIn(maxWidth = SessionShapeMaxSize, maxHeight = SessionShapeMaxSize)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .focusProgressShape(
                        progress = progress,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    .padding(FocuslistSpacing.lg)
            ) {
                FocusTaskTitle(
                    task = task,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    // The shape is a fixed square, so the title inside it is
                    // the one piece of text in the app with a hard ceiling. At
                    // 200% font a long title otherwise runs past the shape and
                    // is cut through the middle of a line, which reads as
                    // broken rather than as truncated. Four lines is what the
                    // square holds at the largest scale, and the full title is
                    // one tap away in the details sheet.
                    maxLines = SessionTitleMaxLines
                )
            }

            Button(
                onClick = onComplete,
                modifier = Modifier.padding(top = FocuslistSpacing.xl)
            ) {
                Text(stringResource(R.string.focus_complete))
            }
        }

        if (nextTask != null) {
            Text(
                text = stringResource(R.string.focus_next, nextTask.title),
                style = MaterialTheme.typography.bodyMedium,
                // Dimmed rather than blurred: Modifier.blur needs API 31 and
                // the app supports 29, so the effect that works everywhere is
                // the one that carries the meaning.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = FocuslistSpacing.lg)
                    .padding(bottom = FocuslistSpacing.xl)
            )
        }
    }
}

/**
 * Reports whether the session is actually in front of the user.
 *
 * Composition is not enough to answer that. Pressing home stops the activity
 * but leaves the composition standing, so a flag set on entering composition
 * and cleared on leaving it stays true the whole time the user is in another
 * app, which is precisely when the notification is supposed to fire. The
 * lifecycle is what knows the difference.
 *
 * Started rather than resumed, so a session sitting behind a permission dialog
 * still counts as on screen: the shape is visible, and the announcement would
 * be telling the user something they can see.
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

        // The observer only reports transitions, and this usually composes
        // into an already-started activity, so the current state has to be
        // read once on the way in.
        FocusSessionVisibility.isSessionOnScreen =
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        owner.lifecycle.addObserver(observer)

        onDispose {
            owner.lifecycle.removeObserver(observer)
            // Leaving the session, not leaving the app: whatever happens next,
            // there is no session on screen to be redundant with.
            FocusSessionVisibility.isSessionOnScreen = false
        }
    }
}

/**
 * Asks for notification permission, at the first moment there is anything to
 * notify about.
 *
 * Not at launch, and not on a task with no estimate. `POST_NOTIFICATIONS` is
 * a question the user can only answer well in context, and the context is a
 * session that has a moment to announce. Asked once per process: Android stops
 * showing the dialog after a refusal, and asking again would be the app
 * pestering a system that has already stopped listening.
 *
 * Refusal costs nothing on screen. The shape still shows progress; the user
 * simply is not told when they are elsewhere, which is what they said.
 */
@Composable
private fun AskToNotifyOnce(hasEstimate: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { asked = true }

    LaunchedEffect(hasEstimate, asked) {
        if (!asked && hasEstimate && !context.canPostNotifications()) {
            asked = true
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Progress through the estimate, resampled while the session runs.
 *
 * Returned as a lambda rather than a value so the caller can read it inside a
 * draw block. Read as state here, every tick would recompose the whole session;
 * read at draw time, a tick redraws one shape and nothing else is touched.
 *
 * Ticking stops when the task has no estimate, since there is no fraction to
 * follow, and once the estimate is used up, since the value cannot change
 * again. A session left running for hours is not paying for a coroutine that
 * has nothing left to say.
 */
@Composable
private fun rememberFocusProgress(
    startedAt: Instant,
    estimatedDurationMinutes: Int?
): () -> Float? {
    val progress = remember(startedAt, estimatedDurationMinutes) {
        mutableStateOf(focusProgress(startedAt, Instant.now(), estimatedDurationMinutes))
    }

    LaunchedEffect(startedAt, estimatedDurationMinutes) {
        if (estimatedDurationMinutes == null) return@LaunchedEffect

        while (true) {
            val current = focusProgress(startedAt, Instant.now(), estimatedDurationMinutes)
            progress.value = current
            if (current == null || current >= 1f) return@LaunchedEffect
            delay(ProgressTickMillis)
        }
    }

    return { progress.value }
}

/**
 * Fills the node with the shape the session has morphed into.
 *
 * Drawn rather than clipped to. A [androidx.compose.ui.graphics.Shape] would
 * have to be a new object on every tick to change, which puts the work in
 * layout; drawing reads [progress] in the draw phase, where a changed value
 * costs one redraw of one node.
 *
 * A task with no estimate gets the starting shape and no movement at all. The
 * alternative would be a shape drifting to a rhythm of its own, which would
 * look like information and be none.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.focusProgressShape(
    progress: () -> Float?,
    color: Color
): Modifier {
    // Nearly a circle to plainly a circle is not a journey anyone would
    // notice across forty-five minutes, so the two ends are different enough
    // to read at a glance and quiet enough not to ask for one.
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Clover4Leaf) }
    val path = remember { Path() }

    return drawBehind {
        // The polygons are normalised, so the path arrives in a unit box and
        // has to be scaled to the node and recentred, exactly as the Material
        // shape helper does it.
        morph.toPath(progress = progress() ?: 0f, path = path)
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(center - path.getBounds().center)
        drawPath(path, color)
    }
}

/**
 * The task, and how long it was reckoned to take.
 *
 * The estimate is shown wherever the task is. Today already carries it, and a
 * screen about doing the work that dropped the one number describing its size
 * would be throwing away what the user already said.
 */
@Composable
private fun FocusTaskTitle(
    task: Task,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = task.title,
            // The strongest emphasized treatment in the app. This is the one
            // screen showing one thing.
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            // Ellipsis rather than clipping: a title that ends in a marker
            // says it was shortened, where one that stops mid-word says the
            // screen is broken.
            overflow = TextOverflow.Ellipsis,
            // The task is what this screen is about, so it is the heading a
            // screen reader should land on.
            modifier = Modifier.semantics { heading() }
        )

        task.estimatedDurationMinutes?.let { minutes ->
            Text(
                text = stringResource(R.string.focus_estimate_minutes, minutes),
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = FocuslistSpacing.xs)
            )
        }
    }
}

/**
 * How often the shape is asked to move.
 *
 * A second is far finer than the eye needs across an estimate measured in
 * minutes, and still cheap: one redraw of one node, and nothing recomposed.
 */
private const val ProgressTickMillis = 1_000L

/** What the session's square holds at the largest system font scale. */
private const val SessionTitleMaxLines = 4

/** The shape stops growing here, so a wide window gets a shape, not a wall. */
private val SessionShapeMaxSize = 320.dp

/** Grown into, not popped into: the session starts a little under full size. */
private const val SessionEnterScale = 0.85f

/** And collapses back toward the task it came from. */
private const val SessionExitScale = 0.92f

/**
 * A fixed timestamp for the sample fixture, so previews stay deterministic
 * rather than shifting with the clock.
 */
private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private val SampleTask = Task(
    id = "sample-focus",
    title = "Review the quarterly budget",
    createdAt = SampleTimestamp,
    placement = TaskPlacement.INBOX,
    scheduledDate = LocalDate.of(2026, 1, 1),
    estimatedDurationMinutes = 45
)

/** Half an hour into a forty-five minute estimate, so the preview shows a morph mid-way. */
private val PreviewSessionStart: Instant = Instant.now().minusSeconds(30 * 60)

private val SampleNextTask = Task(
    id = "sample-focus-next",
    title = "Call the plumber about the leak",
    createdAt = SampleTimestamp,
    placement = TaskPlacement.INBOX,
    scheduledDate = LocalDate.of(2026, 1, 1),
    estimatedDurationMinutes = 15
)

@Preview(name = "Ready", showBackground = true)
@Preview(name = "Ready dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusReadyPreview() {
    FocuslistTheme {
        FocusContent(
            task = SampleTask,
            nextTask = SampleNextTask,
            startedAt = null,
            onStart = {},
            onStop = {},
            onComplete = {}
        )
    }
}

@Preview(name = "Session", showBackground = true)
@Preview(name = "Session dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusSessionPreview() {
    FocuslistTheme {
        FocusContent(
            task = SampleTask,
            nextTask = SampleNextTask,
            startedAt = PreviewSessionStart,
            onStart = {},
            onStop = {},
            onComplete = {}
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun FocusEmptyPreview() {
    FocuslistTheme {
        FocusContent(
            task = null,
            nextTask = null,
            startedAt = null,
            onStart = {},
            onStop = {},
            onComplete = {}
        )
    }
}
