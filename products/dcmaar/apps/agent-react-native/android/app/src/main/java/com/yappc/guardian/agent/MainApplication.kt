package com.yappc.guardian.agent

import android.app.Application
import android.content.res.Configuration

import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.ReactHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.uimanager.ViewManager
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.soloader.SoLoader

import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ReactNativeHostWrapper

import timber.log.Timber
import com.yappc.guardian.agent.native_modules.AccessibilityBridgeModule
import com.yappc.guardian.agent.native_modules.BackgroundSyncModule
import com.yappc.guardian.agent.native_modules.DeviceAdminBridgeModule
import com.yappc.guardian.agent.native_modules.PermissionsModule

/**
 * Main application class for Guardian Agent.
 *
 * <p><b>Initialization</b><br>
 * - Initializes Timber logging
 * - Registers minimal native bridge modules
 * - Loads React Native with Expo integration
 *
 * <p><b>Native Modules</b><br>
 * - AccessibilityBridgeModule - App foreground tracking
 * - DeviceAdminBridgeModule - Device locking
 * - PermissionsModule - Permission status checking
 * - BackgroundSyncModule - Background sync coordination
 *
 * @doc.type application
 * @doc.purpose Guardian Agent main application class
 * @doc.layer native
 * @doc.pattern Android Application
 */
class MainApplication : Application(), ReactApplication {

  override val reactNativeHost: ReactNativeHost = ReactNativeHostWrapper(
        this,
        object : DefaultReactNativeHost(this) {
          override fun getPackages(): List<ReactPackage> {
            val packages = mutableListOf<ReactPackage>()
            
            // Add autolinked packages
            packages.addAll(PackageList(this).packages)
            
            // Register custom native bridge modules
            packages.add(NativeModulesPackage())
            
            return packages
          }

          override fun getJSMainModuleName(): String = ".expo/.virtual-metro-entry"

          override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

          override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
          override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
      }
  )

  override val reactHost: ReactHost
    get() = ReactNativeHostWrapper.createReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    super.onCreate()
    SoLoader.init(this, false)
    
    // Initialize Timber logging
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    
    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
      // If you opted-in for the New Architecture, we load the native entry point for this app.
      load()
    }
    ApplicationLifecycleDispatcher.onApplicationCreate(this)
    
    Timber.d("Guardian Agent initialized with native bridge modules")
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
  }
}

/**
 * Custom React Package for Guardian native modules.
 *
 * <p><b>Purpose</b><br>
 * Registers all minimal bridge modules with React Native.
 * Each module exposes system-level APIs: accessibility, device admin, permissions, sync.
 *
 * @doc.type class
 * @doc.purpose Native module registration package
 * @doc.layer native
 * @doc.pattern React Package
 */
class NativeModulesPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(
            AccessibilityBridgeModule(reactContext),
            DeviceAdminBridgeModule(reactContext),
            PermissionsModule(reactContext),
            BackgroundSyncModule(reactContext)
        )
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList() // No custom view managers needed
    }
}
