package com.vignesh.focuslist.ui.widget

import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TodayBand
import com.vignesh.focuslist.core.domain.todaySections
import com.vignesh.focuslist.core.domain.todayTasks
import com.vignesh.focuslist.data.local.WidgetCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.ZoneId

internal sealed interface WidgetBody {

    /**
     * Nothing has been read yet, per D-051.
     *
     * **A state rather than a wait**, and that is the whole point of it. Until
     * `provideContent` is called, `AppWidgetSession` emits `IgnoreResult()` and
     * the widget publishes nothing, and a session that has published nothing can
     * be timed out after five seconds of device idle and reported as a success.
     * The widget then keeps the launcher's loading layout with nothing left to
     * retry it. Drawing something immediately, even the header alone, is what
     * takes the widget out of that window.
     *
     * Distinct from [NothingScheduled] on purpose: one is the app not knowing
     * yet, the other is the app knowing the day is empty.
     */
    data object Loading : WidgetBody

    data object NothingScheduled : WidgetBody
    data object EverythingDone : WidgetBody

    /**
     * Today's outstanding bands, labelled, in the order `todaySections` gives.
     *
     * **There is no lead and no capacity.** D-049 removed the Focus card, which
     * is what left the rows as one unexplained run and is why the bands are here
     * at all. D-043 removed the truncation: the rows are a `LazyColumn`, so the
     * platform draws what fits and scrolls the rest, and nothing in this file
     * has an opinion about how tall the widget is.
     */
    data class Sections(
        // The day the rows were banded for, carried here since D-051 because the
        // content can now be drawn before any day is known.
        val today: LocalDate,
        val sections: List<WidgetSection>
    ) : WidgetBody
}

/** One band and its rows, carrying the band so the view can label it. */
internal data class WidgetSection(
    val band: TodayBand,
    val rows: List<WidgetRow>
)

internal data class WidgetRow(
    val task: Task,
    val justCompleted: Boolean = false
)

internal data class FocuslistWidgetModel(val body: WidgetBody)

/** Everything the widget draws from, as one value. */
internal data class WidgetSnapshot(
    val tasks: List<Task>,
    val today: LocalDate,
    val completion: WidgetCompletion?
)

/**
 * The widget's data, for as long as something is collecting it. D-045.
 *
 * **A stream rather than a read, because a Glance session outlives a read.**
 * Everything above `provideContent` runs once per session, not once per update:
 * `runGlance` calls `provideGlance` a single time and `provideContent` then
 * suspends and never returns. A session lives 45 seconds past its first
 * composition. `updateAll` does not re-enter any of that, it only forces a
 * recomposition, so a value captured up there was re-rendered rather than
 * re-read and the widget was frozen for the session's whole life.
 *
 * Collected inside the composition instead, a change reaches the widget by
 * recomposing it, which is what makes the session an asset rather than a cache.
 *
 * Takes its sources as arguments so this is answerable without Android. The
 * two lambdas are `SharedPreferences` reads, and `completion` retires the
 * evidence when storage has moved past it, so it is called on every emission
 * rather than only when the result is about to be used.
 */
internal fun widgetSnapshots(
    tasks: Flow<List<Task>>,
    today: Flow<LocalDate>,
    completion: (List<Task>, LocalDate) -> WidgetCompletion?
): Flow<WidgetSnapshot> =
    combine(tasks, today) { currentTasks, currentToday ->
        WidgetSnapshot(
            tasks = currentTasks,
            today = currentToday,
            completion = completion(currentTasks, currentToday)
        )
    }.distinctUntilChanged()

/**
 * The widget's states, derived without Android so the decisions in them can be
 * covered by JVM tests.
 *
 * Reads no clock and no Focus session, per D-049. Both left with the lead card:
 * `now` existed only for `ReminderPassed`, which D-048 deleted, and the paused
 * session is a thing Today says on a screen the user opened.
 */
internal fun focuslistWidgetModel(
    tasks: List<Task>,
    today: LocalDate,
    completion: WidgetCompletion?,
    zoneId: ZoneId = ZoneId.systemDefault()
): FocuslistWidgetModel {
    val completedTask = completion?.let { evidence ->
        tasks.firstOrNull { task -> task.id == evidence.taskId && task.isCompleted && !task.isDeleted }
    }

    // **The just-completed task is banded as though it were still outstanding.**
    // D-031 keeps a checked row in place because on this surface a row that
    // vanishes on tap erases the only evidence of what the user just did, and
    // completing it would otherwise move it into Completed at the bottom.
    // Presenting it as unfinished returns it to its own band in its own place,
    // which is why nothing has to remember where that was.
    val banded = if (completedTask == null) {
        tasks
    } else {
        tasks.map { task ->
            if (task.id == completedTask.id) task.copy(completedAt = null) else task
        }
    }

    val sections = todaySections(banded, today)
        // Finished work is the one thing a glanceable surface never needs to
        // carry, and on Today this band is a disclosure the user opens. A widget
        // has nothing cheap to open into. D-049.
        .filterNot { section -> section.band == TodayBand.COMPLETED }
        .map { section ->
            WidgetSection(
                band = section.band,
                rows = section.tasks.map { task ->
                    WidgetRow(task = task, justCompleted = task.id == completedTask?.id)
                }
            )
        }

    if (sections.isEmpty()) {
        val hadWorkToday = todayTasks(tasks, today).any { task ->
            task.isCompleted && (
                task.scheduledDate == today ||
                    task.completedAt?.atZone(zoneId)?.toLocalDate() == today
                )
        }
        return FocuslistWidgetModel(
            body = if (hadWorkToday) WidgetBody.EverythingDone
            else WidgetBody.NothingScheduled
        )
    }

    return FocuslistWidgetModel(WidgetBody.Sections(today = today, sections = sections))
}
