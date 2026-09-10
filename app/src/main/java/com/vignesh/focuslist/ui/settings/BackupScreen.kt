package com.vignesh.focuslist.ui.settings

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.UndoSnackbarHost
import com.vignesh.focuslist.ui.theme.FocuslistTheme
import java.time.LocalDate

/** The Storage Access Framework wrapper around the stateless backup page. */
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JsonMimeType),
        onResult = { uri -> if (uri != null) viewModel.exportTo(uri) }
    )
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.restoreFrom(uri) }
    )

    val chooseExport = {
        // The name the system file picker offers. Cosmetic, and renamed with the
        // app: the format identifier inside the file is a different thing and did
        // not move, so a backup written under the old name still restores.
        exportLauncher.launch("Catimo-backup-${LocalDate.now()}.json")
    }
    val chooseRestore = {
        restoreLauncher.launch(arrayOf(JsonMimeType, "text/json"))
    }

    // **A finished operation says so, and it used to not.** Success landed on
    // the same state the screen starts in, so the picker closed and nothing
    // else happened. This page shows no tasks, so a restore that replaced the
    // whole database looked exactly like one that had not run.
    //
    // A snackbar rather than a dialog, because the message asks nothing of the
    // user. The error is a dialog because it needs a decision: choose another
    // file. `settings.md` specified that dialog in full and never specified
    // this, which is how the gap survived being reviewed.
    val done = state.done
    val doneMessage = done?.let { finished ->
        pluralStringResource(
            if (finished.operation == BackupOperation.Restore) R.plurals.backup_restore_done
            else R.plurals.backup_export_done,
            finished.taskCount,
            finished.taskCount
        )
    }

    LaunchedEffect(done) {
        if (done == null || doneMessage == null) return@LaunchedEffect

        snackbarHostState.showSnackbar(doneMessage)
        viewModel.consumeDone()
    }

    BackupContent(
        isWorking = state.isWorking,
        onExport = chooseExport,
        onRestore = chooseRestore,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )

    // **Choosing a file asks before it writes.** `docs/decisions.md` D-056.
    // `settings.md` argued no confirmation was needed because the tonal button
    // carried the weight, which is the right rule for an action that can be
    // undone. This one deletes every task and every delivery record in one
    // transaction and nothing puts them back, so the announcement afterwards
    // was a report of the damage rather than a check on it.
    //
    // Drawn before the error dialog because the two cannot both be present: a
    // file that failed to parse never reached a pending state.
    state.pendingRestore?.let { pending ->
        RestoreConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmRestore,
            onCancel = viewModel::cancelRestore
        )
    }

    state.error?.let { error ->
        BackupErrorDialog(
            error = error,
            onDismiss = viewModel::dismissError,
            onChooseAnother = {
                viewModel.dismissError()
                if (error == BackupOperation.Restore) chooseRestore() else chooseExport()
            }
        )
    }
}

@Composable
private fun BackupContent(
    isWorking: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        // The app's one snackbar host, which is a Material host plus a polite
        // live region. Named for where it came from rather than for what it
        // does: nothing in it is specific to undo, and a second component
        // differing only in name is how one of them ends up missing the live
        // region.
        snackbarHost = { UndoSnackbarHost(snackbarHostState) },
        topBar = {
            FocuslistTopAppBar(
                title = stringResource(R.string.backup_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.backup_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.lg),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = FocuslistSpacing.md + gutter,
                    end = FocuslistSpacing.md + gutter,
                    top = FocuslistSpacing.lg,
                    bottom = FocuslistSpacing.lg
                )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LocalFirstMinHeight)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(FocuslistSpacing.md)
                ) {
                    Text(
                        text = stringResource(R.string.backup_local_first),
                        style = MaterialTheme.typography.headlineMediumEmphasized
                    )
                }
            }

            BackupAction(
                title = stringResource(R.string.backup_export_heading),
                description = stringResource(R.string.backup_export_description)
            ) {
                Button(
                    onClick = onExport,
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FocuslistDimensions.ActionHeight)
                ) {
                    Text(stringResource(R.string.backup_export_action))
                }
            }

            BackupAction(
                title = stringResource(R.string.backup_restore_heading),
                description = stringResource(R.string.backup_restore_description)
            ) {
                FilledTonalButton(
                    onClick = onRestore,
                    enabled = !isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FocuslistDimensions.ActionHeight)
                ) {
                    Text(stringResource(R.string.backup_restore_action))
                }
            }
        }
    }
}

@Composable
private fun BackupAction(
    title: String,
    description: String,
    action: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FocuslistSpacing.md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        action()
    }
}

/**
 * The question between choosing a file and replacing the database.
 *
 * `docs/decisions.md` D-056. Two counts, because "this will replace your data"
 * is a claim the user cannot weigh and these can be weighed against each other:
 * a stale file shows fewer tasks than the phone it is about to overwrite, which
 * is the mistake this dialog exists to catch.
 *
 * It is the same standard the success snackbar is held to. An assertion the user
 * cannot check is one they cannot disagree with.
 *
 * **Restore is the confirm action even though it is the destructive one**,
 * because it is the one the user asked for by opening a file. Cancel is a plain
 * dismissal and writes nothing.
 */
@Composable
private fun RestoreConfirmDialog(
    pending: PendingRestore,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val incoming = pluralStringResource(
        R.plurals.backup_restore_confirm_incoming,
        pending.incomingTaskCount,
        pending.incomingTaskCount
    )

    // An empty device gets its own sentence rather than a plural, because
    // English has no quantity for zero and "replaces the 0 tasks on this
    // device" is a sentence no one writes.
    val current = if (pending.currentTaskCount == 0) {
        stringResource(R.string.backup_restore_confirm_current_none)
    } else {
        pluralStringResource(
            R.plurals.backup_restore_confirm_current,
            pending.currentTaskCount,
            pending.currentTaskCount
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = { Text(stringResource(R.string.backup_restore_confirm_body, incoming, current)) },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.backup_restore_confirm_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_restore_confirm_action))
            }
        }
    )
}

@Composable
private fun BackupErrorDialog(
    error: BackupOperation,
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit
) {
    val restore = error == BackupOperation.Restore
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (restore) R.string.backup_restore_error_title
                    else R.string.backup_export_error_title
                )
            )
        },
        text = {
            Text(
                stringResource(
                    if (restore) R.string.backup_restore_error_body
                    else R.string.backup_export_error_body
                )
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_error_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onChooseAnother) {
                Text(
                    stringResource(
                        if (restore) R.string.backup_restore_error_choose_another
                        else R.string.backup_export_error_choose_another
                    )
                )
            }
        }
    )
}

private const val JsonMimeType = "application/json"
private val LocalFirstMinHeight = 154.dp

@Preview(name = "Backup light", heightDp = 720)
@Preview(name = "Backup dark", heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackupPreview() {
    FocuslistTheme(dynamicColor = false) {
        BackupContent(
            isWorking = false,
            onExport = {},
            onRestore = {},
            onBack = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
