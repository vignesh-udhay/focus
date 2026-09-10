package com.vignesh.focuslist.ui.semantics

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.data.local.TaskDao
import com.vignesh.focuslist.ui.inbox.InboxScreen
import com.vignesh.focuslist.ui.logbook.LogbookScreen
import com.vignesh.focuslist.ui.today.TodayScreen
import com.vignesh.focuslist.ui.upcoming.UpcomingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the four lists say before the stored read has answered, per D-065.
 *
 * They used to say the user had no work. `Loaded(emptyList())` was the initial
 * value of the read, so an empty list and a list nobody had looked at were the
 * same value, and every list asserted something specific and untrue about the
 * user's day for as long as the read took.
 *
 * Two halves to each case, and the second is the one that would rot. Absence
 * before the read is the fix; presence after it is the thing the fix could
 * quietly break, and a list that never shows its empty state again would pass a
 * test that only checked the first half.
 *
 * One font scale. The contract is which strings exist, and no part of it varies
 * with the size they are drawn at.
 */
@RunWith(AndroidJUnit4::class)
class ListLoadingSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setScreen(dao: TaskDao, screen: @Composable (dao: TaskDao) -> Unit) {
        rule.setFocuslistContent(FontScale100) { screen(dao) }
    }

    /**
     * The read never answers, so the empty state must not appear, and the
     * chrome must.
     *
     * The title is what makes the blank safe rather than a trap: the bar is
     * drawn above the branch, so a read that never returns still leaves a
     * screen the user recognises and can leave.
     */
    private fun assertSaysNothingBeforeTheRead(
        title: String,
        emptyHeadline: String,
        screen: @Composable (dao: TaskDao) -> Unit
    ) {
        setScreen(SilentTaskDao(), screen)

        rule.onNodeWithText(emptyHeadline).assertDoesNotExist()
        rule.onNodeWithText(title).assertIsDisplayed()
    }

    /** The read answers with nothing, which is when the empty state is true. */
    private fun assertEmptyStateSurvives(
        emptyHeadline: String,
        screen: @Composable (dao: TaskDao) -> Unit
    ) {
        setScreen(FakeTaskDao(), screen)

        rule.onNodeWithText(emptyHeadline).assertIsDisplayed()
    }

    @Test
    fun today_saysNothingBeforeTheRead() = assertSaysNothingBeforeTheRead(
        title = "Today",
        emptyHeadline = "Nothing scheduled for today"
    ) { dao -> TodayScreen(viewModel = testViewModel(dao), onOpenTask = {}) }

    @Test
    fun today_emptyStateSurvivesTheRead() = assertEmptyStateSurvives(
        emptyHeadline = "Nothing scheduled for today"
    ) { dao -> TodayScreen(viewModel = testViewModel(dao), onOpenTask = {}) }

    @Test
    fun inbox_saysNothingBeforeTheRead() = assertSaysNothingBeforeTheRead(
        title = "Inbox",
        emptyHeadline = "Inbox is empty"
    ) { dao -> InboxScreen(viewModel = testViewModel(dao), onOpenTask = {}) }

    @Test
    fun inbox_emptyStateSurvivesTheRead() = assertEmptyStateSurvives(
        emptyHeadline = "Inbox is empty"
    ) { dao -> InboxScreen(viewModel = testViewModel(dao), onOpenTask = {}) }

    @Test
    fun upcoming_saysNothingBeforeTheRead() = assertSaysNothingBeforeTheRead(
        title = "Upcoming",
        emptyHeadline = "Nothing scheduled ahead"
    ) { dao -> UpcomingScreen(viewModel = testViewModel(dao), onOpenTask = {}) }

    @Test
    fun upcoming_emptyStateSurvivesTheRead() = assertEmptyStateSurvives(
        emptyHeadline = "Nothing scheduled ahead"
    ) { dao -> UpcomingScreen(viewModel = testViewModel(dao), onOpenTask = {}) }

    @Test
    fun logbook_saysNothingBeforeTheRead() = assertSaysNothingBeforeTheRead(
        title = "Logbook",
        emptyHeadline = "Nothing completed yet"
    ) { dao -> LogbookScreen(viewModel = testViewModel(dao), onOpenTask = {}) }

    @Test
    fun logbook_emptyStateSurvivesTheRead() = assertEmptyStateSurvives(
        emptyHeadline = "Nothing completed yet"
    ) { dao -> LogbookScreen(viewModel = testViewModel(dao), onOpenTask = {}) }
}
