/**
 * Data Quality API Service
 *
 * Provides API client for data quality monitoring, validation, and PII detection.
 *
 * @doc.type service
 * @doc.purpose Data quality and validation API client
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';

export interface QualityMetric {
  datasetId: string;
  datasetName: string;
  completeness: number;
  accuracy: number;
  freshness: number;
  consistency: number;
  overallScore: number;
  lastChecked: string;
  issues: QualityIssue[];
}

export interface QualityIssue {
  id: string;
  type: 'NULL_VALUES' | 'DUPLICATES' | 'OUTLIERS' | 'FORMAT_ERROR' | 'STALE_DATA';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  field: string;
  description: string;
  affectedRecords: number;
  detectedAt: string;
}

export interface PIIDetection {
  datasetId: string;
  fieldName: string;
  piiType: 'SSN' | 'EMAIL' | 'PHONE' | 'CREDIT_CARD' | 'ADDRESS' | 'NAME';
  confidence: number;
  sampleCount: number;
  masked: boolean;
  detectedAt: string;
}

export interface ValidationRule {
  id: string;
  name: string;
  type: 'FORMAT' | 'RANGE' | 'REQUIRED' | 'CUSTOM';
  field: string;
  condition: string;
  enabled: boolean;
  severity: 'WARNING' | 'ERROR';
  metadata: Record<string, any>;
}

export interface AnomalyEvent {
  id: string;
  timestamp: string;
  type: 'QUALITY_DROP' | 'VOLUME_SPIKE' | 'LATENCY_INCREASE' | 'SCHEMA_CHANGE';
  datasetId: string;
  description: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  metrics: Record<string, number>;
  rootCause?: string;
}

/**
 * Data Quality Service Client
 */
export class QualityService {
  /**
   * Get quality metrics for all datasets
   */
  async getQualityMetrics(): Promise<QualityMetric[]> {
    return apiClient.get<QualityMetric[]>('/quality/metrics');
  }

  /**
   * Get quality metrics for a specific dataset
   */
  async getDatasetQuality(datasetId: string): Promise<QualityMetric> {
    return apiClient.get<QualityMetric>(`/quality/metrics/${datasetId}`);
  }

  /**
   * Run PII scan on a dataset
   */
  async scanForPII(datasetId: string): Promise<PIIDetection[]> {
    return apiClient.post<PIIDetection[]>('/pii/scan', { datasetId });
  }

  /**
   * Get PII detection results
   */
  async getPIIDetections(datasetId?: string): Promise<PIIDetection[]> {
    return apiClient.get<PIIDetection[]>('/pii/scan', { params: { datasetId } });
  }

  /**
   * Mask PII fields
   */
  async maskPII(datasetId: string, fields: string[]): Promise<void> {
    await apiClient.post<void>('/pii/mask', { datasetId, fields });
  }

  /**
   * Get validation rules
   */
  async getValidationRules(datasetId?: string): Promise<ValidationRule[]> {
    return apiClient.get<ValidationRule[]>('/validation/rules', { params: { datasetId } });
  }

  /**
   * Create validation rule
   */
  async createValidationRule(rule: Partial<ValidationRule>): Promise<ValidationRule> {
    return apiClient.post<ValidationRule>('/validation/rules', rule);
  }

  /**
   * Update validation rule
   */
  async updateValidationRule(
    ruleId: string,
    rule: Partial<ValidationRule>
  ): Promise<ValidationRule> {
    return apiClient.put<ValidationRule>(`/validation/rules/${ruleId}`, rule);
  }

  /**
   * Delete validation rule
   */
  async deleteValidationRule(ruleId: string): Promise<void> {
    await apiClient.delete<void>(`/validation/rules/${ruleId}`);
  }

  /**
   * Get anomaly events
   */
  async getAnomalies(datasetId?: string, limit: number = 50): Promise<AnomalyEvent[]> {
    return apiClient.get<AnomalyEvent[]>('/quality/anomalies', { params: { datasetId, limit } });
  }

  /**
   * Correlate quality drop with events
   */
  async correlateQualityDrop(
    datasetId: string,
    timestamp: string
  ): Promise<{ events: any[]; rootCause?: string }> {
    return apiClient.post('/quality/correlate', { datasetId, timestamp });
  }
}

/**
 * Default quality service instance
 */
export const qualityService = new QualityService();

export default qualityService;

