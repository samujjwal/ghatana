/**
 * Cost & Query Optimization API Service
 *
 * Provides API client for cost analysis, query optimization, and predictive routing.
 *
 * @doc.type service
 * @doc.purpose Cost and query optimization API client
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';

export interface CostBreakdown {
  total: number;
  currency: string;
  period: string;
  byDataset: Array<{
    datasetId: string;
    datasetName: string;
    cost: number;
    percentage: number;
  }>;
  byQuery: Array<{
    queryId: string;
    queryHash: string;
    cost: number;
    executionCount: number;
    avgCost: number;
  }>;
  byUser: Array<{
    userId: string;
    userName: string;
    cost: number;
    queryCount: number;
  }>;
}

export interface QueryOptimization {
  queryId: string;
  originalQuery: string;
  suggestions: Array<{
    type: 'PARTITION_FILTER' | 'INDEX' | 'MATERIALIZED_VIEW' | 'QUERY_REWRITE';
    description: string;
    estimatedSavings: number;
    savingsPercentage: number;
    effort: 'LOW' | 'MEDIUM' | 'HIGH';
    applicable: boolean;
    suggestedQuery?: string;
  }>;
  currentCost: number;
  potentialCost: number;
}

export interface QueryPrediction {
  estimatedCost: number;
  estimatedLatency: number;
  recommendedTier: 'HOT' | 'WARM' | 'COLD';
  confidence: number;
  warnings: string[];
}

export interface MaterializedViewSuggestion {
  name: string;
  query: string;
  pattern: string;
  frequency: number;
  estimatedSavings: number;
  refreshStrategy: 'REAL_TIME' | 'SCHEDULED' | 'ON_DEMAND';
  refreshInterval?: string;
}

export interface HotnessMetric {
  datasetId: string;
  tier: 'HOT' | 'WARM' | 'COLD';
  accessFrequency: number;
  lastAccessed: string;
  predictedTier: string;
  recommendedAction?: string;
}

/**
 * Cost Service Client
 */
export class CostService {
  /**
   * Get cost breakdown analysis
   */
  async getCostAnalysis(period: string = '30d'): Promise<CostBreakdown> {
    return apiClient.get<CostBreakdown>('/cost/analysis', {
      params: { period },
    });
  }

  /**
   * Get optimization suggestions for a query
   */
  async getQueryOptimization(query: string): Promise<QueryOptimization> {
    return apiClient.post<QueryOptimization>('/query/optimize', { query });
  }

  /**
   * Apply optimization suggestion
   */
  async applyOptimization(
    queryId: string,
    suggestionType: string
  ): Promise<{ success: boolean; newQuery?: string }> {
    return apiClient.post(`/query/optimize/${queryId}/apply`, { suggestionType });
  }

  /**
   * Predict query cost and latency
   */
  async predictQuery(query: string, tier?: string): Promise<QueryPrediction> {
    return apiClient.post<QueryPrediction>('/query/predict', { query, tier });
  }

  /**
   * Get materialized view suggestions
   */
  async getMaterializedViewSuggestions(): Promise<MaterializedViewSuggestion[]> {
    return apiClient.get<MaterializedViewSuggestion[]>('/query/materialized-views/suggestions');
  }

  /**
   * Create materialized view
   */
  async createMaterializedView(
    suggestion: MaterializedViewSuggestion
  ): Promise<{ id: string; status: string }> {
    return apiClient.post('/query/materialized-views', suggestion);
  }

  /**
   * Get hotness metrics for datasets
   */
  async getHotnessMetrics(): Promise<HotnessMetric[]> {
    return apiClient.get<HotnessMetric[]>('/cost/hotness');
  }

  /**
   * Update tier for a dataset
   */
  async updateDatasetTier(
    datasetId: string,
    tier: 'HOT' | 'WARM' | 'COLD'
  ): Promise<void> {
    await apiClient.put<void>(`/cost/hotness/${datasetId}`, { tier });
  }

  /**
   * Get cost forecast
   */
  async getCostForecast(
    days: number = 30
  ): Promise<{ forecast: Array<{ date: string; cost: number }> }> {
    return apiClient.get('/cost/forecast', { params: { days } });
  }
}

/**
 * Default cost service instance
 */
export const costService = new CostService();

export default costService;

// =============================================================================
// Tier Migration API (B10)
// =============================================================================

/** Storage tiers eligible as manual migration targets. */
export type MigrationTargetTier = 'WARM' | 'COLD';

/** Response shape from POST /api/v1/collections/:id/migrate */
export interface MigrateCollectionResult {
  collection: string;
  targetTier: MigrationTargetTier;
  /** SCHEDULED when migration is queued; COMPLETED when synchronously finished */
  status: 'SCHEDULED' | 'COMPLETED';
  eventsMigrated: number;
}

/**
 * Triggers a manual storage-tier migration for the specified collection.
 *
 * Maps to: `POST /api/v1/collections/:id/migrate?targetTier=WARM|COLD`
 *
 * @param collectionId  The collection (stream name) to migrate
 * @param targetTier    Destination tier — WARM (L1→L2 Iceberg) or COLD (L2→L3 S3 archive)
 * @returns             Migration result with status and event count
 */
export async function migrateCollection(
  collectionId: string,
  targetTier: MigrationTargetTier,
): Promise<MigrateCollectionResult> {
  return apiClient.post<MigrateCollectionResult>(
    `/collections/${collectionId}/migrate`,
    {},
    { params: { targetTier } },
  );
}


