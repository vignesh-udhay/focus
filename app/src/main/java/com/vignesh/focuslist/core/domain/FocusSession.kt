package com.vignesh.focuslist.core.domain

import java.time.Duration
import java.time.Instant

/**
 * A session of work on one task.
 *
 * The clock is one moment, the origin the session is measured from, and
 * `focus.md` is emphatic about how it survives a pause: **pausing moves the
 * origin rather than starting a tally**. Resuming shifts the origin forward by
 * however long the pause lasted, so the moment goes on meaning exactly what it
 * meant before and no running total is ever stored.
 *
 * That is what keeps `focus.md`'s central rule intact: **derived, never
 * accumulated**. A session outlives being backgrounded, and a process frozen for
 * ten minutes has to come back knowing ten minutes went by. A counter that only
 * advanced while someone was watching would come back wrong. Nothing here
 * counts; every reading is worked out from the clock at the moment it is asked.
 *
 * [pausedAt] is the second value and it is not a tally either. It is the moment
 * the clock stopped, which is what a paused session reads from instead of the
 * current time.
 *
 * @param startedAt the origin the session is measured from. Not literally "when
 * work began" once a pause has moved it, which is deliberate: every reading
 * below means the same thing before and after.
 * @param pausedAt when the clock stopped, or null while it is running.
 * @param extraMinutes how far the estimate has been extended, by the +5 min the
 * estimate-reached state offers.
 *
 * This one does *not* move the origin, and the reason is a bug that moving it
 * caused. Elapsed time floors at zero, so shifting the origin into the future
 * left `elapsed` clamped at zero rather than negative, and the extension was
 * silently eaten: the estimate alarm was rescheduled to exactly the moment it
 * already pointed at. Caught by `extendingPushesTheAnnouncementOutByFiveMinutes`.
 *
 * `focus.md` specifies the origin moving for a pause and says nothing about the
 * extension, so this is a free choice, and an explicit field is the one that
 * cannot underflow. It is still not written to the task: a task's estimate is
 * the user's answer to how long the work takes, and one session running long is
 * not a correction to it.
 */
data class FocusSession(
    val startedAt: Instant,
    val pausedAt: Instant? = null,
    val extraMinutes: Int = 0
) {

    /** Whether the clock is stopped. */
    val isPaused: Boolean
        get() = pausedAt != null

    /**
     * The moment the session measures against: the pause, or the real now.
     *
     * The one place the two cases differ. Everything below reads through this,
     * so a paused session cannot be made to advance by any route.
     */
    private fun clockNow(now: Instant): Instant = pausedAt ?: now

    /**
     * How long has been worked, at [now].
     *
     * A clock that has gone backwards reports nothing worked rather than a
     * negative duration. That is the same non-answer the progress function gave
     * before it: the session has not started as far as anyone can tell.
     */
    fun elapsed(now: Instant): Duration {
        val worked = Duration.between(startedAt, clockNow(now))
        return if (worked.isNegative) Duration.ZERO else worked
    }

    /**
     * The session with its clock stopped.
     *
     * Pausing an already paused session changes nothing, so the control can be
     * pressed twice without the second press moving the origin.
     */
    fun paused(now: Instant): FocusSession =
        if (isPaused) this else copy(pausedAt = now)

    /**
     * The session with its clock moving again, from [now].
     *
     * The origin moves forward by the length of the pause, which is what makes
     * the time spent paused uncounted without anything having to remember the
     * work already done.
     */
    fun resumed(now: Instant): FocusSession {
        val stoppedAt = pausedAt ?: return this
        val away = Duration.between(stoppedAt, now)

        // A clock that went backwards during the pause must not drag the origin
        // with it, or resuming would hand the user time they never worked.
        return copy(
            startedAt = if (away.isNegative) startedAt else startedAt.plus(away),
            pausedAt = null
        )
    }

    /** The session with [ExtensionMinutes] more to run. */
    fun extended(): FocusSession = copy(extraMinutes = extraMinutes + ExtensionMinutes)

    /**
     * The estimate this session is measured against, extensions included, or
     * null when the task carries none.
     *
     * A task with no estimate stays open-ended however many times anything is
     * pressed: there is nothing to extend, and the estimate-reached state that
     * offers the extension cannot occur.
     */
    fun estimateMinutes(taskEstimateMinutes: Int?): Int? =
        usableEstimate(taskEstimateMinutes)?.plus(extraMinutes)

    /**
     * What is left of the estimate at [now], or null with no estimate.
     *
     * Floored at zero. Running over the estimate is ordinary and says nothing
     * has gone wrong, so a session past it reports nothing left rather than a
     * negative: the state that follows says the estimate was reached, and there
     * is no further thing a bigger overrun could mean.
     */
    fun remaining(now: Instant, estimateMinutes: Int?): Duration? {
        val minutes = estimateMinutes(estimateMinutes) ?: return null
        val left = Duration.ofMinutes(minutes.toLong()) - elapsed(now)

        return if (left.isNegative) Duration.ZERO else left
    }

    /**
     * When the estimate will be reached, or null when nothing will reach one.
     *
     * What the estimate notification is aimed at. A paused session returns null:
     * its clock is not moving, so there is no moment to announce, and an alarm
     * left pointing at one would fire while the user was deliberately not
     * working.
     */
    fun estimateReachedAt(now: Instant, estimateMinutes: Int?): Instant? {
        if (isPaused) return null
        val left = remaining(now, estimateMinutes) ?: return null

        return if (left.isZero) null else now.plus(left)
    }

    companion object {

        /** What one press of +5 min is worth. D-013 names the number. */
        const val ExtensionMinutes = 5
    }
}

/**
 * The part of Focus that must outlive a screen and be readable by the widget.
 *
 * A session without its task id cannot be resumed. Keeping the two in one
 * value prevents the process-death state the old SavedStateHandle path could
 * produce: a live clock with no task to attach it to.
 */
data class StoredFocusSession(
    val taskId: String,
    val session: FocusSession
)

/** Persistence seam for the one active Focus session. */
interface FocusSessionStore {
    val current: StoredFocusSession?

    fun save(value: StoredFocusSession)

    fun clear()
}

/**
 * An estimate that can actually be measured against, or null.
 *
 * Zero and negative are not durations anything can be a fraction of, and the
 * details sheet already refuses to store one. Shared so that every reading
 * treats "no estimate" and "a nonsense estimate" the same way, which is what
 * keeps an open-ended session open-ended.
 */
private fun usableEstimate(estimateMinutes: Int?): Int? =
    estimateMinutes?.takeIf { minutes -> minutes > 0 }

/**
 * The six states `docs/decisions.md` D-013 draws, derived rather than stored.
 *
 * Storing which state a session is in would be a second answer to a question the
 * session and the task's estimate already answer between them, and two answers
 * is how a paused session ends up drawing a Pause button.
 */
enum class FocusState {

    /** A task chosen, no clock running. The estimate, and a control to begin. */
    Ready,

    /** Working, against an estimate. */
    Running,

    /** Stopped, against an estimate. */
    Paused,

    /** The estimate is used up. Complete is the primary, +5 min the secondary. */
    EstimateReached,

    /** Working, with no estimate to measure against. */
    OpenEnded,

    /** Stopped, with no estimate to measure against. */
    OpenEndedPaused
}

/**
 * Whether the clock is running, which is the whole of what the shape says.
 *
 * `docs/decisions.md` D-014. Ready and both paused states are at rest; the other
 * three are running, including Estimate reached, which is still counting even
 * though it has nothing left to count down.
 */
val FocusState.isClockRunning: Boolean
    get() = when (this) {
        FocusState.Running, FocusState.EstimateReached, FocusState.OpenEnded -> true
        FocusState.Ready, FocusState.Paused, FocusState.OpenEndedPaused -> false
    }

/**
 * Which of the six states [session] is in, for a task estimated at
 * [estimateMinutes].
 *
 * The order of the checks is what keeps them exclusive. Paused is asked before
 * the estimate, because a paused session is paused whether or not its estimate
 * ran out while the user was away from it: offering +5 min on a stopped clock
 * would extend something that is not advancing, and the user pressing Resume
 * would find the extra five already spent.
 */
fun focusStateOf(
    session: FocusSession?,
    estimateMinutes: Int?,
    now: Instant
): FocusState {
    if (session == null) return FocusState.Ready

    val estimate = session.estimateMinutes(estimateMinutes)

    return when {
        session.isPaused && estimate == null -> FocusState.OpenEndedPaused
        session.isPaused -> FocusState.Paused
        estimate == null -> FocusState.OpenEnded
        session.remaining(now, estimateMinutes)?.isZero == true -> FocusState.EstimateReached
        else -> FocusState.Running
    }
}

/**
 * The readout, as `mm:ss`.
 *
 * D-013 put a readable number on this screen and `focus.md` records why the old
 * ban on "a countdown, an elapsed clock, or any digits counting anything" did
 * not survive it: a paused session that cannot say how much is left is a state
 * the user cannot act on.
 *
 * A timed session counts down, so the number answers "how long have I got". An
 * open-ended one counts up, because there is no endpoint for it to count toward
 * and elapsed time is the only true thing it can say.
 *
 * Minutes are not wrapped at sixty. A ninety minute estimate reads "90:00"
 * rather than "1:30:00", because a Focus session is not long enough for hours to
 * be worth the punctuation, and the two-field form stays one width.
 */
fun focusReadout(session: FocusSession?, estimateMinutes: Int?, now: Instant): String {
    val shown = when (session) {
        // A chosen task with no session shows the whole estimate, which is what
        // the user is about to commit to. With no estimate it shows zero: no
        // time has been worked, and that is the honest reading.
        null -> Duration.ofMinutes((usableEstimate(estimateMinutes) ?: 0).toLong())
        else -> session.remaining(now, estimateMinutes) ?: session.elapsed(now)
    }

    val totalSeconds = shown.seconds.coerceAtLeast(0L)

    return "%02d:%02d".format(totalSeconds / SecondsPerMinute, totalSeconds % SecondsPerMinute)
}

private const val SecondsPerMinute = 60L
