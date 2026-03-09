/**
 * @fileoverview Analytics Pipeline
 *
 * Processes collected metrics and events into aggregated insights.
 *
 * @module analytics/AnalyticsPipeline
 */

import type { StorageAdapter } from '../core/interfaces/StorageAdapter';
import type {
  PageMetrics,
  ResourceMetrics,
  InteractionMetrics,
  WebVitalRating,
} from '../core/interfaces/MetricCollector';
import type { BrowserEvent } from '../core/interfaces/EventCapture';
import { AlertManager, type Alert, type AlertSeverity } from './AlertManager';

export interface CollectedMetrics {
  page?: PageMetrics;
  resources?: ResourceMetrics[];
  interactions?: InteractionMetrics[];
  rawEvents?: BrowserEvent[];
  metadata?: Record<string, unknown>;
}

export interface ProcessedMetrics {
  timestamp: number;
  summary: Record<string, number>;
  details: CollectedMetrics;
  alerts?: Alert[];
}

export type MetricProcessor = (
  metrics: CollectedMetrics
) => Promise<Record<string, number>> | Record<string, number>;

export interface Insights {
  timestamp: number;
  counts: Record<string, number>;
  alerts?: Alert[];
}

export interface TimeRange {
  from: number;
  to: number;
}

export interface Trend {
  metric: string;
  change: number;
  current: number;
  baseline: number;
}

export interface Regression {
  metric: string;
  delta: number;
  severity: 'minor' | 'moderate' | 'major';
}

export type Baseline = Record<string, number>;
export type Current = Record<string, number>;
export type AlertThresholds = Record<string, number>;

export class AnalyticsPipeline {
  private processors: MetricProcessor[] = [];
  private storage: StorageAdapter;
  private alertManager: AlertManager;
  private readonly historyKey = 'dcmaar:pageUsage:metricsHistory';
  private readonly maxHistoryEntries = 50;

  constructor(storage: StorageAdapter, alertManager?: AlertManager) {
    this.storage = storage;
    this.alertManager = alertManager ?? new AlertManager();
  }

  registerProcessor(processor: MetricProcessor): void {
    this.processors.push(processor);
  }

  setAlertThresholds(thresholds: AlertThresholds): void {
    const rules = Object.entries(thresholds).map(([metric, threshold]) => ({
      id: `threshold:${metric}`,
      metric,
      operator: '>' as const,
      threshold,
      severity: metric === 'errorRate' ? ('critical' as const) : ('warning' as const),
    }));
    this.alertManager.setAlertRules(rules);
  }

  async processMetrics(metrics: CollectedMetrics): Promise<ProcessedMetrics> {
    const summary: Record<string, number> = {};

    if (metrics.page) {
      this.assignIfDefined(summary, 'ttfb', metrics.page.ttfb);
      this.assignIfDefined(summary, 'fcp', metrics.page.fcp);
      this.assignIfDefined(summary, 'lcp', metrics.page.lcp);
      this.assignIfDefined(summary, 'cls', metrics.page.cls);
      this.assignIfDefined(summary, 'fid', metrics.page.fid);
      this.assignIfDefined(summary, 'tbt', metrics.page.tbt);
      this.assignIfDefined(summary, 'inp', metrics.page.inp);
      this.assignIfDefined(summary, 'speedIndex', metrics.page.speedIndex);

      const diagnostic = metrics.page.diagnostics;
      if (diagnostic) {
        this.assignIfDefined(summary, 'longTaskCount', diagnostic.longTaskCount);
        this.assignIfDefined(summary, 'totalBlockingTime', diagnostic.totalBlockingTime);
        this.assignIfDefined(summary, 'maxInteractionLatency', diagnostic.maxInteractionLatency);
      }

      const ratings = metrics.page.ratings ?? {};
      const overallScore = this.ratingToScore(metrics.page.overallRating);
      if (overallScore !== undefined) {
        summary.overallVitalScore = overallScore;
      }

      const lcpRatingScore = this.ratingToScore(ratings.lcp);
      if (lcpRatingScore !== undefined) {
        summary.lcpRatingScore = lcpRatingScore;
      }

      const clsRatingScore = this.ratingToScore(ratings.cls);
      if (clsRatingScore !== undefined) {
        summary.clsRatingScore = clsRatingScore;
      }

      const inpRatingScore = this.ratingToScore(ratings.inp);
      if (inpRatingScore !== undefined) {
        summary.inpRatingScore = inpRatingScore;
      }

      const fidRatingScore = this.ratingToScore(ratings.fid);
      if (fidRatingScore !== undefined) {
        summary.fidRatingScore = fidRatingScore;
      }
    }

    if (metrics.resources?.length) {
      const totalTransfer = metrics.resources.reduce((sum, resource) => sum + (resource.size || 0), 0);
      summary.resourceTransfer = totalTransfer;
      summary.resourceCount = metrics.resources.length;
    }

    if (metrics.interactions?.length) {
      summary.interactionCount = metrics.interactions.length;
    }

    for (const processor of this.processors) {
      const result = await processor(metrics);
      Object.assign(summary, result);
    }

    const timestamp = metrics.page?.timestamp ?? Date.now();
    const budgetAlerts = this.evaluateDefaultBudgets(metrics, summary, timestamp);

    const processed: ProcessedMetrics = {
      timestamp,
      summary,
      details: metrics,
    };

    if (budgetAlerts.length) {
      processed.alerts = [...budgetAlerts];
    }

    await this.persistMetricsHistory(processed);
    const thresholdAlerts = await this.alertManager.checkThresholds(processed);
    if (thresholdAlerts.length) {
      processed.alerts = [...(processed.alerts ?? []), ...thresholdAlerts];
    }

    return processed;
  }

  async processEvents(events: BrowserEvent[]): Promise<Insights> {
    const counts: Record<string, number> = {};

    for (const event of events) {
      counts[event.type] = (counts[event.type] ?? 0) + 1;
    }

    const insights: Insights = {
      timestamp: Date.now(),
      counts,
    };

    // Evaluate alerts based on counts if thresholds configured
    const metrics: ProcessedMetrics = {
      timestamp: insights.timestamp,
      summary: counts,
      details: { rawEvents: events },
    };
    insights.alerts = await this.alertManager.checkThresholds(metrics);

    return insights;
  }

  async calculateTrends(metricName: string, timeRange: TimeRange): Promise<Trend> {
    const history = (await this.storage.get<ProcessedMetrics[]>(this.historyKey)) ?? [];
    const relevant = history.filter(
      (entry) => entry.timestamp >= timeRange.from && entry.timestamp <= timeRange.to
    );

    if (relevant.length === 0) {
      return {
        metric: metricName,
        change: 0,
        current: 0,
        baseline: 0,
      };
    }

    const baselineValue = relevant[0]?.summary[metricName] ?? 0;
    const currentValue = relevant[relevant.length - 1]?.summary[metricName] ?? baselineValue;

    return {
      metric: metricName,
      change: currentValue - baselineValue,
      current: currentValue,
      baseline: baselineValue,
    };
  }

  async detectRegressions(baseline: Baseline, current: Current): Promise<Regression[]> {
    const regressions: Regression[] = [];

    for (const [metric, baselineValue] of Object.entries(baseline)) {
      const currentValue = current[metric];
      if (currentValue === undefined) {
        continue;
      }

      const delta = currentValue - baselineValue;
      if (delta <= 0) {
        continue;
      }

      const severity =
        delta > baselineValue * 0.5
          ? 'major'
          : delta > baselineValue * 0.25
            ? 'moderate'
            : 'minor';

      regressions.push({
        metric,
        delta,
        severity,
      });
    }

    return regressions;
  }

  private evaluateDefaultBudgets(
    metrics: CollectedMetrics,
    summary: Record<string, number>,
    timestamp: number
  ): Alert[] {
    const page = metrics.page;
    if (!page) {
      summary.budgetViolations = 0;
      return [];
    }

    type BudgetRule = {
      metric: 'lcp' | 'inp' | 'cls' | 'tbt';
      value: number | undefined;
      warning: number;
      critical: number;
      unit: string;
      label: string;
    };

    const budgets: BudgetRule[] = [
      { metric: 'lcp', value: page.lcp, warning: 2500, critical: 4000, unit: 'ms', label: 'Largest Contentful Paint' },
      { metric: 'inp', value: page.inp, warning: 200, critical: 500, unit: 'ms', label: 'Interaction to Next Paint' },
      { metric: 'cls', value: page.cls, warning: 0.1, critical: 0.25, unit: '', label: 'Cumulative Layout Shift' },
      { metric: 'tbt', value: page.tbt, warning: 300, critical: 600, unit: 'ms', label: 'Total Blocking Time' },
    ];

    let violationCount = 0;
    const alerts: Alert[] = [];

    for (const budget of budgets) {
      if (budget.value === undefined || Number.isNaN(budget.value)) {
        continue;
      }

      let status = 0;
      let threshold = budget.warning;
      let severity: AlertSeverity = 'warning';

      if (budget.value > budget.critical) {
        status = 2;
        threshold = budget.critical;
        severity = 'critical';
      } else if (budget.value > budget.warning) {
        status = 1;
        threshold = budget.warning;
        severity = 'warning';
      }

      summary[`budgetStatus:${budget.metric}`] = status;

      if (status > 0) {
        violationCount += 1;
        alerts.push({
          id: `budget:${budget.metric}:${timestamp}`,
          metric: budget.metric,
          severity,
          message: `${budget.label} breached ${severity === 'critical' ? 'critical' : 'warning'} budget (${budget.value}${budget.unit} > ${threshold}${budget.unit})`,
          value: budget.value,
          threshold,
          timestamp,
          metadata: {
            status,
            unit: budget.unit,
          },
        });
      }
    }

    summary.budgetViolations = violationCount;
    return alerts;
  }

  private assignIfDefined(
    summary: Record<string, number>,
    key: string,
    value: number | undefined
  ): void {
    if (typeof value === 'number' && Number.isFinite(value)) {
      summary[key] = value;
    }
  }

  private ratingToScore(rating?: WebVitalRating): number | undefined {
    if (!rating) {
      return undefined;
    }
    switch (rating) {
      case 'good':
        return 0;
      case 'needs-improvement':
        return 1;
      case 'poor':
        return 2;
      default:
        return undefined;
    }
  }

  async getMetricsHistory(limit = this.maxHistoryEntries): Promise<ProcessedMetrics[]> {
    const history = (await this.storage.get<ProcessedMetrics[]>(this.historyKey)) ?? [];
    if (limit >= history.length) {
      return history;
    }
    return history.slice(history.length - limit);
  }

  async getLatestMetrics(): Promise<ProcessedMetrics | undefined> {
    const history = await this.getMetricsHistory(1);
    return history.length ? history[history.length - 1] : undefined;
  }

  async getAlerts(limit = this.maxHistoryEntries): Promise<Alert[]> {
    const history = await this.getMetricsHistory(limit);
    const alerts = history
      .flatMap((entry) => entry.alerts ?? [])
      .sort((a, b) => b.timestamp - a.timestamp);
    return alerts;
  }

  private async persistMetricsHistory(entry: ProcessedMetrics): Promise<void> {
    const history = (await this.storage.get<ProcessedMetrics[]>(this.historyKey)) ?? [];
    history.push(entry);

    if (history.length > this.maxHistoryEntries) {
      history.splice(0, history.length - this.maxHistoryEntries);
    }

    await this.storage.set(this.historyKey, history);
  }
}
