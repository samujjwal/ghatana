/**
 * Content Quality Regression Detection Service
 *
 * Post-publish monitoring that flags AI-generated content that degrades against quality baseline.
 *
 * @doc.type service
 * @doc.purpose Monitor content quality regression in published AI-generated content
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

export interface QualityBaseline {
  contentId: string;
  contentType: string;
  baselineMetrics: {
    clarity: number;
    accuracy: number;
    completeness: number;
    engagement: number;
  };
  establishedAt: Date;
}

export interface QualityAlert {
  id: string;
  contentId: string;
  contentType: string;
  metricType: "clarity" | "accuracy" | "completeness" | "engagement";
  baselineValue: number;
  currentValue: number;
  degradation: number;
  severity: "low" | "medium" | "high" | "critical";
  detectedAt: Date;
  resolved: boolean;
  resolvedAt: Date | null;
}

export class ContentQualityMonitoringService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Establish quality baseline for a content item
   */
  async establishBaseline(
    contentId: string,
    contentType: string,
    metrics: QualityBaseline["baselineMetrics"],
  ): Promise<QualityBaseline> {
    const baseline = await this.prisma.qualityBaseline.upsert({
      where: { contentId },
      create: {
        contentId,
        contentType,
        baselineMetrics: metrics as any,
        establishedAt: new Date(),
      },
      update: {
        baselineMetrics: metrics as any,
        establishedAt: new Date(),
      },
    });

    return {
      contentId: baseline.contentId,
      contentType: baseline.contentType,
      baselineMetrics: metrics,
      establishedAt: baseline.establishedAt,
    };
  }

  /**
   * Monitor content quality against baseline
   */
  async monitorContentQuality(contentId: string): Promise<QualityAlert[]> {
    const baseline = await this.prisma.qualityBaseline.findUnique({
      where: { contentId },
    });

    if (!baseline) {
      throw new Error(`No baseline established for content ${contentId}`);
    }

    const currentMetrics = await this.calculateCurrentMetrics(contentId);
    const baselineMetrics = JSON.parse(baseline.baselineMetrics as string) as QualityBaseline["baselineMetrics"];
    const alerts: QualityAlert[] = [];

    for (const metricType of ["clarity", "accuracy", "completeness", "engagement"] as const) {
      const baselineValue = baselineMetrics[metricType];
      const currentValue = currentMetrics[metricType];
      const degradation = baselineValue - currentValue;

      // Alert if degradation exceeds threshold (10%)
      if (degradation > 0.1) {
        const severity = this.calculateSeverity(degradation);
        const alert = await this.createAlert(
          contentId,
          baseline.contentType,
          metricType,
          baselineValue,
          currentValue,
          degradation,
          severity,
        );
        alerts.push(alert);
      }
    }

    return alerts;
  }

  /**
   * Calculate current quality metrics for content
   */
  private async calculateCurrentMetrics(contentId: string): Promise<{
    clarity: number;
    accuracy: number;
    completeness: number;
    engagement: number;
  }> {
    // Placeholder implementation - in a real system, this would fetch the content
    // and calculate actual metrics based on the content model
    // For now, return placeholder values
    return {
      clarity: 0.8,
      accuracy: 0.75,
      completeness: 0.7,
      engagement: 0.65,
    };
  }

  /**
   * Calculate clarity score
   */
  private calculateClarity(text: string): number {
    const sentences = text.split(/[.!?]+/).filter((s) => s.trim().length > 0);
    if (sentences.length === 0) return 0;

    let clarityScore = 0.5;

    // Prefer shorter sentences (easier to understand)
    const avgSentenceLength = text.length / sentences.length;
    if (avgSentenceLength < 50) clarityScore += 0.2;
    else if (avgSentenceLength < 100) clarityScore += 0.1;

    // Check for clear structure
    if (/\n/.test(text)) clarityScore += 0.1;

    // Check for bullet points or numbered lists
    if (/^[\s]*[-*•]\s/m.test(text) || /^\s*\d+\.\s/m.test(text)) {
      clarityScore += 0.1;
    }

    return Math.min(clarityScore, 1);
  }

  /**
   * Calculate accuracy score
   */
  private async calculateAccuracy(text: string, contentId: string): Promise<number> {
    // Check against content evaluations
    const evaluations = await this.prisma.contentEvaluation.findMany({
      where: { contentId },
    });

    if (evaluations.length === 0) {
      return 0.8; // Default if no evaluations
    }

    const correctEvaluations = evaluations.filter((e) => (e as any).isCorrect).length;
    return correctEvaluations / evaluations.length;
  }

  /**
   * Calculate completeness score
   */
  private calculateCompleteness(text: string): number {
    let completenessScore = 0.5;

    // Check for introduction
    if (/^(introduction|overview|summary)/i.test(text.substring(0, 100))) {
      completenessScore += 0.1;
    }

    // Check for conclusion
    if (/(conclusion|summary|in conclusion)/i.test(text.substring(text.length - 200))) {
      completenessScore += 0.1;
    }

    // Check for sufficient length
    if (text.length > 200) completenessScore += 0.1;
    if (text.length > 500) completenessScore += 0.1;

    return Math.min(completenessScore, 1);
  }

  /**
   * Calculate engagement score
   */
  private async calculateEngagement(contentId: string): Promise<number> {
    // Fetch engagement metrics (views, time spent, interactions)
    // For now, return a default value
    return 0.7;
  }

  /**
   * Calculate alert severity based on degradation
   */
  private calculateSeverity(degradation: number): QualityAlert["severity"] {
    if (degradation > 0.4) return "critical";
    if (degradation > 0.3) return "high";
    if (degradation > 0.2) return "medium";
    return "low";
  }

  /**
   * Create a quality alert
   */
  private async createAlert(
    contentId: string,
    contentType: string,
    metricType: QualityAlert["metricType"],
    baselineValue: number,
    currentValue: number,
    degradation: number,
    severity: QualityAlert["severity"],
  ): Promise<QualityAlert> {
    const alert = await this.prisma.qualityAlert.create({
      data: {
        contentId,
        contentType,
        metricType,
        baselineValue,
        currentValue,
        degradation,
        severity,
        detectedAt: new Date(),
      },
    });

    return {
      id: alert.id,
      contentId: alert.contentId,
      contentType: alert.contentType,
      metricType: alert.metricType as QualityAlert["metricType"],
      baselineValue: alert.baselineValue,
      currentValue: alert.currentValue,
      degradation: alert.degradation,
      severity: alert.severity as QualityAlert["severity"],
      detectedAt: alert.detectedAt,
      resolved: false,
      resolvedAt: null,
    };
  }

  /**
   * Get active quality alerts
   */
  async getActiveAlerts(options: {
    contentType?: string;
    severity?: QualityAlert["severity"];
    limit?: number;
  } = {}): Promise<QualityAlert[]> {
    const where: {
      resolved: boolean;
      contentType?: string;
      severity?: QualityAlert["severity"];
    } = { resolved: false };

    if (options.contentType) {
      where.contentType = options.contentType;
    }

    if (options.severity) {
      where.severity = options.severity;
    }

    const alerts = await this.prisma.qualityAlert.findMany({
      where,
      orderBy: { detectedAt: "desc" },
      take: options.limit || 50,
    });

    return alerts.map((alert) => ({
      id: alert.id,
      contentId: alert.contentId,
      contentType: alert.contentType,
      metricType: alert.metricType as QualityAlert["metricType"],
      baselineValue: alert.baselineValue,
      currentValue: alert.currentValue,
      degradation: alert.degradation,
      severity: alert.severity as QualityAlert["severity"],
      detectedAt: alert.detectedAt,
      resolved: alert.resolved,
      resolvedAt: alert.resolvedAt || null,
    }));
  }

  /**
   * Resolve a quality alert
   */
  async resolveAlert(alertId: string): Promise<void> {
    await this.prisma.qualityAlert.update({
      where: { id: alertId },
      data: {
        resolved: true,
        resolvedAt: new Date(),
      },
    });
  }

  /**
   * Run batch monitoring for all content with baselines
   */
  async runBatchMonitoring(): Promise<{ totalContent: number; alertsCreated: number }> {
    const baselines = await this.prisma.qualityBaseline.findMany();
    let alertsCreated = 0;

    for (const baseline of baselines) {
      try {
        const alerts = await this.monitorContentQuality(baseline.contentId);
        alertsCreated += alerts.length;
      } catch (error) {
        // Log error but continue with other content
        console.error(`Failed to monitor content ${baseline.contentId}:`, error);
      }
    }

    return {
      totalContent: baselines.length,
      alertsCreated,
    };
  }
}
