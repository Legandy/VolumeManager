package io.github.legandy.volumemanager.actions

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.github.legandy.volumemanager.overlay.OverlayService

class ShortcutProxyActivity : Activity() {
    companion object {
        private const val TAG = "ShortcutProxyActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Proxy activity started with action: ${intent.action}")

        val action = intent.action
        if (action != null && action.startsWith("io.github.legandy.volumemanager.action")) {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                this.action = action
            }
            try {
                startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start OverlayService from proxy", e)
            }
        } else {
            Log.w(TAG, "Proxy activity received an invalid or null action.")
        }

        finish()
    }
}