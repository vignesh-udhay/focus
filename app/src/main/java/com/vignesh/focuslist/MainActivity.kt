package com.vignesh.focuslist

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.core.design.ThemePreference
import com.vignesh.focuslist.ui.navigation.FocuslistNavHost
import com.vignesh.focuslist.ui.onboarding.OnboardingScreen
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import com.vignesh.focuslist.ui.widget.WidgetLaunchCommand
import com.vignesh.focuslist.ui.widget.widgetLaunchCommand
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val widgetCommand = MutableStateFlow<WidgetLaunchCommand?>(null)

    /**
     * Re-reads the calendar day whenever the app comes back to the foreground.
     *
     * The date broadcast covers an app left open across midnight. This covers
     * the app that was backgrounded across it, where the process may have been
     * frozen while the broadcast went out.
     */
    override fun onResume() {
        super.onResume()
        (application as FocuslistApplication).currentDay.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetCommand.value = intent.widgetLaunchCommand()
        enableEdgeToEdge()
        setContent {
            val focuslist = application as FocuslistApplication
            val preferences by focuslist.preferences.state.collectAsStateWithLifecycle()
            val onboardingComplete by focuslist.onboarding.completed.collectAsStateWithLifecycle()
            val pendingWidgetCommand by widgetCommand.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (preferences.theme) {
                ThemePreference.System -> systemDark
                ThemePreference.Light -> false
                ThemePreference.Dark -> true
            }

            FocuslistTheme(
                darkTheme = darkTheme,
                dynamicColor = preferences.dynamicColor
            ) {
                // **Something opaque has to sit under the graph.** A `NavHost`
                // transition fades the outgoing screen out while the incoming
                // one fades in, so for a few frames neither covers the window
                // and whatever is behind them shows through. That was
                // `android:windowBackground`, which is light in every
                // configuration because the app declares one theme and no
                // `values-night`, so every page change flashed white in dark
                // mode.
                //
                // Painted here rather than only fixed with a night theme,
                // because Settings can force Dark on a light system and the
                // `-night` qualifier follows the system rather than
                // `ThemePreference`. This reads the resolved scheme, so it is
                // right in all three.
                //
                // `surface` rather than `background`: it is the tone every
                // screen's Scaffold already paints, so the frames mid-transition
                // match the frames either side of them.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (onboardingComplete) {
                        FocuslistNavHost(
                            widgetCommand = pendingWidgetCommand,
                            onWidgetCommandHandled = { handled ->
                                widgetCommand.compareAndSet(handled, null)
                            }
                        )
                    } else {
                        OnboardingScreen(onGetStarted = focuslist.onboarding::complete)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetCommand.value = intent.widgetLaunchCommand()
    }
}
