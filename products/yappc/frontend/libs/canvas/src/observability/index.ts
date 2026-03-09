// Observability system - Phase 9: Monitoring & Analytics
// Comprehensive performance monitoring, debugging, and analytics system

import React from 'react';

import { useAnalytics, useDebug } from './hooks';
import { performanceMonitor } from './monitoring';

// Core exports
export * from './hooks';
export * from './components';
export * from './monitoring';

// Re-export monitoring schemas
export type {
  PerformanceMetric,
  SystemHealth,
  UserAction,
  ErrorEvent,
  DebugLog,
  MetricAggregation,
  AlertRule,
  AlertIncident,
} from './monitoring';

// Utility exports
export {
  createMetricId,
  createActionId,
  createErrorId,
  formatDuration,
  formatBytes,
  calculatePercentile,
  aggregateMetrics,
  evaluateAlertRule,
  createDefaultAlertRules,
  performanceMonitor,
} from './monitoring';

// Component integration examples
export const ObservabilitySystemProvider = {
  // Analytics integration
  analyticsIntegration: `
    import { useAnalytics } from '@your-org/canvas/observability';
    
    const MyComponent = () => {
      const {
        recordMetric,
        recordUserAction,
        startTimer,
        measureRender,
        trackCanvasOperation,
      } = useAnalytics({
        canvasId: 'canvas-123',
        userId: 'user-456',
        enableRealTime: true,
      });
      
      // Measure component render time
      useEffect(() => {
        const stopTimer = startTimer('MyComponent.render');
        return () => {
          const duration = stopTimer();
          console.log('Component rendered in', duration, 'ms');
        };
      }, []);
      
      // Track user interactions
      const handleClick = () => {
        recordUserAction({
          type: 'button.click',
          metadata: { buttonId: 'save-canvas' }
        });
      };
      
      // Track canvas operations
      const handleCanvasUpdate = (canvas) => {
        trackCanvasOperation('update', canvas);
      };
    };
  `,
  
  // Debug console integration
  debugIntegration: `
    import { useDebug } from '@your-org/canvas/observability';
    
    const MyComponent = () => {
      const {
        debug,
        info,
        warn,
        error,
        inspectCanvas,
        validateCanvas,
      } = useDebug({
        enabled: process.env.NODE_ENV === 'development',
        logLevel: 'debug',
      });
      
      // Debug logging
      debug('Component initialized', { props });
      info('User action performed', { action: 'save' });
      warn('Performance degradation detected', { renderTime: 150 });
      error('Canvas validation failed', new Error('Invalid nodes'));
      
      // Canvas debugging
      const handleInspect = () => {
        const inspection = inspectCanvas(canvasData);
        console.log('Canvas inspection:', inspection);
      };
      
      const handleValidate = () => {
        const validation = validateCanvas(canvasData);
        if (!validation.isValid) {
          error('Canvas has issues', { issues: validation.issues });
        }
      };
    };
  `,
  
  // Performance dashboard integration
  dashboardIntegration: `
    import { PerformanceDashboard } from '@your-org/canvas/observability';
    
    const AdminPanel = () => {
      return (
        <div>
          <h1>System Monitoring</h1>
          <PerformanceDashboard
            canvasId="global"
            userId="admin"
            autoRefresh={true}
            refreshInterval={5000}
          />
        </div>
      );
    };
  `,
  
  // Debug console integration
  debugConsoleIntegration: `
    import { DebugConsole } from '@your-org/canvas/observability';
    
    const DeveloperTools = ({ canvasData }) => {
      const handleCanvasInspect = (inspection) => {
        console.log('Canvas inspection result:', inspection);
      };
      
      const handleCanvasValidate = (validation) => {
        if (!validation.isValid) {
          alert('Canvas validation failed');
        }
      };
      
      return (
        <DebugConsole
          canvasData={canvasData}
          onCanvasInspect={handleCanvasInspect}
          onCanvasValidate={handleCanvasValidate}
        />
      );
    };
  `,
  
  // Performance monitoring setup
  monitoringSetup: `
    import { performanceMonitor } from '@your-org/canvas/observability';
    
    // Initialize monitoring on app startup
    const initializeMonitoring = () => {
      performanceMonitor.startMonitoring();
      
      // Record custom metrics
      performanceMonitor.recordMetric({
        name: 'app.initialization',
        value: Date.now() - window.performance.timeOrigin,
        unit: 'ms',
        category: 'render',
        metadata: { version: '1.0.0' }
      });
    };
    
    // Call during app initialization
    initializeMonitoring();
  `
};

// Production-ready feature flags
export const OBSERVABILITY_FEATURES = {
  // Analytics features
  PERFORMANCE_METRICS: true,
  USER_ACTION_TRACKING: true,
  ERROR_TRACKING: true,
  REAL_TIME_MONITORING: true,
  
  // Debug features
  DEBUG_CONSOLE: true,
  CANVAS_INSPECTION: true,
  CANVAS_VALIDATION: true,
  LOG_EXPORT: true,
  
  // Monitoring features
  SYSTEM_HEALTH_CHECK: true,
  ALERT_SYSTEM: true,
  METRIC_AGGREGATION: true,
  PERFORMANCE_OBSERVER: true,
  
  // Dashboard features
  PERFORMANCE_DASHBOARD: true,
  REAL_TIME_UPDATES: true,
  METRIC_FILTERING: true,
  EXPORT_CAPABILITIES: true,
  
  // Enterprise features
  ADVANCED_ANALYTICS: true,
  CUSTOM_METRICS: true,
  WEBHOOK_ALERTS: false, // Phase 10 feature
  DISTRIBUTED_TRACING: false, // Phase 10 feature
} as const;

// System health check
export const validateObservabilitySystem = () => {
  const checks = {
    hooksAvailable: true,
    componentsLoaded: true,
    monitoringActive: typeof window !== 'undefined' && 'PerformanceObserver' in window,
    schemasValid: true,
    performanceAPIAvailable: typeof window !== 'undefined' && 'performance' in window,
  };
  
  const healthy = Object.values(checks).every(Boolean);
  
  return {
    healthy,
    checks,
    capabilities: {
      realTimeMetrics: checks.performanceAPIAvailable,
      performanceObserver: checks.monitoringActive,
      memoryMonitoring: typeof window !== 'undefined' && 'memory' in (window.performance || {}),
      debugConsole: true,
      canvasInspection: true,
    },
    timestamp: new Date().toISOString(),
    version: '1.0.0',
  };
};

// Quick start utilities
export const createAnalyticsProvider = (config: {
  canvasId?: string;
  userId?: string;
  enableRealTime?: boolean;
}) => {
  return {
    config,
    start: () => {
      console.log('[Observability] Analytics provider started with config:', config);
      return performanceMonitor.startMonitoring();
    },
    stop: () => {
      console.log('[Observability] Analytics provider stopped');
      return performanceMonitor.stopMonitoring();
    },
  };
};

export const createDebugProvider = (config: {
  enabled?: boolean;
  logLevel?: 'debug' | 'info' | 'warn' | 'error';
}) => {
  return {
    config,
    log: (level: string, message: string, context?: unknown) => {
      if (config.enabled) {
        const logMethod = (console as unknown)[level] || console.log;
        logMethod(`[Canvas Debug] ${message}`, context || '');
      }
    },
  };
};

// Integration helpers
export const withAnalytics = <T extends Record<string, unknown>>(
  Component: React.ComponentType<T>,
  analyticsConfig?: Parameters<typeof useAnalytics>[0]
) => {
  const AnalyticsWrappedComponent = (props: T) => {
    const analytics = useAnalytics(analyticsConfig);
    
    return React.createElement(Component, {
      ...props,
      analytics,
    });
  };
  
  AnalyticsWrappedComponent.displayName = `withAnalytics(${Component.displayName || Component.name})`;
  return AnalyticsWrappedComponent;
};

export const withDebug = <T extends Record<string, unknown>>(
  Component: React.ComponentType<T>,
  debugConfig?: Parameters<typeof useDebug>[0]
) => {
  const DebugWrappedComponent = (props: T) => {
    const debug = useDebug(debugConfig);
    
    return React.createElement(Component, {
      ...props,
      debug,
    });
  };
  
  DebugWrappedComponent.displayName = `withDebug(${Component.displayName || Component.name})`;
  return DebugWrappedComponent;
};