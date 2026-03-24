/**
 * Performance analytics utilities
 */

import type { PerformanceMetric, PerformanceTrend } from './types';

/**
 * PerformanceAnalytics
 *
 * Machine learning and statistical utilities for trend analysis, regression detection, and change-point detection.
 */
export class PerformanceAnalytics {
  /**
   * linearRegression
   *
   * Compute linear regression line, slope, intercept, and R-squared for trend analysis.
   */
  static linearRegression(data: { x: number; y: number }[]): {
    slope: number;
    intercept: number;
    r2: number;
  } {
    const n = data.length;
    if (n < 2) return { slope: 0, intercept: 0, r2: 0 };

    const sumX = data.reduce((sum, point) => sum + point.x, 0);
    const sumY = data.reduce((sum, point) => sum + point.y, 0);
    const sumXY = data.reduce((sum, point) => sum + point.x * point.y, 0);
    const sumXX = data.reduce((sum, point) => sum + point.x * point.x, 0);

    const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;

    // Calculate R-squared
    const yMean = sumY / n;
    const totalSumSquares = data.reduce(
      (sum, point) => sum + Math.pow(point.y - yMean, 2),
      0
    );
    const residualSumSquares = data.reduce((sum, point) => {
      const predicted = slope * point.x + intercept;
      return sum + Math.pow(point.y - predicted, 2);
    }, 0);
    const r2 = 1 - residualSumSquares / totalSumSquares;

    return { slope, intercept, r2: Math.max(0, Math.min(1, r2)) };
  }

  /**
   * detectChangePoints
   *
   * Detect significant change points in time series using CUSUM algorithm.
   */
  static detectChangePoints(values: number[], threshold: number = 2): number[] {
    if (values.length < 10) return [];

    const changePoints: number[] = [];
    const mean = values.reduce((sum, val) => sum + val, 0) / values.length;
    const stdDev = Math.sqrt(
      values.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) /
        values.length
    );

    let cumSum = 0;
    let maxCumSum = 0;
    let minCumSum = 0;

    for (let i = 0; i < values.length; i++) {
      // eslint-disable-next-line security/detect-object-injection
      const normalized = (values[i] - mean) / stdDev;
      cumSum += normalized;

      if (cumSum > maxCumSum) {
        maxCumSum = cumSum;
      } else if (cumSum - maxCumSum < -threshold) {
        changePoints.push(i);
        cumSum = maxCumSum = 0;
      }

      if (cumSum < minCumSum) {
        minCumSum = cumSum;
      } else if (cumSum - minCumSum > threshold) {
        changePoints.push(i);
        cumSum = minCumSum = 0;
      }
    }

    return changePoints;
  }

  /**
   * analyzeTrend
   *
   * Analyze performance trend including direction, slope, confidence and change points.
   */
  static analyzeTrend(data: PerformanceMetric[]): PerformanceTrend | null {
    if (data.length < 5) return null;

    const points = data.map((metric, index) => ({
      x: index,
      y: metric.value,
    }));

    const regression = this.linearRegression(points);
    const changePoints = this.detectChangePoints(data.map((m) => m.value));

    let direction: 'improving' | 'degrading' | 'stable' = 'stable';
    if (Math.abs(regression.slope) > 0.01) {
      direction = regression.slope > 0 ? 'degrading' : 'improving'; // Assuming lower is better
    }

    const changePercent =
      data.length > 1
        ? ((data[data.length - 1].value - data[0].value) / data[0].value) * 100
        : 0;

    const regressionPoints = changePoints.reduce<Date[]>((acc, index) => {
      // eslint-disable-next-line security/detect-object-injection
      const metric = data[index];
      if (metric?.timestamp) {
        acc.push(metric.timestamp);
      }
      return acc;
    }, []);

    return {
      metric: data[0]?.id || 'unknown',
      direction,
      confidence: Math.max(0, Math.min(1, regression.r2)),
      changePercent,
      timeframe: `${data.length} samples`,
      regressionPoints,
      trend: regression.r2 > 0.8 ? 'linear' : 'polynomial',
    };
  }
}
