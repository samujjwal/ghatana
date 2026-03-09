import { useQuery } from '@tanstack/react-query';
import { AnalyticsApi } from '../../api/analytics';
import type {
  ProcessedMetrics,
  Trend,
  TimeRange,
  Insights,
} from '../../analytics/AnalyticsPipeline';
import type { Alert } from '../../analytics/AlertManager';
import type { EnvironmentSnapshot } from '../../api/analytics';

const analyticsApi = new AnalyticsApi();

const DEFAULT_REFRESH_MS = 5_000;

interface QueryOptions {
  refetchInterval?: number;
  staleTime?: number;
}

export function usePerformanceSummary(
  options?: QueryOptions
) {
  const refetchInterval = options?.refetchInterval ?? DEFAULT_REFRESH_MS;
  const staleTime = options?.staleTime ?? DEFAULT_REFRESH_MS;

  return useQuery<ProcessedMetrics | null>({
    queryKey: ['page-usage', 'summary'],
    queryFn: () => analyticsApi.getPerformanceSummary(),
    refetchInterval,
    staleTime,
  });
}

export function useMetricsHistory(limit = 20, options?: QueryOptions) {
  const refetchInterval = options?.refetchInterval ?? DEFAULT_REFRESH_MS;
  const staleTime = options?.staleTime ?? DEFAULT_REFRESH_MS;

  return useQuery<ProcessedMetrics[]>({
    queryKey: ['page-usage', 'history', limit],
    queryFn: () => analyticsApi.getMetricsHistory(limit),
    refetchInterval,
    staleTime,
  });
}

export function useTrend(metric: string, range?: TimeRange, options?: QueryOptions) {
  const refetchInterval = options?.refetchInterval ?? DEFAULT_REFRESH_MS;
  const staleTime = options?.staleTime ?? DEFAULT_REFRESH_MS;

  return useQuery<Trend>({
    queryKey: ['page-usage', 'trend', metric, range?.from, range?.to],
    queryFn: () => analyticsApi.getTrend(metric, range),
    refetchInterval,
    staleTime,
  });
}

export function useAlerts(options?: QueryOptions) {
  const refetchInterval = options?.refetchInterval ?? DEFAULT_REFRESH_MS;
  const staleTime = options?.staleTime ?? DEFAULT_REFRESH_MS;

  return useQuery<Alert[]>({
    queryKey: ['page-usage', 'alerts'],
    queryFn: () => analyticsApi.getAlerts(),
    refetchInterval,
    staleTime,
  });
}

export function useEnvironmentSnapshot(options?: QueryOptions) {
  const refetchInterval = options?.refetchInterval ?? DEFAULT_REFRESH_MS;
  const staleTime = options?.staleTime ?? DEFAULT_REFRESH_MS;

  return useQuery<EnvironmentSnapshot>({
    queryKey: ['page-usage', 'environment'],
    queryFn: () => analyticsApi.getEnvironmentSnapshot(),
    refetchInterval,
    staleTime,
  });
}

export function useEventInsights(options?: QueryOptions) {
  const refetchInterval = options?.refetchInterval ?? DEFAULT_REFRESH_MS;
  const staleTime = options?.staleTime ?? DEFAULT_REFRESH_MS;

  return useQuery<Insights>({
    queryKey: ['page-usage', 'insights'],
    queryFn: () => analyticsApi.getEventInsights(),
    refetchInterval,
    staleTime,
  });
}
