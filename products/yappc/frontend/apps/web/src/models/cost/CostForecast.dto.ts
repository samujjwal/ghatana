/**
 * CostForecast DTO - Data transfer object for cost forecasts and projections
 *
 * <p><b>Purpose</b><br>
 * Encapsulates predicted cost data for future periods.
 * Used for budget planning and cost projection visualization.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const forecast = await costForecastingService.forecast({
 *   historicalMonths: 12,
 *   projectionMonths: 6
 * });
 * // Returns CostForecast with projected costs for next 6 months
 * }</pre>
 *
 * <p><b>Confidence Intervals</b><br>
 * Forecasts include confidence intervals (80%, 95%) for risk assessment.
 * Used to plan for cost uncertainty.
 *
 * @doc.type interface
 * @doc.purpose Cost forecast data transfer object
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CostForecast {
  /**
   * Forecast period (start and end dates)
   */
  readonly period: {
    readonly start: Date;
    readonly end: Date;
  };

  /**
   * Projected total cost for the period
   * Base estimate (50th percentile)
   */
  readonly projectedCost: number;

  /**
   * Currency code (USD, EUR, etc.)
   */
  readonly currency: string;

  /**
   * Confidence level of projection (0-1)
   * Higher = more confident in prediction
   * Depends on data quality and historical variability
   */
  readonly confidence: number;

  /**
   * Confidence interval lower bound
   * Cost likely won't go below this value (80% confidence)
   */
  readonly confidenceLower80: number;

  /**
   * Confidence interval upper bound
   * Cost likely won't exceed this value (80% confidence)
   */
  readonly confidenceUpper80: number;

  /**
   * Confidence interval lower bound (95% confidence)
   */
  readonly confidenceLower95: number;

  /**
   * Confidence interval upper bound (95% confidence)
   */
  readonly confidenceUpper95: number;

  /**
   * Monthly cost projections
   * Array of projected costs for each month in forecast period
   */
  readonly monthlyProjections: ReadonlyArray<{
    readonly date: Date;
    readonly projectedCost: number;
    readonly confidence: number;
  }>;

  /**
   * Factors influencing the forecast
   * Human-readable list of factors affecting the projection
   */
  readonly factors: ReadonlyArray<string>;

  /**
   * Growth rate (year-over-year percentage)
   * Positive = increasing costs
   * Negative = decreasing costs
   */
  readonly growthRate: number;

  /**
   * Seasonality pattern
   * Indicates seasonal variation in costs
   * e.g., "High in Q4 due to holiday traffic"
   */
  readonly seasonality?: string;

  /**
   * Key risks to the forecast
   * List of scenarios that could impact prediction
   */
  readonly risks: ReadonlyArray<string>;

  /**
   * Forecast generation timestamp
   */
  readonly forecastedAt: Date;
}

/**
 * Creates new CostForecast DTO
 * @param data Partial forecast data
 * @returns Complete CostForecast object
 */
export function createCostForecast(data: Partial<CostForecast>): CostForecast {
  if (!data.period) {
    throw new Error('CostForecast: period is required');
  }
  if (data.projectedCost === undefined || data.projectedCost < 0) {
    throw new Error('CostForecast: projectedCost must be non-negative');
  }
  if (data.confidence === undefined || data.confidence < 0 || data.confidence > 1) {
    throw new Error('CostForecast: confidence must be between 0 and 1');
  }

  return {
    period: data.period,
    projectedCost: data.projectedCost,
    currency: data.currency || 'USD',
    confidence: data.confidence,
    confidenceLower80: data.confidenceLower80 || data.projectedCost * 0.9,
    confidenceUpper80: data.confidenceUpper80 || data.projectedCost * 1.1,
    confidenceLower95: data.confidenceLower95 || data.projectedCost * 0.8,
    confidenceUpper95: data.confidenceUpper95 || data.projectedCost * 1.2,
    monthlyProjections: data.monthlyProjections || [],
    factors: data.factors || [],
    growthRate: data.growthRate || 0,
    seasonality: data.seasonality,
    risks: data.risks || [],
    forecastedAt: data.forecastedAt || new Date(),
  };
}
