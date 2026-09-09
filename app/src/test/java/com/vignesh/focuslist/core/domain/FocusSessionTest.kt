package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The Focus session clock, from `docs/decisions.md` D-013 and D-014.
 *
 * All of it is a pure function of a session, a task's estimate and a moment, so
 * all of it is a JVM test. `AGENTS.md` puts this kind of arithmetic here for the
 * same reason snooze is here: it is domain logic and does not need a device.
 *
 * This absorbed `FocusProgressTest`, which covered a function D-014 removed. The
 * three cases worth keeping from it — overrun, a clock that has gone backwards,
 * and a missing estimate — are asserted below against the session instead.
 *
 * What these cannot check is whether the shape reads as a state at a glance,
 * which is D-014's own reversal condition and is watched on the emulator.
 */
class FocusSessionTest {

    private val start: Instant = Instant.parse("2026-09-08T09:00:00Z")

    private fun at(minutes: Long, seconds: Long = 0L): Instant =
        start.plusSeconds(minutes * 60 + seconds)

    // --- elapsed, and the origin that measures it ----------------------------

    @Test
    fun aRunningSessionMeasuresItselfAgainstTheClock() {
        val session = FocusSession(startedAt = start)

        assertEquals(Duration.ofMinutes(12), session.elapsed(at(12)))
    }

    /**
     * The guarantee `focus.md` insists on: derived, never accumulated. A process
     * frozen for ten minutes has to come back knowing ten minutes went by, which
     * is only true while every reading is worked out from the clock.
     */
    @Test
    fun aRunningSessionDoesNotNeedToHaveBeenWatched() {
        val session = FocusSession(startedAt = start)

        assertEquals(Duration.ofMinutes(45), session.elapsed(at(45)))
    }

    @Test
    fun aPausedSessionStopsCounting() {
        val session = FocusSession(startedAt = start).paused(at(12))

        // Half an hour later it still reads twelve minutes.
        assertEquals(Duration.ofMinutes(12), session.elapsed(at(42)))
    }

    /**
     * The mechanism `focus.md` specifies: **pausing moves the origin rather than
     * starting a tally.** Resuming shifts it forward by the length of the pause,
     * so the single value goes on meaning what it meant before.
     */
    @Test
    fun resumingShiftsTheOriginRatherThanBankingATotal() {
        val session = FocusSession(startedAt = start)
            .paused(at(12))
            .resumed(at(42))

        // Thirty minutes paused, so the origin moved thirty minutes forward.
        assertEquals(start.plus(Duration.ofMinutes(30)), session.startedAt)
        assertNull(session.pausedAt)
    }

    @Test
    fun aSessionPausedAndResumedReportsWhatItHeldWhenItPaused() {
        val paused = FocusSession(startedAt = start).paused(at(12))
        val held = paused.elapsed(at(12))

        val resumed = paused.resumed(at(42))

        // The moment it resumes, before any new work, it reads exactly what it
        // read while stopped. This is the assertion `focus.md` names.
        assertEquals(held, resumed.elapsed(at(42)))
        // And five minutes of real work after that is five minutes more.
        assertEquals(held.plusMinutes(5), resumed.elapsed(at(47)))
    }

    @Test
    fun pausingAndResumingSurviveBeingRepeated() {
        val session = FocusSession(startedAt = start)
            .paused(at(10))
            .paused(at(20))
            .resumed(at(30))
            .resumed(at(40))

        // The second pause banks nothing extra and the second resume does not
        // move the origin again, so this is ten minutes plus the leg from 30.
        assertEquals(Duration.ofMinutes(20), session.elapsed(at(40)))
    }

    /** A clock that has gone backwards reports nothing worked, not a negative. */
    @Test
    fun aClockThatWentBackwardsReportsNothingWorked() {
        val session = FocusSession(startedAt = start)

        assertEquals(Duration.ZERO, session.elapsed(start.minusSeconds(90)))
    }

    /** Nor may it drag the origin back and hand the user time they never worked. */
    @Test
    fun aClockThatWentBackwardsDuringAPauseDoesNotMoveTheOrigin() {
        val session = FocusSession(startedAt = start)
            .paused(at(12))
            .resumed(at(11))

        assertEquals(start, session.startedAt)
    }

    // --- the estimate, and extending it --------------------------------------

    /**
     * The extension is a field rather than a shift of the origin, and this is
     * the assertion that made it one. Moving the origin forward left `elapsed`
     * clamped at its zero floor, so the five minutes were silently eaten and
     * the estimate alarm was rescheduled to the moment it already pointed at.
     *
     * Nothing is written to the task either way: a task's estimate is the
     * user's answer to how long the work takes, and one session running long is
     * not a correction to it.
     */
    @Test
    fun extendingAddsToTheEstimateAndLeavesTheOriginAlone() {
        val session = FocusSession(startedAt = start).extended()

        assertEquals(start, session.startedAt)
        assertEquals(50, session.estimateMinutes(45))
        assertEquals(Duration.ofMinutes(5), session.remaining(at(45), 45))
    }

    /** Twice is ten, and the two presses do not interfere. */
    @Test
    fun extendingTwiceAddsTwice() {
        val session = FocusSession(startedAt = start).extended().extended()

        assertEquals(55, session.estimateMinutes(45))
        assertEquals(Duration.ofMinutes(10), session.remaining(at(45), 45))
    }

    @Test
    fun extendingATaskWithNoEstimateLeavesItOpenEnded() {
        val session = FocusSession(startedAt = start).extended().extended()

        // Nothing to extend. Two presses cannot conjure an estimate out of a
        // task that never had one.
        assertNull(session.estimateMinutes(null))
        assertNull(session.remaining(at(5), null))
        assertEquals(FocusState.OpenEnded, focusStateOf(session, null, at(5)))
    }

    @Test
    fun anEstimateOfZeroIsNotAnEstimate() {
        val session = FocusSession(startedAt = start)

        assertNull(session.remaining(at(5), 0))
        assertNull(session.remaining(at(5), -15))
        assertEquals(FocusState.OpenEnded, focusStateOf(session, 0, at(5)))
    }

    @Test
    fun remainingCountsDown() {
        val session = FocusSession(startedAt = start)

        assertEquals(Duration.ofMinutes(33), session.remaining(at(12), 45))
    }

    /** Running over is ordinary. It reports nothing left, never a negative. */
    @Test
    fun remainingIsFlooredAtZero() {
        val session = FocusSession(startedAt = start)

        assertEquals(Duration.ZERO, session.remaining(at(90), 45))
    }

    // --- what the estimate alarm is aimed at ---------------------------------

    @Test
    fun theEstimateIsReachedAtTheOriginPlusTheEstimate() {
        val session = FocusSession(startedAt = start)

        assertEquals(at(45), session.estimateReachedAt(at(12), 45))
    }

    /**
     * A paused clock has no moment for the estimate to arrive at, and an alarm
     * left pointing at one would fire while the user was deliberately not
     * working.
     */
    @Test
    fun aPausedSessionHasNoMomentToAnnounce() {
        val session = FocusSession(startedAt = start).paused(at(12))

        assertNull(session.estimateReachedAt(at(20), 45))
    }

    @Test
    fun resumingPushesTheAnnouncementOutByTheTimePaused() {
        val session = FocusSession(startedAt = start)
            .paused(at(12))
            .resumed(at(42))

        // Thirty-three minutes were left at the pause, and they are still left.
        assertEquals(at(75), session.estimateReachedAt(at(42), 45))
    }

    @Test
    fun extendingPushesTheAnnouncementOut() {
        val session = FocusSession(startedAt = start).extended()

        assertEquals(at(50), session.estimateReachedAt(at(45), 45))
    }

    @Test
    fun anEstimateAlreadyReachedAnnouncesNothing() {
        val session = FocusSession(startedAt = start)

        assertNull(session.estimateReachedAt(at(50), 45))
    }

    @Test
    fun aTaskWithNoEstimateAnnouncesNothing() {
        val session = FocusSession(startedAt = start)

        assertNull(session.estimateReachedAt(at(12), null))
    }

    // --- the five states -----------------------------------------------------

    /**
     * **There were six, and `noSessionIsReady` tested the one that went.** A null
     * session meant Ready, and D-054 removed both the state and the only path that
     * produced one. `focusStateOf` no longer takes a nullable session, so that test
     * cannot be written any more: the compiler rejects it, which is the point.
     */
    @Test
    fun theFiveStatesAreTheFiveTheDesignDraws() {
        val session = FocusSession(startedAt = start)

        assertEquals(FocusState.Running, focusStateOf(session, 45, at(12)))
        assertEquals(FocusState.Paused, focusStateOf(session.paused(at(12)), 45, at(20)))
        assertEquals(FocusState.EstimateReached, focusStateOf(session, 45, at(45)))
        assertEquals(FocusState.OpenEnded, focusStateOf(session, null, at(12)))
        assertEquals(
            FocusState.OpenEndedPaused,
            focusStateOf(session.paused(at(12)), null, at(20))
        )
    }

    /**
     * D-014: the shape says whether the clock is running, and nothing else. Three
     * states are running, including Estimate reached, which is still counting
     * even though it has nothing left to count down.
     */
    @Test
    fun theShapeFollowsWhetherTheClockIsRunning() {
        assertTrue(FocusState.Running.isClockRunning)
        assertFalse(FocusState.Paused.isClockRunning)
        assertTrue(FocusState.EstimateReached.isClockRunning)
        assertTrue(FocusState.OpenEnded.isClockRunning)
        assertFalse(FocusState.OpenEndedPaused.isClockRunning)
    }

    /**
     * Paused is asked before the estimate, so a session paused with a minute to
     * go and left overnight is still Paused rather than Estimate reached.
     * Offering +5 min on a stopped clock would extend something that is not
     * advancing, and the user pressing Resume would find the extra five spent.
     */
    @Test
    fun aPausedSessionStaysPausedPastItsEstimate() {
        val session = FocusSession(startedAt = start).paused(at(44))

        assertEquals(FocusState.Paused, focusStateOf(session, 45, at(600)))
    }

    @Test
    fun extendingLeavesTheEstimateReachedState() {
        val reached = FocusSession(startedAt = start)
        assertEquals(FocusState.EstimateReached, focusStateOf(reached, 45, at(45)))

        val extended = reached.extended()
        assertEquals(FocusState.Running, focusStateOf(extended, 45, at(45)))
        // And arrives again five minutes later.
        assertEquals(FocusState.EstimateReached, focusStateOf(extended, 45, at(50)))
    }

    // --- the readout ---------------------------------------------------------

    @Test
    fun aTimedSessionCountsDown() {
        val session = FocusSession(startedAt = start)

        assertEquals("44:37", focusReadout(session, 45, at(0, 23)))
    }

    @Test
    fun anOpenEndedSessionCountsUp() {
        val session = FocusSession(startedAt = start)

        assertEquals("12:43", focusReadout(session, null, at(12, 43)))
    }

    /**
     * **Two tests were here and D-054 removed them.** A null session read the whole
     * estimate, or "00:00" with no estimate, and both were describing Ready. The
     * parameter is not nullable any more, so neither case can be expressed. A
     * session that has just started reads the same "45:00" and is covered above.
     */

    @Test
    fun theEstimateReachedStateReadsZero() {
        val session = FocusSession(startedAt = start)

        assertEquals("00:00", focusReadout(session, 45, at(45)))
        // And stays at zero rather than going negative.
        assertEquals("00:00", focusReadout(session, 45, at(90)))
    }

    @Test
    fun aPausedReadoutIsFrozen() {
        val session = FocusSession(startedAt = start).paused(at(12, 42))

        assertEquals("32:18", focusReadout(session, 45, at(12, 42)))
        // An hour later it still reads the same thing.
        assertEquals("32:18", focusReadout(session, 45, at(72, 42)))
    }

    /**
     * Minutes are not wrapped at sixty. A Focus session is not long enough for
     * hours to be worth the punctuation, and the two-field form stays one width.
     */
    @Test
    fun minutesDoNotWrapAtAnHour() {
        val session = FocusSession(startedAt = start)

        assertEquals("90:00", focusReadout(session, 90, start))
        assertEquals("61:00", focusReadout(session, null, at(61)))
    }
}
