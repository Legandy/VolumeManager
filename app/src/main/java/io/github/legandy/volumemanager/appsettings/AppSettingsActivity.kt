package io.github.legandy.volumemanager.appsettings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.legandy.volumemanager.R
import io.github.legandy.volumemanager.settings.SettingsDataStore
import io.github.legandy.volumemanager.settings.ThemeMode
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.emptyPreferences
import io.github.legandy.volumemanager.settings.ui.SettingClickableItem
import kotlinx.coroutines.flow.Flow
import io.github.legandy.volumemanager.appsettings.appSettingsDataStore


class AppSettingsActivity : ComponentActivity() {

    private val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(applicationContext.appSettingsDataStore)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VolumeManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppSettingsScreen(settingsDataStore = settingsDataStore)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(settingsDataStore: SettingsDataStore) {
    val scope = rememberCoroutineScope()
    val themeMode by settingsDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_settings_label)) })
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column {
                    SettingClickableItem(
                        title = "Theme Mode",
                        subtitle = when (themeMode) {
                            ThemeMode.SYSTEM -> "System Default"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        onClick = { showThemeDialog = true },
                        showDivider = false
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentThemeMode = themeMode,
            onDismissRequest = { showThemeDialog = false },
            onThemeSelected = { newTheme ->
                scope.launch {
                    settingsDataStore.setThemeMode(newTheme)
                }
                showThemeDialog = false
            }
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
        ThemeMode.SYSTEM to "System Default",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Theme") },
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
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AppSettingsPreview() {
    VolumeManagerTheme {
        val mockSettingsDataStore = remember {
            SettingsDataStore(object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flowOf(emptyPreferences())
                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                    TODO("Not yet implemented for preview")
                }
            })
        }
        AppSettingsScreen(settingsDataStore = mockSettingsDataStore)
    }
}