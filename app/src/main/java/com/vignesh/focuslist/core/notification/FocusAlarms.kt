package com.vignesh.focuslist.core.notification

import java.time.Instant

/**
 * The one thing Focuslist asks the system to tell the user about.
 *
 * A focus session's estimate running out while the user is somewhere else. The
 * shape on the Focus screen already says it to anyone looking; this is how it
 * reaches someone who is not.
 *
 * An interface, on the same terms as `CurrentDay`: the view model decides when
 * a session should be announced, and does not know that alarms or
 * notifications exist. That keeps the decision testable without a device.
 *
 * At most one alarm is outstanding at a time. There is one session, and it is
 * working on one task.
 */
interface FocusAlarms {

    /**
     * Announce [taskTitle] at [at], replacing whatever was scheduled before.
     *
     * Replacing rather than adding: completing a task inside a session moves
     * the work on to the next one, and the estimate that matters is the new
     * one. Two alarms would mean two announcements, one of them about a task
     * the user has already finished.
     */
    fun scheduleEstimateReached(taskTitle: String, at: Instant)

    /** Nothing is running, or nothing running has an estimate to reach. */
    fun cancel()
}

/**
 * Whether a focus session is on screen right now.
 *
 * Process-scoped and deliberately not a flow: the only reader is the alarm
 * receiver, which asks once, at the moment it fires.
 *
 * A session the user is looking at needs no notification. The shape has been
 * telling them where the estimate stands the whole time, and interrupting the
 * screen whose entire job is to protect attention would be the app working
 * against itself.
 *
 * False after the process dies, which is the correct answer: nothing is on
 * screen, and that is exactly when the notification earns its place.
 */
object FocusSessionVisibility {

    @Volatile
    var isSessionOnScreen: Boolean = false
}
