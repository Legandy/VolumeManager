package io.github.legandy.volumemixerpanel.actions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.legandy.volumemixerpanel.overlay.OverlayService
import io.github.legandy.volumemixerpanel.core.MyApplication

class ActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received action: $action")

        when (action) {
            ACTION_TOGGLE_SILENT -> {
                Log.d(TAG, "Handling ACTION_TOGGLE_SILENT")
                MyApplication.volumeManager.setSilent()
            }
            else -> {
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
    }
}
