package com.vignesh.focuslist.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SplitTrailingDateTest {

    /** A Monday, matching [DateParserTest] so the weekday cases read the same. */
    private val today: LocalDate = LocalDate.of(2026, 8, 31)

    private fun split(text: String) = splitTrailingDate(text, today)

    private fun assertKeptWhole(text: String) {
        val result = split(text)
        assertEquals("title for \"$text\"", text.trim(), result.title)
        assertNull("date for \"$text\"", result.date)
        assertNull("dateStart for \"$text\"", result.dateStart)
    }

    // Reading a day off the end

    @Test
    fun `a trailing tomorrow becomes the date`() {
        val result = split("Call the plumber tomorrow")

        assertEquals("Call the plumber", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), result.date)
    }

    @Test
    fun `a trailing weekday becomes the date`() {
        val result = split("Book the dentist friday")

        assertEquals("Book the dentist", result.title)
        assertEquals(LocalDate.of(2026, 9, 4), result.date)
    }

    @Test
    fun `the longest trailing match wins`() {
        val result = split("Prepare the talk in 2 weeks")

        assertEquals("Prepare the talk", result.title)
        assertEquals(LocalDate.of(2026, 9, 14), result.date)
    }

    @Test
    fun `a trailing month and day becomes the date`() {
        val result = split("Renew the licence 4 september")

        assertEquals("Renew the licence", result.title)
        assertEquals(LocalDate.of(2026, 9, 4), result.date)
    }

    @Test
    fun `the day is matched whatever its case`() {
        val result = split("Send the invoice Tomorrow")

        assertEquals("Send the invoice", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), result.date)
    }

    // Leaving the title alone

    @Test
    fun `a title with no day keeps every word`() {
        assertKeptWhole("Review the quarterly budget")
    }

    @Test
    fun `a day in the middle is not extracted`() {
        assertKeptWhole("Review Tuesday's notes with Priya")
    }

    @Test
    fun `a time of day is refused rather than dropped`() {
        // parseDate will not answer "tomorrow at 3pm", and a task has no hour
        // to store, so the words stay in the title.
        assertKeptWhole("Write the report tomorrow at 3pm")
    }

    @Test
    fun `a title that is only a day stays a title`() {
        assertKeptWhole("tomorrow")
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val result = split("  Call the plumber tomorrow  ")

        assertEquals("Call the plumber", result.title)
        assertEquals(LocalDate.of(2026, 9, 1), result.date)
    }

    // Where the day starts, for marking it in the field

    @Test
    fun `dateStart points at the day in the text handed in`() {
        val text = "Call the plumber tomorrow"
        val result = split(text)

        assertEquals(text.indexOf("tomorrow"), result.dateStart)
        assertEquals("tomorrow", text.substring(result.dateStart!!))
    }

    @Test
    fun `dateStart covers the whole of a multi word day`() {
        val text = "Prepare the talk in 2 weeks"
        val result = split(text)

        assertEquals(text.indexOf("in 2 weeks"), result.dateStart)
        assertEquals("in 2 weeks", text.substring(result.dateStart!!))
    }

    @Test
    fun `dateStart indexes the original text rather than the trimmed title`() {
        // Leading whitespace shifts every offset; the title is trimmed and the
        // index is not, so marking the field cannot drift.
        val text = "   Call the plumber tomorrow"
        val result = split(text)

        assertEquals("Call the plumber", result.title)
        assertEquals("tomorrow", text.substring(result.dateStart!!))
    }

    @Test
    fun `blank text yields a blank title and no date`() {
        val result = split("   ")

        assertEquals("", result.title)
        assertNull(result.date)
    }
}
