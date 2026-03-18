/**
 * Service for ML-based anomaly detection using Java AI algorithms.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates anomaly detection by calling Java ML service (Isolation Forest algorithm)
 * to identify unusual patterns in security metrics, stores results in PostgreSQL, and
 * manages anomaly lifecycle.
 *
 * <p><b>Integration with Java ML Service</b><br>
 * 1. Queries baseline metrics (AnomalyBaseline entities)
 * 2. Calls Java Isolation Forest via AIServiceClient.detectAnomalies()
 * 3. Receives anomaly scores and classifications
 * 4. Stores SecurityAnomaly entities with Java execution reference
 * 5. Tracks javaServiceExecutionId for traceability
 *
 * <p><b>Detection Methods</b><br>
 * - Isolation Forest: Unsupervised outlier detection (primary method)
 * - Baseline comparison: Statistical deviation from expected baseline
 * - Trend analysis: Sudden changes in metric direction
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const service = new AnomalyDetectionService(
 *   repository,
 *   aiServiceClient,
 *   metricsCollector
 * );
 *
 * // Detect anomalies for a metric
 * const anomalies = await service.detectAnomalies({
 *   resourceId: "subnet-123",
 *   metricType: "network_traffic_bytes",
 *   dataPoints: [100, 105, 110, 350] // Last value is anomaly
 * });
 *
 * // Update baseline for future comparisons
 * await service.updateBaseline({
 *   resourceId: "subnet-123",
 *   metricType: "network_traffic_bytes"
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Orchestrates ML anomaly detection with Java algorithms
 * @doc.layer product
 * @doc.pattern Service
 */

import { SecurityAnomaly, AnomalyType } from "../models/anomaly/SecurityAnomaly.entity";
import {
  AnomalyBaseline,
  MetricType,
} from "../models/anomaly/AnomalyBaseline.entity";
import { SecurityAnomalyRepository } from "../repositories/SecurityAnomalyRepository";
import { AIServiceClient } from "../clients/ai/AIServiceClient";
import { MetricsCollector } from "../observability/MetricsCollector";

/**
 * Anomaly detection request parameters.
 */
export interface DetectionRequest {
  readonly resourceId: string;
  readonly metricType: MetricType;
  readonly dataPoints: readonly number[];
  readonly timestamps?: readonly Date[];
}

/**
 * Anomaly detection result from Java service.
 */
export interface DetectionResult {
  readonly anomalyScores: readonly number[]; // 0.0-1.0 for each data point
  readonly isAnomalous: readonly boolean[]; // Anomaly classification
  readonly anomalyIndices: readonly number[]; // Indices of anomalous points
  readonly javaExecutionId: string;
  readonly processingTimeMs: number;
  readonly confidence: number; // 0.0-1.0 overall confidence
}

/**
 * AnomalyDetectionService implementation.
 */
export class AnomalyDetectionService {
  private readonly _repository: SecurityAnomalyRepository;
  private readonly _aiClient: AIServiceClient;
  private readonly _metrics: MetricsCollector;

  /**
   * Creates a new AnomalyDetectionService.
   *
   * @param repository SecurityAnomalyRepository for persistence
   * @param aiClient AIServiceClient for Java ML service calls
   * @param metrics MetricsCollector for observability
   */
  constructor(
    repository: SecurityAnomalyRepository,
    aiClient: AIServiceClient,
    metrics: MetricsCollector
  ) {
    this._repository = repository;
    this._aiClient = aiClient;
    this._metrics = metrics;
  }

  /**
   * Detects anomalies in data points using Java Isolation Forest algorithm.
   *
   * <p><b>Algorithm Flow</b>
   * 1. Send data points to Java IsolationForest via HTTP
   * 2. Receive anomaly scores (0.0-1.0) for each point
   * 3. Filter points above threshold as detected anomalies
   * 4. Create SecurityAnomaly entities with Java execution reference
   * 5. Store in repository
   *
   * @param request Detection request with data points
   * @returns Array of created SecurityAnomaly entities
   * @throws Error if AI service call fails
   */
  async detectAnomalies(request: DetectionRequest): Promise<SecurityAnomaly[]> {
    const timer = this._metrics.startTimer("anomaly_detection_duration");

    try {
      // Call Java Isolation Forest algorithm
      const result = await this._aiClient.detectAnomalies({
        dataPoints: request.dataPoints,
        resourceId: request.resourceId,
        metricType: request.metricType,
      });

      // Filter anomalous data points
      const anomalies: SecurityAnomaly[] = [];
      const startTime = request.timestamps?.[0] ?? new Date();

      for (let i = 0; i < result.anomalyIndices.length; i++) {
        const idx = result.anomalyIndices[i];
        const score = result.anomalyScores[idx];

        // Determine anomaly type based on metric
        const anomalyType = this._mapMetricToAnomalyType(request.metricType);

        // Create anomaly entity
        const anomaly = SecurityAnomaly.create({
          type: anomalyType,
          severity: score, // Score (0.0-1.0) maps to severity
          baseline:
            idx > 0
              ? request.dataPoints[idx - 1]
              : request.dataPoints[idx],
          observed: request.dataPoints[idx],
          description: `Anomaly detected in ${request.metricType} for resource ${request.resourceId}`,
          javaServiceExecutionId: result.javaExecutionId,
          javaExecutionMetadata: {
            executionId: result.javaExecutionId,
            algorithm: "ISOLATION_FOREST",
            executedAt: new Date(),
            processingTimeMs: result.processingTimeMs,
            confidence: result.confidence,
          },
          relatedResourceIds: [request.resourceId],
        });

        // Save to repository
        await this._repository.save(anomaly);
        anomalies.push(anomaly);

        // Emit metric
        this._metrics.incrementCounter("anomalies_detected", 1, {
          type: anomalyType,
          severity: this._categorize(score),
          resourceId: request.resourceId,
        });
      }

      timer.end();

      return anomalies;
    } catch (error) {
      this._metrics.incrementCounter("anomaly_detection_errors", 1, {
        error: error instanceof Error ? error.message : "unknown",
      });
      throw error;
    }
  }

  /**
   * Updates baseline for a metric (calls Java BaselineCalculator).
   *
   * <p><b>Algorithm Flow</b>
   * 1. Query historical data points for resource+metric
   * 2. Send to Java BaselineCalculator
   * 3. Receive baseline value, threshold, std dev
   * 4. Store in AnomalyBaseline table
   * 5. Return updated baseline
   *
   * @param request Baseline update request
   * @returns Updated AnomalyBaseline entity
   */
  async updateBaseline(request: {
    readonly resourceId: string;
    readonly metricType: MetricType;
    readonly dataPoints: readonly number[];
  }): Promise<AnomalyBaseline> {
    const timer = this._metrics.startTimer("baseline_update_duration");

    try {
      // Call Java BaselineCalculator
      const result = await this._aiClient.calculateBaseline({
        dataPoints: request.dataPoints,
        resourceId: request.resourceId,
        metricType: request.metricType,
        confidenceInterval: 0.95,
      });

      // Create baseline entity
      const baseline = AnomalyBaseline.create({
        metricType: request.metricType,
        resourceId: request.resourceId,
        baselineValue: result.baseline,
        threshold: result.threshold,
        standardDeviation: result.standardDeviation,
        confidenceInterval: result.confidenceInterval,
        javaServiceExecutionId: result.javaExecutionId,
        dataPointsUsed: request.dataPoints.length,
      });

      // Store baseline (would be in real repository)
      // Note: In real implementation, would use BaselineRepository
      // For now, just return the created baseline

      timer.end();

      return baseline;
    } catch (error) {
      this._metrics.incrementCounter("baseline_update_errors", 1);
      throw error;
    }
  }

  /**
   * Scores an anomaly's risk level based on multiple factors.
   *
   * <p><b>Scoring Factors</b>
   * - Severity (weight: 0.5): How anomalous is the data point
   * - Deviation (weight: 0.3): Percentage deviation from baseline
   * - Frequency (weight: 0.2): How often this type occurs
   *
   * @param anomaly SecurityAnomaly to score
   * @param frequency How many similar anomalies in last 24h
   * @returns Risk score 0.0-1.0
   */
  scoreAnomalyRisk(anomaly: SecurityAnomaly, frequency: number): number {
    // Severity contributes 50%
    const severityScore = anomaly.severity * 0.5;

    // Deviation contributes 30%
    const deviationPercent =
      anomaly.baseline === 0
        ? 1.0
        : Math.min(
            1.0,
            Math.abs(anomaly.observed - anomaly.baseline) /
              Math.abs(anomaly.baseline)
          );
    const deviationScore = deviationPercent * 0.3;

    // Frequency contributes 20%
    // More than 3 similar anomalies = maximum frequency risk
    const frequencyScore = Math.min(1.0, frequency / 3) * 0.2;

    const totalScore = severityScore + deviationScore + frequencyScore;

    this._metrics.recordHistogram("anomaly_risk_score", totalScore);

    return totalScore;
  }

  /**
   * Gets recent anomalies for dashboard display.
   *
   * @param limit Number of anomalies to return (default 100)
   * @returns Array of recent anomalies
   */
  async getRecentAnomalies(limit: number = 100): Promise<SecurityAnomaly[]> {
    return this._repository.findRecent(limit);
  }

  /**
   * Gets anomalies by severity level.
   *
   * @param minSeverity Minimum severity threshold (0.0-1.0)
   * @returns Array of anomalies above threshold
   */
  async getAnomaliesBySeverity(minSeverity: number): Promise<SecurityAnomaly[]> {
    return this._repository.findBySeverity(minSeverity);
  }

  /**
   * Acknowledges an anomaly and adds investigation notes.
   *
   * @param anomalyId ID of anomaly to acknowledge
   * @param notes Investigation notes
   * @returns Updated SecurityAnomaly
   * @throws Error if anomaly not found
   */
  async acknowledgeAnomaly(anomalyId: string, notes: string): Promise<SecurityAnomaly> {
    const anomaly = await this._repository.findById(anomalyId);
    if (!anomaly) {
      throw new Error(`Anomaly not found: ${anomalyId}`);
    }

    const updated = anomaly.acknowledge(notes);
    await this._repository.update(updated);

    this._metrics.incrementCounter("anomalies_acknowledged", 1);

    return updated;
  }

  /**
   * Resolves an anomaly and marks as mitigated.
   *
   * @param anomalyId ID of anomaly to resolve
   * @param remediationStep Step taken to remediate
   * @returns Updated SecurityAnomaly
   * @throws Error if anomaly not found
   */
  async resolveAnomaly(
    anomalyId: string,
    remediationStep: string
  ): Promise<SecurityAnomaly> {
    const anomaly = await this._repository.findById(anomalyId);
    if (!anomaly) {
      throw new Error(`Anomaly not found: ${anomalyId}`);
    }

    let updated = anomaly.addRemediationStep(remediationStep);
    updated = updated.resolve();

    await this._repository.update(updated);

    this._metrics.incrementCounter("anomalies_resolved", 1);

    return updated;
  }

  /**
   * Maps MetricType to AnomalyType for classification.
   *
   * @param metricType Type of metric
   * @returns Corresponding AnomalyType
   */
  private _mapMetricToAnomalyType(metricType: MetricType): AnomalyType {
    const mapping: Record<MetricType, AnomalyType> = {
      network_traffic_bytes: "NETWORK_SPIKE",
      network_connections: "DDoS_PATTERN",
      cpu_utilization: "RESOURCE_EXHAUSTION",
      memory_utilization: "RESOURCE_EXHAUSTION",
      disk_io_rate: "RESOURCE_EXHAUSTION",
      process_count: "RESOURCE_EXHAUSTION",
      failed_logins: "FAILED_AUTHENTICATION",
      privilege_escalations: "PRIVILEGE_ESCALATION",
      api_request_rate: "DDoS_PATTERN",
      data_transfer_bytes: "UNUSUAL_DATA_ACCESS",
      custom: "UNKNOWN",
    };

    return mapping[metricType];
  }

  /**
   * Categorizes severity score to label.
   *
   * @param score Severity score (0.0-1.0)
   * @returns Category label
   */
  private _categorize(score: number): string {
    if (score >= 0.8) return "CRITICAL";
    if (score >= 0.6) return "HIGH";
    if (score >= 0.3) return "MEDIUM";
    return "LOW";
  }
}
