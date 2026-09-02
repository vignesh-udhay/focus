package com.vignesh.focuslist.ui.logbook

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.completedTasks
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

/**
 * The Logbook: everything finished.
 *
 * The counterpart to the active lists, and the reason completing a task is
 * never destructive. Every other list drops a task once it is done; this one
 * keeps it, whatever its placement or scheduled date, so nothing a user
 * finishes can fall out of reach once the undo offer has passed.
 *
 * Unchecking a row here reopens the task through the ordinary completion path.
 * It leaves the Logbook and returns to whichever active lists it belongs to.
 *
 * There is no add-task button: nothing is captured already done.
 */
@Composable
fun LogbookScreen(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val tasks by viewModel.completedTasks.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    LogbookContent(
        tasks = tasks,
        today = today,
        onToggleComplete = viewModel::toggleComplete,
        onOpenTask = { id -> openTaskId = id },
        onDelete = viewModel::deleteTask,
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
 * The Logbook layout.
 *
 * Stateless, and the same segmented collection every other list uses. [tasks]
 * arrives already filtered and ordered by `completedTasks`; this screen neither
 * filters nor sorts.
 */
@Composable
private fun LogbookContent(
    tasks: List<Task>,
    today: LocalDate,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val taskColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    )

    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            FocuslistTopAppBar(
                title = stringResource(R.string.logbook_title),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            TaskListEmptyState(
                headline = stringResource(R.string.logbook_empty_headline),
                supporting = stringResource(R.string.logbook_empty_supporting),
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
                        // Unchecking a row reopens the task, and it leaves the Logbook by
                        // moving rather than disappearing.
                        modifier = Modifier.animateItem(
                            placementSpec = FocuslistMotion.listChange()
                        )
                    )
                }
            }
        }
    }
}

/** A fixed timestamp, so previews do not shift with the clock. */
private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private fun sampleCompletedTasks(): List<Task> = listOf(
    Task(
        id = "1",
        title = "Send the sprint summary",
        createdAt = SampleTimestamp,
        placement = TaskPlacement.ANYTIME,
        scheduledDate = LocalDate.now(),
        completedAt = SampleTimestamp.plusSeconds(3_600)
    ),
    Task(
        id = "2",
        title = "Work out whether the analytics migration was worth doing, and write " +
            "down the answer for next time",
        createdAt = SampleTimestamp,
        completedAt = SampleTimestamp.plusSeconds(1_800),
        estimatedDurationMinutes = 45
    ),
    Task(
        id = "3",
        title = "Cancel the old subscription",
        createdAt = SampleTimestamp,
        placement = TaskPlacement.SOMEDAY,
        completedAt = SampleTimestamp
    ),
    // Excluded: still outstanding.
    Task(
        id = "4",
        title = "Chase the missing invoice",
        createdAt = SampleTimestamp,
        scheduledDate = LocalDate.now()
    )
)

@Preview(name = "Logbook light", heightDp = 640)
@Preview(name = "Logbook dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogbookScreenPreview() {
    FocuslistTheme(dynamicColor = false) {
        LogbookContent(
            tasks = completedTasks(sampleCompletedTasks()),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Logbook empty light", heightDp = 640)
@Preview(name = "Logbook empty dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogbookScreenEmptyPreview() {
    FocuslistTheme(dynamicColor = false) {
        LogbookContent(
            tasks = emptyList(),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Logbook large font", heightDp = 640, fontScale = 2f)
@Composable
private fun LogbookScreenLargeFontPreview() {
    FocuslistTheme(dynamicColor = false) {
        LogbookContent(
            tasks = completedTasks(sampleCompletedTasks()),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
        )
    }
}
