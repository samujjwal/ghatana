package com.yappc.guardian.agent.native_modules

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import timber.log.Timber

/**
 * React Native bridge for AccessibilityService.
 *
 * <p><b>Purpose</b><br>
 * Minimal native module that captures app foreground events and sends them to React Native.
 * All business logic stays in React Native.
 *
 * <p><b>Events Emitted</b><br>
 * - "appFocused" - app came to foreground with package name
 *
 * <p><b>Methods</b><br>
 * - enableAccessibility() - prompt user to enable accessibility service
 * - isAccessibilityEnabled() - check if enabled
 *
 * <p><b>Design</b><br>
 * This is a MINIMAL bridge. The actual AccessibilityService runs separately
 * and emits events via this module. React Native handles all app logic.
 *
 * @doc.type module
 * @doc.purpose React Native bridge for accessibility events
 * @doc.layer native
 * @doc.pattern React Native Module
 */
class AccessibilityBridgeModule(private val appReactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(appReactContext) {

    companion object {
        private const val TAG = "AccessibilityBridge"
        private const val MODULE_NAME = "AccessibilityBridge"
        private var instance: AccessibilityBridgeModule? = null
        private var sharedReactContext: ReactApplicationContext? = null

        fun getInstance(): AccessibilityBridgeModule? = instance
        
        fun getReactContext(): ReactApplicationContext? = sharedReactContext

        fun emitAppFocused(reactContext: ReactApplicationContext, packageName: String) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("appFocused", packageName)
        }
    }

    init {
        instance = this
        sharedReactContext = appReactContext
    }

    override fun getName(): String = MODULE_NAME

    @ReactMethod
    fun enableAccessibility() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            appReactContext.startActivity(intent)
            Timber.d("Accessibility settings opened")
        } catch (e: Exception) {
            Timber.e(e, "Error opening accessibility settings")
        }
    }

    @ReactMethod
    fun isAccessibilityEnabled(callback: com.facebook.react.bridge.Callback) {
        val isEnabled = isAccessibilityServiceEnabled()
        callback.invoke(isEnabled)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val context = appReactContext
        val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager ?: return false
        
        return am.isEnabled && am.isTouchExplorationEnabled
    }
}

/**
 * Minimal AccessibilityService that only captures events.
 * Real app tracking logic is in React Native.
 */
class AppFocusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrEmpty() && packageName != "android") {
                // Send event to React Native via bridge
                try {
                    val reactContext = AccessibilityBridgeModule.getReactContext()
                    if (reactContext != null) {
                        AccessibilityBridgeModule.emitAppFocused(reactContext, packageName)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error emitting app focus event")
                }
            }
        }
    }

    override fun onInterrupt() {}
}
