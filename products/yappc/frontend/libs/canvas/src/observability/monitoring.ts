import { z } from 'zod';

// Performance monitoring schemas
export const PerformanceMetricSchema = z.object({
  id: z.string(),
  name: z.string(),
  value: z.number(),
  unit: z.string(),
  timestamp: z.string().datetime(),
  category: z.enum(['render', 'interaction', 'network', 'memory', 'canvas']),
  metadata: z.record(z.string(), z.any()).optional(),
});

export const SystemHealthSchema = z.object({
  status: z.enum(['healthy', 'degraded', 'critical']),
  score: z.number().min(0).max(100),
  metrics: z.object({
    renderTime: z.number(),
    memoryUsage: z.number(),
    canvasNodes: z.number(),
    activeConnections: z.number(),
    errorRate: z.number(),
  }),
  timestamp: z.string().datetime(),
});

export const UserActionSchema = z.object({
  id: z.string(),
  type: z.string(),
  timestamp: z.string().datetime(),
  userId: z.string().optional(),
  canvasId: z.string().optional(),
  duration: z.number().optional(),
  metadata: z.record(z.string(), z.any()).optional(),
});

export const ErrorEventSchema = z.object({
  id: z.string(),
  type: z.enum(['error', 'warning', 'info']),
  message: z.string(),
  stack: z.string().optional(),
  timestamp: z.string().datetime(),
  userId: z.string().optional(),
  canvasId: z.string().optional(),
  context: z.record(z.string(), z.any()).optional(),
  resolved: z.boolean().default(false),
});

// Debug logging schemas
export const DebugLogSchema = z.object({
  id: z.string(),
  level: z.enum(['debug', 'info', 'warn', 'error']),
  message: z.string(),
  timestamp: z.string().datetime(),
  context: z.record(z.string(), z.any()).optional(),
  stack: z.string().optional(),
});

// Analytics aggregation schemas
export const MetricAggregationSchema = z.object({
  period: z.enum(['minute', 'hour', 'day', 'week']),
  startTime: z.string().datetime(),
  endTime: z.string().datetime(),
  metrics: z.array(
    z.object({
      name: z.string(),
      category: z.string(),
      count: z.number(),
      sum: z.number(),
      average: z.number(),
      min: z.number(),
      max: z.number(),
      percentiles: z.object({
        p50: z.number(),
        p95: z.number(),
        p99: z.number(),
      }),
    })
  ),
});

export const AlertRuleSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().optional(),
  enabled: z.boolean().default(true),
  conditions: z.array(
    z.object({
      metric: z.string(),
      operator: z.enum(['>', '<', '>=', '<=', '==', '!=']),
      threshold: z.number(),
      duration: z.number(), // seconds
    })
  ),
  actions: z.array(
    z.object({
      type: z.enum(['email', 'webhook', 'console']),
      target: z.string(),
      template: z.string().optional(),
    })
  ),
  cooldown: z.number().default(300), // seconds
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

export const AlertIncidentSchema = z.object({
  id: z.string(),
  ruleId: z.string(),
  ruleName: z.string(),
  status: z.enum(['firing', 'resolved']),
  severity: z.enum(['low', 'medium', 'high', 'critical']),
  startTime: z.string().datetime(),
  endTime: z.string().datetime().optional(),
  description: z.string(),
  context: z.record(z.string(), z.any()).optional(),
  acknowledgedBy: z.string().optional(),
  acknowledgedAt: z.string().datetime().optional(),
});

// Type exports
/**
 *
 */
export type PerformanceMetric = z.infer<typeof PerformanceMetricSchema>;
/**
 *
 */
export type SystemHealth = z.infer<typeof SystemHealthSchema>;
/**
 *
 */
export type UserAction = z.infer<typeof UserActionSchema>;
/**
 *
 */
export type ErrorEvent = z.infer<typeof ErrorEventSchema>;
/**
 *
 */
export type DebugLog = z.infer<typeof DebugLogSchema>;
/**
 *
 */
export type MetricAggregation = z.infer<typeof MetricAggregationSchema>;
/**
 *
 */
export type AlertRule = z.infer<typeof AlertRuleSchema>;
/**
 *
 */
export type AlertIncident = z.infer<typeof AlertIncidentSchema>;

// Monitoring utilities
export const createMetricId = (): string => {
  return `metric-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
};

export const createActionId = (): string => {
  return `action-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
};

export const createErrorId = (): string => {
  return `error-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
};

export const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms.toFixed(1)}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  if (ms < 3600000) return `${(ms / 60000).toFixed(1)}m`;
  return `${(ms / 3600000).toFixed(1)}h`;
};

export const formatBytes = (bytes: number): string => {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = bytes;
  let unitIndex = 0;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }

  return `${size.toFixed(1)}${units[unitIndex]}`;
};

export const calculatePercentile = (
  values: number[],
  percentile: number
): number => {
  if (values.length === 0) return 0;

  const sorted = [...values].sort((a, b) => a - b);
  const index = (percentile / 100) * (sorted.length - 1);

  if (Number.isInteger(index)) {
    return sorted[index];
  }

  const lower = Math.floor(index);
  const upper = Math.ceil(index);
  const weight = index - lower;

  return sorted[lower] * (1 - weight) + sorted[upper] * weight;
};

export const aggregateMetrics = (
  metrics: PerformanceMetric[],
  period: 'minute' | 'hour' | 'day' | 'week'
): MetricAggregation => {
  if (metrics.length === 0) {
    throw new Error('Cannot aggregate empty metrics array');
  }

  const startTime = new Date(
    Math.min(...metrics.map((m) => new Date(m.timestamp).getTime()))
  );
  const endTime = new Date(
    Math.max(...metrics.map((m) => new Date(m.timestamp).getTime()))
  );

  // Group metrics by name and category
  const grouped = metrics.reduce(
    (acc, metric) => {
      const key = `${metric.name}:${metric.category}`;
      if (!acc[key]) {
        acc[key] = [];
      }
      acc[key].push(metric);
      return acc;
    },
    {} as Record<string, PerformanceMetric[]>
  );

  const aggregatedMetrics = Object.entries(grouped).map(
    ([key, metricGroup]) => {
      const [name, category] = key.split(':');
      const values = metricGroup.map((m) => m.value);
      const sum = values.reduce((acc, val) => acc + val, 0);
      const average = sum / values.length;
      const min = Math.min(...values);
      const max = Math.max(...values);

      return {
        name,
        category,
        count: values.length,
        sum,
        average,
        min,
        max,
        percentiles: {
          p50: calculatePercentile(values, 50),
          p95: calculatePercentile(values, 95),
          p99: calculatePercentile(values, 99),
        },
      };
    }
  );

  return {
    period,
    startTime: startTime.toISOString(),
    endTime: endTime.toISOString(),
    metrics: aggregatedMetrics,
  };
};

export const evaluateAlertRule = (
  rule: AlertRule,
  currentMetrics: PerformanceMetric[]
): boolean => {
  if (!rule.enabled) return false;

  return rule.conditions.every((condition) => {
    const relevantMetrics = currentMetrics.filter(
      (m) => m.name === condition.metric
    );
    if (relevantMetrics.length === 0) return false;

    const latestValue = relevantMetrics[relevantMetrics.length - 1].value;

    switch (condition.operator) {
      case '>':
        return latestValue > condition.threshold;
      case '<':
        return latestValue < condition.threshold;
      case '>=':
        return latestValue >= condition.threshold;
      case '<=':
        return latestValue <= condition.threshold;
      case '==':
        return latestValue === condition.threshold;
      case '!=':
        return latestValue !== condition.threshold;
      default:
        return false;
    }
  });
};

export const createDefaultAlertRules = (): AlertRule[] => {
  const now = new Date().toISOString();

  return [
    {
      id: 'high-render-time',
      name: 'High Render Time',
      description: 'Alert when component render time exceeds 100ms',
      enabled: true,
      conditions: [
        {
          metric: 'component.render',
          operator: '>',
          threshold: 100,
          duration: 60,
        },
      ],
      actions: [
        {
          type: 'console',
          target: 'performance-team',
          template: 'Render time alert: {{value}}ms exceeds threshold',
        },
      ],
      cooldown: 300,
      createdAt: now,
      updatedAt: now,
    },
    {
      id: 'high-error-rate',
      name: 'High Error Rate',
      description: 'Alert when error rate exceeds 5 errors per minute',
      enabled: true,
      conditions: [
        {
          metric: 'error.rate',
          operator: '>',
          threshold: 5,
          duration: 60,
        },
      ],
      actions: [
        {
          type: 'console',
          target: 'dev-team',
          template: 'High error rate detected: {{value}} errors/min',
        },
      ],
      cooldown: 600,
      createdAt: now,
      updatedAt: now,
    },
    {
      id: 'memory-usage',
      name: 'High Memory Usage',
      description: 'Alert when memory usage exceeds 100MB',
      enabled: true,
      conditions: [
        {
          metric: 'memory.usage',
          operator: '>',
          threshold: 100,
          duration: 300,
        },
      ],
      actions: [
        {
          type: 'console',
          target: 'platform-team',
          template: 'Memory usage alert: {{value}}MB',
        },
      ],
      cooldown: 900,
      createdAt: now,
      updatedAt: now,
    },
  ];
};

// Performance monitoring utilities
/**
 *
 */
export class PerformanceMonitor {
  private static instance: PerformanceMonitor;
  private metrics: PerformanceMetric[] = [];
  private observers: Map<string, PerformanceObserver> = new Map();

  /**
   *
   */
  static getInstance(): PerformanceMonitor {
    if (!PerformanceMonitor.instance) {
      PerformanceMonitor.instance = new PerformanceMonitor();
    }
    return PerformanceMonitor.instance;
  }

  /**
   *
   */
  startMonitoring(): void {
    if (typeof window === 'undefined') return;

    // Monitor paint metrics
    if ('PerformanceObserver' in window) {
      try {
        const paintObserver = new PerformanceObserver((list) => {
          list.getEntries().forEach((entry) => {
            this.recordMetric({
              name: entry.name,
              value: entry.startTime,
              unit: 'ms',
              category: 'render',
              metadata: { entryType: entry.entryType },
            });
          });
        });

        paintObserver.observe({ entryTypes: ['paint'] });
        this.observers.set('paint', paintObserver);
      } catch (error) {
        console.warn('Paint observer not supported:', error);
      }

      // Monitor navigation metrics
      try {
        const navigationObserver = new PerformanceObserver((list) => {
          list.getEntries().forEach((entry) => {
            if (entry instanceof PerformanceNavigationTiming) {
              this.recordMetric({
                name: 'navigation.domContentLoaded',
                value: entry.domContentLoadedEventEnd - entry.fetchStart,
                unit: 'ms',
                category: 'network',
                metadata: { type: entry.type },
              });
            }
          });
        });

        navigationObserver.observe({ entryTypes: ['navigation'] });
        this.observers.set('navigation', navigationObserver);
      } catch (error) {
        console.warn('Navigation observer not supported:', error);
      }
    }

    // Monitor memory usage
    this.startMemoryMonitoring();
  }

  /**
   *
   */
  stopMonitoring(): void {
    this.observers.forEach((observer) => {
      observer.disconnect();
    });
    this.observers.clear();
  }

  /**
   *
   */
  private startMemoryMonitoring(): void {
    if (typeof window === 'undefined' || !('performance' in window)) return;

    const checkMemory = () => {
      if ('memory' in performance) {
        const memory = (performance as unknown).memory;
        this.recordMetric({
          name: 'memory.used',
          value: memory.usedJSHeapSize / 1048576, // Convert to MB
          unit: 'MB',
          category: 'memory',
          metadata: {
            total: memory.totalJSHeapSize,
            limit: memory.jsHeapSizeLimit,
          },
        });
      }
    };

    // Check memory every 10 seconds
    setInterval(checkMemory, 10000);
    checkMemory(); // Initial check
  }

  /**
   *
   */
  recordMetric(metric: Omit<PerformanceMetric, 'id' | 'timestamp'>): void {
    const fullMetric: PerformanceMetric = {
      ...metric,
      id: createMetricId(),
      timestamp: new Date().toISOString(),
    };

    this.metrics.push(fullMetric);

    // Keep only last 1000 metrics to prevent memory leaks
    if (this.metrics.length > 1000) {
      this.metrics = this.metrics.slice(-1000);
    }
  }

  /**
   *
   */
  getMetrics(filter?: {
    category?: string;
    timeRange?: { start: string; end: string };
    limit?: number;
  }): PerformanceMetric[] {
    let filtered = this.metrics;

    if (filter?.category) {
      filtered = filtered.filter((m) => m.category === filter.category);
    }

    if (filter?.timeRange) {
      const start = new Date(filter.timeRange.start).getTime();
      const end = new Date(filter.timeRange.end).getTime();
      filtered = filtered.filter((m) => {
        const timestamp = new Date(m.timestamp).getTime();
        return timestamp >= start && timestamp <= end;
      });
    }

    if (filter?.limit) {
      filtered = filtered.slice(-filter.limit);
    }

    return filtered.sort(
      (a, b) =>
        new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    );
  }

  /**
   *
   */
  clearMetrics(): void {
    this.metrics = [];
  }
}

// Global performance monitor instance
export const performanceMonitor = PerformanceMonitor.getInstance();
