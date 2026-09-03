package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TaskQueriesTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 31)
    private val yesterday: LocalDate = today.minusDays(1)
    private val tomorrow: LocalDate = today.plusDays(1)
    private val timestamp: Instant = Instant.parse("2026-08-31T09:00:00Z")

    private fun task(
        id: String,
        placement: TaskPlacement = TaskPlacement.ANYTIME,
        scheduledDate: LocalDate? = null,
        completedAt: Instant? = null,
        deletedAt: Instant? = null,
        createdAt: Instant = timestamp
    ) = Task(
        id = id,
        title = "Task $id",
        createdAt = createdAt,
        placement = placement,
        scheduledDate = scheduledDate,
        completedAt = completedAt,
        deletedAt = deletedAt
    )

    private fun ids(tasks: List<Task>) = tasks.map { it.id }

    // Today

    @Test
    fun `today includes a task scheduled for today`() {
        val scheduledToday = task(id = "a", scheduledDate = today)

        assertEquals(listOf("a"), ids(todayTasks(listOf(scheduledToday), today)))
    }

    @Test
    fun `today includes an overdue task`() {
        val overdue = task(id = "a", scheduledDate = yesterday)

        assertEquals(listOf("a"), ids(todayTasks(listOf(overdue), today)))
    }

    @Test
    fun `today excludes a future task`() {
        val future = task(id = "a", scheduledDate = tomorrow)

        assertEquals(emptyList<String>(), ids(todayTasks(listOf(future), today)))
    }

    @Test
    fun `today keeps a completed task scheduled for today`() {
        val completed = task(id = "a", scheduledDate = today, completedAt = timestamp)

        assertEquals(listOf("a"), ids(todayTasks(listOf(completed), today)))
    }

    @Test
    fun `today excludes a deleted task`() {
        val deleted = task(id = "a", scheduledDate = today, deletedAt = timestamp)

        assertEquals(emptyList<String>(), ids(todayTasks(listOf(deleted), today)))
    }

    @Test
    fun `today excludes an unscheduled task`() {
        val unscheduled = task(id = "a", scheduledDate = null)

        assertEquals(emptyList<String>(), ids(todayTasks(listOf(unscheduled), today)))
    }

    @Test
    fun `today preserves the order it was given`() {
        val tasks = listOf(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = tomorrow),
            task(id = "c", scheduledDate = yesterday)
        )

        assertEquals(listOf("a", "c"), ids(todayTasks(tasks, today)))
    }

    // Today ordering

    @Test
    fun `today puts scheduled tasks before overdue ones`() {
        val tasks = listOf(
            task(id = "overdue", scheduledDate = yesterday),
            task(id = "today", scheduledDate = today)
        )

        assertEquals(listOf("today", "overdue"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `today puts incomplete tasks before completed ones`() {
        val tasks = listOf(
            task(id = "done", scheduledDate = today, completedAt = timestamp),
            task(id = "outstanding", scheduledDate = today)
        )

        assertEquals(listOf("outstanding", "done"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `today orders scheduled then overdue then completed`() {
        val tasks = listOf(
            task(id = "done", scheduledDate = today, completedAt = timestamp),
            task(id = "overdue", scheduledDate = yesterday),
            task(id = "today", scheduledDate = today)
        )

        assertEquals(listOf("today", "overdue", "done"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `several tasks scheduled today keep their input order`() {
        val tasks = listOf(
            task(id = "third", scheduledDate = today),
            task(id = "first", scheduledDate = today),
            task(id = "second", scheduledDate = today)
        )

        assertEquals(listOf("third", "first", "second"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `several overdue tasks keep their input order`() {
        val tasks = listOf(
            task(id = "c", scheduledDate = yesterday),
            task(id = "a", scheduledDate = today.minusDays(9)),
            task(id = "b", scheduledDate = today.minusDays(3))
        )

        // Not sorted by how overdue they are, and not by id.
        assertEquals(listOf("c", "a", "b"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `several completed tasks keep their input order`() {
        val tasks = listOf(
            task(id = "c", scheduledDate = today, completedAt = timestamp),
            task(id = "a", scheduledDate = yesterday, completedAt = timestamp),
            task(id = "b", scheduledDate = today, completedAt = timestamp)
        )

        assertEquals(listOf("c", "a", "b"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `a completed overdue task still sits after every incomplete task`() {
        val tasks = listOf(
            task(id = "completedOverdue", scheduledDate = yesterday, completedAt = timestamp),
            task(id = "overdue", scheduledDate = yesterday),
            task(id = "today", scheduledDate = today)
        )

        assertEquals(listOf("today", "overdue", "completedOverdue"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `a completed task scheduled today sits with the completed tasks`() {
        val tasks = listOf(
            task(id = "completedToday", scheduledDate = today, completedAt = timestamp),
            task(id = "overdue", scheduledDate = yesterday)
        )

        // Completion outranks being scheduled for today.
        assertEquals(listOf("overdue", "completedToday"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `an already ordered list is left alone`() {
        val tasks = listOf(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today),
            task(id = "c", scheduledDate = yesterday),
            task(id = "d", scheduledDate = today, completedAt = timestamp)
        )

        assertEquals(listOf("a", "b", "c", "d"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `ordering ignores id and title`() {
        // Ids descend while the correct order ascends, so any sort by id or by
        // the derived title would produce the reverse.
        val tasks = listOf(
            task(id = "zzz", scheduledDate = today),
            task(id = "mmm", scheduledDate = yesterday),
            task(id = "aaa", scheduledDate = today, completedAt = timestamp)
        )

        assertEquals(listOf("zzz", "mmm", "aaa"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `ordering still excludes deleted and unscheduled tasks`() {
        val tasks = listOf(
            task(id = "deleted", scheduledDate = today, deletedAt = timestamp),
            task(id = "today", scheduledDate = today),
            task(id = "unscheduled", placement = TaskPlacement.INBOX),
            task(id = "overdue", scheduledDate = yesterday),
            task(id = "deletedOverdue", scheduledDate = yesterday, deletedAt = timestamp)
        )

        assertEquals(listOf("today", "overdue"), ids(todayTasks(tasks, today)))
    }

    @Test
    fun `ordering is idempotent`() {
        val tasks = listOf(
            task(id = "done", scheduledDate = today, completedAt = timestamp),
            task(id = "overdue", scheduledDate = yesterday),
            task(id = "today", scheduledDate = today)
        )

        val once = todayTasks(tasks, today)

        assertEquals(ids(once), ids(todayTasks(once, today)))
    }

    // Upcoming

    @Test
    fun `upcoming includes a future task`() {
        val future = task(id = "a", scheduledDate = tomorrow)

        assertEquals(listOf("a"), ids(upcomingTasks(listOf(future), today)))
    }

    @Test
    fun `upcoming excludes today and overdue tasks`() {
        val tasks = listOf(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = yesterday)
        )

        assertEquals(emptyList<String>(), ids(upcomingTasks(tasks, today)))
    }

    @Test
    fun `upcoming excludes a completed task`() {
        val completed = task(id = "a", scheduledDate = tomorrow, completedAt = timestamp)

        assertEquals(emptyList<String>(), ids(upcomingTasks(listOf(completed), today)))
    }

    @Test
    fun `upcoming excludes a deleted task`() {
        val deleted = task(id = "a", scheduledDate = tomorrow, deletedAt = timestamp)

        assertEquals(emptyList<String>(), ids(upcomingTasks(listOf(deleted), today)))
    }

    @Test
    fun `upcoming excludes an unscheduled task`() {
        val unscheduled = task(id = "a", scheduledDate = null)

        assertEquals(emptyList<String>(), ids(upcomingTasks(listOf(unscheduled), today)))
    }

    // Upcoming ordering

    @Test
    fun `upcoming puts the nearest task first`() {
        val tasks = listOf(
            task(id = "far", scheduledDate = today.plusDays(30)),
            task(id = "near", scheduledDate = tomorrow),
            task(id = "middle", scheduledDate = today.plusDays(7))
        )

        assertEquals(listOf("near", "middle", "far"), ids(upcomingTasks(tasks, today)))
    }

    @Test
    fun `upcoming keeps input order within a single day`() {
        val tasks = listOf(
            task(id = "c", scheduledDate = tomorrow),
            task(id = "a", scheduledDate = tomorrow),
            task(id = "b", scheduledDate = tomorrow)
        )

        // Not sorted by id, and not reordered at all.
        assertEquals(listOf("c", "a", "b"), ids(upcomingTasks(tasks, today)))
    }

    @Test
    fun `upcoming ordering ignores id and title`() {
        val tasks = listOf(
            task(id = "zzz", scheduledDate = tomorrow),
            task(id = "aaa", scheduledDate = today.plusDays(4))
        )

        assertEquals(listOf("zzz", "aaa"), ids(upcomingTasks(tasks, today)))
    }

    @Test
    fun `an already ordered upcoming list is left alone`() {
        val tasks = listOf(
            task(id = "a", scheduledDate = tomorrow),
            task(id = "b", scheduledDate = today.plusDays(2)),
            task(id = "c", scheduledDate = today.plusDays(3))
        )

        assertEquals(listOf("a", "b", "c"), ids(upcomingTasks(tasks, today)))
    }

    @Test
    fun `upcoming ordering is idempotent`() {
        val tasks = listOf(
            task(id = "far", scheduledDate = today.plusDays(30)),
            task(id = "near", scheduledDate = tomorrow)
        )

        val once = upcomingTasks(tasks, today)

        assertEquals(ids(once), ids(upcomingTasks(once, today)))
    }

    @Test
    fun `upcoming ordering still excludes everything it filtered before`() {
        val tasks = listOf(
            task(id = "completed", scheduledDate = tomorrow, completedAt = timestamp),
            task(id = "far", scheduledDate = today.plusDays(9)),
            task(id = "deleted", scheduledDate = tomorrow, deletedAt = timestamp),
            task(id = "near", scheduledDate = tomorrow),
            task(id = "today", scheduledDate = today),
            task(id = "unscheduled", placement = TaskPlacement.INBOX)
        )

        assertEquals(listOf("near", "far"), ids(upcomingTasks(tasks, today)))
    }

    // Placement views

    @Test
    fun `inbox returns only inbox tasks`() {
        val tasks = listOf(
            task(id = "a", placement = TaskPlacement.INBOX),
            task(id = "b", placement = TaskPlacement.ANYTIME),
            task(id = "c", placement = TaskPlacement.SOMEDAY)
        )

        assertEquals(listOf("a"), ids(inboxTasks(tasks)))
    }

    // Completed

    @Test
    fun `completed includes a completed task`() {
        val done = task(id = "a", completedAt = timestamp)

        assertEquals(listOf("a"), ids(completedTasks(listOf(done))))
    }

    @Test
    fun `completed excludes an outstanding task`() {
        val outstanding = task(id = "a")

        assertEquals(emptyList<String>(), ids(completedTasks(listOf(outstanding))))
    }

    @Test
    fun `completed excludes a deleted task`() {
        val deleted = task(id = "a", completedAt = timestamp, deletedAt = timestamp)

        assertEquals(emptyList<String>(), ids(completedTasks(listOf(deleted))))
    }

    @Test
    fun `completed ignores placement`() {
        val tasks = listOf(
            task(id = "inbox", placement = TaskPlacement.INBOX, completedAt = timestamp),
            task(id = "anytime", placement = TaskPlacement.ANYTIME, completedAt = timestamp),
            task(id = "someday", placement = TaskPlacement.SOMEDAY, completedAt = timestamp)
        )

        assertEquals(listOf("inbox", "anytime", "someday"), ids(completedTasks(tasks)))
    }

    @Test
    fun `completed ignores scheduling`() {
        val tasks = listOf(
            task(id = "unscheduled", completedAt = timestamp),
            task(id = "past", scheduledDate = yesterday, completedAt = timestamp),
            task(id = "today", scheduledDate = today, completedAt = timestamp),
            task(id = "future", scheduledDate = tomorrow, completedAt = timestamp)
        )

        assertEquals(
            listOf("unscheduled", "past", "today", "future"),
            ids(completedTasks(tasks))
        )
    }

    @Test
    fun `completed puts the most recently finished task first`() {
        val tasks = listOf(
            task(id = "middle", completedAt = timestamp),
            task(id = "oldest", completedAt = timestamp.minusSeconds(600)),
            task(id = "newest", completedAt = timestamp.plusSeconds(600))
        )

        assertEquals(listOf("newest", "middle", "oldest"), ids(completedTasks(tasks)))
    }

    @Test
    fun `completed orders by completion time, not creation time`() {
        val tasks = listOf(
            task(
                id = "madeFirstFinishedLast",
                createdAt = timestamp.minusSeconds(600),
                completedAt = timestamp.plusSeconds(600)
            ),
            task(
                id = "madeLastFinishedFirst",
                createdAt = timestamp.plusSeconds(600),
                completedAt = timestamp
            )
        )

        assertEquals(
            listOf("madeFirstFinishedLast", "madeLastFinishedFirst"),
            ids(completedTasks(tasks))
        )
    }

    @Test
    fun `completed keeps input order for equal completion times`() {
        val tasks = listOf(
            task(id = "c", completedAt = timestamp),
            task(id = "a", completedAt = timestamp),
            task(id = "b", completedAt = timestamp)
        )

        assertEquals(listOf("c", "a", "b"), ids(completedTasks(tasks)))
    }

    @Test
    fun `completed ordering is idempotent`() {
        val tasks = listOf(
            task(id = "old", completedAt = timestamp),
            task(id = "new", completedAt = timestamp.plusSeconds(60))
        )

        val once = completedTasks(tasks)

        assertEquals(ids(once), ids(completedTasks(once)))
    }

    @Test
    fun `every completed task is reachable somewhere`() {
        // The hole this list closes: before it, a completed task that was not
        // scheduled on or before today appeared in no list at all.
        val tasks = listOf(
            task(id = "unscheduled", placement = TaskPlacement.INBOX, completedAt = timestamp),
            task(id = "future", scheduledDate = tomorrow, completedAt = timestamp),
            task(id = "anytime", placement = TaskPlacement.ANYTIME, completedAt = timestamp)
        )

        assertEquals(emptyList<String>(), ids(todayTasks(tasks, today)))
        assertEquals(emptyList<String>(), ids(upcomingTasks(tasks, today)))
        assertEquals(emptyList<String>(), ids(inboxTasks(tasks)))
        assertEquals(emptyList<String>(), ids(anytimeTasks(tasks)))
        assertEquals(listOf("unscheduled", "future", "anytime"), ids(completedTasks(tasks)))
    }

    @Test
    fun `inbox excludes a scheduled task`() {
        val scheduled = task(id = "a", placement = TaskPlacement.INBOX, scheduledDate = today)

        // Giving a task a day is a decision, so it is no longer untriaged.
        assertEquals(emptyList<String>(), ids(inboxTasks(listOf(scheduled))))
    }

    @Test
    fun `inbox excludes tasks scheduled for any day`() {
        val tasks = listOf(
            task(id = "yesterday", placement = TaskPlacement.INBOX, scheduledDate = yesterday),
            task(id = "today", placement = TaskPlacement.INBOX, scheduledDate = today),
            task(id = "tomorrow", placement = TaskPlacement.INBOX, scheduledDate = tomorrow),
            task(id = "unscheduled", placement = TaskPlacement.INBOX)
        )

        assertEquals(listOf("unscheduled"), ids(inboxTasks(tasks)))
    }

    @Test
    fun `inbox excludes a completed task`() {
        val completed = task(id = "a", placement = TaskPlacement.INBOX, completedAt = timestamp)

        assertEquals(emptyList<String>(), ids(inboxTasks(listOf(completed))))
    }

    @Test
    fun `inbox puts the newest capture first`() {
        val tasks = listOf(
            task(id = "middle", placement = TaskPlacement.INBOX, createdAt = timestamp),
            task(
                id = "oldest",
                placement = TaskPlacement.INBOX,
                createdAt = timestamp.minusSeconds(600)
            ),
            task(
                id = "newest",
                placement = TaskPlacement.INBOX,
                createdAt = timestamp.plusSeconds(600)
            )
        )

        assertEquals(listOf("newest", "middle", "oldest"), ids(inboxTasks(tasks)))
    }

    @Test
    fun `inbox keeps input order for captures sharing a timestamp`() {
        val tasks = listOf(
            task(id = "c", placement = TaskPlacement.INBOX),
            task(id = "a", placement = TaskPlacement.INBOX),
            task(id = "b", placement = TaskPlacement.INBOX)
        )

        // Not sorted by id, and not reordered at all.
        assertEquals(listOf("c", "a", "b"), ids(inboxTasks(tasks)))
    }

    @Test
    fun `inbox ordering is idempotent`() {
        val tasks = listOf(
            task(id = "old", placement = TaskPlacement.INBOX, createdAt = timestamp),
            task(
                id = "new",
                placement = TaskPlacement.INBOX,
                createdAt = timestamp.plusSeconds(60)
            )
        )

        val once = inboxTasks(tasks)

        assertEquals(ids(once), ids(inboxTasks(once)))
    }

    @Test
    fun `anytime and someday hold undated work only`() {
        val tasks = listOf(
            task(id = "scheduled", placement = TaskPlacement.ANYTIME, scheduledDate = tomorrow),
            task(id = "unscheduled", placement = TaskPlacement.ANYTIME)
        )

        // Giving a task a day is the decision these lists are waiting for.
        assertEquals(listOf("unscheduled"), ids(anytimeTasks(tasks)))
    }

    @Test
    fun `a dated anytime task is in today and not in anytime`() {
        val scheduled = task(id = "a", placement = TaskPlacement.ANYTIME, scheduledDate = today)
        val tasks = listOf(scheduled)

        // These used to overlap. A task sitting in Today and in Anytime at
        // once meant the lists were not a partition and the task had no single
        // home to be found in.
        assertEquals(listOf("a"), ids(todayTasks(tasks, today)))
        assertEquals(emptyList<String>(), ids(anytimeTasks(tasks)))
    }

    @Test
    fun `a dated someday task is in upcoming and not in someday`() {
        val scheduled = task(id = "a", placement = TaskPlacement.SOMEDAY, scheduledDate = tomorrow)
        val tasks = listOf(scheduled)

        // The sharper half of the same problem: Someday means deliberately
        // deferred, and a day on the task is the calendar calling it due.
        assertEquals(listOf("a"), ids(upcomingTasks(tasks, today)))
        assertEquals(emptyList<String>(), ids(somedayTasks(tasks)))
    }

    @Test
    fun `the three undated lists partition the undated work`() {
        val tasks = listOf(
            task(id = "inbox", placement = TaskPlacement.INBOX),
            task(id = "anytime", placement = TaskPlacement.ANYTIME),
            task(id = "someday", placement = TaskPlacement.SOMEDAY),
            task(id = "dated", placement = TaskPlacement.ANYTIME, scheduledDate = tomorrow)
        )

        // Every undated task in exactly one list, and the dated one in none.
        assertEquals(listOf("inbox"), ids(inboxTasks(tasks)))
        assertEquals(listOf("anytime"), ids(anytimeTasks(tasks)))
        assertEquals(listOf("someday"), ids(somedayTasks(tasks)))
    }

    @Test
    fun `placement views exclude completed tasks`() {
        val tasks = listOf(
            task(id = "a", placement = TaskPlacement.ANYTIME, completedAt = timestamp),
            task(id = "b", placement = TaskPlacement.SOMEDAY, completedAt = timestamp)
        )

        assertEquals(emptyList<String>(), ids(anytimeTasks(tasks)))
        assertEquals(emptyList<String>(), ids(somedayTasks(tasks)))
    }

    @Test
    fun `placement views order unscheduled tasks newest first`() {
        val tasks = listOf(
            task(id = "middle", placement = TaskPlacement.SOMEDAY, createdAt = timestamp),
            task(
                id = "oldest",
                placement = TaskPlacement.SOMEDAY,
                createdAt = timestamp.minusSeconds(600)
            ),
            task(
                id = "newest",
                placement = TaskPlacement.SOMEDAY,
                createdAt = timestamp.plusSeconds(600)
            )
        )

        assertEquals(listOf("newest", "middle", "oldest"), ids(somedayTasks(tasks)))
    }

    @Test
    fun `placement views keep input order for equal creation times`() {
        val tasks = listOf(
            task(id = "c", placement = TaskPlacement.SOMEDAY),
            task(id = "a", placement = TaskPlacement.SOMEDAY),
            task(id = "b", placement = TaskPlacement.SOMEDAY)
        )

        assertEquals(listOf("c", "a", "b"), ids(somedayTasks(tasks)))
    }

    @Test
    fun `placement view ordering is idempotent`() {
        val tasks = listOf(
            task(id = "capture", placement = TaskPlacement.ANYTIME),
            task(id = "older", placement = TaskPlacement.ANYTIME, createdAt = timestamp.minusSeconds(60))
        )

        val once = anytimeTasks(tasks)

        assertEquals(ids(once), ids(anytimeTasks(once)))
    }

    @Test
    fun `the placement views do not leak into each other`() {
        val tasks = listOf(
            task(id = "inbox", placement = TaskPlacement.INBOX),
            task(id = "anytime", placement = TaskPlacement.ANYTIME),
            task(id = "someday", placement = TaskPlacement.SOMEDAY)
        )

        assertEquals(listOf("anytime"), ids(anytimeTasks(tasks)))
        assertEquals(listOf("someday"), ids(somedayTasks(tasks)))
        assertEquals(listOf("inbox"), ids(inboxTasks(tasks)))
    }

    @Test
    fun `anytime returns only anytime tasks`() {
        val tasks = listOf(
            task(id = "a", placement = TaskPlacement.INBOX),
            task(id = "b", placement = TaskPlacement.ANYTIME),
            task(id = "c", placement = TaskPlacement.SOMEDAY)
        )

        assertEquals(listOf("b"), ids(anytimeTasks(tasks)))
    }

    @Test
    fun `someday returns only someday tasks`() {
        val tasks = listOf(
            task(id = "a", placement = TaskPlacement.INBOX),
            task(id = "b", placement = TaskPlacement.ANYTIME),
            task(id = "c", placement = TaskPlacement.SOMEDAY)
        )

        assertEquals(listOf("c"), ids(somedayTasks(tasks)))
    }

    @Test
    fun `placement views exclude deleted tasks`() {
        val tasks = listOf(
            task(id = "a", placement = TaskPlacement.INBOX, deletedAt = timestamp),
            task(id = "b", placement = TaskPlacement.ANYTIME, deletedAt = timestamp),
            task(id = "c", placement = TaskPlacement.SOMEDAY, deletedAt = timestamp)
        )

        assertEquals(emptyList<String>(), ids(inboxTasks(tasks)))
        assertEquals(emptyList<String>(), ids(anytimeTasks(tasks)))
        assertEquals(emptyList<String>(), ids(somedayTasks(tasks)))
    }

    // Focus

    @Test
    fun `focus excludes a completed task`() {
        val completed = task(id = "a", scheduledDate = today, completedAt = timestamp)

        assertEquals(emptyList<String>(), ids(focusQueue(listOf(completed), today)))
    }

    @Test
    fun `focus keeps an overdue task`() {
        val overdue = task(id = "a", scheduledDate = yesterday)

        assertEquals(listOf("a"), ids(focusQueue(listOf(overdue), today)))
    }

    @Test
    fun `focus excludes a future task`() {
        val future = task(id = "a", scheduledDate = tomorrow)

        assertEquals(emptyList<String>(), ids(focusQueue(listOf(future), today)))
    }

    @Test
    fun `focus excludes a deleted task`() {
        val deleted = task(id = "a", scheduledDate = today, deletedAt = timestamp)

        assertEquals(emptyList<String>(), ids(focusQueue(listOf(deleted), today)))
    }

    @Test
    fun `focus is today minus the completed tasks`() {
        val tasks = listOf(
            task(id = "overdue", scheduledDate = yesterday),
            task(id = "done", scheduledDate = today, completedAt = timestamp),
            task(id = "now", scheduledDate = today),
            task(id = "future", scheduledDate = tomorrow)
        )

        val expected = ids(todayTasks(tasks, today)).filter { it != "done" }

        assertEquals(expected, ids(focusQueue(tasks, today)))
        // Not just the same members: the same order, taken from Today itself.
        assertEquals(listOf("now", "overdue"), ids(focusQueue(tasks, today)))
    }

    @Test
    fun `focus is empty when nothing is scheduled`() {
        val tasks = listOf(
            task(id = "a", scheduledDate = null),
            task(id = "b", placement = TaskPlacement.INBOX, scheduledDate = null)
        )

        assertEquals(emptyList<String>(), ids(focusQueue(tasks, today)))
    }

    @Test
    fun `focus ignores placement`() {
        val tasks = listOf(
            task(id = "inbox", placement = TaskPlacement.INBOX, scheduledDate = today),
            task(id = "anytime", placement = TaskPlacement.ANYTIME, scheduledDate = today),
            task(id = "someday", placement = TaskPlacement.SOMEDAY, scheduledDate = today)
        )

        assertEquals(
            listOf("inbox", "anytime", "someday"),
            ids(focusQueue(tasks, today))
        )
    }

    // Today sections

    @Test
    fun `sections split today into its bands in order`() {
        val tasks = listOf(
            task(id = "overdue", scheduledDate = yesterday),
            task(id = "done", scheduledDate = today, completedAt = timestamp),
            task(id = "now", scheduledDate = today)
        )

        val sections = todaySections(tasks, today)

        assertEquals(
            listOf(TodayBand.SCHEDULED, TodayBand.OVERDUE, TodayBand.COMPLETED),
            sections.map { it.band }
        )
        assertEquals(listOf("now"), ids(sections[0].tasks))
        assertEquals(listOf("overdue"), ids(sections[1].tasks))
        assertEquals(listOf("done"), ids(sections[2].tasks))
    }

    @Test
    fun `sections concatenate back to exactly what today returned`() {
        val tasks = listOf(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = yesterday),
            task(id = "c", scheduledDate = today, completedAt = timestamp),
            task(id = "d", scheduledDate = today),
            task(id = "e", scheduledDate = tomorrow)
        )

        // The sections are a reading of the order, not a second sort.
        assertEquals(
            ids(todayTasks(tasks, today)),
            ids(todaySections(tasks, today).flatMap { it.tasks })
        )
    }

    @Test
    fun `an empty band produces no section`() {
        val tasks = listOf(
            task(id = "a", scheduledDate = today),
            task(id = "b", scheduledDate = today)
        )

        val sections = todaySections(tasks, today)

        assertEquals(listOf(TodayBand.SCHEDULED), sections.map { it.band })
        assertEquals(listOf("a", "b"), ids(sections.single().tasks))
    }

    @Test
    fun `no tasks produce no sections`() {
        assertEquals(emptyList<TodaySection>(), todaySections(emptyList(), today))
    }

    @Test
    fun `a band keeps every task in it together`() {
        val tasks = listOf(
            task(id = "old1", scheduledDate = yesterday),
            task(id = "now1", scheduledDate = today),
            task(id = "old2", scheduledDate = yesterday.minusDays(3)),
            task(id = "now2", scheduledDate = today)
        )

        val sections = todaySections(tasks, today)

        assertEquals(listOf(TodayBand.SCHEDULED, TodayBand.OVERDUE), sections.map { it.band })
        assertEquals(listOf("now1", "now2"), ids(sections[0].tasks))
        assertEquals(listOf("old1", "old2"), ids(sections[1].tasks))
    }

    @Test
    fun `a band is named for every task it holds`() {
        assertEquals(
            TodayBand.SCHEDULED,
            todayBandOf(task(id = "a", scheduledDate = today), today)
        )
        assertEquals(
            TodayBand.OVERDUE,
            todayBandOf(task(id = "a", scheduledDate = yesterday), today)
        )
        assertEquals(
            TodayBand.COMPLETED,
            todayBandOf(task(id = "a", scheduledDate = today, completedAt = timestamp), today)
        )
        // Completion wins over the date, as it does in the ordering.
        assertEquals(
            TodayBand.COMPLETED,
            todayBandOf(task(id = "a", scheduledDate = yesterday, completedAt = timestamp), today)
        )
    }

    @Test
    fun `sections exclude what today excludes`() {
        val tasks = listOf(
            task(id = "future", scheduledDate = tomorrow),
            task(id = "unscheduled", scheduledDate = null),
            task(id = "deleted", scheduledDate = today, deletedAt = timestamp),
            task(id = "kept", scheduledDate = today)
        )

        assertEquals(listOf("kept"), ids(todaySections(tasks, today).flatMap { it.tasks }))
    }
}
