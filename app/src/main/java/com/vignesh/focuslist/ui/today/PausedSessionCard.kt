package com.vignesh.focuslist.ui.today

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.Task
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Instant
import java.time.LocalDate

/**
 * The paused session card, from `docs/decisions.md` D-048.
 *
 * **This was the Focus now card, and it asked a bigger question than it could
 * answer.** D-012 built it to answer "what should I do now?" for three reasons,
 * D-035 cut one for restating the band label below it, and D-048 cut the second
 * for the same fault once the promotion stopped hiding it. What is left does not
 * answer that question at all. It says one thing: you were in the middle of
 * something.
 *
 * That is the single fact on Today no row can produce. The bands answer "what
 * now" by ordering, past, present, future, with the answer at the top of the
 * screen; no band can say that work was started on a task and has fifteen minutes
 * left in it.
 *
 * **Three things, and the task is not one of them.** A label saying what is
 * paused and how much is left, the title so the user knows which task, and Resume.
 * No checkbox, no reason line, no tap target on the body. All three existed
 * because D-012 lifted the task out of its band and left the row's affordances
 * with nowhere to live; D-048 leaves the task in its band, so the row carries them
 * again and this carries the one thing the row cannot.
 *
 * **The remaining time is in the label rather than on a line of its own.** It is
 * the number the decision turns on: a forty-five minute task paused two thirds of
 * the way through has fifteen minutes left, and showing the estimate would
 * overstate the work threefold. It costs no height because the label line already
 * existed to hold "Focus now", which is the phrase that vacated it.
 *
 * It sits on `primaryContainer`, the one tinted surface on Today. That is what
 * makes it read as the app saying something rather than as another row.
 */
@Composable
fun PausedSessionCard(
    task: Task,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    // What is left of the paused session. Null for a session with no estimate
    // behind it, which is D-013's open-ended session paused: there is no
    // remaining time to name, only the fact of the pause.
    remainingMinutes: Long? = null
) {
    Card(
        // **Not clickable, and the card it replaced was.** D-012 made the card open
        // its task because the promotion had made that task unreachable from Today
        // altogether: not in a band, not in Upcoming, which holds later days, not
        // in Inbox, which holds undated work. D-048 puts the task back in its band,
        // so the row opens it and this does not need to. One card, one action.
        //
        // The same corner the bands below round to. `CardDefaults.shape` is
        // `medium` and a band's outer corners are `large`, so an unnamed shape here
        // sat at 12dp directly above rows at 16dp on the same left edge. Named as
        // the token rather than a number so the two move together.
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(FocuslistSpacing.md)) {
            Text(
                // The state and what is left of it, in one line. Sentence case,
                // not letterspaced capitals: `expressive-components.md` makes that
                // the rule for every label in the app.
                text = if (remainingMinutes == null) {
                    stringResource(R.string.today_focus_now_paused)
                } else {
                    stringResource(
                        R.string.today_focus_now_paused_remaining,
                        // Rounded up, for the same reason Focus's own status line
                        // is: nought minutes left on a clock that has not run out
                        // is a lie the floor would tell through the whole of the
                        // last minute.
                        (remainingMinutes + 1L).toInt()
                    )
                },
                style = MaterialTheme.typography.labelSmallEmphasized
            )

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                // The card is about one task, so the task is the heading a screen
                // reader should land on.
                modifier = Modifier
                    .padding(top = FocuslistSpacing.xs)
                    .semantics { heading() }
            )

            Button(
                onClick = onResume,
                modifier = Modifier.padding(top = FocuslistSpacing.sm)
            ) {
                // Always Resume. The card has one state now, so the label no
                // longer branches: there is no path here that lands the sheet on
                // Ready, because the only thing this card speaks for is work the
                // user already started.
                Text(text = stringResource(R.string.today_focus_now_resume))
            }
        }
    }
}

private val SampleTimestamp: Instant = Instant.parse("2026-01-01T09:00:00Z")

private val SampleTask = Task(
    id = "sample",
    title = "Refine landing page hero",
    createdAt = SampleTimestamp,
    scheduledDate = LocalDate.of(2026, 1, 1),
    estimatedDurationMinutes = 30
)

@Preview(name = "Paused session", showBackground = true)
@Preview(
    name = "Paused session dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PausedSessionPreview() {
    FocuslistTheme(dynamicColor = false) {
        PausedSessionCard(
            task = SampleTask,
            onResume = {},
            modifier = Modifier.padding(FocuslistSpacing.md),
            remainingMinutes = 17
        )
    }
}

/**
 * A session with no estimate behind it, which is D-013's open-ended session paused.
 * The label says only that it is paused, because there is no remaining time to
 * name.
 */
@Preview(name = "Paused session open ended", showBackground = true)
@Composable
private fun PausedSessionOpenEndedPreview() {
    FocuslistTheme(dynamicColor = false) {
        PausedSessionCard(
            task = SampleTask.copy(
                title = "Read through the support inbox",
                estimatedDurationMinutes = null
            ),
            onResume = {},
            modifier = Modifier.padding(FocuslistSpacing.md)
        )
    }
}

/**
 * A long title, and the same card at twice the font scale.
 *
 * The title is the only thing on this card that can wrap, so it is the only thing
 * worth previewing long. It used to share this preview with the longest reason
 * line the card could draw, which D-048 removed.
 */
@Preview(name = "Paused session long title", showBackground = true)
@Preview(name = "Paused session long title large font", showBackground = true, fontScale = 2f)
@Composable
private fun PausedSessionLongTitlePreview() {
    FocuslistTheme(dynamicColor = false) {
        PausedSessionCard(
            task = SampleTask.copy(
                title = "Prepare launch checklist for the release, including the " +
                    "changelog and the store screenshots",
                estimatedDurationMinutes = 45
            ),
            onResume = {},
            modifier = Modifier.padding(FocuslistSpacing.md),
            remainingMinutes = 32
        )
    }
}
