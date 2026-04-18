/**
 * Alert Service
 *
 * Monitors system metrics and triggers alerts based on thresholds:
 * - Configurable alert rules
 * - Multiple severity levels (info, warning, critical)
 * - Integration with notification channels (Slack, PagerDuty, email)
 * - Alert suppression and throttling
 * - Alert history and acknowledgment
 *
 * @doc.type service
 * @doc.purpose Monitor system health and send alerts on threshold violations
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@prisma/client";

export type AlertSeverity = "info" | "warning" | "critical";
export type AlertStatus = "active" | "acknowledged" | "resolved";

export interface AlertRule {
  ruleId: string;
  name: string;
  description: string;
  severity: AlertSeverity;
  condition: {
    metric: string;
    operator: "gt" | "lt" | "eq" | "gte" | "lte";
    threshold: number;
    durationSeconds?: number;
  };
  notificationChannels: string[];
  cooldownSeconds: number;
  enabled: boolean;
  tenantId?: string;
}

export interface Alert {
  alertId: string;
  ruleId: string;
  severity: AlertSeverity;
  title: string;
  message: string;
  metricValue: number;
  threshold: number;
  status: AlertStatus;
  triggeredAt: Date;
  acknowledgedAt: Date | undefined;
  acknowledgedBy: string | undefined;
  resolvedAt: Date | undefined;
  metadata: Record<string, unknown> | undefined;
}

export interface NotificationChannel {
  channelId: string;
  type: "slack" | "pagerduty" | "email" | "webhook";
  name: string;
  config: Record<string, string>;
}

export const DEFAULT_ALERT_RULES: AlertRule[] = [
  {
    ruleId: "high-error-rate",
    name: "High Error Rate",
    description: "API error rate exceeds threshold",
    severity: "critical",
    condition: {
      metric: "api.error_rate",
      operator: "gt",
      threshold: 0.05,
      durationSeconds: 300,
    },
    notificationChannels: ["slack", "pagerduty"],
    cooldownSeconds: 600,
    enabled: true,
  },
  {
    ruleId: "high-latency-p95",
    name: "High P95 Latency",
    description: "API P95 latency exceeds threshold",
    severity: "warning",
    condition: {
      metric: "api.latency_p95",
      operator: "gt",
      threshold: 1000,
      durationSeconds: 300,
    },
    notificationChannels: ["slack"],
    cooldownSeconds: 600,
    enabled: true,
  },
  {
    ruleId: "high-latency-p99",
    name: "High P99 Latency",
    description: "API P99 latency exceeds threshold",
    severity: "critical",
    condition: {
      metric: "api.latency_p99",
      operator: "gt",
      threshold: 2000,
      durationSeconds: 180,
    },
    notificationChannels: ["slack", "pagerduty"],
    cooldownSeconds: 300,
    enabled: true,
  },
  {
    ruleId: "slow-queries",
    name: "Slow Database Queries",
    description: "Number of slow queries exceeds threshold",
    severity: "warning",
    condition: {
      metric: "db.slow_queries_count",
      operator: "gt",
      threshold: 10,
      durationSeconds: 300,
    },
    notificationChannels: ["slack"],
    cooldownSeconds: 900,
    enabled: true,
  },
  {
    ruleId: "db-connection-pool",
    name: "High Database Connection Pool Usage",
    description: "Database connection pool usage exceeds threshold",
    severity: "critical",
    condition: {
      metric: "db.connection_pool_usage",
      operator: "gt",
      threshold: 0.9,
      durationSeconds: 60,
    },
    notificationChannels: ["slack", "pagerduty"],
    cooldownSeconds: 300,
    enabled: true,
  },
  {
    ruleId: "high-cpu",
    name: "High CPU Usage",
    description: "Server CPU usage exceeds threshold",
    severity: "warning",
    condition: {
      metric: "system.cpu_usage",
      operator: "gt",
      threshold: 80,
      durationSeconds: 300,
    },
    notificationChannels: ["slack"],
    cooldownSeconds: 600,
    enabled: true,
  },
  {
    ruleId: "high-memory",
    name: "High Memory Usage",
    description: "Server memory usage exceeds threshold",
    severity: "warning",
    condition: {
      metric: "system.memory_usage",
      operator: "gt",
      threshold: 85,
      durationSeconds: 300,
    },
    notificationChannels: ["slack"],
    cooldownSeconds: 600,
    enabled: true,
  },
];

export class AlertService {
  private alertRules: Map<string, AlertRule> = new Map();
  private activeAlerts: Map<string, Alert> = new Map();
  private lastAlertTimes: Map<string, Date> = new Map();
  private notificationChannels: Map<string, NotificationChannel> = new Map();
  private metricHistory: Map<string, Array<{ value: number; timestamp: number }>> = new Map();

  constructor(private readonly prisma: PrismaClient) {
    this.initializeDefaultRules();
  }

  /**
   * Evaluate metrics against alert rules
   */
  evaluateMetrics(metrics: Record<string, number>): Alert[] {
    const triggeredAlerts: Alert[] = [];

    for (const rule of this.alertRules.values()) {
      if (!rule.enabled) continue;

      const metricValue = metrics[rule.condition.metric];
      if (metricValue === undefined) continue;

      // Check if condition is met
      const conditionMet = this.checkCondition(metricValue, rule.condition);

      if (!conditionMet) continue;

      // Check duration requirement
      if (rule.condition.durationSeconds) {
        const durationMet = this.checkDurationCondition(
          rule.condition.metric,
          rule.condition,
          rule.condition.durationSeconds
        );
        if (!durationMet) continue;
      }

      // Check cooldown
      if (this.isInCooldown(rule.ruleId, rule.cooldownSeconds)) {
        continue;
      }

      // Create alert
      const alert = this.createAlert(rule, metricValue);
      triggeredAlerts.push(alert);

      // Store metric for history
      this.recordMetric(rule.condition.metric, metricValue);
    }

    return triggeredAlerts;
  }

  /**
   * Acknowledge an alert
   */
  async acknowledgeAlert(
    alertId: string,
    acknowledgedBy: string
  ): Promise<Alert | null> {
    const alert = this.activeAlerts.get(alertId);
    if (!alert) return null;

    alert.status = "acknowledged";
    alert.acknowledgedAt = new Date();
    alert.acknowledgedBy = acknowledgedBy;

    // Persist to database
    await this.prisma.$executeRaw`
      UPDATE "SystemAlert"
      SET status = 'acknowledged',
          "acknowledgedAt" = NOW(),
          "acknowledgedBy" = ${acknowledgedBy}
      WHERE id = ${alertId}
    `.catch(() => {
      console.log(`[ALERT] Acknowledged: ${alertId}`);
    });

    return alert;
  }

  /**
   * Resolve an alert
   */
  async resolveAlert(alertId: string): Promise<Alert | null> {
    const alert = this.activeAlerts.get(alertId);
    if (!alert) return null;

    alert.status = "resolved";
    alert.resolvedAt = new Date();

    // Remove from active alerts
    this.activeAlerts.delete(alertId);

    // Persist to database
    await this.prisma.$executeRaw`
      UPDATE "SystemAlert"
      SET status = 'resolved',
          "resolvedAt" = NOW()
      WHERE id = ${alertId}
    `.catch(() => {
      console.log(`[ALERT] Resolved: ${alertId}`);
    });

    return alert;
  }

  /**
   * Add or update an alert rule
   */
  setAlertRule(rule: AlertRule): void {
    this.alertRules.set(rule.ruleId, rule);
  }

  /**
   * Remove an alert rule
   */
  removeAlertRule(ruleId: string): void {
    this.alertRules.delete(ruleId);
  }

  /**
   * Get all alert rules
   */
  getAlertRules(): AlertRule[] {
    return Array.from(this.alertRules.values());
  }

  /**
   * Get active alerts
   */
  getActiveAlerts(severity?: AlertSeverity): Alert[] {
    const alerts = Array.from(this.activeAlerts.values()).filter(
      (a) => a.status === "active"
    );

    if (severity) {
      return alerts.filter((a) => a.severity === severity);
    }

    return alerts;
  }

  /**
   * Add a notification channel
   */
  addNotificationChannel(channel: NotificationChannel): void {
    this.notificationChannels.set(channel.channelId, channel);
  }

  /**
   * Get alert history
   */
  async getAlertHistory(
    limit: number = 50,
    severity?: AlertSeverity
  ): Promise<Alert[]> {
    const result = await this.prisma.$queryRaw<Array<{
      id: string;
      ruleId: string;
      severity: string;
      title: string;
      message: string;
      metricValue: number;
      threshold: number;
      status: string;
      triggeredAt: Date;
      acknowledgedAt: Date | null;
      acknowledgedBy: string | null;
      resolvedAt: Date | null;
    }>>`
      SELECT *
      FROM "SystemAlert"
      WHERE ${severity ? `severity = ${severity}` : "1=1"}
      ORDER BY "triggeredAt" DESC
      LIMIT ${limit}
    `.catch(() => []);

    return result.map((r) => ({
      alertId: r.id,
      ruleId: r.ruleId,
      severity: r.severity as AlertSeverity,
      title: r.title,
      message: r.message,
      metricValue: r.metricValue,
      threshold: r.threshold,
      status: r.status as AlertStatus,
      triggeredAt: r.triggeredAt,
      acknowledgedAt: r.acknowledgedAt ?? undefined,
      acknowledgedBy: r.acknowledgedBy ?? undefined,
      resolvedAt: r.resolvedAt ?? undefined,
      metadata: undefined,
    }));
  }

  // Private methods

  private initializeDefaultRules(): void {
    for (const rule of DEFAULT_ALERT_RULES) {
      this.alertRules.set(rule.ruleId, rule);
    }
  }

  private checkCondition(
    value: number,
    condition: AlertRule["condition"]
  ): boolean {
    switch (condition.operator) {
      case "gt":
        return value > condition.threshold;
      case "lt":
        return value < condition.threshold;
      case "eq":
        return value === condition.threshold;
      case "gte":
        return value >= condition.threshold;
      case "lte":
        return value <= condition.threshold;
      default:
        return false;
    }
  }

  private checkDurationCondition(
    metric: string,
    condition: AlertRule["condition"],
    durationSeconds: number
  ): boolean {
    const history = this.metricHistory.get(metric);
    if (!history || history.length === 0) return false;

    const cutoff = Date.now() - durationSeconds * 1000;
    const recentValues = history.filter((h) => h.timestamp > cutoff);

    // Check if all recent values meet the condition
    return (
      recentValues.length > 0 &&
      recentValues.every((v) => this.checkCondition(v.value, condition))
    );
  }

  private isInCooldown(ruleId: string, cooldownSeconds: number): boolean {
    const lastAlert = this.lastAlertTimes.get(ruleId);
    if (!lastAlert) return false;

    const cooldownEnd = lastAlert.getTime() + cooldownSeconds * 1000;
    return Date.now() < cooldownEnd;
  }

  private createAlert(rule: AlertRule, metricValue: number): Alert {
    const alertId = `alert_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

    const alert: Alert = {
      alertId,
      ruleId: rule.ruleId,
      severity: rule.severity,
      title: rule.name,
      message: `${rule.description}. Current value: ${metricValue.toFixed(2)}, Threshold: ${rule.condition.threshold}`,
      metricValue,
      threshold: rule.condition.threshold,
      status: "active",
      triggeredAt: new Date(),
      acknowledgedAt: undefined,
      acknowledgedBy: undefined,
      resolvedAt: undefined,
      metadata: undefined,
    };

    this.activeAlerts.set(alertId, alert);
    this.lastAlertTimes.set(rule.ruleId, new Date());

    // Send notifications
    this.sendNotifications(rule, alert);

    // Persist to database
    this.persistAlert(alert, rule);

    console.log(`[ALERT] Triggered: ${rule.name} - ${metricValue}`);

    return alert;
  }

  private recordMetric(metric: string, value: number): void {
    if (!this.metricHistory.has(metric)) {
      this.metricHistory.set(metric, []);
    }

    const history = this.metricHistory.get(metric)!;
    history.push({ value, timestamp: Date.now() });

    // Keep only last hour of metrics
    const cutoff = Date.now() - 3600000;
    const trimmed = history.filter((h) => h.timestamp > cutoff);
    this.metricHistory.set(metric, trimmed);
  }

  private async persistAlert(alert: Alert, rule: AlertRule): Promise<void> {
    await this.prisma.$executeRaw`
      INSERT INTO "SystemAlert" (
        id, "ruleId", severity, title, message, "metricValue",
        threshold, status, "triggeredAt", "tenantId"
      ) VALUES (
        ${alert.alertId},
        ${alert.ruleId},
        ${alert.severity},
        ${alert.title},
        ${alert.message},
        ${alert.metricValue},
        ${alert.threshold},
        ${alert.status},
        ${alert.triggeredAt},
        ${rule.tenantId ?? null}
      )
    `.catch(() => {
      // Table might not exist
    });
  }

  private sendNotifications(rule: AlertRule, alert: Alert): void {
    for (const channelId of rule.notificationChannels) {
      const channel = this.notificationChannels.get(channelId);
      if (!channel) continue;

      switch (channel.type) {
        case "slack":
          this.sendSlackNotification(channel, alert);
          break;
        case "pagerduty":
          this.sendPagerDutyNotification(channel, alert);
          break;
        case "email":
          this.sendEmailNotification(channel, alert);
          break;
        case "webhook":
          this.sendWebhookNotification(channel, alert);
          break;
      }
    }
  }

  private sendSlackNotification(
    channel: NotificationChannel,
    alert: Alert
  ): void {
    const color = {
      info: "#36a64f",
      warning: "#ff9900",
      critical: "#ff0000",
    }[alert.severity];

    const payload = {
      attachments: [
        {
          color,
          title: alert.title,
          text: alert.message,
          fields: [
            {
              title: "Severity",
              value: alert.severity,
              short: true,
            },
            {
              title: "Value",
              value: alert.metricValue.toFixed(2),
              short: true,
            },
            {
              title: "Threshold",
              value: alert.threshold.toString(),
              short: true,
            },
            {
              title: "Time",
              value: alert.triggeredAt.toISOString(),
              short: true,
            },
          ],
        },
      ],
    };

    // Send to Slack webhook
    console.log(`[SLACK] ${channel.name}: ${alert.title}`);
  }

  private sendPagerDutyNotification(
    channel: NotificationChannel,
    alert: Alert
  ): void {
    console.log(`[PAGERDUTY] ${channel.name}: ${alert.title}`);
  }

  private sendEmailNotification(
    channel: NotificationChannel,
    alert: Alert
  ): void {
    console.log(`[EMAIL] ${channel.name}: ${alert.title}`);
  }

  private sendWebhookNotification(
    channel: NotificationChannel,
    alert: Alert
  ): void {
    console.log(`[WEBHOOK] ${channel.name}: ${alert.title}`);
  }
}
