/**
 * PluginSystemAdapter
 * Bridge between PluginManager and device-health monitoring system
 *
 * This adapter coordinates metric collection from monitor plugins, alert delivery
 * via notification plugins, and metric persistence through storage plugins.
 */

import {
  CorePluginManager,
  IDataCollector,
  INotification,
  IStorage,
} from '@ghatana/dcmaar-plugin-abstractions';

/**
 * Metric data structure from a monitor plugin
 */
export interface PluginMetricData {
  pluginId: string;
  pluginName: string;
  timestamp: number;
  metrics: Record<string, unknown>;
  status: 'healthy' | 'warning' | 'error';
}

/**
 * Alert data structure for notification plugins
 */
export interface PluginAlert {
  title: string;
  message: string;
  severity: 'info' | 'warning' | 'critical';
  pluginId?: string;
  timestamp?: number;
}

/**
 * PluginSystemAdapter - Unified interface to plugin ecosystem
 * Handles coordination between plugins and monitoring system
 */
export class PluginSystemAdapter {
  /**
   * Create adapter instance
   * @param pluginManager - Core PluginManager instance
   */
  constructor(private pluginManager: CorePluginManager) {}

  /**
   * Collect metrics from all registered monitor plugins
   * @returns Array of metric data from all plugins
   */
  async getAllMetrics(): Promise<PluginMetricData[]> {
    const metrics: PluginMetricData[] = [];
    
    try {
      // Get all installed plugins
      const pluginInstances = this.pluginManager.getAllPlugins();

      for (const plugin of pluginInstances) {
        if (!plugin) continue;
        
        const pluginId = (plugin as Record<string, unknown>).id as string;
        if (!pluginId) continue;

        try {
          // Check if plugin is a data collector
          if (this.isDataCollector(plugin)) {
            const collectorPlugin = plugin as unknown as IDataCollector;
            
            // Collect metrics from available sources
            const sources = await collectorPlugin.getSources();
            const collectedMetrics: Record<string, unknown> = {};

            for (const source of sources) {
              try {
                const sourceData = await collectorPlugin.collect(source);
                collectedMetrics[source] = sourceData;
              } catch (err) {
                // Log error for individual source but continue with other sources
                console.warn(
                  `Failed to collect from ${source} in plugin ${pluginId}:`,
                  err
                );
              }
            }

            // Add to metrics array
            metrics.push({
              pluginId,
              pluginName: pluginId,
              timestamp: Date.now(),
              metrics: collectedMetrics,
              status: this.evaluatePluginHealth(collectedMetrics),
            });
          }
        } catch (err) {
          console.error(`Error collecting metrics from plugin ${pluginId}:`, err);
        }
      }
    } catch (err) {
      console.error('Error in getAllMetrics:', err);
    }

    return metrics;
  }

  /**
   * Collect metrics from a specific plugin
   * @param pluginId - ID of the plugin
   * @returns Metric data from the plugin, or null if not found
   */
  async getMetric(pluginId: string): Promise<PluginMetricData | null> {
    try {
      const plugin = this.pluginManager.getPlugin(pluginId);
      if (!plugin || !this.isDataCollector(plugin)) {
        return null;
      }

      const collectorPlugin = plugin as unknown as IDataCollector;
      const sources = await collectorPlugin.getSources();
      const collectedMetrics: Record<string, unknown> = {};

      for (const source of sources) {
        try {
          const sourceData = await collectorPlugin.collect(source);
          collectedMetrics[source] = sourceData;
        } catch (err) {
          console.warn(
            `Failed to collect from ${source} in plugin ${pluginId}:`,
            err
          );
        }
      }

      return {
        pluginId,
        pluginName: pluginId,
        timestamp: Date.now(),
        metrics: collectedMetrics,
        status: this.evaluatePluginHealth(collectedMetrics),
      };
    } catch (err) {
      console.error(`Error getting metric for plugin ${pluginId}:`, err);
      return null;
    }
  }

  /**
   * Send alert via registered notification plugins
   * @param alert - Alert data to send
   * @returns Array of plugin IDs that successfully sent the alert
   */
  async notify(alert: PluginAlert): Promise<string[]> {
    const successfulPlugins: string[] = [];

    try {
      const pluginInstances = this.pluginManager.getAllPlugins();

      for (const plugin of pluginInstances) {
        if (!plugin) continue;
        
        const pluginId = (plugin as Record<string, unknown>).id as string;
        if (!pluginId) continue;

        try {
          if (!this.isNotificationPlugin(plugin)) {
            continue;
          }

          const notificationPlugin = plugin as unknown as INotification;

          // Check if notification service is available
          const isAvailable = await notificationPlugin.isAvailable();
          if (!isAvailable) {
            console.warn(`Notification plugin ${pluginId} is not available`);
            continue;
          }

          // Send notification
          const success = await notificationPlugin.send(
            pluginId,
            alert.title,
            alert.message,
            alert.severity
          );

          if (success) {
            successfulPlugins.push(pluginId);
          }
        } catch (err) {
          console.error(
            `Error sending notification via plugin ${pluginId}:`,
            err
          );
        }
      }
    } catch (err) {
      console.error('Error in notify:', err);
    }

    return successfulPlugins;
  }

  /**
   * Store metric data via registered storage plugins
   * @param key - Storage key
   * @param value - Data to store
   * @returns Array of plugin IDs that successfully stored the data
   */
  async store(key: string, value: unknown): Promise<string[]> {
    const successfulPlugins: string[] = [];

    try {
      const pluginInstances = this.pluginManager.getAllPlugins();

      for (const plugin of pluginInstances) {
        if (!plugin) continue;
        
        const pluginId = (plugin as Record<string, unknown>).id as string;
        if (!pluginId) continue;

        try {
          if (!this.isStoragePlugin(plugin)) {
            continue;
          }

          const storagePlugin = plugin as unknown as IStorage;

          // Store data with TTL of 30 days
          const ttl = 30 * 24 * 60 * 60 * 1000;
          await storagePlugin.set(`metric:${key}`, value, ttl);
          successfulPlugins.push(pluginId);
        } catch (err) {
          console.error(
            `Error storing metric in plugin ${pluginId}:`,
            err
          );
        }
      }
    } catch (err) {
      console.error('Error in store:', err);
    }

    return successfulPlugins;
  }

  /**
   * Get comprehensive health report from all plugins
   * @returns Object containing health status of all plugins
   */
  async getPluginHealthReport(): Promise<Record<string, unknown>> {
    const report: Record<string, unknown> = {
      timestamp: Date.now(),
      plugins: {},
    };

    try {
      const pluginInstances = this.pluginManager.getAllPlugins();

      for (const plugin of pluginInstances) {
        if (!plugin) continue;
        
        const pluginId = (plugin as Record<string, unknown>).id as string;
        if (!pluginId) continue;

        try {
          const pluginInfo: Record<string, unknown> = {
            id: pluginId,
            type: this.getPluginType(plugin),
            status: 'unknown',
          };

          // Get status from manager
          const state = this.pluginManager.getPluginState(pluginId);
          pluginInfo.status = state || 'unknown';
          pluginInfo.running = this.pluginManager.isPluginRunning(pluginId);

          // For data collectors, check if they're accessible
          if (this.isDataCollector(plugin)) {
            const collectorPlugin = plugin as unknown as IDataCollector;
            try {
              const sources = await collectorPlugin.getSources();
              pluginInfo.sources = sources;
              pluginInfo.sourceCount = sources.length;
              pluginInfo.accessible = sources.length > 0;
            } catch (err) {
              pluginInfo.accessible = false;
              pluginInfo.error = String(err);
            }
          }

          // For notification plugins, check availability
          if (this.isNotificationPlugin(plugin)) {
            const notificationPlugin = plugin as unknown as INotification;
            try {
              pluginInfo.available = await notificationPlugin.isAvailable();
            } catch (err) {
              pluginInfo.available = false;
              pluginInfo.error = String(err);
            }
          }

          // For storage plugins, check if they're operational
          if (this.isStoragePlugin(plugin)) {
            const storagePlugin = plugin as unknown as IStorage;
            try {
              // Try a test write/read/delete to verify functionality
              const testKey = `health-check-${Date.now()}`;
              await storagePlugin.set(testKey, { test: true }, 1000);
              const exists = await storagePlugin.exists(testKey);
              await storagePlugin.delete(testKey);
              pluginInfo.operational = exists;
            } catch (err) {
              pluginInfo.operational = false;
              pluginInfo.error = String(err);
            }
          }

          (report.plugins as Record<string, unknown>)[pluginId] = pluginInfo;
        } catch (err) {
          console.error(`Error getting health for plugin ${pluginId}:`, err);
        }
      }
    } catch (err) {
      console.error('Error in getPluginHealthReport:', err);
    }

    return report;
  }

  /**
   * Evaluate plugin health based on collected metrics
   */
  private evaluatePluginHealth(
    metrics: Record<string, unknown>
  ): 'healthy' | 'warning' | 'error' {
    if (!metrics || Object.keys(metrics).length === 0) {
      return 'error';
    }

    // Check for error indicators in metrics
    for (const value of Object.values(metrics)) {
      if (value === null || value === undefined) {
        return 'warning';
      }

      if (typeof value === 'object') {
        const obj = value as Record<string, unknown>;
        if (obj.error || obj.status === 'error') {
          return 'error';
        }
      }
    }

    return 'healthy';
  }

  /**
   * Check if plugin implements DataCollector interface
   */
  private isDataCollector(plugin: unknown): boolean {
    return (
      typeof plugin === 'object' &&
      plugin !== null &&
      typeof (plugin as Record<string, unknown>).collect === 'function'
    );
  }

  /**
   * Check if plugin implements Notification interface
   */
  private isNotificationPlugin(plugin: unknown): boolean {
    return (
      typeof plugin === 'object' &&
      plugin !== null &&
      typeof (plugin as Record<string, unknown>).send === 'function'
    );
  }

  /**
   * Check if plugin implements Storage interface
   */
  private isStoragePlugin(plugin: unknown): boolean {
    return (
      typeof plugin === 'object' &&
      plugin !== null &&
      typeof (plugin as Record<string, unknown>).set === 'function' &&
      typeof (plugin as Record<string, unknown>).get === 'function'
    );
  }

  /**
   * Determine plugin type based on interfaces it implements
   */
  private getPluginType(plugin: unknown): string {
    if (this.isDataCollector(plugin)) return 'DataCollector';
    if (this.isNotificationPlugin(plugin)) return 'Notification';
    if (this.isStoragePlugin(plugin)) return 'Storage';
    return 'Unknown';
  }
}

export default PluginSystemAdapter;
