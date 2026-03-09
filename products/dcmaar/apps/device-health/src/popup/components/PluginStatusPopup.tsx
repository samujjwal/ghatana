/**
 * @file PluginStatusPopup Component
 *
 * Compact popup showing quick plugin metrics, enable/disable toggles,
 * and quick access to settings. Designed for extension popup display.
 *
 * @module popup/components/PluginStatusPopup
 * @version 1.0.0
 */

import clsx from 'clsx';
import React, { useEffect, useState } from 'react';

import { usePluginConfig } from '../../ui/hooks/usePluginConfig';
import { usePluginMetrics } from '../../ui/hooks/usePluginMetrics';

/**
 * Quick metric summary for popup display.
 */
interface QuickMetric {
  label: string;
  value: string | number;
  status: 'normal' | 'warning' | 'critical';
  icon: string;
}

/**
 * Props for PluginStatusPopup component.
 */
export interface PluginStatusPopupProps {
  /**
   * Callback when settings button is clicked.
   */
  onSettingsClick?: () => void;

  /**
   * Callback when enable/disable toggle is changed.
   */
  onToggleChange?: (enabled: boolean) => void;

  /**
   * Whether plugins are currently enabled.
   */
  isEnabled?: boolean;

  /**
   * Custom class name.
   */
  className?: string;
}

/**
 * Determines status level based on metric value and threshold.
 */
const getMetricStatus = (value: number, warningThreshold: number, criticalThreshold: number) => {
  if (value >= criticalThreshold) return 'critical';
  if (value >= warningThreshold) return 'warning';
  return 'normal';
};

/**
 * PluginStatusPopup Component
 *
 * Compact popup UI for extension. Shows:
 * - Quick metrics summary (CPU, Memory, Battery)
 * - Enable/disable toggle
 * - Status indicator (connected/disconnected)
 * - Quick link to settings
 * - Dashboard link
 *
 * @example
 * ```tsx
 * <PluginStatusPopup
 *   onSettingsClick={() => browser.runtime.openOptionsPage()}
 *   onToggleChange={(enabled) => console.log('Enabled:', enabled)}
 *   isEnabled={true}
 * />
 * ```
 */
export const PluginStatusPopup: React.FC<PluginStatusPopupProps> = ({
  onSettingsClick,
  onToggleChange,
  isEnabled = true,
  className,
}) => {
  const { metrics: pluginMetrics, isLoading } = usePluginMetrics();
  const { config } = usePluginConfig();
  const [isConnected, setIsConnected] = useState(true);

  // Check connection status on mount
  useEffect(() => {
    // Simulate connection check
    setIsConnected(true);
  }, []);

  // Derive quick metrics from plugin metrics
  const quickMetrics: QuickMetric[] = [];

  if (pluginMetrics.cpu) {
    const cpu = pluginMetrics.cpu;
    quickMetrics.push({
      label: 'CPU',
      value: `${cpu.usage.toFixed(0)}%`,
      status: getMetricStatus(cpu.usage, 70, 85),
      icon: '⚙️',
    });

    if (cpu.temperature !== undefined) {
      quickMetrics.push({
        label: 'Temp',
        value: `${cpu.temperature.toFixed(0)}°C`,
        status: getMetricStatus(cpu.temperature, 75, 85),
        icon: '🌡️',
      });
    }
  }

  if (pluginMetrics.memory) {
    const mem = pluginMetrics.memory;
    quickMetrics.push({
      label: 'Memory',
      value: `${mem.usagePercent.toFixed(0)}%`,
      status: getMetricStatus(mem.usagePercent, 70, 85),
      icon: '💾',
    });
  }

  if (pluginMetrics.battery) {
    const battery = pluginMetrics.battery;
    quickMetrics.push({
      label: 'Battery',
      value: `${battery.levelPercent}%`,
      status: getMetricStatus(battery.levelPercent, 30, 15),
      icon: '🔋',
    });
  }

  // Overall status
  const overallStatus = quickMetrics.some((m) => m.status === 'critical')
    ? 'critical'
    : quickMetrics.some((m) => m.status === 'warning')
      ? 'warning'
      : 'normal';

  // Get status color
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'critical':
        return 'text-red-600 bg-red-50 border-red-200';
      case 'warning':
        return 'text-amber-600 bg-amber-50 border-amber-200';
      default:
        return 'text-green-600 bg-green-50 border-green-200';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'critical':
        return '🚨';
      case 'warning':
        return '⚠️';
      default:
        return '✓';
    }
  };

  return (
    <div className={clsx('w-80 bg-white rounded-lg shadow-lg border border-gray-200', className)}>
      {/* Header */}
      <div className="p-4 border-b border-gray-200 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className={clsx(
            'w-2.5 h-2.5 rounded-full',
            isConnected ? 'bg-green-500' : 'bg-gray-400'
          )} />
          <h2 className="text-sm font-semibold text-gray-900">Plugin Monitor</h2>
        </div>
        <span className={clsx(
          'text-xs font-medium px-2 py-1 rounded-full',
          getStatusColor(overallStatus)
        )}>
          {getStatusIcon(overallStatus)} {overallStatus.charAt(0).toUpperCase() + overallStatus.slice(1)}
        </span>
      </div>

      {/* Quick Metrics Grid */}
      <div className="p-4 space-y-2">
        {isLoading ? (
          <div className="space-y-2">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-10 bg-gray-100 rounded animate-pulse" />
            ))}
          </div>
        ) : quickMetrics.length > 0 ? (
          <div className="grid grid-cols-2 gap-2">
            {quickMetrics.map((metric) => (
              <div
                key={metric.label}
                className={clsx(
                  'p-2.5 rounded-lg border',
                  metric.status === 'critical'
                    ? 'bg-red-50 border-red-200'
                    : metric.status === 'warning'
                      ? 'bg-amber-50 border-amber-200'
                      : 'bg-green-50 border-green-200'
                )}
              >
                <div className="flex items-start justify-between mb-1">
                  <span className="text-lg">{metric.icon}</span>
                  <span className={clsx(
                    'text-[10px] font-bold',
                    metric.status === 'critical'
                      ? 'text-red-600'
                      : metric.status === 'warning'
                        ? 'text-amber-600'
                        : 'text-green-600'
                  )}>
                    {metric.value}
                  </span>
                </div>
                <p className="text-[10px] font-medium text-gray-600">{metric.label}</p>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-500 text-center py-3">No metrics available</p>
        )}
      </div>

      {/* Divider */}
      <div className="h-px bg-gray-200" />

      {/* Toggle and Controls */}
      <div className="p-4 space-y-3">
        {/* Enable/Disable Toggle */}
        <div className="flex items-center justify-between">
          <label className="text-sm font-medium text-gray-700">Enable Monitoring</label>
          <button
            onClick={() => onToggleChange?.(!isEnabled)}
            className={clsx(
              'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
              isEnabled ? 'bg-blue-600' : 'bg-gray-300'
            )}
          >
            <span
              className={clsx(
                'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                isEnabled ? 'translate-x-6' : 'translate-x-1'
              )}
            />
          </button>
        </div>

        {/* Connection Status */}
        <div className="flex items-center gap-2 text-xs">
          <div className={clsx(
            'w-2 h-2 rounded-full',
            isConnected ? 'bg-green-500' : 'bg-red-500'
          )} />
          <span className="text-gray-600">
            {isConnected ? 'Connected' : 'Disconnected'}
          </span>
        </div>

        {/* Config Summary */}
        {config && (
          <div className="text-xs text-gray-600 space-y-1">
            <div>CPU: {config.cpu?.enabled ? '✓' : '✗'} ({config.cpu?.intervalMs || 1000}ms)</div>
            <div>Memory: {config.memory?.enabled ? '✓' : '✗'} ({config.memory?.intervalMs || 1000}ms)</div>
            <div>Battery: {config.battery?.enabled ? '✓' : '✗'} ({config.battery?.intervalMs || 10000}ms)</div>
          </div>
        )}
      </div>

      {/* Divider */}
      <div className="h-px bg-gray-200" />

      {/* Action Buttons */}
      <div className="p-4 flex gap-2">
        <button
          onClick={() => {
            // Open dashboard in new tab
            try {
              const browserAPI = (window as unknown as Record<string, unknown>).browser as
                | { tabs?: { create?: (opts: unknown) => Promise<unknown> } }
                | undefined;
              if (browserAPI?.tabs?.create) {
                void browserAPI.tabs.create({ url: 'options.html' });
              }
            } catch (error) {
              console.error('Failed to open dashboard:', error);
            }
            window.close();
          }}
          className="flex-1 px-3 py-2 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 transition-colors"
        >
          Dashboard
        </button>
        <button
          onClick={() => {
            onSettingsClick?.();
            window.close();
          }}
          className="px-3 py-2 bg-gray-100 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-200 transition-colors"
        >
          ⚙️
        </button>
      </div>

      {/* Footer Info */}
      <div className="px-4 py-2 bg-gray-50 text-[10px] text-gray-600 border-t border-gray-200 rounded-b-lg">
        <p>Updates every {config?.cpu?.intervalMs || 1000}ms</p>
      </div>
    </div>
  );
};

export default PluginStatusPopup;
