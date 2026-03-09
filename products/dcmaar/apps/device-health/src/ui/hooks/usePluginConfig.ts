import { useState, useEffect } from "react";

/**
 * Configuration interface matching PluginSettingsPage.
 * Represents all tunable parameters for the plugin monitoring system.
 */
interface PluginConfig {
  cpu: {
    enabled: boolean;
    intervalMs: number;
    temperatureThresholdC?: number;
    throttleAlert: boolean;
  };
  memory: {
    enabled: boolean;
    intervalMs: number;
    usageThresholdPercent: number;
    gcThresholdMs?: number;
  };
  battery: {
    enabled: boolean;
    intervalMs: number;
    lowBatteryPercent: number;
    healthThreshold: "good" | "fair" | "poor";
  };
}

/**
 * Default configuration factory.
 * Returns default config if browser storage has no saved value.
 */
const createDefaultConfig = (): PluginConfig => ({
  cpu: {
    enabled: true,
    intervalMs: 1000,
    temperatureThresholdC: 85,
    throttleAlert: true,
  },
  memory: {
    enabled: true,
    intervalMs: 1000,
    usageThresholdPercent: 90,
    gcThresholdMs: 500,
  },
  battery: {
    enabled: true,
    intervalMs: 10000,
    lowBatteryPercent: 20,
    healthThreshold: "fair",
  },
});

/**
 * Return type for usePluginConfig hook.
 * Includes config, loading state, error, and mutation methods.
 */
export interface UsePluginConfigReturn {
  config: PluginConfig | null;
  isLoading: boolean;
  error: unknown; // Can be string | null or Error
  saveConfig: (config: PluginConfig) => Promise<void>;
  updateCPUConfig: (partial: Partial<PluginConfig["cpu"]>) => Promise<void>;
  updateMemoryConfig: (
    partial: Partial<PluginConfig["memory"]>
  ) => Promise<void>;
  updateBatteryConfig: (
    partial: Partial<PluginConfig["battery"]>
  ) => Promise<void>;
}

/**
 * Browser storage key for persisting plugin configuration.
 */
const STORAGE_KEY = "plugin_monitoring_config";

/**
 * usePluginConfig - Hook for managing plugin monitor configuration.
 *
 * Provides:
 * - Load config from browser.storage.local on mount
 * - Save config changes to browser.storage.local
 * - Update individual monitor configs
 * - Default config factory
 * - Error handling
 *
 * @param enabled - Enable/disable hook automatically (default: true)
 * @returns Configuration state and mutation methods
 *
 * @example
 * const { config, saveConfig, updateCPUConfig } = usePluginConfig();
 * // Later...
 * await updateCPUConfig({ intervalMs: 2000 });
 */
export function usePluginConfig(
  enabled: boolean = true
): UsePluginConfigReturn {
  const [config, setConfig] = useState<PluginConfig | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  // Load config from browser storage on mount
  useEffect(() => {
    if (!enabled) {
      setIsLoading(false);
      return;
    }

    const loadConfig = async () => {
      try {
        setIsLoading(true);
        setError(null);

        // Simulate async storage read
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Try to load from browser storage
        let storedConfig: PluginConfig | null = null;

        try {
          // Check if browser.storage API available (Firefox extensions)
          if (typeof window !== "undefined" && (window as unknown as Record<string, unknown>).browser) {
            const browser = (window as unknown as Record<string, unknown>).browser as Record<string, unknown>;
            const storage = (browser as Record<string, unknown>).storage as Record<string, unknown>;
            const local = (storage as Record<string, unknown>).local as Record<string, unknown>;
            const get = (local as Record<string, unknown>).get as (key: string) => Promise<Record<string, unknown>>;
            
            const result = await get(STORAGE_KEY);
            storedConfig = (result as Record<string, unknown>)[STORAGE_KEY] as PluginConfig | undefined || null;
          } else if (typeof localStorage !== "undefined") {
            // Fallback to localStorage for web
            const stored = localStorage.getItem(STORAGE_KEY);
            storedConfig = stored ? JSON.parse(stored) : null;
          }
        } catch (storageErr) {
          console.warn("Failed to load config from storage:", storageErr);
          storedConfig = null;
        }

        // Use stored config or default
        const finalConfig = storedConfig || createDefaultConfig();
        setConfig(finalConfig);
      } catch (err) {
        console.error("Error loading plugin config:", err);
        setError(err);
        // Use default config on error
        setConfig(createDefaultConfig());
      } finally {
        setIsLoading(false);
      }
    };

    loadConfig();
  }, [enabled]);

  /**
   * Save entire config to storage
   */
  const saveConfig = async (newConfig: PluginConfig): Promise<void> => {
    try {
      setError(null);

      // Simulate async storage write
      await new Promise((resolve) => setTimeout(resolve, 100));

      try {
        // Try browser.storage API first (Firefox extensions)
        if (typeof window !== "undefined" && (window as unknown as Record<string, unknown>).browser) {
          const browser = (window as unknown as Record<string, unknown>).browser as Record<string, unknown>;
          const storage = (browser as Record<string, unknown>).storage as Record<string, unknown>;
          const local = (storage as Record<string, unknown>).local as Record<string, unknown>;
          const set = (local as Record<string, unknown>).set as (data: Record<string, unknown>) => Promise<void>;
          
          await set({ [STORAGE_KEY]: newConfig });
        } else if (typeof localStorage !== "undefined") {
          // Fallback to localStorage
          localStorage.setItem(STORAGE_KEY, JSON.stringify(newConfig));
        }
      } catch (storageErr) {
        console.warn("Failed to save config to storage:", storageErr);
      }

      // Update local state
      setConfig(newConfig);
    } catch (err) {
      console.error("Error saving plugin config:", err);
      setError(err);
      throw err;
    }
  };

  /**
   * Update CPU config partially and save
   */
  const updateCPUConfig = async (
    partial: Partial<PluginConfig["cpu"]>
  ): Promise<void> => {
    if (!config) throw new Error("Config not loaded");

    const updated: PluginConfig = {
      ...config,
      cpu: { ...config.cpu, ...partial },
    };

    await saveConfig(updated);
  };

  /**
   * Update Memory config partially and save
   */
  const updateMemoryConfig = async (
    partial: Partial<PluginConfig["memory"]>
  ): Promise<void> => {
    if (!config) throw new Error("Config not loaded");

    const updated: PluginConfig = {
      ...config,
      memory: { ...config.memory, ...partial },
    };

    await saveConfig(updated);
  };

  /**
   * Update Battery config partially and save
   */
  const updateBatteryConfig = async (
    partial: Partial<PluginConfig["battery"]>
  ): Promise<void> => {
    if (!config) throw new Error("Config not loaded");

    const updated: PluginConfig = {
      ...config,
      battery: { ...config.battery, ...partial },
    };

    await saveConfig(updated);
  };

  return {
    config,
    isLoading,
    error,
    saveConfig,
    updateCPUConfig,
    updateMemoryConfig,
    updateBatteryConfig,
  };
}

export type { PluginConfig };
