import type { UsageRecord, BlockRecord, UsageRecommendation } from "./analytics.service";

/**
 * Service for generating usage recommendations based on patterns.
 *
 * <p><b>Purpose</b><br>
 * Analyzes child behavior and generates actionable recommendations
 * for parents (time limits, category restrictions, behavior interventions).
 *
 * <p><b>Recommendation Types</b><br>
 * - screen_time: Suggestions for usage limits (low/medium/high concern)
 * - alerts: Behavioral red flags (late night, banned apps, anomalies)
 * - engagement: Suggestions for alternative activities
 *
 * <p><b>ML-Ready Architecture</b><br>
 * RecommendationService uses pluggable recommendation engines.
 * Phase 3 can add ML-based recommendations without changing interface.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const service = new RecommendationService();
 * const recs = await service.generateForChild('child-123', usageRecords, blockRecords);
 * // Returns: [
 * //   { headline: "High screen time detected", category: "screen_time" },
 * //   { headline: "Late night usage detected", category: "alerts" }
 * // ]
 * }</pre>
 *
 * @see AnalyticsService
 * @see ReportsService
 * @doc.type class
 * @doc.purpose Generate usage recommendations
 * @doc.layer product
 * @doc.pattern Service
 */
export class RecommendationService {
  constructor(private config: RecommendationConfig = defaultConfig) {}

  /**
   * Generate recommendations for a child based on usage patterns.
   *
   * <p><b>Process</b><br>
   * 1. Calculate screen time metrics
   * 2. Identify behavioral anomalies
   * 3. Check category balance
   * 4. Generate prioritized recommendations
   *
   * @param childId Child to analyze
   * @param usageRecords Usage data for analysis window
   * @param blockRecords Block data for analysis window
   * @return Array of recommendations ordered by urgency
   * @throws Error if childId is empty
   */
  generateForChild(
    childId: string,
    usageRecords: UsageRecord[],
    blockRecords: BlockRecord[]
  ): UsageRecommendation[] {
    if (!childId) {
      throw new Error("childId is required");
    }

    const recommendations: UsageRecommendation[] = [];

    // Screen time recommendations
    const screenTimeRecs = this.recommendScreenTime(childId, usageRecords);
    recommendations.push(...screenTimeRecs);

    // Alert-based recommendations
    const alertRecs = this.recommendAlerts(childId, usageRecords, blockRecords);
    recommendations.push(...alertRecs);

    // Engagement recommendations
    const engagementRecs = this.recommendEngagement(childId, usageRecords);
    recommendations.push(...engagementRecs);

    return recommendations;
  }

  /**
   * Generate screen time recommendations.
   *
   * <p><b>Logic</b><br>
   * - High: >5 hours/day (35+ hours/week)
   * - Medium: 3-5 hours/day (21-35 hours/week)
   * - Healthy: <3 hours/day (<21 hours/week)
   *
   * @param childId Child ID
   * @param records Usage records
   * @return Array of screen time recommendations
   */
  private recommendScreenTime(
    childId: string,
    records: UsageRecord[]
  ): UsageRecommendation[] {
    const recommendations: UsageRecommendation[] = [];

    // Calculate total hours
    const totalSeconds = records.reduce((sum, r) => sum + r.durationSeconds, 0);
    const totalHours = totalSeconds / 3600;
    const dailyAverage = totalHours / 7; // Assuming 7-day window

    if (dailyAverage > this.config.highScreenTimeThreshold) {
      recommendations.push({
        childId,
        headline: "⚠️ Very high screen time detected",
        detail: `${dailyAverage.toFixed(1)} hours/day on average. Consider setting daily time limits to reduce strain.`,
        category: "screen_time",
      });
    } else if (dailyAverage > this.config.mediumScreenTimeThreshold) {
      recommendations.push({
        childId,
        headline: "📱 Screen time is elevated",
        detail: `${dailyAverage.toFixed(1)} hours/day on average. Monitor for balance with offline activities.`,
        category: "screen_time",
      });
    } else {
      recommendations.push({
        childId,
        headline: "✅ Healthy screen time habits",
        detail: `${dailyAverage.toFixed(1)} hours/day on average. Keep up the good balance!`,
        category: "screen_time",
      });
    }

    return recommendations;
  }

  /**
   * Generate behavioral alert recommendations.
   *
   * <p><b>Alerts Detected</b><br>
   * - Late night usage (between lateNightStartHour and 6 AM)
   * - High block rate (>20% of attempts blocked)
   * - Multiple app switches (engagement metric)
   *
   * @param childId Child ID
   * @param usageRecords Usage records
   * @param blockRecords Block records
   * @return Array of alert recommendations
   */
  private recommendAlerts(
    childId: string,
    usageRecords: UsageRecord[],
    blockRecords: BlockRecord[]
  ): UsageRecommendation[] {
    const recommendations: UsageRecommendation[] = [];

    // Check late night usage
    const lateNightRecords = usageRecords.filter((r) => {
      const hour = r.startedAt.getUTCHours();
      return hour >= this.config.lateNightStartHour || hour < 6;
    });

    if (lateNightRecords.length > 0) {
      const lateNightDuration = lateNightRecords.reduce(
        (sum, r) => sum + r.durationSeconds,
        0
      );
      recommendations.push({
        childId,
        headline: "🌙 Late night activity detected",
        detail: `${lateNightRecords.length} sessions after ${this.config.lateNightStartHour} PM (${(lateNightDuration / 60).toFixed(0)} minutes total). Consider setting bedtime limits.`,
        category: "alerts",
      });
    }

    // Check high block rate
    const totalAttempts = usageRecords.length + blockRecords.length;
    const blockRate = totalAttempts > 0 ? blockRecords.length / totalAttempts : 0;

    if (blockRate > this.config.blockRateThreshold) {
      recommendations.push({
        childId,
        headline: "🚫 High block rate",
        detail: `${(blockRate * 100).toFixed(0)}% of activity attempts are being blocked. Review current policies to ensure they're appropriate.`,
        category: "alerts",
      });
    }

    // Check rapid app switching (indicator of distraction or evasion)
    if (usageRecords.length > 10) {
      const uniqueApps = new Set(usageRecords.map((r) => r.itemName)).size;
      const switchRate = uniqueApps / (usageRecords.length / 2); // Sessions to app ratio

      if (switchRate > 0.8) {
        recommendations.push({
          childId,
          headline: "🔄 High app switching",
          detail: `Switching between ${uniqueApps} apps frequently. May indicate distraction or evasion of restrictions.`,
          category: "alerts",
        });
      }
    }

    return recommendations;
  }

  /**
   * Generate engagement recommendations.
   *
   * <p><b>Logic</b><br>
   * - Check education/productivity balance
   * - Suggest alternative content
   * - Encourage beneficial apps
   *
   * @param childId Child ID
   * @param records Usage records
   * @return Array of engagement recommendations
   */
  private recommendEngagement(
    childId: string,
    records: UsageRecord[]
  ): UsageRecommendation[] {
    const recommendations: UsageRecommendation[] = [];

    if (records.length === 0) return recommendations;

    // Check education balance
    const educationRecords = records.filter(
      (r) =>
        r.category === "education" ||
        r.category === "productivity" ||
        r.category === "learning"
    );
    const educationShare = educationRecords.length / records.length;

    if (educationShare < this.config.educationMinimumShare) {
      recommendations.push({
        childId,
        headline: "📚 Limited educational content",
        detail: `Only ${(educationShare * 100).toFixed(0)}% of screen time is educational. Encourage learning-focused apps like Khan Academy, Duolingo, or educational YouTube channels.`,
        category: "engagement",
      });
    }

    // Check for social media dominance
    const socialMediaRecords = records.filter(
      (r) => r.category === "social" || r.category === "social_media"
    );
    const socialShare = socialMediaRecords.length / records.length;

    if (socialShare > 0.4) {
      recommendations.push({
        childId,
        headline: "👥 High social media usage",
        detail: `${(socialShare * 100).toFixed(0)}% of screen time is social media. Consider introducing hobby apps or creative tools as alternatives.`,
        category: "engagement",
      });
    }

    return recommendations;
  }

  /**
   * Get recommendation summary for parent notification.
   *
   * <p><b>Purpose</b><br>
   * Generates a concise summary for quick parent review.
   * Prioritizes by severity.
   *
   * @param recommendations Full recommendation list
   * @param limit Max recommendations to include (default: 3)
   * @return Top N recommendations
   */
  getSummary(
    recommendations: UsageRecommendation[],
    limit: number = 3
  ): UsageRecommendation[] {
    // Prioritize by category (alerts > screen_time > engagement)
    const priority: Record<string, number> = {
      alerts: 0,
      screen_time: 1,
      engagement: 2,
    };

    return recommendations
      .sort(
        (a, b) =>
          (priority[a.category] || 999) - (priority[b.category] || 999)
      )
      .slice(0, limit);
  }
}

/**
 * Configuration for recommendation generation.
 *
 * @doc.type interface
 * @doc.purpose Recommendation thresholds configuration
 * @doc.layer product
 * @doc.pattern Configuration
 */
export interface RecommendationConfig {
  /** High screen time threshold in hours/day (default: 5) */
  highScreenTimeThreshold: number;

  /** Medium screen time threshold in hours/day (default: 3) */
  mediumScreenTimeThreshold: number;

  /** Start hour for late night detection (default: 22 = 10 PM) */
  lateNightStartHour: number;

  /** Block rate threshold before alert (default: 0.2 = 20%) */
  blockRateThreshold: number;

  /** Minimum education/productivity share (default: 0.1 = 10%) */
  educationMinimumShare: number;
}

/**
 * Default configuration for recommendations.
 *
 * @doc.type constant
 * @doc.purpose Default recommendation configuration
 * @doc.layer product
 */
export const defaultConfig: RecommendationConfig = {
  highScreenTimeThreshold: 5, // 5+ hours/day = high
  mediumScreenTimeThreshold: 3, // 3-5 hours/day = medium
  lateNightStartHour: 22, // 10 PM
  blockRateThreshold: 0.2, // 20%+
  educationMinimumShare: 0.1, // 10%+
};
