package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * What the app says about its own reliability.
 *
 * The case these tests exist for is the last one: every permission granted,
 * every check green, and reminders arriving late anyway. That is the OnePlus
 * in `docs/decisions.md` D-009, and a health screen that reported Ready on it
 * would be worse than no health screen, because it would be a confident lie
 * about the one thing the product claims.
 */
class ReminderHealthTest {

    private val now: Instant = Instant.parse("2026-09-05T18:30:00Z")

    private fun delivery(
        id: String = "d1",
        lateBy: Duration = Duration.ZERO,
        arrivedAgo: Duration = Duration.ofHours(1),
        scheduledAhead: Duration = Duration.ofHours(8),
        outcome: DeliveryOutcome = DeliveryOutcome.Announced
    ): ReminderDelivery {
        val arrived = now.minus(arrivedAgo)

        return ReminderDelivery(
            id = id,
            taskId = "t1",
            taskTitle = "Take medication",
            dueAt = LocalDateTime.of(2026, 9, 5, 15, 30),
            scheduledWallAt = arrived.minus(lateBy),
            scheduledElapsedAt = 1_000_000L,
            arrivedWallAt = arrived,
            arrivedElapsedAt = 1_000_000L + lateBy.toMillis(),
            scheduledAhead = scheduledAhead,
            outcome = outcome
        )
    }

    private fun health(
        notifications: CheckState = CheckState.Ok,
        exactAlarms: CheckState = CheckState.Ok,
        restriction: DeviceRestriction? = null,
        deliveries: List<ReminderDelivery> = emptyList()
    ) = reminderHealth(
        notifications = notifications,
        exactAlarms = exactAlarms,
        restriction = restriction,
        deliveries = deliveries,
        now = now
    )

    // 1. Nothing wrong

    /**
     * The fresh install, and `docs/decisions.md` D-058's whole subject. Three
     * permissions granted and no reminder ever sent is Ready, because nothing is
     * wrong, and unverified, because nothing has been seen to arrive. The screen
     * used to say "Reminders are healthy" here on the strength of
     * `canScheduleExactAlarms()` alone.
     */
    @Test
    fun `everything allowed and nothing delivered yet is ready but unverified`() {
        assertEquals(ReminderHealthState.Ready(verified = false), health().state)
    }

    @Test
    fun `a history of punctual deliveries is ready and verified`() {
        assertEquals(
            ReminderHealthState.Ready(verified = true),
            health(deliveries = listOf(delivery("a"), delivery("b"))).state
        )
    }

    /**
     * The Test reminder button fires in thirty seconds, which is far short of
     * `EvidenceHorizon` and so proves nothing about a device that has been left
     * alone. It still counts here, because this flag answers the smaller
     * question of whether anything arrives at all, and a check the user cannot
     * satisfy from the screen it sits on is a check that reads as broken.
     */
    @Test
    fun `a punctual delivery that tested no idle time still verifies`() {
        val health = health(
            deliveries = listOf(delivery(scheduledAhead = Duration.ofSeconds(30)))
        )

        assertEquals(ReminderHealthState.Ready(verified = true), health.state)
    }

    /**
     * Verified is not a permanent claim. A device that behaved a fortnight ago
     * and has delivered nothing since is one the app has no current evidence
     * about, and the window is the same week the rest of the screen works in.
     */
    @Test
    fun `a punctual delivery older than the window stops verifying`() {
        val health = health(
            deliveries = listOf(delivery(arrivedAgo = ConcernWindow.plusDays(1)))
        )

        assertEquals(ReminderHealthState.Ready(verified = false), health.state)
    }

    /**
     * The two delivery questions are separate, and D-058 says why: clearing a
     * manufacturer warning is a claim about a phone that was left alone, and
     * needs `EvidenceOfHealth` idle-exposed deliveries. Saying a reminder has
     * been seen to arrive needs one of any kind.
     */
    @Test
    fun `one punctual delivery verifies without clearing a restriction warning`() {
        val health = health(
            restriction = DeviceRestriction.SleepStandby,
            deliveries = listOf(delivery())
        )

        assertEquals(true, health.verifiedDelivery)
        assertEquals(CheckState.Warning, health.backgroundWork)
        assertEquals(
            ReminderHealthState.WorthChecking(HealthCheck.BackgroundWork),
            health.state
        )
    }

    // 2. A check failing

    @Test
    fun `notifications blocked needs action, and says so`() {
        assertEquals(
            ReminderHealthState.ActionNeeded(HealthCheck.Notifications),
            health(notifications = CheckState.Blocked).state
        )
    }

    @Test
    fun `exact alarms blocked needs action, and says so`() {
        assertEquals(
            ReminderHealthState.ActionNeeded(HealthCheck.ExactAlarms),
            health(exactAlarms = CheckState.Blocked).state
        )
    }

    @Test
    fun `the worst failure is the one named`() {
        // The regression this carries a cause for. The first build of the
        // screen blamed the manufacturer's sleep feature for a notification
        // permission the user had refused, because every failure produced the
        // same sentence. Nothing appearing at all beats appearing late.
        val everythingWrong = health(
            notifications = CheckState.Blocked,
            exactAlarms = CheckState.Blocked,
            restriction = DeviceRestriction.SleepStandby
        )

        assertEquals(
            ReminderHealthState.ActionNeeded(HealthCheck.Notifications),
            everythingWrong.state
        )
        assertEquals(HealthCheck.Notifications, everythingWrong.firstFailing)
        assertEquals(HealthCheck.Notifications, everythingWrong.firstBlocked)
        // The restriction is still a guess and is still reported as one, even
        // on a screen already showing an error. Certainty is a property of the
        // check, not of the headline above it.
        assertEquals(HealthCheck.BackgroundWork, everythingWrong.firstWarning)
    }

    @Test
    fun `a late alarm is named ahead of a device that might cause one`() {
        assertEquals(
            ReminderHealthState.ActionNeeded(HealthCheck.ExactAlarms),
            health(
                exactAlarms = CheckState.Blocked,
                restriction = DeviceRestriction.SleepStandby
            ).state
        )
    }

    @Test
    fun `nothing failing has nothing to name`() {
        assertNull(health().firstFailing)
    }

    /**
     * **`docs/decisions.md` D-021.** A device that merely *has* a sleep feature
     * is worth checking, not an emergency. The app infers the feature from
     * `Build.MANUFACTURER` and has measured nothing, so it says so and does not
     * borrow the colours of a refusal it was actually told about.
     *
     * This assertion used to read `ActionNeeded`, which meant every OnePlus,
     * OPPO, Realme, Xiaomi, Redmi, POCO, Samsung, Huawei and Honor user saw a
     * permanent red screen from first launch on a phone where nothing might be
     * wrong.
     */
    @Test
    fun `a device that can delay alarms is worth checking, not an emergency`() {
        assertEquals(
            ReminderHealthState.WorthChecking(HealthCheck.BackgroundWork),
            health(restriction = DeviceRestriction.SleepStandby).state
        )
    }

    /**
     * It still warns rather than staying silent. Waiting for a real miss is the
     * more honest position and accepts a missed reminder as the price of
     * learning, which `PRODUCT.md` principle 1 forbids.
     */
    @Test
    fun `a warning is still raised before anything is missed`() {
        val state = health(restriction = DeviceRestriction.SleepStandby).state

        assertEquals(false, state is ReminderHealthState.Ready)
    }

    /**
     * A refusal outranks a guess, whatever the guess is about. The app was told
     * about one and inferred the other.
     */
    @Test
    fun `a blocked check outranks an inferred restriction`() {
        assertEquals(
            ReminderHealthState.ActionNeeded(HealthCheck.ExactAlarms),
            health(
                exactAlarms = CheckState.Blocked,
                restriction = DeviceRestriction.SleepStandby
            ).state
        )
    }

    /** And a delivery that actually went wrong outranks both. */
    @Test
    fun `a recorded late delivery outranks a blocked check and a restriction`() {
        val state = health(
            notifications = CheckState.Blocked,
            restriction = DeviceRestriction.SleepStandby,
            deliveries = listOf(delivery("a", lateBy = Duration.ofMinutes(4)))
        ).state

        assertTrue(state is ReminderHealthState.Missed)
    }

    /**
     * The worst-cost ordering survives the split, on both halves. `checks` is
     * ordered by what a failure costs, and both readers walk it in that order.
     */
    @Test
    fun `the split keeps the worst-cost ordering`() {
        val bothBlocked = health(
            notifications = CheckState.Blocked,
            exactAlarms = CheckState.Blocked
        )

        assertEquals(HealthCheck.Notifications, bothBlocked.firstBlocked)

        // Nothing blocked, so the blocked reader has nothing and the warning
        // reader answers instead.
        val onlyWarning = health(restriction = DeviceRestriction.SleepStandby)

        assertNull(onlyWarning.firstBlocked)
        assertEquals(HealthCheck.BackgroundWork, onlyWarning.firstWarning)
        assertEquals(HealthCheck.BackgroundWork, onlyWarning.firstFailing)
    }

    @Test
    fun `a plain device says nothing about background work`() {
        assertEquals(CheckState.Ok, health().backgroundWork)
    }

    @Test
    fun `the restriction is carried, so the screen can name it`() {
        // "Sleep standby" is findable in the user's own settings app. "This
        // device may restrict background work" is not.
        assertEquals(
            DeviceRestriction.SleepStandby,
            health(restriction = DeviceRestriction.SleepStandby).restriction
        )
    }

    // 3. Something actually went wrong

    @Test
    fun `a late reminder outranks the check that explains it`() {
        // Both are true and both are on screen. The user experienced the late
        // reminder; the blocked permission is the explanation, and leading
        // with the explanation would be the app talking about itself.
        val state = health(
            notifications = CheckState.Blocked,
            deliveries = listOf(delivery(lateBy = Duration.ofMinutes(41)))
        ).state

        assertEquals(ReminderHealthState.Missed(delivery(lateBy = Duration.ofMinutes(41))), state)
    }

    @Test
    fun `a suppressed reminder is a missed one`() {
        val state = health(deliveries = listOf(delivery(outcome = DeliveryOutcome.Suppressed))).state

        assertEquals(true, state is ReminderHealthState.Missed)
    }

    @Test
    fun `the missed state carries the delivery, so the screen can name it`() {
        val state = health(
            deliveries = listOf(delivery(lateBy = Duration.ofMinutes(41)))
        ).state as ReminderHealthState.Missed

        assertEquals("Take medication", state.delivery.taskTitle)
        assertEquals(Duration.ofMinutes(41), state.delivery.lateness)
    }

    // 4. When a missed reminder stops being news

    @Test
    fun `an old failure stops being reported`() {
        // Otherwise the screen says MISSED for ever on the strength of one bad
        // afternoon, and a warning that never clears is one people stop
        // reading.
        val state = health(
            deliveries = listOf(
                delivery(lateBy = Duration.ofHours(3), arrivedAgo = ConcernWindow.plusDays(1))
            )
        ).state

        // Unverified rather than verified: the punctual record aged out with
        // the failure, so there is nothing recent to have seen arrive.
        assertEquals(ReminderHealthState.Ready(verified = false), state)
    }

    @Test
    fun `a failure just inside the window is still reported`() {
        val state = health(
            deliveries = listOf(
                delivery(lateBy = Duration.ofHours(3), arrivedAgo = ConcernWindow.minusHours(1))
            )
        ).state

        assertEquals(true, state is ReminderHealthState.Missed)
    }

    @Test
    fun `an old failure does not hide a current misconfiguration`() {
        // The failure aged out, but notifications are off right now. Falling
        // all the way back to Ready would be the app forgetting to look.
        val state = health(
            notifications = CheckState.Blocked,
            deliveries = listOf(delivery(arrivedAgo = ConcernWindow.plusDays(1), lateBy = Duration.ofHours(3)))
        ).state

        assertEquals(ReminderHealthState.ActionNeeded(HealthCheck.Notifications), state)
    }

    // 5. A restrictive device proving itself

    @Test
    fun `a run of punctual reminders stops the warning`() {
        // The app has no way to read the setting, so behaviour is the only
        // evidence there is. Nagging a user whose reminders all arrive is how
        // a health screen teaches people to ignore it.
        val punctual = (1..EvidenceOfHealth).map {
            delivery(id = "d$it", arrivedAgo = Duration.ofHours(it.toLong()))
        }

        val health = health(restriction = DeviceRestriction.SleepStandby, deliveries = punctual)

        assertEquals(CheckState.Ok, health.backgroundWork)
        assertEquals(ReminderHealthState.Ready(verified = true), health.state)
    }

    @Test
    fun `not enough evidence yet leaves the warning up`() {
        val tooFew = (1 until EvidenceOfHealth).map {
            delivery(id = "d$it", arrivedAgo = Duration.ofHours(it.toLong()))
        }

        assertEquals(
            CheckState.Warning,
            health(restriction = DeviceRestriction.SleepStandby, deliveries = tooFew).backgroundWork
        )
    }

    @Test
    fun `one late reminder among punctual ones brings the warning back`() {
        // Intermittent is exactly how these features behave, so a rule that
        // averaged the run would hide the thing it is looking for.
        val mixed = listOf(
            delivery(id = "newest", arrivedAgo = Duration.ofHours(1)),
            delivery(id = "late", arrivedAgo = Duration.ofHours(2), lateBy = Duration.ofMinutes(41)),
            delivery(id = "older", arrivedAgo = Duration.ofHours(3))
        )

        assertEquals(
            CheckState.Warning,
            health(restriction = DeviceRestriction.SleepStandby, deliveries = mixed).backgroundWork
        )
    }

    @Test
    fun `old punctual deliveries do not vouch for the device now`() {
        // Outside the window they are not evidence about today, so the device
        // is back to unproven rather than back to fine.
        val stale = (1..EvidenceOfHealth).map {
            delivery(id = "d$it", arrivedAgo = ConcernWindow.plusDays(it.toLong()))
        }

        assertEquals(
            CheckState.Warning,
            health(restriction = DeviceRestriction.SleepStandby, deliveries = stale).backgroundWork
        )
    }

    @Test
    fun `punctual reminders that were never exposed do not clear the warning`() {
        // The rule this whole column exists for. Three reminders set for five
        // minutes' time arriving on the second is the app proving it can talk
        // to AlarmManager, not that this phone delivers after a night idle.
        val hasty = (1..EvidenceOfHealth).map {
            delivery(
                id = "d$it",
                arrivedAgo = Duration.ofHours(it.toLong()),
                scheduledAhead = Duration.ofMinutes(5)
            )
        }

        assertEquals(
            CheckState.Warning,
            health(restriction = DeviceRestriction.SleepStandby, deliveries = hasty).backgroundWork
        )
    }

    @Test
    fun `a mix clears the warning only on the qualifying ones`() {
        // Two overnight reminders and a pile of hasty ones is still one short
        // of the evidence needed, however many rows the table holds.
        val mixed = (1..EvidenceOfHealth - 1).map {
            delivery(id = "long$it", arrivedAgo = Duration.ofHours(it.toLong()))
        } + (1..5).map {
            delivery(
                id = "short$it",
                arrivedAgo = Duration.ofHours(it.toLong()),
                scheduledAhead = Duration.ofMinutes(5)
            )
        }

        assertEquals(
            CheckState.Warning,
            health(restriction = DeviceRestriction.SleepStandby, deliveries = mixed).backgroundWork
        )
    }

    // 5. The device this is all for

    @Test
    fun `every permission granted and reminders still late reads as missed`() {
        // The OnePlus in D-009. USE_EXACT_ALARM auto-granted, notifications
        // allowed, canScheduleExactAlarms() true, and the alarm demoted
        // anyway. A screen built on the three checks alone would say Ready.
        val state = health(
            notifications = CheckState.Ok,
            exactAlarms = CheckState.Ok,
            restriction = null,
            deliveries = listOf(delivery(lateBy = Duration.ofMinutes(41)))
        ).state

        assertEquals(true, state is ReminderHealthState.Missed)
    }

    @Test
    fun `the checks are still reported underneath a missed reminder`() {
        // The frame draws all three rows in every state, so the data has to
        // survive whatever the headline says.
        val health = health(
            notifications = CheckState.Ok,
            restriction = DeviceRestriction.SleepStandby,
            deliveries = listOf(delivery(lateBy = Duration.ofMinutes(41)))
        )

        assertEquals(true, health.state is ReminderHealthState.Missed)
        assertEquals(
            listOf(
                HealthCheck.Notifications to CheckState.Ok,
                HealthCheck.ExactAlarms to CheckState.Ok,
                HealthCheck.BackgroundWork to CheckState.Warning
            ),
            health.checks
        )
    }

    @Test
    fun `no concern is carried when nothing is concerning`() {
        assertNull(health(deliveries = listOf(delivery())).latestConcern)
    }
}
