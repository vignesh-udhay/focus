package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `everything allowed and nothing gone wrong reads as ready`() {
        assertEquals(ReminderHealthState.Ready, health().state)
    }

    @Test
    fun `a history of punctual deliveries is still ready`() {
        assertEquals(
            ReminderHealthState.Ready,
            health(deliveries = listOf(delivery("a"), delivery("b"))).state
        )
    }

    // 2. A check failing

    @Test
    fun `notifications blocked needs action`() {
        assertEquals(
            ReminderHealthState.ActionNeeded,
            health(notifications = CheckState.Blocked).state
        )
    }

    @Test
    fun `exact alarms blocked needs action`() {
        assertEquals(
            ReminderHealthState.ActionNeeded,
            health(exactAlarms = CheckState.Blocked).state
        )
    }

    @Test
    fun `a device that can delay alarms needs action before one is delayed`() {
        // The point of Warning, and the whole of Phase 2. The app cannot see
        // whether the manufacturer's sleep feature is switched on, only that
        // this device has one, and saying so before a reminder is missed beats
        // explaining it afterwards.
        assertEquals(
            ReminderHealthState.ActionNeeded,
            health(restriction = DeviceRestriction.SleepStandby).state
        )
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

        assertEquals(ReminderHealthState.Ready, state)
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

        assertEquals(ReminderHealthState.ActionNeeded, state)
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
        assertEquals(ReminderHealthState.Ready, health.state)
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
