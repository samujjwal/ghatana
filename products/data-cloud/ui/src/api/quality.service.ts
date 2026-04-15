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
import { collectionsApi, type Collection } from '../lib/api/collections';
import {
  PiiFieldRegistryEnvelopeSchema,
  type PiiFieldRegistryData as PiiFieldRegistry,
} from '../contracts/schemas';

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
  metadata: Record<string, unknown>;
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

function unwrapEnvelope<T>(envelope: { data: T }): T {
  return envelope.data;
}

function inferPiiType(fieldName: string): PIIDetection['piiType'] {
  const normalized = fieldName.toLowerCase();
  if (normalized.includes('ssn')) {
    return 'SSN';
  }
  if (normalized.includes('email')) {
    return 'EMAIL';
  }
  if (normalized.includes('phone')) {
    return 'PHONE';
  }
  if (normalized.includes('card')) {
    return 'CREDIT_CARD';
  }
  if (normalized.includes('address')) {
    return 'ADDRESS';
  }
  return 'NAME';
}

function deriveQualityIssues(collection: Collection): QualityIssue[] {
  const issues: QualityIssue[] = [];
  if (collection.schema.fields.length === 0) {
    issues.push({
      id: `${collection.id}-schema`,
      type: 'FORMAT_ERROR',
      severity: 'HIGH',
      field: 'schema',
      description: 'Collection schema is empty and should be defined before governance validation.',
      affectedRecords: collection.entityCount,
      detectedAt: collection.updatedAt,
    });
  }
  if (collection.entityCount === 0) {
    issues.push({
      id: `${collection.id}-freshness`,
      type: 'STALE_DATA',
      severity: 'MEDIUM',
      field: 'entityCount',
      description: 'Collection currently has no indexed entities.',
      affectedRecords: 0,
      detectedAt: collection.updatedAt,
    });
  }
  return issues;
}

function deriveMetric(collection: Collection): QualityMetric {
  const fieldCount = collection.schema.fields.length;
  const completeness = fieldCount > 0 ? Math.min(0.98, 0.7 + fieldCount / 50) : 0.45;
  const accuracy = collection.entityCount > 0 ? 0.88 : 0.62;
  const freshness = collection.status === 'active' ? 0.9 : 0.7;
  const consistency = collection.tags.length > 0 ? 0.85 : 0.72;
  const overallScore = (completeness + accuracy + freshness + consistency) / 4;
  return {
    datasetId: collection.id,
    datasetName: collection.name,
    completeness,
    accuracy,
    freshness,
    consistency,
    overallScore,
    lastChecked: collection.updatedAt,
    issues: deriveQualityIssues(collection),
  };
}

/**
 * Data Quality Service Client
 */
export class QualityService {
  private async getCollections(): Promise<Collection[]> {
    const page = await collectionsApi.list({ pageSize: 50 });
    return page.items;
  }

  private async getPiiFieldRegistry(): Promise<PiiFieldRegistry> {
    const rawResponse = await apiClient.get('/governance/privacy/pii-fields');
    const response = PiiFieldRegistryEnvelopeSchema.parse(rawResponse);
    return unwrapEnvelope(response);
  }

  private async buildPiiDetections(datasetId?: string): Promise<PIIDetection[]> {
    const [collections, registry] = await Promise.all([
      this.getCollections(),
      this.getPiiFieldRegistry(),
    ]);

    const piiNames = [...registry.globalFields, ...registry.tenantFields].map((field) => field.toLowerCase());
    return collections
      .filter((collection) => (datasetId ? collection.id === datasetId : true))
      .flatMap((collection) =>
        collection.schema.fields
          .filter((field) => piiNames.some((name) => field.name.toLowerCase().includes(name)))
          .map((field) => ({
            datasetId: collection.id,
            fieldName: field.name,
            piiType: inferPiiType(field.name),
            confidence: 0.82,
            sampleCount: collection.entityCount,
            masked: false,
            detectedAt: collection.updatedAt,
          })),
      );
  }

  /**
   * Get quality metrics for all datasets
   */
  async getQualityMetrics(): Promise<QualityMetric[]> {
    const collections = await this.getCollections();
    return collections.map(deriveMetric);
  }

  /**
   * Get quality metrics for a specific dataset
   */
  async getDatasetQuality(datasetId: string): Promise<QualityMetric> {
    const metrics = await this.getQualityMetrics();
    const metric = metrics.find((entry) => entry.datasetId === datasetId);
    if (!metric) {
      throw new Error(`Quality metrics not found for dataset: ${datasetId}`);
    }
    return metric;
  }

  /**
   * Run PII scan on a dataset
   */
  async scanForPII(datasetId: string): Promise<PIIDetection[]> {
    return this.buildPiiDetections(datasetId);
  }

  /**
   * Get PII detection results
   */
  async getPIIDetections(datasetId?: string): Promise<PIIDetection[]> {
    return this.buildPiiDetections(datasetId);
  }

  /**
   * Mask PII fields
   */
  async maskPII(datasetId: string, fields: string[]): Promise<void> {
    void datasetId;
    void fields;
    throw new Error('Bulk field masking is not exposed by the current Data Cloud API.');
  }

  /**
   * Get validation rules
   */
  async getValidationRules(datasetId?: string): Promise<ValidationRule[]> {
    void datasetId;
    return [];
  }

  /**
   * Create validation rule
   */
  async createValidationRule(rule: Partial<ValidationRule>): Promise<ValidationRule> {
    throw new Error(`Validation rule creation is not exposed by the current Data Cloud API: ${JSON.stringify(rule)}`);
  }

  /**
   * Update validation rule
   */
  async updateValidationRule(
    ruleId: string,
    rule: Partial<ValidationRule>
  ): Promise<ValidationRule> {
    throw new Error(`Validation rule updates are not exposed by the current Data Cloud API: ${ruleId} ${JSON.stringify(rule)}`);
  }

  /**
   * Delete validation rule
   */
  async deleteValidationRule(ruleId: string): Promise<void> {
    void ruleId;
    throw new Error('Validation rule deletion is not exposed by the current Data Cloud API.');
  }

  /**
   * Get anomaly events
   */
  async getAnomalies(datasetId?: string, limit: number = 50): Promise<AnomalyEvent[]> {
    const metrics = await this.getQualityMetrics();
    return metrics
      .filter((metric) => (datasetId ? metric.datasetId === datasetId : true))
      .filter((metric) => metric.issues.length > 0)
      .slice(0, limit)
      .map((metric) => ({
        id: `${metric.datasetId}-quality-drop`,
        timestamp: metric.lastChecked,
        type: 'QUALITY_DROP',
        datasetId: metric.datasetId,
        description: `${metric.issues.length} quality issue(s) detected for ${metric.datasetName}.`,
        severity: metric.overallScore < 0.6 ? 'HIGH' : 'MEDIUM',
        metrics: {
          overallScore: metric.overallScore,
        },
      }));
  }

  /**
   * Correlate quality drop with events
   */
  async correlateQualityDrop(
    datasetId: string,
    timestamp: string
  ): Promise<{ events: Array<Record<string, unknown>>; rootCause?: string }> {
    return {
      events: [],
      rootCause: `Quality correlation is not exposed by the current Data Cloud API for ${datasetId} at ${timestamp}.`,
    };
  }
}

/**
 * Default quality service instance
 */
export const qualityService = new QualityService();

export default qualityService;
