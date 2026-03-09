package com.yappc.guardian.agent.native_modules

import android.content.Context
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import timber.log.Timber

/**
 * React Native bridge for background sync coordination.
 *
 * <p><b>Purpose</b><br>
 * MINIMAL module that coordinates background sync between React Native and native.
 * Since sync logic is in React Native, this is mostly a notification bridge.
 *
 * <p><b>Methods</b><br>
 * - notifySyncStarted() - notify native of sync start
 * - notifySyncCompleted() - notify native of sync completion
 * - getSyncStatus() - get current sync status
 *
 * @doc.type module
 * @doc.purpose React Native bridge for sync coordination
 * @doc.layer native
 * @doc.pattern React Native Module
 */
class BackgroundSyncModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val MODULE_NAME = "BackgroundSync"
        private const val TAG = "BackgroundSyncModule"
        private var syncInProgress = false
    }

    override fun getName(): String = MODULE_NAME

    @ReactMethod
    fun notifySyncStarted() {
        try {
            syncInProgress = true
            Timber.d("Background sync started notification received")
        } catch (e: Exception) {
            Timber.e(e, "Error notifying sync started")
        }
    }

    @ReactMethod
    fun notifySyncCompleted() {
        try {
            syncInProgress = false
            Timber.d("Background sync completed notification received")
        } catch (e: Exception) {
            Timber.e(e, "Error notifying sync completed")
        }
    }

    @ReactMethod
    fun getSyncStatus(callback: com.facebook.react.bridge.Callback) {
        try {
            val status = if (syncInProgress) "syncing" else "idle"
            callback.invoke(status)
        } catch (e: Exception) {
            callback.invoke("error")
        }
    }
}
