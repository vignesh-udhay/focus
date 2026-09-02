package com.vignesh.focuslist.core.design

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * What Focuslist animates, named by what it means rather than by how it moves.
 *
 * A spring is a design decision, and design decisions belong in one place. A
 * composable that writes its own `spring` or `tween` is deciding how the
 * product feels from inside a file about laying out a row, so nothing outside
 * this object should name an animation spec.
 *
 * Every token resolves through `MaterialTheme.motionScheme`, and the app runs
 * on the expressive scheme already: `MaterialExpressiveTheme` defaults it to
 * `MotionScheme.expressive()` and `FocuslistTheme` does not override it.
 *
 * Material splits its specs two ways and the split is the part worth getting
 * right. Spatial specs are for animations that change a component's shape or
 * bounds; effects specs are for animations that do not, such as colour. The
 * two schemes differ *only* in their spatial specs, so choosing the expressive
 * scheme does nothing whatsoever for a colour animation. If it moves, it is
 * spatial. If it merely changes appearance, it is an effect.
 *
 * These are functions rather than properties because Kotlin allows type
 * parameters on extension properties only, and the specs are generic in what
 * they animate.
 */
object FocuslistMotion {

    /**
     * Completing a task.
     *
     * The one place the app is allowed to be lively, and the only token backed
     * by a fast spatial spec: at a damping ratio of 0.6 it visibly overshoots,
     * and that overshoot is the whole expressive gesture.
     *
     * `PRODUCT.md` forbids streaks, points, badges and celebrations, and says
     * the reward is getting the work done. A spring on the completion control
     * acknowledges the work without any of that. It is a physical response to
     * a tap, not a prize, and nothing else in the app is allowed to feel this
     * way.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> completion(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.fastSpatialSpec()

    /**
     * Colour or alpha changing to show a state change.
     *
     * An effects spec, because none of it moves anything.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> stateColor(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.defaultEffectsSpec()

    /**
     * A task entering a list, leaving it, or moving within it.
     *
     * Spatial, because position is exactly what changes, and deliberately the
     * default rather than the fast spec: a list of twelve rows springing every
     * time one is ticked is noise rather than delight.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> listChange(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Something appearing or expanding. Spatial, since bounds change. */
    @Composable
    @ReadOnlyComposable
    fun <T> reveal(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.defaultSpatialSpec()

    /**
     * Entering or leaving the Focus session.
     *
     * `PRODUCT.md` names focus mode transitions as motion that earns its
     * place, and this is the only one: the screen changes what it is for, the
     * navigation goes away, and the task grows into the space. Spatial,
     * because bounds are exactly what change.
     *
     * The slow spec rather than the default. Every other transition in the app
     * is something the user is passing through, and should get out of the way;
     * this one is the user settling into a task, and taking a beat over it is
     * the difference between a mode and a flicker.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> focusSession(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.slowSpatialSpec()
}

/**
 * Whether the system wants to be shown motion at all.
 *
 * Turning off "Remove animations" in accessibility sets the animator duration
 * scale to zero, and it is the one signal Android gives for the request. A
 * user who has asked for stillness has usually asked because motion makes them
 * unwell, so this is a requirement rather than a preference to weigh.
 *
 * What it governs is transitions, not information. A screen sliding into place
 * is decoration and should simply not happen; a shape that shows how far a
 * session has run is telling the user something, and freezing it would answer
 * the request by withholding the answer. Callers are expected to know which of
 * the two they have.
 *
 * Observed rather than read once, because the setting can be changed while the
 * app is open and nothing restarts the process when it is.
 */
@Composable
fun focuslistMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) { mutableStateOf(animatorsRun(resolver)) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = animatorsRun(resolver)
            }
        }

        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )

        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return enabled
}

/** Zero means the system has been asked to run no animations at all. */
private fun animatorsRun(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
