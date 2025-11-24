package io.github.legandy.volumemixerpanel.actions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.legandy.volumemixerpanel.overlay.OverlayService

class ActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received action: $action")

        val serviceIntent = Intent(context, OverlayService::class.java).apply {
            this.action = action
        }

        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OverlayService for action: $action", e)
        }
    }
}
