/**
 * Cost Calculation Utilities - Pure functions for cost analysis
 *
 * <p><b>Purpose</b><br>
 * Reusable mathematical functions for cost calculations.
 * All functions are pure (no side effects) for testability and composability.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const costs = [100, 102, 101, 500, 103, 102];
 * const total = calculateTotalCost(costs);
 * const avg = calculateAverageCost(costs);
 * const stdDev = calculateStandardDeviation(costs);
 * }</pre>
 *
 * <p><b>Performance</b><br>
 * All functions are O(n) or better (single pass through data).
 * Suitable for real-time calculations on large datasets.
 *
 * @doc.type module
 * @doc.purpose Cost calculation utility functions
 * @doc.layer product
 * @doc.pattern Utility
 */

/**
 * Calculate total cost from array of costs
 * @param costs Array of cost values
 * @returns Sum of all costs
 * @throws Error if costs array is empty
 */
export function calculateTotalCost(costs: ReadonlyArray<number>): number {
  if (costs.length === 0) {
    throw new Error('calculateTotalCost: costs array cannot be empty');
  }
  return costs.reduce((sum, cost) => sum + cost, 0);
}

/**
 * Calculate average cost
 * @param costs Array of cost values
 * @returns Mean of cost values
 */
export function calculateAverageCost(costs: ReadonlyArray<number>): number {
  if (costs.length === 0) {
    throw new Error('calculateAverageCost: costs array cannot be empty');
  }
  return calculateTotalCost(costs) / costs.length;
}

/**
 * Calculate minimum cost
 * @param costs Array of cost values
 * @returns Smallest cost value
 */
export function calculateMinCost(costs: ReadonlyArray<number>): number {
  if (costs.length === 0) {
    throw new Error('calculateMinCost: costs array cannot be empty');
  }
  return Math.min(...costs);
}

/**
 * Calculate maximum cost
 * @param costs Array of cost values
 * @returns Largest cost value
 */
export function calculateMaxCost(costs: ReadonlyArray<number>): number {
  if (costs.length === 0) {
    throw new Error('calculateMaxCost: costs array cannot be empty');
  }
  return Math.max(...costs);
}

/**
 * Calculate standard deviation of costs
 * @param costs Array of cost values
 * @returns Standard deviation (population std dev)
 */
export function calculateStandardDeviation(costs: ReadonlyArray<number>): number {
  if (costs.length === 0) {
    throw new Error('calculateStandardDeviation: costs array cannot be empty');
  }

  const mean = calculateAverageCost(costs);
  const squaredDiffs = costs.map(cost => Math.pow(cost - mean, 2));
  const variance = squaredDiffs.reduce((sum, diff) => sum + diff, 0) / costs.length;

  return Math.sqrt(variance);
}

/**
 * Calculate variance of costs
 * @param costs Array of cost values
 * @returns Variance (population variance)
 */
export function calculateVariance(costs: ReadonlyArray<number>): number {
  if (costs.length === 0) {
    throw new Error('calculateVariance: costs array cannot be empty');
  }

  const mean = calculateAverageCost(costs);
  const squaredDiffs = costs.map(cost => Math.pow(cost - mean, 2));

  return squaredDiffs.reduce((sum, diff) => sum + diff, 0) / costs.length;
}

/**
 * Calculate percentile value
 * @param costs Array of cost values
 * @param percentile Percentile to calculate (0-100)
 * @returns Value at specified percentile
 */
export function calculatePercentile(
  costs: ReadonlyArray<number>,
  percentile: number
): number {
  if (costs.length === 0) {
    throw new Error('calculatePercentile: costs array cannot be empty');
  }
  if (percentile < 0 || percentile > 100) {
    throw new Error('calculatePercentile: percentile must be between 0 and 100');
  }

  const sorted = [...costs].sort((a, b) => a - b);
  const index = (percentile / 100) * (sorted.length - 1);
  const lower = Math.floor(index);
  const upper = Math.ceil(index);
  const weight = index % 1;

  if (lower === upper) {
    return sorted[lower];
  }

  return sorted[lower] * (1 - weight) + sorted[upper] * weight;
}

/**
 * Calculate cost change (absolute and percentage)
 * @param previousCost Previous period cost
 * @param currentCost Current period cost
 * @returns Object with absolute change and percentage change
 */
export function calculateCostChange(
  previousCost: number,
  currentCost: number
): { absoluteChange: number; percentageChange: number } {
  const absoluteChange = currentCost - previousCost;
  const percentageChange =
    previousCost === 0 ? 0 : (absoluteChange / previousCost) * 100;

  return {
    absoluteChange,
    percentageChange,
  };
}

/**
 * Calculate outliers (costs outside n standard deviations)
 * @param costs Array of cost values
 * @param stdDevMultiplier Number of std devs to consider as outlier (default: 2)
 * @returns Indices of outlier values
 */
export function findOutliers(
  costs: ReadonlyArray<number>,
  stdDevMultiplier: number = 2
): number[] {
  if (costs.length === 0) {
    return [];
  }

  const mean = calculateAverageCost(costs);
  const stdDev = calculateStandardDeviation(costs);
  const threshold = stdDevMultiplier * stdDev;

  return costs
    .map((cost, index) => ({ cost, index }))
    .filter(({ cost }) => Math.abs(cost - mean) > threshold)
    .map(({ index }) => index);
}

/**
 * Calculate moving average
 * @param costs Array of cost values
 * @param windowSize Window size for moving average
 * @returns Array of moving average values
 */
export function calculateMovingAverage(
  costs: ReadonlyArray<number>,
  windowSize: number
): number[] {
  if (windowSize < 1 || windowSize > costs.length) {
    throw new Error('calculateMovingAverage: windowSize must be >= 1 and <= costs length');
  }

  const result: number[] = [];
  for (let i = 0; i <= costs.length - windowSize; i++) {
    const window = costs.slice(i, i + windowSize);
    result.push(calculateAverageCost(window));
  }

  return result;
}

/**
 * Calculate cost breakdown percentages
 * @param costByCategory Map of category to cost
 * @returns Map of category to percentage (0-100)
 */
export function calculatePercentages(
  costByCategory: Record<string, number>
): Record<string, number> {
  const total = Object.values(costByCategory).reduce((sum, cost) => sum + cost, 0);

  if (total === 0) {
    // Return equal percentages if total is 0
    const categories = Object.keys(costByCategory);
    const result: Record<string, number> = {};
    for (const category of categories) {
      result[category] = 100 / categories.length;
    }
    return result;
  }

  const result: Record<string, number> = {};
  for (const [category, cost] of Object.entries(costByCategory)) {
    result[category] = (cost / total) * 100;
  }

  return result;
}
