/**
 * @ghatana/yappc-ide - Memory Manager Component
 * 
 * Advanced memory monitoring and optimization for large workspaces.
 * Provides real-time memory tracking, cleanup, and optimization.
 * 
 * @doc.type component
 * @doc.purpose Memory management and optimization for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { InteractiveButton } from './MicroInteractions';

/**
 * Memory usage statistics
 */
export interface MemoryStats {
  used: number;
  total: number;
  limit: number;
  percentage: number;
  trend: 'increasing' | 'decreasing' | 'stable';
  pressure: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * Memory optimization strategy
 */
export interface OptimizationStrategy {
  id: string;
  name: string;
  description: string;
  impact: 'low' | 'medium' | 'high';
  cost: 'low' | 'medium' | 'high';
  enabled: boolean;
  action: () => Promise<void>;
}

/**
 * Memory manager props
 */
export interface MemoryManagerProps {
  className?: string;
  enableAutoOptimization?: boolean;
  enableMonitoring?: boolean;
  optimizationThreshold?: number;
  monitoringInterval?: number;
  onMemoryPressure?: (stats: MemoryStats) => void;
  onOptimizationComplete?: (strategy: OptimizationStrategy, freedMemory: number) => void;
}

/**
 * Memory monitor class
 */
class MemoryMonitor {
  private history: Array<{ timestamp: number; usage: number }> = [];
  private maxHistorySize = 100;

  record(): number {
    if (!performance.memory) return 0;

    const usage = performance.memory.usedJSHeapSize;
    this.history.push({
      timestamp: Date.now(),
      usage,
    });

    if (this.history.length > this.maxHistorySize) {
      this.history.shift();
    }

    return usage;
  }

  getTrend(): 'increasing' | 'decreasing' | 'stable' {
    if (this.history.length < 10) return 'stable';

    const recent = this.history.slice(-10);
    const first = recent[0].usage;
    const last = recent[recent.length - 1].usage;

    const change = (last - first) / first;

    if (change > 0.05) return 'increasing';
    if (change < -0.05) return 'decreasing';
    return 'stable';
  }

  getAverage(): number {
    if (this.history.length === 0) return 0;
    const sum = this.history.reduce((acc, entry) => acc + entry.usage, 0);
    return sum / this.history.length;
  }

  clear(): void {
    this.history = [];
  }
}

/**
 * Memory Manager Component
 */
export const MemoryManager: React.FC<MemoryManagerProps> = ({
  className = '',
  enableAutoOptimization = true,
  enableMonitoring = true,
  optimizationThreshold = 80,
  monitoringInterval = 5000,
  onMemoryPressure,
  onOptimizationComplete,
}) => {
  const [stats, setStats] = useState<MemoryStats>({
    used: 0,
    total: 0,
    limit: 0,
    percentage: 0,
    trend: 'stable',
    pressure: 'low',
  });

  const [isOptimizing, setIsOptimizing] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const [optimizationHistory, setOptimizationHistory] = useState<Array<{
    timestamp: number;
    strategy: string;
    freedMemory: number;
  }>>([]);

  const monitorRef = useRef<MemoryMonitor>(new MemoryMonitor());
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  // Calculate memory statistics
  const calculateStats = useCallback((): MemoryStats => {
    if (!performance.memory) {
      return {
        used: 0,
        total: 0,
        limit: 0,
        percentage: 0,
        trend: 'stable',
        pressure: 'low',
      };
    }

    const used = performance.memory.usedJSHeapSize;
    const total = performance.memory.totalJSHeapSize;
    const limit = performance.memory.jsHeapSizeLimit;
    const percentage = (used / limit) * 100;

    let pressure: 'low' | 'medium' | 'high' | 'critical' = 'low';
    if (percentage > 90) pressure = 'critical';
    else if (percentage > 75) pressure = 'high';
    else if (percentage > 50) pressure = 'medium';

    const trend = monitorRef.current.getTrend();

    return {
      used,
      total,
      limit,
      percentage,
      trend,
      pressure,
    };
  }, []);

  // Optimization strategies
  const optimizationStrategies: OptimizationStrategy[] = [
    {
      id: 'gc',
      name: 'Garbage Collection',
      description: 'Force garbage collection to free unused memory',
      impact: 'medium' as const,
      cost: 'low' as const,
      enabled: true,
      action: async () => {
        if (window.gc) {
          window.gc();
        }
      },
    },
    {
      id: 'clear-cache',
      name: 'Clear Component Cache',
      description: 'Clear cached component data and unused resources',
      impact: 'high' as const,
      cost: 'medium' as const,
      enabled: true,
      action: async () => {
        // Clear component caches
        if (typeof window !== 'undefined' && 'caches' in window) {
          const cacheNames = await caches.keys();
          await Promise.all(cacheNames.map(name => caches.delete(name)));
        }
      },
    },
    {
      id: 'unload-inactive',
      name: 'Unload Inactive Components',
      description: 'Unload components that haven\'t been used recently',
      impact: 'high' as const,
      cost: 'high' as const,
      enabled: false,
      action: async () => {
        // This would integrate with component lifecycle management
        // For now, just trigger a cleanup event
        window.dispatchEvent(new CustomEvent('memory-cleanup', {
          detail: { type: 'unload-inactive' }
        }));
      },
    },
    {
      id: 'compress-data',
      name: 'Compress Large Data',
      description: 'Compress large datasets and unused files',
      impact: 'medium' as const,
      cost: 'medium' as const,
      enabled: false,
      action: async () => {
        // Trigger data compression
        window.dispatchEvent(new CustomEvent('memory-cleanup', {
          detail: { type: 'compress-data' }
        }));
      },
    },
  ];

  // Run optimization strategy
  const runOptimization = useCallback(async (strategy: OptimizationStrategy) => {
    setIsOptimizing(true);
    const beforeStats = calculateStats();

    try {
      await strategy.action();

      // Wait a moment for changes to take effect
      await new Promise(resolve => setTimeout(resolve, 1000));

      const afterStats = calculateStats();
      const freedMemory = beforeStats.used - afterStats.used;

      setOptimizationHistory(prev => [...prev, {
        timestamp: Date.now(),
        strategy: strategy.name,
        freedMemory,
      }]);

      onOptimizationComplete?.(strategy, freedMemory);
    } catch (error) {
      console.error(`Optimization failed for ${strategy.name}:`, error);
    } finally {
      setIsOptimizing(false);
    }
  }, [calculateStats, onOptimizationComplete]);

  // Auto-optimization
  const runAutoOptimization = useCallback(async () => {
    if (!enableAutoOptimization || isOptimizing) return;

    const currentStats = calculateStats();

    if (currentStats.percentage > optimizationThreshold) {
      // Run strategies in order of impact/cost ratio
      const strategies = optimizationStrategies
        .filter(s => s.enabled)
        .sort((a, b) => {
          const scoreA = a.impact === 'high' ? 3 : a.impact === 'medium' ? 2 : 1;
          const scoreB = b.impact === 'high' ? 3 : b.impact === 'medium' ? 2 : 1;
          const costA = a.cost === 'low' ? 3 : a.cost === 'medium' ? 2 : 1;
          const costB = b.cost === 'low' ? 3 : b.cost === 'medium' ? 2 : 1;
          return (scoreA / costA) - (scoreB / costB);
        });

      for (const strategy of strategies) {
        if (currentStats.percentage <= optimizationThreshold * 0.8) break;
        await runOptimization(strategy);
      }
    }
  }, [enableAutoOptimization, isOptimizing, optimizationThreshold, calculateStats, runOptimization]);

  // Memory monitoring
  useEffect(() => {
    if (!enableMonitoring) return;

    const monitor = () => {
      const currentStats = calculateStats();
      setStats(currentStats);

      // Record usage for trend analysis
      monitorRef.current.record();

      // Trigger memory pressure callback
      if (currentStats.pressure === 'high' || currentStats.pressure === 'critical') {
        onMemoryPressure?.(currentStats);
      }

      // Auto-optimize if needed
      if (enableAutoOptimization) {
        runAutoOptimization();
      }
    };

    monitor(); // Initial monitoring
    intervalRef.current = setInterval(monitor, monitoringInterval);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [enableMonitoring, monitoringInterval, calculateStats, onMemoryPressure, enableAutoOptimization, runAutoOptimization]);

  // Format memory size
  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // Get pressure color
  const getPressureColor = (pressure: string) => {
    switch (pressure) {
      case 'critical': return 'text-red-600 dark:text-red-400';
      case 'high': return 'text-orange-600 dark:text-orange-400';
      case 'medium': return 'text-yellow-600 dark:text-yellow-400';
      default: return 'text-green-600 dark:text-green-400';
    }
  };

  // Get pressure background
  const getPressureBg = (pressure: string) => {
    switch (pressure) {
      case 'critical': return 'bg-red-100 dark:bg-red-900/20';
      case 'high': return 'bg-orange-100 dark:bg-orange-900/20';
      case 'medium': return 'bg-yellow-100 dark:bg-yellow-900/20';
      default: return 'bg-green-100 dark:bg-green-900/20';
    }
  };

  if (!enableMonitoring) {
    return null;
  }

  return (
    <div className={`p-4 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
          🧠 Memory Manager
        </h3>
        <div className="flex items-center gap-2">
          <InteractiveButton
            variant="secondary"
            size="sm"
            onClick={() => setShowDetails(!showDetails)}
          >
            {showDetails ? 'Hide' : 'Show'} Details
          </InteractiveButton>
          <InteractiveButton
            variant="primary"
            size="sm"
            onClick={runAutoOptimization}
            disabled={isOptimizing}
          >
            {isOptimizing ? 'Optimizing...' : 'Optimize Now'}
          </InteractiveButton>
        </div>
      </div>

      {/* Memory usage overview */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
        <div className={`p-3 rounded-lg ${getPressureBg(stats.pressure)}`}>
          <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Memory Usage</div>
          <div className={`text-xl font-bold ${getPressureColor(stats.pressure)}`}>
            {stats.percentage.toFixed(1)}%
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400">
            {formatBytes(stats.used)} / {formatBytes(stats.limit)}
          </div>
        </div>

        <div className="p-3 bg-gray-100 dark:bg-gray-800 rounded-lg">
          <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Trend</div>
          <div className="text-xl font-bold text-gray-900 dark:text-gray-100">
            {stats.trend === 'increasing' ? '📈' : stats.trend === 'decreasing' ? '📉' : '➡️'}
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400 capitalize">
            {stats.trend}
          </div>
        </div>

        <div className="p-3 bg-gray-100 dark:bg-gray-800 rounded-lg">
          <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">Pressure</div>
          <div className="text-xl font-bold text-gray-900 dark:text-gray-100 capitalize">
            {stats.pressure}
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400">
            {stats.pressure === 'critical' ? 'Immediate action required' :
              stats.pressure === 'high' ? 'Optimization recommended' :
                stats.pressure === 'medium' ? 'Monitor closely' : 'Normal'}
          </div>
        </div>
      </div>

      {/* Progress bar */}
      <div className="mb-4">
        <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
          <div
            className={`h-2 rounded-full transition-all duration-500 ${stats.pressure === 'critical' ? 'bg-red-500' :
              stats.pressure === 'high' ? 'bg-orange-500' :
                stats.pressure === 'medium' ? 'bg-yellow-500' : 'bg-green-500'
              }`}
            style={{ width: `${Math.min(stats.percentage, 100)}%` }}
          />
        </div>
      </div>

      {/* Detailed information */}
      {showDetails && (
        <div className="space-y-4">
          {/* Optimization strategies */}
          <div>
            <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">
              Optimization Strategies
            </h4>
            <div className="space-y-2">
              {optimizationStrategies.map(strategy => (
                <div
                  key={strategy.id}
                  className="flex items-center justify-between p-2 bg-gray-50 dark:bg-gray-800 rounded"
                >
                  <div className="flex-1">
                    <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {strategy.name}
                    </div>
                    <div className="text-xs text-gray-500 dark:text-gray-400">
                      {strategy.description}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`px-2 py-1 text-xs rounded ${strategy.impact === 'high' ? 'bg-red-100 text-red-700 dark:bg-red-900/20 dark:text-red-400' :
                      strategy.impact === 'medium' ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/20 dark:text-yellow-400' :
                        'bg-green-100 text-green-700 dark:bg-green-900/20 dark:text-green-400'
                      }`}>
                      {strategy.impact}
                    </span>
                    <InteractiveButton
                      variant="secondary"
                      size="sm"
                      onClick={() => runOptimization(strategy)}
                      disabled={isOptimizing || !strategy.enabled}
                    >
                      Run
                    </InteractiveButton>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Optimization history */}
          {optimizationHistory.length > 0 && (
            <div>
              <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">
                Recent Optimizations
              </h4>
              <div className="space-y-1">
                {optimizationHistory.slice(-5).reverse().map((entry, index) => (
                  <div key={index} className="text-xs text-gray-600 dark:text-gray-400">
                    {new Date(entry.timestamp).toLocaleTimeString()} - {entry.strategy}:
                    freed {formatBytes(entry.freedMemory)}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default MemoryManager;
