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
 * Where a session with no estimate stands in its cycle.
 *
 * A separate function from [focusProgress] and deliberately not a fallback for
 * it, because the two values mean different things. [focusProgress] is a
 * fraction of something: it starts, it advances, it arrives, and arriving means
 * the estimate is used up. This one is a fraction of nothing. There is no
 * endpoint to reach, so it does not have one.
 *
 * A plain sawtooth, because what consumes it is a ring of shapes rather than a
 * journey between two. Wrapping from the end of the ring back to its start is
 * continuous, so nothing jumps at the seam, and there is no final form to be
 * mistaken for an arrival: the caller walks the whole ring and begins again.
 *
 * An earlier version returned a triangle wave, out and back between two shapes,
 * for the same reason. That worked, but it only announced itself as a cycle at
 * the moment it turned round, which was ten minutes in. Everything before that
 * looked exactly like progress toward a destination. Material's own answer to
 * an unknown duration is an indeterminate indicator that walks a sequence of
 * shapes, and a sequence says it is not counting within a form or two.
 *
 * [CycleMillis] is long on purpose. The shape has to be too slow to watch, or
 * it becomes the clock this screen exists to hide.
 */
fun focusElapsedPhase(startedAt: Instant, now: Instant): Float {
    val elapsed = Duration.between(startedAt, now).toMillis()
    // A clock that has gone backwards is the same non-answer it is for
    // progress: the session has not started as far as anyone can tell.
    if (elapsed <= 0L) return 0f

    return (elapsed % CycleMillis).toFloat() / CycleMillis
}

/** One full walk around the ring of shapes. */
private const val CycleMillis = 20L * 60_000L
