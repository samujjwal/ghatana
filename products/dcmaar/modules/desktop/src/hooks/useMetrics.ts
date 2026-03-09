import { useQuery } from '@tanstack/react-query';
import { metricsClient } from '../services/mockClient';

export const METRICS_CATALOGUE_KEY = ['metrics', 'catalogue'] as const;

export const useMetricsCatalogue = () =>
  useQuery({
    queryKey: METRICS_CATALOGUE_KEY,
    queryFn: metricsClient.fetchCatalogue,
    staleTime: 5 * 60_000,
  });

export const useMetricsSeries = (metricId: string) =>
  useQuery({
    queryKey: ['metrics', 'series', metricId],
    queryFn: () => metricsClient.fetchSeries(metricId),
    enabled: Boolean(metricId),
    staleTime: 30_000,
  });
