package com.vignesh.focuslist.ui.semantics

import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.component.PlanRow
import com.vignesh.focuslist.ui.component.PlanRowGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A Plan row keeps its own name however long its value gets.
 *
 * A regression test for a defect found by looking rather than by asserting.
 * `PlanRow` put the value in `ListItem`'s trailing slot, which is measured
 * before the headline and takes what it wants, so a long value ate the row:
 * with every weekday selected the Repeat label measured 139px at 100% and
 * vanished from the screen entirely at 200%, leaving a row that showed a value
 * and had no name. Every row in the group was exposed to it.
 *
 * **Two rows, one composition, and the assertion is that they agree.** The label
 * is the same width beside a short value and beside a long one, and that
 * equality is the property. An absolute width would be a different number on
 * every density, and a ratio between label and value fails on a value that is
 * merely long, which is allowed: the value may take the whole remainder, it may
 * not take the label's share.
 *
 * **The unmerged tree is load-bearing.** `SegmentedListItem` merges its
 * descendants, so the default tree answers both lookups with the same row node.
 * The first version of this test measured the row twice, reported 1080px for
 * both, and passed against the very layout it was written to catch. D-026 says
 * to prove a probe can still fail; this one was checked against the old layout
 * and does, at both scales.
 */
@RunWith(AndroidJUnit4::class)
class PlanRowSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertTheValueDoesNotTakeTheLabelsWidth(fontScale: Float) {
        rule.setFocuslistContent(fontScale) {
            PlanRowGroup {
                PlanRow(
                    label = LABEL,
                    value = ShortValue,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    onClick = {}
                )
                PlanRow(
                    label = LABEL,
                    value = LongValue,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    onClick = {}
                )
            }
        }

        val labels = rule.onAllNodesWithText(LABEL, useUnmergedTree = true)
        labels[0].assertIsDisplayed()
        labels[1].assertIsDisplayed()

        val besideShort = labels[0].fetchSemanticsNode().size
        val besideLong = labels[1].fetchSemanticsNode().size

        assertEquals(
            "at $fontScale the label was ${besideShort.width}px beside a short value " +
                "and ${besideLong.width}px beside a long one",
            besideShort.width,
            besideLong.width
        )
    }

    @Test
    fun aLongValueDoesNotTakeTheLabelsWidth_at100() =
        assertTheValueDoesNotTakeTheLabelsWidth(FontScale100)

    @Test
    fun aLongValueDoesNotTakeTheLabelsWidth_at200() =
        assertTheValueDoesNotTakeTheLabelsWidth(FontScale200)

    private companion object {

        const val LABEL = "Repeat"

        const val ShortValue = "Weekly"

        /**
         * Longer than any real value, on purpose.
         *
         * The seven-day weekday list is what found this and it now reads
         * "Daily", so the case that broke the row no longer reaches it. A test
         * pinned to that value would have stopped testing the moment it was
         * fixed.
         */
        const val LongValue = "Mon, Tue, Wed, Thu, Fri, Sat and Sun, and then some more"
    }
}
