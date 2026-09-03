package com.vignesh.focuslist.ui.upcoming

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.upcomingSections
import com.vignesh.focuslist.core.domain.upcomingTasks
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.SectionLabel
import com.vignesh.focuslist.ui.component.TaskListRow
import com.vignesh.focuslist.ui.component.sectionDateLabel
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.TaskDetailsSheetHost
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Instant
import java.time.LocalDate

/**
 * Upcoming: what is scheduled beyond today.
 *
 * The counterpart to Today, over the same tasks and the same view model.
 * Everything a row can do here it already does on Today, so this screen adds
 * only its own query, title, and empty state.
 *
 * There is no add-task button. Quick Add schedules for today, so a task
 * captured here would not belong here.
 */
@Composable
fun UpcomingScreen(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val tasks by viewModel.upcomingTasks.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    UpcomingContent(
        tasks = tasks,
        today = today,
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
 * The Upcoming layout.
 *
 * Stateless, and the same segmented collection Today uses. [tasks] arrives
 * already filtered and ordered by `upcomingTasks`; this screen neither filters
 * nor sorts.
 */
@Composable
private fun UpcomingContent(
    tasks: List<Task>,
    today: LocalDate,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReschedule: (String, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {}
) {
    // Collapses as the list moves under it, as Today and Inbox do. A pinned
    // behaviour would hold the full height of a two-row bar in every scroll
    // position, which is a lot of chrome for a screen that is only a list.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val taskColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    // The days upcomingTasks already ordered the list into. Read here rather
    // than re-derived, so the ordering stays owned by TaskQueries.
    val sections = upcomingSections(tasks, today)

    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            FocuslistTopAppBar(
                title = stringResource(R.string.upcoming_title),
                // Nothing to count when the list is empty, and the empty state
                // already says so.
                subtitle = if (tasks.isEmpty()) {
                    null
                } else {
                    {
                        Text(
                            text = pluralStringResource(
                                R.plurals.upcoming_scheduled,
                                tasks.size,
                                tasks.size
                            )
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            TaskListEmptyState(
                headline = stringResource(R.string.upcoming_empty_headline),
                supporting = stringResource(R.string.upcoming_empty_supporting),
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
                sections.forEach { section ->
                    item(key = "label-" + section.date) {
                        SectionLabel(
                            text = sectionDateLabel(section.date, today),
                            modifier = Modifier.animateItem(
                                placementSpec = FocuslistMotion.listChange()
                            )
                        )
                    }

                    itemsIndexed(
                        items = section.tasks,
                        key = { _, task -> task.id }
                    ) { index, task ->
                        TaskListRow(
                            task = task,
                            today = today,
                            // Each day rounds its own corners, so a section
                            // reads as one collection rather than a slice of a
                            // longer one.
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = section.tasks.size
                            ),
                            colors = taskColors,
                            onToggleComplete = { onToggleComplete(task.id) },
                            onOpen = { onOpenTask(task.id) },
                            onDelete = { onDelete(task.id) },
                            onReschedule = { date -> onReschedule(task.id, date) },
                            // The heading above already names the day.
                            showDate = false,
                            modifier = Modifier.animateItem(
                                placementSpec = FocuslistMotion.listChange()
                            )
                        )
                    }
                }
            }
        }
    }
}

/** A fixed timestamp, so previews do not shift with the clock. */
private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private fun sampleUpcomingTasks(): List<Task> {
    val today = LocalDate.now()

    return listOf(
        Task(
            id = "1",
            title = "Book the dentist",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today.plusDays(1)
        ),
        Task(
            id = "2",
            title = "Draft the quarterly review, including the parts nobody wants " +
                "to write down",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today.plusDays(3),
            estimatedDurationMinutes = 90
        ),
        Task(
            id = "3",
            title = "Renew the domain",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today.plusDays(12),
            estimatedDurationMinutes = 10
        ),
        // Excluded: scheduled for today, not beyond it.
        Task(
            id = "4",
            title = "Chase the missing invoice",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today
        )
    )
}

@Preview(name = "Upcoming light", heightDp = 640)
@Preview(name = "Upcoming dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UpcomingScreenPreview() {
    val today = LocalDate.now()
    FocuslistTheme(dynamicColor = false) {
        UpcomingContent(
            tasks = upcomingTasks(sampleUpcomingTasks(), today),
            today = today,
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
        )
    }
}

@Preview(name = "Upcoming empty light", heightDp = 640)
@Preview(name = "Upcoming empty dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UpcomingScreenEmptyPreview() {
    FocuslistTheme(dynamicColor = false) {
        UpcomingContent(
            tasks = emptyList(),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
        )
    }
}

@Preview(name = "Upcoming large font", heightDp = 640, fontScale = 2f)
@Composable
private fun UpcomingScreenLargeFontPreview() {
    val today = LocalDate.now()
    FocuslistTheme(dynamicColor = false) {
        UpcomingContent(
            tasks = upcomingTasks(sampleUpcomingTasks(), today),
            today = today,
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
        )
    }
}
