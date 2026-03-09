/**
 * Unit tests for AnomalyDetectionService
 *
 * Tests validate:
 * - Anomaly detection workflow with Java ML integration
 * - Baseline calculation and updates  
 * - Repository persistence operations
 * - Metrics collection on all operations
 * - Error handling and edge cases
 *
 * @see AnomalyDetectionService
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { AnomalyDetectionService, DetectionRequest } from '../../../services/anomaly/AnomalyDetectionService';
import { MetricsCollector, NoopMetricsCollector } from '../../../observability/MetricsCollector';

// Mock repository
class MockSecurityAnomalyRepository {
  private anomalies: Map<string, unknown> = new Map();

  async save(anomaly: unknown): Promise<void> {
    this.anomalies.set(anomaly.id, anomaly);
  }

  async findById(id: string): Promise<any | null> {
    return this.anomalies.get(id) || null;
  }

  async findByType(type: string): Promise<unknown[]> {
    return Array.from(this.anomalies.values()).filter((a) => a.type === type);
  }

  async findByStatus(status: string): Promise<unknown[]> {
    return Array.from(this.anomalies.values()).filter((a) => a.status === status);
  }

  async query(): Promise<unknown[]> {
    return Array.from(this.anomalies.values());
  }

  clear(): void {
    this.anomalies.clear();
  }
}

// Mock AI service client
class MockAIServiceClient {
  async detectAnomalies(request: unknown) {
    return {
      anomalyScores: [0.1, 0.2, 0.9, 0.15],
      isAnomalous: [false, false, true, false],
      anomalyIndices: [2],
      javaExecutionId: 'java-exec-123',
      processingTimeMs: 45,
      confidence: 0.95,
    };
  }

  async calculateBaseline(request: unknown) {
    return {
      baseline: 50,
      threshold: 75,
      standardDeviation: 5.5,
      confidenceInterval: 0.95,
      javaExecutionId: 'java-baseline-456',
    };
  }
}

describe('AnomalyDetectionService', () => {
  let service: AnomalyDetectionService;
  let repository: MockSecurityAnomalyRepository;
  let aiClient: MockAIServiceClient;
  let metrics: MetricsCollector;

  beforeEach(() => {
    repository = new MockSecurityAnomalyRepository();
    aiClient = new MockAIServiceClient();
    metrics = new NoopMetricsCollector();
    service = new AnomalyDetectionService(repository as unknown, aiClient as unknown, metrics);
  });

  afterEach(() => {
    vi.clearAllMocks();
    repository.clear();
  });

  describe('detectAnomalies', () => {
    /**
     * Should detect anomalies and create SecurityAnomaly entities
     *
     * GIVEN: Valid detection request with data points
     * WHEN: detectAnomalies() is called
     * THEN: Returns array of SecurityAnomaly entities with Java metadata
     */
    it('should detect anomalies using Java Isolation Forest', async () => {
      // GIVEN
      const request: DetectionRequest = {
        resourceId: 'subnet-123',
        metricType: 'network_traffic_bytes',
        dataPoints: [100, 105, 110, 350, 115],
      };

      // WHEN
      const anomalies = await service.detectAnomalies(request);

      // THEN
      expect(anomalies).toBeDefined();
      expect(anomalies.length).toBeGreaterThan(0);
      expect(anomalies[0]).toHaveProperty('javaServiceExecutionId');
      expect(anomalies[0].javaServiceExecutionId).toBe('java-exec-123');
    });

    /**
     * Should map metric types to anomaly types correctly
     *
     * GIVEN: Detection request with network_traffic_bytes metric
     * WHEN: detectAnomalies() is called
     * THEN: Anomaly type is set to NETWORK_SPIKE
     */
    it('should map metric types to correct anomaly types', async () => {
      // GIVEN
      const request: DetectionRequest = {
        resourceId: 'res-456',
        metricType: 'network_traffic_bytes',
        dataPoints: [1000, 1100, 5000],
      };

      // WHEN
      const anomalies = await service.detectAnomalies(request);

      // THEN
      expect(anomalies.length).toBeGreaterThan(0);
      expect(anomalies[0].type).toBe('NETWORK_SPIKE');
    });

    /**
     * Should include timestamps if provided
     *
     * GIVEN: Detection request with timestamps
     * WHEN: detectAnomalies() is called
     * THEN: Anomalies are created with correct timestamps
     */
    it('should use provided timestamps for anomalies', async () => {
      // GIVEN
      const now = new Date();
      const timestamps = [
        new Date(now.getTime() - 3000),
        new Date(now.getTime() - 2000),
        new Date(now.getTime() - 1000),
        now,
      ];

      const request: DetectionRequest = {
        resourceId: 'res-789',
        metricType: 'cpu_utilization',
        dataPoints: [40, 42, 45, 95],
        timestamps,
      };

      // WHEN
      const anomalies = await service.detectAnomalies(request);

      // THEN
      expect(anomalies.length).toBeGreaterThan(0);
      expect(anomalies[0].detectedAt).toBeInstanceOf(Date);
    });

    /**
     * Should store anomalies in repository
     *
     * GIVEN: Valid detection request
     * WHEN: detectAnomalies() is called
     * THEN: Anomalies are persisted to repository
     */
    it('should persist detected anomalies to repository', async () => {
      // GIVEN
      const saveSpy = vi.spyOn(repository, 'save');
      const request: DetectionRequest = {
        resourceId: 'res-test',
        metricType: 'memory_usage_percent',
        dataPoints: [60, 65, 98],
      };

      // WHEN
      await service.detectAnomalies(request);

      // THEN
      expect(saveSpy).toHaveBeenCalled();
    });

    /**
     * Should collect metrics on anomaly detection
     *
     * GIVEN: Detection service with spy on metrics
     * WHEN: detectAnomalies() is called
     * THEN: Metrics are recorded for detection and severity
     */
    it('should collect metrics for detected anomalies', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new AnomalyDetectionService(repository as unknown, aiClient as unknown, mockMetrics);

      const request: DetectionRequest = {
        resourceId: 'res-metrics',
        metricType: 'network_traffic_bytes',
        dataPoints: [1000, 1100, 8000],
      };

      // WHEN
      await service.detectAnomalies(request);

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'anomalies_detected',
        1,
        expect.objectContaining({
          resourceId: 'res-metrics',
        })
      );
    });

    /**
     * Should handle empty anomaly indices gracefully
     *
     * GIVEN: Mock AI client returns empty anomaly indices
     * WHEN: detectAnomalies() is called
     * THEN: Returns empty array (no anomalies detected)
     */
    it('should handle case when no anomalies are detected', async () => {
      // GIVEN
      const noAnomaliesClient = {
        detectAnomalies: async () => ({
          anomalyScores: [0.1, 0.2, 0.15],
          isAnomalous: [false, false, false],
          anomalyIndices: [],
          javaExecutionId: 'exec-clean',
          processingTimeMs: 30,
          confidence: 0.98,
        }),
        calculateBaseline: aiClient.calculateBaseline,
      };

      service = new AnomalyDetectionService(
        repository as unknown,
        noAnomaliesClient as unknown,
        metrics
      );

      const request: DetectionRequest = {
        resourceId: 'res-clean',
        metricType: 'cpu_utilization',
        dataPoints: [45, 48, 42],
      };

      // WHEN
      const anomalies = await service.detectAnomalies(request);

      // THEN
      expect(anomalies).toEqual([]);
    });

    /**
     * Should propagate AI service errors
     *
     * GIVEN: AI service that throws error
     * WHEN: detectAnomalies() is called
     * THEN: Error is re-thrown and error metrics recorded
     */
    it('should handle AI service errors', async () => {
      // GIVEN
      const errorClient = {
        detectAnomalies: async () => {
          throw new Error('AI service connection failed');
        },
        calculateBaseline: aiClient.calculateBaseline,
      };

      const mockMetrics = new MetricsCollector();
      const errorSpy = vi.spyOn(mockMetrics, 'incrementCounter');

      service = new AnomalyDetectionService(
        repository as unknown,
        errorClient as unknown,
        mockMetrics
      );

      const request: DetectionRequest = {
        resourceId: 'res-error',
        metricType: 'disk_io_bytes',
        dataPoints: [500, 550, 600],
      };

      // WHEN & THEN
      await expect(service.detectAnomalies(request)).rejects.toThrow(
        'AI service connection failed'
      );

      expect(errorSpy).toHaveBeenCalledWith(
        'anomaly_detection_errors',
        1,
        expect.any(Object)
      );
    });
  });

  describe('updateBaseline', () => {
    /**
     * Should create baseline entity with Java calculated values
     *
     * GIVEN: Baseline update request with data points
     * WHEN: updateBaseline() is called
     * THEN: Returns AnomalyBaseline with Java-calculated values
     */
    it('should calculate baseline using Java service', async () => {
      // GIVEN
      const dataPoints = Array.from({ length: 100 }, (_, i) => 50 + Math.random() * 10);
      const request = {
        resourceId: 'subnet-456',
        metricType: 'network_traffic_bytes' as const,
        dataPoints,
      };

      // WHEN
      const baseline = await service.updateBaseline(request);

      // THEN
      expect(baseline).toBeDefined();
      expect(baseline.baselineValue).toBe(50);
      expect(baseline.threshold).toBe(75);
      expect(baseline.standardDeviation).toBe(5.5);
      expect(baseline.javaServiceExecutionId).toBe('java-baseline-456');
    });

    /**
     * Should include correct data points count
     *
     * GIVEN: Baseline request with 150 data points
     * WHEN: updateBaseline() is called
     * THEN: Baseline records data points count
     */
    it('should record number of data points used', async () => {
      // GIVEN
      const dataPoints = Array.from({ length: 150 }, (_, i) => 45 + Math.random() * 8);
      const request = {
        resourceId: 'res-150',
        metricType: 'cpu_utilization' as const,
        dataPoints,
      };

      // WHEN
      const baseline = await service.updateBaseline(request);

      // THEN
      expect(baseline.dataPointsUsed).toBe(150);
    });

    /**
     * Should handle minimum data points requirement
     *
     * GIVEN: Baseline request with fewer than 100 data points
     * WHEN: updateBaseline() is called  
     * THEN: Baseline is still created (warning in production)
     */
    it('should work with small number of data points', async () => {
      // GIVEN
      const request = {
        resourceId: 'res-small',
        metricType: 'memory_usage_percent' as const,
        dataPoints: [45, 50, 48, 52, 49],
      };

      // WHEN
      const baseline = await service.updateBaseline(request);

      // THEN
      expect(baseline).toBeDefined();
      expect(baseline.dataPointsUsed).toBe(5);
    });

    /**
     * Should collect baseline update metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: updateBaseline() is called
     * THEN: Baseline update metric is recorded
     */
    it('should collect metrics on baseline update', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      service = new AnomalyDetectionService(repository as unknown, aiClient as unknown, mockMetrics);
      const startTimerSpy = vi.spyOn(mockMetrics, 'startTimer');

      const request = {
        resourceId: 'res-metrics',
        metricType: 'disk_io_bytes' as const,
        dataPoints: Array.from({ length: 100 }, () => 1000),
      };

      // WHEN
      await service.updateBaseline(request);

      // THEN
      expect(startTimerSpy).toHaveBeenCalledWith('baseline_update_duration');
    });

    /**
     * Should handle AI service errors during baseline calculation
     *
     * GIVEN: AI service that throws error
     * WHEN: updateBaseline() is called
     * THEN: Error is propagated
     */
    it('should handle baseline calculation errors', async () => {
      // GIVEN
      const errorClient = {
        detectAnomalies: aiClient.detectAnomalies,
        calculateBaseline: async () => {
          throw new Error('Baseline calculation failed');
        },
      };

      service = new AnomalyDetectionService(
        repository as unknown,
        errorClient as unknown,
        metrics
      );

      const request = {
        resourceId: 'res-baseline-error',
        metricType: 'cpu_utilization' as const,
        dataPoints: Array.from({ length: 100 }, () => 50),
      };

      // WHEN & THEN
      await expect(service.updateBaseline(request)).rejects.toThrow(
        'Baseline calculation failed'
      );
    });
  });

  describe('Metric type mapping', () => {
    /**
     * Should map all metric types to correct anomaly types
     *
     * GIVEN: Various metric types
     * WHEN: Used in detection request
     * THEN: Correct anomaly types are assigned
     */
    it('should map network_traffic_bytes to NETWORK_SPIKE', async () => {
      // GIVEN & WHEN
      const anomalies = await service.detectAnomalies({
        resourceId: 'res-1',
        metricType: 'network_traffic_bytes',
        dataPoints: [1000, 1100, 5000],
      });

      // THEN
      expect(anomalies[0].type).toBe('NETWORK_SPIKE');
    });

    it('should map cpu_utilization to RESOURCE_EXHAUSTION', async () => {
      // GIVEN & WHEN
      const anomalies = await service.detectAnomalies({
        resourceId: 'res-2',
        metricType: 'cpu_utilization',
        dataPoints: [40, 45, 98],
      });

      // THEN
      expect(anomalies[0].type).toBe('RESOURCE_EXHAUSTION');
    });

    it('should map memory_usage_percent to RESOURCE_EXHAUSTION', async () => {
      // GIVEN & WHEN
      const anomalies = await service.detectAnomalies({
        resourceId: 'res-3',
        metricType: 'memory_usage_percent',
        dataPoints: [60, 65, 95],
      });

      // THEN
      expect(anomalies[0].type).toBe('RESOURCE_EXHAUSTION');
    });
  });

  describe('Error metrics', () => {
    /**
     * Should record all types of errors with context
     *
     * GIVEN: Various error scenarios
     * WHEN: Errors occur during detection
     * THEN: Metrics include error type and context
     */
    it('should record error details in metrics', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');

      const errorClient = {
        detectAnomalies: async () => {
          throw new Error('Network timeout');
        },
        calculateBaseline: aiClient.calculateBaseline,
      };

      service = new AnomalyDetectionService(
        repository as unknown,
        errorClient as unknown,
        mockMetrics
      );

      // WHEN
      try {
        await service.detectAnomalies({
          resourceId: 'res-error-test',
          metricType: 'cpu_utilization',
          dataPoints: [50, 55, 60],
        });
      } catch {
        // Error expected
      }

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'anomaly_detection_errors',
        1,
        expect.objectContaining({
          error: 'Network timeout',
        })
      );
    });
  });
});
