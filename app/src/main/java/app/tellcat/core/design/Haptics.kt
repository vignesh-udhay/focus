package app.tellcat.core.design

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Tellcat's touch feedback, and the only place a feedback type is chosen.
 *
 * `expressive-motion.md` lists every interaction that answers physically, and
 * this object is that table in code, the way [TellcatMotion] is the motion
 * table: a screen asks for the interaction by name and never picks a
 * [HapticFeedbackType] itself, so the list cannot grow one helpful buzz at a
 * time.
 *
 * Everything goes through [HapticFeedback.performHapticFeedback], never a
 * direct vibration, so the system plays it only when the user has touch
 * feedback on, at the strength the device gives each type.
 */
object TellcatHaptics {

    /**
     * Completing a task, or reopening one.
     *
     * `Confirm` on completion, a step firmer than a toggle, because completion
     * is the strongest interaction in the app and a checklist tick must not
     * answer more firmly than finishing the whole task. Reopening is a toggle
     * off: a reversal, not an achievement.
     */
    fun taskCompletion(haptics: HapticFeedback, completed: Boolean) {
        haptics.performHapticFeedback(
            if (completed) HapticFeedbackType.Confirm else HapticFeedbackType.ToggleOff
        )
    }

    /** Ticking or unticking a checklist item: a light toggle either way. */
    fun checklistTick(haptics: HapticFeedback, done: Boolean) {
        haptics.performHapticFeedback(
            if (done) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
        )
    }
}
