import type { ReactNode } from 'react';

export interface ChartDataPoint {
  label: string;
  value: number;
  secondaryValue?: number;
  target?: number;
  color?: string;
  [key: string]: string | number | undefined;
}

export interface ChartMetricConfig {
  id: string;
  label: string;
  color?: string;
  icon?: ReactNode;
  formatter?: (value: number) => string;
}
