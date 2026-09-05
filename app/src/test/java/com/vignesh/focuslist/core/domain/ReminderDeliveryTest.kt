package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * The arithmetic behind "a reminder arrived 41 minutes late".
 *
 * `docs/decisions.md` D-009 is why this exists: every permission the app can
 * check reports success on a device that delivers reminders late, so the only
 * honest measure is what actually happened. These are the cases where reading
 * that record wrongly would make the app lie about its own reliability, in
 * either direction.
 */
class ReminderDeliveryTest {

    private val due = LocalDateTime.of(2026, 9, 5, 15, 30)
    private val scheduled = Instant.parse("2026-09-05T10:00:00Z")

    /** Both clocks agreeing, which is the ordinary case. */
    private fun delivery(
        id: String = "d1",
        lateBy: Duration = Duration.ZERO,
        wallLateBy: Duration = lateBy,
        scheduledAhead: Duration = Duration.ofHours(8),
        outcome: DeliveryOutcome = DeliveryOutcome.Announced
    ) = ReminderDelivery(
        id = id,
        taskId = "t1",
        taskTitle = "Take medication",
        dueAt = due,
        scheduledWallAt = scheduled,
        scheduledElapsedAt = 1_000_000L,
        arrivedWallAt = scheduled.plus(wallLateBy),
        arrivedElapsedAt = 1_000_000L + lateBy.toMillis(),
        scheduledAhead = scheduledAhead,
        outcome = outcome
    )

    // 1. Lateness

    @Test
    fun `a reminder that arrived on its second is not late`() {
        assertEquals(Duration.ZERO, delivery().lateness)
    }

    @Test
    fun `lateness is the gap on the monotonic clock`() {
        assertEquals(
            Duration.ofMinutes(41),
            delivery(lateBy = Duration.ofMinutes(41)).lateness
        )
    }

    @Test
    fun `a reminder is never early`() {
        // AlarmManager has no mechanism to fire early, so a negative reading
        // is a measurement artefact rather than a fact. The exact-alarm spike
        // produced one of these before both clocks were being recorded.
        assertEquals(
            Duration.ZERO,
            delivery(lateBy = Duration.ofMinutes(-5)).lateness
        )
    }

    @Test
    fun `a clock correction does not become lateness`() {
        // The wall clock jumped forward twenty minutes between scheduling and
        // arrival. The reminder was punctual, and measuring it on the wall
        // alone would report twenty minutes of failure that never happened.
        val corrected = delivery(
            lateBy = Duration.ZERO,
            wallLateBy = Duration.ofMinutes(20)
        )

        assertEquals(Duration.ZERO, corrected.lateness)
        assertEquals(Duration.ofMinutes(20), corrected.clockDrift)
    }

    @Test
    fun `the clocks agreeing means no drift`() {
        assertEquals(
            Duration.ZERO,
            delivery(lateBy = Duration.ofMinutes(41)).clockDrift
        )
    }

    // 2. What counts as worth reporting

    @Test
    fun `a punctual delivery is not concerning`() {
        assertEquals(false, delivery().isConcerning())
    }

    @Test
    fun `a delivery just under the threshold is not concerning`() {
        // The threshold is a judgement about what a person notices. Being
        // strict here is what stops the health screen crying wolf.
        assertEquals(
            false,
            delivery(lateBy = LateThreshold.minusSeconds(1)).isConcerning()
        )
    }

    @Test
    fun `a delivery at the threshold is concerning`() {
        assertEquals(true, delivery(lateBy = LateThreshold).isConcerning())
    }

    @Test
    fun `a suppressed delivery is concerning however punctual`() {
        // Arrived exactly on time and the user was told nothing. This is the
        // failure PRODUCT.md calls the most severe, and it is invisible.
        assertEquals(
            true,
            delivery(outcome = DeliveryOutcome.Suppressed).isConcerning()
        )
    }

    // 3. What the health screen leads with

    @Test
    fun `nothing is reported when every delivery was fine`() {
        assertNull(latestConcern(listOf(delivery("a"), delivery("b"))))
    }

    @Test
    fun `nothing is reported for an empty history`() {
        assertNull(latestConcern(emptyList()))
    }

    @Test
    fun `the most recent concern is reported, not the worst`() {
        // A device that misbehaved badly once and mildly since is still
        // misbehaving, and leading with the old incident would keep a solved
        // problem on screen after it stopped.
        val old = delivery("old", lateBy = Duration.ofHours(3))
            .copy(arrivedWallAt = scheduled.minus(Duration.ofDays(30)))
        val recent = delivery("recent", lateBy = Duration.ofMinutes(4))

        assertEquals("recent", latestConcern(listOf(old, recent))?.id)
    }

    @Test
    fun `a punctual delivery after a late one does not hide it`() {
        // Recency decides between concerns, not between a concern and a
        // success. One reminder arriving on time does not undo the one that
        // did not.
        val late = delivery("late", lateBy = Duration.ofMinutes(41))
        val fine = delivery("fine").copy(arrivedWallAt = scheduled.plus(Duration.ofDays(1)))

        assertEquals("late", latestConcern(listOf(late, fine))?.id)
    }

    @Test
    fun `the record names the task as it read when it fired`() {
        // The whole point of carrying the title. "A reminder was 41 minutes
        // late" is not actionable; naming it is.
        assertEquals("Take medication", latestConcern(listOf(delivery(lateBy = Duration.ofMinutes(41))))?.taskTitle)
    }

    // 4. How bad it gets

    @Test
    fun `the worst lateness is the largest gap seen`() {
        assertEquals(
            Duration.ofMinutes(41),
            worstLateness(
                listOf(
                    delivery("a", lateBy = Duration.ofSeconds(2)),
                    delivery("b", lateBy = Duration.ofMinutes(41)),
                    delivery("c", lateBy = Duration.ofMinutes(3))
                )
            )
        )
    }

    @Test
    fun `an empty history is not the worst case`() {
        // Zero rather than absent, so a caller comparing against a threshold
        // does not have to handle "no answer" as a third state.
        assertEquals(Duration.ZERO, worstLateness(emptyList()))
    }

    // 5. Whether a delivery proves anything

    @Test
    fun `a reminder set the night before is evidence`() {
        // Long enough that a screen-off phone reaches Doze, and that a
        // manufacturer sleep feature has had its chance.
        assertEquals(true, delivery(scheduledAhead = Duration.ofHours(8)).testsIdleDelivery)
    }

    @Test
    fun `a reminder set for five minutes time proves nothing`() {
        // The phone is in the user's hand and nothing has had time to throttle
        // anything. It arriving punctually says only that AlarmManager works,
        // which was never in question.
        assertEquals(false, delivery(scheduledAhead = Duration.ofMinutes(5)).testsIdleDelivery)
    }

    @Test
    fun `the horizon is inclusive`() {
        assertEquals(true, delivery(scheduledAhead = EvidenceHorizon).testsIdleDelivery)
        assertEquals(
            false,
            delivery(scheduledAhead = EvidenceHorizon.minusSeconds(1)).testsIdleDelivery
        )
    }

    @Test
    fun `an unrecorded futurity proves nothing`() {
        // Zero is what a row written before the column existed reads as, and
        // what the receiver falls back to. Unknown has to count as untested,
        // or an absent measurement would clear a warning.
        assertEquals(false, delivery(scheduledAhead = Duration.ZERO).testsIdleDelivery)
    }

    @Test
    fun `a short reminder can still be late`() {
        // Only the clearing of a warning needs evidence. A missed reminder is
        // missed however soon it was set.
        val hasty = delivery(scheduledAhead = Duration.ofMinutes(2), lateBy = Duration.ofMinutes(41))

        assertEquals(false, hasty.testsIdleDelivery)
        assertEquals(true, hasty.isConcerning())
    }

    // 5. The device this was written for

    @Test
    fun `the OnePlus measurement from D-009 reads as a real delivery`() {
        // The reminder set for 18:25 that arrived about fifty seconds later,
        // measured on the device the decision was written from. Under the
        // threshold, so it does not raise a missed reminder, and it is still
        // recorded, because a pattern of these is the finding.
        val observed = delivery(lateBy = Duration.ofSeconds(50))

        assertEquals(Duration.ofSeconds(50), observed.lateness)
        assertEquals(false, observed.isConcerning())
        assertEquals(Duration.ofSeconds(50), worstLateness(listOf(observed)))
    }
}
