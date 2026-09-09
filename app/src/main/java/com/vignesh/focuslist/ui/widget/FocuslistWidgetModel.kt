package com.vignesh.focuslist.ui.widget

import com.vignesh.focuslist.core.domain.FocusNow
import com.vignesh.focuslist.core.domain.StoredFocusSession
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.focusNow
import com.vignesh.focuslist.core.domain.todayTasks
import com.vignesh.focuslist.data.local.WidgetCompletion
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.floor

/**
 * The heights the widget's composables declare, in dp.
 *
 * They exist here so row capacity can be arithmetic rather than a table, per
 * D-036. The obligation that comes with them is that they and
 * `FocuslistWidget.kt` have to stay in step: a row that changes height without
 * changing [RowHeight] puts the calculation out of agreement with the thing it
 * is measuring, and nothing but a render will say so.
 */
private const val HeaderHeight = 60f
private const val RowHeight = 48f
private const val LeadCardHeight = 86f
private const val LeadCardGap = 8f
private const val BottomInset = 8f

/**
 * Reserved whether or not `+N more` appears.
 *
 * It costs 20dp on a widget with nothing to hide, and it buys that the
 * disclosure can never be the thing that gets clipped. It is the only thing on
 * the surface that says rows exist which are not being shown.
 */
private const val DisclosureHeight = 20f

internal sealed interface WidgetBody {
    data object NothingScheduled : WidgetBody
    data object EverythingDone : WidgetBody

    data class Tasks(
        val lead: FocusNow?,
        val rows: List<WidgetRow>,
        val hiddenCount: Int
    ) : WidgetBody
}

internal data class WidgetRow(
    val task: Task,
    val justCompleted: Boolean = false
)

internal data class FocuslistWidgetModel(val body: WidgetBody)

/**
 * The widget's six states, derived without Android so the important decisions
 * can be covered by JVM tests.
 */
internal fun focuslistWidgetModel(
    tasks: List<Task>,
    today: LocalDate,
    now: LocalDateTime,
    storedFocus: StoredFocusSession?,
    completion: WidgetCompletion?,
    // The height the launcher says this widget has, in dp. D-036: a widget is
    // resized by dragging, so most are neither of the two declared responsive
    // sizes, and capacity is a function of this rather than of which breakpoint
    // was matched.
    heightDp: Float,
    fontScale: Float = 1f,
    zoneId: ZoneId = ZoneId.systemDefault()
): FocuslistWidgetModel {
    val todayTasks = todayTasks(tasks, today)
    val completedTask = completion?.let { evidence ->
        tasks.firstOrNull { task -> task.id == evidence.taskId && task.isCompleted && !task.isDeleted }
    }

    // A completion is the state change the user just asked to see. Suppress a
    // newly selected lead until the next refresh rather than replacing their
    // evidence with a different assertion in the same tap.
    // No threshold of its own any more. D-031 filtered `NoTimeToday` out here
    // because a home screen has to earn the right to assert; D-035 accepted the
    // same argument for the app and removed the reason outright, so the widget
    // and Today now lead on the same two events.
    val lead = if (completedTask == null) {
        focusNow(
            tasks = tasks,
            now = now,
            pausedTaskId = storedFocus
                ?.takeIf { stored -> stored.session.isPaused }
                ?.taskId
        )
    } else {
        null
    }

    val outstanding = todayTasks.filterNot { task -> task.isCompleted || task.id == lead?.task?.id }
        .map(::WidgetRow)
        .toMutableList()

    if (completedTask != null && completion != null) {
        outstanding.add(
            index = completion.previousIndex.coerceIn(0, outstanding.size),
            element = WidgetRow(task = completedTask, justCompleted = true)
        )
    }

    if (lead == null && outstanding.isEmpty()) {
        val hadWorkToday = todayTasks.any { task ->
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

    val capacity = rowCapacity(heightDp = heightDp, hasLead = lead != null, fontScale = fontScale)
    val visible = outstanding.take(capacity)

    return FocuslistWidgetModel(
        WidgetBody.Tasks(
            lead = lead,
            rows = visible,
            hiddenCount = (outstanding.size - visible.size).coerceAtLeast(0)
        )
    )
}

/** Where a row was before its completion changed Today's ordering. */
internal fun widgetCompletionIndex(
    tasks: List<Task>,
    today: LocalDate,
    now: LocalDateTime,
    storedFocus: StoredFocusSession?,
    taskId: String
): Int {
    val lead = focusNow(
        tasks = tasks,
        now = now,
        pausedTaskId = storedFocus?.takeIf { it.session.isPaused }?.taskId
    )

    if (lead?.task?.id == taskId) return 0

    return todayTasks(tasks, today)
        .asSequence()
        .filterNot { it.isCompleted || it.id == lead?.task?.id }
        .indexOfFirst { it.id == taskId }
        .coerceAtLeast(0)
}

/**
 * How many rows fit under everything else, per D-036.
 *
 * This was a seven-branch `when` over `WidgetLayout` and a `largeText` flag,
 * which treated the two declared responsive sizes as the only two shapes a
 * widget can have. A widget is resized by dragging, so most are neither: one
 * taller than Compact and slightly narrower than Medium fell to Compact, and
 * Compact with a lead card is no rows at all, which left a large widget mostly
 * empty with one card at the top.
 *
 * The arithmetic reproduces that table exactly at both declared sizes, across
 * lead and no lead and normal and large text. `FocuslistWidgetModelTest` pins
 * all eight, so changing a constant here has to say which case it moved.
 *
 * Text scale multiplies the row and the lead card rather than switching on a
 * threshold, which is what the `largeText` flag was approximating. Never below
 * 1: a user who has made text smaller does not thereby get more rows than the
 * layout was drawn for.
 */
private fun rowCapacity(heightDp: Float, hasLead: Boolean, fontScale: Float): Int {
    val textScale = fontScale.coerceAtLeast(1f)

    var available = heightDp - HeaderHeight - BottomInset - DisclosureHeight
    if (hasLead) available -= (LeadCardHeight * textScale) + LeadCardGap

    return floor(available / (RowHeight * textScale)).toInt().coerceAtLeast(0)
}
