/**
 * @fileoverview Data Retention Settings Component
 *
 * UI for configuring data retention policy settings including
 * retention periods, cleanup options, and storage statistics.
 *
 * @module ui/components/settings
 * @since 2.0.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import {
  dataRetentionPolicy,
  formatRetentionPeriod,
  formatBytes,
  type RetentionConfig,
  type RetentionMetadata,
  type RetentionPeriod,
} from '../../../analytics/storage/DataRetentionPolicy';

interface StorageStats {
  totalEntries: number;
  totalSize: number;
  oldestEntry: number;
  newestEntry: number;
  compressedEntries: number;
}

export const DataRetentionSettings: React.FC = () => {
  const [config, setConfig] = useState<RetentionConfig | null>(null);
  const [metadata, setMetadata] = useState<RetentionMetadata | null>(null);
  const [stats, setStats] = useState<StorageStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRunningCleanup, setIsRunningCleanup] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load initial data
  useEffect(() => {
    const loadData = async () => {
      try {
        setIsLoading(true);
        const [currentConfig, currentMetadata, currentStats] = await Promise.all([
          Promise.resolve(dataRetentionPolicy.getConfig()),
          Promise.resolve(dataRetentionPolicy.getMetadata()),
          dataRetentionPolicy.getStorageStats(),
        ]);
        
        setConfig(currentConfig);
        setMetadata(currentMetadata);
        setStats(currentStats);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load retention settings');
      } finally {
        setIsLoading(false);
      }
    };

    loadData();
  }, []);

  // Handle config changes
  const handleConfigChange = useCallback(async (updates: Partial<RetentionConfig>) => {
    if (!config) return;

    try {
      const newConfig = { ...config, ...updates };
      await dataRetentionPolicy.updateConfig(updates);
      setConfig(newConfig);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update settings');
    }
  }, [config]);

  // Manual cleanup
  const handleManualCleanup = useCallback(async () => {
    try {
      setIsRunningCleanup(true);
      setError(null);
      
      const result = await dataRetentionPolicy.manualCleanup();
      setMetadata(result);
      
      // Refresh stats after cleanup
      const newStats = await dataRetentionPolicy.getStorageStats();
      setStats(newStats);
      
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cleanup failed');
    } finally {
      setIsRunningCleanup(false);
    }
  }, []);

  const formatDate = (timestamp: number): string => {
    if (!timestamp) return 'Never';
    return new Date(timestamp).toLocaleString();
  };

  const calculateRetentionCutoff = (): string => {
    if (!config) return '';
    
    const periods: Record<RetentionPeriod, number> = {
      '7d': 7 * 24 * 60 * 60 * 1000,
      '30d': 30 * 24 * 60 * 60 * 1000,
      '90d': 90 * 24 * 60 * 60 * 1000,
      '1y': 365 * 24 * 60 * 60 * 1000,
    };
    
    const cutoff = Date.now() - periods[config.defaultPeriod];
    return formatDate(cutoff);
  };

  if (isLoading) {
    return (
      <Card className="p-6">
        <div className="flex items-center justify-center">
          <div className="text-sm text-slate-500">Loading retention settings...</div>
        </div>
      </Card>
    );
  }

  if (!config || !metadata || !stats) {
    return (
      <Card className="p-6">
        <div className="text-sm text-rose-600">
          {error || 'Failed to load retention settings'}
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      {/* Configuration */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold text-slate-900 mb-4">
          Data Retention Policy
        </h3>
        
        {error && (
          <div className="mb-4 p-3 bg-rose-50 border border-rose-200 rounded text-sm text-rose-700">
            {error}
          </div>
        )}

        <div className="space-y-4">
          {/* Retention Period */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Keep data for
            </label>
            <select
              value={config.defaultPeriod}
              onChange={(e) => handleConfigChange({ defaultPeriod: e.target.value as RetentionPeriod })}
              className="w-full px-3 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="7d">7 days</option>
              <option value="30d">30 days</option>
              <option value="90d">90 days</option>
              <option value="1y">1 year</option>
            </select>
            <p className="mt-1 text-xs text-slate-500">
              Data older than {formatRetentionPeriod(config.defaultPeriod)} will be automatically deleted
              (cutoff: {calculateRetentionCutoff()})
            </p>
          </div>

          {/* Max Entries */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Maximum entries
            </label>
            <input
              type="number"
              min="100"
              max="10000"
              step="100"
              value={config.maxEntries}
              onChange={(e) => handleConfigChange({ maxEntries: parseInt(e.target.value) || 1000 })}
              className="w-full px-3 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="mt-1 text-xs text-slate-500">
              Keep at most this many metric entries to prevent storage overflow
            </p>
          </div>

          {/* Preserve Options */}
          <div className="space-y-2">
            <label className="flex items-center">
              <input
                type="checkbox"
                checked={config.preserveAlerts}
                onChange={(e) => handleConfigChange({ preserveAlerts: e.target.checked })}
                className="mr-2 h-4 w-4 text-blue-600 focus:ring-blue-500 border-slate-300 rounded"
              />
              <span className="text-sm text-slate-700">Preserve entries with performance alerts</span>
            </label>
            
            <label className="flex items-center">
              <input
                type="checkbox"
                checked={config.preserveBaselines}
                onChange={(e) => handleConfigChange({ preserveBaselines: e.target.checked })}
                className="mr-2 h-4 w-4 text-blue-600 focus:ring-blue-500 border-slate-300 rounded"
              />
              <span className="text-sm text-slate-700">Preserve baseline measurements (first of day)</span>
            </label>
          </div>

          {/* Cleanup Interval */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-2">
              Cleanup interval
            </label>
            <select
              value={config.cleanupInterval}
              onChange={(e) => handleConfigChange({ cleanupInterval: parseInt(e.target.value) })}
              className="w-full px-3 py-2 border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value={1}>Every hour</option>
              <option value={6}>Every 6 hours</option>
              <option value={24}>Daily</option>
              <option value={168}>Weekly</option>
            </select>
            <p className="mt-1 text-xs text-slate-500">
              How often to automatically run cleanup
            </p>
          </div>
        </div>
      </Card>

      {/* Statistics */}
      <Card className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-slate-900">
            Storage Statistics
          </h3>
          <button
            onClick={handleManualCleanup}
            disabled={isRunningCleanup}
            className="px-4 py-2 bg-blue-500 text-white text-sm rounded-md hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isRunningCleanup ? 'Running...' : 'Run Cleanup Now'}
          </button>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="text-center">
            <div className="text-2xl font-bold text-slate-900">
              {stats.totalEntries.toLocaleString()}
            </div>
            <div className="text-sm text-slate-500">Total Entries</div>
          </div>
          
          <div className="text-center">
            <div className="text-2xl font-bold text-slate-900">
              {formatBytes(stats.totalSize)}
            </div>
            <div className="text-sm text-slate-500">Storage Used</div>
          </div>
          
          <div className="text-center">
            <div className="text-2xl font-bold text-slate-900">
              {stats.compressedEntries.toLocaleString()}
            </div>
            <div className="text-sm text-slate-500">Compressed</div>
          </div>
          
          <div className="text-center">
            <div className="text-2xl font-bold text-slate-900">
              {Math.round(((stats.totalEntries - stats.compressedEntries) / Math.max(stats.totalEntries, 1)) * 100)}%
            </div>
            <div className="text-sm text-slate-500">Uncompressed</div>
          </div>
        </div>

        <div className="mt-6 space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-slate-600">Oldest Entry:</span>
            <span className="text-slate-900">{formatDate(stats.oldestEntry)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-600">Newest Entry:</span>
            <span className="text-slate-900">{formatDate(stats.newestEntry)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-600">Last Cleanup:</span>
            <span className="text-slate-900">{formatDate(metadata.lastCleanup)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-600">Total Cleaned:</span>
            <span className="text-slate-900">{metadata.totalCleaned.toLocaleString()} entries</span>
          </div>
        </div>
      </Card>

      {/* Help Text */}
      <Card className="p-4 bg-blue-50 border-blue-200">
        <h4 className="font-medium text-blue-900 mb-2">About Data Retention</h4>
        <div className="text-sm text-blue-800 space-y-1">
          <p>• Data cleanup runs automatically based on your configured interval</p>
          <p>• Important entries with alerts or baseline measurements can be preserved</p>
          <p>• Older entries are compressed to save space before being deleted</p>
          <p>• You can manually run cleanup anytime using the button above</p>
        </div>
      </Card>
    </div>
  );
};

export default DataRetentionSettings;