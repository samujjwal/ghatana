/* eslint-disable max-lines-per-function */
import { useState, useEffect, useCallback, useRef, useMemo } from 'react';

import { PerformanceAnalytics } from './analytics';

import type {
  PerformanceMetric,
  PerformanceTrend,
  PerformanceAlert,
  PerformanceBaseline,
  PerformanceOptions,
  UsePerformanceMonitoringResult,
} from './types';

/**
 * usePerformanceMonitoring
 *
 * Hook for comprehensive performance metrics collection, trend analysis, and historical data management.
 */
export function usePerformanceMonitoring(
  options: PerformanceOptions = {}
): UsePerformanceMonitoringResult {
  const {
    metrics: trackingMetrics = [
      'buildTime',
      'deployTime',
      'testTime',
      'bundleSize',
    ],
    realTimeUpdates = true,
    historicalDays = 30,
    trendAnalysis = true,
    alertThresholds = {},
    samplingInterval = 5000,
  } = options;

  const [metrics, setMetrics] = useState<PerformanceMetric[]>([]);
  const [trends, setTrends] = useState<PerformanceTrend[]>([]);
  const [alerts, setAlerts] = useState<PerformanceAlert[]>([]);
  const [baselines, setBaselines] = useState<PerformanceBaseline[]>([]);
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [isLoading] = useState(false);
  const [error] = useState<string | null>(null);

  const metricsRef = useRef<PerformanceMetric[]>([]);
  const alertIdCounter = useRef(0);

  // WebSocket integration placeholder (simplified)
  const sendMessage = useCallback((_type: string, _data: unknown) => {
    // WebSocket implementation would go here
  }, []);

  const subscribe = useCallback(
    (_channel: string, _handler: (data: unknown) => void) => {
      // WebSocket subscription would go here
    },
    []
  );

  const unsubscribe = useCallback(
    (_channel: string, _handler: (data: unknown) => void) => {
      // WebSocket unsubscription would go here
    },
    []
  );

  // Update metrics ref when state changes
  useEffect(() => {
    metricsRef.current = metrics;
  }, [metrics]);

  // Record a new performance metric
  const recordMetric = useCallback(
    (metricData: Omit<PerformanceMetric, 'id' | 'timestamp'>) => {
      const metric: PerformanceMetric = {
        ...metricData,
        id: `${metricData.value}_${Date.now()}_${Math.random()
          .toString(36)
          .substr(2, 9)}`,
        timestamp: new Date(),
      };

      setMetrics((prev) => {
        const updated = [...prev, metric];
        // Keep only recent metrics within the historical window
        const cutoffDate = new Date(
          Date.now() - historicalDays * 24 * 60 * 60 * 1000
        );
        return updated.filter((m) => m.timestamp >= cutoffDate);
      });

      // Check for alerts
      const threshold = alertThresholds[metricData.value.toString()];
      if (threshold && metricData.value > threshold) {
        const alert: PerformanceAlert = {
          id: `alert_${++alertIdCounter.current}`,
          severity: metricData.value > threshold * 1.5 ? 'critical' : 'high',
          metric: metricData.value.toString(),
          message: `Performance degraded: ${metricData.value} ${metricData.unit} exceeds threshold of ${threshold}`,
          threshold,
          currentValue: metricData.value,
          timestamp: new Date(),
          resolved: false,
        };

        setAlerts((prev) => [...prev, alert]);
      }

      setLastUpdate(new Date());

      // Send to WebSocket if real-time updates are enabled
      if (realTimeUpdates) {
        sendMessage('performance:metric', metric);
      }
    },
    [alertThresholds, historicalDays, realTimeUpdates, sendMessage]
  );

  // Get metric history for a specific metric
  const getMetricHistory = useCallback(
    (metricName: string, timeframe: string = '24h') => {
      const now = Date.now();
      let cutoffTime: number;

      switch (timeframe) {
        case '1h':
          cutoffTime = now - 60 * 60 * 1000;
          break;
        case '6h':
          cutoffTime = now - 6 * 60 * 60 * 1000;
          break;
        case '24h':
          cutoffTime = now - 24 * 60 * 60 * 1000;
          break;
        case '7d':
          cutoffTime = now - 7 * 24 * 60 * 60 * 1000;
          break;
        case '30d':
          cutoffTime = now - 30 * 24 * 60 * 60 * 1000;
          break;
        default:
          cutoffTime = now - 24 * 60 * 60 * 1000;
      }

      return metricsRef.current
        .filter(
          (metric) =>
            metric.value.toString().includes(metricName) &&
            metric.timestamp.getTime() >= cutoffTime
        )
        .sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
    },
    []
  );

  // Get trend analysis for a specific metric
  const getTrendAnalysis = useCallback(
    (metricName: string) => {
      const history = getMetricHistory(metricName, '7d');
      return PerformanceAnalytics.analyzeTrend(history);
    },
    [getMetricHistory]
  );

  // Set performance baseline
  const setBaseline = useCallback(
    (baselineData: Omit<PerformanceBaseline, 'validFrom'>) => {
      const baseline: PerformanceBaseline = {
        ...baselineData,
        validFrom: new Date(),
      };

      setBaselines((prev) => {
        // Remove existing baseline for the same metric and environment
        const filtered = prev.filter(
          (b) =>
            !(
              b.metric === baseline.metric &&
              b.environment === baseline.environment
            )
        );
        return [...filtered, baseline];
      });
    },
    []
  );

  // Update existing baseline
  const updateBaseline = useCallback((metric: string, baseline: number) => {
    setBaselines((prev) =>
      prev.map((b) =>
        b.metric === metric ? { ...b, baseline, validFrom: new Date() } : b
      )
    );
  }, []);

  // Alert management
  const acknowledgeAlert = useCallback((alertId: string) => {
    setAlerts((prev) =>
      prev.map((alert) =>
        alert.id === alertId ? { ...alert, resolved: false } : alert
      )
    );
  }, []);

  const resolveAlert = useCallback((alertId: string) => {
    setAlerts((prev) =>
      prev.map((alert) =>
        alert.id === alertId ? { ...alert, resolved: true } : alert
      )
    );
  }, []);

  // Regression detection
  const detectRegressions = useCallback(
    (metricName: string) => {
      const history = getMetricHistory(metricName, '7d');
      const changePoints = PerformanceAnalytics.detectChangePoints(
        history.map((m) => m.value)
      );
      const regressions = changePoints.reduce<Date[]>((acc, index) => {
        // eslint-disable-next-line security/detect-object-injection
        const metric = history[index];
        if (metric?.timestamp) {
          acc.push(metric.timestamp);
        }
        return acc;
      }, []);
      return regressions;
    },
    [getMetricHistory]
  );

  // Calculate trend confidence
  const calculateTrendConfidence = useCallback(
    (metricName: string) => {
      const trend = getTrendAnalysis(metricName);
      return trend?.confidence || 0;
    },
    [getTrendAnalysis]
  );

  // Export performance data
  const exportData = useCallback(
    (format: 'csv' | 'json' = 'json') => {
      const data = {
        metrics: metricsRef.current,
        trends,
        alerts,
        baselines,
        exportTimestamp: new Date().toISOString(),
      };

      if (format === 'json') {
        return JSON.stringify(data, null, 2);
      }
      // Simple CSV export for metrics
      const csvRows = [
        'Timestamp,Metric,Value,Unit,BuildId,Environment',
        ...metricsRef.current.map(
          (metric) =>
            `${metric.timestamp.toISOString()},${metric.value},${metric.value},${metric.unit},${metric.context.buildId || ''},${metric.context.environment || ''}`
        ),
      ];
      return csvRows.join('\n');
    },
    [trends, alerts, baselines]
  );

  // Clear historical data
  const clearHistory = useCallback((olderThan?: Date) => {
    const cutoff = olderThan || new Date(Date.now() - 24 * 60 * 60 * 1000);
    setMetrics((prev) => prev.filter((m) => m.timestamp >= cutoff));
    setAlerts((prev) => prev.filter((a) => a.timestamp >= cutoff));
  }, []);

  // Trend analysis effect
  useEffect(() => {
    if (!trendAnalysis) return;

    const analyzeTrends = () => {
      const newTrends: PerformanceTrend[] = [];

      trackingMetrics.forEach((metricName) => {
        const trend = getTrendAnalysis(metricName);
        if (trend) {
          newTrends.push(trend);
        }
      });

      setTrends(newTrends);
    };

    const interval = setInterval(analyzeTrends, samplingInterval * 2);
    analyzeTrends(); // Run immediately

    return () => clearInterval(interval);
  }, [trendAnalysis, trackingMetrics, getTrendAnalysis, samplingInterval]);

  // Real-time monitoring setup
  useEffect(() => {
    if (!realTimeUpdates) return;

    setIsMonitoring(true);

    const handlePerformanceData = (data: unknown) => {
      if (
        typeof data === 'object' &&
        data !== null &&
        'type' in data &&
        'payload' in data &&
        data.type === 'performance:update'
      ) {
        recordMetric(
          data.payload as Omit<PerformanceMetric, 'id' | 'timestamp'>
        );
      }
    };

    subscribe('performance:updates', handlePerformanceData);

    return () => {
      unsubscribe('performance:updates', handlePerformanceData);
      setIsMonitoring(false);
    };
  }, [realTimeUpdates, recordMetric, subscribe, unsubscribe]);

  // Memoized computed values
  const computedValues = useMemo(
    () => ({
      activeAlertsCount: alerts.filter((a) => !a.resolved).length,
      criticalAlertsCount: alerts.filter(
        (a) => !a.resolved && a.severity === 'critical'
      ).length,
      metricsCount: metrics.length,
      trendsCount: trends.length,
    }),
    [alerts, metrics.length, trends.length]
  );

  return {
    // Data access
    metrics,
    trends,
    alerts,
    baselines,

    // Real-time status
    isMonitoring,
    lastUpdate,

    // Data operations
    recordMetric,
    getMetricHistory,
    getTrendAnalysis,

    // Baseline management
    setBaseline,
    updateBaseline,

    // Alert management
    acknowledgeAlert,
    resolveAlert,

    // Historical analysis
    detectRegressions,
    calculateTrendConfidence,

    // Utility functions
    exportData,
    clearHistory,

    // Loading states
    isLoading,
    error,

    // Additional computed properties
    ...computedValues,
  };
}
