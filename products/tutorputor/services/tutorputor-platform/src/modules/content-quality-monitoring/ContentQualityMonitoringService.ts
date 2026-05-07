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

type ContentEvaluationRecord = {
  isCorrect?: boolean | null;
};

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
        baselineMetrics: JSON.stringify(metrics),
        establishedAt: new Date(),
      },
      update: {
        baselineMetrics: JSON.stringify(metrics),
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
    try {
      // Try to fetch content from various sources based on contentId format
      // Content IDs may reference different content types
      let contentText = "";

      // Try to fetch as module content block (uses payload field)
      const contentBlock = await this.prisma.moduleContentBlock.findUnique({
        where: { id: contentId },
        select: { payload: true },
      });

      if (contentBlock) {
        contentText = typeof contentBlock.payload === "string" 
          ? contentBlock.payload 
          : JSON.stringify(contentBlock.payload);
      } else {
        // Try to fetch as learning objective (uses label field, id is Int)
        const objectiveId = parseInt(contentId, 10);
        if (!isNaN(objectiveId)) {
          const learningObjective = await this.prisma.moduleLearningObjective.findUnique({
            where: { id: objectiveId },
            select: { label: true },
          });

          if (learningObjective) {
            contentText = learningObjective.label;
          }
        }

        if (!contentText) {
          // Try to fetch as simulation manifest
          const simulationManifest = await this.prisma.simulationManifest.findUnique({
            where: { id: contentId },
            select: { manifest: true },
          });

          if (simulationManifest) {
            contentText = typeof simulationManifest.manifest === "string"
              ? simulationManifest.manifest
              : JSON.stringify(simulationManifest.manifest);
          }
        }
      }

      // If no content found, return default low metrics
      if (!contentText) {
        return {
          clarity: 0.5,
          accuracy: 0.5,
          completeness: 0.5,
          engagement: await this.calculateEngagement(contentId),
        };
      }

      // Calculate actual metrics
      const clarity = this.calculateClarity(contentText);
      const accuracy = await this.calculateAccuracy(contentText, contentId);
      const completeness = this.calculateCompleteness(contentText);
      const engagement = await this.calculateEngagement(contentId);

      return {
        clarity,
        accuracy,
        completeness,
        engagement,
      };
    } catch (error) {
      console.error("Failed to calculate current metrics:", error);
      // Return default values on error
      return {
        clarity: 0.5,
        accuracy: 0.5,
        completeness: 0.5,
        engagement: 0.5,
      };
    }
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

    const correctEvaluations = (evaluations as ContentEvaluationRecord[]).filter(
      (e) => e.isCorrect === true,
    ).length;
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
    try {
      // Try to determine the moduleId from contentId
      let moduleId: string | null = null;

      // Try to fetch as module content block
      const contentBlock = await this.prisma.moduleContentBlock.findUnique({
        where: { id: contentId },
        select: { moduleId: true },
      });

      if (contentBlock) {
        moduleId = contentBlock.moduleId;
      } else {
        // Try to fetch as learning objective
        const objectiveId = parseInt(contentId, 10);
        if (!isNaN(objectiveId)) {
          const learningObjective = await this.prisma.moduleLearningObjective.findUnique({
            where: { id: objectiveId },
            select: { moduleId: true },
          });

          if (learningObjective) {
            moduleId = learningObjective.moduleId;
          }
        }

        if (!moduleId) {
          // Try to fetch as simulation manifest
          const simulationManifest = await this.prisma.simulationManifest.findUnique({
            where: { id: contentId },
            select: { moduleId: true },
          });

          if (simulationManifest) {
            moduleId = simulationManifest.moduleId;
          }
        }
      }

      if (!moduleId) {
        return 0.5; // Default if no module found
      }

      // Calculate engagement based on module enrollment and learning events
      const now = new Date();
      const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

      // Get enrollments for this module
      const enrollments = await this.prisma.enrollment.findMany({
        where: {
          moduleId,
          status: { in: ["IN_PROGRESS", "COMPLETED"] },
        },
        select: {
          id: true,
          userId: true,
          status: true,
          progressPercent: true,
          startedAt: true,
        },
      });

      if (enrollments.length === 0) {
        return 0.5; // No engagement yet
      }

      // Get learning events for this module in the last 30 days
      const learningEvents = await this.prisma.learningEvent.findMany({
        where: {
          moduleId,
          timestamp: { gte: thirtyDaysAgo },
        },
        select: {
          id: true,
          userId: true,
          eventType: true,
        },
      });

      // Calculate engagement score based on:
      // - Average progress across enrollments
      // - Frequency of learning events
      // - Completion rate

      const avgProgress = enrollments.reduce((sum, e) => sum + (e.progressPercent || 0), 0) / enrollments.length;
      const completedCount = enrollments.filter((e) => e.status === "COMPLETED").length;
      const completionRate = completedCount / enrollments.length;

      // Normalize event frequency (events per enrollment per 30 days)
      const eventsPerEnrollment = enrollments.length > 0 ? learningEvents.length / enrollments.length : 0;
      const normalizedEventFrequency = Math.min(eventsPerEnrollment / 10, 1); // Cap at 10 events per enrollment

      // Combine metrics into engagement score
      const engagementScore = (
        (avgProgress / 100) * 0.4 + // Progress contributes 40%
        completionRate * 0.3 + // Completion rate contributes 30%
        normalizedEventFrequency * 0.3 // Event frequency contributes 30%
      );

      return engagementScore;
    } catch (error) {
      console.error("Failed to calculate engagement:", error);
      return 0.5; // Default on error
    }
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
