package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FocusProgressTest {

    private val startedAt: Instant = Instant.parse("2026-09-02T09:00:00Z")

    private fun progressAfterMinutes(minutes: Long, estimate: Int? = 45): Float? =
        focusProgress(startedAt, startedAt.plusSeconds(minutes * 60), estimate)

    // The fraction

    @Test
    fun `a session that has just begun has made no progress`() {
        assertEquals(0f, progressAfterMinutes(0))
    }

    @Test
    fun `half the estimate is half the progress`() {
        assertEquals(0.5f, progressAfterMinutes(minutes = 22, estimate = 44))
    }

    @Test
    fun `the whole estimate is the whole progress`() {
        assertEquals(1f, progressAfterMinutes(minutes = 45))
    }

    // The ends

    @Test
    fun `running over the estimate stays at the end rather than going past it`() {
        // Overrunning is ordinary. The shape settles rather than carrying on
        // into a state that would have to mean something.
        assertEquals(1f, progressAfterMinutes(minutes = 200))
    }

    @Test
    fun `a clock that has gone backwards reads as the start`() {
        // Time zones, a manual clock change, an NTP correction. None of them
        // are the user's doing and none should show a negative session.
        val earlier = startedAt.minusSeconds(600)

        assertEquals(0f, focusProgress(startedAt, earlier, 45))
    }

    // No estimate

    @Test
    fun `a task with no estimate has no progress at all`() {
        // Null, not zero: there is nothing to be a fraction of, and the caller
        // shows a shape that does not move rather than one stuck at the start.
        assertNull(progressAfterMinutes(minutes = 10, estimate = null))
    }

    @Test
    fun `a zero estimate has no progress`() {
        assertNull(progressAfterMinutes(minutes = 10, estimate = 0))
    }

    @Test
    fun `a negative estimate has no progress`() {
        assertNull(progressAfterMinutes(minutes = 10, estimate = -5))
    }

    // Derived, not accumulated

    @Test
    fun `progress depends only on the two instants, not on being watched`() {
        // The guarantee that makes backgrounding safe: asking once after
        // thirty minutes gives what asking every second for thirty minutes
        // would have arrived at.
        val watched = progressAfterMinutes(minutes = 30, estimate = 60)
        val unwatched = focusProgress(
            startedAt = startedAt,
            now = startedAt.plusSeconds(30 * 60),
            estimatedDurationMinutes = 60
        )

        assertEquals(watched, unwatched)
        assertEquals(0.5f, unwatched)
    }

    // The cycle, for a task with no estimate

    private fun phaseAfterMinutes(minutes: Long): Float =
        focusElapsedPhase(startedAt, startedAt.plusSeconds(minutes * 60))

    @Test
    fun `a session with no estimate begins at the start of the ring`() {
        assertEquals(0f, phaseAfterMinutes(0))
    }

    @Test
    fun `the phase advances evenly across the cycle`() {
        assertEquals(0.25f, phaseAfterMinutes(5))
        assertEquals(0.5f, phaseAfterMinutes(10))
        assertEquals(0.75f, phaseAfterMinutes(15))
    }

    @Test
    fun `the phase wraps rather than arriving`() {
        // The property that keeps it from being read as progress. A value that
        // stopped at the end would draw the same picture a used-up estimate
        // draws, and mean something completely different. Wrapping is
        // continuous because the ring's last shape morphs back into its first.
        assertEquals(0f, phaseAfterMinutes(20))
        assertEquals(0f, phaseAfterMinutes(40))
    }

    @Test
    fun `the cycle repeats indefinitely because there is nothing to arrive at`() {
        assertEquals(phaseAfterMinutes(3), phaseAfterMinutes(23))
        assertEquals(phaseAfterMinutes(3), phaseAfterMinutes(203))
    }

    @Test
    fun `a clock that has gone backwards reads as the start of the cycle too`() {
        assertEquals(0f, focusElapsedPhase(startedAt, startedAt.minusSeconds(600)))
    }
}
