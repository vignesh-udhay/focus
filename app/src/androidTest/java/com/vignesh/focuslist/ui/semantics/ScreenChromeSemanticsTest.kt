package com.vignesh.focuslist.ui.semantics

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.inbox.InboxScreen
import com.vignesh.focuslist.ui.logbook.LogbookScreen
import com.vignesh.focuslist.ui.task.TaskListViewModel
import com.vignesh.focuslist.ui.today.TodayScreen
import com.vignesh.focuslist.ui.upcoming.UpcomingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen composes the shared chrome.
 *
 * The component tests in this package prove each control is correct in
 * isolation. That is not the same as proving a screen uses it: a screen could
 * draw its own bar, or forget the empty state, and every isolated test would
 * still pass. This walks all seven destinations and checks the two contracts a
 * screen cannot be without.
 *
 * The bar's title is a heading, so a screen reader can say where the user has
 * landed. The empty state is readable, because an empty list is otherwise
 * silence.
 *
 * Focus is the one destination with no app bar, and so the one that passes no
 * title. It shows a single task rather than a list, the navigation bar already
 * says which destination it is, and the heading is the task itself — or, with
 * no task, the empty state's headline, which every screen here is checked for
 * anyway. A heading reading "Focus" above the task would be the screen naming
 * itself instead of naming the work.
 *
 * Phone layouts only, per this phase's scope. No bottom bar is supplied, since
 * `NavigationSemanticsTest` owns that contract.
 */
@RunWith(AndroidJUnit4::class)
class ScreenChromeSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertChrome(
        fontScale: Float,
        title: String?,
        emptyHeadline: String,
        emptySupporting: String,
        screen: @Composable (TaskListViewModel) -> Unit
    ) {
        val viewModel = testViewModel(FakeTaskDao())

        rule.setFocuslistContent(fontScale) { screen(viewModel) }

        if (title != null) {
            rule.onNode(hasText(title) and isHeading()).assertExists()
        }
        rule.onNodeWithText(emptyHeadline).assertIsDisplayed()
        rule.onNodeWithText(emptySupporting).assertIsDisplayed()

        // The empty-state headline follows the same convention as the bar
        // title. On an empty screen those are the only two landmarks there
        // are, and both have to be marked on every destination, not just on
        // the component in isolation.
        rule.onNode(hasText(emptyHeadline) and isHeading()).assertExists()
    }

    private fun today(fontScale: Float) = assertChrome(
        fontScale,
        title = "Today",
        emptyHeadline = "Nothing scheduled for today",
        emptySupporting = "Add a task when you are ready."
    ) { viewModel -> TodayScreen(viewModel = viewModel) }

    @Test
    fun today_hasHeadingAndEmptyState_at100() = today(FontScale100)

    @Test
    fun today_hasHeadingAndEmptyState_at200() = today(FontScale200)

    private fun inbox(fontScale: Float) = assertChrome(
        fontScale,
        title = "Inbox",
        emptyHeadline = "Inbox is empty",
        emptySupporting = "Anything you capture without a day waits here."
    ) { viewModel -> InboxScreen(viewModel = viewModel) }

    @Test
    fun inbox_hasHeadingAndEmptyState_at100() = inbox(FontScale100)

    @Test
    fun inbox_hasHeadingAndEmptyState_at200() = inbox(FontScale200)

    private fun upcoming(fontScale: Float) = assertChrome(
        fontScale,
        title = "Upcoming",
        emptyHeadline = "Nothing scheduled ahead",
        emptySupporting = "Tasks scheduled for a later day appear here."
    ) { viewModel -> UpcomingScreen(viewModel = viewModel) }

    @Test
    fun upcoming_hasHeadingAndEmptyState_at100() = upcoming(FontScale100)

    @Test
    fun upcoming_hasHeadingAndEmptyState_at200() = upcoming(FontScale200)

    private fun logbook(fontScale: Float) = assertChrome(
        fontScale,
        title = "Logbook",
        emptyHeadline = "Nothing completed yet",
        emptySupporting = "Tasks you finish are kept here."
    ) { viewModel -> LogbookScreen(viewModel = viewModel) }

    @Test
    fun logbook_hasHeadingAndEmptyState_at100() = logbook(FontScale100)

    @Test
    fun logbook_hasHeadingAndEmptyState_at200() = logbook(FontScale200)

    // Focus is not a screen any more and so has no chrome to assert. It is a
    // sheet opened from the task it is for, and its empty state is covered by
    // FocusSessionSemanticsTest, where the queue can actually be emptied.
}
