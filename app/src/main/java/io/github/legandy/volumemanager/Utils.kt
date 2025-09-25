package io.github.legandy.volumemanager

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager

/**
 * Checks if the VolumeManager accessibility service is enabled.
 * Tries multiple methods for reliability across different Android versions.
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val tag = "VolumeManager.Utils"

    // Method 1: Check via AccessibilityManager (most reliable)
    try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val myServiceComponent = ComponentName(context, OverlayService::class.java)

            for (service in enabledServices) {
                val enabledComponent = ComponentName(
                    service.resolveInfo.serviceInfo.packageName,
                    service.resolveInfo.serviceInfo.name
                )
                if (enabledComponent == myServiceComponent) {
                    Log.d(tag, "Accessibility service is enabled (via AccessibilityManager)")
                    return true
                }
            }
        }
    } catch (e: Exception) {
        Log.w(tag, "Failed to check via AccessibilityManager", e)
    }

    // Method 2: Check via Settings.Secure (fallback)
    try {
        val enabledServicesString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (!enabledServicesString.isNullOrBlank()) {
            val myServiceFlat = ComponentName(context, OverlayService::class.java).flattenToString()
            val myServiceShort = ComponentName(context, OverlayService::class.java).flattenToShortString()

            // Check both full and short component names
            if (enabledServicesString.contains(myServiceFlat) ||
                enabledServicesString.contains(myServiceShort)) {
                Log.d(tag, "Accessibility service is enabled (via Settings.Secure)")
                return true
            }
        }
    } catch (e: Exception) {
        Log.w(tag, "Failed to check via Settings.Secure", e)
    }

    Log.d(tag, "Accessibility service is not enabled")
    return false
}