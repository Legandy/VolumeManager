package io.github.legandy.volumemixerpanel.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import io.github.legandy.volumemixerpanel.overlay.OverlayService

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val tag = "VolumeMixerPanel.Utils"
    val myServiceComponent = ComponentName(context, OverlayService::class.java)

    // Check via AccessibilityManager
    try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
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

    // Check via Settings.Secure (fallback)
    try {
        val enabledServicesString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (!enabledServicesString.isNullOrBlank()) {
            val myServiceFlat = myServiceComponent.flattenToString()
            val colonSeparatedList = enabledServicesString.split(":")
            if (colonSeparatedList.any { it == myServiceFlat }) {
                Log.d(tag, "Accessibility service is enabled (via Settings.Secure - robust check)")
                return true
            }
        }
    } catch (e: Exception) {
        Log.w(tag, "Failed to check via Settings.Secure - robust check", e)
    }

    Log.d(tag, "Accessibility service is not enabled")
    return false
}