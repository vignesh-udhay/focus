package com.vignesh.focuslist.core.text

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vignesh.focuslist.core.domain.Recurrence
import com.vignesh.focuslist.core.domain.RecurrenceEnd
import com.vignesh.focuslist.core.domain.RecurrenceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The three amounts of a rule a reader can be told.
 *
 * Instrumented rather than a unit test, because this reads string resources and
 * joins a list through ICU, and neither exists on a bare JVM.
 *
 * **Almost nothing here asserts a literal.** The one thing that would make this
 * test brittle is spelling out what `ListFormatter` produces: en-US gives
 * "Mon, Wed, and Fri" and en-GB gives "Mon, Wed and Fri", both correct, and a
 * test that pinned either would fail on a device that was merely configured
 * differently. `TaskDetailsSemanticsTest` learned that from a chip assertion
 * that read a 12-hour clock. So these assert the relationships between the three
 * styles, which is the contract, rather than the words, which are ICU's business.
 */
@RunWith(AndroidJUnit4::class)
class RecurrenceTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val mondayWednesdayFriday = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.FRIDAY
    )

    private fun weekly(
        interval: Int = 1,
        days: Set<DayOfWeek> = mondayWednesdayFriday,
        end: RecurrenceEnd = RecurrenceEnd.Never
    ) = Recurrence(RecurrenceUnit.WEEKLY, interval, days, end)

    // Fixed rather than today's date, so a horizon's year appears or does not
    // by the rule under test rather than by when the suite happens to run.
    private val today: LocalDate = LocalDate.of(2026, 1, 15)

    private fun summary(rule: Recurrence, style: RecurrenceStyle) =
        recurrenceSummary(context, rule, today, style)

    // --- Compact: the shortest true thing ------------------------------------

    /**
     * Days imply a weekly rule, so saying "week" beside them spends a row's
     * width on nothing. This is the only difference between Compact and
     * Sentence.
     */
    @Test
    fun compactDropsThePeriodWhenTheDaysCarryIt() {
        assertEquals(
            weekdayList(context, mondayWednesdayFriday),
            summary(weekly(), RecurrenceStyle.Compact)
        )
    }

    @Test
    fun compactNamesThePeriodWhenThereAreNoDays() {
        assertEquals(
            context.getString(com.vignesh.focuslist.R.string.task_recurrence_weekly),
            summary(weekly(days = emptySet()), RecurrenceStyle.Compact)
        )
    }

    /** An unusual interval is not implied by anything, so it survives Compact. */
    @Test
    fun compactKeepsAnIntervalAboveOne() {
        val text = summary(weekly(interval = 2), RecurrenceStyle.Compact)

        assertTrue(text, text.contains("2"))
        assertTrue(text, text.endsWith(weekdayList(context, mondayWednesdayFriday)))
    }

    // --- Sentence: the phrase the notification reads --------------------------

    @Test
    fun sentenceKeepsThePeriodThatCompactDrops() {
        val compact = summary(weekly(), RecurrenceStyle.Compact)
        val sentence = summary(weekly(), RecurrenceStyle.Sentence)

        assertNotEquals(compact, sentence)
        assertTrue(sentence, sentence.endsWith(compact))
    }

    /** The two agree once the interval stops being implied. */
    @Test
    fun sentenceAndCompactMatchOnAnIntervalAboveOne() {
        assertEquals(
            summary(weekly(interval = 3), RecurrenceStyle.Compact),
            summary(weekly(interval = 3), RecurrenceStyle.Sentence)
        )
    }

    // --- Full: the sheet's subtitle, and the only style with a horizon --------

    private val untilAugust = weekly(end = RecurrenceEnd.OnDate(LocalDate.of(2026, 8, 17)))
    private val tenTimes = weekly(end = RecurrenceEnd.AfterOccurrences(10))

    @Test
    fun fullIsTheSentenceWithTheHorizonAddedToIt() {
        val sentence = summary(weekly(), RecurrenceStyle.Sentence)

        listOf(untilAugust, tenTimes).forEach { rule ->
            val full = summary(rule, RecurrenceStyle.Full)

            assertTrue(full, full.startsWith(sentence))
            assertTrue(full, full.length > sentence.length)
        }
    }

    /**
     * The rows say how often and not when it stops, which is board 471:6668
     * against 471:6537 and what D-027 fixes. A rule that ends and one that does
     * not read identically until the sheet asks for the whole thing.
     */
    @Test
    fun onlyFullCarriesTheHorizon() {
        listOf(RecurrenceStyle.Compact, RecurrenceStyle.Sentence).forEach { style ->
            assertEquals(
                style.name,
                summary(weekly(), style),
                summary(untilAugust, style)
            )
            assertEquals(
                style.name,
                summary(weekly(), style),
                summary(tenTimes, style)
            )
        }
    }

    @Test
    fun aSeriesThatNeverEndsAddsNothingEvenInFull() {
        assertEquals(
            summary(weekly(), RecurrenceStyle.Sentence),
            summary(weekly(), RecurrenceStyle.Full)
        )
    }

    /**
     * The horizon is built from the resources it claims to be built from.
     *
     * Assembled from the same strings rather than spelled out, so this says the
     * plural and the joiner are actually wired up without pinning the English.
     * It is the one assertion here that would catch the count clause reverting
     * to a different resource.
     */
    @Test
    fun theHorizonComesFromTheStringsItNames() {
        val sentence = summary(weekly(), RecurrenceStyle.Sentence)

        val expected = context.getString(
            com.vignesh.focuslist.R.string.task_repeat_with_end,
            sentence,
            context.resources.getQuantityString(
                com.vignesh.focuslist.R.plurals.task_repeat_for_occurrences,
                10,
                10
            )
        )

        assertEquals(expected, summary(tenTimes, RecurrenceStyle.Full))
    }

    /** The two ends are different sentences, not one with a number in it. */
    @Test
    fun aDateAndACountReadDifferently() {
        assertNotEquals(
            summary(untilAugust, RecurrenceStyle.Full),
            summary(tenTimes, RecurrenceStyle.Full)
        )
    }

    // --- sets with names of their own -----------------------------------------

    /**
     * Every day selected is a daily rule, and the text says so. `RecurrenceRuleTest`
     * proves the two produce the same dates; this asserts the app admits it.
     */
    @Test
    fun everyDaySelectedReadsAsDaily() {
        val everyDay = weekly(days = DayOfWeek.entries.toSet())

        RecurrenceStyle.entries.forEach { style ->
            assertEquals(
                style.name,
                summary(Recurrence(RecurrenceUnit.DAILY), style),
                summary(everyDay, style)
            )
        }
    }

    /** And not at a longer interval, where the two rules genuinely differ. */
    @Test
    fun everyDaySelectedFortnightlyDoesNotReadAsDaily() {
        val fortnightly = weekly(interval = 2, days = DayOfWeek.entries.toSet())

        assertNotEquals(
            summary(Recurrence(RecurrenceUnit.DAILY), RecurrenceStyle.Compact),
            summary(fortnightly, RecurrenceStyle.Compact)
        )
    }

    /**
     * The weekend and its complement have names, and which days they hold is
     * ICU's answer rather than Monday-to-Friday. Asserted against the same
     * source the code reads, so this passes on a device wherever its weekend
     * falls, and still fails if the naming stops happening.
     */
    @Test
    fun theWeekendAndTheWorkingWeekAreNamedRatherThanListed() {
        val weekend = weekendOf(context)
        val working = DayOfWeek.entries.toSet() - weekend

        listOf(weekend, working).forEach { set ->
            val named = summary(weekly(days = set), RecurrenceStyle.Compact)

            assertNotEquals(weekdayList(context, set), named)
            // A name, not a list: no separator from the joined form survives.
            assertTrue(named, !named.contains(","))
        }
    }

    @Test
    fun aSetWithNoNameIsStillListed() {
        val threeDays = mondayWednesdayFriday

        assertEquals(
            weekdayList(context, threeDays),
            summary(weekly(days = threeDays), RecurrenceStyle.Compact)
        )
    }

    /**
     * The device's weekend, worked out the way the code does.
     *
     * Duplicated rather than exposed, because a test that called the production
     * function would agree with it by construction and prove nothing about which
     * days it picked.
     */
    private fun weekendOf(context: Context): Set<DayOfWeek> {
        val calendar = android.icu.util.Calendar.getInstance(
            context.resources.configuration.locales[0]
        )
        val monday = LocalDate.of(2024, 1, 1)

        return DayOfWeek.entries.filter { day ->
            val date = monday.plusDays((day.value - 1).toLong())
            listOf(1, 23).any { hour ->
                calendar.set(date.year, date.monthValue - 1, date.dayOfMonth, hour, 0, 0)
                calendar.isWeekend
            }
        }.toSet()
    }

    // --- the weekday list -----------------------------------------------------

    /**
     * Joined by ICU, so the separator and the conjunction are the locale's and
     * not ours. Asserted by shape: every day present, in week order, and glued
     * by something rather than run together.
     */
    @Test
    fun weekdaysAreJoinedInWeekOrderWhateverOrderTheyWereGivenIn() {
        val backwards = linkedSetOf(DayOfWeek.FRIDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)
        val text = weekdayList(context, backwards)

        assertEquals(weekdayList(context, mondayWednesdayFriday), text)

        val monday = text.indexOf(DayOfWeek.MONDAY.chipLabel(context).first())
        val friday = text.lastIndexOf(DayOfWeek.FRIDAY.chipLabel(context).first())
        assertTrue(text, monday in 0 until friday)
    }

    @Test
    fun oneDayIsJustThatDay() {
        assertEquals(
            weekdayList(context, setOf(DayOfWeek.WEDNESDAY)),
            summary(weekly(days = setOf(DayOfWeek.WEDNESDAY)), RecurrenceStyle.Compact)
        )
    }
}
