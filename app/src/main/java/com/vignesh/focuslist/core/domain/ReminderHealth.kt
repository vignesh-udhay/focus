package com.vignesh.focuslist.core.domain

import java.time.Duration
import java.time.Instant

/**
 * Whether the app can currently be relied on to interrupt the user.
 *
 * The question `PRODUCT.md` makes the product's central claim, and the one
 * `docs/decisions.md` D-009 says the app has been answering wrongly. Three
 * permissions can all report success on a device that delivers reminders a
 * minute late, so a screen built on permissions alone would report green on a
 * phone that is failing.
 *
 * So this holds both: what the platform says the app is allowed to do, and
 * what actually happened when it tried. Where they disagree, what happened
 * wins. That rule is the whole reason this type exists rather than the screen
 * reading three booleans.
 */
data class ReminderHealth(
    val notifications: CheckState,
    val exactAlarms: CheckState,
    val backgroundWork: CheckState,

    /**
     * The feature [backgroundWork] is about, or null when this device has
     * none. Carried so the screen can name it: "Sleep standby" is findable in
     * the user's settings app where "background restrictions" is not.
     */
    val restriction: DeviceRestriction? = null,

    /**
     * The recent delivery worth telling the user about, or null when there is
     * none. Already filtered for recency by [reminderHealth].
     */
    val latestConcern: ReminderDelivery? = null
) {

    /** The three rows the health screen draws, in the order it draws them. */
    val checks: List<Pair<HealthCheck, CheckState>>
        get() = listOf(
            HealthCheck.Notifications to notifications,
            HealthCheck.ExactAlarms to exactAlarms,
            HealthCheck.BackgroundWork to backgroundWork
        )

    /**
     * What the screen leads with.
     *
     * A missed reminder outranks a failing check, even though a failing check
     * is usually its cause. The user experienced the late reminder; the
     * permission is the explanation, and it is still on screen underneath.
     * Leading with the explanation would be the app talking about itself.
     */
    val state: ReminderHealthState
        get() = when {
            latestConcern != null -> ReminderHealthState.Missed(latestConcern)
            checks.any { (_, state) -> state != CheckState.Ok } -> ReminderHealthState.ActionNeeded
            else -> ReminderHealthState.Ready
        }
}

/** One thing that has to hold for a reminder to arrive. */
enum class HealthCheck {

    /** Without this the alarm fires and nothing appears. */
    Notifications,

    /** Without this the notification appears, late. */
    ExactAlarms,

    /**
     * Whether the device lets the app run in the background at all.
     *
     * The vaguest of the three by necessity. Android's own battery allowlist
     * is one part of it; the manufacturer features that `PRODUCT.md` cares
     * about are not visible to any API and have to be inferred.
     */
    BackgroundWork
}

/** How a check came out. */
enum class CheckState {

    /** Nothing wrong that the app can see. */
    Ok,

    /**
     * Known to be a risk, not known to be failing.
     *
     * The state manufacturer restrictions land in. The app can tell that a
     * device has a feature that delays alarms; it usually cannot tell whether
     * it is switched on. Saying so honestly is better than guessing either way.
     */
    Warning,

    /** Known to be failing. The app has been refused. */
    Blocked
}

/** What the health screen says, and which of its four frames it draws. */
sealed interface ReminderHealthState {

    /** Nothing has been read yet. `reminder/Health Checking`. */
    data object Checking : ReminderHealthState

    /** `reminder/Health Ready`. */
    data object Ready : ReminderHealthState

    /** Something is configured in a way that will cost the user. `Health Action`. */
    data object ActionNeeded : ReminderHealthState

    /** A reminder actually went wrong. `reminder/Health Missed`. */
    data class Missed(val delivery: ReminderDelivery) : ReminderHealthState
}

/**
 * A manufacturer power feature known to delay alarms.
 *
 * None of these is visible to any API. `isIgnoringBatteryOptimizations()`
 * reports Android's own allowlist and nothing else, and on the device in
 * D-009 joining that allowlist changed the alarm's flags and left its delivery
 * window untouched. So the app infers the feature from the manufacturer and
 * says what it cannot know, rather than claiming a state it has not measured.
 *
 * Named for the feature rather than the vendor, because several vendors ship
 * the same idea under the same word and the user is looking for the word in
 * their own settings app.
 */
enum class DeviceRestriction {

    /** OnePlus, OPPO and Realme. The feature D-009 was measured against. */
    SleepStandby,

    /** Xiaomi, Redmi and POCO. */
    Autostart,

    /** Samsung. */
    SleepingApps,

    /** Huawei and Honor. */
    ProtectedApps
}

/**
 * How long a missed reminder stays news.
 *
 * Without a window the screen would say MISSED for ever on the strength of one
 * bad afternoon, and a warning that never clears is a warning people learn to
 * ignore. A week is long enough that a user who sees it has probably not
 * forgotten the incident, and short enough that a fixed problem stops being
 * reported.
 */
val ConcernWindow: Duration = Duration.ofDays(7)

/**
 * How many qualifying deliveries in a row count as this device behaving.
 *
 * Small on purpose. The question is not whether the device is proven, it is
 * whether the app has any reason to nag. A few reminders that were genuinely
 * exposed to idle time and arrived anyway is reason enough to stop, and the
 * moment one does not, the record says so and the warning comes back.
 */
const val EvidenceOfHealth = 3

/**
 * Assembles the health of the reminder system from what can be checked and
 * what was recorded.
 *
 * [restriction] is what this device is capable of doing to background work,
 * not what it is doing. Turning that into a state needs the delivery record,
 * which is why it happens here rather than at the platform edge: a phone whose
 * last few reminders all arrived on time is a phone that is behaving, whatever
 * its manufacturer is capable of, and warning about it anyway would be nagging
 * a user whose app works.
 *
 * [now] is passed rather than read, like every other derivation in this
 * package, so the recency window is testable without waiting a week.
 */
fun reminderHealth(
    notifications: CheckState,
    exactAlarms: CheckState,
    restriction: DeviceRestriction?,
    deliveries: List<ReminderDelivery>,
    now: Instant,
    window: Duration = ConcernWindow
): ReminderHealth {
    val recent = deliveries.filter { it.arrivedWallAt.isAfter(now.minus(window)) }

    return ReminderHealth(
        notifications = notifications,
        exactAlarms = exactAlarms,
        restriction = restriction,
        backgroundWork = backgroundWorkState(restriction, recent),
        latestConcern = latestConcern(recent)
    )
}

/**
 * Whether to warn about what this device could do to a reminder.
 *
 * No restriction is nothing to say. A restriction with no evidence either way
 * is a warning, because the cost of a missed reminder is higher than the cost
 * of a sentence the user reads once. A restriction with a run of punctual
 * deliveries behind it is a device demonstrating that it is fine.
 *
 * The run has to be unbroken and recent. One late reminder among three
 * punctual ones is exactly the intermittent behaviour these features produce,
 * and averaging it away would hide the thing being looked for.
 */
private fun backgroundWorkState(
    restriction: DeviceRestriction?,
    recent: List<ReminderDelivery>
): CheckState {
    if (restriction == null) return CheckState.Ok

    // Only deliveries that were actually exposed to idle time count. A run of
    // reminders set for five minutes' time and arriving on the second says
    // nothing about the one set for tomorrow morning, and clearing the warning
    // on them would be the app reassuring the user about a case it never
    // tested.
    val evidence = recent
        .filter { it.testsIdleDelivery }
        .sortedByDescending { it.arrivedWallAt }
        .take(EvidenceOfHealth)

    return if (evidence.size >= EvidenceOfHealth && evidence.none { it.isConcerning() }) {
        CheckState.Ok
    } else {
        CheckState.Warning
    }
}
