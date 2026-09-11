package com.vignesh.focuslist.core.domain

/**
 * The number words a transcript writes where a typed title has digits, D-070.
 *
 * One table for every branch that reads an amount: hours ("six pm"), offsets
 * ("in two hours"), and relative days ("in three days"). Keeping it in one
 * place is what keeps the vocabulary from diverging by branch, which was
 * D-069's argument against number words and is D-070's argument for them.
 *
 * "a" and "an" are one, so "in an hour" is an hour from now.
 *
 * Deliberately not a number parser. The words below cover what people say
 * into a task field; "twenty-seven" and "one hundred" are not among them, and
 * a value the table does not name is refused rather than computed. The day
 * the table starts growing case by case is the day D-070 says it should be
 * replaced.
 */
internal fun spokenNumber(word: String): Long? = SpokenNumbers[word]

private val SpokenNumbers: Map<String, Long> = mapOf(
    "a" to 1L,
    "an" to 1L,
    "one" to 1L,
    "two" to 2L,
    "three" to 3L,
    "four" to 4L,
    "five" to 5L,
    "six" to 6L,
    "seven" to 7L,
    "eight" to 8L,
    "nine" to 9L,
    "ten" to 10L,
    "eleven" to 11L,
    "twelve" to 12L,
    "fifteen" to 15L,
    "twenty" to 20L,
    "thirty" to 30L,
    "forty-five" to 45L
)
