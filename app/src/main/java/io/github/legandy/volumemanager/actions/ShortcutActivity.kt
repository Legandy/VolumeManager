package io.github.legandy.volumemanager.actions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.legandy.volumemanager.R
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

class ShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolumeManagerTheme {
                ShortcutScreen()
            }
        }
    }

    @Composable
    private fun ShortcutScreen() {
        val shortcuts = listOf(
            ShortcutInfo("Show Overlay", ACTION_SHOW_OVERLAY, Icons.Default.PlayArrow),
            ShortcutInfo("Hide Overlay", ACTION_HIDE_OVERLAY, Icons.Default.Stop),
            ShortcutInfo("Toggle Overlay", ACTION_TOGGLE_OVERLAY, Icons.AutoMirrored.Filled.ArrowForward)
        )

        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Create Volume Shortcut",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn {
                    items(shortcuts) { shortcut ->
                        ShortcutItem(shortcutInfo = shortcut) {
                            createShortcut(it)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ShortcutItem(shortcutInfo: ShortcutInfo, onClick: (ShortcutInfo) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(shortcutInfo) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(shortcutInfo.icon, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Text(shortcutInfo.label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    private fun createShortcut(info: ShortcutInfo) {
        val shortcutIntent = Intent(this, ShortcutProxyActivity::class.java).apply {
            action = info.action
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(this, info.label)
            .setShortLabel(info.label)
            .setLongLabel(info.label)
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()

        ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)

        setResult(RESULT_OK)
        finish()
    }

    private data class ShortcutInfo(val label: String, val action: String, val icon: ImageVector)
}