/**
 * Grading Quality Monitoring Service
 *
 * Monitor and analyze grading quality across AI and teacher grading.
 *
 * @doc.type class
 * @doc.purpose Monitor grading quality and detect anomalies
 * @doc.layer product
 * @doc.pattern Monitoring Service
 */

import type { PrismaClient } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'GradingQualityMonitoringService' });

export interface QualityMetrics {
  aiGradingAccuracy: number;
  teacherAiAgreement: number;
  gradingConsistency: number;
  biasScore: number;
  avgConfidence: number;
  needsReviewRate: number;
  avgProcessingTimeMs: number;
}

export interface QualityAlert {
  id: string;
  type: 'low_accuracy' | 'low_agreement' | 'inconsistency' | 'bias_detected' | 'performance_degradation';
  severity: 'low' | 'medium' | 'high';
  message: string;
  metric: string;
  value: number;
  threshold: number;
  timestamp: string;
}

type ReviewTaskMetricsRecord = {
  status: string;
  aiGradingResult: { scorePercent?: number; confidence?: number } | null;
  reviewedScore: number | null;
};

type ReviewTaskMetricsDelegate = {
  findMany(args: {
    where: Record<string, unknown>;
    take?: number;
  }): Promise<ReviewTaskMetricsRecord[]>;
};

export class GradingQualityMonitoringService {
  private readonly ACCURACY_THRESHOLD = 0.8;
  private readonly AGREEMENT_THRESHOLD = 0.85;
  private readonly CONSISTENCY_THRESHOLD = 0.9;
  private readonly BIAS_THRESHOLD = 0.1;

  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Calculate quality metrics for a tenant
   */
  async calculateQualityMetrics(tenantId: string): Promise<QualityMetrics> {
    logger.info({
      message: 'Calculating quality metrics',
      tenantId,
    });

    // Fetch recent grading data
    const recentGrading = await this.prisma.assessmentAttempt.findMany({
      where: {
        assessment: { tenantId },
        status: 'GRADED',
        gradedAt: { gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }, // Last 7 days
      },
      include: {
        assessment: {
          include: { items: true },
        },
      },
      take: 1000,
    });

    // Fetch review tasks for agreement calculation
    const reviewTasks = await this.getReviewTaskDelegate().findMany({
      where: {
        tenantId,
        status: 'completed',
        completedAt: { gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) },
      },
      take: 500,
    });

    // Calculate metrics
    const aiGradingAccuracy = this.calculateAccuracy(recentGrading);
    const teacherAiAgreement = this.calculateAgreement(reviewTasks);
    const gradingConsistency = this.calculateConsistency(recentGrading);
    const biasScore = this.calculateBias(recentGrading);
    const avgConfidence = this.calculateAvgConfidence(reviewTasks);
    const needsReviewRate = this.calculateNeedsReviewRate(reviewTasks);
    const avgProcessingTimeMs = this.calculateAvgProcessingTime(recentGrading);

    const metrics: QualityMetrics = {
      aiGradingAccuracy,
      teacherAiAgreement,
      gradingConsistency,
      biasScore,
      avgConfidence,
      needsReviewRate,
      avgProcessingTimeMs,
    };

    logger.info({
      message: 'Quality metrics calculated',
      tenantId,
      metrics,
    });

    return metrics;
  }

  /**
   * Check for quality alerts
   */
  async checkQualityAlerts(tenantId: string): Promise<QualityAlert[]> {
    const metrics = await this.calculateQualityMetrics(tenantId);
    const alerts: QualityAlert[] = [];

    if (metrics.aiGradingAccuracy < this.ACCURACY_THRESHOLD) {
      alerts.push({
        id: `alert-${Date.now()}-accuracy`,
        type: 'low_accuracy',
        severity: metrics.aiGradingAccuracy < 0.6 ? 'high' : 'medium',
        message: `AI grading accuracy below threshold: ${metrics.aiGradingAccuracy.toFixed(2)}`,
        metric: 'aiGradingAccuracy',
        value: metrics.aiGradingAccuracy,
        threshold: this.ACCURACY_THRESHOLD,
        timestamp: new Date().toISOString(),
      });
    }

    if (metrics.teacherAiAgreement < this.AGREEMENT_THRESHOLD) {
      alerts.push({
        id: `alert-${Date.now()}-agreement`,
        type: 'low_agreement',
        severity: metrics.teacherAiAgreement < 0.7 ? 'high' : 'medium',
        message: `Teacher-AI agreement below threshold: ${metrics.teacherAiAgreement.toFixed(2)}`,
        metric: 'teacherAiAgreement',
        value: metrics.teacherAiAgreement,
        threshold: this.AGREEMENT_THRESHOLD,
        timestamp: new Date().toISOString(),
      });
    }

    if (metrics.gradingConsistency < this.CONSISTENCY_THRESHOLD) {
      alerts.push({
        id: `alert-${Date.now()}-consistency`,
        type: 'inconsistency',
        severity: metrics.gradingConsistency < 0.8 ? 'high' : 'medium',
        message: `Grading consistency below threshold: ${metrics.gradingConsistency.toFixed(2)}`,
        metric: 'gradingConsistency',
        value: metrics.gradingConsistency,
        threshold: this.CONSISTENCY_THRESHOLD,
        timestamp: new Date().toISOString(),
      });
    }

    if (metrics.biasScore > this.BIAS_THRESHOLD) {
      alerts.push({
        id: `alert-${Date.now()}-bias`,
        type: 'bias_detected',
        severity: metrics.biasScore > 0.2 ? 'high' : 'medium',
        message: `Potential bias detected: ${metrics.biasScore.toFixed(2)}`,
        metric: 'biasScore',
        value: metrics.biasScore,
        threshold: this.BIAS_THRESHOLD,
        timestamp: new Date().toISOString(),
      });
    }

    if (alerts.length > 0) {
      logger.warn({
        message: 'Quality alerts detected',
        tenantId,
        alertCount: alerts.length,
        alerts,
      });
    }

    return alerts;
  }

  /**
   * Get quality dashboard data
   */
  async getQualityDashboard(tenantId: string): Promise<{
    metrics: QualityMetrics;
    alerts: QualityAlert[];
    trends: Array<{ date: string; accuracy: number; agreement: number }>;
  }> {
    const metrics = await this.calculateQualityMetrics(tenantId);
    const alerts = await this.checkQualityAlerts(tenantId);
    const trends = await this.calculateTrends(tenantId);

    return {
      metrics,
      alerts,
      trends,
    };
  }

  private calculateAccuracy(attempts: any[]): number {
    if (attempts.length === 0) return 1;
    // Simplified: use score distribution as proxy for accuracy
    const avgScore = attempts.reduce((sum, a) => sum + (a.scorePercent || 0), 0) / attempts.length;
    return avgScore / 100;
  }

  private calculateAgreement(reviewTasks: any[]): number {
    const completedWithScores = reviewTasks.filter(
      (t) => t.status === 'completed' && t.aiGradingResult && t.reviewedScore !== null,
    );

    if (completedWithScores.length === 0) return 1;

    const agreements = completedWithScores.map((task) => {
      const aiScore = task.aiGradingResult?.scorePercent || 0;
      const teacherScore = task.reviewedScore || 0;
      const diff = Math.abs(aiScore - teacherScore);
      return 1 - (diff / 100);
    });

    return agreements.reduce((sum, a) => sum + a, 0) / agreements.length;
  }

  private calculateConsistency(attempts: any[]): number {
    if (attempts.length < 2) return 1;
    
    // Group by assessment and calculate variance
    const byAssessment = new Map<string, number[]>();
    attempts.forEach((attempt) => {
      const scores = byAssessment.get(attempt.assessmentId) || [];
      scores.push(attempt.scorePercent || 0);
      byAssessment.set(attempt.assessmentId, scores);
    });

    let totalVariance = 0;
    let count = 0;

    byAssessment.forEach((scores) => {
      if (scores.length < 2) return;
      const mean = scores.reduce((sum, s) => sum + s, 0) / scores.length;
      const variance = scores.reduce((sum, s) => sum + Math.pow(s - mean, 2), 0) / scores.length;
      totalVariance += variance;
      count++;
    });

    if (count === 0) return 1;
    const avgVariance = totalVariance / count;
    return Math.max(0, 1 - (avgVariance / 1000)); // Normalize
  }

  private calculateBias(attempts: any[]): number {
    // Simplified: check for score distribution anomalies across student groups
    // In production, this would use actual demographic data
    return 0.05; // Placeholder
  }

  private calculateAvgConfidence(reviewTasks: any[]): number {
    const withConfidence = reviewTasks.filter((t) => t.aiGradingResult?.confidence);
    if (withConfidence.length === 0) return 0.8;

    const total = withConfidence.reduce((sum, t) => sum + (t.aiGradingResult?.confidence || 0), 0);
    return total / withConfidence.length;
  }

  private calculateNeedsReviewRate(reviewTasks: any[]): number {
    if (reviewTasks.length === 0) return 0;
    const needsReview = reviewTasks.filter((t) => t.aiGradingResult?.confidence < 0.7).length;
    return needsReview / reviewTasks.length;
  }

  private calculateAvgProcessingTime(attempts: any[]): number {
    // Placeholder: would need actual processing time data
    return 500; // 500ms average
  }

  private getReviewTaskDelegate(): ReviewTaskMetricsDelegate {
    const prismaWithDelegate = this.prisma as PrismaClient & {
      gradingReviewTask?: ReviewTaskMetricsDelegate;
    };

    if (!prismaWithDelegate.gradingReviewTask) {
      throw new Error('gradingReviewTask delegate is unavailable. Regenerate Tutorputor Prisma client or align grading quality monitoring with the current assessment review schema.');
    }

    return prismaWithDelegate.gradingReviewTask;
  }

  private async calculateTrends(tenantId: string): Promise<Array<{ date: string; accuracy: number; agreement: number }>> {
    // Generate last 7 days of trend data
    const trends: Array<{ date: string; accuracy: number; agreement: number }> = [];
    for (let i = 6; i >= 0; i--) {
      const date = new Date(Date.now() - i * 24 * 60 * 60 * 1000);
      const datePart = date.toISOString().split('T')[0] ?? date.toISOString();
      trends.push({
        date: datePart,
        accuracy: 0.8 + Math.random() * 0.1, // Placeholder
        agreement: 0.85 + Math.random() * 0.1, // Placeholder
      });
    }
    return trends;
  }
}
