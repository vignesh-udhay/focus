package app.tellcat.ui.onboarding

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.tellcat.R
import app.tellcat.core.design.TellcatDimensions
import app.tellcat.core.design.TellcatSpacing
import app.tellcat.core.design.tellcatContentGutter
import app.tellcat.ui.component.WelcomeMascot
import app.tellcat.ui.theme.TellcatTheme

/**
 * The one screen shown before a fresh install enters the task lists.
 *
 * It explains only the product's core loop. Permissions stay out of this
 * screen because Tellcat already asks for them after a user sets a reminder,
 * when Android's question has a concrete reason.
 */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gutter = tellcatContentGutter()
    val horizontalPadding = gutter + TellcatSpacing.lg

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Button(
                onClick = onGetStarted,
                // The bottom bar slot is not inset for us. Without this the
                // button sits under three-button navigation.
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = TellcatSpacing.sm,
                        bottom = TellcatSpacing.md
                    )
                    .heightIn(min = TellcatDimensions.ActionHeight)
            ) {
                Text(stringResource(R.string.onboarding_get_started))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = innerPadding.calculateTopPadding() + TellcatSpacing.xl,
                bottom = innerPadding.calculateBottomPadding() + TellcatSpacing.lg
            ),
            // Centred, as Focus is: one column on a screen that does nothing
            // else, so hanging it from the top only leaves the bottom empty.
            verticalArrangement = Arrangement.spacedBy(TellcatSpacing.lg, Alignment.CenterVertically)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The head, drawn against the colour roles rather than the
                    // launcher asset's fixed hex. `WelcomeMascot.kt` has why.
                    WelcomeMascot()
                    Spacer(Modifier.height(TellcatSpacing.lg))
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(R.string.onboarding_promise),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = TellcatSpacing.xs)
                    )
                }
            }

            items(OnboardingPoints.size) { index ->
                OnboardingPoint(OnboardingPoints[index])
            }
        }
    }
}

@Composable
private fun OnboardingPoint(point: OnboardingPoint) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TellcatSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(point.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(TellcatDimensions.TouchTargetMin)
                .padding(TellcatSpacing.sm)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(point.titleRes),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(point.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TellcatSpacing.xxs)
            )
        }
    }
}

private data class OnboardingPoint(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int
)

private val OnboardingPoints = listOf(
    OnboardingPoint(
        iconRes = R.drawable.ic_add,
        titleRes = R.string.onboarding_capture_title,
        bodyRes = R.string.onboarding_capture_body
    ),
    OnboardingPoint(
        iconRes = R.drawable.ic_schedule,
        titleRes = R.string.onboarding_schedule_title,
        bodyRes = R.string.onboarding_schedule_body
    ),
    OnboardingPoint(
        iconRes = R.drawable.ic_notifications,
        titleRes = R.string.onboarding_reminder_title,
        bodyRes = R.string.onboarding_reminder_body
    )
)

@Preview(name = "Onboarding light", heightDp = 760)
@Preview(
    name = "Onboarding dark",
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun OnboardingPreview() {
    TellcatTheme(dynamicColor = false) {
        OnboardingScreen(onGetStarted = {})
    }
}
