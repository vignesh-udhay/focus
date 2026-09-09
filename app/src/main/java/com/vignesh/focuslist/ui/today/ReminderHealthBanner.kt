package com.vignesh.focuslist.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.domain.HealthCheck
import com.vignesh.focuslist.core.domain.ReminderHealthState
import com.vignesh.focuslist.core.notification.displayManufacturer
import com.vignesh.focuslist.ui.component.durationLabel
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.Duration

/**
 * What Today says when a reminder cannot be relied on.
 *
 * `docs/decisions.md` D-040. Two states draw it and three do not: it appears for
 * [ReminderHealthState.ActionNeeded], which is a permission the app was refused,
 * and for [ReminderHealthState.Missed], which is a reminder that actually went
 * wrong. `WorthChecking` is deliberately absent, because it is inferred from
 * `Build.MANUFACTURER` and would put a permanent, unclearable notice on the
 * default screen of every phone from four vendors. `Checking` and `Ready` say
 * nothing worth interrupting a day for.
 *
 * A label, a sentence and one trailing action, and no more than that. The screen behind it
 * holds the body copy, the three checks and the button that fixes the problem;
 * repeating any of that here would be the health screen growing a second copy of
 * itself on top of the user's work. Both strings it draws are the health
 * screen's own, so the same fact is never worded two ways.
 *
 * Stateless, and it renders nothing at all for the states it does not cover, so
 * the caller can hand it whatever the view model currently holds.
 */
@Composable
fun ReminderHealthBanner(
    state: ReminderHealthState?,
    onOpen: () -> Unit,
    onDismissMissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Two exits rather than one, because `bannerLabel` is an extension on a
    // nullable receiver and returning through it tells the compiler nothing
    // about `state`. Naming the non-null value is what lets the title read it.
    val shown = state ?: return
    val label = shown.bannerLabel() ?: return

    Surface(
        // The error container, matching the health screen's own headline for
        // these two states. D-021 reserves this pairing for what the app knows
        // rather than for what it suspects, which is the same line D-040 draws
        // to decide which states appear here at all.
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        // The corner the bands and the Focus now card round to, so the banner
        // reads as another thing on the day rather than as system chrome.
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                start = FocuslistSpacing.md,
                end = if (shown is ReminderHealthState.Missed) {
                    FocuslistSpacing.xs
                } else {
                    FocuslistSpacing.md
                },
                top = FocuslistSpacing.md,
                bottom = FocuslistSpacing.md
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clickable(
                        role = Role.Button,
                        // Announced rather than drawn. The chevron says this
                        // goes somewhere to a user who can see it, and this
                        // says where. A missed banner keeps this target beside
                        // its independent dismiss button.
                        onClickLabel = stringResource(R.string.today_reminder_health_open),
                        onClick = onOpen
                    )
                    // The label and sentence are one route to the detail
                    // screen. The dismiss button remains its own semantics
                    // node, so neither action obscures the other.
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LabelToTitleGap)
                ) {
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        text = bannerTitle(shown),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (shown !is ReminderHealthState.Missed) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_forward),
                        // The sentence beside it already says what this opens,
                        // and the click label says where it goes.
                        contentDescription = null,
                        modifier = Modifier.size(ChevronSize)
                    )
                }
            }

            if (shown is ReminderHealthState.Missed) {
                IconButton(onClick = onDismissMissed) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(
                            R.string.today_reminder_health_dismiss
                        )
                    )
                }
            }
        }
    }
}

/**
 * Whether [ReminderHealthBanner] would draw anything for this state.
 *
 * Exists for `LazyColumn`, which cannot be told this by the banner returning
 * early: an `item` that emits nothing is still an item, and the column's
 * `spacedBy` would put a gap above the first band for a banner that is not
 * there. Callers that place the banner in an ordinary layout do not need it.
 */
fun ReminderHealthState?.hasReminderHealthBanner(): Boolean = bannerLabel() != null

/**
 * The state's label, or null where the state does not earn a banner.
 *
 * Null is the whole of the "should this be drawn" question, so there is one
 * place to change if a state's answer ever moves. The two labels are the health
 * screen's, unchanged.
 */
private fun ReminderHealthState?.bannerLabel(): Int? = when (this) {
    is ReminderHealthState.ActionNeeded -> R.string.reminder_health_action_label
    is ReminderHealthState.Missed -> R.string.reminder_health_missed_label
    is ReminderHealthState.WorthChecking -> null
    ReminderHealthState.Checking, ReminderHealthState.Ready, null -> null
}

/**
 * The one sentence, which names the cause rather than the category.
 *
 * The same strings and the same branching the health screen's own headline uses.
 * Three causes fail differently, and a banner that said "reminders may not work"
 * for all of them would send the user to a screen that then says something else.
 *
 * `BackgroundWork` cannot currently reach here, because `backgroundWorkState`
 * only ever returns `Ok` or `Warning` and a `Warning` is not drawn. It is
 * handled anyway, and named after the manufacturer as the health screen names
 * it, so that a future measured restriction arrives with a sentence already
 * written.
 */
@Composable
private fun bannerTitle(state: ReminderHealthState): String = when (state) {
    is ReminderHealthState.ActionNeeded -> when (state.cause) {
        HealthCheck.Notifications ->
            stringResource(R.string.reminder_health_no_notifications_title)

        HealthCheck.ExactAlarms ->
            stringResource(R.string.reminder_health_no_exact_title)

        HealthCheck.BackgroundWork ->
            stringResource(R.string.reminder_restriction_action_title, displayManufacturer())
    }

    is ReminderHealthState.Missed ->
        if (state.delivery.lateness >= Duration.ofMinutes(1)) {
            stringResource(
                R.string.reminder_health_missed_title,
                durationLabel(state.delivery.lateness.toMinutes().toInt()).spoken
            )
        } else {
            // On time, and nobody was told. Nothing was late, something was
            // lost, and the health screen distinguishes the two the same way.
            stringResource(R.string.reminder_health_missed_silent_title)
        }

    // Unreachable: bannerLabel returned null for these and the caller has
    // already returned.
    else -> ""
}

/** The label sits closer to its sentence than the sentence does to anything. */
private val LabelToTitleGap = 2.dp

/** Matched to the chevron on `PlanRow` and on the Settings rows. */
private val ChevronSize = 20.dp

@Preview(name = "Reminder banner, blocked notifications")
@Composable
private fun ReminderHealthBannerActionPreview() {
    FocuslistTheme(dynamicColor = false) {
        ReminderHealthBanner(
            state = ReminderHealthState.ActionNeeded(HealthCheck.Notifications),
            onOpen = {},
            modifier = Modifier.padding(FocuslistSpacing.md)
        )
    }
}

@Preview(name = "Reminder banner, late alarms")
@Composable
private fun ReminderHealthBannerExactPreview() {
    FocuslistTheme(dynamicColor = false) {
        ReminderHealthBanner(
            state = ReminderHealthState.ActionNeeded(HealthCheck.ExactAlarms),
            onOpen = {},
            modifier = Modifier.padding(FocuslistSpacing.md)
        )
    }
}
