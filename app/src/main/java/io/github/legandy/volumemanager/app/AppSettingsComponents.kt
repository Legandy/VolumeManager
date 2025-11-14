package io.github.legandy.volumemanager.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.legandy.volumemanager.R
import io.github.legandy.volumemanager.settings.ThemeMode
import io.github.legandy.volumemanager.settings.ui.SettingClickableItem
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    uiState: AppSettingsViewModel.AppSettingsUiState,
    onShowSetupRequested: () -> Unit,
    onThemeSettingClick: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismissThemeDialog: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_label)) },
                actions = {
                    // No action buttons in the TopAppBar for AppSettingsScreen
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column {
                    SettingClickableItem(
                        title = stringResource(R.string.setting_theme_mode_title),
                        subtitle = when (uiState.themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system_default)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        },
                        onClick = onThemeSettingClick,
                        showDivider = false
                    )
                }
            }

            Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column {
                    SettingClickableItem(
                        title = stringResource(R.string.show_setup_onboarding_content_description),
                        onClick = onShowSetupRequested,
                        showDivider = false
                    )
                }
            }
        }
    }

    if (uiState.showThemeDialog) {
        ThemeSelectionDialog(
            currentThemeMode = uiState.themeMode,
            onDismissRequest = onDismissThemeDialog,
            onThemeSelected = onThemeSelected
        )
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentThemeMode: ThemeMode,
    onDismissRequest: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit
) {
    val themeModes = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system_default),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark)
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.select_theme_dialog_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                themeModes.forEach { (themeMode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (currentThemeMode == themeMode),
                                onClick = { onThemeSelected(themeMode) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (currentThemeMode == themeMode),
                            onClick = null
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel_button))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AppSettingsPreview() {
    VolumeManagerTheme {
        AppSettingsScreen(
            uiState = AppSettingsViewModel.AppSettingsUiState(),
            onShowSetupRequested = {},
            onThemeSettingClick = {},
            onThemeSelected = {},
            onDismissThemeDialog = {}
        )
    }
}