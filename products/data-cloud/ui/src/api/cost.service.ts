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
import { collectionsApi, type Collection } from '../lib/api/collections';
import type {
  CollectionCostReport,
  MigrateCollectionResult as SharedMigrateCollectionResult,
} from '../contracts/schemas';

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

async function getCollectionReports(): Promise<Array<{ collection: Collection; report: CollectionCostReport }>> {
  const collectionsPage = await collectionsApi.list({ pageSize: 50 });
  const reports = await Promise.all(
    collectionsPage.items.map(async (collection) => {
      try {
        const report = await apiClient.get<CollectionCostReport>(`/collections/${collection.id}/cost-report`);
        return { collection, report };
      } catch {
        return null;
      }
    }),
  );

  return reports.filter((entry): entry is { collection: Collection; report: CollectionCostReport } => entry !== null);
}

/**
 * Cost Service Client
 */
export class CostService {
  /**
   * Get cost breakdown analysis
   */
  async getCostAnalysis(period: string = '30d'): Promise<CostBreakdown> {
    const reports = await getCollectionReports();
    const total = reports.reduce((sum, entry) => sum + entry.report.totalCostDccPerDay, 0);

    return {
      total,
      currency: reports[0]?.report.currency ?? 'DCC',
      period,
      byDataset: reports.map(({ collection, report }) => ({
        datasetId: collection.id,
        datasetName: collection.name,
        cost: report.totalCostDccPerDay,
        percentage: total > 0 ? (report.totalCostDccPerDay / total) * 100 : 0,
      })),
      byQuery: [],
      byUser: total > 0 ? [{
        userId: 'system',
        userName: 'System workload',
        cost: total,
        queryCount: reports.length,
      }] : [],
    };
  }

  /**
   * Get optimization suggestions for a query
   */
  async getQueryOptimization(query: string): Promise<QueryOptimization> {
    return {
      queryId: 'estimate-only',
      originalQuery: query,
      suggestions: [],
      currentCost: 0,
      potentialCost: 0,
    };
  }

  /**
   * Apply optimization suggestion
   */
  async applyOptimization(
    queryId: string,
    suggestionType: string
  ): Promise<{ success: boolean; newQuery?: string }> {
    void queryId;
    void suggestionType;
    return { success: false };
  }

  /**
   * Predict query cost and latency
   */
  async predictQuery(query: string, tier?: string): Promise<QueryPrediction> {
    void query;
    return {
      estimatedCost: 0,
      estimatedLatency: 0,
      recommendedTier: (tier as QueryPrediction['recommendedTier'] | undefined) ?? 'WARM',
      confidence: 0,
      warnings: ['Predictive query routing is not exposed by the current Data Cloud API.'],
    };
  }

  /**
   * Get materialized view suggestions
   */
  async getMaterializedViewSuggestions(): Promise<MaterializedViewSuggestion[]> {
    return [];
  }

  /**
   * Create materialized view
   */
  async createMaterializedView(
    suggestion: MaterializedViewSuggestion
  ): Promise<{ id: string; status: string }> {
    void suggestion;
    return { id: 'unsupported', status: 'unsupported' };
  }

  /**
   * Get hotness metrics for datasets
   */
  async getHotnessMetrics(): Promise<HotnessMetric[]> {
    const costAnalysis = await this.getCostAnalysis('30d');
    return costAnalysis.byDataset.map((dataset) => ({
      datasetId: dataset.datasetId,
      tier: dataset.percentage > 50 ? 'HOT' : dataset.percentage > 20 ? 'WARM' : 'COLD',
      accessFrequency: Math.round(dataset.percentage),
      lastAccessed: new Date().toISOString(),
      predictedTier: dataset.percentage > 50 ? 'HOT' : dataset.percentage > 20 ? 'WARM' : 'COLD',
      recommendedAction: dataset.percentage < 20 ? 'Consider migrating to a colder tier.' : undefined,
    }));
  }

  /**
   * Update tier for a dataset
   */
  async updateDatasetTier(
    datasetId: string,
    tier: 'HOT' | 'WARM' | 'COLD'
  ): Promise<void> {
    if (tier === 'HOT') {
      throw new Error('Manual tier migration only supports WARM or COLD targets.');
    }
    await migrateCollection(datasetId, tier);
  }

  /**
   * Get cost forecast
   */
  async getCostForecast(
    days: number = 30
  ): Promise<{ forecast: Array<{ date: string; cost: number }> }> {
    const analysis = await this.getCostAnalysis('30d');
    const dailyCost = days > 0 ? analysis.total / days : analysis.total;
    return {
      forecast: Array.from({ length: days }, (_, index) => ({
        date: new Date(Date.now() + index * 24 * 60 * 60 * 1000).toISOString(),
        cost: dailyCost,
      })),
    };
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
export type MigrateCollectionResult = SharedMigrateCollectionResult;

/**
 * Triggers a manual storage-tier migration for the specified collection.
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
