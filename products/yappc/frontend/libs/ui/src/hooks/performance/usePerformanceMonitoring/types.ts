/**
 * Types for usePerformanceMonitoring
 */

/**
 * PerformanceMetric
 *
 * A single collected performance measurement with timestamp and contextual data.
 */
export interface PerformanceMetric {
  id: string;
  timestamp: Date;
  value: number;
  unit: string;
  context: {
    buildId?: string;
    deploymentId?: string;
    environment?: string;
    branch?: string;
    commit?: string;
    stage?: string;
  };
  metadata?: Record<string, unknown>;
}

/**
 * PerformanceTrend
 *
 * Analyzed trend direction, confidence, and change metrics over time.
 */
export interface PerformanceTrend {
  metric: string;
  direction: 'improving' | 'degrading' | 'stable';
  confidence: number; // 0-1 scale
  changePercent: number;
  timeframe: string;
  regressionPoints?: Date[];
  trend: 'linear' | 'exponential' | 'logarithmic' | 'polynomial';
}

/**
 * PerformanceAlert
 *
 * An alert triggered when metrics exceed configured thresholds.
 */
export interface PerformanceAlert {
  id: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  metric: string;
  message: string;
  threshold: number;
  currentValue: number;
  timestamp: Date;
  resolved: boolean;
}

/**
 * PerformanceBaseline
 *
 * Expected baseline performance for a specific metric and environment.
 */
export interface PerformanceBaseline {
  metric: string;
  baseline: number;
  tolerance: number;
  environment: string;
  validFrom: Date;
  validUntil?: Date;
}

/**
 * PerformanceOptions
 *
 * Configuration for the usePerformanceMonitoring hook.
 */
export interface PerformanceOptions {
  metrics?: string[];
  realTimeUpdates?: boolean;
  historicalDays?: number;
  trendAnalysis?: boolean;
  regressionDetection?: boolean;
  alertThresholds?: Record<string, number>;
  samplingInterval?: number; // milliseconds
  batchSize?: number;
}

/**
 * UsePerformanceMonitoringResult
 *
 * Return value of the usePerformanceMonitoring hook with all methods and state.
 */
export interface UsePerformanceMonitoringResult {
  // Data access
  metrics: PerformanceMetric[];
  trends: PerformanceTrend[];
  alerts: PerformanceAlert[];
  baselines: PerformanceBaseline[];

  // Real-time status
  isMonitoring: boolean;
  lastUpdate: Date | null;

  // Data operations
  recordMetric: (metric: Omit<PerformanceMetric, 'id' | 'timestamp'>) => void;
  getMetricHistory: (
    metricName: string,
    timeframe?: string
  ) => PerformanceMetric[];
  getTrendAnalysis: (metricName: string) => PerformanceTrend | null;

  // Baseline management
  setBaseline: (baseline: Omit<PerformanceBaseline, 'validFrom'>) => void;
  updateBaseline: (metric: string, baseline: number) => void;

  // Alert management
  acknowledgeAlert: (alertId: string) => void;
  resolveAlert: (alertId: string) => void;

  // Historical analysis
  detectRegressions: (metricName: string) => Date[];
  calculateTrendConfidence: (metricName: string) => number;

  // Utility functions
  exportData: (format?: 'csv' | 'json') => string;
  clearHistory: (olderThan?: Date) => void;

  // Loading states
  isLoading: boolean;
  error: string | null;
}
