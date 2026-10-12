package app.tellcat

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tellcat.core.design.ThemePreference
import app.tellcat.ui.navigation.TellcatNavHost
import app.tellcat.ui.onboarding.OnboardingScreen
import app.tellcat.ui.theme.TellcatTheme
import app.tellcat.ui.widget.WidgetLaunchCommand
import app.tellcat.ui.widget.widgetLaunchCommand
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
        (application as TellcatApplication).currentDay.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetCommand.value = intent.widgetLaunchCommand()
        enableEdgeToEdge()
        setContent {
            val tellcat = application as TellcatApplication
            val preferences by tellcat.preferences.state.collectAsStateWithLifecycle()
            val onboardingComplete by tellcat.onboarding.completed.collectAsStateWithLifecycle()
            val pendingWidgetCommand by widgetCommand.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (preferences.theme) {
                ThemePreference.System -> systemDark
                ThemePreference.Light -> false
                ThemePreference.Dark -> true
            }

            // **The system bars follow the app's theme, not the phone's.** The
            // plain `enableEdgeToEdge()` above picks icon colours from the
            // system's dark mode, so Dark chosen in Settings on a light phone
            // drew dark clock and battery icons on a dark screen, and Light on
            // a dark phone the reverse. Re-applied whenever the resolved theme
            // changes. The scrims are the defaults `enableEdgeToEdge` uses.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightNavigationScrim,
                        DarkNavigationScrim
                    ) { darkTheme }
                )
                onDispose {}
            }

            TellcatTheme(
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
                        TellcatNavHost(
                            widgetCommand = pendingWidgetCommand,
                            onWidgetCommandHandled = { handled ->
                                widgetCommand.compareAndSet(handled, null)
                            }
                        )
                    } else {
                        OnboardingScreen(onGetStarted = tellcat.onboarding::complete)
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

// The scrims `enableEdgeToEdge` applies by default, which are not public.
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
