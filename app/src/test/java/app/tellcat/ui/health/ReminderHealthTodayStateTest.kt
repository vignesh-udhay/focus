package app.tellcat.ui.health

import app.tellcat.core.domain.CheckState
import app.tellcat.core.domain.DeliveryOutcome
import app.tellcat.core.domain.HealthCheck
import app.tellcat.core.domain.ReminderDelivery
import app.tellcat.core.domain.ReminderHealth
import app.tellcat.core.domain.ReminderHealthState
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The incident-specific acknowledgement rule for Today's banner. */
class ReminderHealthTodayStateTest {

    @Test
    fun `the acknowledged missed delivery is hidden on Today`() {
        assertEquals(
            // Unverified: the one recorded delivery is the concerning one being
            // acknowledged, so nothing recent has been seen to arrive on time.
            ReminderHealthState.Ready(verified = false),
            health("delivery-1").visibleOnToday("delivery-1")
        )
    }

    @Test
    fun `a newer missed delivery appears after an earlier one was acknowledged`() {
        val newer = health("delivery-2")

        assertEquals(newer.state, newer.visibleOnToday("delivery-1"))
    }

    @Test
    fun `acknowledging a miss reveals an active failure underneath it`() {
        val blocked = health(
            deliveryId = "delivery-1",
            notifications = CheckState.Blocked
        )

        assertEquals(
            ReminderHealthState.ActionNeeded(HealthCheck.Notifications),
            blocked.visibleOnToday("delivery-1")
        )
    }

    private fun health(
        deliveryId: String,
        notifications: CheckState = CheckState.Ok
    ) = ReminderHealth(
        notifications = notifications,
        exactAlarms = CheckState.Ok,
        backgroundWork = CheckState.Ok,
        latestConcern = missed(deliveryId).delivery
    )

    private fun missed(id: String): ReminderHealthState.Missed = ReminderHealthState.Missed(
        ReminderDelivery(
            id = id,
            taskId = "task-1",
            taskTitle = "Take medication",
            dueAt = LocalDateTime.of(2026, 9, 9, 23, 15),
            scheduledWallAt = Instant.parse("2026-09-09T17:45:00Z"),
            scheduledElapsedAt = 1_000_000L,
            arrivedWallAt = Instant.parse("2026-09-09T17:48:00Z"),
            arrivedElapsedAt = 1_180_000L,
            scheduledAhead = Duration.ofHours(8),
            outcome = DeliveryOutcome.Announced
        )
    )
}
