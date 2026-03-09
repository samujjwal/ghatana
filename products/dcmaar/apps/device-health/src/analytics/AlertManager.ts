/**
 * @fileoverview Alert Manager
 *
 * Evaluates processed metrics against configured thresholds and
 * notifies registered channels/listeners when an alert is generated.
 *
 * @module analytics/AlertManager
 */

import type { ProcessedMetrics } from './AnalyticsPipeline';

export type AlertSeverity = 'info' | 'warning' | 'critical';

export interface Alert {
  id: string;
  metric: string;
  severity: AlertSeverity;
  message: string;
  value: number;
  threshold: number;
  timestamp: number;
  metadata?: Record<string, unknown>;
}

export interface AlertRule {
  id: string;
  metric: string;
  operator: '>' | '>=' | '<' | '<=';
  threshold: number;
  severity: AlertSeverity;
  message?: string;
}

export interface AlertChannel {
  id: string;
  notify(alert: Alert): Promise<void>;
}

type AlertListener = (alert: Alert) => void;

export class AlertManager {
  private alertRules: AlertRule[] = [];
  private channels: AlertChannel[] = [];
  private listeners = new Set<AlertListener>();

  addAlertRule(rule: AlertRule): void {
    this.alertRules.push(rule);
  }

  setAlertRules(rules: AlertRule[]): void {
    this.alertRules = [...rules];
  }

  addAlertChannel(channel: AlertChannel): void {
    this.channels.push(channel);
  }

  onAlert(listener: AlertListener): void {
    this.listeners.add(listener);
  }

  offAlert(listener: AlertListener): void {
    this.listeners.delete(listener);
  }

  async checkThresholds(metrics: ProcessedMetrics): Promise<Alert[]> {
    const alerts: Alert[] = [];

    for (const rule of this.alertRules) {
      const value = metrics.summary[rule.metric];
      if (value === undefined) {
        continue;
      }

      if (this.evaluate(value, rule.operator, rule.threshold)) {
        const alert: Alert = {
          id: `${rule.id}:${metrics.timestamp}`,
          metric: rule.metric,
          severity: rule.severity,
          message:
            rule.message ??
            `Metric ${rule.metric} breached threshold (${value} ${rule.operator} ${rule.threshold})`,
          value,
          threshold: rule.threshold,
          timestamp: metrics.timestamp,
        };
        alerts.push(alert);
        await this.sendAlert(alert);
      }
    }

    return alerts;
  }

  async sendAlert(alert: Alert): Promise<void> {
    this.listeners.forEach((listener) => listener(alert));

    await Promise.allSettled(this.channels.map((channel) => channel.notify(alert)));
  }

  private evaluate(value: number, operator: AlertRule['operator'], threshold: number): boolean {
    switch (operator) {
      case '>':
        return value > threshold;
      case '>=':
        return value >= threshold;
      case '<':
        return value < threshold;
      case '<=':
        return value <= threshold;
      default:
        return false;
    }
  }
}
