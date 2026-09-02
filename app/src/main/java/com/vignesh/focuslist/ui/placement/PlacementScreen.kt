package com.vignesh.focuslist.ui.placement

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.anytimeTasks
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.TaskListRow
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.TaskDetailsSheetHost
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Instant
import java.time.LocalDate

/** The two placements this screen shows, in tab order. */
private val PlacementTabs = listOf(TaskPlacement.ANYTIME, TaskPlacement.SOMEDAY)

/**
 * Anytime and Someday.
 *
 * One screen with a tab each, because they are the same list with one constant
 * changed, and because they are the pair a user flips between when deciding
 * whether something is worth doing soon.
 *
 * Both read placement alone. A task here may also be in Today or Upcoming if it
 * has a date, which is correct: placement says how far a task has been triaged,
 * scheduling says when it is meant to be worked on, and neither hides the other.
 *
 * There is no add-task button. Placement is chosen during triage, in Task
 * Details, not at capture time.
 */
@Composable
fun PlacementScreen(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier,
    initialPlacement: TaskPlacement = TaskPlacement.ANYTIME,
    bottomBar: @Composable () -> Unit = {}
) {

    // Keyed on the entry point, so opening Someday from More lands on Someday.
    var placement by rememberSaveable(initialPlacement) { mutableStateOf(initialPlacement) }

    // Only the visible tab is collected; the other flow stops on its own.
    val list = when (placement) {
        TaskPlacement.SOMEDAY -> viewModel.somedayTasks
        else -> viewModel.anytimeTasks
    }
    val tasks by list.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    PlacementContent(
        tasks = tasks,
        today = today,
        placement = placement,
        onSelectPlacement = { selected -> placement = selected },
        onToggleComplete = viewModel::toggleComplete,
        onOpenTask = { id -> openTaskId = id },
        onDelete = viewModel::deleteTask,
        onReschedule = viewModel::rescheduleTask,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = bottomBar
    )

    TaskDetailsSheetHost(
        openTaskId = openTaskId,
        tasks = tasks,
        today = today,
        viewModel = viewModel,
        onDismiss = { openTaskId = null }
    )
}

/**
 * The Anytime/Someday layout.
 *
 * Stateless. [tasks] arrives already filtered and ordered for [placement]; this
 * screen neither filters nor sorts, and switching tabs swaps the list rather
 * than re-deriving it here.
 */
@Composable
private fun PlacementContent(
    tasks: List<Task>,
    today: LocalDate,
    placement: TaskPlacement,
    onSelectPlacement: (TaskPlacement) -> Unit,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReschedule: (String, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {}
) {
    val taskColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    )

    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            // The bar does not scroll away: the tabs below it have to stay
            // reachable, so there is no pinnedScrollBehavior here.
            Column {
                FocuslistTopAppBar(title = stringResource(placement.labelRes))

                PrimaryTabRow(selectedTabIndex = PlacementTabs.indexOf(placement)) {
                    PlacementTabs.forEach { tab ->
                        Tab(
                            selected = tab == placement,
                            onClick = { onSelectPlacement(tab) },
                            text = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            TaskListEmptyState(
                headline = stringResource(placement.emptyHeadlineRes),
                supporting = stringResource(placement.emptySupportingRes),
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FocuslistSpacing.md + gutter,
                    end = FocuslistSpacing.md + gutter,
                    top = innerPadding.calculateTopPadding() + FocuslistSpacing.xs,
                    // No floating action button here, so nothing to clear.
                    bottom = innerPadding.calculateBottomPadding() + FocuslistSpacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                    TaskListRow(
                        task = task,
                        today = today,
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = tasks.size
                        ),
                        colors = taskColors,
                        onToggleComplete = { onToggleComplete(task.id) },
                        onOpen = { onOpenTask(task.id) },
                        onDelete = { onDelete(task.id) },
                        onReschedule = { date -> onReschedule(task.id, date) },
                        // A completed or retriaged task leaves by moving.
                        modifier = Modifier.animateItem(
                            placementSpec = FocuslistMotion.listChange()
                        )
                    )
                }
            }
        }
    }
}

private val TaskPlacement.labelRes: Int
    get() = when (this) {
        TaskPlacement.INBOX -> R.string.task_placement_inbox
        TaskPlacement.ANYTIME -> R.string.task_placement_anytime
        TaskPlacement.SOMEDAY -> R.string.task_placement_someday
    }

private val TaskPlacement.emptyHeadlineRes: Int
    get() = when (this) {
        TaskPlacement.INBOX -> R.string.inbox_empty_headline
        TaskPlacement.ANYTIME -> R.string.anytime_empty_headline
        TaskPlacement.SOMEDAY -> R.string.someday_empty_headline
    }

private val TaskPlacement.emptySupportingRes: Int
    get() = when (this) {
        TaskPlacement.INBOX -> R.string.inbox_empty_supporting
        TaskPlacement.ANYTIME -> R.string.anytime_empty_supporting
        TaskPlacement.SOMEDAY -> R.string.someday_empty_supporting
    }

/** A fixed timestamp, so previews do not shift with the clock. */
private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private fun samplePlacementTasks(): List<Task> = listOf(
    Task(
        id = "1",
        title = "Read the Compose performance guide",
        createdAt = SampleTimestamp,
        placement = TaskPlacement.ANYTIME
    ),
    Task(
        id = "2",
        title = "Rewrite the onboarding copy so it stops explaining things nobody " +
            "asked about",
        createdAt = SampleTimestamp.plusSeconds(60),
        placement = TaskPlacement.ANYTIME,
        estimatedDurationMinutes = 60
    ),
    Task(
        id = "3",
        title = "Renew the domain",
        createdAt = SampleTimestamp.plusSeconds(120),
        placement = TaskPlacement.ANYTIME,
        scheduledDate = LocalDate.now().plusDays(4)
    ),
    // Excluded: completed.
    Task(
        id = "4",
        title = "Cancel the old subscription",
        createdAt = SampleTimestamp.plusSeconds(180),
        placement = TaskPlacement.ANYTIME,
        completedAt = SampleTimestamp
    ),
    // Excluded: a different placement.
    Task(
        id = "5",
        title = "Learn to sail",
        createdAt = SampleTimestamp.plusSeconds(240),
        placement = TaskPlacement.SOMEDAY
    )
)

@Preview(name = "Anytime light", heightDp = 640)
@Preview(name = "Anytime dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlacementScreenPreview() {
    FocuslistTheme(dynamicColor = false) {
        PlacementContent(
            tasks = anytimeTasks(samplePlacementTasks()),
            today = LocalDate.now(),
            placement = TaskPlacement.ANYTIME,
            onSelectPlacement = {},
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
        )
    }
}

@Preview(name = "Someday empty light", heightDp = 640)
@Preview(name = "Someday empty dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlacementScreenEmptyPreview() {
    FocuslistTheme(dynamicColor = false) {
        PlacementContent(
            tasks = emptyList(),
            today = LocalDate.now(),
            placement = TaskPlacement.SOMEDAY,
            onSelectPlacement = {},
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
        )
    }
}

@Preview(name = "Anytime large font", heightDp = 640, fontScale = 2f)
@Composable
private fun PlacementScreenLargeFontPreview() {
    FocuslistTheme(dynamicColor = false) {
        PlacementContent(
            tasks = anytimeTasks(samplePlacementTasks()),
            today = LocalDate.now(),
            placement = TaskPlacement.ANYTIME,
            onSelectPlacement = {},
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
        )
    }
}
