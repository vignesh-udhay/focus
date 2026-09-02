package com.vignesh.focuslist.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vignesh.focuslist.R

/**
 * A duration in the two forms the UI needs at once.
 *
 * [text] is what the screen draws and [spoken] is what a screen reader says.
 * They differ on purpose: "3h 20m" is compact enough to sit beside a date in a
 * pill, and it is also not a sentence. Anywhere the compact form is drawn, the
 * spoken form has to be attached as a content description, or the value is
 * simply unavailable to anyone using TalkBack.
 */
data class DurationLabel(val text: String, val spoken: String)

/**
 * Formats a whole number of minutes.
 *
 * Presentation, not domain, for the same reason [scheduledDateLabel] is: the
 * domain carries minutes, and deciding that two hundred of them read as
 * "3h 20m" is this layer's call. Shared so the Today header and anything else
 * that totals time can never word it differently.
 *
 * An exact number of hours drops the minutes rather than showing a zero, so
 * sixty minutes reads "1h" and not "1h 0m".
 */
@Composable
fun durationLabel(minutes: Int): DurationLabel {
    val hours = minutes / MinutesPerHour
    val remainder = minutes % MinutesPerHour

    val spokenHours = pluralStringResource(R.plurals.duration_spoken_hours, hours, hours)
    val spokenMinutes =
        pluralStringResource(R.plurals.duration_spoken_minutes, remainder, remainder)

    return when {
        hours == 0 -> DurationLabel(
            text = stringResource(R.string.duration_compact_minutes, remainder),
            spoken = spokenMinutes
        )

        remainder == 0 -> DurationLabel(
            text = stringResource(R.string.duration_compact_hours, hours),
            spoken = spokenHours
        )

        else -> DurationLabel(
            text = stringResource(R.string.duration_compact_hours_minutes, hours, remainder),
            spoken = stringResource(
                R.string.duration_spoken_hours_minutes,
                spokenHours,
                spokenMinutes
            )
        )
    }
}

private const val MinutesPerHour = 60
