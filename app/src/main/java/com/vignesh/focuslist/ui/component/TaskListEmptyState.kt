package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter

/**
 * What a task list shows when it holds nothing.
 *
 * Material 3 has no empty-state component, so it is a centered column of two
 * lines. The copy is plain: an empty list is not an achievement, and nothing
 * here congratulates the user.
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
    illustration: (@Composable () -> Unit)? = null
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
        if (illustration != null) {
            illustration()
            Spacer(Modifier.height(FocuslistSpacing.lg))
        }

        Text(
            text = headline,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
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
    }
}
