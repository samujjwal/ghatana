/**
 * Analytics Hook
 *
 * React hook wrapping AnalyticsService for dashboard consumption.
 *
 * @doc.type hook
 * @doc.purpose Analytics data fetching and report generation
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  generateReport,
  aggregateProjectMetrics,
  aggregateTeamUtilisation,
  identifyBottleneck,
  type AnalyticsReport,
  type AnalyticsRequest,
} from '../services/analytics/AnalyticsService';

// ============================================================================
// Types
// ============================================================================

export interface UseAnalyticsOptions {
  projectIds?: string[];
  startDate: string;
  endDate: string;
  enabled?: boolean;
}

export interface UseAnalyticsResult {
  report: AnalyticsReport | null;
  bottleneck: string | null;
  avgUtilisation: number;
  isLoading: boolean;
  error: Error | null;
  refetch: () => void;
}

// ============================================================================
// Query Keys
// ============================================================================

const ANALYTICS_KEY = 'analytics-report';

// ============================================================================
// Hook
// ============================================================================

export function useAnalytics(options: UseAnalyticsOptions): UseAnalyticsResult {
  const { projectIds, startDate, endDate, enabled = true } = options;

  const request: AnalyticsRequest = useMemo(
    () => ({ projectIds, startDate, endDate }),
    [projectIds, startDate, endDate],
  );

  const {
    data: report,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: [ANALYTICS_KEY, startDate, endDate, projectIds],
    queryFn: () => generateReport(request),
    enabled,
    staleTime: 5 * 60 * 1000,
  });

  const bottleneck = useMemo(
    () => (report ? identifyBottleneck(report.lifecycleMetrics) : null),
    [report],
  );

  const avgUtilisation = useMemo(
    () => (report ? aggregateTeamUtilisation(report.teamMetrics) : 0),
    [report],
  );

  return {
    report: report ?? null,
    bottleneck,
    avgUtilisation,
    isLoading,
    error: error as Error | null,
    refetch,
  };
}
