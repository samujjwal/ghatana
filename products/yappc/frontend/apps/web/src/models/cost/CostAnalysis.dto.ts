/**
 * CostAnalysis DTO - Data transfer object for cost analysis results
 *
 * <p><b>Purpose</b><br>
 * Encapsulates aggregated cost analysis results for transfer to GraphQL layer.
 * Contains period data, costs by dimension, trends, and anomalies.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const analysis = await costAnalysisService.analyze({
 *   provider: 'AWS',
 *   period: { start: new Date('2025-01-01'), end: new Date('2025-01-31') }
 * });
 * // Returns CostAnalysis with aggregated data
 * }</pre>
 *
 * <p><b>Immutability</b><br>
 * This DTO is immutable after construction. Use readonly fields.
 *
 * @doc.type interface
 * @doc.purpose Cost analysis data transfer object
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CostAnalysis {
  /**
   * Analysis period (start and end dates)
   */
  readonly period: {
    readonly start: Date;
    readonly end: Date;
  };

  /**
   * Total cost for the period
   * Sum of all costs in the period
   */
  readonly totalCost: number;

  /**
   * Currency code
   * Default: USD
   */
  readonly currency: string;

  /**
   * Cost breakdown by service
   * Key: service name, Value: cost amount
   * Useful for identifying expensive services
   */
  readonly costByService: Record<string, number>;

  /**
   * Cost breakdown by provider
   * Key: provider name, Value: cost amount
   * Useful for multi-cloud cost tracking
   */
  readonly costByProvider: Record<string, number>;

  /**
   * Cost breakdown by tag
   * Key: tag value, Value: cost amount
   * Useful for cost center allocation
   */
  readonly costByTag: Record<string, Record<string, number>>;

  /**
   * Daily cost trend for visualization
   * Array of { date: Date, cost: number } tuples
   * Used for trend charts
   */
  readonly dailyTrend: ReadonlyArray<{
    readonly date: Date;
    readonly cost: number;
  }>;

  /**
   * Cost anomalies detected in period
   * Array of anomaly records with dates and severity
   */
  readonly anomalies: ReadonlyArray<{
    readonly date: Date;
    readonly cost: number;
    readonly expectedCost: number;
    readonly deviation: number;
    readonly severity: 'LOW' | 'MEDIUM' | 'HIGH';
  }>;

  /**
   * Cost metrics and statistics
   */
  readonly metrics: {
    readonly averageDailyCost: number;
    readonly maxDailyCost: number;
    readonly minDailyCost: number;
    readonly standardDeviation: number;
  };

  /**
   * Analysis timestamp
   * When this analysis was generated
   */
  readonly analyzedAt: Date;
}

/**
 * Creates new CostAnalysis DTO
 * @param data Partial cost analysis data
 * @returns Complete CostAnalysis object
 */
export function createCostAnalysis(data: Partial<CostAnalysis>): CostAnalysis {
  if (!data.period) {
    throw new Error('CostAnalysis: period is required');
  }
  if (data.totalCost === undefined || data.totalCost < 0) {
    throw new Error('CostAnalysis: totalCost must be non-negative');
  }

  return {
    period: data.period,
    totalCost: data.totalCost,
    currency: data.currency || 'USD',
    costByService: data.costByService || {},
    costByProvider: data.costByProvider || {},
    costByTag: data.costByTag || {},
    dailyTrend: data.dailyTrend || [],
    anomalies: data.anomalies || [],
    metrics: data.metrics || {
      averageDailyCost: 0,
      maxDailyCost: 0,
      minDailyCost: 0,
      standardDeviation: 0,
    },
    analyzedAt: data.analyzedAt || new Date(),
  };
}
