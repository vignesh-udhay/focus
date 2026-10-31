package app.tellcat.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tellcat.core.domain.ChecklistItem
import app.tellcat.core.domain.RecurrenceUnit
import app.tellcat.core.domain.withDone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Exercises [TaskDao] against a real in-memory Room database, so the entity,
 * the converters, and the generated SQL are all verified together.
 *
 * This is an instrumented test because Room needs an Android context and a
 * real SQLite implementation. It cannot run as a plain JVM unit test without
 * adding Robolectric or the bundled SQLite driver.
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var database: TellcatDatabase
    private lateinit var dao: TaskDao

    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")
    private val completedAt: Instant = Instant.parse("2026-01-02T17:30:00Z")
    private val deletedAt: Instant = Instant.parse("2026-01-03T08:15:00Z")
    private val scheduled: LocalDate = LocalDate.of(2026, 8, 31)
    private val due: LocalDate = LocalDate.of(2026, 9, 4)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TellcatDatabase::class.java
        ).build()
        dao = database.taskDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        id: String,
        title: String = "Task $id",
        notes: String? = null,
        scheduledDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        reminderAt: LocalDateTime? = null,
        reminderDeliveredAt: Instant? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: RecurrenceUnit? = null,
        spawnedFromId: String? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null,
        createdAt: Instant = this@TaskDaoTest.createdAt
    ) = TaskEntity(
        id = id,
        title = title,
        notes = notes,
        createdAt = createdAt,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt,
        snoozedUntil = null,
        checklist = null,
        estimatedDurationMinutes = estimatedDurationMinutes,
        recurrence = recurrence,
        recurrenceInterval = null,
        recurrenceWeekdays = null,
        recurrenceEndDate = null,
        recurrenceEndCount = null,
        occurrenceNumber = 1,
        spawnedFromId = spawnedFromId,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    private suspend fun observed(): List<TaskEntity> =
        dao.observeTasks().first().sortedBy { it.id }

    // D-063: the point query, and the two writes that have to be atomic

    @Test
    fun findTaskReturnsOneRowRatherThanTheWholeTable() = runBlocking {
        dao.insert(entity("a"))
        dao.insert(entity("b"))

        assertEquals("b", dao.findTask("b")?.id)
        assertNull(dao.findTask("missing"))
    }

    /**
     * The same live-rows filter `observeTasks` applies. Every write path used to
     * read that filtered stream, so a point query that saw the trash would show
     * callers rows they had never been able to see.
     */
    @Test
    fun findTaskIgnoresASoftDeletedRow() = runBlocking {
        dao.insert(entity("a", deletedAt = deletedAt))

        assertNull(dao.findTask("a"))
    }

    /**
     * `PRODUCT.md`: "Completing one occurrence must produce the next one." Two
     * separate writes could not promise that, and a recurring task completed
     * without its successor is a reminder silently lost.
     */
    @Test
    fun completeWithNextWritesBothTheCompletionAndTheOccurrence() = runBlocking {
        dao.insert(entity("a", recurrence = RecurrenceUnit.DAILY))

        val completed = dao.findTask("a")!!.copy(completedAt = completedAt)
        dao.completeWithNext(
            completed = completed,
            next = entity("a-next", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "a")
        )

        assertEquals(completedAt, dao.findTask("a")?.completedAt)
        assertEquals("a", dao.findTask("a-next")?.spawnedFromId)
    }

    /** A task that does not recur has no second write to make. */
    @Test
    fun completeWithNextAcceptsNoOccurrence() = runBlocking {
        dao.insert(entity("a"))

        dao.completeWithNext(completed = dao.findTask("a")!!.copy(completedAt = completedAt), next = null)

        assertEquals(completedAt, dao.findTask("a")?.completedAt)
        assertEquals(1, observed().size)
    }

    /** The mirror, and it loses the occurrence in the other direction. */
    @Test
    fun reopenWithoutSpawnRemovesTheOccurrenceAndClearsTheCompletion() = runBlocking {
        dao.insert(entity("a", completedAt = completedAt, recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("a-next", spawnedFromId = "a", recurrence = RecurrenceUnit.DAILY))

        dao.reopenWithoutSpawn(
            reopened = dao.findTask("a")!!.copy(completedAt = null),
            spawnId = "a-next"
        )

        assertNull(dao.findTask("a")?.completedAt)
        assertNull(dao.findTask("a-next"))
    }

    @Test
    fun findSpawnsOfReturnsOnlyTheOccurrencesOfThatParent() = runBlocking {
        dao.insert(entity("a"))
        dao.insert(entity("a-next", spawnedFromId = "a"))
        dao.insert(entity("b-next", spawnedFromId = "b"))
        dao.insert(entity("a-binned", spawnedFromId = "a", deletedAt = deletedAt))

        assertEquals(listOf("a-next"), dao.findSpawnsOf("a").map { it.id })
    }

    // 1

    @Test
    fun insertThenObserveReturnsTheTask() = runBlocking {
        val task = entity(id = "a", title = "Finish the landing page")

        dao.insert(task)

        assertEquals(listOf(task), observed())
    }

    // 2

    @Test
    fun insertingMultipleTasksPreservesEveryRow() = runBlocking {
        val a = entity(id = "a")
        val b = entity(id = "b")
        val c = entity(id = "c")

        dao.insert(a)
        dao.insert(b)
        dao.insert(c)

        assertEquals(listOf(a, b, c), observed())
    }

    // 3

    @Test
    fun updateChangesTheStoredTask() = runBlocking {
        val original = entity(id = "a", title = "Draft the brief")
        dao.insert(original)

        val edited = original.copy(
            title = "Draft the brief and send it",
            estimatedDurationMinutes = 30
        )
        dao.update(edited)

        assertEquals(listOf(edited), observed())
    }

    // 4

    @Test
    fun insertingAnExistingIdReplacesTheRow() = runBlocking {
        dao.insert(entity(id = "a", title = "First version"))
        val replacement = entity(id = "a", title = "Second version")

        dao.insert(replacement)

        assertEquals(listOf(replacement), observed())
    }

    // 5

    @Test
    fun softDeleteSetsDeletedAt() = runBlocking {
        dao.insert(entity(id = "a"))
        dao.insert(entity(id = "b"))

        dao.softDelete(id = "a", deletedAt = deletedAt.toEpochMilli())

        // The row is gone from the live stream, which is only possible if
        // deletedAt became non-null.
        assertEquals(listOf("b"), observed().map { it.id })
        assertEquals(deletedAt.toEpochMilli(), storedDeletedAt("a"))
    }

    /** Reads the raw column, since the DAO deliberately exposes no deleted rows. */
    private fun storedDeletedAt(id: String): Long? {
        database.query("SELECT deletedAt FROM tasks WHERE id = ?", arrayOf<Any?>(id)).use { cursor ->
            cursor.moveToFirst()
            return if (cursor.isNull(0)) null else cursor.getLong(0)
        }
    }

    // 6

    @Test
    fun observeTasksExcludesSoftDeletedTasks() = runBlocking {
        dao.insert(entity(id = "a"))
        dao.insert(entity(id = "b", deletedAt = deletedAt))

        assertEquals(listOf("a"), observed().map { it.id })
    }

    @Test
    fun aSoftDeletedTaskCanBeRestored() = runBlocking {
        val task = entity(id = "a", title = "Chase the missing invoice")
        dao.insert(task)

        dao.softDelete(id = "a", deletedAt = deletedAt.toEpochMilli())
        assertEquals(emptyList<String>(), observed().map { it.id })
        assertEquals(deletedAt.toEpochMilli(), storedDeletedAt("a"))

        dao.restore(id = "a")

        assertEquals(listOf(task), observed())
        assertNull(storedDeletedAt("a"))
    }

    // 7

    @Test
    fun aCompletedTaskRemainsObservable() = runBlocking {
        val done = entity(id = "a", completedAt = completedAt)

        dao.insert(done)

        assertEquals(listOf(done), observed())
        assertEquals(completedAt, observed().single().completedAt)
    }

    // 8

    @Test
    fun scheduledDatePersistsThroughTheDao() = runBlocking {
        dao.insert(entity(id = "a", scheduledDate = scheduled))

        assertEquals(scheduled, observed().single().scheduledDate)
    }

    @Test
    fun datesBeforeTheEpochPersistThroughTheDao() = runBlocking {
        val early = LocalDate.of(1969, 12, 31)
        dao.insert(entity(id = "a", scheduledDate = early, dueDate = early))

        val stored = observed().single()
        assertEquals(early, stored.scheduledDate)
        assertEquals(early, stored.dueDate)
    }

    // 9

    @Test
    fun nullableFieldsSurviveTheDatabaseRoundTrip() = runBlocking {
        val sparse = entity(id = "a")

        dao.insert(sparse)

        val stored = observed().single()
        assertNull(stored.scheduledDate)
        assertNull(stored.dueDate)
        assertNull(stored.estimatedDurationMinutes)
        assertNull(stored.completedAt)
        assertNull(stored.deletedAt)
        assertEquals(sparse, stored)
    }

    @Test
    fun populatedNullableFieldsSurviveTheDatabaseRoundTrip() = runBlocking {
        val full = entity(
            id = "a",
            scheduledDate = scheduled,
            dueDate = due,
            estimatedDurationMinutes = 45,
            completedAt = completedAt
        )

        dao.insert(full)

        assertEquals(full, observed().single())
    }

    @Test
    fun createdAtSurvivesTheDatabaseRoundTrip() = runBlocking {
        dao.insert(entity(id = "a"))

        assertEquals(createdAt, observed().single().createdAt)
    }

    @Test
    fun notesSurviveTheDatabaseRoundTrip() = runBlocking {
        dao.insert(entity(id = "a", notes = "Ask Priya which logo to use"))

        assertEquals("Ask Priya which logo to use", observed().single().notes)
    }

    @Test
    fun anAbsentNoteStaysNull() = runBlocking {
        dao.insert(entity(id = "a"))

        assertNull(observed().single().notes)
    }

    // The ordering contract

    /**
     * The stream is ordered oldest first, whatever order the rows went in.
     *
     * `TaskQueries` sorts each view for itself, but its sorts are stable, so
     * the order they are handed decides how ties come out. Focus reads the
     * head of its queue, which makes that order user-visible.
     */
    @Test
    fun observeTasksReturnsOldestFirst() = runBlocking {
        dao.insert(entity(id = "third", createdAt = createdAt.plusSeconds(120)))
        dao.insert(entity(id = "first", createdAt = createdAt))
        dao.insert(entity(id = "second", createdAt = createdAt.plusSeconds(60)))

        val ids = dao.observeTasks().first().map { it.id }

        assertEquals(listOf("first", "second", "third"), ids)
    }

    // The link between a recurring task and its successor

    /**
     * The column that lets undo find the occurrence a completion spawned.
     *
     * Every other test here leaves it null, so without this one the table
     * could be storing the id and handing back nothing and the suite would
     * still be green. That is how it was added in the first place: the column
     * reached the entity and the mappers, and this file was never taught the
     * field exists.
     */
    @Test
    fun theSpawningTaskSurvivesTheDatabaseRoundTrip() = runBlocking {
        dao.insert(entity(id = "next", spawnedFromId = "original"))

        assertEquals("original", observed().single().spawnedFromId)
    }

    /** A task somebody typed was spawned by nothing, and says so. */
    @Test
    fun aTaskWithNoOriginStaysNull() = runBlocking {
        dao.insert(entity(id = "a"))

        assertNull(observed().single().spawnedFromId)
    }

    // The ordering contract, continued

    /** Two tasks captured in the same millisecond still have one settled order. */
    @Test
    fun observeTasksBreaksEqualCreationTimesById() = runBlocking {
        dao.insert(entity(id = "c", createdAt = createdAt))
        dao.insert(entity(id = "a", createdAt = createdAt))
        dao.insert(entity(id = "b", createdAt = createdAt))

        val ids = dao.observeTasks().first().map { it.id }

        assertEquals(listOf("a", "b", "c"), ids)
    }

    /**
     * Many ticks on different items, launched at once, all land. Each is a
     * read and a write, and without the transaction a later write put back
     * the list from before an earlier one.
     */
    @Test
    fun concurrentChecklistChangesAllLand() = runBlocking {
        val items = (1..20).map { n -> ChecklistItem("Item $n") }
        dao.insert(entity("a").copy(checklist = items))

        items.indices.map { index ->
            async(Dispatchers.IO) {
                dao.changeChecklist("a") { checklist ->
                    checklist.withDone(index, "Item ${index + 1}", done = true)
                }
            }
        }.awaitAll()

        assertEquals(items.map { it.copy(done = true) }, dao.findTask("a")!!.checklist)
    }

    @Test
    fun aChecklistChangeOnADeletedTaskWritesNothing() = runBlocking {
        dao.insert(entity("a", deletedAt = deletedAt).copy(checklist = listOf(ChecklistItem("Milk"))))

        dao.changeChecklist("a") { emptyList() }

        assertEquals(
            listOf(ChecklistItem("Milk")),
            database.query("SELECT checklist FROM tasks", null).use { cursor ->
                cursor.moveToFirst()
                TaskConverters.linesToChecklist(cursor.getString(0))
            }
        )
    }

    // D-098: an occurrence makes its successor once

    @Test
    fun countSpawnsOfIncludesASuccessorTheUserDeleted() = runBlocking {
        dao.insert(entity("mon", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("tue", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon", deletedAt = deletedAt))

        assertEquals(1, dao.countSpawnsOf("mon"))
        assertEquals(0, dao.findSpawnsOf("mon").size)
    }

    @Test
    fun recordAnnouncedMarksToldStartsTheNextAndRetiresTheMissedOneTogether() = runBlocking {
        val told = Instant.parse("2026-09-01T03:30:04Z")
        dao.insert(entity("sun", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("mon", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "sun"))

        dao.recordAnnounced(
            id = "mon",
            deliveredAt = told.toEpochMilli(),
            next = entity("tue", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"),
            retireId = "sun"
        )

        assertEquals(told, dao.findTask("mon")?.reminderDeliveredAt)
        assertEquals("mon", dao.findTask("tue")?.spawnedFromId)
        assertNull("The missed occurrence is still open", dao.findTask("sun"))
    }

    @Test
    fun recordAnnouncedAddsNoSecondSuccessor() = runBlocking {
        dao.insert(entity("mon", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("tue", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"))

        dao.recordAnnounced(
            id = "mon",
            deliveredAt = createdAt.toEpochMilli(),
            next = entity("tue-again", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"),
            retireId = null
        )

        assertEquals(listOf("tue"), dao.findSpawnsOf("mon").map { it.id })
    }

    // D-108: the follow-up, written with the announcement and cleared by the answers

    private val followUp: LocalDateTime = LocalDateTime.of(2026, 9, 1, 9, 10)

    @Test
    fun recordAnnouncedWritesTheFollowUpInTheSameTransaction() = runBlocking {
        dao.insert(entity("mon"))

        dao.recordAnnounced(
            id = "mon",
            deliveredAt = createdAt.toEpochMilli(),
            next = null,
            retireId = null,
            followUpAt = followUp.toString()
        )

        assertEquals(followUp, dao.findTask("mon")?.followUpAt)
    }

    @Test
    fun setFollowUpClearsIt() = runBlocking {
        dao.insert(entity("mon"))
        dao.setFollowUp("mon", followUp.toString())

        dao.setFollowUp("mon", null)

        assertNull(dao.findTask("mon")?.followUpAt)
    }

    @Test
    fun snoozingClearsTheFollowUp() = runBlocking {
        dao.insert(entity("mon"))
        dao.setFollowUp("mon", followUp.toString())

        dao.snoozeReminder("mon", followUp.plusHours(1).toString())

        assertNull(dao.findTask("mon")?.followUpAt)
    }

    @Test
    fun deletingClearsTheFollowUp() = runBlocking {
        dao.insert(entity("mon"))
        dao.setFollowUp("mon", followUp.toString())

        dao.softDelete("mon", deletedAt.toEpochMilli())
        dao.restore("mon")

        assertNull("Undoing the delete brought the follow-up back", dao.findTask("mon")?.followUpAt)
    }

    @Test
    fun completingAnOccurrenceThatAlreadyHasItsSuccessorWritesNoOther() = runBlocking {
        dao.insert(entity("mon", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("tue", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"))

        val started = dao.completeWithNext(
            completed = entity("mon", recurrence = RecurrenceUnit.DAILY, completedAt = completedAt),
            next = entity("tue-again", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon")
        )

        assertEquals(false, started)
        assertEquals(completedAt, dao.findTask("mon")?.completedAt)
        assertEquals(listOf("tue"), dao.findSpawnsOf("mon").map { it.id })
    }

    @Test
    fun editWithSuccessorWritesTheEditAndWhatEachSuccessorBecomes() = runBlocking {
        dao.insert(entity("mon", title = "Pills", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("tue", title = "Pills", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"))

        val before = dao.editWithSuccessor(
            id = "mon",
            edit = { row -> row.copy(title = "Blue pills") },
            follow = { _, after, successor -> successor.copy(title = after.title) }
        )

        assertEquals("Pills", before?.title)
        assertEquals("Blue pills", dao.findTask("mon")?.title)
        assertEquals("Blue pills", dao.findTask("tue")?.title)
    }

    @Test
    fun editWithSuccessorErasesASuccessorTheSeriesNoLongerMakes() = runBlocking {
        dao.insert(entity("mon", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("tue", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"))

        dao.editWithSuccessor(
            id = "mon",
            edit = { row -> row.copy(recurrence = null) },
            follow = { _, _, _ -> null }
        )

        assertNull(dao.findTask("mon")?.recurrence)
        assertEquals(0, dao.countSpawnsOf("mon"))
    }

    @Test
    fun editWithSuccessorWritesNothingWhenTheEditDeclines() = runBlocking {
        dao.insert(entity("mon", title = "Pills", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("tue", title = "Pills", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"))

        val before = dao.editWithSuccessor(
            id = "mon",
            edit = { null },
            follow = { _, _, _ -> null }
        )

        assertNull(before)
        assertEquals("Pills", dao.findTask("tue")?.title)
    }

    @Test
    fun softDeleteWithSuccessorsTakesOnlyTheOnesItIsToldToAndRestoreAllBringsThemBack() = runBlocking {
        dao.insert(entity("mon", recurrence = RecurrenceUnit.DAILY))
        dao.insert(entity("tue", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"))
        dao.insert(entity("tue-edited", recurrence = RecurrenceUnit.DAILY, spawnedFromId = "mon"))

        val gone = dao.softDeleteWithSuccessors("mon", deletedAt.toEpochMilli()) { _, successor ->
            successor.id == "tue"
        }

        assertEquals(listOf("tue"), gone)
        assertNull(dao.findTask("mon"))
        assertNull(dao.findTask("tue"))
        assertEquals("tue-edited", dao.findTask("tue-edited")?.id)

        dao.restoreAll(listOf("mon") + gone!!)

        assertEquals(listOf("mon", "tue", "tue-edited"), observed().map { it.id })
    }

    @Test
    fun softDeleteWithSuccessorsDeletesNothingWithoutALiveRow() = runBlocking {
        assertNull(dao.softDeleteWithSuccessors("missing", deletedAt.toEpochMilli()) { _, _ -> true })
    }

    // D-099: a deleted task's alarm

    @Test
    fun findDeletedWithRemindersReturnsOnlyDeletedRowsThatHadOne() = runBlocking {
        val nine = LocalDateTime.of(2026, 9, 1, 9, 0)
        dao.insert(entity("live", reminderAt = nine))
        dao.insert(entity("gone", reminderAt = nine, deletedAt = deletedAt))
        dao.insert(entity("gone-quiet", deletedAt = deletedAt))

        assertEquals(listOf("gone"), dao.findDeletedWithReminders().map { it.id })
    }

    // --- D-115: the early reminder -------------------------------------------

    @Test
    fun markEarlyDeliveredWritesOnlyThatColumn() = runBlocking {
        dao.insert(entity("mon").copy(earlyReminderMinutes = 60))
        val told = java.time.Instant.parse("2026-09-01T03:00:00Z")

        dao.markEarlyDelivered("mon", told.toEpochMilli())

        val stored = dao.findTask("mon")
        assertEquals(told, stored?.earlyDeliveredAt)
        assertEquals(60L, stored?.earlyReminderMinutes)
    }

    @Test
    fun snoozingTheEarlyReminderOwesItAgainAndLeavesTheReminder() = runBlocking {
        dao.insert(entity("mon").copy(earlyReminderMinutes = 60, earlyDeliveredAt = java.time.Instant.EPOCH))
        val before = dao.findTask("mon")
        val until = followUp.minusMinutes(30)

        dao.snoozeEarlyReminder("mon", until.toString())

        val stored = dao.findTask("mon")
        assertEquals(until, stored?.earlySnoozedUntil)
        assertNull(stored?.earlyDeliveredAt)
        assertEquals(before?.reminderAt, stored?.reminderAt)
        assertEquals(before?.snoozedUntil, stored?.snoozedUntil)
    }
}
