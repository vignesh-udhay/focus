package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter

/**
 * The two things an empty state can be explaining.
 *
 * [Neutral] is a collection that holds nothing. [Error] is a read that failed,
 * which is a different claim: the collection may be full and the app simply
 * could not see it. Only [Error] takes an action, because only [Error] is
 * something the user can retry.
 */
internal enum class EmptyStateTone { Neutral, Error }

/**
 * What a task list shows when it holds nothing.
 *
 * Material 3 has no empty-state component, so it is a centered column of two
 * lines. The copy is plain: an empty list is not an achievement, and nothing
 * here congratulates the user.
 *
 * [tone] separates a collection that is empty from a read that failed. On
 * [EmptyStateTone.Error] the headline takes the error role, which is the whole
 * of the colour signal: the mascot draws in the fixed primary tones like every
 * other pose, so without the tint the screen would look like an ordinary empty
 * list that happens to be worded oddly.
 *
 * [action] is the retry, and it belongs to the error tone alone. It sits below
 * the supporting line rather than inside it, because an empty state is a
 * statement and a button is not part of a sentence.
 *
 * [illustration] is the screen's mascot, and only some screens have one. It
 * sits above the headline and is not a decoration of the absence: each mascot
 * says why its own screen is empty, which is the same thing the headline says.
 * A screen without one is not a lesser empty state, it is a screen whose mascot
 * has not been drawn yet.
 *
 * Optically centred rather than geometrically. Text sitting on the exact
 * middle of a screen reads as slightly low, so the column is lifted by a
 * bottom bias.
 *
 * On a wide window it keeps to the same content column the task lists use, so
 * two lines of text do not end up stranded across a metre of screen.
 */
@Composable
internal fun TaskListEmptyState(
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
    tone: EmptyStateTone = EmptyStateTone.Neutral,
    illustration: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = focuslistContentGutter())
            .padding(horizontal = FocuslistSpacing.lg)
            .padding(bottom = FocuslistSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateBody(
            headline = headline,
            supporting = supporting,
            tone = tone,
            illustration = illustration,
            action = action
        )
    }
}

/**
 * The statement itself: mascot, headline, supporting line, and any action.
 *
 * Lifted out of [TaskListEmptyState] because the finished-day state draws the
 * same thing at the head of a list rather than in the middle of a screen, and
 * two copies of this column would be two things to keep in step.
 */
@Composable
private fun ColumnScope.EmptyStateBody(
    headline: String,
    supporting: String,
    tone: EmptyStateTone,
    illustration: (@Composable () -> Unit)?,
    action: (@Composable () -> Unit)?
) {
    if (illustration != null) {
        illustration()
        Spacer(Modifier.height(FocuslistSpacing.lg))
    }

        Text(
            text = headline,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = when (tone) {
                EmptyStateTone.Neutral -> MaterialTheme.colorScheme.onSurface
                EmptyStateTone.Error -> MaterialTheme.colorScheme.error
            },
            textAlign = TextAlign.Center,
            // The same convention the app bar titles follow. On an empty list
            // this line is the only thing that says what the screen holds, so
            // it is what a screen reader should be able to jump to rather than
            // meeting two unmarked strings.
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = FocuslistSpacing.xs)
        )

    if (action != null) {
        Spacer(Modifier.height(FocuslistSpacing.lg))
        action()
    }
}

/**
 * The finished-day statement, sized to its content so it can head a list.
 *
 * Today draws this rather than [TaskListEmptyState] when the day had work and
 * all of it is complete, because that day is not empty: its Completed
 * disclosure sits directly beneath this and stays the way a task finished today
 * is reopened. D-033 records why the finished-day state exists and why it heads
 * the list instead of replacing it.
 *
 * No gutter of its own. It is an item in a list whose content padding has
 * already inset it, where [TaskListEmptyState] is the whole screen and has to
 * find the content column for itself.
 */
@Composable
internal fun TaskListDoneHeader(
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = FocuslistSpacing.md, bottom = FocuslistSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStateBody(
            headline = headline,
            supporting = supporting,
            tone = EmptyStateTone.Neutral,
            illustration = illustration,
            action = null
        )
    }
}

/**
 * What a task list shows when it could not be read.
 *
 * The same component in its error tone, so a failed read is not a differently
 * shaped screen from an empty one. It is the only state carrying an action,
 * and [onRetry] starts a fresh read rather than resuming the dead one.
 *
 * The copy never mentions a connection. Focuslist has no account, no sync and
 * no backend, so every read is local, and sending someone to check their
 * connection points them at something that was never involved.
 *
 * The mascot asks rather than rests. It is the one pose in the set that is not
 * about an empty list, which is why it carries a prop where the others carry
 * only a posture.
 */
@Composable
internal fun TaskListErrorState(
    headline: String,
    supporting: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    TaskListEmptyState(
        headline = headline,
        supporting = supporting,
        modifier = modifier,
        tone = EmptyStateTone.Error,
        illustration = { ErrorMascot() },
        action = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.error_try_again))
            }
        }
    )
}
