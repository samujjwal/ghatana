/**
 * Content Drift Detector
 *
 * Monitors learning experiences for content drift — degradation in
 * effectiveness signalled by engagement drops, low mastery rates,
 * high abort rates, or negative learner feedback.
 *
 * When drift is detected, the detector creates DriftSignal records and
 * optionally triggers auto-revision via RegenerationInsight suggestions.
 *
 * @doc.type class
 * @doc.purpose Detect content drift and trigger auto-revision
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';

// ============================================================================
// Types
// ============================================================================

export type DriftSignalType =
    | 'engagement_drop'
    | 'high_abort_rate'
    | 'low_completion'
    | 'low_mastery'
    | 'negative_feedback';

export type DriftSeverity = 'low' | 'medium' | 'high';

export interface DriftThresholds {
    /** Min completion rate before signalling (0-1) */
    minCompletionRate: number;
    /** Max abort rate before signalling (0-1) */
    maxAbortRate: number;
    /** Min average mastery score before signalling (0-1) */
    minAverageMastery: number;
    /** Min engagement score (composite metric, 0-1) */
    minEngagementScore: number;
    /** Min positive feedback ratio (0-1) */
    minPositiveFeedbackRatio: number;
    /** Number of recent enrolments to sample */
    sampleSize: number;
    /** Only flag if at least this many learners have seen the content */
    minLearnerCount: number;
}

export interface DriftScanResult {
    experienceId: string;
    signals: DetectedSignal[];
    insights: GeneratedInsight[];
    scanDurationMs: number;
}

export interface DetectedSignal {
    signalType: DriftSignalType;
    severity: DriftSeverity;
    metric: string;
    value: number;
    threshold: number;
    recommendation: string;
    confidence: number;
}

export interface GeneratedInsight {
    category: string;
    issue: string;
    suggestedAction: string;
    priority: number;
    evidence: Record<string, unknown>;
    confidence: number;
}

const DEFAULT_THRESHOLDS: DriftThresholds = {
    minCompletionRate: 0.6,
    maxAbortRate: 0.3,
    minAverageMastery: 0.5,
    minEngagementScore: 0.4,
    minPositiveFeedbackRatio: 0.6,
    sampleSize: 50,
    minLearnerCount: 10,
};

// ============================================================================
// Drift Detector
// ============================================================================

export class ContentDriftDetector {
    private readonly thresholds: DriftThresholds;

    constructor(
        private readonly prisma: PrismaClient,
        thresholds?: Partial<DriftThresholds>,
    ) {
        this.thresholds = { ...DEFAULT_THRESHOLDS, ...thresholds };
    }

    /**
     * Scan a single experience for drift signals.
     */
    async scanExperience(
        tenantId: string,
        experienceId: string,
    ): Promise<DriftScanResult> {
        const startTime = Date.now();
        const signals: DetectedSignal[] = [];
        const insights: GeneratedInsight[] = [];

        // Gather metrics
        const metrics = await this.gatherMetrics(tenantId, experienceId);

        if (metrics.learnerCount < this.thresholds.minLearnerCount) {
            return { experienceId, signals, insights, scanDurationMs: Date.now() - startTime };
        }

        // Check completion rate
        if (metrics.completionRate < this.thresholds.minCompletionRate) {
            const severity = this.classifySeverity(
                metrics.completionRate,
                this.thresholds.minCompletionRate,
                0.1,
            );
            signals.push({
                signalType: 'low_completion',
                severity,
                metric: 'completion_rate',
                value: metrics.completionRate,
                threshold: this.thresholds.minCompletionRate,
                recommendation: this.getRecommendation('low_completion', severity, metrics),
                confidence: Math.min(0.95, metrics.learnerCount / 100),
            });
        }

        // Check abort rate
        if (metrics.abortRate > this.thresholds.maxAbortRate) {
            const severity = this.classifySeverity(
                this.thresholds.maxAbortRate,
                metrics.abortRate,
                0.1,
            );
            signals.push({
                signalType: 'high_abort_rate',
                severity,
                metric: 'abort_rate',
                value: metrics.abortRate,
                threshold: this.thresholds.maxAbortRate,
                recommendation: this.getRecommendation('high_abort_rate', severity, metrics),
                confidence: Math.min(0.95, metrics.learnerCount / 100),
            });
        }

        // Check average mastery
        if (metrics.avgMastery < this.thresholds.minAverageMastery) {
            const severity = this.classifySeverity(
                metrics.avgMastery,
                this.thresholds.minAverageMastery,
                0.15,
            );
            signals.push({
                signalType: 'low_mastery',
                severity,
                metric: 'average_mastery',
                value: metrics.avgMastery,
                threshold: this.thresholds.minAverageMastery,
                recommendation: this.getRecommendation('low_mastery', severity, metrics),
                confidence: Math.min(0.95, metrics.learnerCount / 100),
            });
        }

        // Check engagement
        if (metrics.engagementScore < this.thresholds.minEngagementScore) {
            const severity = this.classifySeverity(
                metrics.engagementScore,
                this.thresholds.minEngagementScore,
                0.1,
            );
            signals.push({
                signalType: 'engagement_drop',
                severity,
                metric: 'engagement_score',
                value: metrics.engagementScore,
                threshold: this.thresholds.minEngagementScore,
                recommendation: this.getRecommendation('engagement_drop', severity, metrics),
                confidence: Math.min(0.90, metrics.learnerCount / 100),
            });
        }

        // Check feedback ratio
        if (
            metrics.feedbackCount > 5 &&
            metrics.positiveFeedbackRatio < this.thresholds.minPositiveFeedbackRatio
        ) {
            signals.push({
                signalType: 'negative_feedback',
                severity: metrics.positiveFeedbackRatio < 0.3 ? 'high' : 'medium',
                metric: 'positive_feedback_ratio',
                value: metrics.positiveFeedbackRatio,
                threshold: this.thresholds.minPositiveFeedbackRatio,
                recommendation: this.getRecommendation('negative_feedback', 'medium', metrics),
                confidence: Math.min(0.85, metrics.feedbackCount / 50),
            });
        }

        // Generate regeneration insights from high-severity signals
        for (const signal of signals.filter((s) => s.severity === 'high')) {
            insights.push(this.signalToInsight(signal, metrics));
        }

        // Persist signals and insights
        await this.persistResults(tenantId, experienceId, signals, insights);

        return {
            experienceId,
            signals,
            insights,
            scanDurationMs: Date.now() - startTime,
        };
    }

    /**
     * Scan all active experiences for a tenant.
     */
    async scanTenant(tenantId: string): Promise<DriftScanResult[]> {
        const experiences = await this.prisma.learningExperience.findMany({
            where: { tenantId, status: 'PUBLISHED' },
            select: { id: true },
        });

        const results: DriftScanResult[] = [];
        for (const exp of experiences) {
            const result = await this.scanExperience(tenantId, exp.id);
            if (result.signals.length > 0) {
                results.push(result);
            }
        }
        return results;
    }

    // =========================================================================
    // Private: metrics gathering
    // =========================================================================

    private async gatherMetrics(
        tenantId: string,
        experienceId: string,
    ): Promise<{
        learnerCount: number;
        completionRate: number;
        abortRate: number;
        avgMastery: number;
        engagementScore: number;
        feedbackCount: number;
        positiveFeedbackRatio: number;
        avgTimeOnTaskMinutes: number;
        claimMasteryDistribution: Record<string, number>;
    }> {
        // Fetch recent enrolments
        const enrolments = await this.prisma.enrollment.findMany({
            where: {
                module: { experienceId },
                tenantId,
            },
            orderBy: { createdAt: 'desc' },
            take: this.thresholds.sampleSize,
            select: {
                status: true,
                userId: true,
            },
        });

        const learnerCount = enrolments.length;
        if (learnerCount === 0) {
            return {
                learnerCount: 0,
                completionRate: 1,
                abortRate: 0,
                avgMastery: 1,
                engagementScore: 1,
                feedbackCount: 0,
                positiveFeedbackRatio: 1,
                avgTimeOnTaskMinutes: 0,
                claimMasteryDistribution: {},
            };
        }

        const completedCount = enrolments.filter(
            (e: { status: string }) => e.status === 'COMPLETED',
        ).length;
        const abortedCount = enrolments.filter(
            (e: { status: string }) => e.status === 'DROPPED' || e.status === 'ABORTED',
        ).length;

        // Fetch graded assessment attempts for mastery
        const userIds = [...new Set(enrolments.map((e: { userId: string }) => e.userId))];
        const attempts = await this.prisma.assessmentAttempt.findMany({
            where: {
                userId: { in: userIds },
                status: 'GRADED',
                assessment: { module: { experienceId } },
            },
            select: { scorePercent: true },
        });

        const scores = attempts
            .map((a: { scorePercent: number | null }) => a.scorePercent)
            .filter((s: number | null): s is number => s !== null);
        const avgMastery =
            scores.length > 0 ? scores.reduce((sum: number, s: number) => sum + s, 0) / scores.length / 100 : 0.5;

        // Engagement heuristic: completion rate weighted with time-on-task
        const engagementScore = completedCount / learnerCount * 0.7 + (1 - abortedCount / learnerCount) * 0.3;

        // Feedback (simplified — would query a feedback table)
        const feedbackCount = 0;
        const positiveFeedbackRatio = 1;

        return {
            learnerCount,
            completionRate: completedCount / learnerCount,
            abortRate: abortedCount / learnerCount,
            avgMastery,
            engagementScore: Math.max(0, Math.min(1, engagementScore)),
            feedbackCount,
            positiveFeedbackRatio,
            avgTimeOnTaskMinutes: 0,
            claimMasteryDistribution: {},
        };
    }

    // =========================================================================
    // Private: classification and recommendation
    // =========================================================================

    private classifySeverity(
        actual: number,
        threshold: number,
        step: number,
    ): DriftSeverity {
        const delta = Math.abs(actual - threshold);
        if (delta > step * 2) return 'high';
        if (delta > step) return 'medium';
        return 'low';
    }

    private getRecommendation(
        signalType: DriftSignalType,
        severity: DriftSeverity,
        metrics: Record<string, unknown>,
    ): string {
        const recommendations: Record<DriftSignalType, Record<DriftSeverity, string>> = {
            low_completion: {
                low: 'Consider adding more scaffolding or examples to support learners.',
                medium: 'Review content difficulty and add intermediate checkpoints.',
                high: 'Content requires significant restructuring. Consider breaking into smaller modules with more support.',
            },
            high_abort_rate: {
                low: 'Monitor early exit points and consider adding engagement hooks.',
                medium: 'Identify the most common drop-off point and add alternative pathways.',
                high: 'Critical: content is driving learners away. Immediate review of difficulty, relevance, and UX needed.',
            },
            low_mastery: {
                low: 'Add more practice opportunities and worked examples.',
                medium: 'Review assessment alignment with content. Consider adding remediation paths.',
                high: 'Content-assessment misalignment detected. Regenerate examples and add simulation-based practice.',
            },
            engagement_drop: {
                low: 'Add interactive elements or real-world connections.',
                medium: 'Review content pacing and add animation/simulation variety.',
                high: 'Major engagement failure. Consider full content regeneration with different modalities.',
            },
            negative_feedback: {
                low: 'Review specific feedback themes and make targeted improvements.',
                medium: 'Address the most common feedback themes with content updates.',
                high: 'Widespread dissatisfaction. Conduct a full content review and regeneration.',
            },
        };

        return recommendations[signalType]?.[severity] ?? 'Review content and consider improvements.';
    }

    private signalToInsight(
        signal: DetectedSignal,
        metrics: Record<string, unknown>,
    ): GeneratedInsight {
        const categoryMap: Record<DriftSignalType, string> = {
            low_completion: 'content_difficulty',
            high_abort_rate: 'content_clarity',
            low_mastery: 'task_alignment',
            engagement_drop: 'simulation_complexity',
            negative_feedback: 'content_clarity',
        };

        return {
            category: categoryMap[signal.signalType] ?? 'content_difficulty',
            issue: `${signal.signalType}: ${signal.metric} = ${signal.value.toFixed(2)} (threshold: ${signal.threshold.toFixed(2)})`,
            suggestedAction: signal.recommendation,
            priority: signal.severity === 'high' ? 9 : signal.severity === 'medium' ? 6 : 3,
            evidence: {
                signalType: signal.signalType,
                metric: signal.metric,
                value: signal.value,
                threshold: signal.threshold,
            },
            confidence: signal.confidence,
        };
    }

    // =========================================================================
    // Private: persistence
    // =========================================================================

    private async persistResults(
        tenantId: string,
        experienceId: string,
        signals: DetectedSignal[],
        insights: GeneratedInsight[],
    ): Promise<void> {
        // Persist drift signals
        for (const signal of signals) {
            await this.prisma.driftSignal.create({
                data: {
                    tenantId,
                    experienceId,
                    signalType: signal.signalType,
                    severity: signal.severity,
                    metric: signal.metric,
                    value: signal.value,
                    threshold: signal.threshold,
                    recommendation: signal.recommendation,
                    confidence: signal.confidence,
                    status: 'detected',
                },
            });
        }

        // Persist regeneration insights
        for (const insight of insights) {
            await this.prisma.regenerationInsight.create({
                data: {
                    tenantId,
                    experienceId,
                    category: insight.category,
                    issue: insight.issue,
                    suggestedAction: insight.suggestedAction,
                    priority: insight.priority,
                    evidence: insight.evidence,
                    confidence: insight.confidence,
                    status: 'identified',
                },
            });
        }
    }
}

/**
 * Factory function.
 */
export function createContentDriftDetector(
    prisma: PrismaClient,
    thresholds?: Partial<DriftThresholds>,
): ContentDriftDetector {
    return new ContentDriftDetector(prisma, thresholds);
}
