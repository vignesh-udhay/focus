package com.vignesh.focuslist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vignesh.focuslist.ui.navigation.FocuslistNavHost
import com.vignesh.focuslist.ui.theme.FocuslistTheme

class MainActivity : ComponentActivity() {

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
        enableEdgeToEdge()
        setContent {
            FocuslistTheme {
                FocuslistNavHost()
            }
        }
    }
}
