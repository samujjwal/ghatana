import type { PerformanceMetric } from '../../performance/usePerformanceMonitoring';

/**
 * MLAnalytics
 *
 * Machine learning utilities for pattern detection, anomaly detection, and trend analysis.
 */
export class MLAnalytics {
  /**
   * linearRegression
   *
   * Perform simple linear regression on data points to compute slope, intercept and R-squared.
   */
  static linearRegression(data: { x: number[]; y: number }[]): {
    slope: number;
    intercept: number;
    r2: number;
  } {
    if (data.length < 2) return { slope: 0, intercept: 0, r2: 0 };

    const points = data.map((d) => ({ x: d.x[0] || 0, y: d.y }));
    const n = points.length;

    const sumX = points.reduce((sum, p) => sum + p.x, 0);
    const sumY = points.reduce((sum, p) => sum + p.y, 0);
    const sumXY = points.reduce((sum, p) => sum + p.x * p.y, 0);
    const sumXX = points.reduce((sum, p) => sum + p.x * p.x, 0);

    const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;

    const yMean = sumY / n;
    const totalSumSquares = points.reduce(
      (sum, p) => sum + Math.pow(p.y - yMean, 2),
      0
    );
    const residualSumSquares = points.reduce((sum, p) => {
      const predicted = slope * p.x + intercept;
      return sum + Math.pow(p.y - predicted, 2);
    }, 0);

    const r2 =
      totalSumSquares > 0 ? 1 - residualSumSquares / totalSumSquares : 0;

    return { slope, intercept, r2: Math.max(0, Math.min(1, r2)) };
  }

  /**
   * detectAnomalies
   *
   * Detect statistical outliers in a time series using Z-score method.
   */
  static detectAnomalies(values: number[], threshold = 2): number[] {
    if (values.length < 3) return [];

    const mean = values.reduce((sum, val) => sum + val, 0) / values.length;
    const variance =
      values.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) /
      values.length;
    const stdDev = Math.sqrt(variance);

    const anomalies: number[] = [];
    values.forEach((value, index) => {
      const zScore = Math.abs(value - mean) / stdDev;
      if (zScore > threshold) anomalies.push(index);
    });

    return anomalies;
  }

  /**
   * findPatterns
   *
   * Detect recurring patterns and trends in performance metrics (e.g., hourly degradation, increasing build times).
   */
  static findPatterns(data: PerformanceMetric[]): string[] {
    const patterns: string[] = [];

    const hourlyData = new Map<number, number[]>();
    data.forEach((metric) => {
      const hour = metric.timestamp.getHours();
      if (!hourlyData.has(hour)) hourlyData.set(hour, []);
      const values = hourlyData.get(hour);
      if (values) {
        values.push(metric.value);
      }
    });

    hourlyData.forEach((values, hour) => {
      const avgValue =
        values.reduce((sum, val) => sum + val, 0) / values.length;
      const overallAvg =
        data.reduce((sum, metric) => sum + metric.value, 0) / data.length;

      if (avgValue > overallAvg * 1.2 && values.length > 3) {
        patterns.push(`Performance degrades during hour ${hour}:00`);
      }
    });

    const buildData = data.filter((m) => m.context.buildId);
    if (buildData.length > 5) {
      const values = buildData.map((m) => m.value);
      const trend = this.linearRegression(
        values.map((val, idx) => ({ x: [idx], y: val }))
      );
      if (trend.slope > 0.1)
        patterns.push('Build times are increasing over time');
    }

    return patterns;
  }
}
