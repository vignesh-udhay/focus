package com.vignesh.focuslist.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vignesh.focuslist.R
import com.vignesh.focuslist.core.design.FocuslistDimensions
import com.vignesh.focuslist.core.design.FocuslistSpacing
import com.vignesh.focuslist.core.design.ThemePreference
import com.vignesh.focuslist.core.design.focuslistContentGutter
import com.vignesh.focuslist.data.local.AppearancePreferences
import com.vignesh.focuslist.ui.component.FocuslistTopAppBar
import com.vignesh.focuslist.ui.component.SectionLabel

/** D-024's closed four-row Settings screen. */
@Composable
fun SettingsScreen(
    preferences: AppearancePreferences,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onOpenReminderHealth: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    val gutter = focuslistContentGutter()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            FocuslistTopAppBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = FocuslistSpacing.md + gutter,
                    end = FocuslistSpacing.md + gutter,
                    bottom = FocuslistSpacing.lg
                )
        ) {
            SettingsSection(
                label = stringResource(R.string.settings_section_reminders)
            ) {
                NavigationSettingRow(
                    title = stringResource(R.string.settings_reminder_health),
                    supporting = stringResource(R.string.settings_reminder_health_supporting),
                    onClick = onOpenReminderHealth,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsSection(
                label = stringResource(R.string.settings_section_appearance)
            ) {
                val shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2)
                ToggleSettingRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    supporting = stringResource(R.string.settings_dynamic_color_supporting),
                    checked = preferences.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                    shape = shapes.shape,
                    modifier = Modifier.fillMaxWidth()
                )

                NavigationSettingRow(
                    title = stringResource(R.string.settings_theme),
                    supporting = stringResource(preferences.theme.label),
                    onClick = { showThemeDialog = true },
                    shape = ListItemDefaults.segmentedShapes(index = 1, count = 2).shape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsSection(
                label = stringResource(R.string.settings_section_data)
            ) {
                NavigationSettingRow(
                    title = stringResource(R.string.settings_backup_restore),
                    supporting = stringResource(R.string.settings_backup_restore_supporting),
                    onClick = onOpenBackup,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            selected = preferences.theme,
            onSelect = { theme ->
                onThemeChange(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SettingsSection(
    label: String,
    content: @Composable () -> Unit
) {
    SectionLabel(label)
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        content()
    }
}

@Composable
private fun NavigationSettingRow(
    title: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.heightIn(min = SettingRowMinHeight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.md),
            modifier = Modifier.padding(
                start = FocuslistSpacing.md,
                top = FocuslistSpacing.sm,
                end = FocuslistSpacing.lg,
                bottom = FocuslistSpacing.sm
            )
        ) {
            SettingText(title = title, supporting = supporting, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_forward),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .heightIn(min = SettingRowMinHeight)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.md),
            modifier = Modifier.padding(
                start = FocuslistSpacing.md,
                top = FocuslistSpacing.sm,
                end = FocuslistSpacing.lg,
                bottom = FocuslistSpacing.sm
            )
        ) {
            SettingText(title = title, supporting = supporting, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SettingText(title: String, supporting: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeDialog(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_choose_theme)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemePreference.entries.forEach { theme ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FocuslistSpacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = FocuslistDimensions.TouchTargetMin)
                            .selectable(
                                selected = theme == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(theme) }
                            )
                    ) {
                        RadioButton(selected = theme == selected, onClick = null)
                        Text(text = stringResource(theme.label))
                    }
                }
            }
        },
        confirmButton = {}
    )
}

private val ThemePreference.label: Int
    get() = when (this) {
        ThemePreference.System -> R.string.settings_theme_system
        ThemePreference.Light -> R.string.settings_theme_light
        ThemePreference.Dark -> R.string.settings_theme_dark
    }

private val SettingRowMinHeight = 72.dp

