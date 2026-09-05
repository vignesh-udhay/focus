package com.vignesh.focuslist.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.ui.navigation.FocuslistNavigationBar
import com.vignesh.focuslist.ui.navigation.FocuslistRoutes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom navigation bar's accessibility contract.
 *
 * Two things have to be true for a screen reader user to navigate at all: every
 * destination must carry a readable label, and the current one must publish its
 * selected state so "Today, selected" is what gets announced rather than four
 * items that sound identical.
 *
 * More is not a destination. It is a menu, and what it opens has to be
 * reachable and labelled too, which is the part most easily lost.
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
        onOpenTopLevel: (String) -> Unit = {},
        onOpenSecondary: (String) -> Unit = {}
    ) {
        rule.setFocuslistContent(fontScale) {
            FocuslistNavigationBar(
                currentRoute = currentRoute,
                onOpenTopLevel = onOpenTopLevel,
                onOpenSecondary = onOpenSecondary
            )
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

    private fun assertCurrentDestinationIsSelected(fontScale: Float) {
        setBar(fontScale, currentRoute = FocuslistRoutes.INBOX)

        rule.onNode(hasText(INBOX) and isSelectable()).assertIsSelected()
        rule.onNode(hasText(TODAY) and isSelectable()).assertIsNotSelected()
        rule.onNode(hasText(TODAY) and isSelectable()).assertIsNotSelected()
        rule.onNode(hasText(MORE) and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun currentDestination_isSelected_at100() =
        assertCurrentDestinationIsSelected(FontScale100)

    @Test
    fun currentDestination_isSelected_at200() =
        assertCurrentDestinationIsSelected(FontScale200)

    private fun assertMoreIsSelectedOnASecondaryRoute(fontScale: Float) {
        setBar(fontScale, currentRoute = FocuslistRoutes.LOGBOOK)

        // Logbook is reached through More, so More is where the user is.
        rule.onNode(hasText(MORE) and isSelectable()).assertIsSelected()
        rule.onNode(hasText(TODAY) and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun more_isSelectedOnASecondaryRoute_at100() =
        assertMoreIsSelectedOnASecondaryRoute(FontScale100)

    @Test
    fun more_isSelectedOnASecondaryRoute_at200() =
        assertMoreIsSelectedOnASecondaryRoute(FontScale200)

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

    private fun assertMoreOpensLabelledDestinations(fontScale: Float) {
        val opened = mutableListOf<String>()
        setBar(fontScale, onOpenSecondary = { route -> opened += route })

        rule.onNode(hasText(MORE) and isSelectable()).performClick()

        SECONDARY_LABELS.forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }

        rule.onNodeWithText(LOGBOOK).performClick()

        assertEquals(listOf(FocuslistRoutes.LOGBOOK), opened)
    }

    @Test
    fun more_opensLabelledDestinations_at100() =
        assertMoreOpensLabelledDestinations(FontScale100)

    @Test
    fun more_opensLabelledDestinations_at200() =
        assertMoreOpensLabelledDestinations(FontScale200)

    private companion object {
        const val TODAY = "Today"
        const val INBOX = "Inbox"
        const val MORE = "More"
        const val UPCOMING = "Upcoming"
        const val LOGBOOK = "Logbook"

        /**
         * Three, not four. Focus left the bar when it became a sheet opened
         * from the task it is for; `focus.md` records why.
         */
        val DESTINATION_LABELS = listOf(TODAY, INBOX, MORE)
        val SECONDARY_LABELS = listOf(UPCOMING, LOGBOOK)
    }
}
