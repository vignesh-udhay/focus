package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.navigation.FocuslistNavigationBar
import com.vignesh.focuslist.ui.navigation.FocuslistOverflowMenu
import com.vignesh.focuslist.ui.navigation.FocuslistRoutes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The navigation chrome's accessibility contract.
 *
 * Two things have to be true for a screen reader user to navigate at all: every
 * destination must carry a readable label, and the current one must publish its
 * selected state, so "Inbox, selected" is announced rather than three items
 * that sound identical.
 *
 * The bar holds the three primary destinations `PRODUCT.md` names. Everything
 * else is behind the app-bar overflow, which is a menu rather than a place, and
 * what it opens has to be reachable and labelled too. That is the part most
 * easily lost.
 *
 * **These tests name the three destinations as a requirement, not as a record
 * of what the bar happens to hold.** The previous version asserted whatever was
 * there, so when Upcoming sat behind More instead of in the bar, in
 * contradiction to `PRODUCT.md`, nothing failed and the gap survived three
 * commits. A test that only describes the code cannot notice the code is wrong.
 *
 * Phone layouts only, per this phase's scope. The rail is not exercised here.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setBar(
        fontScale: Float,
        currentRoute: String = FocuslistRoutes.TODAY,
        onOpenTopLevel: (String) -> Unit = {}
    ) {
        rule.setFocuslistContent(fontScale) {
            FocuslistNavigationBar(
                currentRoute = currentRoute,
                onOpenTopLevel = onOpenTopLevel
            )
        }
    }

    private fun setOverflow(fontScale: Float, onOpen: (String) -> Unit = {}) {
        rule.setFocuslistContent(fontScale) {
            FocuslistOverflowMenu(onOpen = onOpen)
        }
    }

    private fun assertEveryDestinationIsLabelled(fontScale: Float) {
        setBar(fontScale)

        DESTINATION_LABELS.forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun everyDestination_isLabelled_at100() = assertEveryDestinationIsLabelled(FontScale100)

    @Test
    fun everyDestination_isLabelled_at200() = assertEveryDestinationIsLabelled(FontScale200)

    /**
     * The bar holds those three and nothing else.
     *
     * The half the old suite was missing. Labelling what is present cannot
     * catch a destination that should be present and is not, nor a fourth that
     * should not be there at all, which is what More was.
     */
    private fun assertTheBarHoldsExactlyTheThree(fontScale: Float) {
        setBar(fontScale)

        assertEquals(
            DESTINATION_LABELS.size,
            rule.onAllNodes(isSelectable()).fetchSemanticsNodes().size
        )
    }

    @Test
    fun theBar_holdsExactlyTheThree_at100() = assertTheBarHoldsExactlyTheThree(FontScale100)

    @Test
    fun theBar_holdsExactlyTheThree_at200() = assertTheBarHoldsExactlyTheThree(FontScale200)

    private fun assertCurrentDestinationIsSelected(fontScale: Float) {
        setBar(fontScale, currentRoute = FocuslistRoutes.INBOX)

        rule.onNode(hasText(INBOX) and isSelectable()).assertIsSelected()
        rule.onNode(hasText(TODAY) and isSelectable()).assertIsNotSelected()
        rule.onNode(hasText(UPCOMING) and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun currentDestination_isSelected_at100() =
        assertCurrentDestinationIsSelected(FontScale100)

    @Test
    fun currentDestination_isSelected_at200() =
        assertCurrentDestinationIsSelected(FontScale200)

    /**
     * On an overflow destination, nothing in the bar claims to be current.
     *
     * Logbook and Reminder health are rooms rather than places, and they are
     * why those screens hide the bar entirely. If one ever shows it, this says
     * what the user would see: three items, none of them where they are.
     */
    private fun assertNothingIsSelectedOnAnOverflowRoute(fontScale: Float) {
        setBar(fontScale, currentRoute = FocuslistRoutes.LOGBOOK)

        DESTINATION_LABELS.forEach { label ->
            rule.onNode(hasText(label) and isSelectable()).assertIsNotSelected()
        }
    }

    @Test
    fun nothing_isSelectedOnAnOverflowRoute_at100() =
        assertNothingIsSelectedOnAnOverflowRoute(FontScale100)

    @Test
    fun nothing_isSelectedOnAnOverflowRoute_at200() =
        assertNothingIsSelectedOnAnOverflowRoute(FontScale200)

    private fun assertTappingADestinationOpensIt(fontScale: Float) {
        val opened = mutableListOf<String>()
        setBar(fontScale, onOpenTopLevel = { route -> opened += route })

        rule.onNode(hasText(INBOX) and isSelectable()).performClick()

        assertEquals(listOf(FocuslistRoutes.INBOX), opened)
    }

    @Test
    fun tappingADestination_opensIt_at100() = assertTappingADestinationOpensIt(FontScale100)

    @Test
    fun tappingADestination_opensIt_at200() = assertTappingADestinationOpensIt(FontScale200)

    /** The overflow button is reachable without sight of the three dots. */
    private fun assertTheOverflowIsLabelled(fontScale: Float) {
        setOverflow(fontScale)

        rule.onNodeWithContentDescription(MORE).assertIsDisplayed()
    }

    @Test
    fun theOverflow_isLabelled_at100() = assertTheOverflowIsLabelled(FontScale100)

    @Test
    fun theOverflow_isLabelled_at200() = assertTheOverflowIsLabelled(FontScale200)

    private fun assertTheOverflowOpensLabelledDestinations(fontScale: Float) {
        val opened = mutableListOf<String>()
        setOverflow(fontScale, onOpen = { route -> opened += route })

        rule.onNodeWithContentDescription(MORE).performClick()

        OVERFLOW_LABELS.forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }

        rule.onNodeWithText(LOGBOOK).performClick()

        assertEquals(listOf(FocuslistRoutes.LOGBOOK), opened)
    }

    @Test
    fun theOverflow_opensLabelledDestinations_at100() =
        assertTheOverflowOpensLabelledDestinations(FontScale100)

    @Test
    fun theOverflow_opensLabelledDestinations_at200() =
        assertTheOverflowOpensLabelledDestinations(FontScale200)

    private companion object {
        const val TODAY = "Today"
        const val INBOX = "Inbox"
        const val UPCOMING = "Upcoming"
        const val MORE = "More"
        const val LOGBOOK = "Logbook"
        const val REMINDER_HEALTH = "Reminder health"

        /**
         * The three `PRODUCT.md` names, in the order the board shows them.
         *
         * Focus is not among them. It left the bar when it became a sheet
         * opened from the task it is for; `focus.md` records why.
         */
        val DESTINATION_LABELS = listOf(TODAY, INBOX, UPCOMING)

        /** Settings joins this when it exists. */
        val OVERFLOW_LABELS = listOf(LOGBOOK, REMINDER_HEALTH)
    }
}
