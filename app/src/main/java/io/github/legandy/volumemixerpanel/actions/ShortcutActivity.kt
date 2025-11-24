package io.github.legandy.volumemixerpanel.actions

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
import io.github.legandy.volumemixerpanel.ui.theme.VolumeMixerPanelTheme
import io.github.legandy.volumemixerpanel.R
import androidx.compose.ui.res.stringResource


class ShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolumeMixerPanelTheme {
                ShortcutScreen()
            }
        }
    }

    @Composable
    private fun ShortcutScreen() {
        // 1. Resolve the String resource IDs into actual String values
        val showOverlayLabel = stringResource(id = R.string.show_overlay)
        val hideOverlayLabel = stringResource(id = R.string.hide_overlay)
        val toggleOverlayLabel = stringResource(id = R.string.toggle_overlay)

        // 2. Update the ShortcutInfo list to use the resolved String variables
        val shortcuts = listOf(
            ShortcutInfo(showOverlayLabel, ACTION_SHOW_OVERLAY, Icons.Default.PlayArrow),
            ShortcutInfo(hideOverlayLabel, ACTION_HIDE_OVERLAY, Icons.Default.Stop),
            ShortcutInfo(toggleOverlayLabel, ACTION_TOGGLE_OVERLAY, Icons.AutoMirrored.Filled.ArrowForward)
        )

        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.create_volume_shortcut),
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
        // 1. Setup the Proxy Intent (Legacy fallback)
        val proxyIntent = Intent(applicationContext, ShortcutProxyActivity::class.java).apply {
            action = info.action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Helps prevent animation glitches
        }

        // 2. Setup the Direct Receiver Intent (Modern path)
        val receiverIntent = Intent(applicationContext, ActionReceiver::class.java).apply {
            action = info.action
            setPackage(packageName) // Important for broadcasts
        }

        val resultIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_NAME, info.label)
            putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(applicationContext, R.mipmap.ic_launcher))

            val requestPendingIntent = intent.getBooleanExtra(EXTRA_REQUEST_PENDING_INTENT, false)

            if (requestPendingIntent) {
                // MODERN WAY: If the gesture app asks for a PendingIntent,
                // we give it a BROADCAST directly. This completely skips the proxy.
                val pendingIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    0,
                    receiverIntent, // Points directly to ActionReceiver
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, pendingIntent)
            } else {
                // LEGACY WAY: If it wants a raw Intent, we MUST use the Proxy
                // because gesture apps usually call startActivity(), not sendBroadcast().
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, proxyIntent)
            }
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private data class ShortcutInfo(val label: String, val action: String, val icon: ImageVector)

    companion object {
        const val EXTRA_REQUEST_PENDING_INTENT = "io.github.legandy.volumemixerpanel.extra.REQUEST_PENDING_INTENT"
    }
}