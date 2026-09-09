package com.vignesh.focuslist.ui.today

import android.content.res.Configuration
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vignesh.focuslist.core.domain.ReminderHealthState
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.core.domain.TodayBand
import com.vignesh.focuslist.core.domain.TodaySection
import com.vignesh.focuslist.core.domain.todaySections
import com.vignesh.focuslist.core.domain.todayTasks
import com.vignesh.focuslist.ui.component.AddTaskFab
import com.vignesh.focuslist.ui.component.DurationLabel
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.AllDoneMascot
import com.vignesh.focuslist.ui.component.TaskListDoneHeader
import com.vignesh.focuslist.ui.component.TaskListEmptyState
import com.vignesh.focuslist.ui.component.TaskListErrorState
import com.vignesh.focuslist.ui.component.SectionLabel
import com.vignesh.focuslist.ui.component.TaskListRow
import com.vignesh.focuslist.ui.component.TodayMascot
import com.vignesh.focuslist.ui.component.durationLabel
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.task.QuickAddSheet
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.task.UndoSnackbarEffect
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Today, the default task view.
 *
 * Holds no task state of its own: it reads the view model and hands the result
 * to the stateless [TodayContent], which remains the preview and test seam.
 */
@Composable
fun TodayScreen(
    viewModel: TaskListViewModel,
    // Tapping a row opens Task Details, which is a destination since D-018
    // rather than a sheet this screen hosts. The route is the host's to know.
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    quickAddRequest: Int = 0,
    // Called once the request above has been acted on, so the host can retire
    // it. Without this the request is a latch rather than an event: see the
    // effect below.
    onQuickAddRequestHandled: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    // Whether reminders can currently be relied on, read from the health view
    // model the host owns rather than from this screen's own. D-040 draws it as
    // a banner for the two states the app is sure about; the default is the
    // state that draws nothing, so a caller with no opinion gets no banner.
    reminderHealth: ReminderHealthState? = null,
    onOpenReminderHealth: () -> Unit = {},
    onDismissReminderHealth: () -> Unit = {},
    // The three dots at the end of the header row. A slot rather than a route,
    // because navigating is the host's job and this screen only has to leave
    // room for it.
    overflow: @Composable RowScope.() -> Unit = {}
) {
    val tasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val pausedTask by viewModel.pausedFocusTask.collectAsStateWithLifecycle()
    val focusSession by viewModel.focusSession.collectAsStateWithLifecycle()
    val readFailed by viewModel.readFailed.collectAsStateWithLifecycle()

    // Screen state, not app state: opening Quick Add here says nothing about
    // whether Inbox has its own sheet open.
    var isQuickAddVisible by rememberSaveable { mutableStateOf(false) }

    // **The request is retired as soon as it is acted on**, because this screen
    // is a navigation destination and is disposed the moment the user leaves it.
    // A request that stays above zero is therefore replayed by every later
    // composition: open Quick Add from the widget, dismiss it, visit Inbox, come
    // back, and the sheet opens again on a request the user made once and had
    // already answered. The host holds the counter in `rememberSaveable`, so
    // that survived process death too.
    LaunchedEffect(quickAddRequest) {
        if (quickAddRequest > 0) {
            isQuickAddVisible = true
            onQuickAddRequestHandled()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    TodayContent(
        tasks = tasks,
        today = today,
        pausedTask = pausedTask,
        // Read once here rather than inside the card, so the card stays a
        // stateless thing that renders what it is handed. A paused session is
        // stopped, so this value does not move and needs no ticking.
        //
        // No guard on the reason any more. The task is only non-null when a
        // session is paused on it, which is the whole of what D-048 left the card
        // speaking for.
        pausedRemainingMinutes = pausedTask?.let { task ->
            focusSession?.remaining(Instant.now(), task.estimatedDurationMinutes)?.toMinutes()
        },
        onToggleComplete = viewModel::toggleComplete,
        onOpenTask = onOpenTask,
        // The card's one action. It resumes, and the sheet opens on a session
        // already running.
        //
        // There is no longer a second behaviour behind it. D-012's card could also
        // land on Ready, for a task the *app* had picked, where Ready was the user
        // agreeing with the choice before the clock ran. D-048 leaves only the
        // task the user picked and started themselves, so there is nothing left to
        // agree to and pressing Resume twice would be a confirmation of a
        // confirmation.
        onResumeFocus = {
            viewModel.resumeFocusFromCard()
            onOpenFocus()
        },
        onAddTask = { isQuickAddVisible = true },
        readFailed = readFailed,
        onRetry = viewModel::retryRead,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = bottomBar,
        overflow = overflow,
        reminderHealth = reminderHealth,
        onOpenReminderHealth = onOpenReminderHealth,
        onDismissReminderHealth = onDismissReminderHealth
    )


    if (isQuickAddVisible) {
        QuickAddSheet(
            // The collected day, so the sheet marks and names the same date the
            // capture will get, and both follow a rollover while it is open.
            today = today,
            // Today dates an undated capture, so the line under the field can
            // say so. D-053.
            fallbackDate = today,
            onDismiss = { isQuickAddVisible = false },
            // Captured onto today's list unless the title named a day itself.
            onSave = { parsed ->
                // Read at save time, so a task captured after midnight gets
                // the new day rather than the one the screen was built on. A
                // blank title captures nothing and leaves the sheet open.
                val day = parsed.date ?: viewModel.today.value
                val captured = viewModel.createTask(
                    title = parsed.title,
                    scheduledDate = day,
                    // A trailing time sets a reminder, per D-011. It lands on
                    // the day the task is being saved to, so a time with no day
                    // of its own is a reminder today, or tomorrow when today's
                    // has gone by: D-030 resolves it forward rather than
                    // capturing a moment that can only ring at once.
                    //
                    // The clock is read here for the same reason the day above
                    // is, and from the same instant it is compared against.
                    reminderAt = parsed.reminderAt(day, LocalDateTime.now())
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
    pausedTask: Task?,
    pausedRemainingMinutes: Long? = null,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onResumeFocus: () -> Unit = {},
    onAddTask: () -> Unit,
    readFailed: Boolean = false,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {},
    overflow: @Composable RowScope.() -> Unit = {},
    reminderHealth: ReminderHealthState? = null,
    onOpenReminderHealth: () -> Unit = {},
    onDismissReminderHealth: () -> Unit = {}
) {
    // The collection runs away from the page rather than sitting a step above
    // it: toward white in light, toward black in dark. The page is the tinted
    // ground and the list is the thing on it, which is the relationship the
    // Material products this was measured against use.
    val taskColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    // The bands todayTasks already sorted into. Reading them here, rather than
    // re-deriving the rule, keeps the ordering owned by TaskQueries.
    //
    // Nothing is held back. D-048 leaves the paused session's task in its band,
    // so every task Today shows is in exactly one of them.
    val sections = todaySections(tasks, today)

    // A day that had work and finished it, which D-033 separates from a day
    // that never had any. Read off the bands rather than the tasks, so the one
    // place that decides what is outstanding stays `todaySections`.
    //
    // A paused session still rules it out, and now for a narrower reason. When the
    // paused task is on today's list it sits in a band that is not Completed, so
    // the bands rule it out on their own. When it is not on today's list at all,
    // which happens to a task focused from Inbox, the bands cannot see it: "all
    // done" above a card reading "Paused, 15 min remaining" would be the two
    // halves of the screen disagreeing.
    val isEverythingDone = pausedTask == null &&
        sections.isNotEmpty() &&
        sections.all { it.band == TodayBand.COMPLETED }

    // Collapsed by default, per D-012, and screen state rather than app state:
    // whether the user opened Completed on Today says nothing about anything
    // else. Saveable, so it survives a rotation.
    var isCompletedExpanded by rememberSaveable { mutableStateOf(false) }

    // Zero on a phone. On a wide window it is what keeps the collection in a
    // column instead of letting it run the width of the screen.
    val gutter = focuslistContentGutter()

    val listState = rememberLazyListState()
    listState.HoldViewportAcross(sections)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            FocuslistTopAppBar(
                actions = overflow,
                title = stringResource(R.string.today_title)
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
        // Empty means nothing at all, card included. A screen holding a Focus
        // now card and nothing else is not empty, and telling the user there is
        // nothing scheduled while a task sits above the words would be the two
        // halves of the screen disagreeing.
        if (readFailed) {
            TaskListErrorState(
                headline = stringResource(R.string.error_tasks_headline),
                supporting = stringResource(R.string.error_tasks_supporting),
                onRetry = onRetry,
                modifier = Modifier.padding(innerPadding)
            )
        } else if (tasks.isEmpty() && pausedTask == null) {
            // The banner survives the empty screen, and D-040 says this is
            // where it matters most: a user who has just declined the
            // notification permission and owns no tasks yet is exactly the user
            // about to set a first reminder into silence. A Column rather than
            // a list item, because the empty state is not a scroll container
            // and has no contentPadding to carry this.
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ReminderHealthBanner(
                    state = reminderHealth,
                    onOpen = onOpenReminderHealth,
                    onDismissMissed = onDismissReminderHealth,
                    modifier = Modifier.padding(
                        start = FocuslistSpacing.md + gutter,
                        end = FocuslistSpacing.md + gutter,
                        top = FocuslistSpacing.xs
                    )
                )

                TaskListEmptyState(
                    headline = stringResource(R.string.today_empty_headline),
                    supporting = stringResource(R.string.today_empty_supporting),
                    modifier = Modifier.weight(1f),
                    illustration = { TodayMascot() }
                )
            }
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
                // First, above the day's work and above the card, because it is
                // about whether any of that work will be announced at all.
                //
                // No `animateItem`. `expressive-motion.md` gives the banner no
                // token and says why: it appears because a check found
                // something, not because the user did anything, and sliding it
                // in would be the app performing its own bad news.
                if (reminderHealth.hasReminderHealthBanner()) {
                    item(key = "reminder-health") {
                        ReminderHealthBanner(
                            state = reminderHealth,
                            onOpen = onOpenReminderHealth,
                            onDismissMissed = onDismissReminderHealth,
                            // The collection's own gap is for segments of one
                            // list. The banner is not one of them, so it takes
                            // a little more room below itself.
                            modifier = Modifier.padding(bottom = FocuslistSpacing.xs)
                        )
                    }
                }

                // Above the Completed disclosure, not instead of it. The
                // day is finished, which is worth saying, but the rows are
                // still how a task completed today is reopened once the undo
                // snackbar has gone. D-033 records the version that replaced
                // them and why it was wrong.
                if (isEverythingDone) {
                    item(key = "all-done") {
                        TaskListDoneHeader(
                            headline = stringResource(R.string.today_all_done_headline),
                            supporting = stringResource(R.string.today_all_done_supporting),
                            illustration = { AllDoneMascot() },
                            modifier = Modifier.animateItem(
                                fadeInSpec = FocuslistMotion.reveal(),
                                placementSpec = FocuslistMotion.reveal(),
                                fadeOutSpec = FocuslistMotion.reveal()
                            )
                        )
                    }
                }

                if (pausedTask != null) {
                    item(key = "paused-session") {
                        PausedSessionCard(
                            task = pausedTask,
                            onResume = onResumeFocus,
                            remainingMinutes = pausedRemainingMinutes,
                            // The card arrives and leaves on `reveal`, which is
                            // what that token is for. Not a container transform
                            // out of the row, and since D-048 the row is still
                            // there to make the point: the card is a control
                            // about a task, not that task relocated, and
                            // `expressive-motion.md` allows the app exactly one
                            // shape morph, which Focus has.
                            modifier = Modifier.animateItem(
                                fadeInSpec = FocuslistMotion.reveal(),
                                placementSpec = FocuslistMotion.reveal(),
                                fadeOutSpec = FocuslistMotion.reveal()
                            )
                        )
                    }
                }

                sections.forEach { section ->
                    val isCompleted = section.band == TodayBand.COMPLETED

                    if (isCompleted) {
                        item(key = "label-" + section.band.name) {
                            CompletedDisclosure(
                                count = section.tasks.size,
                                expanded = isCompletedExpanded,
                                onToggle = { isCompletedExpanded = !isCompletedExpanded },
                                modifier = Modifier.animateItem(
                                    placementSpec = FocuslistMotion.listChange()
                                )
                            )
                        }

                        // The band collapses, so its rows are simply not
                        // emitted. The disclosure gets `reveal` through the
                        // rows arriving and leaving, and everything below moves
                        // on `listChange` as it always does.
                        if (!isCompletedExpanded) return@forEach
                    } else {
                        item(key = "label-" + section.band.name) {
                            SectionLabel(
                                text = stringResource(section.labelRes),
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
                            // The heading already fixes the day for every band
                            // but this one: No time set and Later today are
                            // both today, and Completed is whenever it was.
                            // Overdue is the exception, because its tasks come
                            // from various past days and the date is the whole
                            // reason the row is there. It is also what makes
                            // overdue readable without relying on the colour.
                            showDate = section.band == TodayBand.OVERDUE,
                            onToggleComplete = { onToggleComplete(task.id) },
                            onOpen = { onOpenTask(task.id) },
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
 * What a band is called.
 *
 * Every band carries one since D-012. The first band used to carry none, on the
 * argument that "at the top of the Today screen, today's work needs no
 * announcement"; the band order changed, so the first band is no longer the one
 * that needs no announcement and the argument retired with the position.
 *
 * Completed is absent here because it is not a plain label: it counts and it
 * collapses, and `CompletedDisclosure` draws it.
 */
@get:StringRes
private val TodaySection.labelRes: Int
    get() = when (band) {
        TodayBand.OVERDUE -> R.string.today_section_overdue
        TodayBand.NO_TIME_SET -> R.string.today_section_no_time_set
        TodayBand.LATER_TODAY -> R.string.today_section_later_today
        // Drawn by CompletedDisclosure, which never asks for this. Named rather
        // than thrown, so a future band added above cannot crash the screen.
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
            scheduledDate = today,
            estimatedDurationMinutes = 30
        ),
        Task(
            id = "4",
            title = "Book the dentist",
            createdAt = SampleTimestamp,
            scheduledDate = today.plusDays(1)
        ),
        Task(
            id = "5",
            title = "Send the sprint summary",
            createdAt = SampleTimestamp,
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
            scheduledDate = today.minusDays(2),
            estimatedDurationMinutes = 15
        ),
        // The paused session's task. Scheduled for today with no time, so it
        // shows up in the "No time set" band as well as on the card: since D-048
        // the card no longer takes its task out of the list, and a preview that
        // hid the overlap would hide the thing worth looking at.
        Task(
            id = "8",
            title = "Confirm the venue booking",
            createdAt = SampleTimestamp,
            scheduledDate = today,
            estimatedDurationMinutes = 20
        )
    )
}

/** The sample task the paused session card is drawn for. */
private fun samplePausedTask(): Task = sampleTodayTasks().first { it.id == "8" }

@Preview(name = "Today light", heightDp = 640)
@Preview(name = "Today dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayScreenPreview() {
    val today = LocalDate.now()
    FocuslistTheme(dynamicColor = false) {
        TodayContent(
            tasks = todayTasks(sampleTodayTasks(), today),
            today = today,
            pausedTask = samplePausedTask(),
            pausedRemainingMinutes = 12,
            onToggleComplete = {},
            onOpenTask = {},
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
            pausedTask = null,
            onToggleComplete = {},
            onOpenTask = {},
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
            pausedTask = samplePausedTask(),
            pausedRemainingMinutes = 12,
            onToggleComplete = {},
            onOpenTask = {},
            onAddTask = {},
        )
    }
}
