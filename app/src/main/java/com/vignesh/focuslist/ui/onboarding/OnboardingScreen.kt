package com.vignesh.focuslist.ui.onboarding

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
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.ui.component.MascotImage
import com.vignesh.focuslist.ui.component.SitHeight
import com.vignesh.focuslist.ui.component.SitWidth
import com.vignesh.focuslist.ui.component.catSittingFront
import com.vignesh.focuslist.ui.theme.FocuslistTheme

/**
 * The one screen shown before a fresh install enters the task lists.
 *
 * It explains only the product's core loop. Permissions stay out of this
 * screen because Catimo already asks for them after a user sets a reminder,
 * when Android's question has a concrete reason.
 */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gutter = focuslistContentGutter()
    val horizontalPadding = gutter + FocuslistSpacing.lg

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = FocuslistSpacing.sm,
                        bottom = FocuslistSpacing.md
                    )
                    .heightIn(min = FocuslistDimensions.ActionHeight)
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
                top = innerPadding.calculateTopPadding() + FocuslistSpacing.xl,
                bottom = innerPadding.calculateBottomPadding() + FocuslistSpacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.lg)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The mascot, not the launcher icon. `EmptyStateMascot.kt`
                    // draws the line: an in-app illustration binds to the fixed
                    // colour roles so it follows the user's palette, and the
                    // launcher takes fixed hex only because it draws outside the
                    // app's theme. This screen is inside it.
                    MascotImage(
                        width = SitWidth,
                        height = SitHeight,
                        build = ::catSittingFront
                    )
                    Spacer(Modifier.height(FocuslistSpacing.md))
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
                        modifier = Modifier.padding(top = FocuslistSpacing.xs)
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
        horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(point.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(FocuslistDimensions.TouchTargetMin)
                .padding(FocuslistSpacing.sm)
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
                modifier = Modifier.padding(top = FocuslistSpacing.xxs)
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
    FocuslistTheme(dynamicColor = false) {
        OnboardingScreen(onGetStarted = {})
    }
}
