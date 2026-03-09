/**
 * Comprehensive Monitoring and Alerting Framework
 * 
 * Provides metrics collection, health monitoring, and alerting
 * for the Tutorputor platform with support for multiple backends.
 */

import { EventEmitter } from 'events';
import { createLogger } from '../utils/logger.js';

const logger = createLogger('monitoring');

// ============================================================================
// Metrics Collection
// ============================================================================

export interface MetricValue {
  value: number;
  timestamp: Date;
  labels?: Record<string, string>;
}

export interface Metric {
  name: string;
  type: 'counter' | 'gauge' | 'histogram' | 'timer';
  help?: string;
  values: MetricValue[];
  aggregation?: 'sum' | 'average' | 'min' | 'max' | 'last';
}

export class MetricsRegistry {
  private metrics = new Map<string, Metric>();
  private maxValuesPerMetric = 1000;

  registerMetric(name: string, type: Metric['type'], help?: string): void {
    if (this.metrics.has(name)) {
      return;
    }

    this.metrics.set(name, {
      name,
      type,
      help,
      values: [],
      aggregation: type === 'counter' ? 'sum' : type === 'gauge' ? 'last' : 'average',
    });
  }

  increment(name: string, value: number = 1, labels?: Record<string, string>): void {
    const metric = this.metrics.get(name);
    if (!metric || metric.type !== 'counter') {
      return;
    }

    metric.values.push({
      value,
      timestamp: new Date(),
      labels,
    });

    this.cleanupOldValues(metric);
  }

  set(name: string, value: number, labels?: Record<string, string>): void {
    const metric = this.metrics.get(name);
    if (!metric || metric.type !== 'gauge') {
      return;
    }

    metric.values.push({
      value,
      timestamp: new Date(),
      labels,
    });

    this.cleanupOldValues(metric);
  }

  observe(name: string, value: number, labels?: Record<string, string>): void {
    const metric = this.metrics.get(name);
    if (!metric || metric.type !== 'histogram') {
      return;
    }

    metric.values.push({
      value,
      timestamp: new Date(),
      labels,
    });

    this.cleanupOldValues(metric);
  }

  timer(name: string, labels?: Record<string, string>): () => void {
    const start = Date.now();
    return () => {
      const duration = Date.now() - start;
      this.observe(name, duration, labels);
    };
  }

  private cleanupOldValues(metric: Metric): void {
    if (metric.values.length > this.maxValuesPerMetric) {
      metric.values = metric.values.slice(-this.maxValuesPerMetric);
    }
  }

  getValue(name: string, labels?: Record<string, string>): number | undefined {
    const metric = this.metrics.get(name);
    if (!metric) {
      return undefined;
    }

    const filteredValues = labels
      ? metric.values.filter(v => this.matchLabels(v.labels, labels))
      : metric.values;

    if (filteredValues.length === 0) {
      return undefined;
    }

    switch (metric.aggregation) {
      case 'sum':
        return filteredValues.reduce((sum, v) => sum + v.value, 0);
      case 'average':
        return filteredValues.reduce((sum, v) => sum + v.value, 0) / filteredValues.length;
      case 'min':
        return Math.min(...filteredValues.map(v => v.value));
      case 'max':
        return Math.max(...filteredValues.map(v => v.value));
      case 'last':
        return filteredValues[filteredValues.length - 1].value;
      default:
        return filteredValues[0].value;
    }
  }

  private matchLabels(metricLabels?: Record<string, string>, filterLabels?: Record<string, string>): boolean {
    if (!filterLabels) return true;
    if (!metricLabels) return false;

    for (const [key, value] of Object.entries(filterLabels)) {
      if (metricLabels[key] !== value) {
        return false;
      }
    }

    return true;
  }

  getAllMetrics(): Record<string, Metric> {
    const result: Record<string, Metric> = {};
    for (const [name, metric] of this.metrics) {
      result[name] = { ...metric };
    }
    return result;
  }

  reset(): void {
    for (const metric of this.metrics.values()) {
      metric.values = [];
    }
  }
}

// ============================================================================
// Health Checks
// ============================================================================

export interface HealthCheckResult {
  status: 'healthy' | 'unhealthy' | 'degraded';
  message?: string;
  details?: Record<string, any>;
  timestamp: Date;
  duration?: number;
}

export interface HealthCheck {
  name: string;
  timeout?: number;
  check(): Promise<HealthCheckResult>;
}

export class HealthCheckRegistry {
  private checks = new Map<string, HealthCheck>();

  register(check: HealthCheck): void {
    this.checks.set(check.name, check);
  }

  unregister(name: string): void {
    this.checks.delete(name);
  }

  async runCheck(name: string): Promise<HealthCheckResult> {
    const check = this.checks.get(name);
    if (!check) {
      throw new Error(`Health check '${name}' not found`);
    }

    const start = Date.now();
    const timeout = check.timeout || 30000;

    try {
      const result = await Promise.race([
        check.check(),
        new Promise<HealthCheckResult>((_, reject) =>
          setTimeout(() => reject(new Error('Health check timeout')), timeout)
        ),
      ]);

      return {
        ...result,
        duration: Date.now() - start,
      };
    } catch (error) {
      return {
        status: 'unhealthy',
        message: error instanceof Error ? error.message : 'Unknown error',
        duration: Date.now() - start,
        timestamp: new Date(),
      };
    }
  }

  async runAllChecks(): Promise<Record<string, HealthCheckResult>> {
    const results: Record<string, HealthCheckResult> = {};
    const promises = Array.from(this.checks.keys()).map(async (name) => {
      results[name] = await this.runCheck(name);
    });

    await Promise.allSettled(promises);
    return results;
  }

  getOverallStatus(results: Record<string, HealthCheckResult>): 'healthy' | 'degraded' | 'unhealthy' {
    const statuses = Object.values(results).map(r => r.status);
    
    if (statuses.includes('unhealthy')) {
      return 'unhealthy';
    }
    
    if (statuses.includes('degraded')) {
      return 'degraded';
    }
    
    return 'healthy';
  }
}

// ============================================================================
// Alerting System
// ============================================================================

export interface Alert {
  id: string;
  name: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  status: 'firing' | 'resolved';
  message: string;
  details?: Record<string, any>;
  timestamp: Date;
  resolvedAt?: Date;
  labels?: Record<string, string>;
}

export interface AlertRule {
  id: string;
  name: string;
  condition: (metrics: Record<string, Metric>) => boolean;
  severity: Alert['severity'];
  message: string;
  cooldown?: number; // ms between alerts
  labels?: Record<string, string>;
  enabled: boolean;
}

export interface AlertChannel {
  name: string;
  type: 'email' | 'slack' | 'webhook' | 'pagerduty';
  config: Record<string, any>;
  send(alert: Alert): Promise<void>;
}

export class AlertManager extends EventEmitter {
  private alerts = new Map<string, Alert>();
  private rules = new Map<string, AlertRule>();
  private channels = new Map<string, AlertChannel>();
  private cooldowns = new Map<string, number>();
  private metricsRegistry: MetricsRegistry;

  constructor(metricsRegistry: MetricsRegistry) {
    super();
    this.metricsRegistry = metricsRegistry;
  }

  addRule(rule: AlertRule): void {
    this.rules.set(rule.id, rule);
  }

  removeRule(ruleId: string): void {
    this.rules.delete(ruleId);
  }

  addChannel(channel: AlertChannel): void {
    this.channels.set(channel.name, channel);
  }

  removeChannel(channelName: string): void {
    this.channels.delete(channelName);
  }

  async evaluateRules(): Promise<void> {
    const metrics = this.metricsRegistry.getAllMetrics();
    const now = Date.now();

    for (const rule of this.rules.values()) {
      if (!rule.enabled) {
        continue;
      }

      // Check cooldown
      const lastAlert = this.cooldowns.get(rule.id);
      if (lastAlert && now - lastAlert < (rule.cooldown || 300000)) {
        continue;
      }

      try {
        const shouldAlert = rule.condition(metrics);
        
        if (shouldAlert) {
          await this.fireAlert(rule);
          this.cooldowns.set(rule.id, now);
        } else {
          await this.resolveAlert(rule.id);
        }
      } catch (error) {
        logger.error({
          ruleId: rule.id,
          error: error instanceof Error ? error.message : String(error),
        }, 'Error evaluating alert rule');
      }
    }
  }

  private async fireAlert(rule: AlertRule): Promise<void> {
    const existingAlert = Array.from(this.alerts.values())
      .find(a => a.labels?.['ruleId'] === rule.id && a.status === 'firing');

    if (existingAlert) {
      return; // Already firing
    }

    const alert: Alert = {
      id: `alert-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      name: rule.name,
      severity: rule.severity,
      status: 'firing',
      message: rule.message,
      timestamp: new Date(),
      labels: { ...rule.labels, ruleId: rule.id },
    };

    this.alerts.set(alert.id, alert);
    this.emit('alert', alert);

    // Send to all channels
    const promises = Array.from(this.channels.values()).map(channel =>
      this.sendToChannel(channel, alert).catch(error =>
        logger.error({
          channel: channel.name,
          alertId: alert.id,
          error: error instanceof Error ? error.message : String(error),
        }, 'Failed to send alert to channel')
      )
    );

    await Promise.allSettled(promises);
  }

  private async resolveAlert(ruleId: string): Promise<void> {
    const existingAlert = Array.from(this.alerts.values())
      .find(a => a.labels?.['ruleId'] === ruleId && a.status === 'firing');

    if (!existingAlert) {
      return;
    }

    existingAlert.status = 'resolved';
    existingAlert.resolvedAt = new Date();
    
    this.emit('resolved', existingAlert);

    // Send resolution to channels
    const promises = Array.from(this.channels.values()).map(channel =>
      this.sendToChannel(channel, existingAlert).catch(error =>
        logger.error({
          channel: channel.name,
          alertId: existingAlert.id,
          error: error instanceof Error ? error.message : String(error),
        }, 'Failed to send alert resolution to channel')
      )
    );

    await Promise.allSettled(promises);
  }

  private async sendToChannel(channel: AlertChannel, alert: Alert): Promise<void> {
    await channel.send(alert);
  }

  getActiveAlerts(): Alert[] {
    return Array.from(this.alerts.values()).filter(a => a.status === 'firing');
  }

  getAllAlerts(): Alert[] {
    return Array.from(this.alerts.values());
  }
}

// ============================================================================
// Built-in Health Checks
// ============================================================================

export class DatabaseHealthCheck implements HealthCheck {
  name = 'database';
  timeout = 5000;

  constructor(private db: any) {}

  async check(): Promise<HealthCheckResult> {
    try {
      await this.db.$queryRaw`SELECT 1`;
      return {
        status: 'healthy',
        message: 'Database connection successful',
        timestamp: new Date(),
      };
    } catch (error) {
      return {
        status: 'unhealthy',
        message: `Database connection failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        timestamp: new Date(),
      };
    }
  }
}

export class RedisHealthCheck implements HealthCheck {
  name = 'redis';
  timeout = 3000;

  constructor(private redis: any) {}

  async check(): Promise<HealthCheckResult> {
    try {
      await this.redis.ping();
      return {
        status: 'healthy',
        message: 'Redis connection successful',
        timestamp: new Date(),
      };
    } catch (error) {
      return {
        status: 'unhealthy',
        message: `Redis connection failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        timestamp: new Date(),
      };
    }
  }
}

export class ExternalServiceHealthCheck implements HealthCheck {
  name: string;
  timeout = 10000;

  constructor(
    private url: string,
    private expectedStatus = 200,
    name?: string
  ) {
    this.name = name || `external-${url.replace(/[^a-zA-Z0-9]/g, '-')}`;
  }

  async check(): Promise<HealthCheckResult> {
    try {
      const response = await fetch(this.url, {
        method: 'GET',
        signal: AbortSignal.timeout(this.timeout || 10000),
      });

      if (response.status === this.expectedStatus) {
        return {
          status: 'healthy',
          message: `External service responded with ${response.status}`,
          timestamp: new Date(),
        };
      } else {
        return {
          status: 'degraded',
          message: `External service responded with ${response.status}, expected ${this.expectedStatus}`,
          timestamp: new Date(),
        };
      }
    } catch (error) {
      return {
        status: 'unhealthy',
        message: `External service check failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        timestamp: new Date(),
      };
    }
  }
}

// ============================================================================
// Built-in Alert Channels
// ============================================================================

export class EmailAlertChannel implements AlertChannel {
  name: string;
  type: 'email' | 'slack' | 'webhook' | 'pagerduty' = 'email';

  constructor(
    public config: {
      smtp: {
        host: string;
        port: number;
        secure: boolean;
        auth: { user: string; pass: string };
      };
      from: string;
      to: string[];
      name?: string;
    }
  ) {
    this.name = config.name || 'email';
  }

  async send(alert: Alert): Promise<void> {
    // Implementation would use nodemailer or similar
    logger.info({
      alertId: alert.id,
      alertName: alert.name,
      severity: alert.severity,
      message: alert.message,
      recipients: this.config.to,
    }, 'Email alert sent (placeholder implementation)');
  }
}

export class WebhookAlertChannel implements AlertChannel {
  name: string;
  type: 'email' | 'slack' | 'webhook' | 'pagerduty' = 'webhook';

  constructor(
    public config: {
      url: string;
      headers?: Record<string, string>;
      name?: string;
    }
  ) {
    this.name = config.name || 'webhook';
  }

  async send(alert: Alert): Promise<void> {
    try {
      const response = await fetch(this.config.url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...this.config.headers,
        },
        body: JSON.stringify(alert),
      });

      if (!response.ok) {
        throw new Error(`Webhook failed with status ${response.status}`);
      }
    } catch (error) {
      logger.error({
        alertId: alert.id,
        webhookUrl: this.config.url,
        error: error instanceof Error ? error.message : String(error),
      }, 'Failed to send webhook alert');
      throw error;
    }
  }
}

// ============================================================================
// Monitoring Manager
// ============================================================================

export class MonitoringManager {
  private metricsRegistry: MetricsRegistry;
  private healthCheckRegistry: HealthCheckRegistry;
  private alertManager: AlertManager;
  private evaluationInterval?: NodeJS.Timeout;

  constructor() {
    this.metricsRegistry = new MetricsRegistry();
    this.healthCheckRegistry = new HealthCheckRegistry();
    this.alertManager = new AlertManager(this.metricsRegistry);
    
    this.setupDefaultMetrics();
    this.setupDefaultAlerts();
  }

  private setupDefaultMetrics(): void {
    // System metrics
    this.metricsRegistry.registerMetric('http_requests_total', 'counter', 'Total HTTP requests');
    this.metricsRegistry.registerMetric('http_request_duration_ms', 'histogram', 'HTTP request duration');
    this.metricsRegistry.registerMetric('http_errors_total', 'counter', 'Total HTTP errors');
    
    // Database metrics
    this.metricsRegistry.registerMetric('db_connections_active', 'gauge', 'Active database connections');
    this.metricsRegistry.registerMetric('db_query_duration_ms', 'histogram', 'Database query duration');
    this.metricsRegistry.registerMetric('db_errors_total', 'counter', 'Total database errors');
    
    // Business metrics
    this.metricsRegistry.registerMetric('users_active', 'gauge', 'Active users');
    this.metricsRegistry.registerMetric('modules_accessed_total', 'counter', 'Total modules accessed');
    this.metricsRegistry.registerMetric('assessments_completed_total', 'counter', 'Total assessments completed');
    
    // Resilience metrics
    this.metricsRegistry.registerMetric('circuit_breaker_trips_total', 'counter', 'Circuit breaker trips');
    this.metricsRegistry.registerMetric('retry_attempts_total', 'counter', 'Retry attempts');
    this.metricsRegistry.registerMetric('bulkhead_rejections_total', 'counter', 'Bulkhead rejections');
  }

  private setupDefaultAlerts(): void {
    // High error rate alert
    this.alertManager.addRule({
      id: 'high_error_rate',
      name: 'High Error Rate',
      condition: (metrics) => {
        const requests = metrics['http_requests_total'];
        const errors = metrics['http_errors_total'];
        
        if (!requests || !errors) return false;
        
        const requestCount = this.metricsRegistry.getValue('http_requests_total');
        const errorCount = this.metricsRegistry.getValue('http_errors_total');
        
        return requestCount && requestCount > 0 && errorCount && errorCount / requestCount > 0.1;
      },
      severity: 'high',
      message: 'Error rate is above 10%',
      cooldown: 300000, // 5 minutes
    });

    // Database connection issues
    this.alertManager.addRule({
      id: 'db_connection_issues',
      name: 'Database Connection Issues',
      condition: (metrics) => {
        const errors = this.metricsRegistry.getValue('db_errors_total');
        return errors && errors > 5;
      },
      severity: 'critical',
      message: 'Database errors detected',
      cooldown: 60000, // 1 minute
    });

    // High response time
    this.alertManager.addRule({
      id: 'high_response_time',
      name: 'High Response Time',
      condition: (metrics) => {
        const avgDuration = this.metricsRegistry.getValue('http_request_duration_ms');
        return avgDuration && avgDuration > 5000;
      },
      severity: 'medium',
      message: 'Average response time is above 5 seconds',
      cooldown: 600000, // 10 minutes
    });
  }

  startEvaluation(intervalMs: number = 30000): void {
    if (this.evaluationInterval) {
      clearInterval(this.evaluationInterval);
    }

    this.evaluationInterval = setInterval(async () => {
      try {
        await this.alertManager.evaluateRules();
      } catch (error) {
        logger.error({
          error: error instanceof Error ? error.message : String(error),
        }, 'Error during alert evaluation');
      }
    }, intervalMs);
  }

  stopEvaluation(): void {
    if (this.evaluationInterval) {
      clearInterval(this.evaluationInterval);
      this.evaluationInterval = undefined;
    }
  }

  // Getters
  get metrics(): MetricsRegistry {
    return this.metricsRegistry;
  }

  get healthChecks(): HealthCheckRegistry {
    return this.healthCheckRegistry;
  }

  get alerts(): AlertManager {
    return this.alertManager;
  }

  // Convenience methods
  incrementHttpRequests(labels?: Record<string, string>): void {
    this.metricsRegistry.increment('http_requests_total', 1, labels);
  }

  recordHttpRequestDuration(duration: number, labels?: Record<string, string>): void {
    this.metricsRegistry.observe('http_request_duration_ms', duration, labels);
  }

  incrementHttpErrors(labels?: Record<string, string>): void {
    this.metricsRegistry.increment('http_errors_total', 1, labels);
  }

  async getSystemHealth(): Promise<{
    status: 'healthy' | 'degraded' | 'unhealthy';
    checks: Record<string, HealthCheckResult>;
    metrics: Record<string, Metric>;
    alerts: Alert[];
  }> {
    const [checks, metrics, alerts] = await Promise.all([
      this.healthCheckRegistry.runAllChecks(),
      Promise.resolve(this.metricsRegistry.getAllMetrics()),
      Promise.resolve(this.alertManager.getActiveAlerts()),
    ]);

    const status = this.healthCheckRegistry.getOverallStatus(checks);

    return {
      status,
      checks,
      metrics,
      alerts,
    };
  }
}

// Global instance
export const monitoring = new MonitoringManager();
