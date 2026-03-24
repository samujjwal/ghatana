import { useState, useEffect, useCallback, useRef } from 'react';

import type { CanvasData } from '../schemas/canvas-schemas';

// Performance metrics types
/**
 *
 */
export interface PerformanceMetric {
  id: string;
  name: string;
  value: number;
  unit: string;
  timestamp: string;
  category: 'render' | 'interaction' | 'network' | 'memory' | 'canvas';
  metadata?: Record<string, unknown>;
}

/**
 *
 */
export interface SystemHealth {
  status: 'healthy' | 'degraded' | 'critical';
  score: number; // 0-100
  metrics: {
    renderTime: number;
    memoryUsage: number;
    canvasNodes: number;
    activeConnections: number;
    errorRate: number;
  };
  timestamp: string;
}

/**
 *
 */
export interface UserAction {
  id: string;
  type: string;
  timestamp: string;
  userId?: string;
  canvasId?: string;
  duration?: number;
  metadata?: Record<string, unknown>;
}

/**
 *
 */
export interface ErrorEvent {
  id: string;
  type: 'error' | 'warning' | 'info';
  message: string;
  stack?: string;
  timestamp: string;
  userId?: string;
  canvasId?: string;
  context?: Record<string, unknown>;
  resolved?: boolean;
}

// Analytics hook for performance monitoring
/**
 *
 */
export interface UseAnalyticsConfig {
  canvasId?: string;
  userId?: string;
  enableRealTime?: boolean;
  metricsInterval?: number;
  batchSize?: number;
  autoFlush?: boolean;
}

/**
 *
 */
export interface UseAnalyticsReturn {
  // Metrics collection
  recordMetric: (metric: Omit<PerformanceMetric, 'id' | 'timestamp'>) => void;
  recordUserAction: (action: Omit<UserAction, 'id' | 'timestamp'>) => void;
  recordError: (error: Omit<ErrorEvent, 'id' | 'timestamp'>) => void;

  // Performance monitoring
  startTimer: (name: string) => () => void;
  measureRender: (componentName: string) => (duration: number) => void;
  trackCanvasOperation: (operation: string, canvasData: CanvasData) => void;

  // System health
  getSystemHealth: () => SystemHealth;
  getMetrics: (
    category?: string,
    timeRange?: { start: string; end: string }
  ) => PerformanceMetric[];

  // Analytics state
  isRecording: boolean;
  metricsCount: number;
  lastFlush: string | null;

  // Control methods
  startRecording: () => void;
  stopRecording: () => void;
  flushMetrics: () => Promise<void>;
  clearMetrics: () => void;
}

export const useAnalytics = ({
  canvasId,
  userId,
  enableRealTime = true,
  metricsInterval = 5000,
  batchSize = 50,
  autoFlush = true,
}: UseAnalyticsConfig = {}): UseAnalyticsReturn => {
  const [isRecording, setIsRecording] = useState(enableRealTime);
  const [metricsBuffer, setMetricsBuffer] = useState<PerformanceMetric[]>([]);
  const [actionsBuffer, setActionsBuffer] = useState<UserAction[]>([]);
  const [errorsBuffer, setErrorsBuffer] = useState<ErrorEvent[]>([]);
  const [lastFlush, setLastFlush] = useState<string | null>(null);

  const timersRef = useRef<Map<string, number>>(new Map());
  const flushTimeoutRef = useRef<NodeJS.Timeout>();

  // Auto-flush metrics when buffer reaches batch size
  useEffect(() => {
    if (autoFlush && metricsBuffer.length >= batchSize) {
      flushMetrics();
    }
    // ensure function returns void in all code paths
    return undefined;
  }, [metricsBuffer.length, batchSize, autoFlush]);

  // Periodic flush
  useEffect(() => {
    if (isRecording && autoFlush) {
      flushTimeoutRef.current = setInterval(() => {
        if (
          metricsBuffer.length > 0 ||
          actionsBuffer.length > 0 ||
          errorsBuffer.length > 0
        ) {
          flushMetrics();
        }
      }, metricsInterval);

      return () => {
        if (flushTimeoutRef.current) {
          clearInterval(flushTimeoutRef.current);
        }
        return undefined;
      };
    }
    return undefined;
  }, [isRecording, autoFlush, metricsInterval]);

  const recordMetric = useCallback(
    (metric: Omit<PerformanceMetric, 'id' | 'timestamp'>) => {
      if (!isRecording) return;

      const fullMetric: PerformanceMetric = {
        ...metric,
        id: `metric-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        timestamp: new Date().toISOString(),
      };

      setMetricsBuffer((prev) => [...prev, fullMetric]);
    },
    [isRecording]
  );

  const recordUserAction = useCallback(
    (action: Omit<UserAction, 'id' | 'timestamp'>) => {
      if (!isRecording) return;

      const fullAction: UserAction = {
        ...action,
        id: `action-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        timestamp: new Date().toISOString(),
        userId: action.userId || userId,
        canvasId: action.canvasId || canvasId,
      };

      setActionsBuffer((prev) => [...prev, fullAction]);
    },
    [isRecording, userId, canvasId]
  );

  const recordError = useCallback(
    (error: Omit<ErrorEvent, 'id' | 'timestamp'>) => {
      const fullError: ErrorEvent = {
        ...error,
        id: `error-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        timestamp: new Date().toISOString(),
        userId: error.userId || userId,
        canvasId: error.canvasId || canvasId,
      };

      setErrorsBuffer((prev) => [...prev, fullError]);

      // Immediately log critical errors
      if (error.type === 'error') {
        console.error('[Analytics] Error recorded:', fullError);
      }
    },
    [userId, canvasId]
  );

  const startTimer = useCallback(
    (name: string) => {
      const startTime = performance.now();
      timersRef.current.set(name, startTime);

      return () => {
        const endTime = performance.now();
        const duration = endTime - startTime;
        timersRef.current.delete(name);

        recordMetric({
          name: `timer.${name}`,
          value: duration,
          unit: 'ms',
          category: 'render',
          metadata: { timerName: name },
        });

        return duration;
      };
    },
    [recordMetric]
  );

  const measureRender = useCallback(
    (componentName: string) => {
      return (duration: number) => {
        recordMetric({
          name: 'component.render',
          value: duration,
          unit: 'ms',
          category: 'render',
          metadata: { componentName },
        });
      };
    },
    [recordMetric]
  );

  const trackCanvasOperation = useCallback(
    (operation: string, canvasData: CanvasData) => {
      recordMetric({
        name: `canvas.${operation}`,
        value: 1,
        unit: 'count',
        category: 'canvas',
        metadata: {
          operation,
          nodeCount: canvasData.nodes.length,
          edgeCount: canvasData.edges.length,
          layerCount: canvasData.layers.length,
        },
      });

      recordUserAction({
        type: `canvas.${operation}`,
        metadata: {
          nodeCount: canvasData.nodes.length,
          edgeCount: canvasData.edges.length,
          canvasName: canvasData.metadata.name,
        },
      });
    },
    [recordMetric, recordUserAction]
  );

  const getSystemHealth = useCallback((): SystemHealth => {
    const now = new Date().toISOString();
    const recentMetrics = metricsBuffer.filter(
      (m) => new Date(m.timestamp).getTime() > Date.now() - 60000 // Last minute
    );

    // Calculate health metrics
    const renderMetrics = recentMetrics.filter((m) => m.category === 'render');
    const avgRenderTime =
      renderMetrics.length > 0
        ? renderMetrics.reduce((sum, m) => sum + m.value, 0) /
          renderMetrics.length
        : 0;

    const memoryMetrics = recentMetrics.filter((m) =>
      m.name.includes('memory')
    );
    const memoryUsage =
      memoryMetrics.length > 0
        ? memoryMetrics[memoryMetrics.length - 1].value
        : 0;

    const canvasMetrics = recentMetrics.filter((m) => m.category === 'canvas');
    const canvasNodes =
      canvasMetrics.length > 0
        ? Math.max(...canvasMetrics.map((m) => m.metadata?.nodeCount || 0))
        : 0;

    const errorMetrics = errorsBuffer.filter(
      (e) => new Date(e.timestamp).getTime() > Date.now() - 60000
    );
    const errorRate = errorMetrics.length;

    // Calculate health score (0-100)
    let score = 100;
    if (avgRenderTime > 100) score -= 20; // Slow rendering
    if (memoryUsage > 100) score -= 15; // High memory usage
    if (canvasNodes > 1000) score -= 10; // Large canvas
    if (errorRate > 5) score -= 25; // High error rate

    const status: SystemHealth['status'] =
      score >= 80 ? 'healthy' : score >= 60 ? 'degraded' : 'critical';

    return {
      status,
      score: Math.max(0, score),
      metrics: {
        renderTime: avgRenderTime,
        memoryUsage,
        canvasNodes,
        activeConnections: actionsBuffer.length,
        errorRate,
      },
      timestamp: now,
    };
  }, [metricsBuffer, errorsBuffer, actionsBuffer]);

  const getMetrics = useCallback(
    (
      category?: string,
      timeRange?: { start: string; end: string }
    ): PerformanceMetric[] => {
      let filtered = metricsBuffer;

      if (category) {
        filtered = filtered.filter((m) => m.category === category);
      }

      if (timeRange) {
        const start = new Date(timeRange.start).getTime();
        const end = new Date(timeRange.end).getTime();
        filtered = filtered.filter((m) => {
          const timestamp = new Date(m.timestamp).getTime();
          return timestamp >= start && timestamp <= end;
        });
      }

      return filtered.sort(
        (a, b) =>
          new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
      );
    },
    [metricsBuffer]
  );

  const startRecording = useCallback(() => {
    setIsRecording(true);
    recordUserAction({
      type: 'analytics.start',
      metadata: { reason: 'manual' },
    });
  }, [recordUserAction]);

  const stopRecording = useCallback(() => {
    setIsRecording(false);
    recordUserAction({
      type: 'analytics.stop',
      metadata: { reason: 'manual' },
    });
  }, [recordUserAction]);

  const flushMetrics = useCallback(async () => {
    if (
      metricsBuffer.length === 0 &&
      actionsBuffer.length === 0 &&
      errorsBuffer.length === 0
    ) {
      return;
    }

    try {
      // In a real implementation, this would send to analytics service
      const payload = {
        metrics: metricsBuffer,
        actions: actionsBuffer,
        errors: errorsBuffer,
        timestamp: new Date().toISOString(),
        userId,
        canvasId,
      };

      // Mock API call
      console.log('[Analytics] Flushing metrics:', {
        metricsCount: metricsBuffer.length,
        actionsCount: actionsBuffer.length,
        errorsCount: errorsBuffer.length,
      });

      // Simulate API delay
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Clear buffers
      setMetricsBuffer([]);
      setActionsBuffer([]);
      setErrorsBuffer([]);
      setLastFlush(new Date().toISOString());
    } catch (error) {
      console.error('[Analytics] Failed to flush metrics:', error);
      recordError({
        type: 'error',
        message: 'Failed to flush metrics',
        stack: error instanceof Error ? error.stack : undefined,
        context: {
          metricsCount: metricsBuffer.length,
          actionsCount: actionsBuffer.length,
          errorsCount: errorsBuffer.length,
        },
      });
    }
  }, [metricsBuffer, actionsBuffer, errorsBuffer, userId, canvasId]);

  const clearMetrics = useCallback(() => {
    setMetricsBuffer([]);
    setActionsBuffer([]);
    setErrorsBuffer([]);
    timersRef.current.clear();
    recordUserAction({
      type: 'analytics.clear',
      metadata: { reason: 'manual' },
    });
  }, [recordUserAction]);

  return {
    recordMetric,
    recordUserAction,
    recordError,
    startTimer,
    measureRender,
    trackCanvasOperation,
    getSystemHealth,
    getMetrics,
    isRecording,
    metricsCount:
      metricsBuffer.length + actionsBuffer.length + errorsBuffer.length,
    lastFlush,
    startRecording,
    stopRecording,
    flushMetrics,
    clearMetrics,
  };
};

// Debug utilities hook
/**
 *
 */
export interface UseDebugConfig {
  enabled?: boolean;
  logLevel?: 'debug' | 'info' | 'warn' | 'error';
  persistLogs?: boolean;
  maxLogEntries?: number;
}

/**
 *
 */
export interface DebugLog {
  id: string;
  level: 'debug' | 'info' | 'warn' | 'error';
  message: string;
  timestamp: string;
  context?: Record<string, unknown>;
  stack?: string;
}

/**
 *
 */
export interface UseDebugReturn {
  // Logging methods
  debug: (message: string, context?: Record<string, unknown>) => void;
  info: (message: string, context?: Record<string, unknown>) => void;
  warn: (message: string, context?: Record<string, unknown>) => void;
  error: (
    message: string,
    error?: Error,
    context?: Record<string, unknown>
  ) => void;

  // Debug state
  logs: DebugLog[];
  isEnabled: boolean;

  // Control methods
  enable: () => void;
  disable: () => void;
  clearLogs: () => void;
  exportLogs: () => string;

  // Canvas debugging
  inspectCanvas: (canvas: CanvasData) => Record<string, unknown>;
  validateCanvas: (canvas: CanvasData) => {
    isValid: boolean;
    issues: string[];
  };
}

export const useDebug = ({
  enabled = process.env.NODE_ENV === 'development',
  logLevel = 'debug',
  persistLogs = true,
  maxLogEntries = 1000,
}: UseDebugConfig = {}): UseDebugReturn => {
  const [isEnabled, setIsEnabled] = useState(enabled);
  const [logs, setLogs] = useState<DebugLog[]>([]);

  const logLevels = { debug: 0, info: 1, warn: 2, error: 3 };
  const currentLevel = logLevels[logLevel];

  const addLog = useCallback(
    (
      level: DebugLog['level'],
      message: string,
      context?: Record<string, unknown>,
      stack?: string
    ) => {
      if (!isEnabled || logLevels[level] < currentLevel) return;

      const log: DebugLog = {
        id: `log-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        level,
        message,
        timestamp: new Date().toISOString(),
        context,
        stack,
      };

      setLogs((prev) => {
        const newLogs = [log, ...prev];
        return newLogs.slice(0, maxLogEntries);
      });

      // Also log to console
      const consoleMethod = console[level] || console.log;
      consoleMethod(`[Canvas Debug] ${message}`, context || '');
    },
    [isEnabled, currentLevel, maxLogEntries]
  );

  const debug = useCallback(
    (message: string, context?: Record<string, unknown>) => {
      addLog('debug', message, context);
    },
    [addLog]
  );

  const info = useCallback(
    (message: string, context?: Record<string, unknown>) => {
      addLog('info', message, context);
    },
    [addLog]
  );

  const warn = useCallback(
    (message: string, context?: Record<string, unknown>) => {
      addLog('warn', message, context);
    },
    [addLog]
  );

  const error = useCallback(
    (message: string, error?: Error, context?: Record<string, unknown>) => {
      addLog('error', message, context, error?.stack);
    },
    [addLog]
  );

  const enable = useCallback(() => {
    setIsEnabled(true);
    info('Debug logging enabled');
  }, [info]);

  const disable = useCallback(() => {
    info('Debug logging disabled');
    setIsEnabled(false);
  }, [info]);

  const clearLogs = useCallback(() => {
    setLogs([]);
    info('Debug logs cleared');
  }, [info]);

  const exportLogs = useCallback((): string => {
    const exportData = {
      timestamp: new Date().toISOString(),
      logCount: logs.length,
      logs: logs.sort(
        (a, b) =>
          new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
      ),
    };

    return JSON.stringify(exportData, null, 2);
  }, [logs]);

  const inspectCanvas = useCallback(
    (canvas: CanvasData): Record<string, unknown> => {
      const inspection = {
        metadata: {
          id: canvas.metadata.id,
          name: canvas.metadata.name,
          version: canvas.metadata.version,
          nodeCount: canvas.nodes.length,
          edgeCount: canvas.edges.length,
          layerCount: canvas.layers.length,
          createdAt: canvas.metadata.createdAt,
          updatedAt: canvas.metadata.updatedAt,
        },
        structure: {
          nodes: canvas.nodes.map((node) => ({
            id: node.id,
            type: node.type,
            position: node.position,
            hasData: !!node.data,
            dataKeys: node.data ? Object.keys(node.data) : [],
          })),
          edges: canvas.edges.map((edge) => ({
            id: edge.id,
            type: edge.type,
            source: edge.source,
            target: edge.target,
            hasData: !!edge.data,
            dataKeys: edge.data ? Object.keys(edge.data) : [],
          })),
          layers: canvas.layers.map((layer) => ({
            id: layer.id,
            name: layer.name,
            isVisible: layer.visible,
            isLocked: layer.locked,
            nodeCount: layer.nodeIds?.length || 0,
          })),
        },
        viewport: canvas.viewport,
        settings: canvas.settings,
      };

      debug('Canvas inspection completed', inspection);
      return inspection;
    },
    [debug]
  );

  const validateCanvas = useCallback(
    (canvas: CanvasData): { isValid: boolean; issues: string[] } => {
      const issues: string[] = [];

      // Validate metadata
      if (!canvas.metadata.id) issues.push('Missing canvas ID');
      if (!canvas.metadata.name) issues.push('Missing canvas name');
      if (canvas.metadata.version < 1) issues.push('Invalid canvas version');

      // Validate nodes
      const nodeIds = new Set();
      canvas.nodes.forEach((node, index) => {
        if (!node.id) issues.push(`Node ${index} missing ID`);
        if (nodeIds.has(node.id)) issues.push(`Duplicate node ID: ${node.id}`);
        nodeIds.add(node.id);

        if (!node.type) issues.push(`Node ${(node as unknown).id} missing type`);
        if (!node.position) issues.push(`Node ${node.id} missing position`);
      });

      // Validate edges
      const edgeIds = new Set();
      canvas.edges.forEach((edge, index) => {
        if (!edge.id) issues.push(`Edge ${index} missing ID`);
        if (edgeIds.has(edge.id)) issues.push(`Duplicate edge ID: ${edge.id}`);
        edgeIds.add(edge.id);

        if (!edge.source) issues.push(`Edge ${edge.id} missing source`);
        if (!edge.target) issues.push(`Edge ${edge.id} missing target`);

        // Check if source and target nodes exist
        if (!nodeIds.has(edge.source))
          issues.push(
            `Edge ${edge.id} references non-existent source node: ${edge.source}`
          );
        if (!nodeIds.has(edge.target))
          issues.push(
            `Edge ${edge.id} references non-existent target node: ${edge.target}`
          );
      });

      // Validate layers
      const layerIds = new Set();
      canvas.layers.forEach((layer, index) => {
        if (!layer.id) issues.push(`Layer ${index} missing ID`);
        if (layerIds.has(layer.id))
          issues.push(`Duplicate layer ID: ${layer.id}`);
        layerIds.add(layer.id);

        if (!layer.name) issues.push(`Layer ${layer.id} missing name`);

        // Check if referenced nodes exist
        layer.nodeIds?.forEach((nodeId) => {
          if (!nodeIds.has(nodeId)) {
            issues.push(
              `Layer ${layer.id} references non-existent node: ${nodeId}`
            );
          }
        });
      });

      const isValid = issues.length === 0;

      if (isValid) {
        info('Canvas validation passed', { canvasId: canvas.metadata.id });
      } else {
        warn('Canvas validation failed', {
          canvasId: canvas.metadata.id,
          issueCount: issues.length,
          issues: issues.slice(0, 5), // Log first 5 issues
        });
      }

      return { isValid, issues };
    },
    [info, warn]
  );

  return {
    debug,
    info,
    warn,
    error,
    logs,
    isEnabled,
    enable,
    disable,
    clearLogs,
    exportLogs,
    inspectCanvas,
    validateCanvas,
  };
};
