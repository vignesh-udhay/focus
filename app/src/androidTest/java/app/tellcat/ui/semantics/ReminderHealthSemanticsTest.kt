package app.tellcat.ui.semantics

import androidx.compose.ui.test.assertIsDisplayed
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tellcat.core.domain.CheckState
import app.tellcat.core.domain.DeviceRestriction
import app.tellcat.core.notification.ReminderHealthChecks
import app.tellcat.data.local.ReminderHealthAcknowledgements
import app.tellcat.data.local.ReminderDeliveryDao
import app.tellcat.data.local.ReminderDeliveryEntity
import app.tellcat.data.repository.ReminderDeliveryRepository
import app.tellcat.ui.health.ReminderHealthScreen
import app.tellcat.ui.health.ReminderHealthViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Reminder health says out loud.
 *
 * **The status mark is drawn and not spoken, and until the Phase 5 pass it was
 * both.** Each check row carries a glyph beside its words: a tick when the check
 * passes, a question mark when the app is guessing, an exclamation when something
 * is actually blocked. `reminder-health.md` and D-021 are explicit that the mark
 * is a second channel for someone scanning the screen, on top of words that
 * already differ: "Notifications, Not allowed" says the whole thing by itself.
 *
 * The tick was hidden from the start, because an `Icon` takes a
 * `contentDescription` and it was given null. The other two are `Text`, so they
 * went into the accessibility tree as a bare "!" and a bare "?" and a screen
 * reader read them out after the row it was annotating.
 *
 * This is the assertion that would have caught it. Nothing else does: every other
 * check on this screen passes with the mark announced, because the mark adds a
 * character rather than changing one.
 */
@RunWith(AndroidJUnit4::class)
class ReminderHealthSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    /** No deliveries, so the state is decided by the checks alone. */
    private class EmptyDeliveryDao : ReminderDeliveryDao {
        private val rows = MutableStateFlow(emptyList<ReminderDeliveryEntity>())
        override fun observeDeliveries(limit: Int): Flow<List<ReminderDeliveryEntity>> = rows
        override suspend fun insert(delivery: ReminderDeliveryEntity) = Unit
        override suspend fun trimTo(keep: Int) = Unit
    }

    private class FixedChecks(
        private val notifications: CheckState,
        private val exactAlarms: CheckState = CheckState.Ok,
        private val restriction: DeviceRestriction? = null,
        private val alarmStyle: CheckState = CheckState.Ok
    ) : ReminderHealthChecks {
        override fun notifications() = notifications
        override fun exactAlarms() = exactAlarms
        override fun restriction() = restriction
        override fun alarmStyle() = alarmStyle
    }

    private fun show(
        checks: ReminderHealthChecks,
        fontScale: Float = FontScale100,
        hasAlarms: Boolean = false
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = ReminderHealthViewModel(
            deliveries = ReminderDeliveryRepository(EmptyDeliveryDao()),
            checks = checks,
            acknowledgements = ReminderHealthAcknowledgements(context),
            alarmStyleReminders = flowOf(hasAlarms)
        )

        rule.setTellcatContent(fontScale) {
            ReminderHealthScreen(viewModel = viewModel, onBack = {})
        }
    }

    private fun assertTheMarkIsNotSpoken(fontScale: Float) {
        show(FixedChecks(notifications = CheckState.Blocked), fontScale)

        // The row is there and says the whole thing in words.
        rule.onNodeWithText(NOTIFICATIONS).assertIsDisplayed()
        rule.onNodeWithText(NOT_ALLOWED).assertIsDisplayed()

        // And the glyph beside it is not a node a screen reader can land on.
        rule.onNodeWithText(BLOCKED_MARK).assertDoesNotExist()
    }

    @Test
    fun blockedMark_isNotSpoken_at100() = assertTheMarkIsNotSpoken(FontScale100)

    @Test
    fun blockedMark_isNotSpoken_at200() = assertTheMarkIsNotSpoken(FontScale200)

    /**
     * The same for the question mark, which D-021 added as the second cue for a
     * risk the app inferred rather than a refusal it was told about.
     */
    @Test
    fun warningMark_isNotSpoken() {
        show(FixedChecks(notifications = CheckState.Ok, exactAlarms = CheckState.Warning))

        rule.onNodeWithText(WARNING_MARK).assertDoesNotExist()
    }

    private companion object {
        const val NOTIFICATIONS = "Notifications"
        const val NOT_ALLOWED = "Not allowed"
        const val BLOCKED_MARK = "!"
        const val WARNING_MARK = "?"
        const val ALARM_ROW = "Alarm-style reminders"
        const val ALARM_BANNER = "Ring as a banner until answered"
        const val TIMEOUT_MILLIS = 5_000L
    }

    // --- D-102: the alarm row ------------------------------------------------

    @Test
    fun theAlarmRowIsAbsentWithoutAnAlarmStyleReminder() {
        show(FixedChecks(notifications = CheckState.Ok), hasAlarms = false)

        rule.waitForIdle()
        rule.onAllNodesWithText(ALARM_ROW).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun assertTheAlarmRowSaysHowTheyRing(fontScale: Float) {
        show(FixedChecks(notifications = CheckState.Ok, alarmStyle = CheckState.Warning), fontScale, hasAlarms = true)

        rule.waitUntilExactlyOneExists(hasText(ALARM_ROW), TIMEOUT_MILLIS)
        rule.onNodeWithText(ALARM_BANNER).assertExists()
    }

    @Test
    fun theAlarmRowSaysHowTheyRing_at100() = assertTheAlarmRowSaysHowTheyRing(FontScale100)

    @Test
    fun theAlarmRowSaysHowTheyRing_at200() = assertTheAlarmRowSaysHowTheyRing(FontScale200)
}
