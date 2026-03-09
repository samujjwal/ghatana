/**
 * Cost Notification Service - Manages cost alerts and notifications
 *
 * <p><b>Purpose</b><br>
 * Generates alerts for cost anomalies and threshold violations.
 * Supports multiple notification channels (email, Slack, webhook, SMS).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const service = new CostNotificationService(repository);
 * const alerts = await service.checkAnomalies();
 * await service.notifyViaChannel(alerts, 'slack');
 * }</pre>
 *
 * <p><b>Alert Types</b><br>
 * - THRESHOLD_EXCEEDED: Daily/monthly cost exceeds threshold
 * - SPIKE_DETECTED: Sudden cost increase detected
 * - BUDGET_WARNING: Forecasted costs exceed budget
 * - TREND_ALERT: Significant trend change detected
 * - RESOURCE_ALERT: Specific resource cost spike
 *
 * @doc.type class
 * @doc.purpose Manages cost alerts and notifications
 * @doc.layer product
 * @doc.pattern Service
 */

import { CloudCostRepository, DateRange } from '../../repositories/CloudCostRepository';
import { CloudCost } from '../../models/cost/CloudCost.entity';
import {
  calculateAverageCost,
  calculateStandardDeviation,
} from '../../utils/cost/CostCalculations';

/**
 * Alert severity levels
 */
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

/**
 * Alert type enumeration
 */
export type AlertType =
  | 'THRESHOLD_EXCEEDED'
  | 'SPIKE_DETECTED'
  | 'BUDGET_WARNING'
  | 'TREND_ALERT'
  | 'RESOURCE_ALERT';

/**
 * Cost alert
 */
export interface CostAlert {
  readonly id: string;
  readonly type: AlertType;
  readonly severity: AlertSeverity;
  readonly title: string;
  readonly description: string;
  readonly affectedResources: ReadonlyArray<string>;
  readonly metricValue: number;
  readonly threshold: number;
  readonly timestamp: Date;
  readonly actionItems: ReadonlyArray<string>;
  readonly metadata: Record<string, unknown>;
}

/**
 * Alert rule configuration
 */
export interface AlertRule {
  readonly id: string;
  readonly name: string;
  readonly enabled: boolean;
  readonly type: AlertType;
  readonly threshold: number;
  readonly window: 'daily' | 'weekly' | 'monthly';
  readonly severity: AlertSeverity;
  readonly notificationChannels: ReadonlyArray<'email' | 'slack' | 'webhook' | 'sms'>;
  readonly recipients: ReadonlyArray<string>;
}

/**
 * Notification configuration
 */
export interface NotificationConfig {
  readonly channel: 'email' | 'slack' | 'webhook' | 'sms';
  readonly webhookUrl?: string;
  readonly slackChannel?: string;
  readonly emailAddresses?: ReadonlyArray<string>;
  readonly phoneNumbers?: ReadonlyArray<string>;
}

/**
 * CostNotificationService implementation
 */
export class CostNotificationService {
  // In-memory storage for alert rules
  private alertRules: Map<string, AlertRule> = new Map();

  // In-memory storage for recent alerts
  private recentAlerts: CostAlert[] = [];

  /**
   * Initialize service with repository
   * @param repository Data access layer for costs
   */
  constructor(private readonly repository: CloudCostRepository) {
    this.initializeDefaultRules();
  }

  /**
   * Check for cost anomalies based on configured rules
   * @returns Array of triggered alerts
   */
  async checkAnomalies(): Promise<CostAlert[]> {
    const alerts: CostAlert[] = [];

    // Check threshold alerts
    alerts.push(...(await this.checkThresholdAlerts()));

    // Check spike alerts
    alerts.push(...(await this.checkSpikeAlerts()));

    // Check trend alerts
    alerts.push(...(await this.checkTrendAlerts()));

    // Store recent alerts
    this.recentAlerts = [...this.recentAlerts, ...alerts].slice(-100); // Keep last 100

    return alerts;
  }

  /**
   * Get recent alerts
   * @param limit Maximum number of alerts to return
   * @returns Recent cost alerts
   */
  getRecentAlerts(limit: number = 10): ReadonlyArray<CostAlert> {
    return this.recentAlerts.slice(-limit).reverse();
  }

  /**
   * Create or update alert rule
   * @param rule Alert rule configuration
   */
  addAlertRule(rule: AlertRule): void {
    this.alertRules.set(rule.id, rule);
  }

  /**
   * Remove alert rule
   * @param ruleId ID of rule to remove
   */
  removeAlertRule(ruleId: string): void {
    this.alertRules.delete(ruleId);
  }

  /**
   * Get all alert rules
   * @returns Map of alert rules
   */
  getAlertRules(): ReadonlyMap<string, AlertRule> {
    return new Map(this.alertRules);
  }

  /**
   * Enable or disable alert rule
   * @param ruleId ID of rule to toggle
   * @param enabled Whether to enable the rule
   */
  setRuleEnabled(ruleId: string, enabled: boolean): void {
    const rule = this.alertRules.get(ruleId);
    if (rule) {
      this.alertRules.set(ruleId, { ...rule, enabled });
    }
  }

  /**
   * Send notification for alerts
   * @param alerts Alerts to notify about
   * @param channel Notification channel
   */
  async notifyViaChannel(
    alerts: ReadonlyArray<CostAlert>,
    channel: 'email' | 'slack' | 'webhook' | 'sms'
  ): Promise<void> {
    if (alerts.length === 0) {
      return;
    }

    switch (channel) {
      case 'email':
        await this.sendEmailNotification(alerts);
        break;
      case 'slack':
        await this.sendSlackNotification(alerts);
        break;
      case 'webhook':
        await this.sendWebhookNotification(alerts);
        break;
      case 'sms':
        await this.sendSmsNotification(alerts);
        break;
    }
  }

  /**
   * Check threshold violations
   */
  private async checkThresholdAlerts(): Promise<CostAlert[]> {
    const alerts: CostAlert[] = [];

    // Get today's costs
    const today = new Date();
    const startOfDay = new Date(today);
    startOfDay.setHours(0, 0, 0, 0);

    const costs = await this.repository.findByPeriod({
      start: startOfDay,
      end: today,
    });

    const totalCost = costs.reduce((sum, c) => sum + c.cost, 0);

    // Check against configured threshold rule
    const thresholdRule = Array.from(this.alertRules.values()).find(
      r => r.type === 'THRESHOLD_EXCEEDED' && r.enabled
    );

    if (thresholdRule && totalCost > thresholdRule.threshold) {
      const alert: CostAlert = {
        id: `alert-${Date.now()}-${Math.random()}`,
        type: 'THRESHOLD_EXCEEDED',
        severity: thresholdRule.severity,
        title: `Cost threshold exceeded: $${totalCost.toFixed(2)} > $${thresholdRule.threshold.toFixed(2)}`,
        description: `Today's costs have exceeded the configured threshold. Current: $${totalCost.toFixed(2)}`,
        affectedResources: costs.map(c => `${c.provider}-${c.service}`),
        metricValue: totalCost,
        threshold: thresholdRule.threshold,
        timestamp: new Date(),
        actionItems: [
          'Review active resources and stop unused ones',
          'Check for runaway jobs or processes',
          'Verify no misconfigured autoscaling',
        ],
        metadata: {
          dailyTotal: totalCost,
          resourceCount: costs.length,
          providers: [...new Set(costs.map(c => c.provider))],
          services: [...new Set(costs.map(c => c.service))],
        },
      };

      alerts.push(alert);
    }

    return alerts;
  }

  /**
   * Check for cost spikes
   */
  private async checkSpikeAlerts(): Promise<CostAlert[]> {
    const alerts: CostAlert[] = [];

    // Get last 30 days of data
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - 30);

    const costs = await this.repository.findByPeriod({
      start: startDate,
      end: endDate,
    });

    if (costs.length < 7) {
      return [];
    }

    // Aggregate daily costs
    const dailyMap: Record<string, number> = {};
    for (const cost of costs) {
      const dateKey = cost.date.toISOString().split('T')[0];
      dailyMap[dateKey] = (dailyMap[dateKey] || 0) + cost.cost;
    }

    const dailyCosts = Object.entries(dailyMap)
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([, cost]) => cost);

    // Calculate average and std dev
    const avgCost = calculateAverageCost(dailyCosts);
    const stdDev = calculateStandardDeviation(dailyCosts);

    // Find spike alert rule
    const spikeRule = Array.from(this.alertRules.values()).find(
      r => r.type === 'SPIKE_DETECTED' && r.enabled
    );

    if (spikeRule) {
      const today = dailyCosts[dailyCosts.length - 1];
      const threshold = avgCost + spikeRule.threshold * stdDev;

      if (today > threshold) {
        const alert: CostAlert = {
          id: `alert-${Date.now()}-${Math.random()}`,
          type: 'SPIKE_DETECTED',
          severity: spikeRule.severity,
          title: `Cost spike detected: $${today.toFixed(2)}`,
          description: `Today's cost ($${today.toFixed(2)}) is ${((today / avgCost - 1) * 100).toFixed(1)}% above average ($${avgCost.toFixed(2)})`,
          affectedResources: costs
            .filter(c => c.date.toISOString().split('T')[0] === Object.keys(dailyMap).pop())
            .map(c => `${c.provider}-${c.service}`),
          metricValue: today,
          threshold,
          timestamp: new Date(),
          actionItems: [
            'Investigate new resources created today',
            'Check for deployment or scaling events',
            'Review CloudTrail/audit logs for changes',
            'Identify spike contributors by service',
          ],
          metadata: {
            todaysCost: today,
            averageCost: avgCost,
            stdDevMultiple: (today - avgCost) / stdDev,
            percentAboveAverage: ((today / avgCost - 1) * 100).toFixed(1),
          },
        };

        alerts.push(alert);
      }
    }

    return alerts;
  }

  /**
   * Check for cost trend changes
   */
  private async checkTrendAlerts(): Promise<CostAlert[]> {
    const alerts: CostAlert[] = [];

    // Get last 3 months
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - 3);

    const costs = await this.repository.findByPeriod({
      start: startDate,
      end: endDate,
    });

    if (costs.length === 0) {
      return [];
    }

    // Group by month
    const monthlyCosts: Record<string, number> = {};
    for (const cost of costs) {
      const monthKey = cost.date.toISOString().substring(0, 7);
      monthlyCosts[monthKey] = (monthlyCosts[monthKey] || 0) + cost.cost;
    }

    const months = Object.entries(monthlyCosts)
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([, cost]) => cost);

    if (months.length < 2) {
      return [];
    }

    const previousMonth = months[months.length - 2];
    const currentMonth = months[months.length - 1];
    const percentChange = ((currentMonth - previousMonth) / previousMonth) * 100;

    const trendRule = Array.from(this.alertRules.values()).find(
      r => r.type === 'TREND_ALERT' && r.enabled
    );

    if (trendRule && Math.abs(percentChange) > trendRule.threshold) {
      const direction = percentChange > 0 ? 'increasing' : 'decreasing';

      const alert: CostAlert = {
        id: `alert-${Date.now()}-${Math.random()}`,
        type: 'TREND_ALERT',
        severity: percentChange > 0 ? trendRule.severity : 'INFO',
        title: `Cost trend ${direction}: ${Math.abs(percentChange).toFixed(1)}%`,
        description: `Monthly costs are ${direction} significantly. ` +
          `Previous: $${previousMonth.toFixed(2)}, Current: $${currentMonth.toFixed(2)}`,
        affectedResources: [...new Set(costs.map(c => c.provider))],
        metricValue: percentChange,
        threshold: trendRule.threshold,
        timestamp: new Date(),
        actionItems:
          percentChange > 0
            ? [
                'Review new resource deployments',
                'Check for increased usage patterns',
                'Validate pricing changes',
              ]
            : [
                'Continue cost optimization efforts',
                'Document what drove the reduction',
              ],
        metadata: {
          previousMonth,
          currentMonth,
          percentChange,
        },
      };

      alerts.push(alert);
    }

    return alerts;
  }

  /**
   * Send email notification (stub implementation)
   */
  private async sendEmailNotification(alerts: ReadonlyArray<CostAlert>): Promise<void> {
    // In production, integrate with email service (SendGrid, SES, etc.)
    console.log(
      `Email notification: ${alerts.length} alerts to configured recipients`
    );

    for (const alert of alerts) {
      console.log(
        `  - [${alert.severity}] ${alert.title} (${alert.timestamp.toISOString()})`
      );
    }
  }

  /**
   * Send Slack notification (stub implementation)
   */
  private async sendSlackNotification(alerts: ReadonlyArray<CostAlert>): Promise<void> {
    // In production, integrate with Slack API
    console.log(
      `Slack notification: ${alerts.length} alerts to configured channel`
    );

    for (const alert of alerts) {
      console.log(
        `  - [${alert.severity}] ${alert.title} (${alert.timestamp.toISOString()})`
      );
    }
  }

  /**
   * Send webhook notification (stub implementation)
   */
  private async sendWebhookNotification(alerts: ReadonlyArray<CostAlert>): Promise<void> {
    // In production, make HTTP POST to configured webhook URL
    console.log(
      `Webhook notification: ${alerts.length} alerts to configured endpoint`
    );

    const payload = {
      timestamp: new Date().toISOString(),
      alertCount: alerts.length,
      alerts: alerts.map(a => ({
        type: a.type,
        severity: a.severity,
        title: a.title,
        metricValue: a.metricValue,
        threshold: a.threshold,
      })),
    };

    console.log(`  Payload: ${JSON.stringify(payload)}`);
  }

  /**
   * Send SMS notification (stub implementation)
   */
  private async sendSmsNotification(alerts: ReadonlyArray<CostAlert>): Promise<void> {
    // In production, integrate with SMS service (Twilio, etc.)
    console.log(
      `SMS notification: ${alerts.length} alerts to configured phone numbers`
    );

    const criticalAlerts = alerts.filter(a => a.severity === 'CRITICAL');
    if (criticalAlerts.length > 0) {
      const message = `[CRITICAL] ${criticalAlerts.length} cost alert(s): ${criticalAlerts
        .map(a => a.title)
        .join('; ')}`;
      console.log(`  SMS message: ${message.substring(0, 160)}`);
    }
  }

  /**
   * Initialize default alert rules
   */
  private initializeDefaultRules(): void {
    // Default threshold rule: $1000/day
    this.alertRules.set('threshold-daily', {
      id: 'threshold-daily',
      name: 'Daily Cost Threshold',
      enabled: true,
      type: 'THRESHOLD_EXCEEDED',
      threshold: 1000,
      window: 'daily',
      severity: 'WARNING',
      notificationChannels: ['email', 'slack'],
      recipients: ['ops-team@example.com'],
    });

    // Default spike rule: 2 std devs
    this.alertRules.set('spike-detection', {
      id: 'spike-detection',
      name: 'Cost Spike Detection',
      enabled: true,
      type: 'SPIKE_DETECTED',
      threshold: 2,
      window: 'daily',
      severity: 'WARNING',
      notificationChannels: ['slack'],
      recipients: ['ops-team@example.com'],
    });

    // Default trend rule: 20% change
    this.alertRules.set('trend-alert', {
      id: 'trend-alert',
      name: 'Cost Trend Alert',
      enabled: true,
      type: 'TREND_ALERT',
      threshold: 20,
      window: 'monthly',
      severity: 'INFO',
      notificationChannels: ['email'],
      recipients: ['finance-team@example.com'],
    });
  }
}
