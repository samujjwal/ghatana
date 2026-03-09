/**
 * Plugin Performance Metrics Component
 *
 * Displays detailed performance metrics for a plugin including:
 * - Execution time trends
 * - Memory usage patterns
 * - Throughput statistics
 * - Error rates
 * - Resource utilization
 *
 * @doc.type component
 * @doc.purpose Plugin performance monitoring dashboard
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Activity,
  Clock,
  Cpu,
  HardDrive,
  TrendingUp,
  TrendingDown,
  AlertCircle,
  BarChart3,
  Zap,
} from 'lucide-react';
import { cn, cardStyles, textStyles } from '../../lib/theme';

export interface PluginPerformanceMetrics {
  pluginId: string;
  timestamp: string;
  executionTime: {
    avg: number;
    min: number;
    max: number;
    p50: number;
    p95: number;
    p99: number;
  };
  memory: {
    used: number;
    peak: number;
    average: number;
  };
  throughput: {
    requestsPerSecond: number;
    recordsProcessed: number;
    bytesProcessed: number;
  };
  errors: {
    count: number;
    rate: number;
    types: Record<string, number>;
  };
  cpu: {
    usage: number;
    cores: number;
  };
}

export interface PluginPerformanceMetricsProps {
  /** Plugin ID */
  pluginId: string;
  /** Time range for metrics (1h, 24h, 7d, 30d) */
  timeRange?: '1h' | '24h' | '7d' | '30d';
  /** Auto-refresh interval in milliseconds */
  refreshInterval?: number;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Plugin Performance Metrics Component
 */
export function PluginPerformanceMetrics({
  pluginId,
  timeRange = '24h',
  refreshInterval = 60000,
  className,
}: PluginPerformanceMetricsProps): React.ReactElement {
  const [selectedRange, setSelectedRange] = useState(timeRange);

  // Fetch performance metrics
  const { data: metrics, isLoading } = useQuery({
    queryKey: ['plugins', pluginId, 'performance', selectedRange],
    queryFn: async (): Promise<PluginPerformanceMetrics> => {
      // Mock data - in production, this would fetch from API
      await new Promise((resolve) => setTimeout(resolve, 500));
      
      return {
        pluginId,
        timestamp: new Date().toISOString(),
        executionTime: {
          avg: 245,
          min: 120,
          max: 1850,
          p50: 220,
          p95: 580,
          p99: 920,
        },
        memory: {
          used: 128,
          peak: 256,
          average: 145,
        },
        throughput: {
          requestsPerSecond: 42,
          recordsProcessed: 158420,
          bytesProcessed: 2.4 * 1024 * 1024 * 1024, // 2.4 GB
        },
        errors: {
          count: 12,
          rate: 0.008,
          types: {
            'ConnectionTimeout': 5,
            'ValidationError': 4,
            'RateLimitExceeded': 3,
          },
        },
        cpu: {
          usage: 34,
          cores: 4,
        },
      };
    },
    refetchInterval: refreshInterval,
  });

  // Fetch historical data for charts
  const { data: history } = useQuery({
    queryKey: ['plugins', pluginId, 'performance', 'history', selectedRange],
    queryFn: async () => {
      await new Promise((resolve) => setTimeout(resolve, 300));
      
      // Generate mock historical data
      const points = selectedRange === '1h' ? 12 : selectedRange === '24h' ? 24 : 30;
      return Array.from({ length: points }, (_, i) => ({
        timestamp: new Date(Date.now() - (points - i) * 3600000).toISOString(),
        executionTime: 200 + Math.random() * 200,
        memory: 120 + Math.random() * 80,
        requests: 30 + Math.random() * 30,
        errors: Math.random() < 0.3 ? Math.floor(Math.random() * 5) : 0,
      }));
    },
    refetchInterval: refreshInterval,
  });

  if (isLoading || !metrics) {
    return (
      <div className={cn(cardStyles.base, 'animate-pulse', className)}>
        <div className="h-64 bg-gray-200 dark:bg-gray-700 rounded" />
      </div>
    );
  }

  // Calculate trends
  const avgChange = history && history.length > 1
    ? ((history[history.length - 1].executionTime - history[0].executionTime) / history[0].executionTime) * 100
    : 0;

  const formatBytes = (bytes: number): string => {
    if (bytes >= 1024 * 1024 * 1024) {
      return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
    }
    if (bytes >= 1024 * 1024) {
      return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
    }
    return `${(bytes / 1024).toFixed(2)} KB`;
  };

  const formatNumber = (num: number): string => {
    return new Intl.NumberFormat().format(num);
  };

  return (
    <div className={cn('space-y-6', className)}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-blue-600" />
          <h3 className={textStyles.h4}>Performance Metrics</h3>
        </div>

        {/* Time Range Selector */}
        <div className="flex items-center gap-2">
          {(['1h', '24h', '7d', '30d'] as const).map((range) => (
            <button
              key={range}
              onClick={() => setSelectedRange(range)}
              className={cn(
                'px-3 py-1 text-sm rounded transition-colors',
                selectedRange === range
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
              )}
            >
              {range}
            </button>
          ))}
        </div>
      </div>

      {/* Key Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Avg Execution Time */}
        <div className={cardStyles.base}>
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <Clock className="h-4 w-4 text-blue-600" />
              <span className="text-sm text-gray-600 dark:text-gray-400">Avg Response</span>
            </div>
            {avgChange !== 0 && (
              <div className={cn(
                'flex items-center gap-1 text-xs',
                avgChange > 0 ? 'text-red-600' : 'text-green-600'
              )}>
                {avgChange > 0 ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                {Math.abs(avgChange).toFixed(1)}%
              </div>
            )}
          </div>
          <div className="text-2xl font-bold">{metrics.executionTime.avg}ms</div>
          <div className="text-xs text-gray-500 mt-1">
            P95: {metrics.executionTime.p95}ms • P99: {metrics.executionTime.p99}ms
          </div>
        </div>

        {/* Memory Usage */}
        <div className={cardStyles.base}>
          <div className="flex items-center gap-2 mb-2">
            <HardDrive className="h-4 w-4 text-purple-600" />
            <span className="text-sm text-gray-600 dark:text-gray-400">Memory</span>
          </div>
          <div className="text-2xl font-bold">{metrics.memory.used} MB</div>
          <div className="text-xs text-gray-500 mt-1">
            Peak: {metrics.memory.peak} MB • Avg: {metrics.memory.average} MB
          </div>
          {/* Memory bar */}
          <div className="mt-2 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              className="h-full bg-purple-600 transition-all"
              style={{ width: `${(metrics.memory.used / metrics.memory.peak) * 100}%` }}
            />
          </div>
        </div>

        {/* Throughput */}
        <div className={cardStyles.base}>
          <div className="flex items-center gap-2 mb-2">
            <Zap className="h-4 w-4 text-yellow-600" />
            <span className="text-sm text-gray-600 dark:text-gray-400">Throughput</span>
          </div>
          <div className="text-2xl font-bold">{metrics.throughput.requestsPerSecond} req/s</div>
          <div className="text-xs text-gray-500 mt-1">
            {formatNumber(metrics.throughput.recordsProcessed)} records
          </div>
          <div className="text-xs text-gray-500">
            {formatBytes(metrics.throughput.bytesProcessed)} processed
          </div>
        </div>

        {/* Error Rate */}
        <div className={cardStyles.base}>
          <div className="flex items-center gap-2 mb-2">
            <AlertCircle className={cn(
              'h-4 w-4',
              metrics.errors.rate > 0.05 ? 'text-red-600' : metrics.errors.rate > 0.01 ? 'text-yellow-600' : 'text-green-600'
            )} />
            <span className="text-sm text-gray-600 dark:text-gray-400">Error Rate</span>
          </div>
          <div className={cn(
            'text-2xl font-bold',
            metrics.errors.rate > 0.05 ? 'text-red-600' : metrics.errors.rate > 0.01 ? 'text-yellow-600' : 'text-green-600'
          )}>
            {(metrics.errors.rate * 100).toFixed(2)}%
          </div>
          <div className="text-xs text-gray-500 mt-1">
            {metrics.errors.count} errors in period
          </div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Execution Time Chart */}
        <div className={cardStyles.base}>
          <div className="flex items-center gap-2 mb-4">
            <BarChart3 className="h-4 w-4 text-blue-600" />
            <h4 className="font-semibold text-sm">Execution Time Trend</h4>
          </div>
          {history && history.length > 0 ? (
            <div className="h-48 flex items-end gap-1">
              {history.map((point, idx) => {
                const maxExecTime = Math.max(...history.map(p => p.executionTime));
                const height = (point.executionTime / maxExecTime) * 100;
                return (
                  <div
                    key={idx}
                    className="flex-1 bg-blue-500 rounded-t hover:bg-blue-600 transition-colors cursor-pointer relative group"
                    style={{ height: `${height}%` }}
                    title={`${point.executionTime.toFixed(0)}ms`}
                  >
                    <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 hidden group-hover:block bg-gray-900 text-white text-xs px-2 py-1 rounded whitespace-nowrap">
                      {point.executionTime.toFixed(0)}ms
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="h-48 flex items-center justify-center text-gray-400">
              No data available
            </div>
          )}
          <div className="flex justify-between text-xs text-gray-500 mt-2">
            <span>Min: {metrics.executionTime.min}ms</span>
            <span>Avg: {metrics.executionTime.avg}ms</span>
            <span>Max: {metrics.executionTime.max}ms</span>
          </div>
        </div>

        {/* Memory Usage Chart */}
        <div className={cardStyles.base}>
          <div className="flex items-center gap-2 mb-4">
            <BarChart3 className="h-4 w-4 text-purple-600" />
            <h4 className="font-semibold text-sm">Memory Usage Trend</h4>
          </div>
          {history && history.length > 0 ? (
            <div className="h-48 flex items-end gap-1">
              {history.map((point, idx) => {
                const maxMemory = Math.max(...history.map(p => p.memory));
                const height = (point.memory / maxMemory) * 100;
                return (
                  <div
                    key={idx}
                    className="flex-1 bg-purple-500 rounded-t hover:bg-purple-600 transition-colors cursor-pointer relative group"
                    style={{ height: `${height}%` }}
                    title={`${point.memory.toFixed(0)} MB`}
                  >
                    <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 hidden group-hover:block bg-gray-900 text-white text-xs px-2 py-1 rounded whitespace-nowrap">
                      {point.memory.toFixed(0)} MB
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="h-48 flex items-center justify-center text-gray-400">
              No data available
            </div>
          )}
        </div>
      </div>

      {/* Detailed Stats */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* CPU Usage */}
        <div className={cardStyles.base}>
          <div className="flex items-center gap-2 mb-4">
            <Cpu className="h-4 w-4 text-green-600" />
            <h4 className="font-semibold text-sm">CPU Utilization</h4>
          </div>
          <div className="space-y-3">
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span>Usage</span>
                <span className="font-semibold">{metrics.cpu.usage}%</span>
              </div>
              <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                <div
                  className={cn(
                    'h-full transition-all',
                    metrics.cpu.usage > 80 ? 'bg-red-500' : metrics.cpu.usage > 60 ? 'bg-yellow-500' : 'bg-green-500'
                  )}
                  style={{ width: `${metrics.cpu.usage}%` }}
                />
              </div>
            </div>
            <div className="text-xs text-gray-600 dark:text-gray-400">
              Cores allocated: {metrics.cpu.cores}
            </div>
          </div>
        </div>

        {/* Error Types */}
        <div className={cardStyles.base}>
          <div className="flex items-center gap-2 mb-4">
            <AlertCircle className="h-4 w-4 text-red-600" />
            <h4 className="font-semibold text-sm">Error Breakdown</h4>
          </div>
          {Object.keys(metrics.errors.types).length > 0 ? (
            <div className="space-y-2">
              {Object.entries(metrics.errors.types)
                .sort(([, a], [, b]) => b - a)
                .map(([type, count]) => (
                  <div key={type} className="flex items-center justify-between text-sm">
                    <span className="text-gray-700 dark:text-gray-300">{type}</span>
                    <span className="font-semibold text-red-600">{count}</span>
                  </div>
                ))}
            </div>
          ) : (
            <div className="text-sm text-gray-600 dark:text-gray-400">No errors in period</div>
          )}
        </div>
      </div>
    </div>
  );
}
