package com.vignesh.focuslist.ui.today

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import androidx.annotation.StringRes
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistMotion
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TaskPlacement
import com.vignesh.focuslist.core.domain.TodayBand
import com.vignesh.focuslist.core.domain.TodaySection
import com.vignesh.focuslist.core.domain.todaySections
import com.vignesh.focuslist.core.domain.todayPlannedMinutes
import com.vignesh.focuslist.core.domain.todayTasks
import com.vignesh.focuslist.ui.component.AddTaskFab
import com.vignesh.focuslist.ui.component.DurationLabel
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.TaskListRow
import com.vignesh.focuslist.ui.component.durationLabel
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.QuickAddSheet
import com.vignesh.focuslist.ui.task.TaskDetailsSheetHost
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Today, the default task view.
 *
 * Holds no task state of its own: it reads the view model and hands the result
 * to the stateless [TodayContent], which remains the preview and test seam.
 */
@Composable
fun TodayScreen(
    viewModel: TaskListViewModel,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    onOpenFocus: () -> Unit = {}
) {
    val tasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()

    // Screen state, not app state: opening Quick Add here says nothing about
    // whether Inbox has its own sheet open.
    var isQuickAddVisible by rememberSaveable { mutableStateOf(false) }
    var openTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    TodayContent(
        tasks = tasks,
        today = today,
        onToggleComplete = viewModel::toggleComplete,
        onOpenTask = { id -> openTaskId = id },
        onDelete = viewModel::deleteTask,
        onReschedule = viewModel::rescheduleTask,
        // Choose the task, then move to Focus. The queue is derived from this
        // very list, so the task is already in it and Focus opens on it.
        //
        // Straight into the session, without stopping at the ready state.
        // Picking one task out of a list and choosing Focus on it is the
        // deciding already done; asking the user to confirm it with a second
        // tap would be the friction Quick Add was just cleared of.
        onFocusTask = { id ->
            viewModel.focusTask(id)
            viewModel.startFocusSession()
            onOpenFocus()
        },
        onAddTask = { isQuickAddVisible = true },
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

    if (isQuickAddVisible) {
        QuickAddSheet(
            // The collected day, so the sheet marks and names the same date the
            // capture will get, and both follow a rollover while it is open.
            today = today,
            onDismiss = { isQuickAddVisible = false },
            // Captured onto today's list unless the title named a day itself.
            onSave = { parsed ->
                // Read at save time, so a task captured after midnight gets
                // the new day rather than the one the screen was built on. A
                // blank title captures nothing and leaves the sheet open.
                val captured = viewModel.createTask(
                    title = parsed.title,
                    scheduledDate = parsed.date ?: viewModel.today.value
                )
                if (captured) isQuickAddVisible = false
            }
        )
    }
}

/**
 * The Today layout.
 *
 * Stateless: it renders the tasks it is handed and reports completion,
 * opening, deletion, and starting Focus by task id. It owns the Scaffold, the app bar, the collection, the
 * empty state, and the floating action button, but none of the task state.
 *
 * [tasks] is already filtered to the Today view. [today] is passed in rather
 * than read from the clock so the layout stays deterministic.
 *
 * [snackbarHostState] is hoisted so the caller can raise the undo offer. The
 * default keeps the previews self-contained.
 */
@Composable
private fun TodayContent(
    tasks: List<Task>,
    today: LocalDate,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReschedule: (String, LocalDate?) -> Unit,
    onFocusTask: (String) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {}
) {
    // The large title collapses into a small bar as the list moves under it.
    // A pinned behaviour would hold all 152dp of it in place, which spends a
    // sixth of the screen on a word the user just tapped to get to.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // The collection runs away from the page rather than sitting a step above
    // it: toward white in light, toward black in dark. The page is the tinted
    // ground and the list is the thing on it, which is the relationship the
    // Material products this was measured against use.
    val taskColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    // The bands todayTasks already sorted into. Reading them here, rather than
    // re-deriving the rule, keeps the ordering owned by TaskQueries.
    val sections = todaySections(tasks, today)

    // Zero on a phone. On a wide window it is what keeps the collection in a
    // column instead of letting it run the width of the screen.
    val gutter = focuslistContentGutter()

    val listState = rememberLazyListState()
    listState.HoldViewportAcross(sections)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            FocuslistTopAppBar(
                title = stringResource(R.string.today_title),
                subtitle = { TodaySubtitle(today = today, tasks = tasks) },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AddTaskFab(
                onClick = onAddTask,
                // Follows the content column in rather than sitting against
                // the window edge, so on a wide screen the button stays with
                // the list it adds to.
                modifier = Modifier.padding(end = gutter)
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            TaskListEmptyState(
                headline = stringResource(R.string.today_empty_headline),
                supporting = stringResource(R.string.today_empty_supporting),
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FocuslistSpacing.md + gutter,
                    end = FocuslistSpacing.md + gutter,
                    top = innerPadding.calculateTopPadding() + FocuslistSpacing.xs,
                    // Clears the floating action button, so the last task in the
                    // collection stays fully visible and tappable.
                    bottom = innerPadding.calculateBottomPadding() + FocuslistDimensions.FabClearance
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                sections.forEach { section ->
                    section.labelRes?.let { labelRes ->
                        item(key = "label-" + section.band.name) {
                            SectionLabel(
                                text = stringResource(labelRes),
                                modifier = Modifier.animateItem(
                                    placementSpec = FocuslistMotion.listChange()
                                )
                            )
                        }
                    }

                    itemsIndexed(
                        items = section.tasks,
                        key = { _, task -> task.id }
                    ) { index, task ->
                        TaskListRow(
                            task = task,
                            today = today,
                            // Each band rounds its own corners, so a section
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
                            // Today is the only list that offers this: the queue
                            // is derived from Today, so nowhere else can start
                            // Focus without inventing a reason the task belongs.
                            onFocus = { onFocusTask(task.id) },
                            // A completed task travelling to its band is the
                            // movement that makes the ordering legible.
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

/**
 * Holds the viewport still when [sections] changes shape.
 *
 * `LazyColumn` remembers the *key* of its first visible item, not just its
 * index, and after a data change it looks that key up and scrolls so the item
 * stays first. That is the right behaviour almost always: it stops tasks
 * appearing above the viewport from shoving the list down.
 *
 * It is wrong in exactly one case, and Today is the only screen that can reach
 * it. Completing the task at the top moves that task to the completed band
 * rather than removing it, so its key travels to the bottom of the list and the
 * viewport dutifully follows, dragging the user to a band they were not looking
 * at. Completing any other task does nothing of the sort, because the first
 * visible item has not moved. Every other list drops a completed task outright,
 * and a key that no longer exists falls back to the previous index, so nothing
 * moves there.
 *
 * `requestScrollToItem` is the framework's own answer: it pins the position by
 * index and drops the remembered key, but only for the very next remeasure.
 * That is why this cannot be done from the checkbox. Completing a task writes
 * to the database and the new list arrives an unknown number of frames later,
 * and `requestScrollToItem` schedules a remeasure of its own that would consume
 * the request long before then.
 *
 * So it is done here instead, from a `SideEffect` that runs when a composition
 * carrying a new order has been applied and before that frame is laid out. The
 * request is still in force for exactly the remeasure that would otherwise
 * chase the key.
 *
 * Pinning to the current index rather than the current key is the point: the
 * rows below simply move up by one, which is what the user expects to see.
 */
@Composable
private fun LazyListState.HoldViewportAcross(sections: List<TodaySection>) {
    val order = sections.flatMap { section -> section.tasks.map(Task::id) }
    val previous = remember { mutableStateOf<List<String>?>(null) }

    SideEffect {
        val before = previous.value
        if (before != null && before != order) {
            requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        }
        previous.value = order
    }
}

/**
 * The line under the Today title: the date, and what is still on the plate.
 *
 * The date is the anchor. "Today" alone does not say which day it is, and a
 * task list is one of the few screens where that matters.
 *
 * Two facts on one line, told apart by where they sit rather than by drawing a
 * container around one of them. The total was briefly given a tinted pill; it
 * read as decoration, which `PRODUCT.md` rules out, and alignment separates the
 * two just as well for nothing.
 *
 * The total is not a score: nothing accumulates, nothing is compared, and a day
 * with no estimates simply shows no total.
 */
@Composable
private fun TodaySubtitle(today: LocalDate, tasks: List<Task>) {
    val planned = todayPlannedMinutes(tasks, today)

    Row(
        // Lines the total up with the right edge of the task collection. The
        // app bar's own end inset is smaller than its start inset, so without
        // this the total overhangs the rows. See the token for the measurement.
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = FocuslistDimensions.AppBarTrailingTextAlignment),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = today.format(rememberSubtitleDateFormat()))

        if (planned != null) {
            val duration = durationLabel(planned)
            val description =
                stringResource(R.string.today_planned_description, duration.spoken)

            // The compact form is what fits beside a date. It is not a
            // sentence, so the spoken form rides along as a description; the
            // total appears nowhere else.
            Text(
                text = stringResource(R.string.today_planned, duration.text),
                modifier = Modifier.semantics { contentDescription = description }
            )
        }
    }
}

/**
 * The subtitle's date, abbreviated.
 *
 * "Wed, Sep 2" rather than "Wednesday, September 2". The long form spent most of
 * the line on two words the user is not reading; the short form says the same
 * thing and leaves the width to the total at the other end.
 *
 * The year is left out. It is noise on a screen about today.
 *
 * Built from a skeleton rather than a literal pattern, because field order is
 * not universal: `getBestDateTimePattern` returns the arrangement the locale
 * actually uses for a weekday, a month and a day, which a hardcoded
 * "EEE, MMM d" would get wrong everywhere it differs.
 */
@Composable
private fun rememberSubtitleDateFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, SubtitleDateSkeleton),
            locale
        )
    }
}

/** Weekday, month, day: the three fields the subtitle shows. */
private const val SubtitleDateSkeleton = "EEEMMMd"


/**
 * A band's name, above the tasks in it.
 *
 * Deliberately slight: a line of label text and nothing else. No divider, no
 * container, no count, not collapsible. It exists to explain an order the list
 * already had, not to turn Today into a dashboard.
 *
 * The first band carries no label. At the top of the Today screen, "today's
 * work" needs no announcement.
 */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // No start inset. The label belongs to the collection beneath it, so it
        // begins where those cards begin. An inset of its own put it 12dp
        // inboard of the cards and ~66dp outboard of the row titles, which is
        // to say aligned with nothing on the screen.
        modifier = modifier.padding(
            top = FocuslistSpacing.lg,
            bottom = FocuslistSpacing.xs
        )
    )
}

/** What a band is called, or null for the band that begins the screen. */
private val TodaySection.labelRes: Int?
    @StringRes get() = when (band) {
        TodayBand.SCHEDULED -> null
        TodayBand.OVERDUE -> R.string.today_section_overdue
        TodayBand.COMPLETED -> R.string.today_section_completed
    }

/**
 * A fixed timestamp for the sample fixture, so previews stay deterministic
 * rather than shifting with the clock.
 */
private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private fun sampleTodayTasks(): List<Task> {
    val today = LocalDate.now()

    return listOf(
        Task(
            id = "1",
            title = "Finish the landing page",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today,
            estimatedDurationMinutes = 45
        ),
        Task(
            id = "2",
            title = "Reply to Priya about the roadmap",
            createdAt = SampleTimestamp
        ),
        Task(
            id = "3",
            title = "Draft the accessibility checklist for the task list, covering " +
                "TalkBack, font scaling and touch targets",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today,
            estimatedDurationMinutes = 30
        ),
        Task(
            id = "4",
            title = "Book the dentist",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today.plusDays(1)
        ),
        Task(
            id = "5",
            title = "Send the sprint summary",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today,
            completedAt = SampleTimestamp
        ),
        Task(
            id = "6",
            title = "Pick up the parcel",
            createdAt = SampleTimestamp,
            completedAt = SampleTimestamp
        ),
        // Overdue, so it still surfaces in Today.
        Task(
            id = "7",
            title = "Chase the missing invoice",
            createdAt = SampleTimestamp,
            placement = TaskPlacement.ANYTIME,
            scheduledDate = today.minusDays(2),
            estimatedDurationMinutes = 15
        )
    )
}

@Preview(name = "Today light", heightDp = 640)
@Preview(name = "Today dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayScreenPreview() {
    val today = LocalDate.now()
    FocuslistTheme(dynamicColor = false) {
        TodayContent(
            tasks = todayTasks(sampleTodayTasks(), today),
            today = today,
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
            onFocusTask = {},
            onAddTask = {},
        )
    }
}

@Preview(name = "Today empty light", heightDp = 640)
@Preview(name = "Today empty dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayScreenEmptyPreview() {
    FocuslistTheme(dynamicColor = false) {
        TodayContent(
            tasks = emptyList(),
            today = LocalDate.now(),
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
            onFocusTask = {},
            onAddTask = {},
        )
    }
}

@Preview(name = "Today large font", heightDp = 640, fontScale = 2f)
@Composable
private fun TodayScreenLargeFontPreview() {
    val today = LocalDate.now()
    FocuslistTheme(dynamicColor = false) {
        TodayContent(
            tasks = todayTasks(sampleTodayTasks(), today),
            today = today,
            onToggleComplete = {},
            onOpenTask = {},
            onDelete = {},
            onReschedule = { _, _ -> },
            onFocusTask = {},
            onAddTask = {},
        )
    }
}
