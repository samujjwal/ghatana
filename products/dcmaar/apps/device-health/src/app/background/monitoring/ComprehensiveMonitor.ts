/**
 * @fileoverview Comprehensive Monitor - Health, Performance, Usage Analytics
 *
 * Provides comprehensive monitoring capabilities for the extension including
 * health checking, performance analysis, usage tracking, and anomaly detection.
 *
 * @module app/background/monitoring/ComprehensiveMonitor
 */

import { EventEmitter } from 'events';

/**
 * Health status
 */
export type HealthStatus = 'healthy' | 'degraded' | 'unhealthy';

/**
 * Component health
 */
export interface ComponentHealth {
  name: string;
  status: HealthStatus;
  message?: string;
  lastCheck: number;
  metrics?: Record<string, number>;
}

/**
 * Health report
 */
export interface HealthReport {
  overall: HealthStatus;
  components: Record<string, ComponentHealth>;
  metrics: {
    uptime: number;
    errorRate: number;
    latency: number;
  };
  timestamp: number;
}

/**
 * Performance metrics
 */
export interface PerformanceMetrics {
  cpu: number;
  memory: number;
  eventRate: number;
  latency: { p50: number; p90: number; p99: number };
  throughput: number;
}

/**
 * Performance report
 */
export interface PerformanceReport {
  bottlenecks: Array<{ component: string; severity: 'low' | 'medium' | 'high'; description: string }>;
  slowQueries: Array<{ query: string; duration: number; timestamp: number }>;
  memoryLeaks: Array<{ component: string; growth: number; timestamp: number }>;
  recommendations: string[];
  metrics: PerformanceMetrics;
  timestamp: number;
}

/**
 * Usage event
 */
export interface UsageEvent {
  feature: string;
  action: string;
  timestamp: number;
  metadata?: Record<string, any>;
}

/**
 * Usage report
 */
export interface UsageReport {
  features: Record<string, { count: number; lastUsed: number }>;
  userFlows: Array<{ flow: string[]; count: number }>;
  engagement: {
    activeTime: number;
    sessionCount: number;
    avgSessionDuration: number;
  };
  timestamp: number;
}

/**
 * Anomaly
 */
export interface Anomaly {
  type: 'spike' | 'drop' | 'pattern' | 'threshold';
  metric: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  description: string;
  value: number;
  threshold: number;
  timestamp: number;
}

/**
 * Monitor configuration
 */
export interface MonitorConfig {
  /** Health check interval in milliseconds */
  healthCheckInterval?: number;

  /** Performance analysis interval in milliseconds */
  performanceInterval?: number;

  /** Anomaly detection enabled */
  anomalyDetection?: boolean;

  /** Anomaly thresholds */
  anomalyThresholds?: {
    errorRate?: number;
    latency?: number;
    memoryGrowth?: number;
  };
}

/**
 * Comprehensive Monitor
 *
 * Monitors health, performance, usage, and detects anomalies.
 */
export class ComprehensiveMonitor extends EventEmitter {
  private config: Required<MonitorConfig>;
  private startTime: number;
  private healthCheckTimer: NodeJS.Timeout | null = null;
  private performanceTimer: NodeJS.Timeout | null = null;
  
  // Component health tracking
  private componentHealth = new Map<string, ComponentHealth>();
  
  // Plugin system adapter (optional, integrated when available)
  private pluginAdapter: any = null;
  
  // Performance tracking
  private latencyHistory: number[] = [];
  private eventRateHistory: number[] = [];
  private memoryHistory: number[] = [];
  
  // Usage tracking
  private usageEvents: UsageEvent[] = [];
  private sessionStart: number;
  private sessionCount = 0;
  
  // Error tracking
  private errorCount = 0;
  private totalEvents = 0;

  constructor(config: MonitorConfig = {}) {
    super();
    
    this.config = {
      healthCheckInterval: config.healthCheckInterval || 30000,
      performanceInterval: config.performanceInterval || 60000,
      anomalyDetection: config.anomalyDetection !== false,
      anomalyThresholds: {
        errorRate: 0.05, // 5%
        latency: 1000, // 1 second
        memoryGrowth: 50 * 1024 * 1024, // 50MB
        ...config.anomalyThresholds,
      },
    };

    this.startTime = Date.now();
    this.sessionStart = Date.now();
  }

  /**
   * Start monitoring
   */
  start(): void {
    // Start health checks
    this.healthCheckTimer = setInterval(() => {
      void this.performHealthCheck();
    }, this.config.healthCheckInterval);

    // Start performance analysis
    this.performanceTimer = setInterval(() => {
      void this.performPerformanceAnalysis();
    }, this.config.performanceInterval);

    // Initial checks
    void this.performHealthCheck();
    void this.performPerformanceAnalysis();
  }

  /**
   * Stop monitoring
   */
  stop(): void {
    if (this.healthCheckTimer) {
      clearInterval(this.healthCheckTimer);
      this.healthCheckTimer = null;
    }

    if (this.performanceTimer) {
      clearInterval(this.performanceTimer);
      this.performanceTimer = null;
    }
  }

  /**
   * Set the plugin system adapter for integrated monitoring
   * @param adapter PluginSystemAdapter instance for plugin metrics collection
   */
  setPluginAdapter(adapter: any): void {
    this.pluginAdapter = adapter;
  }

  /**
   * Get plugin health metrics if adapter is available
   * @private
   */
  private async getPluginHealthMetrics(): Promise<Record<string, ComponentHealth>> {
    const pluginHealth: Record<string, ComponentHealth> = {};

    if (!this.pluginAdapter) {
      return pluginHealth;
    }

    try {
      const report = await this.pluginAdapter.getPluginHealthReport();

      for (const [pluginId, health] of Object.entries(report)) {
        const healthData = health as Record<string, any>;
        pluginHealth[`plugin:${pluginId}`] = {
          name: `plugin:${pluginId}`,
          status: (healthData.status || 'healthy') as HealthStatus,
          message: healthData.message,
          lastCheck: Date.now(),
          metrics: healthData.metrics as Record<string, number>,
        };
      }
    } catch (error) {
      // Log but don't fail health check if plugin adapter fails
       
      console.warn('Failed to get plugin health metrics:', error);
    }

    return pluginHealth;
  }

  /**
   * Register component for health checking
   */
  registerComponent(name: string, healthCheck: () => Promise<ComponentHealth>): void {
    // Store health check function
    (this as any)[`healthCheck_${name}`] = healthCheck;
  }

  /**
   * Check health of all components
   */
  async checkHealth(): Promise<HealthReport> {
    const components: Record<string, ComponentHealth> = {};
    let overallStatus: HealthStatus = 'healthy';

    // Check each registered component
    for (const [name, health] of this.componentHealth.entries()) {
      components[name] = health;

      if (health.status === 'unhealthy') {
        overallStatus = 'unhealthy';
      } else if (health.status === 'degraded' && overallStatus === 'healthy') {
        overallStatus = 'degraded';
      }
    }

    // Include plugin health metrics if adapter available
    const pluginMetrics = await this.getPluginHealthMetrics();
    Object.assign(components, pluginMetrics);

    // Update overall status based on plugin health
    for (const health of Object.values(pluginMetrics)) {
      if (health.status === 'unhealthy') {
        overallStatus = 'unhealthy';
      } else if (health.status === 'degraded' && overallStatus === 'healthy') {
        overallStatus = 'degraded';
      }
    }

    const errorRate = this.totalEvents > 0 ? this.errorCount / this.totalEvents : 0;
    const latency = this.calculatePercentile(this.latencyHistory, 0.95);

    return {
      overall: overallStatus,
      components,
      metrics: {
        uptime: Date.now() - this.startTime,
        errorRate,
        latency,
      },
      timestamp: Date.now(),
    };
  }

  /**
   * Analyze performance
   */
  async analyzePerformance(): Promise<PerformanceReport> {
    const metrics = await this.collectPerformanceMetrics();
    const bottlenecks = this.identifyBottlenecks(metrics);
    const recommendations = this.generateRecommendations(metrics, bottlenecks);

    return {
      bottlenecks,
      slowQueries: [],
      memoryLeaks: this.detectMemoryLeaks(),
      recommendations,
      metrics,
      timestamp: Date.now(),
    };
  }

  /**
   * Track usage event
   */
  trackUsage(event: UsageEvent): void {
    this.usageEvents.push({
      ...event,
      timestamp: event.timestamp || Date.now(),
    });

    // Keep only last 1000 events
    if (this.usageEvents.length > 1000) {
      this.usageEvents.shift();
    }

    this.emit('usage-event', event);
  }

  /**
   * Get usage report
   */
  getUsageReport(): UsageReport {
    const features: Record<string, { count: number; lastUsed: number }> = {};
    const flows: Map<string, number> = new Map();

    // Aggregate feature usage
    for (const event of this.usageEvents) {
      if (!features[event.feature]) {
        features[event.feature] = { count: 0, lastUsed: 0 };
      }
      features[event.feature].count++;
      features[event.feature].lastUsed = Math.max(features[event.feature].lastUsed, event.timestamp);
    }

    // Calculate engagement
    const sessionDuration = Date.now() - this.sessionStart;
    const avgSessionDuration = this.sessionCount > 0 ? sessionDuration / this.sessionCount : 0;

    return {
      features,
      userFlows: Array.from(flows.entries()).map(([flow, count]) => ({
        flow: flow.split('->'),
        count,
      })),
      engagement: {
        activeTime: sessionDuration,
        sessionCount: this.sessionCount,
        avgSessionDuration,
      },
      timestamp: Date.now(),
    };
  }

  /**
   * Detect anomalies
   */
  async detectAnomalies(): Promise<Anomaly[]> {
    if (!this.config.anomalyDetection) {
      return [];
    }

    const anomalies: Anomaly[] = [];
    const errorRate = this.totalEvents > 0 ? this.errorCount / this.totalEvents : 0;
    const latency = this.calculatePercentile(this.latencyHistory, 0.95);

    // Check error rate
    if (errorRate > this.config.anomalyThresholds.errorRate!) {
      anomalies.push({
        type: 'threshold',
        metric: 'error_rate',
        severity: errorRate > 0.1 ? 'critical' : 'high',
        description: `Error rate ${(errorRate * 100).toFixed(2)}% exceeds threshold`,
        value: errorRate,
        threshold: this.config.anomalyThresholds.errorRate!,
        timestamp: Date.now(),
      });
    }

    // Check latency
    if (latency > this.config.anomalyThresholds.latency!) {
      anomalies.push({
        type: 'threshold',
        metric: 'latency',
        severity: latency > 5000 ? 'critical' : 'high',
        description: `P95 latency ${latency}ms exceeds threshold`,
        value: latency,
        threshold: this.config.anomalyThresholds.latency!,
        timestamp: Date.now(),
      });
    }

    return anomalies;
  }

  /**
   * Record event processing
   */
  recordEvent(latency: number, error: boolean = false): void {
    this.totalEvents++;
    if (error) {
      this.errorCount++;
    }

    this.latencyHistory.push(latency);
    if (this.latencyHistory.length > 1000) {
      this.latencyHistory.shift();
    }
  }

  /**
   * Perform health check
   */
  private async performHealthCheck(): Promise<void> {
    const report = await this.checkHealth();
    this.emit('health-check', report);

    if (report.overall === 'unhealthy') {
      this.emit('health-alert', report);
    }
  }

  /**
   * Perform performance analysis
   */
  private async performPerformanceAnalysis(): Promise<void> {
    const report = await this.analyzePerformance();
    this.emit('performance-analysis', report);

    const criticalBottlenecks = report.bottlenecks.filter(b => b.severity === 'high');
    if (criticalBottlenecks.length > 0) {
      this.emit('performance-alert', report);
    }
  }

  /**
   * Collect performance metrics
   */
  private async collectPerformanceMetrics(): Promise<PerformanceMetrics> {
    const memory = (performance as any).memory?.usedJSHeapSize || 0;
    this.memoryHistory.push(memory);
    if (this.memoryHistory.length > 100) {
      this.memoryHistory.shift();
    }

    return {
      cpu: 0, // TODO: Implement CPU tracking
      memory,
      eventRate: this.calculateEventRate(),
      latency: {
        p50: this.calculatePercentile(this.latencyHistory, 0.5),
        p90: this.calculatePercentile(this.latencyHistory, 0.9),
        p99: this.calculatePercentile(this.latencyHistory, 0.99),
      },
      throughput: this.totalEvents / ((Date.now() - this.startTime) / 1000),
    };
  }

  /**
   * Identify bottlenecks
   */
  private identifyBottlenecks(metrics: PerformanceMetrics): PerformanceReport['bottlenecks'] {
    const bottlenecks: PerformanceReport['bottlenecks'] = [];

    if (metrics.latency.p99 > 1000) {
      bottlenecks.push({
        component: 'event-processing',
        severity: 'high',
        description: `P99 latency ${metrics.latency.p99}ms is high`,
      });
    }

    if (metrics.memory > 100 * 1024 * 1024) {
      bottlenecks.push({
        component: 'memory',
        severity: 'medium',
        description: `Memory usage ${(metrics.memory / 1024 / 1024).toFixed(2)}MB is high`,
      });
    }

    return bottlenecks;
  }

  /**
   * Detect memory leaks
   */
  private detectMemoryLeaks(): PerformanceReport['memoryLeaks'] {
    if (this.memoryHistory.length < 10) {
      return [];
    }

    const recent = this.memoryHistory.slice(-10);
    const growth = recent[recent.length - 1] - recent[0];

    if (growth > this.config.anomalyThresholds.memoryGrowth!) {
      return [{
        component: 'extension',
        growth,
        timestamp: Date.now(),
      }];
    }

    return [];
  }

  /**
   * Generate recommendations
   */
  private generateRecommendations(metrics: PerformanceMetrics, bottlenecks: PerformanceReport['bottlenecks']): string[] {
    const recommendations: string[] = [];

    if (bottlenecks.some(b => b.component === 'event-processing')) {
      recommendations.push('Consider increasing batch size or flush interval');
      recommendations.push('Review processor chain for optimization opportunities');
    }

    if (bottlenecks.some(b => b.component === 'memory')) {
      recommendations.push('Enable auto-cleanup for old events');
      recommendations.push('Reduce retention period or max events');
    }

    return recommendations;
  }

  /**
   * Calculate percentile
   */
  private calculatePercentile(values: number[], percentile: number): number {
    if (values.length === 0) return 0;

    const sorted = [...values].sort((a, b) => a - b);
    const index = Math.ceil(sorted.length * percentile) - 1;
    return sorted[Math.max(0, index)];
  }

  /**
   * Calculate event rate
   */
  private calculateEventRate(): number {
    const duration = (Date.now() - this.startTime) / 1000;
    return duration > 0 ? this.totalEvents / duration : 0;
  }
}
