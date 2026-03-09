import { describe, it, expect } from 'vitest';
import type { ChartDataPoint, ChartMetricConfig } from '../types';

describe('ChartDataPoint', () => {
  it('should accept minimal data point', () => {
    const point: ChartDataPoint = { label: 'Jan', value: 100 };
    expect(point.label).toBe('Jan');
    expect(point.value).toBe(100);
    expect(point.secondaryValue).toBeUndefined();
  });

  it('should accept full data point', () => {
    const point: ChartDataPoint = {
      label: 'Feb',
      value: 200,
      secondaryValue: 150,
      target: 250,
      color: '#ff0000',
    };
    expect(point.target).toBe(250);
    expect(point.color).toBe('#ff0000');
  });

  it('should accept arbitrary extra keys', () => {
    const point: ChartDataPoint = {
      label: 'Mar',
      value: 300,
      customField: 'extra',
    };
    expect(point.customField).toBe('extra');
  });
});

describe('ChartMetricConfig', () => {
  it('should accept minimal metric config', () => {
    const config: ChartMetricConfig = { id: 'cpu', label: 'CPU Usage' };
    expect(config.id).toBe('cpu');
    expect(config.label).toBe('CPU Usage');
  });

  it('should accept custom formatter', () => {
    const config: ChartMetricConfig = {
      id: 'memory',
      label: 'Memory',
      color: '#00ff00',
      formatter: (v: number) => `${v} MB`,
    };
    expect(config.formatter!(1024)).toBe('1024 MB');
  });
});

describe('Chart data helpers', () => {
  it('should be sortable by value', () => {
    const data: ChartDataPoint[] = [
      { label: 'A', value: 30 },
      { label: 'B', value: 10 },
      { label: 'C', value: 20 },
    ];
    const sorted = [...data].sort((a, b) => a.value - b.value);
    expect(sorted[0].label).toBe('B');
    expect(sorted[2].label).toBe('A');
  });

  it('should compute total', () => {
    const data: ChartDataPoint[] = [
      { label: 'A', value: 10 },
      { label: 'B', value: 20 },
      { label: 'C', value: 30 },
    ];
    const total = data.reduce((sum, p) => sum + p.value, 0);
    expect(total).toBe(60);
  });

  it('should filter by threshold', () => {
    const data: ChartDataPoint[] = [
      { label: 'Low', value: 5 },
      { label: 'Mid', value: 50 },
      { label: 'High', value: 95 },
    ];
    const aboveThreshold = data.filter(p => p.value > 10);
    expect(aboveThreshold).toHaveLength(2);
  });
});
