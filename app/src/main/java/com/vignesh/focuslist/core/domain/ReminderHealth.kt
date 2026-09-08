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
     * Ordered by certainty, which is `docs/decisions.md` D-021's whole point.
     * A missed reminder outranks everything, because it happened. A refused
     * permission comes next, because the app was told. An inferred restriction
     * comes last, because the app guessed.
     *
     * A missed reminder outranks a failing check even though a failing check is
     * usually its cause. The user experienced the late reminder; the permission
     * is the explanation, and it is still on screen underneath. Leading with the
     * explanation would be the app talking about itself.
     *
     * **`ActionNeeded` used to fire for any non-`Ok` check**, which meant a
     * `Warning` the app inferred from `Build.MANUFACTURER` rendered exactly like
     * a `Blocked` permission the user had explicitly refused. Because that
     * inference is by manufacturer, every OnePlus, OPPO, Realme, Xiaomi, Redmi,
     * POCO, Samsung, Huawei and Honor user saw a permanent red "Action needed"
     * from first launch, on a phone where nothing might be wrong. A reliability
     * screen that is always red teaches people to ignore it, which is the one
     * thing this screen cannot afford.
     */
    val state: ReminderHealthState
        get() = when {
            latestConcern != null -> ReminderHealthState.Missed(latestConcern)
            firstBlocked != null -> ReminderHealthState.ActionNeeded(firstBlocked!!)
            firstWarning != null -> ReminderHealthState.WorthChecking(firstWarning!!)
            else -> ReminderHealthState.Ready
        }

    /**
     * The worst check the app was refused outright, or null when none was.
     *
     * Ordered by what a failure costs rather than by how the screen lays them
     * out. Blocked notifications mean nothing appears at all; a refused exact
     * alarm means it appears late. One screen, one sentence, one button, all
     * naming the same thing.
     */
    val firstBlocked: HealthCheck?
        get() = firstMatching(CheckState.Blocked)

    /**
     * The worst check the app merely suspects, or null when it suspects none.
     *
     * The same worst-cost ordering, and it applies here for the same reason: the
     * screen says one sentence and offers one button, so it has to choose which
     * risk to name when more than one is present.
     */
    val firstWarning: HealthCheck?
        get() = firstMatching(CheckState.Warning)

    /**
     * The first check in [checks] whose state is [wanted].
     *
     * [checks] is already in worst-cost order, so first is worst. That ordering
     * is deliberate and is documented on [firstBlocked]; both readers depend on
     * it, which is why they share this rather than each walking the list with
     * their own predicate.
     */
    private fun firstMatching(wanted: CheckState): HealthCheck? =
        checks.firstOrNull { (_, state) -> state == wanted }?.first

    /**
     * The check to talk about, worst first, or null when all three pass.
     *
     * Kept because it is the honest answer to "is anything wrong at all", which
     * is a different question from which headline to draw. It no longer decides
     * the headline: D-021 split that by certainty and [state] is where it lives.
     */
    val firstFailing: HealthCheck?
        get() = firstBlocked ?: firstWarning
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

/** What the health screen says, and which of its five frames it draws. */
sealed interface ReminderHealthState {

    /** Nothing has been read yet. `reminder/Health Checking`. */
    data object Checking : ReminderHealthState

    /** `reminder/Health Ready`. */
    data object Ready : ReminderHealthState

    /**
     * Something is configured in a way that will cost the user.
     * `reminder/Health Action`.
     *
     * Carries which check caused it, because the three fail differently and a
     * screen that said the same sentence for all of them would be wrong twice
     * out of three times. That is not hypothetical: the first build of this
     * screen blamed the manufacturer for a notification permission the user
     * had refused.
     */
    data class ActionNeeded(val cause: HealthCheck) : ReminderHealthState

    /**
     * Something the app suspects but has never measured.
     *
     * `docs/decisions.md` D-021. A manufacturer power feature is inferred from
     * `Build.MANUFACTURER` and is visible to no API, so the app knows the device
     * *has* it and cannot tell whether it is switched on. This is the state that
     * says so, and it is deliberately not an error: the app colours what it
     * knows.
     *
     * It still warns rather than staying silent. Waiting for a real miss before
     * mentioning the restriction is the more honest position and it accepts a
     * missed reminder as the price of learning, which `PRODUCT.md` principle 1
     * forbids. The warning is pre-emptive; it just has to be accurate about its
     * own certainty.
     *
     * Carries its cause for the same reason [ActionNeeded] does: the three fail
     * differently and one sentence has to name the right one.
     */
    data class WorthChecking(val cause: HealthCheck) : ReminderHealthState

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
