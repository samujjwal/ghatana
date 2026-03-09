/**
 * Native Bridge Interface
 * Type-safe interface for React Native ↔ Native communication
 */

import {
  AppInfo,
  DeviceInfo,
  UsageData,
  ManagedSettings,
  ScreenTimeData
} from './types';

/**
 * Android-specific: AccessibilityService interface
 */
export interface IAccessibilityService {
  /**
   * Start monitoring foreground app changes
   */
  startMonitoring(): Promise<void>;

  /**
   * Stop monitoring foreground app changes
   */
  stopMonitoring(): Promise<void>;

  /**
   * Get currently active app
   */
  getCurrentApp(): Promise<AppInfo>;

  /**
   * Register callback for app changes
   */
  onAppChange(callback: (app: AppInfo) => void): { remove: () => void };
}

/**
 * Android-specific: UsageStatsManager interface
 */
export interface IUsageStatsCollector {
  /**
   * Get app usage for a specific date
   * @param date - ISO 8601 date string (YYYY-MM-DD)
   */
  getDailyUsage(date: string): Promise<Record<string, number>>;

  /**
   * Get app usage for a date range
   * @param startDate - ISO 8601 date string
   * @param endDate - ISO 8601 date string
   */
  getUsageRange(startDate: string, endDate: string): Promise<UsageData[]>;

  /**
   * Get real-time usage for today
   */
  getTodayUsage(): Promise<Record<string, number>>;
}

/**
 * Android-specific: Device Admin interface
 */
export interface IDeviceAdminManager {
  /**
   * Check if device admin is active
   */
  isDeviceAdminActive(): Promise<boolean>;

  /**
   * Request device admin privileges (launches system dialog)
   */
  requestDeviceAdmin(): Promise<boolean>;

  /**
   * Prevent app uninstallation
   */
  preventUninstall(): Promise<void>;

  /**
   * Lock device screen
   */
  lockDevice(): Promise<void>;

  /**
   * Wipe device data (emergency only)
   */
  wipeData(): Promise<void>;
}

/**
 * Android-specific: Overlay Manager interface
 */
export interface IOverlayManager {
  /**
   * Check if overlay permission is granted
   */
  hasOverlayPermission(): Promise<boolean>;

  /**
   * Request overlay permission (launches system settings)
   */
  requestOverlayPermission(): Promise<boolean>;

  /**
   * Show blocking overlay
   */
  showBlockingOverlay(appName: string, reason: string): Promise<void>;

  /**
   * Dismiss blocking overlay
   */
  dismissBlockingOverlay(): Promise<void>;

  /**
   * Check if overlay is currently showing
   */
  isOverlayShowing(): Promise<boolean>;
}

/**
 * iOS-specific: ScreenTime Manager interface
 */
export interface IScreenTimeManager {
  /**
   * Get screen time data for date range
   */
  getScreenTimeData(startDate: string, endDate: string): Promise<ScreenTimeData>;

  /**
   * Get today's screen time
   */
  getTodayScreenTime(): Promise<ScreenTimeData>;

  /**
   * Set blocked apps by bundle identifiers
   */
  setBlockedApps(bundleIds: string[]): Promise<void>;

  /**
   * Get currently blocked apps
   */
  getBlockedApps(): Promise<string[]>;
}

/**
 * iOS-specific: Family Controls Manager interface
 */
export interface IFamilyControlsManager {
  /**
   * Request Family Controls access (shows system dialog)
   */
  requestFamilyControlsAccess(): Promise<boolean>;

  /**
   * Check if Family Controls is available on device
   */
  isFamilyControlsAvailable(): Promise<boolean>;

  /**
   * Check if Family Controls access is granted
   */
  hasFamilyControlsAccess(): Promise<boolean>;

  /**
   * Set managed settings (app blocking, web filtering)
   */
  setManagedSettings(settings: ManagedSettings): Promise<void>;

  /**
   * Get current managed settings
   */
  getManagedSettings(): Promise<ManagedSettings>;

  /**
   * Clear all restrictions
   */
  clearAllRestrictions(): Promise<void>;
}

/**
 * Main Guardian Native Module
 * Available on both Android and iOS
 */
export interface GuardianNativeModule {
  /**
   * Get device information
   */
  getDeviceInfo(): Promise<DeviceInfo>;

  /**
   * Get list of all installed apps
   */
  getAppList(): Promise<AppInfo[]>;

  /**
   * Initiate sync with backend
   */
  initiateSync(): Promise<void>;

  /**
   * Check if monitoring is active
   */
  isMonitoringActive(): Promise<boolean>;

  // Platform-specific modules (null on unsupported platforms)
  AccessibilityService?: IAccessibilityService;
  UsageStatsCollector?: IUsageStatsCollector;
  DeviceAdminManager?: IDeviceAdminManager;
  OverlayManager?: IOverlayManager;

  // iOS-specific
  ScreenTimeManager?: IScreenTimeManager;
  FamilyControlsManager?: IFamilyControlsManager;
}

/**
 * Export singleton instance
 * This will be implemented by native code
 */
declare module 'react-native' {
  interface NativeModulesStatic {
    GuardianNativeModule: GuardianNativeModule;
  }
}

// Export for use in app
import { NativeModules } from 'react-native';
export const Guardian: GuardianNativeModule = NativeModules.GuardianNativeModule;
