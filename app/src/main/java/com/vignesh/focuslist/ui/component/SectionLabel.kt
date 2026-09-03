package com.vignesh.focuslist.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vignesh.focuslist.core.design.FocuslistSpacing

/**
 * The name of a group, above the tasks in it.
 *
 * Deliberately slight: a line of label text and nothing else. No divider, no
 * container, no count, not collapsible. It exists to explain an order the list
 * already had, not to turn a list into a dashboard.
 *
 * Sentence case, on every screen. The design draws Today's bands in capitals
 * and Upcoming's dates in sentence case; one of the two had to give, and
 * shouting a word the user is not reading is the one worth losing.
 *
 * Shared so Today and Upcoming cannot drift. Today names bands, Upcoming names
 * days, and both are the same kind of heading over the same kind of collection.
 *
 * No start inset. The label belongs to the collection beneath it, so it begins
 * where those rows begin. An inset of its own put it 12dp inboard of the rows
 * and about 66dp outboard of the row titles, which is to say aligned with
 * nothing on the screen.
 */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            top = FocuslistSpacing.lg,
            bottom = FocuslistSpacing.xs
        )
    )
}
