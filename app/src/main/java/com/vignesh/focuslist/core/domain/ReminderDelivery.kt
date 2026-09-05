package com.vignesh.focuslist.core.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * What happened when a reminder came due.
 *
 * The app's own record of whether it kept its promise. `docs/decisions.md`
 * D-009 is the argument for it: on a device that silently demotes exact
 * alarms, every permission the app can check says yes while reminders arrive
 * late, so the only honest question is what actually happened, and the only
 * way to answer it is to have written it down.
 *
 * A row per firing, kept apart from the task on purpose. A task's own
 * [Task.reminderDeliveredAt] is cleared by rescheduling, by completing, and by
 * a recurring occurrence rolling forward, so the evidence is destroyed by
 * ordinary use. This outlives all three, and carries the title as it read at
 * the time, because a record that says a reminder was late is worthless if it
 * cannot say which one.
 */
data class ReminderDelivery(
    val id: String,
    val taskId: String,

    /** The title as it read when this fired, not as it reads now. */
    val taskTitle: String,

    /** The time the user chose, for saying "due 3:30 PM" back to them. */
    val dueAt: LocalDateTime,

    /**
     * The moment the alarm was aimed at, on the wall clock.
     *
     * Not always [dueAt]: a reminder set for a time already past is aimed at
     * the present instead, so it arrives at once rather than never. Lateness
     * is measured against what was actually promised, so a user who sets a
     * reminder for this morning at lunchtime has not been failed.
     */
    val scheduledWallAt: Instant,

    /** The same moment on `SystemClock.elapsedRealtime()`. */
    val scheduledElapsedAt: Long,

    /** When the alarm actually arrived, on the wall clock. */
    val arrivedWallAt: Instant,

    /** The same arrival on `SystemClock.elapsedRealtime()`. */
    val arrivedElapsedAt: Long,

    /**
     * How far ahead this alarm was set when it was scheduled.
     *
     * Not derivable from the two timestamps above, which say when the alarm
     * was aimed and when it landed, never when it was placed. It is recorded
     * because it is the only signal the app has about whether a delivery
     * actually tested anything: see [testsIdleDelivery].
     */
    val scheduledAhead: Duration,

    val outcome: DeliveryOutcome
) {

    /**
     * How late the reminder was, measured on the clock that cannot move.
     *
     * `AGENTS.md` requires both clocks and this is why. A phone corrects its
     * own clock routinely, from carrier NITZ and from an NTP poll, and a
     * correction between scheduling and firing lands entirely on the wall
     * clock. Measured there, a reminder can appear to arrive early, which
     * `AlarmManager` has no mechanism to do. The exact-alarm spike produced
     * exactly that reading before anyone was recording the second clock.
     *
     * Negative values are clamped away for the same reason: early is not a
     * thing that happens, so a negative here is a measurement artefact, not a
     * fact about the reminder.
     */
    val lateness: Duration
        get() = Duration.ofMillis(arrivedElapsedAt - scheduledElapsedAt).coerceAtLeast(Duration.ZERO)

    /**
     * Whether the wall clock moved between scheduling and arrival.
     *
     * The difference between the two clocks' answers, which is zero when
     * nothing moved. Worth keeping because it is the one explanation for a
     * strange reading that is not the app's fault, and because a health screen
     * that blamed the app for a timezone change would be lying.
     */
    val clockDrift: Duration
        get() = Duration.ofMillis(
            (arrivedWallAt.toEpochMilli() - scheduledWallAt.toEpochMilli()) -
                (arrivedElapsedAt - scheduledElapsedAt)
        )

    /**
     * Whether this delivery is worth anything as evidence that the device
     * behaves.
     *
     * The failure Phase 2 exists for happens to a phone that has been left
     * alone: manufacturer sleep features and Doze both need idle time before
     * they bite. A reminder set for five minutes' time, while the user is
     * holding the phone, is never going to meet either, so it arriving
     * punctually says nothing about the one set the night before.
     *
     * Futurity is a proxy rather than a measurement. A phone can be in use for
     * the whole hour and the app cannot tell. It is the honest best available:
     * a demoted alarm's window is a fraction of its futurity, so the further
     * ahead an alarm is set, the more exposure it has and the more its
     * punctuality is worth.
     *
     * A delivery that fails this is still recorded and can still be late. Only
     * the clearing of a warning needs evidence; a missed reminder is a missed
     * reminder however soon it was set.
     */
    val testsIdleDelivery: Boolean
        get() = scheduledAhead >= EvidenceHorizon
}

/**
 * How far ahead an alarm has to be set before its punctuality means anything.
 *
 * An hour, which is comfortably past the point where a screen-off phone enters
 * light Doze and where the manufacturer features in question start freezing
 * background processes. Shorter than that and a punctual delivery proves the
 * app can talk to `AlarmManager`, which was never in doubt.
 */
val EvidenceHorizon: Duration = Duration.ofHours(1)

/** Whether the reminder actually reached the user. */
enum class DeliveryOutcome {

    /** Posted. The user was told. */
    Announced,

    /**
     * The alarm arrived and the app could not post.
     *
     * A failure the user never sees, which is why it is written down. The
     * reminder is still owed: `ReminderReceiver` leaves it undelivered so a
     * later pass can announce it.
     */
    Suppressed
}

/**
 * How late a reminder has to be before the app calls it missed.
 *
 * A judgement, and worth naming as one. Below this a person is unlikely to
 * notice; above it they are, and the app has broken the one promise it makes.
 *
 * Deliberately not zero. Every alarm has some delivery cost, and a health
 * screen that reported a failure whenever a reminder arrived 300ms after its
 * second would cry wolf until it was ignored, which is worse than saying
 * nothing.
 */
val LateThreshold: Duration = Duration.ofMinutes(2)

/** Whether this delivery is one the user should be told about. */
fun ReminderDelivery.isConcerning(threshold: Duration = LateThreshold): Boolean =
    outcome == DeliveryOutcome.Suppressed || lateness >= threshold

/**
 * The delivery a health screen should lead with, or null when all is well.
 *
 * The most recent concerning one rather than the worst ever. A device that
 * misbehaved once last month and has been fine since is a device that is fine,
 * and leading with its worst day would keep a solved problem on screen.
 *
 * Recency is measured on arrival, because that is when the user experienced it.
 */
fun latestConcern(
    deliveries: List<ReminderDelivery>,
    threshold: Duration = LateThreshold
): ReminderDelivery? =
    deliveries.filter { it.isConcerning(threshold) }.maxByOrNull { it.arrivedWallAt }

/**
 * The worst lateness across [deliveries], for saying how bad it gets.
 *
 * Zero when there is nothing to report, so a caller can compare without
 * handling an absent case.
 */
fun worstLateness(deliveries: List<ReminderDelivery>): Duration =
    deliveries.maxOfOrNull { it.lateness } ?: Duration.ZERO
