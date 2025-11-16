package io.github.legandy.volumemanager.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.legandy.volumemanager.overlay.OverlayService
import io.github.legandy.volumemanager.ui.theme.VolumeManagerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action?.let { isShortcutAction(it) } == true) {
            handleShortcutIntent(intent)
            return
        }

        enableEdgeToEdge()
        setContent {
            VolumeManagerTheme {
                // val mainViewModel: MainViewModel = viewModel() // Removed as MainScreen does not accept it
                MainScreen()
            }
        }
    }

    private fun isShortcutAction(action: String) = action.startsWith("io.github.legandy.volumemanager.action")

    private fun handleShortcutIntent(intent: Intent) {
        startService(Intent(this, OverlayService::class.java).setAction(intent.action))
        finish()
    }
}
