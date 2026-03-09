import { useQuery } from '@tanstack/react-query';
import api from './api';

export interface MetricPoint {
  timestamp: string;
  value: number;
}

export interface TimeRange {
  start: string;
  end: string;
  interval?: string; // e.g., '1h', '1d'
}

const fetchMetrics = async (metricName: string, timeRange: TimeRange): Promise<MetricPoint[]> => {
  const { start, end, interval = '1h' } = timeRange;
  const response = await api.get(`/metrics/${metricName}`, {
    params: { start, end, interval },
  });
  return response.data;
};

export const useMetrics = (metricName: string, timeRange: TimeRange) => {
  return useQuery({
    queryKey: ['metrics', metricName, timeRange],
    queryFn: () => fetchMetrics(metricName, timeRange),
    staleTime: 5 * 60 * 1000, // 5 minutes
    refetchInterval: 30 * 1000, // 30 seconds
    refetchOnWindowFocus: false,
  });
};

// Mock data generators for development
const generateMockData = (days: number, min: number, max: number): MetricPoint[] => {
  const now = new Date();
  return Array.from({ length: days * 24 }, (_, i) => {
    const date = new Date(now);
    date.setHours(date.getHours() - (days * 24 - i));
    return {
      timestamp: date.toISOString(),
      value: Math.floor(Math.random() * (max - min + 1)) + min,
    };
  });
};

export const mockMetrics = {
  cpu: generateMockData(7, 10, 90),
  memory: generateMockData(7, 100, 500),
  requests: generateMockData(7, 50, 5000),
  errors: generateMockData(7, 0, 50),
};

// Types for our metrics
export type MetricType = keyof typeof mockMetrics;
