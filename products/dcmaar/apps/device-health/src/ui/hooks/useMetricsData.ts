import { useQuery } from '@tanstack/react-query';
import type { TimeRange } from '@ghatana/dcmaar-shared-ui-core';

import { AnalyticsApi } from '../../api/analytics';
import type { ProcessedMetrics } from '../../analytics/AnalyticsPipeline';

const RANGE_DURATION_MS: Record<TimeRange, number> = {
  last1h: 1 * 60 * 60 * 1000,
  last24h: 24 * 60 * 60 * 1000,
  last7d: 7 * 24 * 60 * 60 * 1000,
  last30d: 30 * 24 * 60 * 60 * 1000,
};

const analyticsApi = new AnalyticsApi();

export interface MetricsData {
  sampleCount: number;
  averages: {
    lcp: number;
    inp: number;
    cls: number;
    tbt: number;
  };
  p95: {
    lcp: number;
    inp: number;
    cls: number;
    tbt: number;
  };
  p99: {
    lcp: number;
    inp: number;
    cls: number;
    tbt: number;
  };
  timeline: Array<{
    timestamp: string;
    lcp?: number;
    inp?: number;
    cls?: number;
    tbt?: number;
    budgetViolations?: number;
    resourceTransfer?: number;
    resourceCount?: number;
  }>;
  budgetStatus: Record<'lcp' | 'inp' | 'cls' | 'tbt', { ok: number; warning: number; critical: number }>;
  network: {
    totalTransfer: number;
    averageTransfer: number;
    totalRequests: number;
    averageRequests: number;
  };
}

export const useMetricsData = (timeRange: TimeRange = 'last24h') =>
  useQuery({
    queryKey: ['metrics-overview', timeRange],
    queryFn: async (): Promise<MetricsData> => {
      const history = await analyticsApi.getMetricsHistory(200);
      if (!history.length) {
        return getDefaultMetrics();
      }

      const duration = RANGE_DURATION_MS[timeRange] ?? RANGE_DURATION_MS.last24h;
      const cutoff = Date.now() - duration;
      const filtered = history.filter((entry) => entry.timestamp >= cutoff);

      if (!filtered.length) {
        return getDefaultMetrics();
      }

      const sorted = [...filtered].sort((a, b) => a.timestamp - b.timestamp);

      const collectValues = (key: string): number[] =>
        sorted
          .map((entry) => entry.summary[key])
          .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));

      const lcpValues = collectValues('lcp');
      const inpValues = collectValues('inp');
      const clsValues = collectValues('cls');
      const tbtValues = collectValues('tbt');

      const timeline = sorted.map((entry) => ({
        timestamp: new Date(entry.timestamp).toISOString(),
        lcp: typeof entry.summary.lcp === 'number' ? entry.summary.lcp : undefined,
        inp: typeof entry.summary.inp === 'number' ? entry.summary.inp : undefined,
        cls: typeof entry.summary.cls === 'number' ? entry.summary.cls : undefined,
        tbt: typeof entry.summary.tbt === 'number' ? entry.summary.tbt : undefined,
        budgetViolations:
          typeof entry.summary.budgetViolations === 'number'
            ? entry.summary.budgetViolations
            : undefined,
        resourceTransfer:
          typeof entry.summary.resourceTransfer === 'number'
            ? entry.summary.resourceTransfer / 1024
            : undefined,
        resourceCount:
          typeof entry.summary.resourceCount === 'number'
            ? entry.summary.resourceCount
            : undefined,
      }));

      const budgetStatus: MetricsData['budgetStatus'] = {
        lcp: tallyBudgetStatus(sorted, 'lcp'),
        inp: tallyBudgetStatus(sorted, 'inp'),
        cls: tallyBudgetStatus(sorted, 'cls'),
        tbt: tallyBudgetStatus(sorted, 'tbt'),
      };

      const transferValues = sorted
        .map((entry) => entry.summary.resourceTransfer)
        .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
      const requestValues = sorted
        .map((entry) => entry.summary.resourceCount)
        .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));

      const transferTotalBytes = transferValues.reduce((sum, value) => sum + value, 0);
      const totalTransferKb = transferTotalBytes / 1024;
      const averageTransferKb = transferValues.length
        ? transferTotalBytes / (transferValues.length * 1024)
        : 0;
      const totalRequests = requestValues.reduce((sum, value) => sum + value, 0);

      return {
        sampleCount: sorted.length,
        averages: {
          lcp: average(lcpValues),
          inp: average(inpValues),
          cls: average(clsValues),
          tbt: average(tbtValues),
        },
        p95: {
          lcp: percentile(lcpValues, 0.95),
          inp: percentile(inpValues, 0.95),
          cls: percentile(clsValues, 0.95),
          tbt: percentile(tbtValues, 0.95),
        },
        p99: {
          lcp: percentile(lcpValues, 0.99),
          inp: percentile(inpValues, 0.99),
          cls: percentile(clsValues, 0.99),
          tbt: percentile(tbtValues, 0.99),
        },
        timeline,
        budgetStatus,
        network: {
          totalTransfer: totalTransferKb,
          averageTransfer: averageTransferKb,
          totalRequests,
          averageRequests: average(requestValues),
        },
      };
    },
    refetchInterval: 30000,
    staleTime: 15000,
  });

function average(values: number[]): number {
  if (!values.length) {
    return 0;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function percentile(values: number[], ratio: number): number {
  if (!values.length) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.max(0, Math.floor(sorted.length * ratio)));
  return sorted[index] ?? 0;
}

function tallyBudgetStatus(entries: ProcessedMetrics[], metric: 'lcp' | 'inp' | 'cls' | 'tbt') {
  const result = { ok: 0, warning: 0, critical: 0 };
  const key = `budgetStatus:${metric}`;
  for (const entry of entries) {
    const value = entry.summary[key];
    if (value === 2) {
      result.critical += 1;
    } else if (value === 1) {
      result.warning += 1;
    } else {
      result.ok += 1;
    }
  }
  return result;
}

function getDefaultMetrics(): MetricsData {
  return {
    sampleCount: 0,
    averages: { lcp: 0, inp: 0, cls: 0, tbt: 0 },
    p95: { lcp: 0, inp: 0, cls: 0, tbt: 0 },
    p99: { lcp: 0, inp: 0, cls: 0, tbt: 0 },
    timeline: [],
    budgetStatus: {
      lcp: { ok: 0, warning: 0, critical: 0 },
      inp: { ok: 0, warning: 0, critical: 0 },
      cls: { ok: 0, warning: 0, critical: 0 },
      tbt: { ok: 0, warning: 0, critical: 0 },
    },
    network: {
      totalTransfer: 0,
      averageTransfer: 0,
      totalRequests: 0,
      averageRequests: 0,
    },
  };
}
