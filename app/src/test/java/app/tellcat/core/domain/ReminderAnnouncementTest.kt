package app.tellcat.core.domain

import app.tellcat.core.time.CurrentDay
import app.tellcat.data.local.TaskConverters
import app.tellcat.data.local.TaskDao
import app.tellcat.data.local.TaskEntity
import app.tellcat.data.local.toDomain
import app.tellcat.data.local.toEntity
import app.tellcat.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Storage in a list, with the real transactions' rules.
 *
 * The transactions themselves are `TaskDao`'s default bodies, so what is under
 * test here is the code production runs, over rows rather than SQLite.
 * `TaskDaoTest` checks the same writes against Room on a device.
 */
private class ListTaskDao : TaskDao {

    val rows = MutableStateFlow<List<TaskEntity>>(emptyList())

    override fun observeTasks(): Flow<List<TaskEntity>> =
        rows.map { stored -> stored.filter { row -> row.deletedAt == null } }

    override suspend fun findTask(id: String): TaskEntity? =
        rows.value.firstOrNull { row -> row.id == id && row.deletedAt == null }

    override suspend fun findSpawnsOf(parentId: String): List<TaskEntity> =
        rows.value.filter { row -> row.spawnedFromId == parentId && row.deletedAt == null }

    override suspend fun countSpawnsOf(parentId: String): Int =
        rows.value.count { row -> row.spawnedFromId == parentId }

    override suspend fun findDeletedWithReminders(): List<TaskEntity> =
        rows.value.filter { row -> row.deletedAt != null && row.reminderAt != null }

    override suspend fun insert(task: TaskEntity) {
        rows.value = rows.value.filterNot { row -> row.id == task.id } + task
    }

    override suspend fun update(task: TaskEntity) = change(task.id) { task }

    override suspend fun softDelete(id: String, deletedAt: Long) =
        change(id) { row -> row.copy(deletedAt = Instant.ofEpochMilli(deletedAt), followUpAt = null) }

    override suspend fun setFollowUp(id: String, followUpAt: String?) =
        change(id) { row -> row.copy(followUpAt = TaskConverters.textToLocalDateTime(followUpAt)) }

    override suspend fun markEarlyDelivered(id: String, deliveredAt: Long) =
        change(id) { row -> row.copy(earlyDeliveredAt = Instant.ofEpochMilli(deliveredAt)) }

    override suspend fun snoozeEarlyReminder(id: String, until: String) =
        change(id) { row ->
            row.copy(earlySnoozedUntil = TaskConverters.textToLocalDateTime(until), earlyDeliveredAt = null)
        }

    override suspend fun markReminderDelivered(id: String, deliveredAt: Long) =
        change(id) { row -> row.copy(reminderDeliveredAt = Instant.ofEpochMilli(deliveredAt)) }

    override suspend fun snoozeReminder(id: String, snoozedUntil: String) = change(id) { row ->
        row.copy(
            snoozedUntil = TaskConverters.textToLocalDateTime(snoozedUntil),
            reminderDeliveredAt = null,
            followUpAt = null
        )
    }

    override suspend fun setChecklist(id: String, checklist: String?) =
        change(id) { row -> row.copy(checklist = TaskConverters.linesToChecklist(checklist)) }

    override suspend fun restore(id: String) = change(id) { row -> row.copy(deletedAt = null) }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.filterNot { row -> row.id == id }
    }

    private fun change(id: String, edit: (TaskEntity) -> TaskEntity) {
        rows.value = rows.value.map { row -> if (row.id == id) edit(row) else row }
    }
}

/**
 * `docs/decisions.md` D-098: a repeating reminder nobody answers still rings
 * next time.
 *
 * The failure it fixes was found by leaving a daily 5:10 reminder alone on the
 * emulator: nothing was armed for the next day, and nothing ever would be,
 * because only completing an occurrence made the next one. These tests are that
 * walk, without the wait.
 */
class ReminderAnnouncementTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
    private val nine: LocalDateTime = monday.atTime(9, 0)

    /** A few seconds after nine, which is when an exact alarm lands. */
    private val announcedAt: Instant = Instant.parse("2026-09-07T09:00:04Z")

    private val dao = ListTaskDao()
    private val repository = TaskRepository(dao)

    private var nextId = 0
    private val announcement = ReminderAnnouncement(
        repository = repository,
        newId = { "next-${++nextId}" },
        zone = { zone }
    )

    private fun pills(
        id: String = "pills",
        scheduledDate: LocalDate = monday,
        reminderAt: LocalDateTime = nine,
        recurrence: Recurrence? = Recurrence(RecurrenceUnit.DAILY)
    ) = Task(
        id = id,
        title = "Take the pills",
        createdAt = Instant.parse("2026-09-01T08:00:00Z"),
        scheduledDate = scheduledDate,
        reminderAt = reminderAt,
        recurrence = recurrence
    )

    private fun store(vararg tasks: Task) {
        dao.rows.value = tasks.map { task -> task.toEntity() }
    }

    /** Every row, deleted ones included, by id. */
    private fun stored(): Map<String, Task> =
        dao.rows.value.associate { row -> row.id to row.toDomain() }

    private fun spawnsOf(id: String): List<Task> =
        stored().values.filter { task -> task.spawnedFromId == id }

    private fun announce(id: String, at: Instant = announcedAt) =
        runBlocking { announcement.record(id, at) }

    // 1. The bug

    @Test
    fun `an announced daily reminder starts tomorrow's occurrence, ringing at the same time`() {
        store(pills())

        announce("pills")

        val next = spawnsOf("pills").single()
        assertEquals(monday.plusDays(1), next.scheduledDate)
        assertEquals(nine.plusDays(1), next.reminderAt)
        assertNull(next.reminderDeliveredAt)
        assertNull(next.completedAt)
        assertEquals(2, next.occurrenceNumber)
    }

    @Test
    fun `tomorrow's reminder is owed, which is what the scheduler arms`() {
        store(pills())

        announce("pills")

        // What the reconciliation reads: live tasks, an hour after the
        // reminder was announced and ignored.
        val live = stored().values.filterNot { task -> task.isDeleted }
        val owed = pendingReminders(live, nine.plusHours(1))
        assertEquals(listOf(nine.plusDays(1)), owed.map { task -> task.ringsAt })
    }

    @Test
    fun `the announced occurrence stays open, so the miss is seen rather than rolled away`() {
        store(pills())

        announce("pills")

        val announced = stored().getValue("pills")
        assertEquals(announcedAt, announced.reminderDeliveredAt)
        assertNull(announced.completedAt)
        assertNull(announced.deletedAt)
        assertEquals(monday, announced.scheduledDate)
    }

    @Test
    fun `a task that happens once is only marked told`() {
        store(pills(recurrence = null))

        announce("pills")

        assertEquals(listOf("pills"), stored().keys.toList())
        assertEquals(announcedAt, stored().getValue("pills").reminderDeliveredAt)
    }

    // 2. One successor per occurrence

    @Test
    fun `ringing the same occurrence again after a snooze makes no second successor`() {
        store(pills())
        announce("pills")
        runBlocking { repository.snoozeReminder("pills", nine.plusMinutes(10)) }

        announce("pills", at = announcedAt.plusSeconds(600))

        assertEquals(1, spawnsOf("pills").size)
    }

    @Test
    fun `a successor the user deleted is not brought back`() {
        store(pills())
        announce("pills")
        val next = spawnsOf("pills").single()
        runBlocking { repository.softDelete(next.id, announcedAt.plusSeconds(60)) }
        runBlocking { repository.snoozeReminder("pills", nine.plusMinutes(10)) }

        announce("pills", at = announcedAt.plusSeconds(600))

        assertEquals(listOf(next.id), spawnsOf("pills").map { task -> task.id })
    }

    @Test
    fun `completing an announced occurrence late makes no duplicate`() {
        store(pills())
        announce("pills")

        val created = runBlocking { completion(today = monday.plusDays(1)).complete("pills") }

        // Nothing for an undo to take back, and still one Tuesday.
        assertNull(created)
        assertEquals(1, spawnsOf("pills").size)
        assertNotNull(stored().getValue("pills").completedAt)
    }

    @Test
    fun `reopening an announced occurrence keeps the successor its reminder made`() {
        store(pills())
        announce("pills")
        val tasks = completion(today = monday.plusDays(1))
        runBlocking { tasks.complete("pills") }

        runBlocking { tasks.reopen("pills") }

        assertNull(stored().getValue("pills").completedAt)
        assertEquals(1, spawnsOf("pills").size)
    }

    @Test
    fun `completing before the reminder still makes the next occurrence, and reopening takes it back`() {
        store(pills())
        val tasks = completion(today = monday)

        val created = runBlocking { tasks.complete("pills") }
        assertNotNull(created)

        runBlocking { tasks.reopen("pills") }
        assertTrue(spawnsOf("pills").isEmpty())
    }

    // 3. One missed occurrence open, the latest

    @Test
    fun `announcing an occurrence retires the missed one before it`() {
        val tuesday = pills(id = "tue", scheduledDate = monday.plusDays(1), reminderAt = nine.plusDays(1))
            .copy(spawnedFromId = "mon", occurrenceNumber = 2)
        store(pills(id = "mon").copy(reminderDeliveredAt = announcedAt), tuesday)

        announce("tue", at = announcedAt.plusSeconds(86_400))

        assertNotNull("Monday was left open behind Tuesday", stored().getValue("mon").deletedAt)
        assertNull(stored().getValue("tue").deletedAt)
        assertEquals(monday.plusDays(2), spawnsOf("tue").single().scheduledDate)
    }

    @Test
    fun `a completed occurrence before it is left alone`() {
        val done = Instant.parse("2026-09-07T20:00:00Z")
        val tuesday = pills(id = "tue", scheduledDate = monday.plusDays(1), reminderAt = nine.plusDays(1))
            .copy(spawnedFromId = "mon", occurrenceNumber = 2)
        store(pills(id = "mon").copy(completedAt = done), tuesday)

        announce("tue", at = announcedAt.plusSeconds(86_400))

        assertNull(stored().getValue("mon").deletedAt)
        assertEquals(done, stored().getValue("mon").completedAt)
    }

    // 4. The series' arithmetic

    @Test
    fun `a series that has run out makes nothing`() {
        store(
            pills(recurrence = Recurrence(RecurrenceUnit.DAILY, end = RecurrenceEnd.AfterOccurrences(1)))
        )

        announce("pills")

        assertTrue(spawnsOf("pills").isEmpty())
    }

    @Test
    fun `a reminder told late counts the next occurrence from the day it was told`() {
        store(pills())

        // The phone was off from Monday morning until Wednesday at ten, and
        // the missed reminder was announced then.
        announce("pills", at = Instant.parse("2026-09-09T10:00:00Z"))

        val next = spawnsOf("pills").single()
        assertEquals(monday.plusDays(3), next.scheduledDate)
        assertEquals(nine.plusDays(3), next.reminderAt)
    }

    @Test
    fun `a weekly reminder set the day before keeps the gap`() {
        // Due on Friday, told on the Thursday evening before.
        val friday = monday.plusDays(4)
        val thursdayEvening = friday.minusDays(1).atTime(18, 0)
        store(
            pills(
                scheduledDate = friday,
                reminderAt = thursdayEvening,
                recurrence = Recurrence(RecurrenceUnit.WEEKLY)
            )
        )

        announce("pills", at = Instant.parse("2026-09-10T18:00:03Z"))

        val next = spawnsOf("pills").single()
        assertEquals(friday.plusWeeks(1), next.scheduledDate)
        assertEquals(thursdayEvening.plusWeeks(1), next.reminderAt)
    }

    // 5. Edits after the ring reach the next occurrence while it is untouched

    private val edits = OccurrenceEdits(repository, zone = { zone })

    private fun edit(id: String, change: (Task) -> Task) =
        runBlocking { edits.edit(id, change) }

    @Test
    fun `setting Doesn't repeat after the ring stops tomorrow's occurrence`() {
        store(pills())
        announce("pills")

        edit("pills") { task -> task.copy(recurrence = null) }

        assertTrue("Tomorrow's occurrence still rings", spawnsOf("pills").isEmpty())
        assertNull(stored().getValue("pills").recurrence)
    }

    @Test
    fun `a new time after the ring is the time tomorrow`() {
        store(pills())
        announce("pills")
        val next = spawnsOf("pills").single()

        edit("pills") { task -> task.copy(reminderAt = monday.atTime(10, 0), reminderDeliveredAt = null) }

        val followed = spawnsOf("pills").single()
        assertEquals(monday.plusDays(1).atTime(10, 0), followed.reminderAt)
        // The same occurrence, made again: same id, same moment of making, so
        // reopening still knows it came from the reminder.
        assertEquals(next.id, followed.id)
        assertEquals(next.createdAt, followed.createdAt)
    }

    @Test
    fun `a new title after the ring is tomorrow's title`() {
        store(pills())
        announce("pills")

        edit("pills") { task -> task.copy(title = "Take the blue pills") }

        assertEquals("Take the blue pills", spawnsOf("pills").single().title)
    }

    @Test
    fun `a new rule after the ring moves tomorrow's occurrence`() {
        store(pills())
        announce("pills")

        edit("pills") { task -> task.copy(recurrence = Recurrence(RecurrenceUnit.WEEKLY)) }

        assertEquals(monday.plusWeeks(1), spawnsOf("pills").single().scheduledDate)
    }

    @Test
    fun `an item added after the ring is on tomorrow's list, unticked`() {
        store(pills().copy(checklist = listOf(ChecklistItem("Blue"))))
        announce("pills")

        edit("pills") { task -> task.copy(checklist = listOf(ChecklistItem("Blue", done = true), ChecklistItem("Red"))) }

        assertEquals(listOf(ChecklistItem("Blue"), ChecklistItem("Red")), spawnsOf("pills").single().checklist)
    }

    @Test
    fun `an occurrence the user has edited is left alone`() {
        store(pills())
        announce("pills")
        val next = spawnsOf("pills").single()
        edit(next.id) { task -> task.copy(title = "Take the pills, then breakfast") }

        edit("pills") { task -> task.copy(reminderAt = monday.atTime(10, 0), reminderDeliveredAt = null) }

        val kept = spawnsOf("pills").single()
        assertEquals("Take the pills, then breakfast", kept.title)
        assertEquals(nine.plusDays(1), kept.reminderAt)
    }

    @Test
    fun `an occurrence whose reminder has rung is left alone, even when the series stops`() {
        store(pills())
        announce("pills")
        val next = spawnsOf("pills").single()
        // Marked told directly: announcing it would also retire today's,
        // which would leave nothing open to edit.
        runBlocking { repository.markReminderDelivered(next.id, announcedAt.plusSeconds(86_400)) }

        val edited = edit("pills") { task -> task.copy(recurrence = null) }

        assertNotNull("Today's occurrence was not there to edit", edited)
        assertEquals(listOf(next.id), spawnsOf("pills").map { task -> task.id })
    }

    @Test
    fun `an occurrence the user completed is left alone`() {
        store(pills())
        announce("pills")
        val next = spawnsOf("pills").single()
        runBlocking { completion(today = monday).complete(next.id) }

        edit("pills") { task -> task.copy(title = "Take the blue pills") }

        assertEquals("Take the pills", stored().getValue(next.id).title)
    }

    @Test
    fun `ticking today's items leaves tomorrow's occurrence as it was`() {
        store(pills().copy(checklist = listOf(ChecklistItem("Blue"))))
        announce("pills")
        val before = dao.rows.value.single { row -> row.spawnedFromId == "pills" }

        edit("pills") { task -> task.copy(checklist = listOf(ChecklistItem("Blue", done = true))) }

        assertEquals(before, dao.rows.value.single { row -> row.spawnedFromId == "pills" })
    }

    // 6. Deleting after the ring ends the series, as deleting before it does

    private fun delete(id: String) =
        runBlocking { edits.delete(id, deletedAt = announcedAt.plusSeconds(3_600)) }

    @Test
    fun `deleting an occurrence after the ring deletes tomorrow's with it`() {
        store(pills())
        announce("pills")
        val next = spawnsOf("pills").single()

        val gone = delete("pills")

        assertEquals(listOf(next.id), gone)
        assertTrue("Tomorrow's occurrence would still ring", stored().getValue(next.id).isDeleted)
        assertTrue(stored().getValue("pills").isDeleted)
    }

    @Test
    fun `undoing that delete brings both back, and tomorrow's reminder is owed again`() {
        store(pills())
        announce("pills")
        val gone = delete("pills")!!

        runBlocking { repository.restoreAll(listOf("pills") + gone) }

        val live = stored().values.filterNot { task -> task.isDeleted }
        assertEquals(setOf("pills") + gone, live.map { task -> task.id }.toSet())
        assertEquals(
            listOf(nine.plusDays(1)),
            pendingReminders(live, nine.plusHours(2)).map { task -> task.ringsAt }
        )
    }

    @Test
    fun `deleting keeps an occurrence the user has touched`() {
        store(pills())
        announce("pills")
        val next = spawnsOf("pills").single()
        edit(next.id) { task -> task.copy(notes = "With water") }

        val gone = delete("pills")

        assertEquals(emptyList<String>(), gone)
        assertNull(stored().getValue(next.id).deletedAt)
    }

    @Test
    fun `deleting before the ring has no successor to take`() {
        store(pills())

        assertEquals(emptyList<String>(), delete("pills"))
        assertTrue(stored().getValue("pills").isDeleted)
    }

    @Test
    fun `deleting what is not there deletes nothing`() {
        assertNull(delete("missing"))
    }

    // D-108: the one follow-up, set at the announcement and cleared by every answer

    private fun announceRemindingAgain(id: String, at: Instant = announcedAt) =
        runBlocking { announcement.record(id, at, remindsAgain = true) }

    @Test
    fun `a reminder announced as a notification is followed up ten minutes after its minute`() {
        store(pills(recurrence = null))

        announceRemindingAgain("pills")

        // Announced at 9:00:04, so the follow-up is 9:10 on the dot.
        val told = stored().getValue("pills")
        assertEquals(nine.plusMinutes(10), told.followUpAt)
        assertEquals(nine.plusMinutes(10), told.pendingFollowUp)
    }

    @Test
    fun `an announcement that does not remind again sets no follow-up`() {
        store(pills(recurrence = null))

        announce("pills")

        assertNull(stored().getValue("pills").followUpAt)
    }

    @Test
    fun `the next occurrence of a repeat carries no follow-up, and is still made`() {
        store(pills())

        announceRemindingAgain("pills")

        val next = spawnsOf("pills").single()
        assertNull(next.followUpAt)
        assertEquals(nine.plusDays(1), next.reminderAt)
        assertEquals(nine.plusMinutes(10), stored().getValue("pills").pendingFollowUp)
    }

    @Test
    fun `snoozing forgets the follow-up`() {
        store(pills(recurrence = null))
        announceRemindingAgain("pills")

        runBlocking { repository.snoozeReminder("pills", nine.plusHours(1)) }

        val snoozed = stored().getValue("pills")
        assertNull(snoozed.followUpAt)
        assertNull(snoozed.pendingFollowUp)
    }

    @Test
    fun `completing forgets the follow-up, so a reopen cannot bring it back`() {
        store(pills(recurrence = null))
        announceRemindingAgain("pills")

        runBlocking { completion(today = monday).complete("pills") }

        assertNull(stored().getValue("pills").followUpAt)
    }

    @Test
    fun `deleting forgets the follow-up`() {
        store(pills(recurrence = null))
        announceRemindingAgain("pills")

        runBlocking { repository.softDelete("pills", announcedAt.plusSeconds(60)) }

        assertNull(stored().getValue("pills").followUpAt)
    }

    @Test
    fun `opening the task, which clears it, forgets the follow-up`() {
        store(pills(recurrence = null))
        announceRemindingAgain("pills")

        runBlocking { repository.clearFollowUp("pills") }

        assertNull(stored().getValue("pills").pendingFollowUp)
    }

    @Test
    fun `the next occurrence taking over retires the missed one and its follow-up`() {
        store(pills())
        announceRemindingAgain("pills")
        val next = spawnsOf("pills").single()

        // A day later, nobody having answered either.
        announceRemindingAgain(next.id, announcedAt.plusSeconds(24 * 60 * 60))

        val missed = stored().getValue("pills")
        assertTrue(missed.isDeleted)
        assertNull(missed.pendingFollowUp)
        assertEquals(nine.plusDays(1).plusMinutes(10), stored().getValue(next.id).pendingFollowUp)
    }

    private fun completion(today: LocalDate) = TaskCompletion(
        repository = repository,
        currentDay = object : CurrentDay {
            override val today: StateFlow<LocalDate> = MutableStateFlow(today)
        },
        clock = { today.atTime(20, 0).toInstant(ZoneOffset.UTC) },
        newId = { "done-${++nextId}" },
        zone = { zone }
    )
}
