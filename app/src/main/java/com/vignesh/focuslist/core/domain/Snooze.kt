package com.vignesh.focuslist.core.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Putting a reminder off, and how far.
 *
 * The four choices the notification offers, from the `notify/Snooze options`
 * frame. Two are relative to the moment the user tapped, and two land on a
 * named hour, which is the distinction that makes this worth its own file:
 * "in ten minutes" cannot be got wrong, and "this evening" can.
 *
 * Pure and taking the current time as a parameter, like the rest of
 * `TaskQueries.kt` and `Reminders.kt`. Snoozing at 23:58 and snoozing across
 * a month boundary are tests here, not situations discovered in the field.
 */
enum class SnoozeOption {

    /** Ten minutes from now. */
    TenMinutes,

    /** An hour from now. */
    OneHour,

    /**
     * [EveningHour] today.
     *
     * The only option that can be unavailable. Offering "this evening" at
     * 22:00 would either mean a time already gone or quietly mean tomorrow,
     * and both are the notification lying about what the button does.
     */
    ThisEvening,

    /** [MorningHour] tomorrow. */
    TomorrowMorning
}

/** What "this evening" means. Early enough to still be an evening. */
val EveningHour: LocalTime = LocalTime.of(18, 0)

/** What "tomorrow" means, and it is the 9:00 the design's chip names. */
val MorningHour: LocalTime = LocalTime.of(9, 0)

/**
 * When [option] puts the reminder, or null when it does not apply.
 *
 * Null only ever comes back for [SnoozeOption.ThisEvening], and only when the
 * evening has gone. Callers should not offer an option this returns null for
 * rather than substituting something for it: a chip that says one time and
 * sets another is worse than a chip that is not there.
 *
 * The result is a wall-clock time, matching [Task.reminderAt], so a snooze
 * survives a timezone change the same way the original reminder does.
 */
fun snoozedUntil(option: SnoozeOption, now: LocalDateTime): LocalDateTime? = when (option) {
    SnoozeOption.TenMinutes -> now.plus(Duration.ofMinutes(10))
    SnoozeOption.OneHour -> now.plus(Duration.ofHours(1))

    // Strictly after, so tapping it at exactly 18:00 does not set a reminder
    // for the moment that has just arrived.
    SnoozeOption.ThisEvening ->
        now.toLocalDate().atTime(EveningHour).takeIf { it.isAfter(now) }

    SnoozeOption.TomorrowMorning -> now.toLocalDate().plusDays(1).atTime(MorningHour)
}

/**
 * The options worth showing at [now], in the order the design lists them.
 *
 * Filtering here rather than in the notification, so what the user is offered
 * and what the arithmetic will do cannot drift apart.
 */
fun availableSnoozeOptions(now: LocalDateTime): List<SnoozeOption> =
    SnoozeOption.entries.filter { option -> snoozedUntil(option, now) != null }
