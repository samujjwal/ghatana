import type { UsageReport } from "./reports.service";

export interface UsageRecord {
  childId: string;
  childName?: string;
  deviceId: string;
  sessionType: "app" | "website";
  itemName: string;
  category?: string | null;
  durationSeconds: number;
  startedAt: Date;
}

export interface BlockRecord {
  childId: string;
  childName?: string;
  deviceId: string;
  blockedItem: string;
  category?: string | null;
  timestamp: Date;
}

export interface DailyUsagePoint {
  date: string;
  totalDuration: number;
}

export interface DailyBlockPoint {
  date: string;
  totalBlocks: number;
}

export interface TrendAnalysisResult {
  direction: "upward" | "downward" | "flat";
  percentageChange: number;
}

export interface TimeSeriesAnalysis {
  movingAverage: number[];
  volatility: number;
}

export interface CorrelationResult {
  coefficient: number;
  strength: "none" | "weak" | "moderate" | "strong";
}

export interface UsageRecommendation {
  childId: string;
  headline: string;
  detail: string;
  category: "screen_time" | "alerts" | "engagement";
}

/**
 * Calculate top apps by usage duration.
 *
 * <p><b>Purpose</b><br>
 * Aggregates usage records to identify most-used apps.
 * Used for dashboard app usage charts and reports.
 *
 * <p><b>Algorithm</b><br>
 * - Group by item_name (app name)
 * - Sum duration for each app
 * - Sort by total duration DESC
 * - Return top N results
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const topApps = calculateTopApps(usageRecords, 5);
 * // Returns: [{ name: "Instagram", duration: 7200 }, ...]
 * }</pre>
 *
 * @param records Usage/block records to analyze
 * @param limit Number of top apps to return (default: 5)
 * @return Top apps sorted by duration DESC
 * @see calculateTopCategories
 * @see identifyUsagePatterns
 * @doc.type function
 * @doc.purpose Aggregate top apps by usage duration
 * @doc.layer product
 * @doc.pattern Service
 */
export function calculateTopApps(records: UsageRecord[], limit: number = 5) {
  const totals = new Map<string, { name: string; duration: number }>();

  records.forEach((record) => {
    const existing = totals.get(record.itemName);
    if (existing) {
      existing.duration += record.durationSeconds;
    } else {
      totals.set(record.itemName, {
        name: record.itemName,
        duration: record.durationSeconds,
      });
    }
  });

  return Array.from(totals.values())
    .sort((a, b) => b.duration - a.duration)
    .slice(0, limit);
}

/**
 * Calculate top categories by usage duration.
 *
 * <p><b>Purpose</b><br>
 * Aggregates usage by category (e.g., "social", "games", "education").
 * Used for category-level usage analysis and restrictions.
 *
 * <p><b>Algorithm</b><br>
 * - Group by category field
 * - Handle null category as "uncategorized"
 * - Sum duration for each category
 * - Sort by total duration DESC
 * - Return top N results
 *
 * @param records Usage/block records to analyze
 * @param limit Number of top categories to return (default: 5)
 * @return Top categories sorted by duration DESC
 * @see calculateTopApps
 * @see identifyUsagePatterns
 * @doc.type function
 * @doc.purpose Aggregate top categories by usage duration
 * @doc.layer product
 * @doc.pattern Service
 */
export function calculateTopCategories(
  records: UsageRecord[],
  limit: number = 5
) {
  const totals = new Map<string, number>();

  records.forEach((record) => {
    const category = record.category || "uncategorized";
    totals.set(category, (totals.get(category) || 0) + record.durationSeconds);
  });

  return Array.from(totals.entries())
    .map(([category, duration]) => ({ category, duration }))
    .sort((a, b) => b.duration - a.duration)
    .slice(0, limit);
}

/**
 * Identify usage patterns from records.
 *
 * <p><b>Purpose</b><br>
 * Detects temporal usage patterns for behavioral insights.
 * Identifies peak usage hours, weekend vs weekday, late-night usage.
 *
 * <p><b>Metrics Calculated</b><br>
 * - Peak hours: Top 3 hours with highest usage (0-23)
 * - Weekend share: Percentage of usage on weekends
 * - Late night usage: Total usage 10pm-midnight (22-23)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const patterns = identifyUsagePatterns(records);
 * // Returns: { peakHours: [14, 20, 21], weekendShare: 0.35, lateNightUsage: 1200 }
 * }</pre>
 *
 * @param records Usage records to analyze (requires timestamp)
 * @return Usage pattern metrics
 * @see detectUsageTrends
 * @see detectAnomalies
 * @doc.type function
 * @doc.purpose Detect temporal usage patterns
 * @doc.layer product
 * @doc.pattern Service
 */
export function identifyUsagePatterns(records: UsageRecord[]) {
  const hourlyTotals = new Array<number>(24).fill(0);
  let weekdayDuration = 0;
  let weekendDuration = 0;

  records.forEach((record) => {
    const hour = record.startedAt.getUTCHours();
    hourlyTotals[hour] += record.durationSeconds;
    const day = record.startedAt.getUTCDay();
    if (day === 0 || day === 6) {
      weekendDuration += record.durationSeconds;
    } else {
      weekdayDuration += record.durationSeconds;
    }
  });

  const peakHours = hourlyTotals
    .map((duration, hour) => ({ hour, duration }))
    .sort((a, b) => b.duration - a.duration)
    .slice(0, 3);

  return {
    peakHours,
    weekendShare:
      weekendDuration === 0 && weekdayDuration === 0
        ? 0
        : weekendDuration / (weekdayDuration + weekendDuration),
    lateNightUsage: hourlyTotals
      .slice(22)
      .reduce((sum, value) => sum + value, 0),
  };
}

/**
 * Detect usage trends from time series data.
 *
 * <p><b>Purpose</b><br>
 * Analyzes whether usage is increasing, decreasing, or stable.
 * Used for trend alerts and recommendations.
 *
 * <p><b>Algorithm</b><br>
 * - Split data points into first half and second half
 * - Calculate average for each half
 * - Compare: (secondAvg - firstAvg) / firstAvg
 * - Classify: >5%: "upward", <-5%: "downward", else: "flat"
 *
 * @param points Daily usage time series data points
 * @return Trend direction and percentage change
 * @see identifyUsagePatterns
 * @see detectAnomalies
 * @doc.type function
 * @doc.purpose Detect increasing/decreasing usage trends
 * @doc.layer product
 * @doc.pattern Service
 */
export function detectUsageTrends(
  points: DailyUsagePoint[]
): TrendAnalysisResult {
  if (points.length < 2) {
    return { direction: "flat", percentageChange: 0 };
  }

  const midpoint = Math.floor(points.length / 2);
  const firstHalfAvg =
    points
      .slice(0, midpoint)
      .reduce((sum, point) => sum + point.totalDuration, 0) / midpoint;
  const secondHalfAvg =
    points
      .slice(midpoint)
      .reduce((sum, point) => sum + point.totalDuration, 0) /
    (points.length - midpoint);

  if (firstHalfAvg === 0) {
    return {
      direction: secondHalfAvg === 0 ? "flat" : "upward",
      percentageChange: secondHalfAvg === 0 ? 0 : 100,
    };
  }

  const change = ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100;

  return {
    direction: change > 5 ? "upward" : change < -5 ? "downward" : "flat",
    percentageChange: Number(change.toFixed(2)),
  };
}

/**
 * Detect anomalies using statistical z-score method.
 *
 * <p><b>Purpose</b><br>
 * Identifies outlier data points for alerting parents.
 * Detects unusual spikes or drops in usage.
 *
 * <p><b>Algorithm (Z-Score)</b><br>
 * - Calculate mean (μ) and standard deviation (σ)
 * - For each point: z = (value - μ) / σ
 * - Flag as anomaly if |z| >= threshold
 *
 * @param points Time series data points with values
 * @param threshold Z-score threshold (default: 2)
 * @return Array of anomalous points
 * @see detectUsageTrends
 * @see identifyUsagePatterns
 * @doc.type function
 * @doc.purpose Detect statistical outliers (z-score method)
 * @doc.layer product
 * @doc.pattern Service
 */
export function detectAnomalies(
  points: DailyUsagePoint[],
  threshold: number = 2
) {
  if (points.length === 0) return [];

  const values = points.map((point) => point.totalDuration);
  const mean = values.reduce((sum, value) => sum + value, 0) / values.length;
  const variance =
    values.reduce((sum, value) => sum + Math.pow(value - mean, 2), 0) /
    values.length;
  const stdDev = Math.sqrt(variance);

  if (stdDev === 0) {
    return [];
  }

  return points.filter(
    (point) => Math.abs((point.totalDuration - mean) / stdDev) >= threshold
  );
}

/**
 * Generate recommendations based on child reports.
 *
 * <p><b>Purpose</b><br>
 * Rule-based recommendation engine for parental guidance.
 * Analyzes usage patterns and suggests actions.
 *
 * <p><b>Rules Implemented</b><br>
 * - Long sessions (>2 hours avg): Suggest session time limits
 * - Social media spike (>1 hour): Alert about engagement
 * - No activity: Suggest connectivity check
 *
 * @param childReports Array of child usage reports
 * @return Array of recommendations
 * @see identifyUsagePatterns
 * @see detectUsageTrends
 * @doc.type function
 * @doc.purpose Generate rule-based usage recommendations
 * @doc.layer product
 * @doc.pattern Service
 */
export function generateRecommendations(
  childReports: Array<UsageReport & { alerts?: number }>
): UsageRecommendation[] {
  const recommendations: UsageRecommendation[] = [];

  childReports.forEach((report) => {
    const averageSession =
      report.session_count === 0
        ? 0
        : report.total_screen_time / report.session_count;

    if (averageSession > 7200) {
      recommendations.push({
        childId: report.child_id,
        headline: "Long session durations detected",
        detail: `${report.child_name} averages ${(
          averageSession / 3600
        ).toFixed(1)} hours per session. Consider setting session limits.`,
        category: "screen_time",
      });
    }

    const socialApp = report.top_apps.find((app) =>
      /insta|snap|tiktok|facebook/i.test(app.app_name)
    );
    if (socialApp && socialApp.duration > 3600) {
      recommendations.push({
        childId: report.child_id,
        headline: "Social media usage spike",
        detail: `${report.child_name} spent ${Math.round(
          socialApp.duration / 60
        )} minutes on ${socialApp.app_name}.`,
        category: "engagement",
      });
    }

    if (report.total_screen_time === 0 && (report as any).alerts === 0) {
      recommendations.push({
        childId: report.child_id,
        headline: "No activity detected",
        detail: `No screen time or alerts recorded for ${report.child_name}. Check connectivity.`,
        category: "alerts",
      });
    }
  });

  return recommendations;
}

/**
 * Analyze time series with moving average and volatility.
 *
 * <p><b>Purpose</b><br>
 * Smooths noisy data and measures variability.
 * Used for trend visualization and volatility alerts.
 *
 * <p><b>Moving Average (Simple)</b><br>
 * - Window size N (default: 3)
 * - For each point: avg(window[i-N+1 : i+1])
 *
 * <p><b>Volatility</b><br>
 * - Standard deviation of all values: σ = √(Σ(x - μ)² / n)
 *
 * @param points Time series data points
 * @param windowSize Moving average window size (default: 3)
 * @return Moving average array and volatility metric
 * @see detectUsageTrends
 * @see detectAnomalies
 * @doc.type function
 * @doc.purpose Calculate moving average and volatility
 * @doc.layer product
 * @doc.pattern Service
 */
export function analyzeTimeSeries(
  points: DailyUsagePoint[],
  windowSize: number = 3
): TimeSeriesAnalysis {
  if (windowSize <= 0) {
    throw new Error("windowSize must be greater than zero");
  }
  if (points.length === 0) {
    return { movingAverage: [], volatility: 0 };
  }

  const movingAverage: number[] = [];
  for (let i = 0; i < points.length; i++) {
    const windowStart = Math.max(0, i - windowSize + 1);
    const window = points.slice(windowStart, i + 1);
    const avg =
      window.reduce((sum, point) => sum + point.totalDuration, 0) /
      window.length;
    movingAverage.push(Number(avg.toFixed(2)));
  }

  const mean =
    points.reduce((sum, point) => sum + point.totalDuration, 0) / points.length;
  const variance =
    points.reduce(
      (sum, point) => sum + Math.pow(point.totalDuration - mean, 2),
      0
    ) / points.length;

  return {
    movingAverage,
    volatility: Number(Math.sqrt(variance).toFixed(2)),
  };
}

/**
 * Analyze correlation between usage and blocks.
 *
 * <p><b>Purpose</b><br>
 * Measures relationship between screen time and blocks.
 * Helps identify if blocks correlate with usage patterns.
 *
 * <p><b>Algorithm (Pearson Correlation)</b><br>
 * - Align usage and block series by date
 * - Calculate: r = Σ((x - μx)(y - μy)) / (σx · σy · n)
 * - Result: -1 (inverse) to +1 (direct)
 *
 * <p><b>Strength Classification</b><br>
 * - |r| < 0.2: "none", |r| < 0.4: "weak", |r| < 0.6: "moderate", |r| >= 0.6: "strong"
 *
 * @param usageSeries Time series of usage data
 * @param blockSeries Time series of block data
 * @return Correlation coefficient and strength
 * @see analyzeTimeSeries
 * @see detectUsageTrends
 * @doc.type function
 * @doc.purpose Calculate Pearson correlation coefficient
 * @doc.layer product
 * @doc.pattern Service
 */
export function analyzeCorrelation(
  usageSeries: DailyUsagePoint[],
  blockSeries: DailyBlockPoint[]
): CorrelationResult {
  if (usageSeries.length === 0 || blockSeries.length === 0) {
    return { coefficient: 0, strength: "none" };
  }

  const indexedBlocks = new Map(
    blockSeries.map((point) => [point.date, point.totalBlocks])
  );
  const aligned = usageSeries
    .map((point) => {
      const blocks = indexedBlocks.get(point.date);
      if (blocks === undefined) return null;
      return { usage: point.totalDuration, blocks };
    })
    .filter(
      (value): value is { usage: number; blocks: number } => value !== null
    );

  if (aligned.length < 2) {
    return { coefficient: 0, strength: "none" };
  }

  const usageValues = aligned.map((value) => value.usage);
  const blockValues = aligned.map((value) => value.blocks);
  const usageMean =
    usageValues.reduce((sum, value) => sum + value, 0) / usageValues.length;
  const blockMean =
    blockValues.reduce((sum, value) => sum + value, 0) / blockValues.length;

  const numerator = aligned.reduce((sum, value) => {
    return sum + (value.usage - usageMean) * (value.blocks - blockMean);
  }, 0);
  const usageVariance = Math.sqrt(
    aligned.reduce(
      (sum, value) => sum + Math.pow(value.usage - usageMean, 2),
      0
    )
  );
  const blockVariance = Math.sqrt(
    aligned.reduce(
      (sum, value) => sum + Math.pow(value.blocks - blockMean, 2),
      0
    )
  );

  if (usageVariance === 0 || blockVariance === 0) {
    return { coefficient: 0, strength: "none" };
  }

  const coefficient = Number(
    (numerator / (usageVariance * blockVariance)).toFixed(3)
  );
  const absolute = Math.abs(coefficient);
  const strength =
    absolute < 0.2
      ? "none"
      : absolute < 0.4
        ? "weak"
        : absolute < 0.6
          ? "moderate"
          : "strong";

  return { coefficient, strength };
}
