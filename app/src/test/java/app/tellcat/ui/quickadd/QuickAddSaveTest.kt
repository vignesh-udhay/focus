package app.tellcat.ui.quickadd

import app.tellcat.core.domain.EarlyReminder
import app.tellcat.core.domain.PrimaryList
import app.tellcat.core.domain.Recurrence
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.core.domain.splitTrailingCapture
import app.tellcat.ui.task.QuickAddCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** What Add saves from Quick Add over the home screen. D-132. */
class QuickAddSaveTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 16)
    private val morning: LocalDateTime = today.atTime(9, 0)

    private fun capture(text: String, block: QuickAddCapture.() -> QuickAddCapture = { this }) =
        QuickAddCapture(parsed = splitTrailingCapture(text, today)).block()

    @Test
    fun aCaptureWithNoDay_goesToToday() {
        val save = quickAddSave(capture("Buy milk"), today, morning)!!

        assertEquals("Buy milk", save.task.title)
        assertEquals(today, save.task.scheduledDate)
        assertNull(save.task.reminderAt)
        assertEquals(PrimaryList.TODAY, save.landed)
    }

    @Test
    fun aCaptureForTomorrow_goesToUpcoming() {
        val save = quickAddSave(capture("Call Sam tomorrow"), today, morning)!!

        assertEquals("Call Sam", save.task.title)
        assertEquals(today.plusDays(1), save.task.scheduledDate)
        assertEquals(PrimaryList.UPCOMING, save.landed)
    }

    @Test
    fun aReminderLater_today_isSetAndLandsOnToday() {
        val save = quickAddSave(capture("Gym at 6pm"), today, morning)!!

        assertEquals(today.atTime(18, 0), save.task.reminderAt)
        assertEquals(PrimaryList.TODAY, save.landed)
    }

    /** D-075: a time already gone today rings tomorrow, and the task goes with it. */
    @Test
    fun aReminderAtATimeAlreadyGone_landsOnTheDayItRings() {
        val evening = today.atTime(19, 0)
        val save = quickAddSave(capture("Call mom at 5pm"), today, evening)!!

        assertEquals(today.plusDays(1).atTime(17, 0), save.task.reminderAt)
        assertEquals(PrimaryList.UPCOMING, save.landed)
    }

    @Test
    fun aBlankTitle_savesNothing() {
        assertNull(quickAddSave(capture("   "), today, morning))
    }

    /** What When, Repeat and the checklist hold comes with the title, as in the app. */
    @Test
    fun theToolsComeAlong() {
        val save = quickAddSave(
            capture("Stand-up at 10am") {
                copy(
                    recurrence = Recurrence(RecurrenceUnit.DAILY),
                    checklist = listOf("Notes", "Blockers"),
                    ringsLikeAlarm = true,
                    earlyReminder = EarlyReminder.FifteenMinutes
                )
            },
            today,
            morning
        )!!

        assertNotNull(save.task.recurrence)
        assertEquals(listOf("Notes", "Blockers"), save.task.checklist.map { it.text })
        assertTrue(save.task.ringsLikeAlarm)
        assertEquals(EarlyReminder.FifteenMinutes, save.task.earlyReminder)
    }

    /** D-129: the alarm belongs to the reminder and is dropped without one. */
    @Test
    fun theAlarmWithoutAReminder_isDropped() {
        val save = quickAddSave(
            capture("Buy milk") { copy(ringsLikeAlarm = true, earlyReminder = EarlyReminder.OneHour) },
            today,
            morning
        )!!

        assertFalse(save.task.ringsLikeAlarm)
        assertNull(save.task.earlyReminder)
    }
}
