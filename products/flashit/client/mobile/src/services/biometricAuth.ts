/**
 * FlashIt Mobile - Biometric Authentication Service
 *
 * Provides biometric authentication (Face ID, Touch ID, fingerprint) for app security.
 *
 * @doc.type service
 * @doc.purpose Biometric authentication for app lock
 * @doc.layer product
 * @doc.pattern Security Service
 */

import * as LocalAuthentication from 'expo-local-authentication';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const STORAGE_KEYS = {
  BIOMETRIC_ENABLED: 'biometric_enabled',
  APP_LOCK_ENABLED: 'app_lock_enabled',
  LOCK_TIMEOUT: 'lock_timeout',
  LAST_ACTIVE: 'last_active_time',
};

/**
 * Biometric type.
 */
export type BiometricType = 'face' | 'fingerprint' | 'iris' | 'none';

/**
 * Authentication result.
 */
export interface AuthResult {
  success: boolean;
  error?: string;
  cancelled?: boolean;
}

/**
 * Biometric settings.
 */
export interface BiometricSettings {
  enabled: boolean;
  appLockEnabled: boolean;
  lockTimeout: number; // seconds
  biometricType: BiometricType;
  isEnrolled: boolean;
  isSupported: boolean;
}

/**
 * Lock timeout options in seconds.
 */
export const LOCK_TIMEOUT_OPTIONS = [
  { label: 'Immediately', value: 0 },
  { label: 'After 1 minute', value: 60 },
  { label: 'After 5 minutes', value: 300 },
  { label: 'After 15 minutes', value: 900 },
  { label: 'After 30 minutes', value: 1800 },
  { label: 'After 1 hour', value: 3600 },
];

/**
 * Biometric Authentication Service.
 */
class BiometricAuthService {
  private settings: BiometricSettings = {
    enabled: false,
    appLockEnabled: false,
    lockTimeout: 0,
    biometricType: 'none',
    isEnrolled: false,
    isSupported: false,
  };
  private lastActiveTime: number = Date.now();
  private initialized: boolean = false;

  /**
   * Initialize the biometric service.
   */
  async init(): Promise<BiometricSettings> {
    if (this.initialized) return this.settings;

    try {
      // Check hardware support
      const compatible = await LocalAuthentication.hasHardwareAsync();
      this.settings.isSupported = compatible;

      // Check enrollment
      const enrolled = await LocalAuthentication.isEnrolledAsync();
      this.settings.isEnrolled = enrolled;

      // Get biometric type
      if (compatible) {
        const types = await LocalAuthentication.supportedAuthenticationTypesAsync();
        this.settings.biometricType = this.mapBiometricType(types);
      }

      // Load saved settings
      await this.loadSettings();

      this.initialized = true;
      console.log('[Biometric] Initialized:', this.settings);

      return this.settings;
    } catch (error) {
      console.error('[Biometric] Init error:', error);
      return this.settings;
    }
  }

  /**
   * Map authentication types to friendly names.
   */
  private mapBiometricType(
    types: LocalAuthentication.AuthenticationType[]
  ): BiometricType {
    if (types.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION)) {
      return 'face';
    }
    if (types.includes(LocalAuthentication.AuthenticationType.FINGERPRINT)) {
      return 'fingerprint';
    }
    if (types.includes(LocalAuthentication.AuthenticationType.IRIS)) {
      return 'iris';
    }
    return 'none';
  }

  /**
   * Load settings from secure storage.
   */
  private async loadSettings(): Promise<void> {
    try {
      const enabled = await SecureStore.getItemAsync(STORAGE_KEYS.BIOMETRIC_ENABLED);
      const appLock = await SecureStore.getItemAsync(STORAGE_KEYS.APP_LOCK_ENABLED);
      const timeout = await SecureStore.getItemAsync(STORAGE_KEYS.LOCK_TIMEOUT);
      const lastActive = await SecureStore.getItemAsync(STORAGE_KEYS.LAST_ACTIVE);

      this.settings.enabled = enabled === 'true';
      this.settings.appLockEnabled = appLock === 'true';
      this.settings.lockTimeout = timeout ? parseInt(timeout, 10) : 0;
      this.lastActiveTime = lastActive ? parseInt(lastActive, 10) : Date.now();
    } catch (error) {
      console.error('[Biometric] Failed to load settings:', error);
    }
  }

  /**
   * Save settings to secure storage.
   */
  private async saveSettings(): Promise<void> {
    try {
      await SecureStore.setItemAsync(
        STORAGE_KEYS.BIOMETRIC_ENABLED,
        String(this.settings.enabled)
      );
      await SecureStore.setItemAsync(
        STORAGE_KEYS.APP_LOCK_ENABLED,
        String(this.settings.appLockEnabled)
      );
      await SecureStore.setItemAsync(
        STORAGE_KEYS.LOCK_TIMEOUT,
        String(this.settings.lockTimeout)
      );
    } catch (error) {
      console.error('[Biometric] Failed to save settings:', error);
    }
  }

  /**
   * Get current settings.
   */
  getSettings(): BiometricSettings {
    return { ...this.settings };
  }

  /**
   * Get a user-friendly name for the biometric type.
   */
  getBiometricTypeName(): string {
    switch (this.settings.biometricType) {
      case 'face':
        return Platform.OS === 'ios' ? 'Face ID' : 'Face Unlock';
      case 'fingerprint':
        return Platform.OS === 'ios' ? 'Touch ID' : 'Fingerprint';
      case 'iris':
        return 'Iris Scan';
      default:
        return 'Biometrics';
    }
  }

  /**
   * Enable biometric authentication.
   */
  async enable(): Promise<AuthResult> {
    if (!this.settings.isSupported) {
      return { success: false, error: 'Biometric authentication not supported' };
    }

    if (!this.settings.isEnrolled) {
      return { success: false, error: 'No biometrics enrolled on this device' };
    }

    // Verify biometrics before enabling
    const result = await this.authenticate('Verify to enable biometric lock');
    
    if (result.success) {
      this.settings.enabled = true;
      this.settings.appLockEnabled = true;
      await this.saveSettings();
    }

    return result;
  }

  /**
   * Disable biometric authentication.
   */
  async disable(): Promise<void> {
    this.settings.enabled = false;
    this.settings.appLockEnabled = false;
    await this.saveSettings();
  }

  /**
   * Set app lock enabled.
   */
  async setAppLockEnabled(enabled: boolean): Promise<void> {
    this.settings.appLockEnabled = enabled;
    await this.saveSettings();
  }

  /**
   * Set lock timeout.
   */
  async setLockTimeout(seconds: number): Promise<void> {
    this.settings.lockTimeout = seconds;
    await this.saveSettings();
  }

  /**
   * Authenticate user with biometrics.
   */
  async authenticate(reason?: string): Promise<AuthResult> {
    if (!this.settings.isSupported || !this.settings.isEnrolled) {
      return { success: false, error: 'Biometrics not available' };
    }

    try {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: reason || 'Authenticate to continue',
        cancelLabel: 'Cancel',
        disableDeviceFallback: false,
        fallbackLabel: 'Use Passcode',
      });

      if (result.success) {
        this.updateLastActiveTime();
        return { success: true };
      }

      if (result.error === 'user_cancel') {
        return { success: false, cancelled: true };
      }

      return { success: false, error: result.error };
    } catch (error) {
      console.error('[Biometric] Auth error:', error);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Authentication failed',
      };
    }
  }

  /**
   * Check if app should be locked.
   */
  shouldLock(): boolean {
    if (!this.settings.appLockEnabled || !this.settings.enabled) {
      return false;
    }

    const elapsed = (Date.now() - this.lastActiveTime) / 1000;
    return elapsed > this.settings.lockTimeout;
  }

  /**
   * Update last active time.
   */
  updateLastActiveTime(): void {
    this.lastActiveTime = Date.now();
    SecureStore.setItemAsync(STORAGE_KEYS.LAST_ACTIVE, String(this.lastActiveTime));
  }

  /**
   * Get last active time.
   */
  getLastActiveTime(): number {
    return this.lastActiveTime;
  }

  /**
   * Lock the app immediately.
   */
  lockNow(): void {
    this.lastActiveTime = 0;
  }

  /**
   * Check if biometric is available.
   */
  isAvailable(): boolean {
    return this.settings.isSupported && this.settings.isEnrolled;
  }

  /**
   * Check if app lock is enabled.
   */
  isLockEnabled(): boolean {
    return this.settings.appLockEnabled && this.settings.enabled;
  }
}

// Export singleton instance
export const biometricAuth = new BiometricAuthService();
export default biometricAuth;
