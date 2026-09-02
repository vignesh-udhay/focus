package com.vignesh.focuslist.ui.semantics

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared snackbar host's accessibility contract.
 *
 * Undo is the only way back from completing or deleting a task, so the offer
 * has to reach a screen reader without being asked for. It is a *polite* live
 * region rather than an assertive one: cutting off whatever is being announced
 * to report something reversible would be rude about something that is not
 * urgent.
 *
 * The host is checked with nothing showing, which is the only way to see the
 * host's own declaration on its own. Material's `Snackbar` publishes a polite
 * region of its own once one is displayed, so with a message on screen there
 * are two nested regions and neither test could tell which was ours.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UndoSnackbarSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertHostDeclaresAPoliteLiveRegion(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            UndoSnackbarHost(remember { SnackbarHostState() })
        }

        // Nothing is showing, so the only polite region in the tree is the
        // host's own. Exactly one, and it is ours.
        rule.onAllNodes(hasLiveRegion(LiveRegionMode.Polite)).assertCountEquals(1)
    }

    @Test
    fun host_declaresAPoliteLiveRegion_at100() =
        assertHostDeclaresAPoliteLiveRegion(FontScale100)

    @Test
    fun host_declaresAPoliteLiveRegion_at200() =
        assertHostDeclaresAPoliteLiveRegion(FontScale200)

    private fun assertMessageSitsInsideThePoliteRegion(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            val hostState = remember { SnackbarHostState() }

            UndoSnackbarHost(hostState)

            LaunchedEffect(Unit) {
                hostState.showSnackbar(message = MESSAGE, actionLabel = UNDO)
            }
        }

        rule.waitUntilExactlyOneExists(hasText(MESSAGE), TIMEOUT_MILLIS)

        val regions = rule.onAllNodes(
            hasLiveRegion(LiveRegionMode.Polite) and hasAnyDescendant(hasText(MESSAGE))
        ).fetchSemanticsNodes()

        // The host's region and Material's own, nested. What matters is that
        // the message is announced from inside one rather than silently.
        assertTrue(
            "expected the message to sit inside a polite live region",
            regions.isNotEmpty()
        )
    }

    @Test
    fun message_sitsInsideThePoliteRegion_at100() =
        assertMessageSitsInsideThePoliteRegion(FontScale100)

    @Test
    fun message_sitsInsideThePoliteRegion_at200() =
        assertMessageSitsInsideThePoliteRegion(FontScale200)

    private companion object {
        const val MESSAGE = "Task completed"
        const val UNDO = "Undo"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
