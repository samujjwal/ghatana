package com.yappc.guardian.agent.native_modules

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Callback
import timber.log.Timber

/**
 * React Native bridge for Device Admin policies.
 *
 * <p><b>Purpose</b><br>
 * Minimal native module that handles device admin operations.
 * React Native calls this when policies need system-level enforcement.
 *
 * <p><b>Methods</b><br>
 * - lockDevice() - lock device immediately
 * - requestDeviceAdmin() - prompt user to enable device admin
 * - isDeviceAdminEnabled() - check status
 *
 * <p><b>Design</b><br>
 * MINIMAL bridge. Only handles direct device admin calls.
 * Policy decision logic is in React Native.
 *
 * @doc.type module
 * @doc.purpose React Native bridge for device admin operations
 * @doc.layer native
 * @doc.pattern React Native Module
 */
class DeviceAdminBridgeModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val MODULE_NAME = "DeviceAdminBridge"
        private const val TAG = "DeviceAdminBridge"
    }

    override fun getName(): String = MODULE_NAME

    @ReactMethod
    fun lockDevice() {
        try {
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = GuardianDeviceAdminReceiver.getComponentName(reactContext)
            
            if (dpm.isAdminActive(componentName)) {
                dpm.lockNow()
                Timber.d("Device locked via policy")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error locking device")
        }
    }

    @ReactMethod
    fun requestDeviceAdmin() {
        try {
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = GuardianDeviceAdminReceiver.getComponentName(reactContext)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Guardian needs device admin for policy enforcement")
            reactContext.startActivity(intent)
            Timber.d("Device admin request shown")
        } catch (e: Exception) {
            Timber.e(e, "Error requesting device admin")
        }
    }

    @ReactMethod
    fun isDeviceAdminEnabled(callback: Callback) {
        try {
            val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = GuardianDeviceAdminReceiver.getComponentName(reactContext)
            val isEnabled = dpm.isAdminActive(componentName)
            callback.invoke(isEnabled)
        } catch (e: Exception) {
            Timber.e(e, "Error checking device admin status")
            callback.invoke(false)
        }
    }
}

/**
 * Minimal Device Admin Receiver.
 * Only handles enable/disable callbacks.
 */
class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Timber.d("Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Timber.d("Device admin disabled")
    }
}
