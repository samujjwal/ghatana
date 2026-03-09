/**
 * Cost & Query Optimization API Service
 *
 * Provides API client for cost analysis, query optimization, and predictive routing.
 *
 * @doc.type service
 * @doc.purpose Cost and query optimization API client
 * @doc.layer frontend
 */

import axios, { AxiosInstance } from 'axios';

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
   * Get cost breakdown analysis
   */
  async getCostAnalysis(period: string = '30d'): Promise<CostBreakdown> {
    const response = await this.client.get<CostBreakdown>('/cost/analysis', {
      params: { period },
    });
    return response.data;
  }

  /**
   * Get optimization suggestions for a query
   */
  async getQueryOptimization(query: string): Promise<QueryOptimization> {
    const response = await this.client.post<QueryOptimization>('/query/optimize', {
      query,
    });
    return response.data;
  }

  /**
   * Apply optimization suggestion
   */
  async applyOptimization(
    queryId: string,
    suggestionType: string
  ): Promise<{ success: boolean; newQuery?: string }> {
    const response = await this.client.post(
      `/query/optimize/${queryId}/apply`,
      { suggestionType }
    );
    return response.data;
  }

  /**
   * Predict query cost and latency
   */
  async predictQuery(query: string, tier?: string): Promise<QueryPrediction> {
    const response = await this.client.post<QueryPrediction>('/query/predict', {
      query,
      tier,
    });
    return response.data;
  }

  /**
   * Get materialized view suggestions
   */
  async getMaterializedViewSuggestions(): Promise<MaterializedViewSuggestion[]> {
    const response = await this.client.get<MaterializedViewSuggestion[]>(
      '/query/materialized-views/suggestions'
    );
    return response.data;
  }

  /**
   * Create materialized view
   */
  async createMaterializedView(
    suggestion: MaterializedViewSuggestion
  ): Promise<{ id: string; status: string }> {
    const response = await this.client.post('/query/materialized-views', suggestion);
    return response.data;
  }

  /**
   * Get hotness metrics for datasets
   */
  async getHotnessMetrics(): Promise<HotnessMetric[]> {
    const response = await this.client.get<HotnessMetric[]>('/cost/hotness');
    return response.data;
  }

  /**
   * Update tier for a dataset
   */
  async updateDatasetTier(
    datasetId: string,
    tier: 'HOT' | 'WARM' | 'COLD'
  ): Promise<void> {
    await this.client.put(`/cost/hotness/${datasetId}`, { tier });
  }

  /**
   * Get cost forecast
   */
  async getCostForecast(
    days: number = 30
  ): Promise<{ forecast: Array<{ date: string; cost: number }> }> {
    const response = await this.client.get('/cost/forecast', {
      params: { days },
    });
    return response.data;
  }
}

/**
 * Default cost service instance
 */
export const costService = new CostService();

export default costService;

