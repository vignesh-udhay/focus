package app.tellcat.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.tellcat.R
import app.tellcat.TellcatApplication
import app.tellcat.core.design.ThemePreference
import app.tellcat.core.domain.SharedTask
import app.tellcat.core.domain.Task
import app.tellcat.core.domain.sharedTask
import app.tellcat.ui.theme.TellcatTheme
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * "Add to Tellcat" in other apps' share sheets. D-097.
 *
 * A see-through window over the app the user shared from, holding one sheet, so
 * adding a task never takes them out of what they were reading. Adding saves to
 * Inbox and closes; a toast confirms it, because the app underneath is not ours
 * and a snackbar has nowhere to sit.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = intent.takeIf { it.action == Intent.ACTION_SEND }?.let { send ->
            sharedTask(
                text = send.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                subject = send.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
            )
        }
        if (shared == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            val preferences by (application as TellcatApplication).preferences.state
                .collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            TellcatTheme(
                darkTheme = when (preferences.theme) {
                    ThemePreference.System -> systemDark
                    ThemePreference.Light -> false
                    ThemePreference.Dark -> true
                },
                dynamicColor = preferences.dynamicColor
            ) {
                AddSharedTaskSheet(shared = shared, onDismiss = ::finish, onAdd = ::add)
            }
        }
    }

    private fun add(shared: SharedTask) {
        val repository = (application as TellcatApplication).taskRepository
        lifecycleScope.launch {
            val added = runCatching {
                repository.insert(
                    Task(
                        id = UUID.randomUUID().toString(),
                        title = shared.title,
                        createdAt = Instant.now(),
                        notes = shared.notes,
                        checklist = shared.checklist
                    )
                )
            }.isSuccess

            // On failure the sheet stays open with the text, so nothing is lost.
            Toast.makeText(
                this@ShareActivity,
                if (added) R.string.share_added else R.string.share_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (added) finish()
        }
    }
}
