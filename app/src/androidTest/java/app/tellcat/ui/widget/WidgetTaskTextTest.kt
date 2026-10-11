package app.tellcat.ui.widget

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.tellcat.core.domain.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The widget row says a reminder the way the app's row does. D-075.
 *
 * Instrumented only because the text is built from resources and the locale's
 * clock, which need a real `Context`.
 */
@RunWith(AndroidJUnit4::class)
class WidgetTaskTextTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val today: LocalDate = LocalDate.of(2026, 9, 2)

    private fun task(reminderAt: LocalDateTime?) = Task(
        id = "1",
        title = "Write the report",
        createdAt = Instant.parse("2026-01-01T09:00:00Z"),
        scheduledDate = today,
        reminderAt = reminderAt
    )

    private fun metadata(reminderAt: LocalDateTime?): String? =
        widgetTaskText(context, task(reminderAt), today).metadata

    @Test
    fun aReminderOnTheTasksDayIsJustTheTime() {
        val text = metadata(today.atTime(17, 0))

        assertTrue(text!!.contains(":00"))
        assertTrue(!text.contains("Tomorrow"))
    }

    @Test
    fun aReminderOnAnotherDayNamesIt() {
        assertTrue(metadata(today.plusDays(1).atTime(17, 0))!!.startsWith("Tomorrow, "))
    }

    @Test
    fun aReminderWhoseDayIsOverIsDropped() {
        assertEquals(null, metadata(today.minusDays(1).atTime(17, 0)))
    }
}
