import browser from 'webextension-polyfill';

import type {
  ProcessedMetrics,
  Trend,
  TimeRange,
  Insights,
} from '../analytics/AnalyticsPipeline';
import type { Alert } from '../analytics/AlertManager';

interface MetricHistoryResponse {
  data: ProcessedMetrics[];
}

export interface EnvironmentSnapshot {
  device?: Record<string, unknown>;
  network?: Record<string, unknown>;
  viewport?: Record<string, unknown>;
  geo?: Record<string, unknown>;
}

export class AnalyticsApi {
  async getPerformanceSummary(): Promise<ProcessedMetrics | null> {
    return this.request<ProcessedMetrics | null>('PAGE_USAGE_GET_SUMMARY');
  }

  async getMetricsHistory(limit = 10): Promise<ProcessedMetrics[]> {
    const history = await this.request<MetricHistoryResponse['data'] | null>(
      'PAGE_USAGE_GET_HISTORY',
      { limit }
    );
    return history ?? [];
  }

  async getTrend(metric: string, range?: TimeRange): Promise<Trend> {
    return this.request<Trend>('PAGE_USAGE_GET_TRENDS', { metric, range });
  }

  async getAlerts(): Promise<Alert[]> {
    const alerts = await this.request<Alert[] | null>('PAGE_USAGE_GET_ALERTS');
    return alerts ?? [];
  }

  async getEnvironmentSnapshot(): Promise<EnvironmentSnapshot> {
    const snapshot = await this.request<EnvironmentSnapshot | null>('PAGE_USAGE_GET_ENVIRONMENT');
    return snapshot ?? {};
  }

  async getEventInsights(): Promise<Insights> {
    const history = await this.getMetricsHistory(50);
    const latestAlerts = history.length ? history[history.length - 1].alerts : undefined;

    const counts: Record<string, number> = {
      samples: history.length,
      criticalBreaches: history.filter((entry) => hasBudgetStatus(entry, 2)).length,
      warningBreaches: history.filter((entry) => hasBudgetStatus(entry, 1)).length,
      totalViolations: history.reduce(
        (acc, entry) => acc + (entry.summary.budgetViolations ?? 0),
        0
      ),
    };

    return {
      timestamp: Date.now(),
      counts,
      alerts: latestAlerts,
    };
  }

  private async request<T>(type: string, payload?: unknown): Promise<T> {
    const response = (await browser.runtime.sendMessage({ type, payload })) as {
      success: boolean;
      data?: T;
      error?: string;
    };

    if (!response?.success) {
      throw new Error(response?.error ?? 'Analytics request failed');
    }

    return response.data as T;
  }
}

function hasBudgetStatus(entry: ProcessedMetrics, level: number): boolean {
  return (
    entry.summary['budgetStatus:lcp'] === level ||
    entry.summary['budgetStatus:inp'] === level ||
    entry.summary['budgetStatus:cls'] === level ||
    entry.summary['budgetStatus:tbt'] === level
  );
}
