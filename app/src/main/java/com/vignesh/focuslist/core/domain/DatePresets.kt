package com.vignesh.focuslist.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The day presets Task Details offers, from `docs/decisions.md` D-018.
 *
 * **A preset may offer what the parser refuses.** `date-parsing.md` excludes
 * `next week` because English does not agree on which week it means. That is an
 * argument about ambiguous *text*, and a button does not inherit it: a button
 * carries one defined meaning. So the two ways of setting a date are allowed
 * different vocabularies.
 *
 * Two of these are not in the parser and are not self-evident, so D-018 fixes
 * their meaning rather than leaving each reader to guess. They are pure
 * functions taking the day explicitly, like every query in `TaskQueries.kt`, so
 * the answer is deterministic and testable without a device.
 */

/**
 * The coming Saturday, for **This weekend**.
 *
 * Strictly after [today], so the preset always moves the task. On a Saturday it
 * means the Saturday a week away, not today: a button that resolves to the day
 * you are already on has done nothing, and the user would have no way to tell
 * whether it worked.
 *
 * Saturday rather than the whole weekend because a task lands on one day. The
 * earlier of the two is the one that leaves Sunday spare.
 */
fun thisWeekend(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))

/**
 * The coming Friday, for **End of week**.
 *
 * Strictly after [today], for the same reason [thisWeekend] is: a preset that
 * can resolve to today is a preset that sometimes does nothing.
 *
 * Friday rather than Sunday, because this one is offered on the due date, and a
 * deadline of "end of week" is understood as the end of the working week.
 */
fun endOfWeek(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
