package app.tellcat.core.domain

import app.tellcat.core.time.CurrentDay
import app.tellcat.data.local.toDomain
import app.tellcat.data.local.toEntity
import app.tellcat.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * `docs/decisions.md` D-125: which reminders a restart puts back in the shade.
 *
 * Driven through the real writes, over [ListTaskDao]: the announcement, the
 * follow-up being posted, and each answer, so what is under test is the column
 * those writes leave behind as well as the rule that reads it.
 */
class ShadeRestoreTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val monday: LocalDate = LocalDate.of(2026, 11, 2)
    private val nine: LocalDateTime = monday.atTime(9, 30)
    private val toldAt: Instant = Instant.parse("2026-11-02T09:30:04Z")

    /** The phone restarted that night, and the receiver ran a minute later. */
    private val bootedAt: Instant = Instant.parse("2026-11-02T23:00:00Z")
    private val afterBoot: LocalDateTime = LocalDateTime.of(2026, 11, 2, 23, 1)

    private val dao = ListTaskDao()
    private val repository = TaskRepository(dao)

    private var nextId = 0
    private val announcement = ReminderAnnouncement(repository, newId = { "next-${++nextId}" }, zone = { zone })

    private fun dentist(id: String = "dentist", recurrence: Recurrence? = null, ringsLikeAlarm: Boolean = false) = Task(
        id = id,
        title = "Call the dentist",
        createdAt = Instant.parse("2026-11-01T08:00:00Z"),
        scheduledDate = monday,
        reminderAt = nine,
        recurrence = recurrence,
        ringsLikeAlarm = ringsLikeAlarm
    )

    private fun store(vararg tasks: Task) {
        dao.rows.value = tasks.map { task -> task.toEntity() }
    }

    /** Without a follow-up unless asked: one owed by now is D-108's, not the restore's. */
    private fun announce(id: String = "dentist", at: Instant = toldAt, remindsAgain: Boolean = false) =
        runBlocking { announcement.record(id, at, remindsAgain) }

    /** Every live task, as the recovery receiver reads them. */
    private fun live(): List<Task> =
        dao.rows.value.map { row -> row.toDomain() }.filterNot { task -> task.isDeleted }

    private fun restored(now: LocalDateTime = afterBoot, booted: Instant = bootedAt) =
        remindersToRestore(live(), booted, now)

    // Put back

    @Test
    fun `an announced reminder nobody answered comes back as itself`() {
        store(dentist())
        announce()

        val back = restored().single()
        assertEquals("dentist", back.task.id)
        assertFalse(back.followedUp)
    }

    @Test
    fun `the announcement is what puts it in the shade`() {
        store(dentist())
        announce()

        assertEquals(toldAt, live().single().inShadeSince)
    }

    @Test
    fun `a reminder that was followed up comes back as the follow-up`() {
        store(dentist())
        announce(remindsAgain = true)
        runBlocking { repository.recordFollowUpPosted("dentist", toldAt.plusSeconds(600)) }

        val back = restored().single()
        assertTrue(back.followedUp)
        assertNull("The follow-up is not owed again", back.task.pendingFollowUp)
    }

    /** D-102: it comes back, and the receiver posts it, never rings it. */
    @Test
    fun `an alarm that rang comes back as a reminder`() {
        store(dentist(ringsLikeAlarm = true))
        announce()

        assertFalse(restored().single().followedUp)
    }

    /** "No age limit": a week-old reminder still unanswered is still there. */
    @Test
    fun `however old it is`() {
        store(dentist())
        announce()

        val weekLater = bootedAt.plusSeconds(7 * 86_400)
        assertEquals(1, restored(now = afterBoot.plusDays(7), booted = weekLater).size)
    }

    @Test
    fun `several come back in the order they were first posted`() {
        store(dentist("later"), dentist("earlier"))
        announce("later", at = toldAt.plusSeconds(3_600))
        announce("earlier")

        assertEquals(listOf("earlier", "later"), restored().map { it.task.id })
    }

    // Not put back

    @Test
    fun `nothing announced, nothing put back`() {
        store(dentist())

        assertTrue(restored().isEmpty())
    }

    @Test
    fun `no tasks, nothing put back`() {
        assertTrue(remindersToRestore(emptyList(), bootedAt, afterBoot).isEmpty())
    }

    @Test
    fun `opening the task answers it`() {
        store(dentist())
        announce()
        runBlocking { repository.answerReminder("dentist") }

        assertTrue(restored().isEmpty())
    }

    @Test
    fun `swiping it away dismisses it, and leaves the follow-up owed`() {
        store(dentist())
        announce(remindsAgain = true)
        runBlocking { repository.dismissFromShade("dentist") }

        assertTrue(restored().isEmpty())
        assertEquals(nine.plusMinutes(10), live().single().pendingFollowUp)
    }

    /** Swiped, then followed up: the follow-up is a new post, and it is in the shade. */
    @Test
    fun `a follow-up posted after a swipe is in the shade again`() {
        store(dentist())
        announce()
        runBlocking {
            repository.dismissFromShade("dentist")
            repository.recordFollowUpPosted("dentist", toldAt.plusSeconds(600))
        }

        assertTrue(restored().single().followedUp)
    }

    @Test
    fun `completing it answers it`() {
        store(dentist())
        announce()
        runBlocking { completion().complete("dentist") }

        assertTrue(restored().isEmpty())
        assertNull(dao.rows.value.single().toDomain().inShadeSince)
    }

    @Test
    fun `deleting it answers it, and undo does not put it back`() {
        store(dentist())
        announce()
        runBlocking {
            repository.softDelete("dentist", toldAt.plusSeconds(60))
            repository.restore("dentist")
        }

        assertTrue(restored().isEmpty())
    }

    @Test
    fun `snoozing it answers it, until the snooze rings`() {
        store(dentist())
        announce()
        runBlocking { repository.snoozeReminder("dentist", nine.plusHours(1)) }

        assertTrue(restored().isEmpty())

        announce(at = toldAt.plusSeconds(3_600))
        assertEquals(toldAt.plusSeconds(3_600), restored().single().task.inShadeSince)
    }

    /** D-127: moving it to today is a new time, which answers it. */
    @Test
    fun `moving it to today answers it`() {
        store(dentist())
        announce()

        val moved = live().single().movedToToday(monday.plusDays(1), afterBoot, bootedAt)
        assertNull(moved.inShadeSince)
    }

    /** D-098: the next announcement retires the missed occurrence, and its notification. */
    @Test
    fun `an occurrence retired by the next one is not put back, the next one is`() {
        store(dentist(recurrence = Recurrence(RecurrenceUnit.DAILY)))
        announce()
        val tuesday = live().single { it.spawnedFromId == "dentist" }
        assertNull("The next occurrence is not in the shade", tuesday.inShadeSince)

        announce(tuesday.id, at = toldAt.plusSeconds(86_400))

        val tuesdayBoot = bootedAt.plusSeconds(86_400)
        assertEquals(
            listOf(tuesday.id),
            restored(now = afterBoot.plusDays(1), booted = tuesdayBoot).map { it.task.id }
        )
        assertNull(dao.rows.value.single { it.id == "dentist" }.inShadeSince)
    }

    /**
     * Told before the unlock, or followed up since the restart: it was posted
     * after the shade was cleared, so it is on screen or the user swiped it.
     */
    @Test
    fun `one posted since the restart is not posted again`() {
        store(dentist())
        announce(at = bootedAt.plusSeconds(30))

        assertTrue(restored().isEmpty())
    }

    /** D-108 posts a follow-up owed by now, with sound; the restore does not add a second. */
    @Test
    fun `a reminder whose follow-up is owed is left to the follow-up`() {
        store(dentist())
        announce(remindsAgain = true)

        val beforeTheFollowUp = nine.plusMinutes(5)
        assertEquals(1, restored(now = beforeTheFollowUp, booted = toldAt.plusSeconds(60)).size)
        assertTrue(restored(now = nine.plusMinutes(10), booted = toldAt.plusSeconds(60)).isEmpty())
    }

    // The day on the line

    /** The line names the moment it was set for, with its day when that was not today. */
    @Test
    fun `the line names the day when it was set for another one`() {
        store(dentist())
        announce()

        val back = restored().single().task
        assertNull("Same night: the time alone", dayToName(back.reminderAt!!, afterBoot.toLocalDate()))
        assertEquals(monday, dayToName(back.reminderAt!!, monday.plusDays(1)))
    }

    private fun completion() = TaskCompletion(
        repository = repository,
        currentDay = object : CurrentDay {
            override val today: StateFlow<LocalDate> = MutableStateFlow(monday)
        },
        clock = { afterBoot.toInstant(ZoneOffset.UTC) },
        newId = { "done-${++nextId}" },
        zone = { zone }
    )
}
