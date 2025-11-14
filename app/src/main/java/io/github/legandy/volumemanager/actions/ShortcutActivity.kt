package io.github.legandy.volumemanager.actions

import android.app.Activity
import android.app.PendingIntent
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

    @Suppress("DEPRECATION")
    private fun createShortcut(info: ShortcutInfo) {
        val shortcutIntent = Intent(applicationContext, ShortcutProxyActivity::class.java).apply {
            action = info.action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_DEFAULT) // Ensure default category is added for implicit resolution
        }

        val resultIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_NAME, info.label)

            // Check if the calling app requests a PendingIntent (modern approach)
            val requestPendingIntent = intent.getBooleanExtra(EXTRA_REQUEST_PENDING_INTENT, false)

            if (requestPendingIntent) {
                // Modern approach: Return a PendingIntent
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0, // Request code, can be unique if needed
                    shortcutIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, pendingIntent)
            } else {
                // Deprecated/Old approach: Return a direct Intent for compatibility with older apps/launchers
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            }

            // Include icon resource for compatibility with older launchers/apps
            putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(applicationContext, R.mipmap.ic_launcher))
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private data class ShortcutInfo(val label: String, val action: String, val icon: ImageVector)

    companion object {
        const val EXTRA_REQUEST_PENDING_INTENT = "io.github.legandy.volumemanager.extra.REQUEST_PENDING_INTENT"
    }
}