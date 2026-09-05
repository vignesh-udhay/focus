package com.vignesh.focuslist.ui.task

import androidx.lifecycle.SavedStateHandle
import com.vignesh.focuslist.MainDispatcherRule
import com.vignesh.focuslist.core.notification.FocusAlarms
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.upcomingTasks
import com.vignesh.focuslist.core.time.CurrentDay
import com.vignesh.focuslist.data.local.TaskDao
import com.vignesh.focuslist.data.local.TaskConverters
import com.vignesh.focuslist.data.local.TaskEntity
import com.vignesh.focuslist.data.local.toEntity
import com.vignesh.focuslist.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * A hand-written stand-in for the Room-generated DAO.
 *
 * [TaskDao] is an interface, so a real [TaskRepository] can be driven by a fake
 * with no mocking library. That keeps the repository's own mapping in the loop
 * rather than stubbing it out.
 */
private class FakeTaskDao : TaskDao {

    val emissions = MutableStateFlow<List<TaskEntity>>(emptyList())
    val inserted = mutableListOf<TaskEntity>()
    val updated = mutableListOf<TaskEntity>()
    val softDeleted = mutableListOf<Pair<String, Long>>()
    val restored = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    /**
     * Mirrors the real query's `WHERE deletedAt IS NULL`, so a soft-deleted row
     * stays in [emissions] but stops being observed.
     */
    override fun observeTasks(): Flow<List<TaskEntity>> =
        emissions.map { rows -> rows.filter { row -> row.deletedAt == null } }

    /** Stores the row so the flow re-emits, as the real DAO does. */
    override suspend fun insert(task: TaskEntity) {
        inserted += task
        emissions.value = emissions.value + task
    }

    /** Replaces the row so the flow re-emits, as the real DAO does. */
    override suspend fun update(task: TaskEntity) {
        updated += task
        emissions.value = emissions.value.map { stored ->
            if (stored.id == task.id) task else stored
        }
    }

    /** Marks the row rather than removing it, as the real UPDATE does. */
    override suspend fun rescheduleReminder(id: String, reminderAt: String?) {
        emissions.value = emissions.value.map { stored ->
            if (stored.id == id) {
                stored.copy(
                    reminderAt = TaskConverters.textToLocalDateTime(reminderAt),
                    reminderDeliveredAt = null
                )
            } else {
                stored
            }
        }
    }

    override suspend fun markReminderDelivered(id: String, deliveredAt: Long) {
        emissions.value = emissions.value.map { stored ->
            if (stored.id == id) {
                stored.copy(reminderDeliveredAt = Instant.ofEpochMilli(deliveredAt))
            } else {
                stored
            }
        }
    }

    override suspend fun softDelete(id: String, deletedAt: Long) {
        softDeleted += id to deletedAt
        emissions.value = emissions.value.map { stored ->
            if (stored.id == id) {
                stored.copy(deletedAt = Instant.ofEpochMilli(deletedAt))
            } else {
                stored
            }
        }
    }

    /** Clears the stamp so the row is observed again, as the real UPDATE does. */
    override suspend fun restore(id: String) {
        restored += id
        emissions.value = emissions.value.map { stored ->
            if (stored.id == id) stored.copy(deletedAt = null) else stored
        }
    }

    /** Drops the row outright, as the real DELETE does. */
    override suspend fun deleteById(id: String) {
        deleted += id
        emissions.value = emissions.value.filterNot { stored -> stored.id == id }
    }
}

/**
 * A calendar day the test controls.
 *
 * The production day comes from the device and changes at midnight. This one
 * changes when [advanceTo] is called, so rollover is exercised in a
 * millisecond and without touching the clock.
 */
private class FakeCurrentDay(initial: LocalDate) : CurrentDay {

    private val _today = MutableStateFlow(initial)

    override val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun advanceTo(day: LocalDate) {
        _today.value = day
    }
}

/** How long a bounded wait gives the Main dispatcher before giving up. */
private const val TIMEOUT_MILLIS = 2_000L

/** Between polls when waiting on work that reaches a collaborator, not a flow. */
private const val POLL_MILLIS = 10L

/**
 * A JVM test, because nothing here needs a device.
 *
 * [TaskListViewModel] observes through `viewModelScope`, which dispatches on
 * `Dispatchers.Main`, and the JVM has no main dispatcher: the state never
 * emits and the collector blocks. That single gap, not anything Android in the
 * code under test, is what used to run these through an emulator. The DAO, the
 * clock and the alarms are all fakes, and `FocusAlarms` exists precisely so
 * this decision stays testable without a device.
 *
 * [MainDispatcherRule] closes the gap, and the whole class comes off the
 * device: the build no longer has to assemble two APKs, install them, and
 * uninstall them again to answer a question about a Kotlin object.
 */
class TaskListViewModelTest {

    companion object {
        // A class rule, not a per-test one: see MainDispatcherRule.
        @get:ClassRule
        @JvmStatic
        val mainDispatcher = MainDispatcherRule()
    }

    private val today: LocalDate = LocalDate.of(2026, 8, 31)
    private val tomorrow: LocalDate = today.plusDays(1)
    private val createdAt: Instant = Instant.parse("2026-01-01T09:00:00Z")

    /** A reminder on the fixture day, at an hour nothing else in here uses. */
    private val reminder: LocalDateTime = today.atTime(9, 0)
    private val completedAt: Instant = Instant.parse("2026-01-02T17:30:00Z")
    private val deletedAt: Instant = Instant.parse("2026-01-03T08:15:00Z")

    private val dao = FakeTaskDao()
    private val repository = TaskRepository(dao)

    private val currentDay = FakeCurrentDay(today)

    private val alarms = RecordingFocusAlarms()

    private fun viewModel() = TaskListViewModel(repository, currentDay, SavedStateHandle(), alarms)

    private fun task(
        id: String,
        title: String = "Task $id",
        notes: String? = null,
        placement: TaskPlacement = TaskPlacement.ANYTIME,
        scheduledDate: LocalDate? = null,
        dueDate: LocalDate? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: Recurrence? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null,
        reminderAt: LocalDateTime? = null,
        reminderDeliveredAt: Instant? = null,
        createdAt: Instant = this@TaskListViewModelTest.createdAt
    ) = Task(
        id = id,
        title = title,
        createdAt = createdAt,
        notes = notes,
        placement = placement,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        estimatedDurationMinutes = estimatedDurationMinutes,
        recurrence = recurrence,
        completedAt = completedAt,
        deletedAt = deletedAt,
        reminderAt = reminderAt,
        reminderDeliveredAt = reminderDeliveredAt
    )

    private fun store(vararg tasks: Task) {
        dao.emissions.value = tasks.map { it.toEntity() }
    }

    /** The state starts empty, so wait for the derived emission. */
    private fun visible(model: TaskListViewModel, size: Int): List<Task> = runBlocking {
        model.todayTasks.first { it.size == size }
    }

    /** Waits for the toggle coroutine to reach the DAO. */
    private fun awaitUpdate(): TaskEntity {
        repeat(200) {
            dao.updated.firstOrNull()?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("no update reached the DAO")
    }

    // 1

    @Test
    fun repositoryTasksAreExposedAsTodayTasks() {
        store(task(id = "a", scheduledDate = today))

        assertEquals(listOf("a"), visible(viewModel(), 1).map { it.id })
    }

    // 2

    @Test
    fun futureTasksAreExcluded() {
        store(
            task(id = "today", scheduledDate = today),
            task(id = "future", scheduledDate = today.plusDays(1))
        )

        assertEquals(listOf("today"), visible(viewModel(), 1).map { it.id })
    }

    // 3

    @Test
    fun unscheduledTasksAreExcluded() {
        store(
            task(id = "today", scheduledDate = today),
            task(id = "unscheduled", placement = TaskPlacement.INBOX)
        )

        assertEquals(listOf("today"), visible(viewModel(), 1).map { it.id })
    }

    // 4

    @Test
    fun overdueTasksAreIncluded() {
        store(task(id = "overdue", scheduledDate = today.minusDays(3)))

        assertEquals(listOf("overdue"), visible(viewModel(), 1).map { it.id })
    }

    // 5

    @Test
    fun completedTodayTasksRemainVisible() {
        store(task(id = "done", scheduledDate = today, completedAt = completedAt))

        val tasks = visible(viewModel(), 1)

        assertEquals(listOf("done"), tasks.map { it.id })
        assertTrue(tasks.single().isCompleted)
    }

    // 6

    @Test
    fun deletedTasksDoNotAppear() {
        store(
            task(id = "live", scheduledDate = today),
            task(id = "gone", scheduledDate = today, deletedAt = deletedAt)
        )

        assertEquals(listOf("live"), visible(viewModel(), 1).map { it.id })
    }

    // 7

    @Test
    fun repositoryUpdatesFlowThroughToTheExposedState() {
        val model = viewModel()
        store(task(id = "a", scheduledDate = today))
        assertEquals(listOf("a"), visible(model, 1).map { it.id })

        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )

        assertEquals(listOf("a", "b"), visible(model, 2).map { it.id })
    }

    // 8

    @Test
    fun togglingAnIncompleteTaskRecordsACompletionTime() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")

        assertNotNull(awaitUpdate().completedAt)
    }

    // 9

    @Test
    fun togglingACompletedTaskClearsTheCompletionTime() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")

        assertNull(awaitUpdate().completedAt)
    }

    // 10

    @Test
    fun theToggledTaskIsFoundByIdNotByPosition() {
        store(
            task(id = "first", scheduledDate = today),
            task(id = "second", scheduledDate = today),
            task(id = "third", scheduledDate = today)
        )
        val model = viewModel()
        visible(model, 3)

        model.toggleComplete("third")

        assertEquals("third", awaitUpdate().id)
    }

    @Test
    fun togglingAnUnknownIdWritesNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("missing")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
    }

    // 11

    @Test
    fun everyOtherFieldIsUnchangedWhenCompletionIsToggled() {
        val original = task(
            id = "a",
            title = "Finish the landing page",
            scheduledDate = today,
            dueDate = today.plusDays(4),
            estimatedDurationMinutes = 45
        )
        store(original)
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")

        val written = awaitUpdate()
        assertEquals(original.id, written.id)
        assertEquals(original.title, written.title)
        assertEquals(original.placement, written.placement)
        assertEquals(original.createdAt, written.createdAt)
        assertEquals(original.scheduledDate, written.scheduledDate)
        assertEquals(original.dueDate, written.dueDate)
        assertEquals(original.estimatedDurationMinutes, written.estimatedDurationMinutes)
        assertNull(written.deletedAt)
    }

    // 12

    @Test
    fun theOriginalTaskObjectIsNotMutated() {
        val original = task(id = "a", scheduledDate = today)
        store(original)
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")
        awaitUpdate()

        assertNull(original.completedAt)
        assertEquals(false, original.isCompleted)
    }

    // Quick Add

    @Test
    fun creatingATaskPersistsItWithTheExpectedFields() {
        val model = viewModel()

        model.createTask("Buy milk", scheduledDate = today)

        val stored = awaitInsert()
        assertEquals("Buy milk", stored.title)
        assertEquals(TaskPlacement.INBOX, stored.placement)
        assertEquals(today, stored.scheduledDate)
        assertNull(stored.dueDate)
        assertNull(stored.estimatedDurationMinutes)
        assertNull(stored.completedAt)
        assertNull(stored.deletedAt)
        assertNotNull(stored.createdAt)
    }

    @Test
    fun aCreatedTaskGetsAGeneratedUuid() {
        val model = viewModel()

        model.createTask("Buy milk", scheduledDate = today)

        val id = awaitInsert().id
        assertEquals(36, id.length)
        assertEquals(id, UUID.fromString(id).toString())
    }

    /** The screen closes its sheet on a true, so the answer has to be right. */
    @Test
    fun capturingATaskReportsThatItCaptured() {
        assertTrue(viewModel().createTask("Buy milk", scheduledDate = today))
    }

    @Test
    fun aCreatedTaskAppearsInToday() {
        val model = viewModel()
        assertEquals(emptyList<Task>(), model.todayTasks.value)

        model.createTask("Buy milk", scheduledDate = today)

        assertEquals(listOf("Buy milk"), visible(model, 1).map { it.title })
    }

    @Test
    fun theTitleIsTrimmed() {
        viewModel().createTask("   Buy milk   ", scheduledDate = today)

        assertEquals("Buy milk", awaitInsert().title)
    }

    /**
     * Quick Add's visibility is now each screen's own state, but both screens
     * capture through this one view model. Nothing here may remember which
     * screen asked, or Inbox would start dating its captures like Today.
     */
    @Test
    fun capturesFromDifferentScreensDoNotAffectEachOther() {
        val model = viewModel()

        assertTrue(model.createTask("Chase the invoice", scheduledDate = today))
        assertTrue(model.createTask("Find a dentist", scheduledDate = null))

        val stored = awaitInserts(2).associate { it.title to it.scheduledDate }
        assertEquals(today, stored.getValue("Chase the invoice"))
        assertNull(stored.getValue("Find a dentist"))
    }

    /** A false is what keeps the sheet open, so the user can finish typing. */
    @Test
    fun aBlankTitleCreatesNothingAndReportsNoCapture() {
        val model = viewModel()

        assertEquals(false, model.createTask("   ", scheduledDate = today))

        repeat(20) { if (dao.inserted.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.inserted.isEmpty())
    }

    // Completion persists through the stack

    @Test
    fun aToggledCompletionIsEmittedBackThroughTheFlow() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")

        assertNotNull(awaitTodayTask(model) { it.isCompleted }.completedAt)
    }

    @Test
    fun aCompletedTaskRemainsVisibleInTodayAfterCompletion() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")
        awaitTodayTask(model) { it.isCompleted }

        assertEquals(listOf("a"), model.todayTasks.value.map { it.id })
    }

    @Test
    fun reopeningACompletedTaskIsEmittedBackThroughTheFlow() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")

        assertNull(awaitTodayTask(model) { !it.isCompleted }.completedAt)
    }

    @Test
    fun completionSurvivesTheRepositoryAndDaoBoundary() = runBlocking {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")
        val written = awaitUpdate()

        // Read back through the repository, which re-maps entity to domain.
        val reloaded = repository.observeTasks().first().single { it.id == "a" }
        assertTrue(reloaded.isCompleted)
        assertEquals(written.completedAt, reloaded.completedAt)
    }

    @Test
    fun aTaskOutsideTheTodayListCanStillBeToggledById() {
        store(
            task(id = "today", scheduledDate = today),
            task(id = "future", scheduledDate = today.plusDays(5))
        )
        val model = viewModel()
        // The future task is in the repository stream but not the Today list.
        assertEquals(listOf("today"), visible(model, 1).map { it.id })

        model.toggleComplete("future")

        val written = awaitUpdate()
        assertEquals("future", written.id)
        assertNotNull(written.completedAt)
    }

    @Test
    fun aCompletedFutureTaskStillDoesNotAppearInUpcoming() = runBlocking {
        store(task(id = "future", scheduledDate = today.plusDays(5)))
        val model = viewModel()

        model.toggleComplete("future")
        awaitUpdate()

        val stored = repository.observeTasks().first()
        assertTrue(stored.single().isCompleted)
        assertEquals(emptyList<String>(), upcomingTasks(stored, today).map { it.id })
    }

    // Deletion

    @Test
    fun deletingATaskSoftDeletesItById() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()
        visible(model, 2)

        model.deleteTask("b")

        assertEquals("b", awaitSoftDelete().first)
        assertEquals(1, dao.softDeleted.size)
    }

    @Test
    fun aDeletedTaskIsStampedWithADeletionTime() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.deleteTask("a")
        awaitSoftDelete()

        assertNotNull(storedRow("a").deletedAt)
    }

    @Test
    fun aDeletedTaskLeavesTheTodayList() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()
        visible(model, 2)

        model.deleteTask("a")

        assertEquals(listOf("b"), visible(model, 1).map { it.id })
    }

    @Test
    fun theRowSurvivesDeletionSoItCouldBeRestored() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.deleteTask("a")
        awaitSoftDelete()

        // Soft, not hard: the row is still there, only marked.
        assertEquals(1, dao.emissions.value.size)
        assertEquals("a", storedRow("a").id)
    }

    @Test
    fun deletingACompletedTaskKeepsItsCompletionTime() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        visible(model, 1)

        model.deleteTask("a")
        awaitSoftDelete()

        val stored = storedRow("a")
        assertEquals(completedAt, stored.completedAt)
        assertNotNull(stored.deletedAt)
    }

    @Test
    fun deletingAnUnknownIdWritesNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.deleteTask("missing")

        repeat(20) { if (dao.softDeleted.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.softDeleted.isEmpty())
        assertTrue(dao.updated.isEmpty())
        assertEquals(listOf("a"), model.todayTasks.value.map { it.id })
    }

    @Test
    fun deletingOneTaskLeavesTheOthersUntouched() {
        val untouched = task(
            id = "b",
            title = "Chase the missing invoice",
            scheduledDate = today.minusDays(2),
            dueDate = today.plusDays(1),
            estimatedDurationMinutes = 15,
            completedAt = completedAt
        )
        store(task(id = "a", scheduledDate = today), untouched)
        val model = viewModel()
        visible(model, 2)

        model.deleteTask("a")
        awaitSoftDelete()

        assertEquals(untouched.toEntity(), storedRow("b"))
    }

    @Test
    fun theRemainingTasksKeepTheirOrder() {
        store(
            task(id = "first", scheduledDate = today),
            task(id = "second", scheduledDate = today),
            task(id = "third", scheduledDate = today),
            task(id = "fourth", scheduledDate = today)
        )
        val model = viewModel()
        visible(model, 4)

        model.deleteTask("second")

        assertEquals(listOf("first", "third", "fourth"), visible(model, 3).map { it.id })
    }

    @Test
    fun aDeletedTaskDoesNotReappearWhenTheFlowEmitsAgain() = runBlocking {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)

        model.deleteTask("a")
        assertEquals(listOf("b"), visible(model, 1).map { it.id })

        // Another write pushes a fresh emission through the same flow.
        model.createTask("Buy milk", scheduledDate = today)
        val tasks = model.todayTasks.first { it.size == 2 }

        assertEquals(listOf("Task b", "Buy milk"), tasks.map { it.title })
        assertEquals(emptyList<String>(), tasks.filter { it.id == "a" }.map { it.id })
        assertEquals(emptyList<Task>(), repository.observeTasks().first().filter { it.id == "a" })
    }

    @Test
    fun deletionAndCompletionAreIndependent() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)

        model.toggleComplete("a")
        awaitUpdate()
        model.deleteTask("b")
        awaitSoftDelete()

        assertNotNull(storedRow("a").completedAt)
        assertNull(storedRow("a").deletedAt)
        assertNull(storedRow("b").completedAt)
        assertNotNull(storedRow("b").deletedAt)
    }

    // Undo

    @Test
    fun aDeletionOffersAnUndoForThatTask() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        assertNull(model.pendingUndo.value)

        model.deleteTask("b")

        assertEquals(PendingUndo.Deletion("b"), awaitUndoOffer(model))
    }

    @Test
    fun deletingAnUnknownIdOffersNoUndo() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.deleteTask("missing")

        repeat(20) { if (model.pendingUndo.value == null) Thread.sleep(10) }
        assertNull(model.pendingUndo.value)
        assertTrue(dao.softDeleted.isEmpty())
    }

    @Test
    fun undoRestoresTheTaskThroughTheDao() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.deleteTask("a")
        awaitUndoOffer(model)

        model.undoDelete("a")

        assertEquals("a", awaitRestore())
    }

    @Test
    fun anUndoneDeletionClearsTheStoredTimestamp() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.deleteTask("a")
        awaitUndoOffer(model)

        model.undoDelete("a")
        awaitRestore()

        assertNull(storedRow("a").deletedAt)
    }

    @Test
    fun anUndoneDeletionBringsBackTheIdenticalTask() = runBlocking {
        val original = task(
            id = "a",
            title = "Chase the missing invoice",
            placement = TaskPlacement.INBOX,
            scheduledDate = today.minusDays(2),
            dueDate = today.plusDays(3),
            estimatedDurationMinutes = 15
        )
        store(original)
        val model = viewModel()
        visible(model, 1)
        model.deleteTask("a")
        awaitUndoOffer(model)

        model.undoDelete("a")
        awaitRestore()

        // Every field survives the round trip, not just the ones Today reads.
        val reloaded = repository.observeTasks().first().single { it.id == "a" }
        assertEquals(original, reloaded)
    }

    @Test
    fun anUndoneTaskReappearsInToday() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        model.deleteTask("a")
        assertEquals(listOf("b"), visible(model, 1).map { it.id })
        awaitUndoOffer(model)

        model.undoDelete("a")

        assertEquals(listOf("a", "b"), visible(model, 2).map { it.id })
    }

    @Test
    fun anUndoneCompletedTaskIsStillCompleted() = runBlocking {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        visible(model, 1)
        model.deleteTask("a")
        awaitUndoOffer(model)

        model.undoDelete("a")
        awaitRestore()

        val reloaded = repository.observeTasks().first().single { it.id == "a" }
        assertTrue(reloaded.isCompleted)
        assertEquals(completedAt, reloaded.completedAt)
        assertNull(reloaded.deletedAt)
    }

    @Test
    fun anUndoneTaskLandsInItsOrderingBandNotWhereItWas() {
        store(
            task(id = "done", scheduledDate = today, completedAt = completedAt),
            task(id = "overdue", scheduledDate = today.minusDays(2)),
            task(id = "today", scheduledDate = today)
        )
        val model = viewModel()
        assertEquals(listOf("today", "overdue", "done"), visible(model, 3).map { it.id })

        model.deleteTask("overdue")
        assertEquals(listOf("today", "done"), visible(model, 2).map { it.id })
        awaitUndoOffer(model)

        model.undoDelete("overdue")

        // Back between the two bands, not appended to the end of the list.
        assertEquals(listOf("today", "overdue", "done"), visible(model, 3).map { it.id })
    }

    @Test
    fun undoingClearsTheOffer() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.deleteTask("a")
        awaitUndoOffer(model)

        model.undoDelete("a")

        assertNull(model.pendingUndo.value)
    }

    @Test
    fun dismissingTheOfferLeavesTheTaskDeleted() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.deleteTask("a")
        awaitUndoOffer(model)

        model.dismissUndo("a")

        assertNull(model.pendingUndo.value)
        repeat(20) { if (dao.restored.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.restored.isEmpty())
        assertNotNull(storedRow("a").deletedAt)
        assertEquals(emptyList<String>(), model.todayTasks.value.map { it.id })
    }

    @Test
    fun aSecondDeletionBecomesTheUndoTarget() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)

        model.deleteTask("a")
        assertEquals(PendingUndo.Deletion("a"), awaitUndoOffer(model))
        model.deleteTask("b")

        assertEquals(
            PendingUndo.Deletion("b"),
            awaitUndoOffer(model, PendingUndo.Deletion("b"))
        )
        assertEquals(listOf("a", "b"), dao.softDeleted.map { it.first })
    }

    @Test
    fun undoAfterASecondDeletionRestoresTheSecondTask() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        model.deleteTask("a")
        awaitUndoOffer(model)
        model.deleteTask("b")
        awaitUndoOffer(model, PendingUndo.Deletion("b"))

        model.undoDelete("b")
        awaitRestore()

        assertEquals(listOf("b"), dao.restored)
        assertNull(storedRow("b").deletedAt)
        // The superseded deletion stays deleted.
        assertNotNull(storedRow("a").deletedAt)
        assertEquals(listOf("b"), model.todayTasks.value.map { it.id })
    }

    @Test
    fun anUndoForASupersededDeletionDoesNothing() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        model.deleteTask("a")
        awaitUndoOffer(model)
        model.deleteTask("b")
        awaitUndoOffer(model, PendingUndo.Deletion("b"))

        // The offer has moved on to b, so a's undo is stale.
        model.undoDelete("a")

        repeat(20) { if (dao.restored.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.restored.isEmpty())
        assertEquals(PendingUndo.Deletion("b"), model.pendingUndo.value)
    }

    @Test
    fun undoingAnUnknownIdDoesNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.deleteTask("a")
        awaitUndoOffer(model)

        model.undoDelete("missing")

        repeat(20) { if (dao.restored.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.restored.isEmpty())
        // The real offer is untouched.
        assertEquals(PendingUndo.Deletion("a"), model.pendingUndo.value)
    }

    @Test
    fun undoingWithNothingOnOfferDoesNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.undoDelete("a")

        repeat(20) { if (dao.restored.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.restored.isEmpty())
        assertNull(model.pendingUndo.value)
    }

    // Completion undo

    @Test
    fun completingATaskRecordsTheTimeAndOffersAnUndo() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        assertNull(model.pendingUndo.value)

        model.toggleComplete("b")

        assertEquals(PendingUndo.Completion("b"), awaitUndoOffer(model))
        assertNotNull(storedRow("b").completedAt)
    }

    @Test
    fun aCompletedTaskStaysInTodayBeforeUndo() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")
        awaitUndoOffer(model)

        val shown = awaitTodayTask(model) { it.isCompleted }
        assertEquals("a", shown.id)
        assertTrue(shown.isCompleted)
    }

    @Test
    fun undoingACompletionClearsTheCompletionTime() = runBlocking {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.toggleComplete("a")
        awaitUndoOffer(model)

        model.undoComplete("a")

        val reopened = awaitTodayTask(model) { !it.isCompleted }
        assertNull(reopened.completedAt)
        assertNull(repository.observeTasks().first().single { it.id == "a" }.completedAt)
    }

    @Test
    fun undoingACompletionLeavesEveryOtherFieldAlone() = runBlocking {
        val original = task(
            id = "a",
            title = "Chase the missing invoice",
            placement = TaskPlacement.INBOX,
            scheduledDate = today.minusDays(2),
            dueDate = today.plusDays(3),
            estimatedDurationMinutes = 15
        )
        store(original)
        val model = viewModel()
        visible(model, 1)
        model.toggleComplete("a")
        awaitUndoOffer(model)

        model.undoComplete("a")
        awaitTodayTask(model) { !it.isCompleted }

        // Read back through the repository, so the entity boundary is in the loop.
        assertEquals(original, repository.observeTasks().first().single { it.id == "a" })
    }

    @Test
    fun undoingACompletionReturnsATodayTaskToTheTodayBand() {
        store(
            task(id = "done", scheduledDate = today, completedAt = completedAt),
            task(id = "overdue", scheduledDate = today.minusDays(2)),
            task(id = "a", scheduledDate = today)
        )
        val model = viewModel()
        assertEquals(listOf("a", "overdue", "done"), visible(model, 3).map { it.id })

        model.toggleComplete("a")
        awaitUndoOffer(model)
        assertEquals(
            listOf("overdue", "done", "a"),
            awaitTodayIds(model, listOf("overdue", "done", "a"))
        )

        model.undoComplete("a")

        assertEquals(
            listOf("a", "overdue", "done"),
            awaitTodayIds(model, listOf("a", "overdue", "done"))
        )
    }

    @Test
    fun undoingACompletionReturnsAnOverdueTaskToTheOverdueBand() {
        store(
            task(id = "today", scheduledDate = today),
            task(id = "done", scheduledDate = today, completedAt = completedAt),
            task(id = "a", scheduledDate = today.minusDays(4))
        )
        val model = viewModel()
        assertEquals(listOf("today", "a", "done"), visible(model, 3).map { it.id })

        model.toggleComplete("a")
        awaitUndoOffer(model)
        // Completion sinks it below the already-completed task.
        assertEquals(
            listOf("today", "done", "a"),
            awaitTodayIds(model, listOf("today", "done", "a"))
        )

        model.undoComplete("a")

        // Back to overdue, between today's work and what is done.
        assertEquals(
            listOf("today", "a", "done"),
            awaitTodayIds(model, listOf("today", "a", "done"))
        )
    }

    @Test
    fun reopeningATaskByHandRaisesNoNewOffer() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("a")
        awaitTodayTask(model) { !it.isCompleted }

        repeat(20) { if (model.pendingUndo.value == null) Thread.sleep(10) }
        assertNull(model.pendingUndo.value)
    }

    @Test
    fun reopeningATaskByHandWithdrawsItsCompletionOffer() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.toggleComplete("a")
        awaitUndoOffer(model)

        // The user reversed it themselves, so the offer no longer describes it.
        model.toggleComplete("a")
        awaitTodayTask(model) { !it.isCompleted }

        // The list emits during the write, a beat before the offer is withdrawn.
        repeat(20) { if (model.pendingUndo.value != null) Thread.sleep(10) }
        assertNull(model.pendingUndo.value)
    }

    @Test
    fun undoingACompletionAlreadyReversedByHandWritesNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.toggleComplete("a")
        val offer = awaitUndoOffer(model)
        model.toggleComplete("a")
        awaitTodayTask(model) { !it.isCompleted }
        val writes = dao.updated.size

        model.undoComplete((offer as PendingUndo.Completion).taskId)

        repeat(20) { if (dao.updated.size == writes) Thread.sleep(10) }
        assertEquals(writes, dao.updated.size)
        assertNull(storedRow("a").completedAt)
    }

    @Test
    fun completingAnUnknownIdOffersNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("missing")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertNull(model.pendingUndo.value)
    }

    @Test
    fun undoingACompletionForAnUnknownIdDoesNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        model.toggleComplete("a")
        awaitUndoOffer(model)
        val writes = dao.updated.size

        model.undoComplete("missing")

        repeat(20) { if (dao.updated.size == writes) Thread.sleep(10) }
        assertEquals(writes, dao.updated.size)
        assertEquals(PendingUndo.Completion("a"), model.pendingUndo.value)
    }

    @Test
    fun undoingACompletionWithNothingOnOfferDoesNothing() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        visible(model, 1)

        model.undoComplete("a")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertNull(model.pendingUndo.value)
        assertNotNull(storedRow("a").completedAt)
    }

    // One offer at a time, across both actions

    @Test
    fun deletingAfterCompletingSupersedesTheCompletionOffer() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        model.toggleComplete("a")
        assertEquals(PendingUndo.Completion("a"), awaitUndoOffer(model))

        model.deleteTask("b")

        assertEquals(
            PendingUndo.Deletion("b"),
            awaitUndoOffer(model, PendingUndo.Deletion("b"))
        )
    }

    @Test
    fun aSupersededCompletionUndoCannotReopenTheTask() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        model.toggleComplete("a")
        awaitUndoOffer(model)
        model.deleteTask("b")
        awaitUndoOffer(model, PendingUndo.Deletion("b"))
        val writes = dao.updated.size

        model.undoComplete("a")

        repeat(20) { if (dao.updated.size == writes) Thread.sleep(10) }
        assertEquals(writes, dao.updated.size)
        // a stays completed, and the deletion offer is untouched.
        assertNotNull(storedRow("a").completedAt)
        assertEquals(PendingUndo.Deletion("b"), model.pendingUndo.value)
    }

    @Test
    fun theSupersedingDeletionUndoStillRestoresCorrectly() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        model.toggleComplete("a")
        awaitUndoOffer(model)
        model.deleteTask("b")
        awaitUndoOffer(model, PendingUndo.Deletion("b"))

        model.undoDelete("b")
        awaitRestore()

        assertNull(storedRow("b").deletedAt)
        // The completion that was superseded still stands.
        assertNotNull(storedRow("a").completedAt)
        assertEquals(listOf("b", "a"), awaitTodayIds(model, listOf("b", "a")))
    }

    @Test
    fun completingAfterDeletingSupersedesTheDeletionOffer() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        model.deleteTask("a")
        assertEquals(PendingUndo.Deletion("a"), awaitUndoOffer(model))

        model.toggleComplete("b")
        awaitUndoOffer(model, PendingUndo.Completion("b"))

        // The stale deletion undo can no longer restore a.
        model.undoDelete("a")
        repeat(20) { if (dao.restored.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.restored.isEmpty())
        assertNotNull(storedRow("a").deletedAt)
    }

    // Editing

    /**
     * The six fields the details editor may change, defaulting to no change.
     *
     * `notes` defaults to whatever is stored rather than to null, because that
     * is what the details sheet does: it edits a copy of the task it is
     * showing, so a field the user did not touch arrives unchanged. A default
     * of null here would quietly rewrite the note on every one of the fifty
     * edits below and hide exactly the regression this phase guards against.
     */
    private fun TaskListViewModel.edit(
        id: String,
        title: String = "Task $id",
        notes: String? = storedNotes(id),
        placement: TaskPlacement = TaskPlacement.ANYTIME,
        scheduledDate: LocalDate? = this@TaskListViewModelTest.today,
        dueDate: LocalDate? = null,
        estimatedDurationMinutes: Int? = null,
        recurrence: Recurrence? = storedRecurrence(id),
        reminderAt: LocalDateTime? = storedReminder(id)
    ) = editTask(
        id = id,
        title = title,
        notes = notes,
        placement = placement,
        scheduledDate = scheduledDate,
        dueDate = dueDate,
        estimatedDurationMinutes = estimatedDurationMinutes,
        recurrence = recurrence,
        reminderAt = reminderAt
    )

    /** The note the row currently holds, as the open sheet would be holding it. */
    private fun storedNotes(id: String): String? =
        dao.emissions.value.firstOrNull { it.id == id }?.notes

    /**
     * The reminder the row currently holds, for the same reason as the note,
     * and a sharper one: an edit that dropped it would silently unset a
     * promise the user made.
     */
    private fun storedReminder(id: String): LocalDateTime? =
        dao.emissions.value.firstOrNull { it.id == id }?.reminderAt

    /** The rule the row currently holds, for the same reason as the note. */
    private fun storedRecurrence(id: String): Recurrence? =
        dao.emissions.value.firstOrNull { it.id == id }?.recurrence

    /** Waits for the edit to reach the DAO and reads it back as a domain task. */
    private fun awaitEdited(id: String): Task = runBlocking {
        repeat(200) {
            if (dao.updated.isNotEmpty()) {
                return@runBlocking repository.observeTasks().first().single { it.id == id }
            }
            Thread.sleep(10)
        }
        throw AssertionError("no edit reached the DAO")
    }

    /** Waits until at least [count] writes have reached the DAO. */
    private fun awaitUpdates(count: Int) {
        repeat(200) {
            if (dao.updated.size >= count) return
            Thread.sleep(10)
        }
        throw AssertionError("only ${dao.updated.size} of $count edits reached the DAO")
    }

    @Test
    fun editingATaskPersistsThroughTheRepository() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Chase the missing invoice")

        assertEquals("Chase the missing invoice", awaitEdited("a").title)
        assertEquals(1, dao.updated.size)
    }

    @Test
    fun editingTrimsTheTitle() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "   Book the dentist   ")

        assertEquals("Book the dentist", awaitEdited("a").title)
    }

    @Test
    fun aBlankTitleIsRejected() {
        store(task(id = "a", title = "Original", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "   ")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertEquals("Original", storedRow("a").title)
    }

    @Test
    fun editingAnUnknownIdDoesNothing() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "missing", title = "Ghost")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
    }

    @Test
    fun everyPlacementValuePersists() {
        for (placement in TaskPlacement.entries) {
            val dao = FakeTaskDao()
            val repository = TaskRepository(dao)
            val model = TaskListViewModel(repository, FakeCurrentDay(today), SavedStateHandle(), RecordingFocusAlarms())
            dao.emissions.value = listOf(task(id = "a", scheduledDate = today).toEntity())
            runBlocking { model.todayTasks.first { it.size == 1 } }

            model.edit(id = "a", placement = placement)

            repeat(200) { if (dao.updated.isEmpty()) Thread.sleep(10) }
            assertEquals(placement, dao.updated.single().placement)
        }
    }

    @Test
    fun theScheduledDateCanBeSetChangedAndCleared() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", scheduledDate = today.minusDays(3))
        awaitUpdates(1)
        assertEquals(today.minusDays(3), storedRow("a").scheduledDate)

        model.edit(id = "a", scheduledDate = today.plusDays(2))
        awaitUpdates(2)
        assertEquals(today.plusDays(2), storedRow("a").scheduledDate)

        model.edit(id = "a", scheduledDate = null)
        awaitUpdates(3)
        assertNull(storedRow("a").scheduledDate)
    }

    @Test
    fun theDueDateCanBeSetChangedAndCleared() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", dueDate = today.plusDays(5))
        awaitUpdates(1)
        assertEquals(today.plusDays(5), storedRow("a").dueDate)

        model.edit(id = "a", dueDate = today.plusDays(9))
        awaitUpdates(2)
        assertEquals(today.plusDays(9), storedRow("a").dueDate)

        model.edit(id = "a", dueDate = null)
        awaitUpdates(3)
        assertNull(storedRow("a").dueDate)
    }

    @Test
    fun theDurationCanBeSetChangedAndCleared() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", estimatedDurationMinutes = 45)
        awaitUpdates(1)
        assertEquals(45, storedRow("a").estimatedDurationMinutes)

        model.edit(id = "a", estimatedDurationMinutes = 15)
        awaitUpdates(2)
        assertEquals(15, storedRow("a").estimatedDurationMinutes)

        model.edit(id = "a", estimatedDurationMinutes = null)
        awaitUpdates(3)
        assertNull(storedRow("a").estimatedDurationMinutes)
    }

    @Test
    fun aNonPositiveDurationIsRejected() {
        store(task(id = "a", scheduledDate = today, estimatedDurationMinutes = 30))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", estimatedDurationMinutes = 0)
        model.edit(id = "a", estimatedDurationMinutes = -5)

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertEquals(30, storedRow("a").estimatedDurationMinutes)
    }

    @Test
    fun editingOneFieldPreservesEveryOther() = runBlocking {
        val original = task(
            id = "a",
            title = "Chase the missing invoice",
            placement = TaskPlacement.SOMEDAY,
            scheduledDate = today.minusDays(2),
            dueDate = today.plusDays(3),
            estimatedDurationMinutes = 15,
            completedAt = completedAt
        )
        store(original)
        val model = viewModel()
        visible(model, 1)

        model.edit(
            id = "a",
            title = "Chase the invoice",
            placement = original.placement,
            scheduledDate = original.scheduledDate,
            dueDate = original.dueDate,
            estimatedDurationMinutes = original.estimatedDurationMinutes
        )

        val edited = awaitEdited("a")
        assertEquals(original.copy(title = "Chase the invoice"), edited)
    }

    @Test
    fun editingPreservesIdAndCreatedAt() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Renamed")

        val edited = awaitEdited("a")
        assertEquals("a", edited.id)
        assertEquals(createdAt, edited.createdAt)
    }

    @Test
    fun editingACompletedTaskLeavesItCompleted() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Renamed")

        val edited = awaitEdited("a")
        assertTrue(edited.isCompleted)
        assertEquals(completedAt, edited.completedAt)
    }

    @Test
    fun editingCannotCompleteOrReopenATask() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Renamed")

        assertNull(awaitEdited("a").completedAt)
        assertEquals(false, model.todayTasks.value.single().isCompleted)
    }

    @Test
    fun editingCannotRestoreADeletedTask() {
        // The stream excludes deleted rows, so there is nothing for an edit to find.
        store(task(id = "a", scheduledDate = today, deletedAt = deletedAt))
        val model = viewModel()

        model.edit(id = "a", title = "Renamed")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertNotNull(storedRow("a").deletedAt)
        assertEquals("Task a", storedRow("a").title)
    }

    @Test
    fun editingALiveTaskLeavesDeletedAtNull() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Renamed")

        assertNull(awaitEdited("a").deletedAt)
    }

    @Test
    fun reschedulingMovesATaskBetweenOrderingBands() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today),
            task(id = "done", scheduledDate = today, completedAt = completedAt)
        )
        val model = viewModel()
        assertEquals(listOf("a", "b", "done"), visible(model, 3).map { it.id })

        // Overdue now, so it sits below today's outstanding work.
        model.edit(id = "a", scheduledDate = today.minusDays(1))

        assertEquals(
            listOf("b", "a", "done"),
            awaitTodayIds(model, listOf("b", "a", "done"))
        )
    }

    @Test
    fun clearingTheScheduledDateRemovesTheTaskFromToday() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)

        model.edit(id = "a", scheduledDate = null)

        assertEquals(listOf("b"), awaitTodayIds(model, listOf("b")))
        // Gone from the view, still very much stored.
        assertNull(storedRow("a").deletedAt)
        assertNull(storedRow("a").scheduledDate)
    }

    @Test
    fun schedulingATaskForTheFutureRemovesItFromToday() = runBlocking {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)

        model.edit(id = "a", scheduledDate = today.plusDays(4))

        assertEquals(listOf("b"), awaitTodayIds(model, listOf("b")))
        assertEquals(listOf("a"), upcomingTasks(repository.observeTasks().first(), today).map { it.id })
    }

    @Test
    fun theRepositoryFlowRemainsTheSourceOfTruthAfterAnEdit() = runBlocking {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Renamed", estimatedDurationMinutes = 25)
        awaitEdited("a")

        // What the screen shows is what the repository emits, not a local copy.
        val shown = model.todayTasks.first { it.single().title == "Renamed" }.single()
        val stored = repository.observeTasks().first().single { it.id == "a" }
        assertEquals(stored, shown)
        assertEquals(25, stored.estimatedDurationMinutes)
    }

    @Test
    fun editingDoesNotDisturbOtherTasks() {
        val untouched = task(
            id = "b",
            title = "Book the dentist",
            scheduledDate = today,
            dueDate = today.plusDays(1),
            estimatedDurationMinutes = 20
        )
        store(task(id = "a", scheduledDate = today), untouched)
        val model = viewModel()
        visible(model, 2)

        model.edit(id = "a", title = "Renamed")
        awaitEdited("a")

        assertEquals(untouched.toEntity(), storedRow("b"))
    }

    @Test
    fun editingDoesNotRaiseAnUndoOffer() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Renamed")
        awaitEdited("a")

        assertNull(model.pendingUndo.value)
    }

    // Upcoming

    /** The Upcoming state starts empty, so wait for the derived emission. */
    private fun upcoming(model: TaskListViewModel, size: Int): List<Task> = runBlocking {
        model.upcomingTasks.first { it.size == size }
    }

    /** Waits for Upcoming to read as [ids], then returns what it actually holds. */
    private fun awaitUpcomingIds(model: TaskListViewModel, ids: List<String>): List<String> =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.upcomingTasks.first { tasks -> tasks.map { it.id } == ids }
            } ?: model.upcomingTasks.value
        }.map { it.id }

    @Test
    fun upcomingShowsFutureScheduledTasks() {
        store(task(id = "a", scheduledDate = today.plusDays(1)))

        assertEquals(listOf("a"), upcoming(viewModel(), 1).map { it.id })
    }

    @Test
    fun upcomingExcludesTodayAndOverdueTasks() {
        store(
            task(id = "future", scheduledDate = today.plusDays(2)),
            task(id = "today", scheduledDate = today),
            task(id = "overdue", scheduledDate = today.minusDays(3))
        )

        assertEquals(listOf("future"), upcoming(viewModel(), 1).map { it.id })
    }

    @Test
    fun upcomingExcludesCompletedTasks() {
        store(
            task(id = "outstanding", scheduledDate = today.plusDays(2)),
            task(id = "done", scheduledDate = today.plusDays(3), completedAt = completedAt)
        )

        assertEquals(listOf("outstanding"), upcoming(viewModel(), 1).map { it.id })
    }

    @Test
    fun upcomingExcludesDeletedTasks() {
        store(
            task(id = "live", scheduledDate = today.plusDays(2)),
            task(id = "gone", scheduledDate = today.plusDays(1), deletedAt = deletedAt)
        )

        assertEquals(listOf("live"), upcoming(viewModel(), 1).map { it.id })
    }

    @Test
    fun upcomingExcludesUnscheduledTasks() {
        store(
            task(id = "future", scheduledDate = today.plusDays(2)),
            task(id = "unscheduled", placement = TaskPlacement.INBOX)
        )

        assertEquals(listOf("future"), upcoming(viewModel(), 1).map { it.id })
    }

    @Test
    fun upcomingKeepsTheQueryOrderingNearestFirst() {
        store(
            task(id = "far", scheduledDate = today.plusDays(30)),
            task(id = "near", scheduledDate = today.plusDays(1)),
            task(id = "middle", scheduledDate = today.plusDays(6))
        )

        assertEquals(listOf("near", "middle", "far"), upcoming(viewModel(), 3).map { it.id })
    }

    @Test
    fun todayAndUpcomingAreDisjointOverTheSameStream() {
        store(
            task(id = "today", scheduledDate = today),
            task(id = "overdue", scheduledDate = today.minusDays(1)),
            task(id = "future", scheduledDate = today.plusDays(1))
        )
        val model = viewModel()

        assertEquals(listOf("today", "overdue"), visible(model, 2).map { it.id })
        assertEquals(listOf("future"), upcoming(model, 1).map { it.id })
    }

    // Editing across the two lists

    @Test
    fun reschedulingATodayTaskIntoTheFutureMovesItToUpcoming() {
        store(task(id = "a", scheduledDate = today), task(id = "b", scheduledDate = today))
        val model = viewModel()
        visible(model, 2)
        assertEquals(emptyList<String>(), model.upcomingTasks.value.map { it.id })

        model.edit(id = "a", scheduledDate = today.plusDays(3))

        assertEquals(listOf("b"), awaitTodayIds(model, listOf("b")))
        assertEquals(listOf("a"), awaitUpcomingIds(model, listOf("a")))
    }

    @Test
    fun reschedulingAnUpcomingTaskToTodayMovesItBack() {
        store(task(id = "a", scheduledDate = today.plusDays(4)))
        val model = viewModel()
        upcoming(model, 1)

        model.edit(id = "a", scheduledDate = today)

        assertEquals(emptyList<String>(), awaitUpcomingIds(model, emptyList()))
        assertEquals(listOf("a"), awaitTodayIds(model, listOf("a")))
    }

    @Test
    fun reschedulingAnUpcomingTaskIntoThePastMovesItToTheOverdueBand() {
        store(
            task(id = "today", scheduledDate = today),
            task(id = "a", scheduledDate = today.plusDays(4))
        )
        val model = viewModel()
        upcoming(model, 1)

        model.edit(id = "a", scheduledDate = today.minusDays(2))

        assertEquals(emptyList<String>(), awaitUpcomingIds(model, emptyList()))
        // Overdue, so below today's outstanding work.
        assertEquals(listOf("today", "a"), awaitTodayIds(model, listOf("today", "a")))
    }

    @Test
    fun clearingAnUpcomingTaskScheduledDateRemovesItFromBothLists() {
        store(task(id = "a", scheduledDate = today.plusDays(4)))
        val model = viewModel()
        upcoming(model, 1)

        model.edit(id = "a", scheduledDate = null)

        assertEquals(emptyList<String>(), awaitUpcomingIds(model, emptyList()))
        assertEquals(emptyList<String>(), model.todayTasks.value.map { it.id })
        // Still stored, just not in any dated view.
        assertNull(storedRow("a").deletedAt)
        assertNull(storedRow("a").scheduledDate)
    }

    @Test
    fun editingAnUpcomingTaskPreservesIdAndCreatedAt() {
        store(task(id = "a", scheduledDate = today.plusDays(4)))
        val model = viewModel()
        upcoming(model, 1)

        model.edit(id = "a", title = "Renamed", scheduledDate = today.plusDays(4))

        val edited = awaitEdited("a")
        assertEquals("a", edited.id)
        assertEquals(createdAt, edited.createdAt)
    }

    @Test
    fun editingAnUpcomingTaskPreservesCompletionAndDeletionFields() {
        store(task(id = "a", scheduledDate = today.plusDays(4)))
        val model = viewModel()
        upcoming(model, 1)

        model.edit(
            id = "a",
            title = "Renamed",
            scheduledDate = today.plusDays(6),
            dueDate = today.plusDays(8),
            estimatedDurationMinutes = 20
        )

        val edited = awaitEdited("a")
        assertNull(edited.completedAt)
        assertNull(edited.deletedAt)
        assertEquals(false, edited.isCompleted)
    }

    @Test
    fun editingAnUpcomingTaskGoesThroughTheSameUpdatePath() = runBlocking {
        val original = task(
            id = "a",
            title = "Renew the domain",
            placement = TaskPlacement.SOMEDAY,
            scheduledDate = today.plusDays(12),
            dueDate = today.plusDays(14),
            estimatedDurationMinutes = 10
        )
        store(original)
        val model = viewModel()
        upcoming(model, 1)

        model.edit(
            id = "a",
            title = "Renew the domain and the certificate",
            placement = original.placement,
            scheduledDate = original.scheduledDate,
            dueDate = original.dueDate,
            estimatedDurationMinutes = original.estimatedDurationMinutes
        )
        awaitEdited("a")

        // One update through the repository, and the whole task round-trips.
        assertEquals(1, dao.updated.size)
        assertEquals(
            original.copy(title = "Renew the domain and the certificate"),
            repository.observeTasks().first().single { it.id == "a" }
        )
    }

    @Test
    fun editingAnUnknownIdChangesNeitherList() {
        store(task(id = "a", scheduledDate = today.plusDays(4)))
        val model = viewModel()
        upcoming(model, 1)

        model.edit(id = "missing", title = "Ghost")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertEquals(listOf("a"), model.upcomingTasks.value.map { it.id })
        assertEquals(emptyList<String>(), model.todayTasks.value.map { it.id })
    }

    @Test
    fun theRepositoryFlowDrivesUpcoming() = runBlocking {
        val model = viewModel()
        assertEquals(emptyList<Task>(), model.upcomingTasks.value)

        store(task(id = "a", scheduledDate = today.plusDays(2)))

        val shown = model.upcomingTasks.first { it.size == 1 }.single()
        assertEquals(repository.observeTasks().first().single { it.id == "a" }, shown)
    }

    // Completion and deletion behave the same way here

    @Test
    fun completingAnUpcomingTaskRemovesItAndOffersAnUndo() {
        store(task(id = "a", scheduledDate = today.plusDays(2)))
        val model = viewModel()
        upcoming(model, 1)

        model.toggleComplete("a")

        assertEquals(PendingUndo.Completion("a"), awaitUndoOffer(model))
        // Upcoming has no completed band, so it simply leaves.
        assertEquals(emptyList<String>(), awaitUpcomingIds(model, emptyList()))
    }

    @Test
    fun undoingThatCompletionBringsTheTaskBackToUpcoming() {
        store(task(id = "a", scheduledDate = today.plusDays(2)))
        val model = viewModel()
        upcoming(model, 1)
        model.toggleComplete("a")
        awaitUndoOffer(model)
        awaitUpcomingIds(model, emptyList())

        model.undoComplete("a")

        assertEquals(listOf("a"), awaitUpcomingIds(model, listOf("a")))
    }

    @Test
    fun deletingAnUpcomingTaskRemovesItAndCanBeUndone() {
        store(task(id = "a", scheduledDate = today.plusDays(2)))
        val model = viewModel()
        upcoming(model, 1)

        model.deleteTask("a")
        assertEquals(PendingUndo.Deletion("a"), awaitUndoOffer(model))
        assertEquals(emptyList<String>(), awaitUpcomingIds(model, emptyList()))

        model.undoDelete("a")

        assertEquals(listOf("a"), awaitUpcomingIds(model, listOf("a")))
    }

    // Inbox

    /** The Inbox state starts empty, so wait for the derived emission. */
    private fun inbox(model: TaskListViewModel, size: Int): List<Task> = runBlocking {
        model.inboxTasks.first { it.size == size }
    }

    /** Waits for Inbox to read as [ids], then returns what it actually holds. */
    private fun awaitInboxIds(model: TaskListViewModel, ids: List<String>): List<String> =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.inboxTasks.first { tasks -> tasks.map { it.id } == ids }
            } ?: model.inboxTasks.value
        }.map { it.id }

    @Test
    fun inboxShowsUntriagedUnscheduledTasks() {
        store(task(id = "a", placement = TaskPlacement.INBOX))

        assertEquals(listOf("a"), inbox(viewModel(), 1).map { it.id })
    }

    @Test
    fun inboxExcludesScheduledTasks() {
        store(
            task(id = "captured", placement = TaskPlacement.INBOX),
            task(id = "today", placement = TaskPlacement.INBOX, scheduledDate = today),
            task(id = "future", placement = TaskPlacement.INBOX, scheduledDate = today.plusDays(2))
        )

        assertEquals(listOf("captured"), inbox(viewModel(), 1).map { it.id })
    }

    @Test
    fun inboxExcludesTriagedTasks() {
        store(
            task(id = "captured", placement = TaskPlacement.INBOX),
            task(id = "anytime", placement = TaskPlacement.ANYTIME),
            task(id = "someday", placement = TaskPlacement.SOMEDAY)
        )

        assertEquals(listOf("captured"), inbox(viewModel(), 1).map { it.id })
    }

    @Test
    fun inboxExcludesCompletedAndDeletedTasks() {
        store(
            task(id = "live", placement = TaskPlacement.INBOX),
            task(id = "done", placement = TaskPlacement.INBOX, completedAt = completedAt),
            task(id = "gone", placement = TaskPlacement.INBOX, deletedAt = deletedAt)
        )

        assertEquals(listOf("live"), inbox(viewModel(), 1).map { it.id })
    }

    @Test
    fun inboxKeepsTheQueryOrderingNewestFirst() {
        store(
            task(id = "middle", placement = TaskPlacement.INBOX, createdAt = createdAt),
            task(
                id = "oldest",
                placement = TaskPlacement.INBOX,
                createdAt = createdAt.minusSeconds(600)
            ),
            task(
                id = "newest",
                placement = TaskPlacement.INBOX,
                createdAt = createdAt.plusSeconds(600)
            )
        )

        assertEquals(listOf("newest", "middle", "oldest"), inbox(viewModel(), 3).map { it.id })
    }

    @Test
    fun theThreeListsAreDisjointOverTheSameStream() {
        store(
            task(id = "captured", placement = TaskPlacement.INBOX),
            task(id = "today", placement = TaskPlacement.INBOX, scheduledDate = today),
            task(id = "future", placement = TaskPlacement.INBOX, scheduledDate = today.plusDays(2))
        )
        val model = viewModel()

        assertEquals(listOf("captured"), inbox(model, 1).map { it.id })
        assertEquals(listOf("today"), visible(model, 1).map { it.id })
        assertEquals(listOf("future"), upcoming(model, 1).map { it.id })
    }

    // Capture

    @Test
    fun captureFromInboxCreatesAnUnscheduledInboxTask() {
        val model = viewModel()

        model.createTask(title = "Replace the kitchen bulb", scheduledDate = null)

        val stored = awaitInsert()
        assertEquals("Replace the kitchen bulb", stored.title)
        assertEquals(TaskPlacement.INBOX, stored.placement)
        assertNull(stored.scheduledDate)
        assertNull(stored.dueDate)
        assertNull(stored.estimatedDurationMinutes)
        assertNull(stored.completedAt)
        assertNull(stored.deletedAt)
    }

    @Test
    fun captureFromTodayStillSchedulesForToday() {
        val model = viewModel()

        model.createTask(title = "Chase the missing invoice", scheduledDate = today)

        val stored = awaitInsert()
        assertEquals(TaskPlacement.INBOX, stored.placement)
        assertEquals(today, stored.scheduledDate)
    }

    @Test
    fun anInboxCaptureAppearsInInboxAndNotInToday() {
        val model = viewModel()

        model.createTask(title = "Replace the kitchen bulb", scheduledDate = null)

        assertEquals(
            listOf("Replace the kitchen bulb"),
            runBlocking { model.inboxTasks.first { it.size == 1 } }.map { it.title }
        )
        assertEquals(emptyList<String>(), model.todayTasks.value.map { it.id })
    }

    @Test
    fun aTodayCaptureAppearsInTodayAndNotInInbox() {
        val model = viewModel()

        model.createTask(title = "Chase the missing invoice", scheduledDate = today)

        assertEquals(1, visible(model, 1).size)
        assertEquals(emptyList<String>(), model.inboxTasks.value.map { it.id })
    }

    @Test
    fun aBlankCaptureCreatesNothingWhicheverScreenItCameFrom() {
        val model = viewModel()

        assertEquals(false, model.createTask(title = "   ", scheduledDate = null))

        repeat(20) { if (dao.inserted.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.inserted.isEmpty())
    }

    // Triage moves a task out of Inbox

    @Test
    fun schedulingAnInboxTaskMovesItToToday() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.edit(id = "a", placement = TaskPlacement.INBOX, scheduledDate = today)

        assertEquals(emptyList<String>(), awaitInboxIds(model, emptyList()))
        assertEquals(listOf("a"), awaitTodayIds(model, listOf("a")))
    }

    @Test
    fun triagingAnInboxTaskToAnytimeRemovesItFromInbox() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.edit(id = "a", placement = TaskPlacement.ANYTIME, scheduledDate = null)

        assertEquals(emptyList<String>(), awaitInboxIds(model, emptyList()))
        assertEquals(TaskPlacement.ANYTIME, storedRow("a").placement)
        // Still stored, just no longer in the queue.
        assertNull(storedRow("a").deletedAt)
    }

    @Test
    fun triagingAnInboxTaskToSomedayRemovesItFromInbox() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.edit(id = "a", placement = TaskPlacement.SOMEDAY, scheduledDate = null)

        assertEquals(emptyList<String>(), awaitInboxIds(model, emptyList()))
        assertEquals(TaskPlacement.SOMEDAY, storedRow("a").placement)
    }

    @Test
    fun unschedulingAnInboxPlacedTaskReturnsItToInbox() {
        store(task(id = "a", placement = TaskPlacement.INBOX, scheduledDate = today))
        val model = viewModel()
        visible(model, 1)
        assertEquals(emptyList<String>(), model.inboxTasks.value.map { it.id })

        model.edit(id = "a", placement = TaskPlacement.INBOX, scheduledDate = null)

        assertEquals(listOf("a"), awaitInboxIds(model, listOf("a")))
        assertEquals(emptyList<String>(), awaitTodayIds(model, emptyList()))
    }

    @Test
    fun editingAnInboxTaskPreservesIdAndCreatedAt() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.edit(id = "a", title = "Renamed", placement = TaskPlacement.INBOX, scheduledDate = null)

        val edited = awaitEdited("a")
        assertEquals("a", edited.id)
        assertEquals(createdAt, edited.createdAt)
        assertNull(edited.completedAt)
        assertNull(edited.deletedAt)
    }

    @Test
    fun editingAnUnknownIdChangesNoList() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.edit(id = "missing", title = "Ghost")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertEquals(listOf("a"), model.inboxTasks.value.map { it.id })
    }

    // Completion and deletion behave the same way here

    @Test
    fun completingAnInboxTaskRemovesItAndOffersAnUndo() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.toggleComplete("a")

        assertEquals(PendingUndo.Completion("a"), awaitUndoOffer(model))
        assertEquals(emptyList<String>(), awaitInboxIds(model, emptyList()))
    }

    @Test
    fun undoingThatCompletionReturnsTheTaskToInbox() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)
        model.toggleComplete("a")
        awaitUndoOffer(model)
        awaitInboxIds(model, emptyList())

        model.undoComplete("a")

        assertEquals(listOf("a"), awaitInboxIds(model, listOf("a")))
    }

    @Test
    fun deletingAnInboxTaskRemovesItAndCanBeUndone() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.deleteTask("a")
        assertEquals(PendingUndo.Deletion("a"), awaitUndoOffer(model))
        assertEquals(emptyList<String>(), awaitInboxIds(model, emptyList()))

        model.undoDelete("a")

        assertEquals(listOf("a"), awaitInboxIds(model, listOf("a")))
    }

    @Test
    fun theRepositoryFlowDrivesInbox() = runBlocking {
        val model = viewModel()
        assertEquals(emptyList<Task>(), model.inboxTasks.value)

        store(task(id = "a", placement = TaskPlacement.INBOX))

        val shown = model.inboxTasks.first { it.size == 1 }.single()
        assertEquals(repository.observeTasks().first().single { it.id == "a" }, shown)
    }

    // Anytime and Someday

    /** The placement states start empty, so wait for the derived emission. */
    private fun anytime(model: TaskListViewModel, size: Int): List<Task> = runBlocking {
        model.anytimeTasks.first { it.size == size }
    }

    private fun someday(model: TaskListViewModel, size: Int): List<Task> = runBlocking {
        model.somedayTasks.first { it.size == size }
    }

    /** Waits for Anytime to read as [ids], then returns what it actually holds. */
    private fun awaitAnytimeIds(model: TaskListViewModel, ids: List<String>): List<String> =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.anytimeTasks.first { tasks -> tasks.map { it.id } == ids }
            } ?: model.anytimeTasks.value
        }.map { it.id }

    /** Waits for Someday to read as [ids], then returns what it actually holds. */
    private fun awaitSomedayIds(model: TaskListViewModel, ids: List<String>): List<String> =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.somedayTasks.first { tasks -> tasks.map { it.id } == ids }
            } ?: model.somedayTasks.value
        }.map { it.id }

    @Test
    fun anytimeShowsOnlyAnytimeTasks() {
        store(
            task(id = "anytime", placement = TaskPlacement.ANYTIME),
            task(id = "someday", placement = TaskPlacement.SOMEDAY),
            task(id = "inbox", placement = TaskPlacement.INBOX)
        )

        assertEquals(listOf("anytime"), anytime(viewModel(), 1).map { it.id })
    }

    @Test
    fun somedayShowsOnlySomedayTasks() {
        store(
            task(id = "anytime", placement = TaskPlacement.ANYTIME),
            task(id = "someday", placement = TaskPlacement.SOMEDAY),
            task(id = "inbox", placement = TaskPlacement.INBOX)
        )

        assertEquals(listOf("someday"), someday(viewModel(), 1).map { it.id })
    }

    @Test
    fun bothPlacementListsExcludeCompletedTasks() {
        store(
            task(id = "a", placement = TaskPlacement.ANYTIME, completedAt = completedAt),
            task(id = "s", placement = TaskPlacement.SOMEDAY, completedAt = completedAt)
        )
        val model = viewModel()

        assertEquals(emptyList<String>(), awaitAnytimeIds(model, emptyList()))
        assertEquals(emptyList<String>(), awaitSomedayIds(model, emptyList()))
    }

    @Test
    fun bothPlacementListsExcludeDeletedTasks() {
        store(
            task(id = "a", placement = TaskPlacement.ANYTIME, deletedAt = deletedAt),
            task(id = "s", placement = TaskPlacement.SOMEDAY, deletedAt = deletedAt)
        )
        val model = viewModel()

        assertEquals(emptyList<String>(), awaitAnytimeIds(model, emptyList()))
        assertEquals(emptyList<String>(), awaitSomedayIds(model, emptyList()))
    }

    // Each of these keeps an undated control task alongside the dated one, and
    // waits for the list to read as exactly that control. Asserting against
    // the empty list would pass without proving anything: these flows start
    // empty, so "still empty" is also what not-yet-loaded looks like, and the
    // wait would return on the first emission before the query had run.

    @Test
    fun aScheduledAnytimeTaskLeavesAnytime() {
        store(
            task(id = "dated", placement = TaskPlacement.ANYTIME, scheduledDate = today.plusDays(3)),
            task(id = "undated", placement = TaskPlacement.ANYTIME)
        )

        assertEquals(listOf("undated"), anytime(viewModel(), 1).map { it.id })
    }

    @Test
    fun aScheduledSomedayTaskLeavesSomeday() {
        store(
            task(id = "dated", placement = TaskPlacement.SOMEDAY, scheduledDate = today.plusDays(3)),
            task(id = "undated", placement = TaskPlacement.SOMEDAY)
        )

        assertEquals(listOf("undated"), someday(viewModel(), 1).map { it.id })
    }

    @Test
    fun anAnytimeTaskScheduledForTodayIsOnlyInToday() {
        store(
            task(id = "dated", placement = TaskPlacement.ANYTIME, scheduledDate = today),
            task(id = "undated", placement = TaskPlacement.ANYTIME)
        )
        val model = viewModel()

        // These used to overlap deliberately, on the reasoning that placement
        // and scheduling are independent axes. They are, in the data; in the
        // lists it meant Anytime showed work already planned for today and a
        // task had no single place to be found.
        assertEquals(listOf("dated"), visible(model, 1).map { it.id })
        assertEquals(listOf("undated"), anytime(model, 1).map { it.id })
    }

    @Test
    fun aSomedayTaskScheduledAheadIsOnlyInUpcoming() {
        store(
            task(id = "dated", placement = TaskPlacement.SOMEDAY, scheduledDate = today.plusDays(5)),
            task(id = "undated", placement = TaskPlacement.SOMEDAY)
        )
        val model = viewModel()

        // The sharper half: Someday means deliberately deferred, and a day on
        // the task is the calendar calling it due.
        assertEquals(listOf("dated"), upcoming(model, 1).map { it.id })
        assertEquals(listOf("undated"), someday(model, 1).map { it.id })
    }

    @Test
    fun placementListsOrderCapturesNewestFirst() {
        store(
            task(id = "capture", placement = TaskPlacement.ANYTIME, createdAt = createdAt),
            task(
                id = "olderCapture",
                placement = TaskPlacement.ANYTIME,
                createdAt = createdAt.minusSeconds(60)
            )
        )

        // The two-group ordering that put dated tasks first went with the
        // dated tasks; these lists hold undated work only.
        assertEquals(
            listOf("capture", "olderCapture"),
            anytime(viewModel(), 2).map { it.id }
        )
    }

    @Test
    fun bothPlacementListsAreBackedByTheSameStream() = runBlocking {
        store(
            task(id = "a", placement = TaskPlacement.ANYTIME),
            task(id = "s", placement = TaskPlacement.SOMEDAY)
        )
        val model = viewModel()

        val stored = repository.observeTasks().first()
        assertEquals(stored.single { it.id == "a" }, anytime(model, 1).single())
        assertEquals(stored.single { it.id == "s" }, someday(model, 1).single())
    }

    // Moving between placements

    @Test
    fun movingAnytimeToSomedayUpdatesBothLists() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME))
        val model = viewModel()
        anytime(model, 1)

        model.edit(id = "a", placement = TaskPlacement.SOMEDAY, scheduledDate = null)

        assertEquals(emptyList<String>(), awaitAnytimeIds(model, emptyList()))
        assertEquals(listOf("a"), awaitSomedayIds(model, listOf("a")))
        assertEquals(TaskPlacement.SOMEDAY, storedRow("a").placement)
    }

    @Test
    fun movingSomedayToAnytimeUpdatesBothLists() {
        store(task(id = "s", placement = TaskPlacement.SOMEDAY))
        val model = viewModel()
        someday(model, 1)

        model.edit(id = "s", placement = TaskPlacement.ANYTIME, scheduledDate = null)

        assertEquals(emptyList<String>(), awaitSomedayIds(model, emptyList()))
        assertEquals(listOf("s"), awaitAnytimeIds(model, listOf("s")))
    }

    @Test
    fun movingAPlacementTaskToInboxRemovesItFromThePlacementList() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME))
        val model = viewModel()
        anytime(model, 1)

        model.edit(id = "a", placement = TaskPlacement.INBOX, scheduledDate = null)

        assertEquals(emptyList<String>(), awaitAnytimeIds(model, emptyList()))
        assertEquals(listOf("a"), awaitInboxIds(model, listOf("a")))
    }

    @Test
    fun schedulingMovesATaskOutOfItsPlacementList() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME))
        val model = viewModel()
        anytime(model, 1)

        model.edit(id = "a", placement = TaskPlacement.ANYTIME, scheduledDate = today)
        awaitEdited("a")

        // Giving a task a day is the decision Anytime is waiting for, so it
        // leaves for Today rather than sitting in both. Waiting the list down
        // from one task to none is a real transition, not the empty state it
        // started in.
        assertEquals(emptyList<String>(), awaitAnytimeIds(model, emptyList()))
        assertEquals(listOf("a"), awaitTodayIds(model, listOf("a")))
    }

    @Test
    fun unschedulingReturnsATaskToItsPlacementList() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME, scheduledDate = today))
        val model = viewModel()
        // Deliberately not waiting on Anytime here: the task has a day, so
        // Anytime is empty and a wait for one task would never return.
        awaitTodayIds(model, listOf("a"))

        model.edit(id = "a", placement = TaskPlacement.ANYTIME, scheduledDate = null)
        awaitEdited("a")

        // Taking the day away hands it back to the placement it kept all along.
        assertEquals(listOf("a"), awaitAnytimeIds(model, listOf("a")))
        assertEquals(emptyList<String>(), awaitTodayIds(model, emptyList()))
    }

    @Test
    fun editingAPlacementTaskPreservesItsIdentityAndState() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME, completedAt = null))
        val model = viewModel()
        anytime(model, 1)

        model.edit(
            id = "a",
            title = "Renamed",
            placement = TaskPlacement.ANYTIME,
            scheduledDate = null
        )

        val edited = awaitEdited("a")
        assertEquals("a", edited.id)
        assertEquals(createdAt, edited.createdAt)
        assertNull(edited.completedAt)
        assertNull(edited.deletedAt)
    }

    @Test
    fun editingAnUnknownIdLeavesBothPlacementListsAlone() {
        store(
            task(id = "a", placement = TaskPlacement.ANYTIME),
            task(id = "s", placement = TaskPlacement.SOMEDAY)
        )
        val model = viewModel()
        // Both flows are WhileSubscribed, so subscribe before reading either.
        anytime(model, 1)
        someday(model, 1)

        model.edit(id = "missing", title = "Ghost")

        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertEquals(listOf("a"), model.anytimeTasks.value.map { it.id })
        assertEquals(listOf("s"), model.somedayTasks.value.map { it.id })
    }

    // Completion and deletion behave the same way here

    @Test
    fun completingAnAnytimeTaskRemovesItAndUndoRestoresIt() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME))
        val model = viewModel()
        anytime(model, 1)

        model.toggleComplete("a")
        assertEquals(PendingUndo.Completion("a"), awaitUndoOffer(model))
        assertEquals(emptyList<String>(), awaitAnytimeIds(model, emptyList()))

        model.undoComplete("a")

        assertEquals(listOf("a"), awaitAnytimeIds(model, listOf("a")))
    }

    @Test
    fun deletingASomedayTaskRemovesItAndUndoRestoresIt() {
        store(task(id = "s", placement = TaskPlacement.SOMEDAY))
        val model = viewModel()
        someday(model, 1)

        model.deleteTask("s")
        assertEquals(PendingUndo.Deletion("s"), awaitUndoOffer(model))
        assertEquals(emptyList<String>(), awaitSomedayIds(model, emptyList()))

        model.undoDelete("s")

        assertEquals(listOf("s"), awaitSomedayIds(model, listOf("s")))
    }

    @Test
    fun theRepositoryFlowDrivesThePlacementLists() = runBlocking {
        val model = viewModel()
        assertEquals(emptyList<Task>(), model.anytimeTasks.value)
        assertEquals(emptyList<Task>(), model.somedayTasks.value)

        store(task(id = "a", placement = TaskPlacement.ANYTIME))

        val shown = model.anytimeTasks.first { it.size == 1 }.single()
        assertEquals(repository.observeTasks().first().single { it.id == "a" }, shown)
    }

    // Logbook

    /** The Logbook state starts empty, so wait for the derived emission. */
    private fun logbook(model: TaskListViewModel, size: Int): List<Task> = runBlocking {
        model.completedTasks.first { it.size == size }
    }

    /** Waits for the Logbook to read as [ids], then returns what it actually holds. */
    private fun awaitLogbookIds(model: TaskListViewModel, ids: List<String>): List<String> =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.completedTasks.first { tasks -> tasks.map { it.id } == ids }
            } ?: model.completedTasks.value
        }.map { it.id }

    @Test
    fun theLogbookShowsCompletedTasksWhateverTheirPlacementOrDate() {
        store(
            task(id = "inbox", placement = TaskPlacement.INBOX, completedAt = completedAt),
            task(id = "future", scheduledDate = today.plusDays(5), completedAt = completedAt),
            task(id = "anytime", placement = TaskPlacement.ANYTIME, completedAt = completedAt),
            task(id = "outstanding", scheduledDate = today)
        )

        assertEquals(
            listOf("inbox", "future", "anytime"),
            logbook(viewModel(), 3).map { it.id }
        )
    }

    @Test
    fun theLogbookExcludesOutstandingAndDeletedTasks() {
        store(
            task(id = "done", completedAt = completedAt),
            task(id = "outstanding", scheduledDate = today),
            task(id = "gone", completedAt = completedAt, deletedAt = deletedAt)
        )

        assertEquals(listOf("done"), logbook(viewModel(), 1).map { it.id })
    }

    @Test
    fun theLogbookPutsTheMostRecentlyFinishedTaskFirst() {
        store(
            task(id = "middle", completedAt = completedAt),
            task(id = "oldest", completedAt = completedAt.minusSeconds(600)),
            task(id = "newest", completedAt = completedAt.plusSeconds(600))
        )

        assertEquals(
            listOf("newest", "middle", "oldest"),
            logbook(viewModel(), 3).map { it.id }
        )
    }

    // The hole the Logbook closes

    @Test
    fun completingAnInboxTaskLeavesItReachableInTheLogbook() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)

        model.toggleComplete("a")

        // Gone from Inbox, but not gone.
        assertEquals(emptyList<String>(), awaitInboxIds(model, emptyList()))
        assertEquals(listOf("a"), awaitLogbookIds(model, listOf("a")))
    }

    @Test
    fun completingAnUpcomingTaskLeavesItReachableInTheLogbook() {
        store(task(id = "a", scheduledDate = today.plusDays(4)))
        val model = viewModel()
        upcoming(model, 1)

        model.toggleComplete("a")

        assertEquals(emptyList<String>(), awaitUpcomingIds(model, emptyList()))
        assertEquals(listOf("a"), awaitLogbookIds(model, listOf("a")))
    }

    @Test
    fun completingAnAnytimeTaskLeavesItReachableInTheLogbook() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME))
        val model = viewModel()
        anytime(model, 1)

        model.toggleComplete("a")

        assertEquals(emptyList<String>(), awaitAnytimeIds(model, emptyList()))
        assertEquals(listOf("a"), awaitLogbookIds(model, listOf("a")))
    }

    @Test
    fun completingASomedayTaskLeavesItReachableInTheLogbook() {
        store(task(id = "a", placement = TaskPlacement.SOMEDAY))
        val model = viewModel()
        someday(model, 1)

        model.toggleComplete("a")

        assertEquals(emptyList<String>(), awaitSomedayIds(model, emptyList()))
        assertEquals(listOf("a"), awaitLogbookIds(model, listOf("a")))
    }

    // Reopening from the Logbook

    @Test
    fun unCompletingFromTheLogbookReturnsTheTaskToItsActiveLists() {
        store(task(id = "a", placement = TaskPlacement.ANYTIME, completedAt = completedAt))
        val model = viewModel()
        logbook(model, 1)
        assertEquals(emptyList<String>(), model.anytimeTasks.value.map { it.id })

        model.toggleComplete("a")

        assertEquals(emptyList<String>(), awaitLogbookIds(model, emptyList()))
        assertEquals(listOf("a"), awaitAnytimeIds(model, listOf("a")))
        assertNull(storedRow("a").completedAt)
    }

    @Test
    fun unCompletingFromTheLogbookRaisesNoNewOffer() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        logbook(model, 1)

        model.toggleComplete("a")
        awaitLogbookIds(model, emptyList())

        // Reopening is already the reversal, as it is everywhere else.
        repeat(20) { if (model.pendingUndo.value != null) Thread.sleep(10) }
        assertNull(model.pendingUndo.value)
    }

    @Test
    fun aCompletionUndoneFromItsSnackbarAlsoLeavesTheLogbook() {
        store(task(id = "a", placement = TaskPlacement.INBOX))
        val model = viewModel()
        inbox(model, 1)
        model.toggleComplete("a")
        awaitUndoOffer(model)
        awaitLogbookIds(model, listOf("a"))

        model.undoComplete("a")

        assertEquals(emptyList<String>(), awaitLogbookIds(model, emptyList()))
        assertEquals(listOf("a"), awaitInboxIds(model, listOf("a")))
    }

    // Editing and deleting from the Logbook

    @Test
    fun aCompletedTaskStaysEditableAndStaysCompleted() {
        val original = task(
            id = "a",
            title = "Send the sprint summary",
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today,
            dueDate = today.plusDays(2),
            estimatedDurationMinutes = 30,
            completedAt = completedAt
        )
        store(original)
        val model = viewModel()
        logbook(model, 1)

        model.edit(
            id = "a",
            title = "Send the sprint summary to the team",
            placement = original.placement,
            scheduledDate = original.scheduledDate,
            dueDate = original.dueDate,
            estimatedDurationMinutes = original.estimatedDurationMinutes
        )

        val edited = awaitEdited("a")
        assertEquals(original.copy(title = "Send the sprint summary to the team"), edited)
        assertEquals(completedAt, edited.completedAt)
        // Editing does not move it out of the Logbook.
        assertEquals(listOf("a"), awaitLogbookIds(model, listOf("a")))
    }

    @Test
    fun reschedulingaCompletedTaskDoesNotRemoveItFromTheLogbook() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()
        logbook(model, 1)

        model.edit(id = "a", scheduledDate = today.plusDays(30))
        awaitEdited("a")

        // The Logbook ignores scheduling entirely.
        assertEquals(listOf("a"), awaitLogbookIds(model, listOf("a")))
    }

    @Test
    fun deletingFromTheLogbookUsesTheOrdinaryDeletionAndUndo() {
        store(task(id = "a", completedAt = completedAt))
        val model = viewModel()
        logbook(model, 1)

        model.deleteTask("a")
        assertEquals(PendingUndo.Deletion("a"), awaitUndoOffer(model))
        assertEquals(emptyList<String>(), awaitLogbookIds(model, emptyList()))
        // Soft, as everywhere: the row survives with its completion intact.
        assertNotNull(storedRow("a").deletedAt)
        assertEquals(completedAt, storedRow("a").completedAt)

        model.undoDelete("a")

        assertEquals(listOf("a"), awaitLogbookIds(model, listOf("a")))
    }

    @Test
    fun theRepositoryFlowDrivesTheLogbook() = runBlocking {
        val model = viewModel()
        assertEquals(emptyList<Task>(), model.completedTasks.value)

        store(task(id = "a", completedAt = completedAt))

        val shown = model.completedTasks.first { it.size == 1 }.single()
        assertEquals(repository.observeTasks().first().single { it.id == "a" }, shown)
    }

    @Test
    fun aCompletedTodayTaskIsInBothTodayAndTheLogbook() {
        store(task(id = "a", scheduledDate = today, completedAt = completedAt))
        val model = viewModel()

        // Today keeps its completed band; the Logbook keeps everything.
        assertEquals(listOf("a"), visible(model, 1).map { it.id })
        assertEquals(listOf("a"), logbook(model, 1).map { it.id })
    }

    // Midnight rollover

    @Test
    fun theDayItselfMovesForward() {
        val model = viewModel()
        assertEquals(today, model.today.value)

        currentDay.advanceTo(tomorrow)

        assertEquals(tomorrow, model.today.value)
    }

    @Test
    fun yesterdaysTaskBecomesOverdueAfterMidnight() {
        store(
            task(id = "wasToday", scheduledDate = today),
            task(id = "isNowToday", scheduledDate = tomorrow)
        )
        val model = viewModel()
        // Before: one Today task, one Upcoming task.
        assertEquals(listOf("wasToday"), visible(model, 1).map { it.id })

        currentDay.advanceTo(tomorrow)

        // After: both are in Today, the older one now overdue and below.
        assertEquals(
            listOf("isNowToday", "wasToday"),
            awaitTodayIds(model, listOf("isNowToday", "wasToday"))
        )
    }

    @Test
    fun tomorrowsTaskBecomesATodayTaskAfterMidnight() {
        store(task(id = "a", scheduledDate = tomorrow))
        val model = viewModel()
        assertEquals(emptyList<String>(), awaitTodayIds(model, emptyList()))
        assertEquals(listOf("a"), upcoming(model, 1).map { it.id })

        currentDay.advanceTo(tomorrow)

        assertEquals(listOf("a"), awaitTodayIds(model, listOf("a")))
        assertEquals(emptyList<String>(), awaitUpcomingIds(model, emptyList()))
    }

    @Test
    fun aTaskFurtherOutStaysUpcomingAfterMidnight() {
        store(task(id = "a", scheduledDate = today.plusDays(5)))
        val model = viewModel()
        assertEquals(listOf("a"), upcoming(model, 1).map { it.id })

        currentDay.advanceTo(tomorrow)

        assertEquals(listOf("a"), awaitUpcomingIds(model, listOf("a")))
        assertEquals(emptyList<String>(), awaitTodayIds(model, emptyList()))
    }

    @Test
    fun captureUsesTheNewDayAfterMidnight() {
        val model = viewModel()
        currentDay.advanceTo(tomorrow)

        // The screen reads the day at save time, as Quick Add does.
        model.createTask(title = "Chase the invoice", scheduledDate = model.today.value)

        assertEquals(tomorrow, awaitInsert().scheduledDate)
    }

    @Test
    fun captureFromInboxStaysUnscheduledAfterMidnight() {
        val model = viewModel()
        currentDay.advanceTo(tomorrow)

        model.createTask(title = "Replace the bulb", scheduledDate = null)

        val stored = awaitInsert()
        assertNull(stored.scheduledDate)
        assertEquals(TaskPlacement.INBOX, stored.placement)
    }

    @Test
    fun rollingOverRewritesNothing() = runBlocking {
        val original = task(
            id = "a",
            title = "Chase the missing invoice",
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today,
            dueDate = today.plusDays(3),
            estimatedDurationMinutes = 15
        )
        store(original)
        val model = viewModel()
        visible(model, 1)

        currentDay.advanceTo(tomorrow)
        awaitTodayIds(model, listOf("a"))

        // The task is classified differently; the task itself is untouched.
        repeat(20) { if (dao.updated.isEmpty()) Thread.sleep(10) }
        assertTrue(dao.updated.isEmpty())
        assertEquals(original, repository.observeTasks().first().single { it.id == "a" })
    }

    @Test
    fun theUndatedListsAreUnaffectedByRollover() {
        store(
            task(id = "inbox", placement = TaskPlacement.INBOX),
            task(id = "anytime", placement = TaskPlacement.ANYTIME),
            task(id = "someday", placement = TaskPlacement.SOMEDAY),
            task(id = "done", completedAt = completedAt)
        )
        val model = viewModel()
        inbox(model, 1)

        currentDay.advanceTo(tomorrow)

        assertEquals(listOf("inbox"), awaitInboxIds(model, listOf("inbox")))
        assertEquals(listOf("anytime"), awaitAnytimeIds(model, listOf("anytime")))
        assertEquals(listOf("someday"), awaitSomedayIds(model, listOf("someday")))
        assertEquals(listOf("done"), awaitLogbookIds(model, listOf("done")))
    }

    @Test
    fun completionStillWorksAcrossARollover() {
        store(task(id = "a", scheduledDate = tomorrow))
        val model = viewModel()
        upcoming(model, 1)
        currentDay.advanceTo(tomorrow)
        awaitTodayIds(model, listOf("a"))

        model.toggleComplete("a")

        assertEquals(PendingUndo.Completion("a"), awaitUndoOffer(model))
        assertNotNull(storedRow("a").completedAt)

        model.undoComplete("a")

        assertNull(awaitTodayTask(model) { !it.isCompleted }.completedAt)
    }

    @Test
    fun rolloverForwardThenBackReclassifiesBothWays() {
        store(task(id = "a", scheduledDate = tomorrow))
        val model = viewModel()
        upcoming(model, 1)

        currentDay.advanceTo(tomorrow)
        assertEquals(listOf("a"), awaitTodayIds(model, listOf("a")))

        // A corrected clock or a time-zone change can move the day back.
        currentDay.advanceTo(today)

        assertEquals(emptyList<String>(), awaitTodayIds(model, emptyList()))
        assertEquals(listOf("a"), awaitUpcomingIds(model, listOf("a")))
    }

    // Reminders
    //
    // The sheet is the only way a user can set one, so these cover the seam
    // between what they picked and what the scheduler will later read back.

    @Test
    fun savingAReminderStoresIt() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", reminderAt = reminder)

        assertEquals(reminder, awaitEdited("a").reminderAt)
    }

    @Test
    fun clearingAReminderStoresNothing() {
        store(task(id = "a", scheduledDate = today, reminderAt = reminder))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", reminderAt = null)

        assertNull(awaitEdited("a").reminderAt)
    }

    /**
     * The same regression the notes tests exist for, on the field where it
     * matters most. The sheet passes back every value it is holding, so an
     * edit to the title must arrive here carrying the reminder untouched.
     */
    @Test
    fun editingAnotherFieldDoesNotClearAnExistingReminder() {
        store(task(id = "a", scheduledDate = today, reminderAt = reminder))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Chase the missing invoice")

        val edited = awaitEdited("a")
        assertEquals("Chase the missing invoice", edited.title)
        assertEquals(reminder, edited.reminderAt)
    }

    @Test
    fun movingAReminderMakesItOwedAgain() {
        // Already announced once. Choosing a new time is the user asking to be
        // told again, so the record of the first delivery has to go.
        store(
            task(
                id = "a",
                scheduledDate = today,
                reminderAt = reminder,
                reminderDeliveredAt = createdAt
            )
        )
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", reminderAt = reminder.plusHours(1))

        val edited = awaitEdited("a")
        assertEquals(reminder.plusHours(1), edited.reminderAt)
        assertNull(edited.reminderDeliveredAt)
    }

    @Test
    fun editingATaskAfterItsReminderArrivedDoesNotAnnounceItTwice() {
        // The other half of the rule above, and the one a naive implementation
        // gets wrong: saving the sheet with the time untouched must not put a
        // reminder that has already been delivered back on the schedule.
        store(
            task(
                id = "a",
                scheduledDate = today,
                reminderAt = reminder,
                reminderDeliveredAt = createdAt
            )
        )
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", title = "Chase the missing invoice")

        val edited = awaitEdited("a")
        assertEquals(reminder, edited.reminderAt)
        assertEquals(createdAt, edited.reminderDeliveredAt)
    }

    @Test
    fun clearingAReminderAlsoClearsTheRecordOfItArriving() {
        // Otherwise a task that had a reminder, lost it, and was given a new
        // one at the same time as the old would arrive already delivered.
        store(
            task(
                id = "a",
                scheduledDate = today,
                reminderAt = reminder,
                reminderDeliveredAt = createdAt
            )
        )
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", reminderAt = null)

        assertNull(awaitEdited("a").reminderDeliveredAt)
    }

    // Notes

    @Test
    fun savingNotesStoresThem() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        val stored = visible(model, 1).single()

        model.editTask(
            id = stored.id,
            title = stored.title,
            notes = "Ask Priya which logo to use",
            placement = stored.placement,
            scheduledDate = stored.scheduledDate,
            dueDate = stored.dueDate,
            estimatedDurationMinutes = stored.estimatedDurationMinutes,
            recurrence = null,
            reminderAt = stored.reminderAt
        )

        assertEquals("Ask Priya which logo to use", awaitEdited("a").notes)
    }

    /**
     * The regression this phase exists to prevent.
     *
     * The arguments are the ones the details sheet produces for a title-only
     * change: it holds the whole task and edits a copy of it, so every field
     * the user did not touch, notes included, comes back exactly as stored.
     */
    @Test
    fun editingAnotherFieldDoesNotClearAnExistingNote() {
        store(task(id = "a", notes = "Ask Priya which logo to use", scheduledDate = today))
        val model = viewModel()
        val stored = visible(model, 1).single()
        assertEquals("Ask Priya which logo to use", stored.notes)

        model.editTask(
            id = stored.id,
            title = "Finish the landing page",
            notes = stored.notes,
            placement = stored.placement,
            scheduledDate = stored.scheduledDate,
            dueDate = stored.dueDate,
            estimatedDurationMinutes = stored.estimatedDurationMinutes,
            recurrence = null,
            reminderAt = stored.reminderAt
        )

        val edited = awaitEdited("a")
        assertEquals("Finish the landing page", edited.title)
        assertEquals("Ask Priya which logo to use", edited.notes)
    }

    @Test
    fun editingEveryOtherFieldDoesNotClearAnExistingNote() {
        store(task(id = "a", notes = "Bring the receipts", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        // Through the same helper the other edit tests use, which carries the
        // stored note exactly as the sheet does.
        model.edit(
            id = "a",
            title = "Renamed",
            placement = TaskPlacement.SOMEDAY,
            scheduledDate = tomorrow,
            dueDate = tomorrow,
            estimatedDurationMinutes = 30
        )

        val edited = awaitEdited("a")
        assertEquals("Renamed", edited.title)
        assertEquals(TaskPlacement.SOMEDAY, edited.placement)
        assertEquals(tomorrow, edited.scheduledDate)
        assertEquals(30, edited.estimatedDurationMinutes)
        assertEquals("Bring the receipts", edited.notes)
    }

    @Test
    fun clearingNotesStoresNull() {
        store(task(id = "a", notes = "No longer relevant", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", notes = "")

        assertNull(awaitEdited("a").notes)
    }

    @Test
    fun aWhitespaceOnlyNoteStoresNull() {
        store(task(id = "a", notes = "No longer relevant", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        // Blank and null both mean no note, so only one of them is stored.
        model.edit(id = "a", notes = "   ")

        assertNull(awaitEdited("a").notes)
    }

    @Test
    fun notesAreTrimmedBeforeStoring() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", notes = "  Ask about the deposit  ")

        assertEquals("Ask about the deposit", awaitEdited("a").notes)
    }

    @Test
    fun notesSurviveBeingReadBackFromStorage() {
        store(task(id = "a", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.edit(id = "a", notes = "Ask about the deposit")
        awaitEdited("a")

        // A fresh view model over the same storage, as reopening the sheet or
        // relaunching the app would give.
        val reopened = viewModel()
        assertEquals("Ask about the deposit", visible(reopened, 1).single().notes)
    }

    @Test
    fun editingCannotChangeANoteOnADifferentTask() {
        store(
            task(id = "a", notes = "Keep me", scheduledDate = today),
            task(id = "b", notes = "Keep me too", scheduledDate = today)
        )
        val model = viewModel()
        visible(model, 2)

        model.edit(id = "a", notes = "Rewritten")
        awaitEdited("a")

        assertEquals("Keep me too", visible(model, 2).single { it.id == "b" }.notes)
    }

    // Focus

    @Test
    fun focusedTaskDefaultsToTheQueueHead() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )

        assertEquals("a", awaitFocusedTaskId(viewModel(), "a"))
    }

    @Test
    fun focusTaskSelectsThatTask() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()

        model.focusTask("b")

        assertEquals("b", awaitFocusedTaskId(model, "b"))
    }

    // Focus is entered by choosing a task

    /**
     * Choosing and starting are one act now that Focus is a sheet opened from
     * the task it is for. Two calls left a window in which a task was chosen
     * and no session was running, which is exactly the Ready state that no
     * longer exists.
     */
    @Test
    fun beginFocusChoosesTheTaskAndStartsTheSession() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()

        model.beginFocus("b")

        assertEquals("b", awaitFocusedTaskId(model, "b"))
        assertTrue(model.isFocusSessionActive.value)
        assertNotNull(model.focusSessionStartedAt.value)
    }

    /**
     * The running flag is what puts the sheet on screen, so the chosen task has
     * to go when the session does. Left behind, it would be a choice nobody
     * made, waiting to reopen on a task the user had walked away from.
     */
    @Test
    fun stoppingForgetsWhichTaskWasChosen() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()
        model.beginFocus("b")
        awaitFocusedTaskId(model, "b")

        model.stopFocusSession()

        assertEquals(false, model.isFocusSessionActive.value)
        assertNull(model.focusSessionStartedAt.value)
        // Back to the head of the queue rather than still pointing at "b".
        assertEquals("a", awaitFocusedTaskId(model, "a"))
    }

    /**
     * The whole reason the sheet stays open when a task is finished. Completing
     * inside Focus has to keep the session running, or the mode would end every
     * time it succeeded.
     */
    @Test
    fun completingInsideASessionKeepsItRunning() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()
        model.beginFocus("a")
        awaitFocusedTaskId(model, "a")

        model.toggleComplete("a")

        assertEquals("b", awaitFocusedTaskId(model, "b"))
        assertTrue(model.isFocusSessionActive.value)
    }

    @Test
    fun completingTheFocusedTaskShowsTheNextOne() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()
        assertEquals("a", awaitFocusedTaskId(model, "a"))

        model.toggleComplete("a")

        assertEquals("b", awaitFocusedTaskId(model, "b"))
    }

    @Test
    fun reschedulingTheFocusedTaskOutOfTodayShowsTheNextOne() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()
        assertEquals("a", awaitFocusedTaskId(model, "a"))

        model.editTask(
            id = "a",
            title = "Task a",
            notes = null,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = tomorrow,
            dueDate = null,
            estimatedDurationMinutes = null,
            recurrence = null,
            reminderAt = null
        )

        assertEquals("b", awaitFocusedTaskId(model, "b"))
    }

    @Test
    fun deletingTheFocusedTaskShowsTheNextOne() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )
        val model = viewModel()
        assertEquals("a", awaitFocusedTaskId(model, "a"))

        model.deleteTask("a")

        assertEquals("b", awaitFocusedTaskId(model, "b"))
    }

    @Test
    fun focusingATaskOutsideTheQueueFallsBackToTheHead() {
        store(
            task(id = "a", scheduledDate = today),
            task(id = "later", scheduledDate = tomorrow)
        )
        val model = viewModel()

        // Nothing rejects the id. It simply never matches anything in the
        // queue, which is the same path a task takes when it leaves.
        model.focusTask("later")

        assertEquals("a", awaitFocusedTaskId(model, "a"))
    }

    /**
     * Waits for Focus to land on [id], then reports what it actually holds.
     *
     * Bounded, so a wrong task fails the assertion that follows instead of
     * hanging the run.
     */
    private fun awaitFocusedTaskId(model: TaskListViewModel, id: String?): String? =
        runBlocking {
            val matched = withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.focusedTask.first { task -> task?.id == id }
                true
            }

            if (matched == true) id else model.focusedTask.value?.id
        }

    // Announcing the estimate

    @Test
    fun startingASessionSchedulesTheEstimateForTheFocusedTask() {
        store(task(id = "a", scheduledDate = today, estimatedDurationMinutes = 45))
        val model = viewModel()
        awaitFocusedTaskId(model, "a")

        model.startFocusSession()

        val (title, at) = awaitScheduled()
        assertEquals("Task a", title)
        // Forty-five minutes after the clock started, give or take the moment
        // the test took to get here.
        val started = model.focusSessionStartedAt.value!!
        assertEquals(started.plusSeconds(45 * 60), at)
    }

    @Test
    fun aTaskWithNoEstimateAnnouncesNothing() {
        store(task(id = "a", scheduledDate = today, estimatedDurationMinutes = null))
        val model = viewModel()
        awaitFocusedTaskId(model, "a")

        model.startFocusSession()

        // Nothing to be a fraction of, so nothing to announce.
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun stoppingASessionCancelsTheAnnouncement() {
        store(task(id = "a", scheduledDate = today, estimatedDurationMinutes = 45))
        val model = viewModel()
        awaitFocusedTaskId(model, "a")
        model.startFocusSession()
        awaitScheduled()

        model.stopFocusSession()

        assertTrue(alarms.cancellations > 0)
    }

    @Test
    fun movingToTheNextTaskRestartsTheClockAndReschedules() {
        store(
            task(id = "a", scheduledDate = today, estimatedDurationMinutes = 45),
            task(id = "b", scheduledDate = today, estimatedDurationMinutes = 15)
        )
        val model = viewModel()
        awaitFocusedTaskId(model, "a")
        model.startFocusSession()
        val firstStart = model.focusSessionStartedAt.value!!
        awaitScheduled()

        model.toggleComplete("a")
        awaitFocusedTaskId(model, "b")

        // The clock measures this task against its own estimate, not the
        // session against the first task's. Without the restart, a fifteen
        // minute task picked up after forty minutes of work would be announced
        // as overrun before it had been started.
        val secondStart = awaitClockRestart(model, firstStart)
        assertTrue(secondStart.isAfter(firstStart))

        val (title, at) = awaitScheduled { it.first == "Task b" }
        assertEquals("Task b", title)
        assertEquals(secondStart.plusSeconds(15 * 60), at)
    }

    /** Waits for an alarm to be scheduled, optionally matching [predicate]. */
    private fun awaitScheduled(
        predicate: (Pair<String, Instant>) -> Boolean = { true }
    ): Pair<String, Instant> {
        repeat(200) {
            alarms.scheduled.lastOrNull(predicate)?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }

        throw AssertionError("no matching alarm scheduled; saw ${alarms.scheduled}")
    }

    /** Waits for the session clock to be restarted away from [previous]. */
    private fun awaitClockRestart(model: TaskListViewModel, previous: Instant): Instant {
        repeat(200) {
            val current = model.focusSessionStartedAt.value
            if (current != null && current != previous) return current
            Thread.sleep(POLL_MILLIS)
        }

        throw AssertionError("session clock never restarted")
    }

    /** Waits for the Today state to hold a single task satisfying [predicate]. */
    private fun awaitTodayTask(model: TaskListViewModel, predicate: (Task) -> Boolean): Task =
        runBlocking {
            model.todayTasks
                .first { tasks -> tasks.singleOrNull()?.let(predicate) == true }
                .single()
        }

    /** Waits for the delete coroutine to reach the DAO. */
    private fun awaitSoftDelete(): Pair<String, Long> {
        repeat(200) {
            dao.softDeleted.firstOrNull()?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("no soft delete reached the DAO")
    }

    /** Waits for the undo coroutine to reach the DAO. */
    private fun awaitRestore(): String {
        repeat(200) {
            dao.restored.firstOrNull()?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("no restore reached the DAO")
    }

    /** Waits for an action to raise its undo offer. */
    private fun awaitUndoOffer(model: TaskListViewModel): PendingUndo = runBlocking {
        model.pendingUndo.first { it != null }!!
    }

    /** Waits for a specific offer, so a superseded one is not mistaken for it. */
    private fun awaitUndoOffer(model: TaskListViewModel, offer: PendingUndo): PendingUndo =
        runBlocking { model.pendingUndo.first { it == offer }!! }

    /**
     * Waits for Today to read as [ids], then returns what it actually holds.
     *
     * Bounded, so a wrong order fails the assertion that follows instead of
     * hanging the run.
     */
    private fun awaitTodayIds(model: TaskListViewModel, ids: List<String>): List<String> =
        runBlocking {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                model.todayTasks.first { tasks -> tasks.map { it.id } == ids }
            } ?: model.todayTasks.value
        }.map { it.id }

    /** The row as it is actually stored, including rows no longer observed. */
    private fun storedRow(id: String): TaskEntity = dao.emissions.value.single { it.id == id }

    /** Waits for the create coroutine to reach the DAO. */
    private fun awaitInsert(): TaskEntity {
        repeat(200) {
            dao.inserted.firstOrNull()?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("no insert reached the DAO")
    }

    /** Waits for [count] inserts, so a test can compare captures. */
    private fun awaitInserts(count: Int): List<TaskEntity> {
        repeat(200) {
            if (dao.inserted.size >= count) return dao.inserted.toList()
            Thread.sleep(10)
        }
        throw AssertionError("only ${dao.inserted.size} of $count inserts reached the DAO")
    }

    // Recurrence

    /** Waits for a row to stop being observed. */
    private fun awaitDeletion(id: String) {
        repeat(200) {
            if (dao.deleted.contains(id)) return
            Thread.sleep(POLL_MILLIS)
        }

        throw AssertionError("$id was not deleted")
    }

    @Test
    fun completingATaskThatDoesNotRecurInsertsNothing() {
        store(task("once", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("once")
        awaitUpdate()

        // Give the spawn a chance to happen before concluding it did not.
        Thread.sleep(100)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun completingARecurringTaskStartsTheNextOccurrence() {
        store(task("chore", scheduledDate = today, recurrence = Recurrence.DAILY))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("chore")

        val next = awaitInsert()
        assertEquals("Task chore", next.title)
        assertEquals(tomorrow, next.scheduledDate)
        assertEquals(Recurrence.DAILY, next.recurrence)
        assertNull(next.completedAt)
    }

    @Test
    fun theNextOccurrenceIsANewTaskRatherThanTheSameOneMoved() {
        store(task("chore", scheduledDate = today, recurrence = Recurrence.DAILY))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("chore")
        val next = awaitInsert()

        // A new id, so the completed one keeps its own place in the Logbook
        // rather than being rewritten into the future.
        assertTrue(next.id != "chore")
        assertNotNull(awaitUpdate().completedAt)
    }

    @Test
    fun theNextOccurrenceRecordsWhichOneItCameFrom() {
        store(task("chore", scheduledDate = today, recurrence = Recurrence.DAILY))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("chore")

        assertEquals("chore", awaitInsert().spawnedFromId)
    }

    @Test
    fun reopeningARecurringTaskByHandTakesTheSpawnedOccurrenceBackWithIt() {
        store(task("chore", scheduledDate = today, recurrence = Recurrence.DAILY))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("chore")
        val spawn = awaitInsert()
        // Past the four seconds the undo offer stands for: the checkbox is the
        // permanent way back and has to behave like the snackbar, or ticking a
        // daily task and unticking it a moment later leaves two.
        model.dismissUndo("chore")

        model.toggleComplete("chore")
        awaitDeletion(spawn.id)

        // What is left is the task the user started with, reopened, and only
        // that one.
        val remaining = runBlocking { repository.observeTasks().first { it.size == 1 } }
        assertEquals("chore", remaining.single().id)
        assertNull(remaining.single().completedAt)
    }

    @Test
    fun reopeningLeavesASpawnTheUserHasAlreadyFinished() {
        store(
            task("chore", scheduledDate = today, recurrence = Recurrence.DAILY),
            task("spawn", scheduledDate = tomorrow, recurrence = Recurrence.DAILY)
                .copy(spawnedFromId = "chore", completedAt = completedAt)
        )
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("chore")
        awaitUpdate()

        // A finished spawn has its own record and its own successor. Deleting
        // it would be destroying work rather than tidying up a row nobody
        // asked for.
        Thread.sleep(100)
        assertTrue(dao.deleted.none { it == "spawn" })
    }

    @Test
    fun undoingACompletionTakesTheSpawnedOccurrenceBackWithIt() {
        store(task("chore", scheduledDate = today, recurrence = Recurrence.DAILY))
        val model = viewModel()
        visible(model, 1)

        model.toggleComplete("chore")
        val next = awaitInsert()

        model.undoComplete("chore")
        awaitDeletion(next.id)

        // The task the user actually has is the one they started with, and
        // only that one.
        val remaining = runBlocking { repository.observeTasks().first { it.size == 1 } }
        assertEquals("chore", remaining.single().id)
        assertNull(remaining.single().completedAt)
    }

    @Test
    fun reopeningARecurringTaskByHandDoesNotStartAnything() {
        store(
            task(
                "chore",
                scheduledDate = today,
                recurrence = Recurrence.DAILY,
                completedAt = completedAt
            )
        )
        val model = viewModel()
        runBlocking { model.completedTasks.first { it.size == 1 } }

        model.toggleComplete("chore")
        awaitUpdate()

        Thread.sleep(100)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun editingATaskWritesItsRecurrence() {
        store(task("chore", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.editTask(
            id = "chore",
            title = "Task chore",
            notes = null,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today,
            dueDate = null,
            estimatedDurationMinutes = null,
            recurrence = Recurrence.WEEKLY,
            reminderAt = null
        )

        assertEquals(Recurrence.WEEKLY, awaitUpdate().recurrence)
    }

    // Rescheduling

    @Test
    fun reschedulingMovesTheTaskToTheDayItWasGiven() {
        store(task("move", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.rescheduleTask("move", tomorrow)

        assertEquals(tomorrow, awaitUpdate().scheduledDate)
    }

    @Test
    fun reschedulingATaskWithNoDayGivesItOne() {
        store(task("undated", placement = TaskPlacement.INBOX))
        val model = viewModel()
        runBlocking { model.inboxTasks.first { it.size == 1 } }

        model.rescheduleTask("undated", today)

        assertEquals(today, awaitUpdate().scheduledDate)
    }

    @Test
    fun reschedulingToNoDayTakesTheDayAway() {
        store(task("dated", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.rescheduleTask("dated", null)

        assertNull(awaitUpdate().scheduledDate)
    }

    @Test
    fun reschedulingOffersAWayBack() {
        store(task("move", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.rescheduleTask("move", tomorrow)
        awaitUpdate()

        val offer = runBlocking {
            model.pendingUndo.first { it is PendingUndo.Reschedule } as PendingUndo.Reschedule
        }

        assertEquals("move", offer.taskId)
        assertEquals(today, offer.previousDate)
    }

    @Test
    fun undoingARescheduleReturnsTheTaskToTheDayItWasOn() {
        store(task("move", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.rescheduleTask("move", tomorrow)
        runBlocking { model.pendingUndo.first { it is PendingUndo.Reschedule } }

        model.undoReschedule("move")

        val restored = runBlocking {
            repository.observeTasks().first { tasks ->
                tasks.single().scheduledDate == today
            }
        }
        assertEquals(today, restored.single().scheduledDate)
    }

    @Test
    fun undoingARescheduleReturnsATaskThatHadNoDayToHavingNone() {
        store(task("undated", placement = TaskPlacement.INBOX))
        val model = viewModel()
        runBlocking { model.inboxTasks.first { it.size == 1 } }

        model.rescheduleTask("undated", today)
        runBlocking { model.pendingUndo.first { it is PendingUndo.Reschedule } }

        model.undoReschedule("undated")

        val restored = runBlocking {
            repository.observeTasks().first { tasks -> tasks.single().scheduledDate == null }
        }
        assertNull(restored.single().scheduledDate)
    }

    @Test
    fun reschedulingToTheDayTheTaskIsAlreadyOnChangesNothing() {
        store(task("stay", scheduledDate = today))
        val model = viewModel()
        visible(model, 1)

        model.rescheduleTask("stay", today)

        Thread.sleep(100)
        assertTrue(dao.updated.isEmpty())
        assertNull(model.pendingUndo.value)
    }
}

/**
 * Records what the view model asked the system to announce.
 *
 * A copy of the semantics suite's fake rather than a shared one: the two source
 * sets do not see each other, and a fake this small is cheaper duplicated than
 * hoisted into main just so tests can share it.
 */
private class RecordingFocusAlarms : FocusAlarms {

    val scheduled = mutableListOf<Pair<String, Instant>>()
    var cancellations = 0
        private set

    override fun scheduleEstimateReached(taskTitle: String, at: Instant) {
        scheduled += taskTitle to at
    }

    override fun cancel() {
        cancellations++
    }
}
