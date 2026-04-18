/**
 * Anomaly Detection Engine
 *
 * Provides statistical anomaly detection capabilities for monitoring metrics
 * and identifying unusual patterns in data.
 *
 * @doc.type class
 * @doc.purpose Anomaly detection using statistical methods
 * @doc.layer product
 * @doc.pattern Service
 */

export interface MetricDataPoint {
  timestamp: number;
  value: number;
  metadata?: Record<string, unknown>;
}

export interface AnomalyDetectionResult {
  isAnomaly: boolean;
  value: number;
  expectedValue: number;
  deviation: number;
  zScore: number;
  severity: 'info' | 'warning' | 'critical';
  confidence: number;
  description: string;
}

export interface BaselineStatistics {
  mean: number;
  stdDev: number;
  min: number;
  max: number;
  count: number;
  trend: 'increasing' | 'decreasing' | 'stable';
}

export interface DetectionConfig {
  sensitivity: number; // Number of standard deviations for threshold (default: 3)
  minDataPoints: number; // Minimum data points required (default: 30)
  lookbackWindow: number; // Number of recent data points to consider (default: 100)
}

/**
 * Anomaly Detection Engine
 *
 * Uses statistical methods (Z-score, IQR, moving averages) to detect anomalies
 * in time-series metric data.
 */
export class AnomalyDetectionEngine {
  private defaultConfig: DetectionConfig = {
    sensitivity: 3,
    minDataPoints: 30,
    lookbackWindow: 100,
  };

  /**
   * Detect anomalies in a time-series dataset
   */
  detectAnomalies(
    dataPoints: MetricDataPoint[],
    config: Partial<DetectionConfig> = {}
  ): AnomalyDetectionResult[] {
    const mergedConfig = { ...this.defaultConfig, ...config };

    if (dataPoints.length < mergedConfig.minDataPoints) {
      return [];
    }

    // Sort by timestamp
    const sorted = [...dataPoints].sort((a, b) => a.timestamp - b.timestamp);

    // Calculate baseline statistics
    const baseline = this.calculateBaselineStatistics(sorted);

    // Detect anomalies using Z-score method
    const anomalies: AnomalyDetectionResult[] = [];

    for (const point of sorted) {
      const zScore = this.calculateZScore(point.value, baseline);
      const deviation = Math.abs(point.value - baseline.mean);

      if (Math.abs(zScore) >= mergedConfig.sensitivity) {
        const severity = this.determineSeverity(Math.abs(zScore));
        const confidence = this.calculateConfidence(Math.abs(zScore), baseline.count);

        anomalies.push({
          isAnomaly: true,
          value: point.value,
          expectedValue: baseline.mean,
          deviation,
          zScore,
          severity,
          confidence,
          description: this.generateDescription(point, baseline, zScore, severity),
        });
      }
    }

    return anomalies;
  }

  /**
   * Calculate baseline statistics from data points
   */
  calculateBaselineStatistics(dataPoints: MetricDataPoint[]): BaselineStatistics {
    const values = dataPoints.map((p) => p.value);

    const mean = values.reduce((sum, v) => sum + v, 0) / values.length;
    const variance = values.reduce((sum, v) => sum + Math.pow(v - mean, 2), 0) / values.length;
    const stdDev = Math.sqrt(variance);
    const min = Math.min(...values);
    const max = Math.max(...values);

    // Calculate trend using linear regression
    const trend = this.calculateTrend(dataPoints);

    return {
      mean,
      stdDev,
      min,
      max,
      count: values.length,
      trend,
    };
  }

  /**
   * Calculate Z-score for a value
   */
  private calculateZScore(value: number, baseline: BaselineStatistics): number {
    if (baseline.stdDev === 0) return 0;
    return (value - baseline.mean) / baseline.stdDev;
  }

  /**
   * Determine severity based on Z-score magnitude
   */
  private determineSeverity(zScore: number): 'info' | 'warning' | 'critical' {
    if (zScore >= 5) return 'critical';
    if (zScore >= 3) return 'warning';
    return 'info';
  }

  /**
   * Calculate confidence based on Z-score and sample size
   */
  private calculateConfidence(zScore: number, sampleSize: number): number {
    // Higher confidence with higher Z-score and larger sample size
    const zScoreConfidence = Math.min(zScore / 5, 1);
    const sampleSizeConfidence = Math.min(sampleSize / 100, 1);
    return (zScoreConfidence + sampleSizeConfidence) / 2;
  }

  /**
   * Calculate trend using simple linear regression
   */
  private calculateTrend(dataPoints: MetricDataPoint[]): 'increasing' | 'decreasing' | 'stable' {
    if (dataPoints.length < 2) return 'stable';

    const n = dataPoints.length;
    let sumX = 0;
    let sumY = 0;
    let sumXY = 0;
    let sumX2 = 0;

    for (let i = 0; i < n; i++) {
      const x = i;
      const y = dataPoints[i].value;
      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

    // Determine trend based on slope
    if (slope > 0.01) return 'increasing';
    if (slope < -0.01) return 'decreasing';
    return 'stable';
  }

  /**
   * Generate human-readable description
   */
  private generateDescription(
    point: MetricDataPoint,
    baseline: BaselineStatistics,
    zScore: number,
    severity: string
  ): string {
    const direction = point.value > baseline.mean ? 'higher' : 'lower';
    const percentChange = ((point.value - baseline.mean) / baseline.mean) * 100;

    return `Value is ${percentChange.toFixed(1)}% ${direction} than expected (${point.value.toFixed(2)} vs ${baseline.mean.toFixed(2)}), ${severity} severity (Z-score: ${zScore.toFixed(2)})`;
  }

  /**
   * Detect anomalies using IQR (Interquartile Range) method
   */
  detectAnomaliesIQR(
    dataPoints: MetricDataPoint[],
    multiplier: number = 1.5
  ): AnomalyDetectionResult[] {
    if (dataPoints.length < 10) return [];

    const values = dataPoints.map((p) => p.value).sort((a, b) => a - b);
    const q1 = this.percentile(values, 25);
    const q3 = this.percentile(values, 75);
    const iqr = q3 - q1;
    const lowerBound = q1 - multiplier * iqr;
    const upperBound = q3 + multiplier * iqr;

    const anomalies: AnomalyDetectionResult[] = [];

    for (const point of dataPoints) {
      if (point.value < lowerBound || point.value > upperBound) {
        const isLow = point.value < lowerBound;
        const deviation = isLow ? lowerBound - point.value : point.value - upperBound;
        const zScore = deviation / (iqr / 2);

        anomalies.push({
          isAnomaly: true,
          value: point.value,
          expectedValue: (q1 + q3) / 2,
          deviation,
          zScore,
          severity: Math.abs(zScore) > 3 ? 'critical' : 'warning',
          confidence: 0.8,
          description: `Value outside IQR bounds (${lowerBound.toFixed(2)} - ${upperBound.toFixed(2)})`,
        });
      }
    }

    return anomalies;
  }

  /**
   * Calculate percentile
   */
  private percentile(values: number[], p: number): number {
    const index = (p / 100) * (values.length - 1);
    const lower = Math.floor(index);
    const upper = Math.ceil(index);
    const weight = index - lower;

    if (upper >= values.length) return values[values.length - 1];
    return values[lower] * (1 - weight) + values[upper] * weight;
  }

  /**
   * Detect anomalies using moving average
   */
  detectAnomaliesMovingAverage(
    dataPoints: MetricDataPoint[],
    windowSize: number = 10,
    thresholdMultiplier: number = 2
  ): AnomalyDetectionResult[] {
    if (dataPoints.length < windowSize * 2) return [];

    const sorted = [...dataPoints].sort((a, b) => a.timestamp - b.timestamp);
    const anomalies: AnomalyDetectionResult[] = [];

    for (let i = windowSize; i < sorted.length; i++) {
      const window = sorted.slice(i - windowSize, i);
      const movingAverage = window.reduce((sum, p) => sum + p.value, 0) / windowSize;
      const movingStdDev = Math.sqrt(
        window.reduce((sum, p) => sum + Math.pow(p.value - movingAverage, 2), 0) / windowSize
      );

      const current = sorted[i];
      const deviation = Math.abs(current.value - movingAverage);
      const zScore = movingStdDev > 0 ? deviation / movingStdDev : 0;

      if (zScore >= thresholdMultiplier) {
        anomalies.push({
          isAnomaly: true,
          value: current.value,
          expectedValue: movingAverage,
          deviation,
          zScore,
          severity: zScore >= 3 ? 'critical' : 'warning',
          confidence: 0.75,
          description: `Deviation from ${windowSize}-point moving average`,
        });
      }
    }

    return anomalies;
  }
}

/**
 * Singleton instance
 */
let engineInstance: AnomalyDetectionEngine | null = null;

export function getAnomalyDetectionEngine(): AnomalyDetectionEngine {
  if (!engineInstance) {
    engineInstance = new AnomalyDetectionEngine();
  }
  return engineInstance;
}
