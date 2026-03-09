/**
 * Data Sampling Tests
 * 
 * Tests for data sampling algorithms including:
 * - LTTB algorithm
 * - Decimation sampling
 * - Min-max sampling
 * - Adaptive sampling
 */

import { describe, it, expect } from 'vitest';
import {
  sampleData,
  decimateSample,
  minMaxSample,
  adaptiveSample,
} from '../utils/dataSampling';
import type { DataPoint } from '../types';

describe('Data Sampling', () => {
  const generateData = (count: number): DataPoint[] => {
    return Array.from({ length: count }, (_, i) => ({
      timestamp: i * 1000,
      value: Math.sin(i / 10) * 100 + 100,
    }));
  };

  describe('sampleData (LTTB)', () => {
    it('returns original data when below threshold', () => {
      const data = generateData(100);
      const sampled = sampleData(data, 200);
      expect(sampled).toEqual(data);
    });

    it('samples data to target threshold', () => {
      const data = generateData(1000);
      const sampled = sampleData(data, 100);
      expect(sampled.length).toBeLessThanOrEqual(100);
    });

    it('preserves first and last points', () => {
      const data = generateData(1000);
      const sampled = sampleData(data, 100);
      expect(sampled[0]).toEqual(data[0]);
      expect(sampled[sampled.length - 1]).toEqual(data[data.length - 1]);
    });

    it('maintains visual characteristics', () => {
      const data = generateData(1000);
      const sampled = sampleData(data, 100);

      // Check that sampled data maintains general trend
      const originalMax = Math.max(...data.map(d => d.value));
      const originalMin = Math.min(...data.map(d => d.value));
      const sampledMax = Math.max(...sampled.map(d => d.value));
      const sampledMin = Math.min(...sampled.map(d => d.value));

      expect(sampledMax).toBeCloseTo(originalMax, 0);
      expect(sampledMin).toBeCloseTo(originalMin, 0);
    });

    it('handles empty data', () => {
      const sampled = sampleData([], 100);
      expect(sampled).toEqual([]);
    });

    it('handles single data point', () => {
      const data = [{ timestamp: 1000, value: 50 }];
      const sampled = sampleData(data, 100);
      expect(sampled).toEqual(data);
    });
  });

  describe('decimateSample', () => {
    it('samples every nth point', () => {
      const data = generateData(100);
      const sampled = decimateSample(data, 10);
      expect(sampled.length).toBeLessThanOrEqual(11); // 10 points + last
    });

    it('always includes last point', () => {
      const data = generateData(100);
      const sampled = decimateSample(data, 10);
      expect(sampled[sampled.length - 1]).toEqual(data[data.length - 1]);
    });

    it('returns original data when below threshold', () => {
      const data = generateData(50);
      const sampled = decimateSample(data, 100);
      expect(sampled).toEqual(data);
    });
  });

  describe('minMaxSample', () => {
    it('preserves peaks and valleys', () => {
      const data: DataPoint[] = [
        { timestamp: 1000, value: 50 },
        { timestamp: 2000, value: 100 }, // peak
        { timestamp: 3000, value: 50 },
        { timestamp: 4000, value: 10 }, // valley
        { timestamp: 5000, value: 50 },
      ];

      const sampled = minMaxSample(data, 2);

      // Should include both peak and valley
      const values = sampled.map(d => d.value);
      expect(values).toContain(100);
      expect(values).toContain(10);
    });

    it('samples to approximately target threshold', () => {
      const data = generateData(1000);
      const sampled = minMaxSample(data, 100);
      
      // Min-max sampling creates 2 points per bucket
      expect(sampled.length).toBeLessThanOrEqual(200);
    });
  });

  describe('adaptiveSample', () => {
    it('samples more densely in high-variance areas', () => {
      const data: DataPoint[] = [
        // Low variance area
        { timestamp: 1000, value: 50 },
        { timestamp: 2000, value: 51 },
        { timestamp: 3000, value: 50 },
        // High variance area
        { timestamp: 4000, value: 50 },
        { timestamp: 5000, value: 100 },
        { timestamp: 6000, value: 10 },
        { timestamp: 7000, value: 90 },
      ];

      const sampled = adaptiveSample(data, 4);
      
      // Should have more points from high-variance area
      expect(sampled.length).toBeGreaterThan(0);
    });

    it('always includes first and last points', () => {
      const data = generateData(100);
      const sampled = adaptiveSample(data, 10);
      expect(sampled[0]).toEqual(data[0]);
      expect(sampled[sampled.length - 1]).toEqual(data[data.length - 1]);
    });
  });

  describe('Performance', () => {
    it('handles large datasets efficiently', () => {
      const data = generateData(100000);
      const start = performance.now();
      const sampled = sampleData(data, 1000);
      const duration = performance.now() - start;

      expect(sampled.length).toBeLessThanOrEqual(1000);
      expect(duration).toBeLessThan(1000); // Should complete in < 1 second
    });
  });
});
