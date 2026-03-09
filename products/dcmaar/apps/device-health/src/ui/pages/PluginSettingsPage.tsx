import clsx from "clsx";
import React, { useState, useEffect } from "react";

import { usePluginConfig } from "../hooks/usePluginConfig";

/**
 * Configuration thresholds and ranges for plugin monitoring.
 * Defines all tunable parameters for CPU, Memory, and Battery monitors.
 */
interface MonitorConfig {
  // CPU Configuration
  cpu: {
    enabled: boolean;
    intervalMs: number; // 500-5000ms
    temperatureThresholdC?: number; // Alert if exceeds
    throttleAlert: boolean; // Alert when throttled
  };
  // Memory Configuration
  memory: {
    enabled: boolean;
    intervalMs: number; // 500-5000ms
    usageThresholdPercent: number; // 0-100, alert if exceeds
    gcThresholdMs?: number; // Alert if GC takes longer
  };
  // Battery Configuration
  battery: {
    enabled: boolean;
    intervalMs: number; // 5000-60000ms
    lowBatteryPercent: number; // Alert threshold
    healthThreshold: "good" | "fair" | "poor"; // Alert if health below this
  };
}

/**
 * Default configuration values.
 * Used as fallback if browser storage has no saved config.
 */
const DEFAULT_CONFIG: MonitorConfig = {
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
};

/**
 * PluginSettingsPage - Configuration UI for monitoring intervals and thresholds.
 *
 * Provides sliders and inputs for tuning:
 * - Polling intervals (independent per monitor)
 * - Alert thresholds (CPU temp, Memory %, Battery %)
 * - Enable/disable toggles
 * - Reset to defaults
 *
 * All settings persist to browser.storage.local via usePluginConfig hook.
 *
 * @returns JSX component rendering settings form
 */
export const PluginSettingsPage: React.FC = () => {
  const { config, isLoading, error: _configError, saveConfig } = usePluginConfig();
  const [localConfig, setLocalConfig] = useState<MonitorConfig | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  // Load config from hook into local state
  useEffect(() => {
    if (config) {
      setLocalConfig(config);
    }
  }, [config]);

  // Handle save button click
  const handleSave = async () => {
    if (!localConfig) return;

    setIsSaving(true);
    setSaveMessage(null);

    try {
      await saveConfig(localConfig);
      setSaveMessage("✓ Settings saved successfully");
      setTimeout(() => setSaveMessage(null), 3000);
    } catch (err) {
      setSaveMessage(`✗ Error saving settings: ${String(err)}`);
    } finally {
      setIsSaving(false);
    }
  };

  // Handle reset to defaults
  const handleReset = () => {
    if (window.confirm("Reset all settings to defaults?")) {
      setLocalConfig(DEFAULT_CONFIG);
    }
  };

  // Update CPU config
  const updateCPU = (key: string, value: unknown) => {
    if (!localConfig) return;
    setLocalConfig({
      ...localConfig,
      cpu: { ...localConfig.cpu, [key]: value },
    });
  };

  // Update Memory config
  const updateMemory = (key: string, value: unknown) => {
    if (!localConfig) return;
    setLocalConfig({
      ...localConfig,
      memory: { ...localConfig.memory, [key]: value },
    });
  };

  // Update Battery config
  const updateBattery = (key: string, value: unknown) => {
    if (!localConfig) return;
    setLocalConfig({
      ...localConfig,
      battery: { ...localConfig.battery, [key]: value },
    });
  };

  if (isLoading || !localConfig) {
    return (
      <div className="settings-page p-6 max-w-2xl mx-auto">
        <div className="text-center text-gray-500">Loading settings...</div>
      </div>
    );
  }

  return (
    <div className="settings-page p-6 max-w-2xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">Plugin Settings</h1>
        <p className="text-gray-600 mt-2">
          Configure monitoring intervals and alert thresholds
        </p>
      </div>

      {/* Error Alert */}
      {(_configError as string | null) && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
          <p className="text-red-900">Error: {_configError}</p>
        </div>
      )}

      {/* Save Message */}
      {saveMessage && (
        <div
          className={clsx(
            "mb-6 p-4 rounded-lg",
            saveMessage.startsWith("✓")
              ? "bg-green-50 border border-green-200 text-green-900"
              : "bg-red-50 border border-red-200 text-red-900"
          )}
        >
          {saveMessage}
        </div>
      )}

      {/* CPU Settings */}
      <div className="mb-8 p-6 bg-blue-50 border border-blue-200 rounded-lg">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-semibold text-gray-900">
            CPU Monitoring
          </h2>
          <label className="flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={localConfig.cpu.enabled}
              onChange={(e) => updateCPU("enabled", e.target.checked)}
              className="w-5 h-5 text-blue-600 rounded"
            />
            <span className="ml-2 text-sm text-gray-700">
              {localConfig.cpu.enabled ? "Enabled" : "Disabled"}
            </span>
          </label>
        </div>

        {localConfig.cpu.enabled && (
          <>
            {/* Polling Interval */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Polling Interval: {localConfig.cpu.intervalMs}ms
              </label>
              <input
                type="range"
                min="500"
                max="5000"
                step="100"
                value={localConfig.cpu.intervalMs}
                onChange={(e) => updateCPU("intervalMs", Number(e.target.value))}
                className="w-full h-2 bg-blue-200 rounded-lg appearance-none cursor-pointer"
              />
              <p className="text-xs text-gray-500 mt-1">
                Fast: 500-2000ms | Normal: 2000-3500ms | Slow: 3500-5000ms
              </p>
            </div>

            {/* Temperature Threshold */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Temperature Alert Threshold:{" "}
                {localConfig.cpu.temperatureThresholdC}°C
              </label>
              <input
                type="range"
                min="60"
                max="100"
                step="5"
                value={localConfig.cpu.temperatureThresholdC || 85}
                onChange={(e) =>
                  updateCPU("temperatureThresholdC", Number(e.target.value))
                }
                className="w-full h-2 bg-red-200 rounded-lg appearance-none cursor-pointer"
              />
              <p className="text-xs text-gray-500 mt-1">
                Alert if CPU exceeds this temperature
              </p>
            </div>

            {/* Throttle Alert Toggle */}
            <div>
              <label className="flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={localConfig.cpu.throttleAlert}
                  onChange={(e) => updateCPU("throttleAlert", e.target.checked)}
                  className="w-4 h-4 text-blue-600 rounded"
                />
                <span className="ml-2 text-sm text-gray-700">
                  Alert when CPU is throttled
                </span>
              </label>
            </div>
          </>
        )}
      </div>

      {/* Memory Settings */}
      <div className="mb-8 p-6 bg-purple-50 border border-purple-200 rounded-lg">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-semibold text-gray-900">
            Memory Monitoring
          </h2>
          <label className="flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={localConfig.memory.enabled}
              onChange={(e) => updateMemory("enabled", e.target.checked)}
              className="w-5 h-5 text-purple-600 rounded"
            />
            <span className="ml-2 text-sm text-gray-700">
              {localConfig.memory.enabled ? "Enabled" : "Disabled"}
            </span>
          </label>
        </div>

        {localConfig.memory.enabled && (
          <>
            {/* Polling Interval */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Polling Interval: {localConfig.memory.intervalMs}ms
              </label>
              <input
                type="range"
                min="500"
                max="5000"
                step="100"
                value={localConfig.memory.intervalMs}
                onChange={(e) =>
                  updateMemory("intervalMs", Number(e.target.value))
                }
                className="w-full h-2 bg-purple-200 rounded-lg appearance-none cursor-pointer"
              />
              <p className="text-xs text-gray-500 mt-1">
                Fast: 500-2000ms | Normal: 2000-3500ms | Slow: 3500-5000ms
              </p>
            </div>

            {/* Usage Threshold */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Usage Alert Threshold: {localConfig.memory.usageThresholdPercent}%
              </label>
              <input
                type="range"
                min="50"
                max="95"
                step="5"
                value={localConfig.memory.usageThresholdPercent}
                onChange={(e) =>
                  updateMemory("usageThresholdPercent", Number(e.target.value))
                }
                className="w-full h-2 bg-purple-200 rounded-lg appearance-none cursor-pointer"
              />
              <p className="text-xs text-gray-500 mt-1">
                Alert if memory usage exceeds this percentage
              </p>
            </div>

            {/* GC Threshold */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                GC Activity Threshold: {localConfig.memory.gcThresholdMs}ms
              </label>
              <input
                type="range"
                min="100"
                max="1000"
                step="50"
                value={localConfig.memory.gcThresholdMs || 500}
                onChange={(e) =>
                  updateMemory("gcThresholdMs", Number(e.target.value))
                }
                className="w-full h-2 bg-purple-200 rounded-lg appearance-none cursor-pointer"
              />
              <p className="text-xs text-gray-500 mt-1">
                Alert if garbage collection takes longer
              </p>
            </div>
          </>
        )}
      </div>

      {/* Battery Settings */}
      <div className="mb-8 p-6 bg-yellow-50 border border-yellow-200 rounded-lg">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-semibold text-gray-900">
            Battery Monitoring
          </h2>
          <label className="flex items-center cursor-pointer">
            <input
              type="checkbox"
              checked={localConfig.battery.enabled}
              onChange={(e) => updateBattery("enabled", e.target.checked)}
              className="w-5 h-5 text-yellow-600 rounded"
            />
            <span className="ml-2 text-sm text-gray-700">
              {localConfig.battery.enabled ? "Enabled" : "Disabled"}
            </span>
          </label>
        </div>

        {localConfig.battery.enabled && (
          <>
            {/* Polling Interval */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Polling Interval: {localConfig.battery.intervalMs}ms
              </label>
              <input
                type="range"
                min="5000"
                max="60000"
                step="1000"
                value={localConfig.battery.intervalMs}
                onChange={(e) =>
                  updateBattery("intervalMs", Number(e.target.value))
                }
                className="w-full h-2 bg-yellow-200 rounded-lg appearance-none cursor-pointer"
              />
              <p className="text-xs text-gray-500 mt-1">
                Slow poll (battery changes slowly): 5-15s | Normal: 15-30s |
                Fast: 30-60s
              </p>
            </div>

            {/* Low Battery Threshold */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Low Battery Alert: {localConfig.battery.lowBatteryPercent}%
              </label>
              <input
                type="range"
                min="5"
                max="50"
                step="5"
                value={localConfig.battery.lowBatteryPercent}
                onChange={(e) =>
                  updateBattery("lowBatteryPercent", Number(e.target.value))
                }
                className="w-full h-2 bg-red-200 rounded-lg appearance-none cursor-pointer"
              />
              <p className="text-xs text-gray-500 mt-1">
                Alert when battery level drops below this percentage
              </p>
            </div>

            {/* Health Threshold */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Health Alert Threshold: {localConfig.battery.healthThreshold}
              </label>
              <div className="space-y-2">
                <label className="flex items-center">
                  <input
                    type="radio"
                    name="health"
                    value="good"
                    checked={localConfig.battery.healthThreshold === "good"}
                    onChange={(e) =>
                      updateBattery(
                        "healthThreshold",
                        e.target.value as "good" | "fair" | "poor"
                      )
                    }
                    className="w-4 h-4 text-yellow-600"
                  />
                  <span className="ml-2 text-sm text-gray-700">
                    Good (alert only if poor)
                  </span>
                </label>
                <label className="flex items-center">
                  <input
                    type="radio"
                    name="health"
                    value="fair"
                    checked={localConfig.battery.healthThreshold === "fair"}
                    onChange={(e) =>
                      updateBattery(
                        "healthThreshold",
                        e.target.value as "good" | "fair" | "poor"
                      )
                    }
                    className="w-4 h-4 text-yellow-600"
                  />
                  <span className="ml-2 text-sm text-gray-700">
                    Fair (alert if fair or poor)
                  </span>
                </label>
                <label className="flex items-center">
                  <input
                    type="radio"
                    name="health"
                    value="poor"
                    checked={localConfig.battery.healthThreshold === "poor"}
                    onChange={(e) =>
                      updateBattery(
                        "healthThreshold",
                        e.target.value as "good" | "fair" | "poor"
                      )
                    }
                    className="w-4 h-4 text-yellow-600"
                  />
                  <span className="ml-2 text-sm text-gray-700">
                    Poor (alert only if poor, more sensitive)
                  </span>
                </label>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Action Buttons */}
      <div className="flex gap-4 mt-8">
        <button
          onClick={handleSave}
          disabled={isSaving}
          className={clsx(
            "px-6 py-3 rounded-lg font-medium transition-colors",
            isSaving
              ? "bg-gray-300 text-gray-500 cursor-not-allowed"
              : "bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800"
          )}
        >
          {isSaving ? "Saving..." : "Save Settings"}
        </button>

        <button
          onClick={handleReset}
          className="px-6 py-3 rounded-lg font-medium bg-gray-300 text-gray-700 hover:bg-gray-400 active:bg-gray-500 transition-colors"
        >
          Reset to Defaults
        </button>

        <a
          href="/dashboard"
          className="px-6 py-3 rounded-lg font-medium bg-gray-100 text-gray-700 hover:bg-gray-200 active:bg-gray-300 transition-colors inline-flex items-center"
        >
          Back to Dashboard
        </a>
      </div>

      {/* Info Box */}
      <div className="mt-8 p-4 bg-blue-100 border border-blue-300 rounded-lg">
        <h3 className="font-semibold text-blue-900 mb-2">💡 Quick Tips</h3>
        <ul className="text-sm text-blue-800 space-y-1">
          <li>• Lower intervals = higher accuracy but more CPU usage</li>
          <li>• Higher thresholds = fewer alerts but less early warning</li>
          <li>• Battery polling is slower by design (battery changes slowly)</li>
          <li>• Disable monitors you don't need to save battery</li>
          <li>• Changes save immediately when you click "Save Settings"</li>
        </ul>
      </div>
    </div>
  );
};

export default PluginSettingsPage;
