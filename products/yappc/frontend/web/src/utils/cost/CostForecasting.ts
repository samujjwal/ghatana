/**
 * Cost Forecasting Utilities - Mathematical functions for cost prediction
 *
 * <p><b>Purpose</b><br>
 * Provides forecasting algorithms and statistical methods for cost prediction.
 * Implements multiple techniques: linear regression, exponential smoothing, seasonal decomposition.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const historicalCosts = [100, 102, 101, 103, 102, 104];
 * const forecast = linearRegression(historicalCosts, 3);
 * const smooth = exponentialSmoothing(historicalCosts, 0.3, 6);
 * }</pre>
 *
 * <p><b>Algorithms</b><br>
 * - Linear Regression: Simple trend extrapolation (best for stable trends)
 * - Exponential Smoothing: Weighted averaging with recency bias (best for responsive)
 * - Seasonal Decomposition: Trend + Seasonal + Residual (best for cyclic patterns)
 *
 * @doc.type module
 * @doc.purpose Cost forecasting and prediction utilities
 * @doc.layer product
 * @doc.pattern Utility
 */

/**
 * Linear regression result
 */
export interface LinearRegressionResult {
  /** Slope coefficient */
  slope: number;
  /** Intercept coefficient */
  intercept: number;
  /** R-squared value (0-1, higher is better fit) */
  rSquared: number;
  /** Predicted values at each point */
  predictedValues: number[];
}

/**
 * Forecast result with confidence intervals
 */
export interface ForecastResult {
  /** Forecasted values */
  forecast: number[];
  /** Lower bound of 95% confidence interval */
  confidenceLower95: number[];
  /** Upper bound of 95% confidence interval */
  confidenceUpper95: number[];
  /** Lower bound of 80% confidence interval */
  confidenceLower80: number[];
  /** Upper bound of 80% confidence interval */
  confidenceUpper80: number[];
}

/**
 * Seasonal decomposition result
 */
export interface SeasonalDecomposition {
  /** Trend component */
  trend: number[];
  /** Seasonal component */
  seasonal: number[];
  /** Residual component */
  residual: number[];
}

/**
 * Perform linear regression on cost data
 * Uses least squares method to fit y = mx + b
 *
 * @param costs Historical cost values
 * @param periods Number of periods to forecast
 * @returns Linear regression parameters and predicted values
 */
export function linearRegression(
  costs: ReadonlyArray<number>,
  periods: number
): LinearRegressionResult {
  if (costs.length < 2) {
    throw new Error('linearRegression: requires at least 2 data points');
  }
  if (periods < 1) {
    throw new Error('linearRegression: periods must be >= 1');
  }

  const n = costs.length;
  const xValues = Array.from({ length: n }, (_, i) => i);

  // Calculate sums for least squares formula
  const sumX = xValues.reduce((sum, x) => sum + x, 0);
  const sumY = costs.reduce((sum, y) => sum + y, 0);
  const sumXY = xValues.reduce(
    (sum, x, i) => sum + x * costs[i],
    0
  );
  const sumX2 = xValues.reduce((sum, x) => sum + x * x, 0);

  // Calculate slope and intercept
  const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
  const intercept = (sumY - slope * sumX) / n;

  // Calculate R-squared
  const meanY = sumY / n;
  const ssTotal = costs.reduce(
    (sum, y) => sum + Math.pow(y - meanY, 2),
    0
  );
  const predictedAtX = xValues.map(x => slope * x + intercept);
  const ssResidual = costs.reduce(
    (sum, y, i) => sum + Math.pow(y - predictedAtX[i], 2),
    0
  );
  const rSquared = ssTotal === 0 ? 1 : 1 - ssResidual / ssTotal;

  // Forecast future periods
  const forecast: number[] = [];
  for (let i = n; i < n + periods; i++) {
    forecast.push(slope * i + intercept);
  }

  return {
    slope,
    intercept,
    rSquared,
    predictedValues: predictedAtX,
  };
}

/**
 * Exponential smoothing for cost forecasting
 * Uses recursive weighted averaging with exponential decay
 *
 * @param costs Historical cost values
 * @param alpha Smoothing factor (0-1, higher = more weight on recent data)
 * @param periods Number of periods to forecast
 * @returns Exponentially smoothed forecast
 */
export function exponentialSmoothing(
  costs: ReadonlyArray<number>,
  alpha: number,
  periods: number
): number[] {
  if (costs.length === 0) {
    throw new Error('exponentialSmoothing: costs cannot be empty');
  }
  if (alpha < 0 || alpha > 1) {
    throw new Error('exponentialSmoothing: alpha must be between 0 and 1');
  }
  if (periods < 1) {
    throw new Error('exponentialSmoothing: periods must be >= 1');
  }

  // Initialize smoothed level with first value
  let level = costs[0];

  // Apply exponential smoothing to all historical data
  for (let i = 1; i < costs.length; i++) {
    level = alpha * costs[i] + (1 - alpha) * level;
  }

  // Forecast constant level for future periods
  const forecast: number[] = Array(periods).fill(level);

  return forecast;
}

/**
 * Double exponential smoothing (Holt's method) for trends
 * Captures both level and trend in data
 *
 * @param costs Historical cost values
 * @param alpha Level smoothing factor (0-1)
 * @param beta Trend smoothing factor (0-1)
 * @param periods Number of periods to forecast
 * @returns Forecast with trend component
 */
export function doubleExponentialSmoothing(
  costs: ReadonlyArray<number>,
  alpha: number = 0.3,
  beta: number = 0.1,
  periods: number
): number[] {
  if (costs.length < 2) {
    throw new Error('doubleExponentialSmoothing: requires at least 2 data points');
  }
  if (alpha < 0 || alpha > 1 || beta < 0 || beta > 1) {
    throw new Error('doubleExponentialSmoothing: alpha and beta must be between 0 and 1');
  }

  // Initialize level and trend
  let level = costs[0];
  let trend = costs[1] - costs[0];

  // Apply double exponential smoothing
  for (let i = 1; i < costs.length; i++) {
    const previousLevel = level;
    level = alpha * costs[i] + (1 - alpha) * (previousLevel + trend);
    trend = beta * (level - previousLevel) + (1 - beta) * trend;
  }

  // Forecast with trend
  const forecast: number[] = [];
  for (let i = 1; i <= periods; i++) {
    forecast.push(level + i * trend);
  }

  return forecast;
}

/**
 * Calculate seasonal indices (simple seasonal decomposition)
 * Assumes data follows: Actual = Trend * Seasonal
 *
 * @param costs Historical cost values
 * @param seasonLength Length of seasonal period (e.g., 12 for monthly data)
 * @returns Seasonal indices for each position
 */
export function calculateSeasonalIndices(
  costs: ReadonlyArray<number>,
  seasonLength: number
): number[] {
  if (costs.length < seasonLength) {
    throw new Error('calculateSeasonalIndices: costs length must be >= seasonLength');
  }
  if (seasonLength < 2) {
    throw new Error('calculateSeasonalIndices: seasonLength must be >= 2');
  }

  // Calculate average for each seasonal position
  const seasonalAverages = Array(seasonLength).fill(0);
  const seasonalCounts = Array(seasonLength).fill(0);

  for (let i = 0; i < costs.length; i++) {
    const seasonPosition = i % seasonLength;
    seasonalAverages[seasonPosition] += costs[i];
    seasonalCounts[seasonPosition] += 1;
  }

  // Average out each seasonal position
  const indices = seasonalAverages.map((sum, i) => sum / seasonalCounts[i]);

  // Normalize so average is 1
  const avgIndex = indices.reduce((sum, idx) => sum + idx, 0) / indices.length;

  return indices.map(idx => idx / avgIndex);
}

/**
 * Simple seasonal decomposition (additive model)
 * Decomposes time series into trend, seasonal, and residual components
 *
 * @param costs Historical cost values
 * @param seasonLength Length of seasonal period
 * @returns Decomposition components
 */
export function seasonalDecomposition(
  costs: ReadonlyArray<number>,
  seasonLength: number
): SeasonalDecomposition {
  if (costs.length < seasonLength) {
    throw new Error('seasonalDecomposition: costs length must be >= seasonLength');
  }

  // Calculate trend using centered moving average
  const trend: number[] = Array(costs.length).fill(0);
  const halfWindow = Math.floor(seasonLength / 2);

  for (let i = halfWindow; i < costs.length - halfWindow; i++) {
    let sum = 0;
    for (let j = -halfWindow; j <= halfWindow; j++) {
      sum += costs[i + j];
    }
    trend[i] = sum / (2 * halfWindow + 1);
  }

  // Fill edges with linear interpolation
  if (halfWindow > 0) {
    const firstTrendSlope =
      (trend[halfWindow] - trend[halfWindow + 1]) /
      (halfWindow + 1);
    for (let i = 0; i < halfWindow; i++) {
      trend[i] = trend[halfWindow] + firstTrendSlope * (i - halfWindow);
    }

    const lastIdx = costs.length - 1;
    const lastTrendIdx = lastIdx - halfWindow;
    const lastTrendSlope =
      (trend[lastTrendIdx] - trend[lastTrendIdx - 1]) /
      (halfWindow + 1);
    for (let i = lastTrendIdx + 1; i <= lastIdx; i++) {
      trend[i] = trend[lastTrendIdx] + lastTrendSlope * (i - lastTrendIdx);
    }
  }

  // Calculate seasonal component
  const seasonal: number[] = Array(costs.length).fill(0);
  const seasonalSums = Array(seasonLength).fill(0);
  const seasonalCounts = Array(seasonLength).fill(0);

  for (let i = 0; i < costs.length; i++) {
    const detrended = costs[i] - trend[i];
    const seasonPosition = i % seasonLength;
    seasonalSums[seasonPosition] += detrended;
    seasonalCounts[seasonPosition] += 1;
  }

  // Average seasonal component for each position
  for (let i = 0; i < costs.length; i++) {
    const seasonPosition = i % seasonLength;
    seasonal[i] = seasonalSums[seasonPosition] / seasonalCounts[seasonPosition];
  }

  // Calculate residual
  const residual = costs.map(
    (cost, i) => cost - trend[i] - seasonal[i]
  );

  return { trend, seasonal, residual };
}

/**
 * Generate forecast with confidence intervals using linear regression
 * @param costs Historical cost values
 * @param periods Number of periods to forecast
 * @returns Forecast with 80% and 95% confidence intervals
 */
export function generateForecastWithConfidence(
  costs: ReadonlyArray<number>,
  periods: number
): ForecastResult {
  if (costs.length < 2) {
    throw new Error('generateForecastWithConfidence: requires at least 2 data points');
  }

  const regression = linearRegression(costs, periods);
  const forecast = regression.predictedValues.slice(-periods);

  // Calculate standard error of regression
  const n = costs.length;
  const ssResidual = costs.reduce(
    (sum, y, i) => sum + Math.pow(y - regression.predictedValues[i], 2),
    0
  );
  const stdError = Math.sqrt(ssResidual / (n - 2));

  // Calculate prediction intervals
  const xValues = Array.from({ length: n }, (_, i) => i);
  const meanX = xValues.reduce((sum, x) => sum + x, 0) / n;
  const ssX = xValues.reduce((sum, x) => sum + Math.pow(x - meanX, 2), 0);

  // t-values for 80% (1.282) and 95% (1.960) confidence intervals
  const tValue95 = 1.96;
  const tValue80 = 1.282;

  const confidenceLower95: number[] = [];
  const confidenceUpper95: number[] = [];
  const confidenceLower80: number[] = [];
  const confidenceUpper80: number[] = [];

  for (let i = 0; i < periods; i++) {
    const xForecast = n + i;
    const stdDeviation = stdError * Math.sqrt(1 + 1 / n + Math.pow(xForecast - meanX, 2) / ssX);

    confidenceLower95.push(forecast[i] - tValue95 * stdDeviation);
    confidenceUpper95.push(forecast[i] + tValue95 * stdDeviation);
    confidenceLower80.push(forecast[i] - tValue80 * stdDeviation);
    confidenceUpper80.push(forecast[i] + tValue80 * stdDeviation);
  }

  return {
    forecast: forecast,
    confidenceLower95,
    confidenceUpper95,
    confidenceLower80,
    confidenceUpper80,
  };
}
