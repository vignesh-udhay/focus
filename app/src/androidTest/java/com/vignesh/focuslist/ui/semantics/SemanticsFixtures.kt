package com.vignesh.focuslist.ui.semantics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import com.vignesh.focuslist.core.notification.FocusAlarms
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.time.CurrentDay
import com.vignesh.focuslist.data.local.TaskDao
import com.vignesh.focuslist.data.local.TaskEntity
import com.vignesh.focuslist.data.local.toEntity
import com.vignesh.focuslist.data.repository.TaskRepository
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * The two font scales every semantics contract in this package is checked at.
 *
 * `PRODUCT.md` requires system font scaling, and 200% is where a layout either
 * holds or hides something. Checking semantics at both is how we know a control
 * that is reachable at 100% has not been pushed off screen, clipped, or merged
 * into its neighbour at 200%.
 */
internal const val FontScale100 = 1f
internal const val FontScale200 = 2f

/**
 * Renders [content] in the real Focuslist theme at a chosen font scale.
 *
 * The scale is imposed by overriding [LocalDensity] rather than by changing the
 * device configuration, so a test needs no permissions, leaves no state behind
 * on the emulator, and cannot leak a scale into the next test.
 *
 * Dynamic colour is off for the same reason the previews turn it off: the
 * fallback schemes are the ones the design was measured against, and a test
 * should not depend on the wallpaper the emulator happens to have.
 */
internal fun ComposeContentTestRule.setFocuslistContent(
    fontScale: Float,
    content: @Composable () -> Unit
) {
    setContent {
        val base = LocalDensity.current

        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale)
        ) {
            FocuslistTheme(dynamicColor = false) { content() }
        }
    }
}

/** Matches a node marked as a heading for accessibility services. */
internal fun isHeading(): SemanticsMatcher =
    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

/**
 * Matches a node whose tap action is announced as [label].
 *
 * The label is what a screen reader reads as "double tap to <label>", so it is
 * the contract, not the presence of a click handler.
 */
internal fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("click action labelled '$label'") { node ->
        node.config.contains(SemanticsActions.OnClick) &&
            node.config[SemanticsActions.OnClick].label == label
    }

/** Matches a node whose long-press action is announced as [label]. */
internal fun hasLongClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("long click action labelled '$label'") { node ->
        node.config.contains(SemanticsActions.OnLongClick) &&
            node.config[SemanticsActions.OnLongClick].label == label
    }

/** Matches a node that publishes a live region, at [mode]. */
internal fun hasLiveRegion(mode: androidx.compose.ui.semantics.LiveRegionMode): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)

/**
 * A stand-in for the Room-generated DAO, mirroring the real query's
 * `WHERE deletedAt IS NULL` so a soft-deleted row stays stored but stops being
 * observed.
 *
 * [TaskDao] is an interface, so the real [TaskRepository] and the real
 * [TaskListViewModel] stay in the loop and only storage is replaced.
 */
internal class FakeTaskDao(initial: List<Task> = emptyList()) : TaskDao {

    private val rows = MutableStateFlow(initial.map { task -> task.toEntity() })

    override fun observeTasks(): Flow<List<TaskEntity>> =
        rows.map { stored ->
            stored.filter { row -> row.deletedAt == null }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
        }

    override suspend fun insert(task: TaskEntity) {
        rows.value = rows.value + task
    }

    override suspend fun update(task: TaskEntity) {
        rows.value = rows.value.map { stored -> if (stored.id == task.id) task else stored }
    }

    override suspend fun softDelete(id: String, deletedAt: Long) {
        rows.value = rows.value.map { stored ->
            if (stored.id == id) stored.copy(deletedAt = Instant.ofEpochMilli(deletedAt)) else stored
        }
    }

    override suspend fun restore(id: String) {
        rows.value = rows.value.map { stored ->
            if (stored.id == id) stored.copy(deletedAt = null) else stored
        }
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.filterNot { stored -> stored.id == id }
    }

    /** What storage currently holds, deleted rows included. */
    fun stored(): List<TaskEntity> = rows.value
}

/** A calendar day the test owns, in place of the device clock. */
internal class FakeCurrentDay(initial: LocalDate) : CurrentDay {

    private val _today = MutableStateFlow(initial)

    override val today: StateFlow<LocalDate> = _today.asStateFlow()
}

/** The fixed day every test in this package derives its dated views against. */
internal val TestToday: LocalDate = LocalDate.of(2026, 9, 2)

/** A fixed capture time, so ordering never depends on how fast a test runs. */
internal val TestCreatedAt: Instant = Instant.parse("2026-01-01T09:00:00Z")

/** Builds a task with the fixture defaults, overriding only what a test cares about. */
internal fun testTask(
    id: String,
    title: String,
    placement: TaskPlacement = TaskPlacement.INBOX,
    scheduledDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    estimatedDurationMinutes: Int? = null,
    notes: String? = null,
    recurrence: Recurrence? = null,
    completedAt: Instant? = null,
    createdAt: Instant = TestCreatedAt
): Task = Task(
    id = id,
    title = title,
    createdAt = createdAt,
    notes = notes,
    placement = placement,
    scheduledDate = scheduledDate,
    dueDate = dueDate,
    estimatedDurationMinutes = estimatedDurationMinutes,
    recurrence = recurrence,
    completedAt = completedAt
)

/**
 * A real view model over fake storage and a fixed day.
 *
 * Screens are driven through this rather than through a stripped-down copy of
 * their state, so what the tests exercise is the production composition.
 */
internal fun testViewModel(
    dao: FakeTaskDao,
    today: LocalDate = TestToday
): TaskListViewModel = TaskListViewModel(
    repository = TaskRepository(dao),
    currentDay = FakeCurrentDay(today),
    // A real handle with nothing in it. Tests start no session, and the ones
    // that do only need it to hold a value, not to survive anything.
    savedState = SavedStateHandle(),
    alarms = RecordingFocusAlarms()
)

/**
 * A [FocusAlarms] that records instead of asking the system for anything.
 *
 * Scheduling a real alarm from a test would put a notification on the device
 * running the suite, minutes later, unrelated to anything the user did.
 */
internal class RecordingFocusAlarms : FocusAlarms {

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

/**
 * Invokes the long-press action the way an accessibility service does.
 *
 * A synthesised gesture would prove a finger works. Firing the semantics action
 * proves the action is exposed to TalkBack and Switch Access, which is the
 * contract this package is about, and is the only route a user who cannot
 * long-press has.
 */
internal fun SemanticsNodeInteraction.performAccessibilityLongClick(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnLongClick)
