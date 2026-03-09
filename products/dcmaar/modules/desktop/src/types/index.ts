export type Severity = 'info' | 'warning' | 'critical' | 'error';

export interface TimelineItem {
  timestamp: string;
  title: string;
  description: string;
  action?: unknown;
}

export interface MetricSeriesPoint {
  timestamp: string;
  value: number;
  unit?: string;
}
