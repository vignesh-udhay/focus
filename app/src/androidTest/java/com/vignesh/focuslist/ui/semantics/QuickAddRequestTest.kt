package com.vignesh.focuslist.ui.semantics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.today.TodayScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the widget's add button opens Quick Add once, and only once.
 *
 * The request travels from the widget as a counter the host holds in
 * `rememberSaveable`. Today is a navigation destination, so it is disposed the
 * moment the user leaves it and composed again when they return. A request left
 * standing above zero is therefore replayed by every one of those compositions:
 * open Quick Add from the widget, dismiss it, visit Inbox, come back, and the
 * sheet is open again on a request that was made once and already answered.
 * `rememberSaveable` meant it survived process death as well.
 *
 * The harness below is the smallest thing that has the property that matters:
 * a switch that takes Today out of the composition and puts it back, which is
 * what navigating away and returning does.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddRequestTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aQuickAddRequestIsNotReplayedWhenTodayIsComposedAgain() {
        val viewModel = testViewModel(FakeTaskDao())
        var request by mutableIntStateOf(0)
        var onToday by mutableStateOf(true)

        rule.setFocuslistContent(fontScale = 1f) {
            if (onToday) {
                TodayScreen(
                    viewModel = viewModel,
                    onOpenTask = {},
                    quickAddRequest = request,
                    onQuickAddRequestHandled = { request = 0 }
                )
            } else {
                Text(ElsewhereMarker)
            }
        }

        // The widget's add button.
        request = 1
        rule.waitUntilExactlyOneExists(hasText(QuickAddMarker))

        // Away, and back. Today is disposed and built again, exactly as the
        // navigation graph does it.
        onToday = false
        rule.waitUntilExactlyOneExists(hasText(ElsewhereMarker))
        onToday = true
        rule.waitForIdle()

        rule.onNodeWithText(QuickAddMarker).assertDoesNotExist()
    }

    /**
     * A second press still opens it, which is what the counter is for and what
     * a plain "handled" flag would have broken while fixing the replay.
     */
    @Test
    fun pressingAddAgainOpensItAgain() {
        val viewModel = testViewModel(FakeTaskDao())
        var request by mutableIntStateOf(0)

        rule.setFocuslistContent(fontScale = 1f) {
            TodayScreen(
                viewModel = viewModel,
                onOpenTask = {},
                quickAddRequest = request,
                onQuickAddRequestHandled = { request = 0 }
            )
        }

        request = 1
        rule.waitUntilExactlyOneExists(hasText(QuickAddMarker))
        rule.onNodeWithText(QuickAddMarker).assertExists()

        request = 1
        rule.waitUntilExactlyOneExists(hasText(QuickAddMarker))
    }

    private companion object {
        /** The Quick Add sheet's own field label, so this asserts on the sheet. */
        const val QuickAddMarker = "New task"
        const val ElsewhereMarker = "Somewhere that is not Today"
    }
}
