package io.github.legandy.volumemixerpanel.actions

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class ShortcutProxyActivity : Activity() {
    companion object {
        private const val TAG = "ShortcutProxyActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ... send your broadcast logic here ...
        intent.action?.let { action ->
            val broadcastIntent = Intent(action).setPackage(packageName)
            sendBroadcast(broadcastIntent)
        }

        finish() // Kill it immediately
    }
}