/**
 * Battery Optimizer Service for Flashit Mobile
 * Monitors battery state and optimizes app behavior accordingly
 *
 * @doc.type service
 * @doc.purpose Battery-aware optimization and power management
 * @doc.layer product
 * @doc.pattern OptimizationService
 */

import * as Battery from 'expo-battery';
import * as Network from 'expo-network';
import AsyncStorage from '@react-native-async-storage/async-storage';

// ============================================================================
// Types & Interfaces
// ============================================================================

export type BatteryState = 'unknown' | 'charging' | 'unplugged' | 'full';
export type PowerMode = 'normal' | 'low_power' | 'ultra_low_power' | 'charging';

export interface BatteryStatus {
  level: number; // 0-1
  state: BatteryState;
  lowPowerMode: boolean;
  isCharging: boolean;
}

export interface PowerSettings {
  mode: PowerMode;
  syncInterval: number; // milliseconds
  enableBackgroundSync: boolean;
  enableImageCompression: boolean;
  compressionQuality: number; // 0-1
  enableAnimations: boolean;
  enableAutoUpload: boolean;
  enableWebSocket: boolean;
  maxConcurrentUploads: number;
  enablePreloading: boolean;
  locationUpdateInterval: number; // milliseconds
}

export interface BatteryConfig {
  lowBatteryThreshold: number; // 0-1, default 0.2
  ultraLowBatteryThreshold: number; // 0-1, default 0.1
  checkIntervalMs: number; // default 60000
  persistSettings: boolean;
}

export interface BatteryStats {
  estimatedDrainPerHour: number;
  sessionDuration: number;
  batteryConsumed: number;
  lastUpdated: Date;
}

// ============================================================================
// Default Settings
// ============================================================================

const DEFAULT_SETTINGS: Record<PowerMode, PowerSettings> = {
  normal: {
    mode: 'normal',
    syncInterval: 30000,
    enableBackgroundSync: true,
    enableImageCompression: true,
    compressionQuality: 0.8,
    enableAnimations: true,
    enableAutoUpload: true,
    enableWebSocket: true,
    maxConcurrentUploads: 3,
    enablePreloading: true,
    locationUpdateInterval: 60000,
  },
  low_power: {
    mode: 'low_power',
    syncInterval: 120000,
    enableBackgroundSync: false,
    enableImageCompression: true,
    compressionQuality: 0.6,
    enableAnimations: false,
    enableAutoUpload: false,
    enableWebSocket: false,
    maxConcurrentUploads: 1,
    enablePreloading: false,
    locationUpdateInterval: 300000,
  },
  ultra_low_power: {
    mode: 'ultra_low_power',
    syncInterval: 300000,
    enableBackgroundSync: false,
    enableImageCompression: true,
    compressionQuality: 0.4,
    enableAnimations: false,
    enableAutoUpload: false,
    enableWebSocket: false,
    maxConcurrentUploads: 1,
    enablePreloading: false,
    locationUpdateInterval: 600000,
  },
  charging: {
    mode: 'charging',
    syncInterval: 15000,
    enableBackgroundSync: true,
    enableImageCompression: true,
    compressionQuality: 0.9,
    enableAnimations: true,
    enableAutoUpload: true,
    enableWebSocket: true,
    maxConcurrentUploads: 5,
    enablePreloading: true,
    locationUpdateInterval: 30000,
  },
};

const DEFAULT_CONFIG: BatteryConfig = {
  lowBatteryThreshold: 0.2,
  ultraLowBatteryThreshold: 0.1,
  checkIntervalMs: 60000,
  persistSettings: true,
};

const STORAGE_KEY = '@ghatana/flashit-battery_optimizer';

// ============================================================================
// Battery Optimizer Service
// ============================================================================

/**
 * BatteryOptimizerService manages power-aware app behavior
 */
class BatteryOptimizerService {
  private static instance: BatteryOptimizerService | null = null;
  
  private config: BatteryConfig;
  private currentSettings: PowerSettings;
  private currentStatus: BatteryStatus | null = null;
  private listeners: Set<(settings: PowerSettings) => void> = new Set();
  private batterySubscription: Battery.Subscription | null = null;
  private checkInterval: NodeJS.Timeout | null = null;
  
  private sessionStartTime: number = Date.now();
  private sessionStartBattery: number = 1;
  private customOverrides: Partial<PowerSettings> = {};

  private constructor(config: Partial<BatteryConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.currentSettings = DEFAULT_SETTINGS.normal;
  }

  /**
   * Get singleton instance
   */
  static getInstance(config?: Partial<BatteryConfig>): BatteryOptimizerService {
    if (!this.instance) {
      this.instance = new BatteryOptimizerService(config);
    }
    return this.instance;
  }

  /**
   * Initialize the battery optimizer
   */
  async initialize(): Promise<void> {
    // Load persisted settings
    if (this.config.persistSettings) {
      await this.loadPersistedOverrides();
    }

    // Get initial battery status
    await this.updateBatteryStatus();

    // Subscribe to battery state changes
    this.batterySubscription = Battery.addBatteryStateListener(async ({ batteryState }) => {
      await this.updateBatteryStatus();
    });

    // Start periodic checks
    this.checkInterval = setInterval(async () => {
      await this.updateBatteryStatus();
    }, this.config.checkIntervalMs);

    // Record session start
    this.sessionStartTime = Date.now();
    this.sessionStartBattery = await Battery.getBatteryLevelAsync();
  }

  /**
   * Update battery status and adjust settings
   */
  private async updateBatteryStatus(): Promise<void> {
    try {
      const [level, state, lowPowerMode] = await Promise.all([
        Battery.getBatteryLevelAsync(),
        Battery.getBatteryStateAsync(),
        Battery.isLowPowerModeEnabledAsync(),
      ]);

      const stateMap: Record<number, BatteryState> = {
        [Battery.BatteryState.UNKNOWN]: 'unknown',
        [Battery.BatteryState.CHARGING]: 'charging',
        [Battery.BatteryState.UNPLUGGED]: 'unplugged',
        [Battery.BatteryState.FULL]: 'full',
      };

      this.currentStatus = {
        level,
        state: stateMap[state] || 'unknown',
        lowPowerMode,
        isCharging: state === Battery.BatteryState.CHARGING || state === Battery.BatteryState.FULL,
      };

      // Determine power mode
      const newMode = this.determinePowerMode(this.currentStatus);
      
      // Update settings if mode changed
      if (this.currentSettings.mode !== newMode) {
        this.updateSettings(newMode);
      }
    } catch (error) {
      console.warn('Failed to get battery status:', error);
    }
  }

  /**
   * Determine the appropriate power mode
   */
  private determinePowerMode(status: BatteryStatus): PowerMode {
    // Charging always gets charging mode
    if (status.isCharging) {
      return 'charging';
    }

    // System low power mode triggers low power
    if (status.lowPowerMode) {
      return 'low_power';
    }

    // Ultra low battery
    if (status.level <= this.config.ultraLowBatteryThreshold) {
      return 'ultra_low_power';
    }

    // Low battery
    if (status.level <= this.config.lowBatteryThreshold) {
      return 'low_power';
    }

    return 'normal';
  }

  /**
   * Update current settings
   */
  private updateSettings(mode: PowerMode): void {
    const baseSettings = DEFAULT_SETTINGS[mode];
    this.currentSettings = { ...baseSettings, ...this.customOverrides };

    // Notify listeners
    this.notifyListeners();

    // Persist if needed
    if (this.config.persistSettings) {
      this.persistOverrides();
    }
  }

  /**
   * Notify all listeners of settings change
   */
  private notifyListeners(): void {
    for (const listener of this.listeners) {
      try {
        listener(this.currentSettings);
      } catch (error) {
        console.error('Battery optimizer listener error:', error);
      }
    }
  }

  /**
   * Get current battery status
   */
  getBatteryStatus(): BatteryStatus | null {
    return this.currentStatus;
  }

  /**
   * Get current power settings
   */
  getSettings(): PowerSettings {
    return { ...this.currentSettings };
  }

  /**
   * Get current power mode
   */
  getCurrentMode(): PowerMode {
    return this.currentSettings.mode;
  }

  /**
   * Subscribe to settings changes
   */
  subscribe(listener: (settings: PowerSettings) => void): () => void {
    this.listeners.add(listener);
    // Immediately call with current settings
    listener(this.currentSettings);
    
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Override specific settings (persisted)
   */
  setOverride<K extends keyof PowerSettings>(key: K, value: PowerSettings[K]): void {
    this.customOverrides[key] = value;
    this.currentSettings[key] = value;
    this.notifyListeners();
    
    if (this.config.persistSettings) {
      this.persistOverrides();
    }
  }

  /**
   * Clear an override
   */
  clearOverride(key: keyof PowerSettings): void {
    delete this.customOverrides[key];
    const baseSettings = DEFAULT_SETTINGS[this.currentSettings.mode];
    (this.currentSettings as Record<string, unknown>)[key] = baseSettings[key];
    this.notifyListeners();
    
    if (this.config.persistSettings) {
      this.persistOverrides();
    }
  }

  /**
   * Clear all overrides
   */
  clearAllOverrides(): void {
    this.customOverrides = {};
    const mode = this.currentSettings.mode;
    this.currentSettings = { ...DEFAULT_SETTINGS[mode] };
    this.notifyListeners();
    
    if (this.config.persistSettings) {
      this.persistOverrides();
    }
  }

  /**
   * Get battery statistics
   */
  async getStats(): Promise<BatteryStats> {
    const currentLevel = await Battery.getBatteryLevelAsync();
    const sessionDuration = (Date.now() - this.sessionStartTime) / 1000 / 60; // minutes
    const batteryConsumed = this.sessionStartBattery - currentLevel;
    const estimatedDrainPerHour = sessionDuration > 0
      ? (batteryConsumed / sessionDuration) * 60 * 100 // percentage per hour
      : 0;

    return {
      estimatedDrainPerHour: Math.round(estimatedDrainPerHour * 10) / 10,
      sessionDuration: Math.round(sessionDuration),
      batteryConsumed: Math.round(batteryConsumed * 100),
      lastUpdated: new Date(),
    };
  }

  /**
   * Check if a feature should be enabled
   */
  shouldEnableFeature(feature: keyof PowerSettings): boolean {
    const value = this.currentSettings[feature];
    return typeof value === 'boolean' ? value : true;
  }

  /**
   * Get value for a specific setting
   */
  getSetting<K extends keyof PowerSettings>(key: K): PowerSettings[K] {
    return this.currentSettings[key];
  }

  /**
   * Force a specific power mode (for testing/debugging)
   */
  forceMode(mode: PowerMode): void {
    this.updateSettings(mode);
  }

  /**
   * Reset to automatic mode detection
   */
  async resetToAutomatic(): Promise<void> {
    await this.updateBatteryStatus();
  }

  /**
   * Persist overrides to storage
   */
  private async persistOverrides(): Promise<void> {
    try {
      await AsyncStorage.setItem(
        STORAGE_KEY,
        JSON.stringify(this.customOverrides)
      );
    } catch (error) {
      console.warn('Failed to persist battery overrides:', error);
    }
  }

  /**
   * Load persisted overrides
   */
  private async loadPersistedOverrides(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem(STORAGE_KEY);
      if (stored) {
        this.customOverrides = JSON.parse(stored);
      }
    } catch (error) {
      console.warn('Failed to load battery overrides:', error);
    }
  }

  /**
   * Cleanup resources
   */
  destroy(): void {
    if (this.batterySubscription) {
      this.batterySubscription.remove();
    }
    if (this.checkInterval) {
      clearInterval(this.checkInterval);
    }
    this.listeners.clear();
    BatteryOptimizerService.instance = null;
  }
}

/**
 * Get the battery optimizer instance
 */
export function getBatteryOptimizer(config?: Partial<BatteryConfig>): BatteryOptimizerService {
  return BatteryOptimizerService.getInstance(config);
}

/**
 * React hook for battery-aware settings
 */
export function useBatterySettings(): {
  settings: PowerSettings;
  status: BatteryStatus | null;
  mode: PowerMode;
} {
  const [state, setState] = React.useState<{
    settings: PowerSettings;
    status: BatteryStatus | null;
    mode: PowerMode;
  }>({
    settings: DEFAULT_SETTINGS.normal,
    status: null,
    mode: 'normal',
  });

  React.useEffect(() => {
    const optimizer = getBatteryOptimizer();
    
    const unsubscribe = optimizer.subscribe((settings) => {
      setState({
        settings,
        status: optimizer.getBatteryStatus(),
        mode: settings.mode,
      });
    });

    return unsubscribe;
  }, []);

  return state;
}

import React from 'react';

export default BatteryOptimizerService;
