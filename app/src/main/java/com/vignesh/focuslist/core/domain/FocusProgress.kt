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
