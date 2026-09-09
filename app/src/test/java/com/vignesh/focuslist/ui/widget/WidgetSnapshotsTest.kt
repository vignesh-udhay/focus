package com.vignesh.focuslist.ui.widget

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.data.local.WidgetCompletion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * That the widget's data is a stream, per D-045.
 *
 * The bug this covers had no visible symptom in any single render: every frame
 * the widget drew was internally consistent and correct as of when its Glance
 * session had started. What was missing was a second reading. Everything above
 * `provideContent` runs once per session, a session lives 45 seconds past its
 * first composition, and `updateAll` only recomposes, so a value read up there
 * was the widget's only data for the whole window.
 *
 * These assertions are all the same assertion: a change to a source reaches a
 * collector that was already listening. Capture the value again anywhere in
 * this path and they stop passing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotsTest {

    private val today = LocalDate.of(2026, 9, 10)
    private val createdAt = Instant.parse("2026-09-01T09:00:00Z")

    private fun task(id: String) = Task(
        id = id,
        title = "Task $id",
        createdAt = createdAt,
        scheduledDate = today
    )

    @Test
    fun aTaskWriteReachesAWidgetAlreadyListening() = runTest {
        val tasks = MutableStateFlow(listOf(task("a")))
        val seen = collected(tasks = tasks)

        tasks.value = listOf(task("a"), task("b"))

        assertEquals(2, seen.size)
        assertEquals(listOf("a", "b"), seen.last().tasks.map { it.id })
    }

    /**
     * The widget is the surface a rollover strands: nobody opens it, so nothing
     * would correct yesterday's list until something else happened to write.
     */
    @Test
    fun aDayRolloverReachesIt() = runTest {
        val day = MutableStateFlow(today)
        val seen = collected(today = day)

        day.value = today.plusDays(1)

        assertEquals(2, seen.size)
        assertEquals(today.plusDays(1), seen.last().today)
    }

    /** There is a snapshot to draw before anything has changed. */
    @Test
    fun theFirstSnapshotArrivesWithoutWaitingForAChange() = runTest {
        val seen = collected()

        assertEquals(1, seen.size)
        assertEquals(listOf("a"), seen.single().tasks.map { it.id })
    }

    @Test
    fun anUnchangedStateIsNotRepublished() = runTest {
        val tasks = MutableStateFlow(listOf(task("a")))
        val seen = collected(tasks = tasks)

        tasks.value = listOf(task("a"))
        tasks.value = listOf(task("a"))

        assertEquals(1, seen.size)
    }

    /**
     * Reading the completion is what retires it once storage has moved past the
     * interaction, so it has to be consulted on every emission rather than only
     * when the answer is about to be drawn.
     */
    @Test
    fun theCompletionIsConsultedOnEveryEmission() = runTest {
        val tasks = MutableStateFlow(listOf(task("a")))
        var reads = 0
        collected(tasks = tasks, completion = { _, _ -> reads++; null })

        tasks.value = listOf(task("a"), task("b"))
        tasks.value = listOf(task("b"))

        assertEquals(3, reads)
    }

    /** Collects into a list that keeps filling as the sources change. */
    private fun TestScope.collected(
        tasks: MutableStateFlow<List<Task>> = MutableStateFlow(listOf(task("a"))),
        today: MutableStateFlow<LocalDate> = MutableStateFlow(this@WidgetSnapshotsTest.today),
        completion: (List<Task>, LocalDate) -> WidgetCompletion? = { _, _ -> null }
    ): List<WidgetSnapshot> {
        val seen = mutableListOf<WidgetSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            widgetSnapshots(tasks = tasks, today = today, completion = completion).toList(seen)
        }
        return seen
    }
}
