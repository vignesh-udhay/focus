package com.vignesh.focuslist.ui.inbox

import android.content.res.Configuration
import androidx.compose.foundation.layout.RowScope
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
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.inboxTasks
import com.vignesh.focuslist.ui.component.AddTaskFab
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.TaskListRow
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.QuickAddSheet
import com.vignesh.focuslist.ui.task.TaskDetailsSheetHost
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Instant
import java.time.LocalDate

/**
 * Inbox: everything outstanding without a scheduled date.
 *
 * A task leaves by being scheduled, completed, or deleted.
 *
 * Quick Add here captures without a date, which is the difference from Today.
 * Deciding when to do it is exactly the decision Inbox exists to defer.
 */
@Composable
fun InboxScreen(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    // The three dots at the end of the header row. A slot rather than a route,
    // because navigating is the host's job and this screen only has to leave
    // room for it.
    overflow: @Composable RowScope.() -> Unit = {}
) {
    val tasks by viewModel.inboxTasks.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    // Screen state, not app state: opening Quick Add here says nothing about
    // whether Today has its own sheet open.
    var isQuickAddVisible by rememberSaveable { mutableStateOf(false) }
    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    InboxContent(
        tasks = tasks,
        today = today,
        onToggleComplete = viewModel::toggleComplete,
        onOpenTask = { id -> openTaskId = id },
        onDelete = viewModel::deleteTask,
        onReschedule = viewModel::rescheduleTask,
        onAddTask = { isQuickAddVisible = true },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = bottomBar,
        overflow = overflow
    )

    if (isQuickAddVisible) {
        QuickAddSheet(
            today = today,
            onDismiss = { isQuickAddVisible = false },
            // Captured without a date unless the title named one: deciding when
            // to do it is the decision Inbox defers, but a user who already
            // said "friday" has made it. A blank title captures nothing and
            // leaves the sheet open.
            onSave = { parsed ->
                val captured = viewModel.createTask(
                    title = parsed.title,
                    scheduledDate = parsed.date
                )
                if (captured) isQuickAddVisible = false
            }
        )
    }

    TaskDetailsSheetHost(
        openTaskId = openTaskId,
        tasks = tasks,
        today = today,
        viewModel = viewModel,
        onDismiss = { openTaskId = null }
    )
}

/**
 * The Inbox layout.
 *
 * Stateless, and the same segmented collection Today and Upcoming use. [tasks]
 * arrives already filtered and ordered by `inboxTasks`; this screen neither
 * filters nor sorts.
 */
@Composable
private fun InboxContent(
    tasks: List<Task>,
    today: LocalDate,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReschedule: (String, LocalDate?) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {},
    overflow: @Composable RowScope.() -> Unit = {}
) {
    // The large title collapses as the list moves under it, as on Today.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val taskColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            FocuslistTopAppBar(
                actions = overflow,
                title = stringResource(R.string.inbox_title),
                // Nothing to count when the list is empty, and "0 items
                // waiting to process" above an empty state says the same
                // thing twice, the second time worse.
                subtitle = if (tasks.isEmpty()) {
                    null
                } else {
                    {
                        Text(
                            text = pluralStringResource(
                                R.plurals.inbox_waiting,
                                tasks.size,
                                tasks.size
                            )
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AddTaskFab(
                onClick = onAddTask,
                // Follows the content column in on a wide window, as on Today.
                modifier = Modifier.padding(end = gutter)
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            TaskListEmptyState(
                headline = stringResource(R.string.inbox_empty_headline),
                supporting = stringResource(R.string.inbox_empty_supporting),
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FocuslistSpacing.md + gutter,
                    end = FocuslistSpacing.md + gutter,
                    top = innerPadding.calculateTopPadding() + FocuslistSpacing.xs,
                    // Clears the floating action button, as on Today.
                    bottom = innerPadding.calculateBottomPadding() + FocuslistDimensions.FabClearance
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
                        // A task leaving the list when it is scheduled or
                        // completed travels out rather than vanishing.
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

private fun sampleInboxTasks(): List<Task> = listOf(
    Task(
        id = "1",
        title = "Ask Priya about the contract renewal",
        createdAt = SampleTimestamp
    ),
    Task(
        id = "2",
        title = "Work out whether the analytics migration is worth doing at all, " +
            "or whether it can simply be dropped",
        createdAt = SampleTimestamp.plusSeconds(60)
    ),
    Task(
        id = "3",
        title = "Replace the kitchen bulb",
        createdAt = SampleTimestamp.plusSeconds(120),
        estimatedDurationMinutes = 5
    ),
    // Excluded: scheduled, so it belongs in a dated list.
    Task(
        id = "4",
        title = "Chase the missing invoice",
        createdAt = SampleTimestamp.plusSeconds(180),
        scheduledDate = LocalDate.now()
    ),
    // Included: it has no scheduled date.
    Task(
        id = "5",
        title = "Read the Compose performance guide",
        createdAt = SampleTimestamp.plusSeconds(240)
    )
)

@Preview(name = "Inbox light", heightDp = 640)
@Preview(name = "Inbox dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InboxScreenPreview() {
    FocuslistTheme(dynamicColor = false) {
        InboxContent(
            tasks = inboxTasks(sampleInboxTasks()),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
            onAddTask = {}
        )
    }
}

@Preview(name = "Inbox empty light", heightDp = 640)
@Preview(name = "Inbox empty dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InboxScreenEmptyPreview() {
    FocuslistTheme(dynamicColor = false) {
        InboxContent(
            tasks = emptyList(),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
            onAddTask = {}
        )
    }
}

@Preview(name = "Inbox large font", heightDp = 640, fontScale = 2f)
@Composable
private fun InboxScreenLargeFontPreview() {
    FocuslistTheme(dynamicColor = false) {
        InboxContent(
            tasks = inboxTasks(sampleInboxTasks()),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
            onAddTask = {}
        )
    }
}
