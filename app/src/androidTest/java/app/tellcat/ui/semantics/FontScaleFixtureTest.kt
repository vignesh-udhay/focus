package app.tellcat.ui.semantics

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [setTellcatContent]'s scale reaches a sheet, which is a window of its own.
 * Until 2026-11-02 it did not, and every "at 200%" sheet was drawn at the
 * phone's own text size.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class FontScaleFixtureTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertASheetIsDrawnAt(fontScale: Float) {
        rule.setTellcatContent(fontScale) {
            ModalBottomSheet(onDismissRequest = {}) {
                Text("Scale ${LocalDensity.current.fontScale}")
            }
        }
        rule.waitUntilExactlyOneExists(hasText("Scale $fontScale"), 5_000L)
    }

    @Test
    fun aSheetIsDrawnAt100() = assertASheetIsDrawnAt(FontScale100)

    @Test
    fun aSheetIsDrawnAt200() = assertASheetIsDrawnAt(FontScale200)
}
