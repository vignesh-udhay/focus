package com.vignesh.focuslist.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing

/**
 * The Completed band's header, which counts and collapses.
 *
 * The one exception to `expressive-components.md`'s rule that a section label is
 * "a label and nothing more: no divider, no background, no container, no
 * chevron, no count badge, not collapsible". `docs/decisions.md` D-012 makes
 * this band the exception and says why: a plain completed list grows through the
 * day and pushes live work down the screen, and the count keeps the day's
 * progress visible without spending rows on it.
 *
 * The exception is deliberately narrow. Only this band counts, only this band
 * collapses, and the other three still use the shared `SectionLabel`.
 *
 * Collapsed by default, which is the state the day starts in anyway.
 */
@Composable
fun CompletedDisclosure(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(
        if (expanded) R.string.today_completed_collapse else R.string.today_completed_expand
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The whole header is the target, not the chevron. A 24dp icon is a
            // small thing to ask someone to hit, and the words beside it are
            // part of the same control.
            .clickable(onClick = onToggle)
            // Announced as an action with a name, rather than as a row that
            // happens to respond. Without this the disclosure is a gesture a
            // screen reader user has to discover.
            .semantics { onClick(label = label, action = null) }
            .padding(
                top = FocuslistSpacing.lg,
                bottom = FocuslistSpacing.md
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.today_section_completed_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Icon(
            painter = painterResource(
                if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            ),
            // The row already carries the action and its label, so the icon is
            // decoration as far as a screen reader is concerned. Describing it
            // too would announce the control twice.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
