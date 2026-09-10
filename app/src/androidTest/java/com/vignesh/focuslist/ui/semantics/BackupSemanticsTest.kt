package com.vignesh.focuslist.ui.semantics

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.settings.BackupContent
import com.vignesh.focuslist.ui.settings.BackupOperation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Backup & restore says while one of its two operations runs, per D-066.
 *
 * Both buttons disable while either is in flight, and the page used to stop
 * there: two greyed controls, no word between them, and a screen reader saying
 * "disabled" with no reason attached. The running one now names itself.
 *
 * Rendered through `BackupContent` rather than `BackupScreen`, because reaching
 * this state through the screen means a real `ContentResolver`, a real document
 * picker and a real file, and none of them are part of the contract.
 */
@RunWith(AndroidJUnit4::class)
class BackupSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setBackup(fontScale: Float, working: BackupOperation?) {
        rule.setFocuslistContent(fontScale) {
            BackupContent(
                working = working,
                onExport = {},
                onRestore = {},
                onBack = {},
                snackbarHostState = remember { SnackbarHostState() }
            )
        }
    }

    /**
     * The running half says so and the other half does not.
     *
     * Both scales, because the working label is longer than the idle one on a
     * button that now also carries an indicator, and a fixed-height button at
     * 200% is where a label gets clipped out of the semantics tree.
     */
    private fun assertExportNamesItself(fontScale: Float) {
        setBackup(fontScale, BackupOperation.Export)

        rule.onNodeWithText("Exporting…").assertIsDisplayed()
        rule.onNodeWithText("Export backup").assertDoesNotExist()

        // The other button is disabled and unchanged, which is the half that
        // says the wait belongs to the export rather than to this.
        rule.onNodeWithText("Restore from file").assertIsNotEnabled()
        rule.onNodeWithText("Restoring…").assertDoesNotExist()
    }

    @Test
    fun export_namesItself_at100() = assertExportNamesItself(FontScale100)

    @Test
    fun export_namesItself_at200() = assertExportNamesItself(FontScale200)

    private fun assertRestoreNamesItself(fontScale: Float) {
        setBackup(fontScale, BackupOperation.Restore)

        rule.onNodeWithText("Restoring…").assertIsDisplayed()
        rule.onNodeWithText("Restore from file").assertDoesNotExist()

        rule.onNodeWithText("Export backup").assertIsNotEnabled()
        rule.onNodeWithText("Exporting…").assertDoesNotExist()
    }

    @Test
    fun restore_namesItself_at100() = assertRestoreNamesItself(FontScale100)

    @Test
    fun restore_namesItself_at200() = assertRestoreNamesItself(FontScale200)

    /**
     * Idle is both buttons offered and neither claiming to be working.
     *
     * The half that would rot: a page permanently stuck on "Exporting…" would
     * pass every assertion above.
     */
    @Test
    fun idle_offersBoth() {
        setBackup(FontScale100, working = null)

        rule.onNodeWithText("Export backup").assertIsEnabled()
        rule.onNodeWithText("Restore from file").assertIsEnabled()
        rule.onNodeWithText("Exporting…").assertDoesNotExist()
        rule.onNodeWithText("Restoring…").assertDoesNotExist()
    }
}
