package com.guardian.childmobile

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class RNBlockModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "RNBlockModule"

    @ReactMethod
    fun getBlockEvents(promise: Promise) {
        try {
            // Stub implementation: no native block events recorded yet.
            // The JS bridge treats an empty array as "no native data" and falls back to API.
            val result = Arguments.createArray()
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message, e)
        }
    }
}
