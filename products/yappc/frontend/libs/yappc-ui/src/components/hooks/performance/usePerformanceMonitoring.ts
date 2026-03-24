// Re-export the modular implementation (keeps existing import paths stable)
// Note: reference the folder index explicitly to avoid resolving to this file
export { usePerformanceMonitoring } from './usePerformanceMonitoring/index';

// Re-export types for convenience
export type {
  PerformanceMetric,
  PerformanceTrend,
  PerformanceAlert,
  PerformanceBaseline,
  PerformanceOptions,
  UsePerformanceMonitoringResult,
} from './usePerformanceMonitoring/types';
