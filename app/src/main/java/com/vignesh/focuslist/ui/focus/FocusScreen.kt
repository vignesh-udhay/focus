package com.vignesh.focuslist.ui.focus

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
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
 * There is no top app bar in either state. Every other screen is a list and
 * wears its name; this one shows a single task, the navigation bar already
 * says which destination it is, and a heading reading "Focus" above the task
 * would be the screen naming itself instead of naming the work.
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
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = { if (!isSessionActive) bottomBar() }
    ) { innerPadding ->
        // The transition is decoration in the strict sense and a user who
        // asked for no motion gets the next state directly. The shape inside
        // the session is not covered by this: how far the session has run is
        // information, and holding it still would withhold an answer.
        val animate = focuslistMotionEnabled()

        // Read here rather than inside transitionSpec, which is not a
        // composable scope. An effects spec, because a fade changes no bounds.
        val fadeSpec = FocuslistMotion.stateColor<Float>()

        // The bottom inset is deliberately not applied here, and this is the
        // whole of what keeps the task still. Session takes the navigation bar
        // away, so the Scaffold hands back a content area that is suddenly a
        // bar taller; anything centred in it drops by half a bar the instant
        // Start is pressed. Measured on the emulator, the title fell 42dp.
        //
        // Centring against a region that ignores the bottom therefore gives
        // both states the same middle. The footer is the only thing that
        // actually needs the inset, and it is given it directly.
        val layoutDirection = LocalLayoutDirection.current
        val bottomInset = innerPadding.calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding()
                )
        ) {
            AnimatedContent(
                targetState = task,
                contentKey = { it == null },
                transitionSpec = {
                    if (!animate) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        // A fade, on the effects spec, because nothing here
                        // changes bounds. The beat that makes an emptied queue
                        // feel like an ending comes from the shape unwinding
                        // into the button first, which is spatial and is
                        // animated where it happens.
                        fadeIn(animationSpec = fadeSpec) togetherWith
                            fadeOut(animationSpec = fadeSpec)
                    }
                },
                label = "focus content"
            ) { current ->
                if (current == null) {
                    // One state for both ways the queue empties: nothing
                    // scheduled, and everything scheduled already done. A
                    // separate "all done" would be a celebration.
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
                        bottomInset = bottomInset,
                        onStart = onStart,
                        onStop = onStop,
                        onComplete = { onComplete(current.id) }
                    )
                }
            }
        }
    }
}

/**
 * One task, in whichever of the two states it is in.
 *
 * Both states are one layout rather than two, because the movement between
 * them is the design. Nothing is composed only in Ready or only in Session and
 * cross-faded against its opposite number; the shape, the title and the action
 * slot are each a single element that changes.
 *
 * What that buys is that the title never moves. The shape grows up from the
 * button and around words that were already there, which is what makes the
 * session feel like the same screen rather than a new one.
 */
@Composable
private fun FocusTask(
    task: Task,
    nextTask: Task?,
    startedAt: Instant?,
    animate: Boolean,
    bottomInset: Dp,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onComplete: () -> Unit
) {
    val isSessionActive = startedAt != null

    if (isSessionActive) {
        TrackSessionVisibility()
    }

    // How far the button has become the shape. Spatial, because bounds are
    // exactly what change, and the slow spec because this is the user settling
    // into a task rather than passing through.
    //
    // An `Animatable` rather than `animateFloatAsState`, because one thing on
    // this screen needs to know not just where the transform is but whether it
    // has finished: the permission prompt waits for it.
    val expansionSpec = FocuslistMotion.focusSession<Float>()
    val expansionAnim = remember { Animatable(if (isSessionActive) 1f else 0f) }

    LaunchedEffect(isSessionActive, animate) {
        val target = if (isSessionActive) 1f else 0f
        if (animate) expansionAnim.animateTo(target, expansionSpec)
        else expansionAnim.snapTo(target)
    }

    val expansion = expansionAnim.value

    // Asked once the shape has arrived, never while it is still travelling.
    // The permission dialog is a system window and it opens over whatever is
    // on screen, so requesting it as the session composes meant the very first
    // Start a user ever pressed had its transform covered by a prompt. Nobody
    // saw the animation on the run that matters most.
    AskToNotifyOnce(
        hasEstimate = task.estimatedDurationMinutes != null,
        enabled = isSessionActive && !expansionAnim.isRunning
    )

    // Where the session has got to. Not a transition, and so not governed by
    // the reduced-motion setting: it is a value derived from the clock.
    val shapeProgress = rememberShapeProgress(
        startedAt = startedAt,
        estimatedDurationMinutes = task.estimatedDurationMinutes,
        animate = animate
    )

    // Read off the expansion rather than animated separately, which is the
    // one case where a colour gets no spec of its own. Given one it would need
    // an effects spec, and an effects spec settles at stiffness 1600 while the
    // bounds are still travelling at 200: the container turned pale while it
    // was visibly still the button, and read as two things rather than one.
    //
    // Making the colour a function of how far the shape has grown means there
    // is only one animation here, so the two cannot come apart. The fraction
    // is clamped where the rectangle is not, because a colour extrapolated
    // past its endpoint by the spring's overshoot is not a colour anyone
    // chose.
    val shade = expansion.coerceIn(0f, 1f)
    val containerColor = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        shade
    )
    val titleColor = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary,
        shade
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // The way out. An on-screen control and not only a gesture, because
        // gesture navigation draws no visible back affordance and this state
        // has hidden the one control the user knows about.
        AnimatedVisibility(
            visible = isSessionActive,
            enter = if (animate) fadeIn(FocuslistMotion.stateColor()) else EnterTransition.None,
            exit = if (animate) fadeOut(FocuslistMotion.stateColor()) else ExitTransition.None,
            modifier = Modifier.align(Alignment.TopStart).padding(FocuslistSpacing.xs)
        ) {
            IconButton(onClick = onStop) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.focus_stop)
                )
            }
        }

        FocusShape(
            task = task,
            expansion = { expansion },
            progress = { shapeProgress.value },
            containerColor = containerColor,
            titleColor = titleColor,
            isSessionActive = isSessionActive,
            onStart = onStart,
            onComplete = onComplete,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = focuslistContentGutter())
                .padding(horizontal = FocuslistSpacing.lg)
        )

        // The foot of the screen is only ever the peek at what follows. It
        // used to double as Ready's Complete shortcut, which gave one position
        // two grammars: an action in one state and unreadable information in
        // the other, ninety density pixels from anything it related to.
        FocusFooter(
            nextTask = nextTask,
            expansion = expansion,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = FocuslistSpacing.lg)
                .padding(bottom = bottomInset + FocuslistSpacing.lg)
        )
    }
}

/**
 * The container that is the button in one state and the session in the other,
 * with the task inside it and the action slot beneath.
 *
 * The container is drawn rather than clipped to. A [androidx.compose.ui.graphics.Shape]
 * would have to be a new object on every frame to change, which puts the work
 * in layout; drawing reads the two animated values in the draw phase, where a
 * changed value costs one redraw of one node and nothing is remeasured.
 *
 * That matters more for the session than for the transition. The transition is
 * over in under a second, but the shape carries on advancing for as long as the
 * task's estimate lasts, and a session left running is not paying to relayout
 * a screen once a second.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusShape(
    task: Task,
    expansion: () -> Float,
    progress: () -> Float,
    containerColor: Color,
    titleColor: Color,
    isSessionActive: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The button growing into the circle is a rounded rectangle whose corners
    // stay at half its height, which is a stadium the whole way up and a circle
    // the moment the box is square. That is how every Material container
    // transform is built, and both rings begin at that circle, so the growth
    // hands straight over with nothing in between.
    //
    // `MaterialShapes.Pill` is the wrong tool for it: the shapes are normalised
    // into a unit box, so stretching one back out to a wide, short rectangle
    // gives an ellipse rather than a stadium.
    //
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

    // Everything that appears and disappears here is driven off the container's
    // own travel rather than given an animation of its own. A cross-fade on an
    // effects spec settles eight times stiffer than the container moves, so the
    // labels finished swapping while the container was barely underway: "Start"
    // was left drawn on the page it had been lifted off, in the `onPrimary` its
    // vanished container called for, which in a light theme is white on white.
    val shade = expansion().coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.wrapContentSize()) {
        // Capped, so a tablet gets a shape and not a billboard, and bounded by
        // the window so a narrow phone is not overflowed.
        val side = min(maxWidth, SessionShapeMaxSize)

        // The shortcut sits outside the drawn box on purpose. Inside it, it
        // displaced the action slot upward while the container's own rectangle
        // went on being computed as the bottom of the box, so the two came
        // apart and every label was left drawn on the wrong background.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(side)
                    .height(side + FocuslistSpacing.lg + ActionSlotHeight)
                    .drawBehind {
                        val ready = Rect(
                            left = (size.width - ActionSlotWidth.toPx()) / 2f,
                            top = size.height - ActionSlotHeight.toPx(),
                            right = (size.width + ActionSlotWidth.toPx()) / 2f,
                            bottom = size.height
                        )
                        val session = Rect(0f, 0f, size.width, size.width)

                        drawFocusContainer(
                            expansion = expansion(),
                            phase = progress(),
                            determinate = determinate,
                            ringMorphs = ringMorphs,
                            path = path,
                            color = containerColor,
                            ready = ready,
                            session = session
                        )
                    }
            ) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).size(side),
                    contentAlignment = Alignment.Center
                ) {
                    FocusTaskTitle(
                        task = task,
                        color = titleColor,
                        modifier = Modifier.padding(FocuslistSpacing.lg)
                    )
                }

                // Start becomes the shape, so what sits in this slot afterwards
                // is the action the session is for. The slot itself never moves,
                // and it is exactly the rectangle the container starts from.
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    if (leavingAlpha(shade) > 0f) {
                        FocusAction(
                            label = stringResource(R.string.focus_start),
                            onClick = onStart,
                            alpha = leavingAlpha(shade),
                            // Transparent, because the fill under this button is
                            // the drawn container: it is the thing that will grow
                            // away, and a second container painted on top of it
                            // would stay behind and give the trick away.
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }

                    if (arrivingAlpha(shade) > 0f) {
                        FocusAction(
                            label = stringResource(R.string.focus_complete),
                            onClick = onComplete,
                            alpha = arrivingAlpha(shade),
                            // The session draws its own container here. The shape
                            // has taken the drawn one up the screen with it.
                            colors = ButtonDefaults.buttonColors()
                        )
                    }
                }
            }

            // Ready's shortcut, directly under the control it qualifies. Always
            // composed rather than swapped in and out, so the height it occupies
            // is the same at every font scale and nothing above it shifts when the
            // session takes it away.
            TextButton(
                onClick = { if (!isSessionActive) onComplete() },
                modifier = Modifier
                    .padding(top = FocuslistSpacing.xs)
                    .alpha(leavingAlpha(shade))
                    .then(
                        if (isSessionActive) Modifier.clearAndSetSemantics {} else Modifier
                    )
            ) {
                Text(stringResource(R.string.focus_complete))
            }
        }
    }
}

/**
 * Material's fade-through, expressed against the container's travel.
 *
 * The outgoing label is gone before the incoming one appears, rather than the
 * two of them sitting on top of each other at half opacity each, which is what
 * a cross-fade does and which read as neither word. Material splits the two
 * halves at thirty percent, and thirty percent of this particular transform is
 * also about as far as the container can move while still covering the slot it
 * started in, so the label never outlives its own background.
 */
private fun leavingAlpha(fraction: Float): Float =
    (1f - fraction / FadeThroughSplit).coerceIn(0f, 1f)

private fun arrivingAlpha(fraction: Float): Float =
    ((fraction - FadeThroughSplit) / (1f - FadeThroughSplit)).coerceIn(0f, 1f)

private const val FadeThroughSplit = 0.3f

/**
 * The one control in the action slot, at Material's medium button size.
 *
 * A real [Button] rather than a drawn one with a click listener, so that the
 * ripple, the state layers, the focus indication and the button role all come
 * from Material rather than being approximated here.
 */
@Composable
private fun FocusAction(
    label: String,
    onClick: () -> Unit,
    alpha: Float,
    colors: androidx.compose.material3.ButtonColors
) {
    Button(
        onClick = onClick,
        colors = colors,
        shape = CircleShape,
        contentPadding = ButtonDefaults.contentPaddingFor(ActionSlotHeight),
        modifier = Modifier
            .size(width = ActionSlotWidth, height = ActionSlotHeight)
            .alpha(alpha)
    ) {
        Text(text = label, style = ButtonDefaults.textStyleFor(ActionSlotHeight))
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
    expansion: Float,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(arrivingAlpha(expansion.coerceIn(0f, 1f)))
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
 * Draws the container at wherever it currently is between the button and the
 * session.
 *
 * Two ways of drawing one container, and they meet at a circle. On the way up
 * it is a rounded rectangle cornered at half its own height, which is a stadium
 * while the box is wide and a circle the instant the box is square. Once it is
 * square the session's ring takes over, and every ring starts at that same
 * circle, so the handover cannot be seen.
 *
 * The rectangle is interpolated without clamping, so the spring's overshoot
 * carries into the size. That overshoot is the expressive part of the
 * expressive motion scheme and there is no reason to throw it away here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DrawScope.drawFocusContainer(
    expansion: Float,
    phase: Float,
    determinate: Boolean,
    ringMorphs: List<Morph>,
    path: Path,
    color: Color,
    ready: Rect,
    session: Rect
) {
    val rect = Rect(
        left = lerp(ready.left, session.left, expansion),
        top = lerp(ready.top, session.top, expansion),
        right = lerp(ready.right, session.right, expansion),
        bottom = lerp(ready.bottom, session.bottom, expansion)
    )

    if (expansion < 1f) {
        drawRoundRect(
            color = color,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(rect.height / 2f)
        )
        return
    }

    // One segment per pair of neighbours. The determinate ring is walked once
    // and stops at its last shape; the indeterminate one wraps, and its final
    // segment morphs back into the shape it began at, so the seam cannot be
    // seen.
    val segments = if (determinate) ringMorphs.size - 1 else ringMorphs.size
    val walked = (phase.coerceIn(0f, 1f) * segments).coerceIn(0f, segments.toFloat())
    val index = floor(walked).toInt().coerceIn(0, segments - 1)
    ringMorphs[index].toPath(progress = walked - index, path = path)

    // The polygons are normalised, so the path arrives in a unit box and has
    // to be scaled to the rectangle and recentred on it, exactly as the
    // Material shape helper does it.
    path.transform(Matrix().apply { scale(x = rect.width, y = rect.height) })
    path.translate(rect.center - path.getBounds().center)
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
 * Half the content row less the gutter between two of them, which is the width
 * the design draws its buttons at. A layout measurement rather than a Material
 * token, because Material has no opinion on how wide a button should be.
 */
private val ActionSlotWidth = 176.dp

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
