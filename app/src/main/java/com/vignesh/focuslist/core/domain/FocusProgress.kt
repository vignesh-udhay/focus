package com.vignesh.focuslist.core.domain

import java.time.Duration
import java.time.Instant

/**
 * How far a focus session has run against the estimate the task was given.
 *
 * Derived from [startedAt] and [now] rather than accumulated tick by tick.
 * A session outlives being backgrounded, and a process that was frozen for ten
 * minutes has to come back knowing ten minutes went by; a counter that only
 * advanced while someone was watching would come back wrong.
 *
 * Null when the task carries no estimate. That is not zero progress, it is the
 * absence of anything to be a fraction of, and the caller shows a shape that
 * does not move rather than one frozen at the start.
 *
 * Clamped to 0..1. Running over the estimate is ordinary and says nothing has
 * gone wrong, so the shape settles at its final form and stays there instead of
 * carrying on into a state that would have to mean something.
 */
fun focusProgress(
    startedAt: Instant,
    now: Instant,
    estimatedDurationMinutes: Int?
): Float? {
    // A zero or negative estimate is not a duration anything can be measured
    // against. The details sheet already refuses to store one.
    if (estimatedDurationMinutes == null || estimatedDurationMinutes <= 0) return null

    val elapsed = Duration.between(startedAt, now).toMillis()
    if (elapsed <= 0L) return 0f

    val estimated = estimatedDurationMinutes.toLong() * MillisPerMinute

    return (elapsed.toFloat() / estimated.toFloat()).coerceIn(0f, 1f)
}

private const val MillisPerMinute = 60_000L

/**
 * Where the shape stands for a session whose task carries no estimate.
 *
 * A separate function from [focusProgress] and deliberately not a fallback for
 * it, because the two values mean different things. [focusProgress] is a
 * fraction of something: it starts, it advances, it arrives, and arriving means
 * the estimate is used up. This one is a fraction of nothing. There is no
 * endpoint to reach, so it does not have one.
 *
 * A triangle wave rather than a ramp, and that is the whole of the design. A
 * ramp would climb to the far shape and stay there, which is the same picture a
 * finished estimate draws, and a user who has seen the estimate case once would
 * read it as "done". Going out and coming back cannot be read that way: a shape
 * that returns to where it began is plainly not counting toward anything.
 *
 * [CycleMillis] is long on purpose. The shape has to be too slow to watch, or
 * it becomes the clock this screen exists to hide; twenty minutes out and back
 * is slower than the eye follows from moment to moment and obviously different
 * if you look away and return.
 *
 * This is `focus.md`'s recorded exception to the shape-morphing rule being
 * widened, not the rule being dropped. The morph still says one thing and only
 * one: the session is running.
 */
fun focusElapsedCycle(startedAt: Instant, now: Instant): Float {
    val elapsed = Duration.between(startedAt, now).toMillis()
    // A clock that has gone backwards is the same non-answer it is for
    // progress: the session has not started as far as anyone can tell.
    if (elapsed <= 0L) return 0f

    val phase = (elapsed % CycleMillis).toFloat() / CycleMillis

    return if (phase <= 0.5f) phase * 2f else (1f - phase) * 2f
}

/** One full traverse out to the far shape and back. */
private const val CycleMillis = 20L * 60_000L
