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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(JsonMimeType),
        onResult = { uri -> if (uri != null) viewModel.exportTo(uri) }
    )
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.restoreFrom(uri) }
    )

    val chooseExport = {
        exportLauncher.launch("Focuslist-backup-${LocalDate.now()}.json")
    }
    val chooseRestore = {
        restoreLauncher.launch(arrayOf(JsonMimeType, "text/json"))
    }

    BackupContent(
        isWorking = state.isWorking,
        onExport = chooseExport,
        onRestore = chooseRestore,
        onBack = onBack,
        modifier = modifier
    )

    state.error?.let { error ->
        BackupErrorDialog(
            error = error,
            onDismiss = viewModel::dismissError,
            onChooseAnother = {
                viewModel.dismissError()
                if (error == BackupError.Restore) chooseRestore() else chooseExport()
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
    modifier: Modifier = Modifier
) {
    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
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

@Composable
private fun BackupErrorDialog(
    error: BackupError,
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit
) {
    val restore = error == BackupError.Restore
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
            onBack = {}
        )
    }
}
