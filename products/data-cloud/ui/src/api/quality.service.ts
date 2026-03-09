/**
 * Data Quality API Service
 *
 * Provides API client for data quality monitoring, validation, and PII detection.
 *
 * @doc.type service
 * @doc.purpose Data quality and validation API client
 * @doc.layer frontend
 */

import axios, { AxiosInstance } from 'axios';

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
  private client: AxiosInstance;

  constructor(baseURL: string = '/api') {
    this.client = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * Get quality metrics for all datasets
   */
  async getQualityMetrics(): Promise<QualityMetric[]> {
    const response = await this.client.get<QualityMetric[]>('/quality/metrics');
    return response.data;
  }

  /**
   * Get quality metrics for a specific dataset
   */
  async getDatasetQuality(datasetId: string): Promise<QualityMetric> {
    const response = await this.client.get<QualityMetric>(`/quality/metrics/${datasetId}`);
    return response.data;
  }

  /**
   * Run PII scan on a dataset
   */
  async scanForPII(datasetId: string): Promise<PIIDetection[]> {
    const response = await this.client.post<PIIDetection[]>('/pii/scan', { datasetId });
    return response.data;
  }

  /**
   * Get PII detection results
   */
  async getPIIDetections(datasetId?: string): Promise<PIIDetection[]> {
    const response = await this.client.get<PIIDetection[]>('/pii/scan', {
      params: { datasetId },
    });
    return response.data;
  }

  /**
   * Mask PII fields
   */
  async maskPII(datasetId: string, fields: string[]): Promise<void> {
    await this.client.post('/pii/mask', { datasetId, fields });
  }

  /**
   * Get validation rules
   */
  async getValidationRules(datasetId?: string): Promise<ValidationRule[]> {
    const response = await this.client.get<ValidationRule[]>('/validation/rules', {
      params: { datasetId },
    });
    return response.data;
  }

  /**
   * Create validation rule
   */
  async createValidationRule(rule: Partial<ValidationRule>): Promise<ValidationRule> {
    const response = await this.client.post<ValidationRule>('/validation/rules', rule);
    return response.data;
  }

  /**
   * Update validation rule
   */
  async updateValidationRule(
    ruleId: string,
    rule: Partial<ValidationRule>
  ): Promise<ValidationRule> {
    const response = await this.client.put<ValidationRule>(
      `/validation/rules/${ruleId}`,
      rule
    );
    return response.data;
  }

  /**
   * Delete validation rule
   */
  async deleteValidationRule(ruleId: string): Promise<void> {
    await this.client.delete(`/validation/rules/${ruleId}`);
  }

  /**
   * Get anomaly events
   */
  async getAnomalies(datasetId?: string, limit: number = 50): Promise<AnomalyEvent[]> {
    const response = await this.client.get<AnomalyEvent[]>('/quality/anomalies', {
      params: { datasetId, limit },
    });
    return response.data;
  }

  /**
   * Correlate quality drop with events
   */
  async correlateQualityDrop(
    datasetId: string,
    timestamp: string
  ): Promise<{ events: any[]; rootCause?: string }> {
    const response = await this.client.post('/quality/correlate', {
      datasetId,
      timestamp,
    });
    return response.data;
  }
}

/**
 * Default quality service instance
 */
export const qualityService = new QualityService();

export default qualityService;

