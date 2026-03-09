package com.yappc.guardian.agent.native_modules

import android.content.Context
import android.content.Intent
import android.os.Build
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Callback
import timber.log.Timber

/**
 * React Native bridge for permissions management.
 *
 * <p><b>Purpose</b><br>
 * MINIMAL module that checks and requests necessary permissions.
 * All permission handling delegated to React Native permission library.
 *
 * <p><b>Methods</b><br>
 * - checkAccessibilityPermission() - check if accessibility service enabled
 * - checkDeviceAdminPermission() - check if device admin enabled
 * - getPermissionStatus() - get status of all required permissions
 *
 * @doc.type module
 * @doc.purpose React Native bridge for permissions
 * @doc.layer native
 * @doc.pattern React Native Module
 */
class PermissionsModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val MODULE_NAME = "GuardianPermissions"
        private const val TAG = "PermissionsModule"
    }

    override fun getName(): String = MODULE_NAME

    @ReactMethod
    fun checkAccessibilityPermission(callback: Callback) {
        try {
            val am = reactApplicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as? android.view.accessibility.AccessibilityManager
            val isEnabled = am?.isEnabled ?: false
            callback.invoke(isEnabled)
        } catch (e: Exception) {
            Timber.e(e, "Error checking accessibility permission")
            callback.invoke(false)
        }
    }

    @ReactMethod
    fun checkDeviceAdminPermission(callback: Callback) {
        try {
            val dpm = reactApplicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as? android.app.admin.DevicePolicyManager
            val componentName = GuardianDeviceAdminReceiver.getComponentName(reactApplicationContext)
            val isAdmin = dpm?.isAdminActive(componentName) ?: false
            callback.invoke(isAdmin)
        } catch (e: Exception) {
            Timber.e(e, "Error checking device admin permission")
            callback.invoke(false)
        }
    }

    @ReactMethod
    fun getPermissionStatus(callback: Callback) {
        try {
            val context = reactApplicationContext
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as? android.view.accessibility.AccessibilityManager
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as? android.app.admin.DevicePolicyManager

            val accessibilityEnabled = am?.isEnabled ?: false
            val adminEnabled = dpm?.isAdminActive(GuardianDeviceAdminReceiver.getComponentName(context)) ?: false

            val status = mapOf(
                "accessibility" to accessibilityEnabled,
                "deviceAdmin" to adminEnabled,
                "sdkVersion" to Build.VERSION.SDK_INT
            )

            callback.invoke(status)
        } catch (e: Exception) {
            Timber.e(e, "Error getting permission status")
            callback.invoke(mapOf("error" to e.message))
        }
    }
}
