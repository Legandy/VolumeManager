package io.github.legandy.volumemixerpanel.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.joor.Reflect
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    companion object {
        private const val TAG = "VolumeMixerPanel.PermMgr"
        private const val SHIZUKU_REQ_CODE = 42
        private const val SERVICE_NAME_SEPARATOR = ":"
    }

    fun requestShizukuPermission(activity: Activity) {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQ_CODE)
        }
    }
    
    @SuppressLint("NewApi")
    fun grantSystemAlertWindowPermission() {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, android.os.Process.myUid(), context.packageName)
        if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
            Log.d(TAG, "SYSTEM_ALERT_WINDOW already granted.")
            return
        }

        try {
            val process = Reflect.onClass(Shizuku::class.java).call("newProcess", arrayOf("appops", "set", context.packageName, "SYSTEM_ALERT_WINDOW", "allow"), null, null).get<Process>()
            val exitValue = process.waitFor()
            Log.i(TAG, "appops set SYSTEM_ALERT_WINDOW allow exit value: $exitValue")

            val newMode = appOpsManager.checkOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, android.os.Process.myUid(), context.packageName)
            if (newMode == android.app.AppOpsManager.MODE_ALLOWED) {
                Log.d(TAG, "SYSTEM_ALERT_WINDOW granted successfully via Shizuku.")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant SYSTEM_ALERT_WINDOW via Shizuku: ${e.message}", e)
            throw SecurityException("Can't grant SYSTEM_ALERT_WINDOW permission via Shizuku.")
        }

        throw SecurityException("Can't grant SYSTEM_ALERT_WINDOW permission.")
    }

    @SuppressLint("MissingPermission")
    fun grantWriteSecureSettingsPermission() {
        var state = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        if (state == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS already granted.")
            return
        }

        try {
            val process = Reflect.onClass(Shizuku::class.java).call("newProcess", arrayOf("pm", "grant", context.packageName, android.Manifest.permission.WRITE_SECURE_SETTINGS), null, null).get<Process>()
            val exitValue = process.waitFor()
            Log.i(TAG, "pm grant WRITE_SECURE_SETTINGS exit value: $exitValue")

            state = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
            if (state == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "WRITE_SECURE_SETTINGS granted successfully via Shizuku.")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant WRITE_SECURE_SETTINGS via Shizuku: ${e.message}", e)
            throw SecurityException("Can't grant WRITE_SECURE_SETTINGS permission via Shizuku.")
        }

        throw SecurityException("Can't grant WRITE_SECURE_SETTINGS permission.")
    }


    @SuppressLint("MissingPermission")
    fun grantNotificationPolicyPermission(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            Log.d(TAG, "Notification Policy Access already granted.")
            return true
        }

        val shizukuNotificationManager = shizukuManager.shizukuNotificationManager
        if (shizukuNotificationManager == null) {
            Log.e(TAG, "Shizuku Notification Manager not initialized. Cannot grant permission.")
            return false
        }

        return try {
            shizukuNotificationManager.call("setNotificationPolicyAccessGranted", context.packageName, true)
            Log.d(TAG, "Notification Policy Access granted successfully via Shizuku.")
            notificationManager.isNotificationPolicyAccessGranted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant Notification Policy Access via Shizuku: ${e.message}", e)
            false
        }
    }

    fun enableAccessibilityService(componentName: ComponentName) {
        try {
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)

            var enabledAccessibilityServices = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            val serviceName = componentName.flattenToString()

            if (enabledAccessibilityServices.isNullOrBlank()) {
                enabledAccessibilityServices = serviceName
            } else if (!enabledAccessibilityServices.contains(serviceName)) {
                enabledAccessibilityServices += SERVICE_NAME_SEPARATOR + serviceName
            } else {
                Log.d(TAG, "Accessibility service $serviceName already enabled.")
                return
            }

            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                enabledAccessibilityServices
            )

            val finalEnabledServices = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (finalEnabledServices == null || !finalEnabledServices.contains(serviceName)) {
                throw SecurityException("Can't enable accessibility service $serviceName")
            }
            Log.d(TAG, "Accessibility service $serviceName enabled successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable accessibility service: ${e.message}", e)
            throw SecurityException("Failed to enable accessibility service: ${e.message}", e)
        }
    }
}
