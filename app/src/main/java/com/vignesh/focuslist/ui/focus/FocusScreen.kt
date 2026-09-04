package com.vignesh.focuslist.ui.focus

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.design.focuslistMotionEnabled
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.focusElapsedPhase
import com.vignesh.focuslist.core.domain.focusProgress
import com.vignesh.focuslist.core.notification.FocusSessionVisibility
import com.vignesh.focuslist.core.notification.canPostNotifications
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import kotlinx.coroutines.delay
import kotlin.math.floor
import java.time.Instant
import java.time.LocalDate
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Focus, the execution mode.
 *
 * Two states over one destination, and one continuous piece of motion between
 * them. Ready is a place: the task, and a control to begin. Session is a mode:
 * the navigation goes, and what is left is the task, the action that finishes
 * it, and a quiet line saying what follows.
 *
 * The control the user presses is the thing that becomes the session. Start is
 * a pill; the session is a shape; pressing Start grows the one into the other.
 * That is Material's container transform, and taking it here rather than the
 * scale-and-fade that used to be here is also a correction: M3 says Android
 * avoids scale on enter and exit because it implies an elevation change the
 * system does not have.
 *
 * The queue behind it is unchanged. Completing a task takes it out, the next
 * appears without an advance step, and when none are left the session ends on
 * its own rather than stranding the user in a screen with no navigation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusSheet(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier
) {
    val task by viewModel.focusedTask.collectAsStateWithLifecycle()
    val queue by viewModel.focusQueue.collectAsStateWithLifecycle()
    val startedAt by viewModel.focusSessionStartedAt.collectAsStateWithLifecycle()

    // The sheet carries its own, because the screen it opened over is behind a
    // scrim and a bar shown down there would be invisible. Both effects watch
    // the one app-wide offer, so the copy still running underneath cannot
    // disagree with this one: whichever resolves first clears `pendingUndo`,
    // and the other is cancelled by the same change.
    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    // What follows the one being worked on. Read from the same queue Focus
    // draws from, so it cannot disagree with what appears next.
    val nextTask = remember(queue, task) {
        val index = queue.indexOfFirst { it.id == task?.id }
        queue.getOrNull(index + 1).takeIf { index >= 0 }
    }

    ModalBottomSheet(
        // Dismissing is stopping. There is no way to leave the sheet and keep a
        // session running, which is deliberate: a session with nothing on
        // screen pointing at it would be state the user cannot see or reach.
        // Backgrounding the app is a different thing and does not stop it,
        // which is what the estimate notification exists for.
        onDismissRequest = viewModel::stopFocusSession,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            // Full height or gone. A half-open Focus would be a list of one
            // task peeking over the screen it was trying to replace.
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        modifier = modifier
    ) {
        FocusSheetContent(
            task = task,
            nextTask = nextTask,
            startedAt = startedAt,
            // The same write every list makes, so finishing a task here is as
            // undoable as finishing it anywhere else. It is also what advances
            // the queue: completing takes the task out, the chosen id stops
            // matching, and `focusedTask` falls through to the next one.
            onComplete = { id -> viewModel.toggleComplete(id) },
            snackbarHostState = snackbarHostState
        )
    }
}

/**
 * What the sheet holds: one task, or the fact that there are none left.
 *
 * Stateless. It renders the task it is handed and reports completing.
 *
 * There is no top app bar and no name anywhere on it. A sheet the user opened
 * by choosing a task does not need to introduce itself, and a heading reading
 * "Focus" would be the screen naming itself instead of naming the work.
 */
@Composable
private fun FocusSheetContent(
    task: Task?,
    nextTask: Task?,
    startedAt: Instant?,
    onComplete: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    // The transition between having a task and having none is decoration in
    // the strict sense, and a user who asked for no motion gets the next state
    // directly. The shape inside the session is not covered by this: how far
    // the session has run is information, and holding it still would withhold
    // an answer.
    val animate = focuslistMotionEnabled()

    // Read here rather than inside transitionSpec, which is not a composable
    // scope. An effects spec, because a fade changes no bounds.
    val fadeSpec = FocuslistMotion.stateColor<Float>()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = task,
            contentKey = { it == null },
            transitionSpec = {
                if (!animate) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(animationSpec = fadeSpec) togetherWith
                        fadeOut(animationSpec = fadeSpec)
                }
            },
            label = "focus content"
        ) { current ->
            if (current == null) {
                // One state for both ways the queue empties: nothing scheduled,
                // and everything scheduled already done. A separate "all done"
                // would be a celebration.
                //
                // The sheet stays open on it rather than closing itself. The
                // queue emptying is the moment the user most deserves to be
                // told, and a sheet that vanished would have answered by
                // disappearing.
                TaskListEmptyState(
                    headline = stringResource(R.string.focus_empty_headline),
                    supporting = stringResource(R.string.focus_empty_supporting)
                )
            } else {
                FocusTask(
                    task = current,
                    nextTask = nextTask,
                    startedAt = startedAt,
                    animate = animate,
                    onComplete = { onComplete(current.id) }
                )
            }
        }

        UndoSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * One task, being worked on.
 *
 * There is only one state now. The sheet is entered by choosing a task, so it
 * opens already running, and the Ready half this screen used to carry — a task
 * sitting still with a control to begin — has no reason to exist. What used to
 * justify it was hiding the navigation bar honestly; a sheet leaves the bar
 * where it is, behind the scrim, and needs no justifying.
 */
@Composable
private fun FocusTask(
    task: Task,
    nextTask: Task?,
    startedAt: Instant?,
    animate: Boolean,
    onComplete: () -> Unit
) {
    if (startedAt != null) {
        TrackSessionVisibility()
    }

    // Asked once the sheet has arrived rather than as it composes. The
    // permission dialog is a system window and opens over whatever is on
    // screen, so requesting it on the way in put a prompt over the sheet's own
    // entrance on the very first session a user ever ran.
    AskToNotifyOnce(
        hasEstimate = task.estimatedDurationMinutes != null,
        enabled = startedAt != null
    )

    // Where the session has got to. Not a transition, and so not governed by
    // the reduced-motion setting: it is a value derived from the clock.
    val shapeProgress = rememberShapeProgress(
        startedAt = startedAt,
        estimatedDurationMinutes = task.estimatedDurationMinutes,
        animate = animate
    )

    Box(modifier = Modifier.fillMaxSize()) {
        FocusShape(
            task = task,
            progress = { shapeProgress.value },
            onComplete = onComplete,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = focuslistContentGutter())
                .padding(horizontal = FocuslistSpacing.lg)
        )

        FocusFooter(
            nextTask = nextTask,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.lg)
                .padding(bottom = FocuslistSpacing.xxl)
        )
    }
}

/**
 * The shape, the task inside it, and the action beneath.
 *
 * The shape is drawn rather than clipped to. A [androidx.compose.ui.graphics.Shape]
 * would have to be a new object on every frame to change, which puts the work
 * in layout; drawing reads the animated value in the draw phase, where a
 * changed value costs one redraw of one node and nothing is remeasured. That
 * matters because the shape carries on advancing for as long as the task's
 * estimate lasts, and a session left running is not paying to relayout a
 * screen once a second.
 *
 * It used to be a container transform as well, growing out of a button. The
 * sheet's own entrance replaced that: one motion instead of two, and the shape
 * is simply there when the sheet arrives.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusShape(
    task: Task,
    progress: () -> Float,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keyed on whether there is an estimate rather than on the list itself. The
    // list is rebuilt on every recomposition and only compares equal because
    // `MaterialShapes` memoises its polygons; keying on the thing that actually
    // varies does not depend on that.
    val determinate = task.estimatedDurationMinutes != null
    val shapes = sessionShapes(determinate)
    val ringMorphs = remember(determinate) {
        shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) }
    }
    val path = remember { Path() }

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val titleColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier.wrapContentSize()) {
        // Capped, so a tablet gets a shape and not a billboard, and bounded by
        // the window so a narrow phone is not overflowed.
        val side = min(maxWidth, SessionShapeMaxSize)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(side)
                    .drawBehind {
                        drawFocusShape(
                            phase = progress(),
                            determinate = determinate,
                            ringMorphs = ringMorphs,
                            path = path,
                            color = containerColor,
                            bounds = Rect(0f, 0f, size.width, size.height)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                FocusTaskTitle(
                    task = task,
                    color = titleColor,
                    modifier = Modifier.padding(FocuslistSpacing.lg)
                )
            }

            Spacer(Modifier.height(FocuslistSpacing.lg))

            FocusCompleteButton(onClick = onComplete)
        }
    }
}

/**
 * The action the session is for.
 *
 * A real [Button] rather than a drawn one with a click listener, so the ripple,
 * the state layers, the focus indication and the button role all come from
 * Material rather than being approximated here.
 *
 * It used to travel, stretching from beside a play button into the middle as
 * the session opened, and to change tone on the way. Both belonged to a Ready
 * state that no longer exists: the sheet opens already running, so there is one
 * action here and it has always been in the middle. What survives from that
 * work is the corner and the pressed morph.
 */
@Composable
private fun FocusCompleteButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(shape = ActionShape),
        contentPadding = ButtonDefaults.contentPaddingFor(ActionSlotHeight),
        // A floor rather than a fixed height. Pinned at exactly the medium
        // height, a label at 200% font scale was cut through the middle of its
        // letters; the button is allowed to grow to hold its own text.
        modifier = Modifier.heightIn(min = ActionSlotHeight)
    ) {
        Text(
            text = stringResource(R.string.focus_complete),
            style = ButtonDefaults.textStyleFor(ActionSlotHeight),
            maxLines = 1
        )
    }
}

/**
 * The line at the foot of the screen: what follows the task being worked on.
 *
 * A peek and not a picker. It cannot be tapped, scrolled or chosen from,
 * because deciding belongs to Today and a control that let the user swap tasks
 * here would import the deciding back into the mode.
 *
 * It appears on the container's travel rather than on a fade of its own, for
 * the same reason the labels do, and it keeps its space when nothing follows so
 * that the last task of a session does not move the screen.
 */
@Composable
private fun FocusFooter(
    nextTask: Task?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The ring of shapes a session walks, and which way round it walks it.
 *
 * A task with an estimate gets two shapes and runs them once, from the busy one
 * to the circle: the task is at its most complicated when it starts and
 * resolves as the estimate is used up. A task without one gets a ring of six
 * and walks round it forever, arriving nowhere.
 *
 * This is Material's own distinction rather than a private vocabulary. Its
 * loading indicator ships two shape lists, a pair for the determinate case and
 * a sequence of seven for the indeterminate one, and encodes known against
 * unknown duration by the *kind* of motion rather than by what any one shape
 * means. That matters, because the shape principles say in as many words that
 * shape is versatile and not semantic: no single form here stands for
 * anything, and swapping the ring for another set of shapes would change how
 * the screen looks and nothing about what it says.
 *
 * Both rings deliberately begin at the circle, which is the shape the
 * container transform ends at, so a session continues straight out of the
 * growth with nothing in between. None of these are the elongated shapes:
 * `Pill` and `Oval` normalise into a unit box, so a square draw would stretch
 * them back into ovals.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun sessionShapes(hasEstimate: Boolean): List<RoundedPolygon> =
    if (hasEstimate) {
        listOf(MaterialShapes.Circle, MaterialShapes.Clover8Leaf)
    } else {
        listOf(
            MaterialShapes.Circle,
            MaterialShapes.Square,
            MaterialShapes.Cookie4Sided,
            MaterialShapes.Pentagon,
            MaterialShapes.SoftBurst,
            MaterialShapes.Gem
        )
    }

/**
 * Draws the shape at whatever point of its ring the session has reached.
 *
 * There is one way of drawing it now. It used to be two, meeting at a circle,
 * because the shape had to grow out of a button first; the sheet's own entrance
 * does that job, so the rounded-rectangle half and the interpolation it needed
 * are gone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DrawScope.drawFocusShape(
    phase: Float,
    determinate: Boolean,
    ringMorphs: List<Morph>,
    path: Path,
    color: Color,
    bounds: Rect
) {
    // One segment per pair of neighbours. The determinate ring is walked once
    // and stops at its last shape; the indeterminate one wraps, and its final
    // segment morphs back into the shape it began at, so the seam cannot be
    // seen.
    val segments = if (determinate) ringMorphs.size - 1 else ringMorphs.size
    val walked = (phase.coerceIn(0f, 1f) * segments).coerceIn(0f, segments.toFloat())
    val index = floor(walked).toInt().coerceIn(0, segments - 1)
    ringMorphs[index].toPath(progress = walked - index, path = path)

    // The polygons are normalised, so the path arrives in a unit box and has to
    // be scaled to the rectangle and recentred on it, exactly as the Material
    // shape helper does it.
    path.transform(Matrix().apply { scale(x = bounds.width, y = bounds.height) })
    path.translate(bounds.center - path.getBounds().center)
    drawPath(path, color)
}

/**
 * Where the shape stands, as a value that can be sprung to rather than only
 * jumped to.
 *
 * An [Animatable] rather than a plain state, because the shape has to move on
 * its own in two places the clock does not account for. A session that ends
 * unwinds to the circle instead of snapping, so what shrinks back into the
 * button is the shape that grew out of it. And a session that moves on to the
 * next task springs back to the circle, which is the reset the user sees when
 * one task gives way to another: the clock has genuinely restarted, and this
 * is the shape saying so.
 *
 * Reading `value` inside a draw block records the read in the draw phase, so a
 * new value costs one redraw and nothing is recomposed or remeasured.
 *
 * Sampled, not accumulated: each tick asks the clock what time it is and works
 * the value out again, so a session frozen while the user was in another app
 * comes back where it actually is rather than where it was left.
 */
@Composable
private fun rememberShapeProgress(
    startedAt: Instant?,
    estimatedDurationMinutes: Int?,
    animate: Boolean
): Animatable<Float, AnimationVector1D> {
    val shapeProgress = remember { Animatable(0f) }
    val spec = FocuslistMotion.focusSession<Float>()

    LaunchedEffect(startedAt, estimatedDurationMinutes, animate) {
        if (startedAt == null) {
            if (animate) shapeProgress.animateTo(0f, spec) else shapeProgress.snapTo(0f)
            return@LaunchedEffect
        }

        val first = shapeValue(startedAt, estimatedDurationMinutes)
        if (animate) shapeProgress.animateTo(first, spec) else shapeProgress.snapTo(first)

        while (true) {
            delay(ProgressTickMillis)
            val current = shapeValue(startedAt, estimatedDurationMinutes)
            shapeProgress.snapTo(current)
            // An estimate that is used up has nothing left to say, so the
            // ticking stops. A session with no estimate never arrives
            // anywhere, so it keeps going for as long as the session does.
            if (estimatedDurationMinutes != null && current >= 1f) return@LaunchedEffect
        }
    }

    return shapeProgress
}

/**
 * What the shape should read, from whichever of the two clocks applies.
 *
 * With an estimate the value is a fraction of it, and arriving means the
 * estimate is used up. Without one there is nothing to be a fraction of, and
 * the value cycles instead: it says the session is running and nothing more.
 * The two are separate functions in the domain because they mean different
 * things, and the screen picking between them here is the whole of the
 * difference.
 */
private fun shapeValue(startedAt: Instant, estimatedDurationMinutes: Int?): Float {
    val now = Instant.now()
    return focusProgress(startedAt, now, estimatedDurationMinutes)
        ?: focusElapsedPhase(startedAt, now)
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
 *
 * [enabled] is what holds it back until the session has actually arrived. The
 * dialog is a system window drawn over everything, and asking as the session
 * composed put it on top of the container transform every time: the first
 * Start a user ever pressed was the one run of the animation they never got to
 * see. Waiting costs nothing, because the moment being announced is minutes
 * away.
 */
@Composable
private fun AskToNotifyOnce(hasEstimate: Boolean, enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { asked = true }

    LaunchedEffect(enabled, hasEstimate, asked) {
        if (enabled && !asked && hasEstimate && !context.canPostNotifications()) {
            asked = true
            request.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * The task, and how long it was reckoned to take.
 *
 * The estimate is shown wherever the task is. Today already carries it, and a
 * screen about doing the work that dropped the one number describing its size
 * would be throwing away what the user already said.
 *
 * Capped at four lines in both states, which is what the shape's square holds
 * at the largest system font scale. The cap applies in Ready too, even though
 * there is no shape there yet: the square is reserved in both states, and a
 * title that overran it in Ready would collide with the Start button and then
 * be cut anyway the moment the session began.
 */
@Composable
private fun FocusTaskTitle(
    task: Task,
    color: Color,
    modifier: Modifier = Modifier
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
            maxLines = TitleMaxLines,
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

/** What the shape's square holds at the largest system font scale. */
private const val TitleMaxLines = 4

/** The shape stops growing here, so a wide window gets a shape, not a wall. */
private val SessionShapeMaxSize = 320.dp

/**
 * Material's medium button, which is the size the action slot is.
 *
 * The design draws it at 84dp, which is not one of Material's five button
 * heights. Medium is the one that survives 200% font scale with room to spare;
 * large, at 96dp, is a fixed box that a label at that scale has to fit inside.
 */
private val ActionSlotHeight = ButtonDefaults.MediumContainerHeight

/**
 * The resting corner of both action buttons.
 *
 * Rounded rather than fully round, which is what tells them apart from the
 * container: play's fill is the circle-to-be and reads as one thing, while the
 * two buttons read as a pair of controls.
 */
private val ActionShape = RoundedCornerShape(20.dp)

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

@Preview(name = "Session", showBackground = true)
@Preview(name = "Session dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusSessionPreview() {
    FocuslistTheme {
        FocusSheetContent(
            task = SampleTask,
            nextTask = SampleNextTask,
            startedAt = PreviewSessionStart,
            onComplete = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

/** The queue emptying while the sheet is open, which is where the user is told. */
@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusEmptyPreview() {
    FocuslistTheme {
        FocusSheetContent(
            task = null,
            nextTask = null,
            startedAt = null,
            onComplete = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}
