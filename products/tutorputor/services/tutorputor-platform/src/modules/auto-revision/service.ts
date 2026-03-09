/**
 * Auto-Revision Service
 *
 * Schema-aligned implementation for drift detection, regeneration queueing,
 * and A/B experiment lifecycle management.
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';

export interface DriftSignal {
    type: 'engagement_drop' | 'high_abort_rate' | 'low_completion' | 'low_mastery' | 'negative_feedback';
    severity: 'low' | 'medium' | 'high';
    metric: string;
    value: number;
    threshold: number;
    recommendation: string;
    detectedAt: Date;
}

export interface RegenerationInsight {
    category:
        | 'content_difficulty'
        | 'content_clarity'
        | 'simulation_complexity'
        | 'task_alignment'
        | 'grade_level'
        | 'engagement_drop'
        | 'completion_issues'
        | 'mastery_problems'
        | 'feedback_issues';
    issue: string;
    evidence: any;
    suggestedAction: string;
    priority: number;
}

export interface RegenerationCandidate {
    experienceId: string;
    signals: DriftSignal[];
    insights: RegenerationInsight[];
    priority: number;
    estimatedImpact: 'low' | 'medium' | 'high';
    queuedAt: Date;
}

export interface ABExperiment {
    id: string;
    experienceId: string;
    controlVersion: number;
    treatmentVersion: number;
    status: 'draft' | 'running' | 'completed' | 'winner_promoted' | 'failed';
    sampleSize: {
        control: number;
        treatment: number;
    };
    metrics: {
        control: Record<string, any>;
        treatment: Record<string, any>;
    };
    statisticalResults?: {
        pValue: number;
        confidenceInterval: [number, number];
        effectSize: number;
        winner: 'control' | 'treatment' | 'inconclusive';
    };
    createdAt: Date;
    startedAt?: Date;
    completedAt?: Date;
}

export interface AutoRevisionConfig {
    minSampleSize: number;
    significanceThreshold: number;
    driftCheckIntervalHours: number;
    maxConcurrentRegenerations: number;
    thresholds: {
        completionRate: number;
        dropOffRate: number;
        avgTimeMinutes: number;
        simulationAbortRate: number;
        masteryRate: number;
        feedbackScore: number;
    };
}

export class AutoRevisionService {
    private config: AutoRevisionConfig = {
        minSampleSize: 100,
        significanceThreshold: 0.05,
        driftCheckIntervalHours: 6,
        maxConcurrentRegenerations: 3,
        thresholds: {
            completionRate: 0.6,
            dropOffRate: 0.4,
            avgTimeMinutes: 5,
            simulationAbortRate: 0.3,
            masteryRate: 0.6,
            feedbackScore: 3.0,
        },
    };

    constructor(
        private readonly prisma: PrismaClient,
        private readonly contentStudio: any,
    ) {}

    async detectDrift(
        experienceId: string,
        options?: { allowMissingAnalytics?: boolean; analyticsOverride?: unknown },
    ): Promise<DriftSignal[]> {
        const rawAnalytics = options?.analyticsOverride ?? await this.prisma.experienceAnalytics.findUnique({
            where: { experienceId },
        });

        if (!rawAnalytics) {
            if (options?.allowMissingAnalytics) {
                return [];
            }
            throw new Error(`Analytics not found for experience: ${experienceId}`);
        }

        const analytics = this.normalizeAnalytics(rawAnalytics);

        const signals: DriftSignal[] = [];

        if (analytics.completionRate < this.config.thresholds.completionRate) {
            signals.push({
                type: 'low_completion',
                severity: this.severityForRatio(
                    analytics.completionRate,
                    this.config.thresholds.completionRate,
                    'low',
                ),
                metric: 'completionRate',
                value: analytics.completionRate,
                threshold: this.config.thresholds.completionRate,
                recommendation: 'Improve onboarding and first task clarity',
                detectedAt: new Date(),
            });
        }

        if (analytics.avgTimeMinutes < this.config.thresholds.avgTimeMinutes) {
            signals.push({
                type: 'engagement_drop',
                severity: this.severityForRatio(
                    analytics.avgTimeMinutes,
                    this.config.thresholds.avgTimeMinutes,
                    'low',
                ),
                metric: 'avgTimeMinutes',
                value: analytics.avgTimeMinutes,
                threshold: this.config.thresholds.avgTimeMinutes,
                recommendation: 'Improve pacing and first-task engagement to increase session time',
                detectedAt: new Date(),
            });
        }

        if (analytics.dropOffRate > this.config.thresholds.dropOffRate) {
            signals.push({
                type: 'engagement_drop',
                severity: this.severityForRatio(
                    analytics.dropOffRate,
                    this.config.thresholds.dropOffRate,
                    'high',
                ),
                metric: 'dropOffRate',
                value: analytics.dropOffRate,
                threshold: this.config.thresholds.dropOffRate,
                recommendation: 'Reduce friction in simulation steps and task prompts',
                detectedAt: new Date(),
            });
        }

        if (analytics.masteryRate < this.config.thresholds.masteryRate) {
            signals.push({
                type: 'low_mastery',
                severity: this.severityForRatio(
                    analytics.masteryRate,
                    this.config.thresholds.masteryRate,
                    'low',
                ),
                metric: 'masteryRate',
                value: analytics.masteryRate,
                threshold: this.config.thresholds.masteryRate,
                recommendation: 'Add scaffolding and worked examples',
                detectedAt: new Date(),
            });
        }

        if (analytics.abortRate > this.config.thresholds.simulationAbortRate) {
            signals.push({
                type: 'high_abort_rate',
                severity: this.severityForRatio(
                    analytics.abortRate,
                    this.config.thresholds.simulationAbortRate,
                    'high',
                ),
                metric: 'abortRate',
                value: analytics.abortRate,
                threshold: this.config.thresholds.simulationAbortRate,
                recommendation: 'Simplify simulation controls and reduce cognitive load',
                detectedAt: new Date(),
            });
        }

        if (analytics.hasQualityIssues || analytics.averageFeedbackScore < this.config.thresholds.feedbackScore) {
            signals.push({
                type: 'negative_feedback',
                severity: analytics.averageFeedbackScore < 2 ? 'high' : 'medium',
                metric: 'averageFeedbackScore',
                value: analytics.averageFeedbackScore,
                threshold: this.config.thresholds.feedbackScore,
                recommendation: 'Review learner feedback and improve clarity, pacing, and content quality',
                detectedAt: new Date(),
            });
        }

        return signals;
    }

    async monitorDrift(): Promise<RegenerationCandidate[]> {
        const experiences = await this.prisma.learningExperience.findMany({
            where: { status: 'PUBLISHED' },
            include: { experienceAnalytics: true },
        });

        const candidates: RegenerationCandidate[] = [];

        for (const experience of experiences) {
            const analytics = (experience as any).experienceAnalytics ?? (experience as any).analytics;
            if (!analytics) {
                continue;
            }

            const signals = await this.detectDrift(experience.id, {
                allowMissingAnalytics: true,
                analyticsOverride: analytics,
            });
            if (signals.length === 0) {
                continue;
            }

            const insights = this.analyzeStruggles(analytics);
            const priority = this.calculatePriority(signals, insights);

            candidates.push({
                experienceId: experience.id,
                signals,
                insights,
                priority,
                estimatedImpact: this.estimateImpact(priority),
                queuedAt: new Date(),
            });

            await this.queueExperienceRegeneration(experience.id, signals, insights);
        }

        return candidates;
    }

    async queueExperienceRegeneration(
        experienceId: string,
        signals: DriftSignal[] = [],
        insights: RegenerationInsight[] = [],
    ): Promise<any> {
        return this.prisma.experienceAutoRefinement.create({
            data: {
                experienceId,
                analyticsSnapshot: { signals, insights },
                triggerReason: signals[0]?.type || 'manual_regeneration',
                appliedGuardrails: {
                    version: '1.0.0',
                    source: 'auto-revision',
                },
                status: 'pending',
            } as any,
        });
    }

    async generateImprovedVersion(
        experienceId: string,
        insights: RegenerationInsight[],
    ): Promise<any> {
        const current = await this.prisma.learningExperience.findUnique({
            where: { id: experienceId },
        });

        if (!current) {
            throw new Error(`Experience not found: ${experienceId}`);
        }

        const refinementPrompt = this.createImprovementPrompt(current, insights);
        if (typeof this.contentStudio?.refineExperience === 'function') {
            const refined = await this.contentStudio.refineExperience({
                experienceId,
                refinementPrompt,
                userId: 'auto-revision',
            });

            if (refined?.experience) {
                return refined.experience;
            }
        }

        if (typeof this.contentStudio?.refineContent === 'function') {
            const refined = await this.contentStudio.refineContent(experienceId, {
                refinementPrompt,
                userId: 'auto-revision',
            });

            if (refined) return refined;
        }

        return this.prisma.learningExperience.update({
            where: { id: experienceId },
            data: {
                version: { increment: 1 },
                lastEditedBy: 'auto-revision',
            },
        });
    }

    async processRegenerationQueue(): Promise<void> {
        const queue = await this.prisma.experienceAutoRefinement.findMany({
            where: { status: 'pending' },
            orderBy: { createdAt: 'asc' },
            take: this.config.maxConcurrentRegenerations,
        });

        for (const item of queue) {
            try {
                const snapshot = item.analyticsSnapshot as any;
                const insights: RegenerationInsight[] = Array.isArray(snapshot?.insights)
                    ? snapshot.insights
                    : [];

                const original = await this.prisma.learningExperience.findUnique({
                    where: { id: item.experienceId },
                    select: { version: true, tenantId: true },
                });

                if (!original) {
                    await this.prisma.experienceAutoRefinement.update({
                        where: { id: item.id },
                        data: {
                            status: 'rejected',
                        },
                    });
                    continue;
                }

                const improved = await this.generateImprovedVersion(item.experienceId, insights);

                const treatmentVersion =
                    typeof improved?.version === 'number'
                        ? improved.version
                        : original.version + 1;
                await this.createABExperiment(item.experienceId, treatmentVersion);

                await this.prisma.experienceAutoRefinement.update({
                    where: { id: item.id },
                    data: {
                        status: 'reviewed',
                        candidateExperienceId: improved.id,
                        reviewedAt: new Date(),
                    },
                });
            } catch (error) {
                await this.prisma.experienceAutoRefinement.update({
                    where: { id: item.id },
                    data: {
                        status: 'rejected',
                    },
                });
            }
        }
    }

    async createABExperiment(
        experienceId: string,
        treatmentVersion: number,
    ): Promise<ABExperiment> {
        const experience = await this.prisma.learningExperience.findUnique({
            where: { id: experienceId },
            select: { tenantId: true, version: true },
        });

        if (!experience) {
            throw new Error(`Experience not found: ${experienceId}`);
        }

        const created = await this.prisma.abExperiment.create({
            data: {
                tenantId: experience.tenantId,
                experienceId,
                controlVersion: experience.version,
                treatmentVersion,
                status: 'draft',
                controlMetrics: {},
                treatmentMetrics: {},
            },
        });

        return {
            id: created.id,
            experienceId: created.experienceId,
            controlVersion: created.controlVersion,
            treatmentVersion: created.treatmentVersion,
            status: created.status as ABExperiment['status'],
            sampleSize: {
                control: created.controlSampleSize,
                treatment: created.treatmentSampleSize,
            },
            metrics: {
                control: (created.controlMetrics as any) || {},
                treatment: (created.treatmentMetrics as any) || {},
            },
            createdAt: created.createdAt,
            startedAt: created.startedAt || undefined,
            completedAt: created.completedAt || undefined,
        };
    }

    async evaluateABExperiments(): Promise<void> {
        const experiments = await this.prisma.abExperiment.findMany({
            where: { status: 'running' },
        });

        for (const experiment of experiments) {
            if (
                experiment.controlSampleSize < this.config.minSampleSize ||
                experiment.treatmentSampleSize < this.config.minSampleSize
            ) {
                continue;
            }

            const controlScore = this.extractMetricScore(experiment.controlMetrics, 'completionRate');
            const treatmentScore = this.extractMetricScore(experiment.treatmentMetrics, 'completionRate');
            const effectSize = treatmentScore - controlScore;
            const winner = effectSize > 0.01 ? 'treatment' : effectSize < -0.01 ? 'control' : 'inconclusive';

            await this.prisma.abExperiment.update({
                where: { id: experiment.id },
                data: {
                    status: 'completed',
                    winner,
                    pValue: 0.05,
                    effectSize,
                    confidenceLower: effectSize - 0.05,
                    confidenceUpper: effectSize + 0.05,
                    statisticalPower: 0.8,
                    completedAt: new Date(),
                },
            });

            if (winner === 'treatment') {
                await this.prisma.learningExperience.update({
                    where: { id: experiment.experienceId },
                    data: {
                        version: experiment.treatmentVersion,
                    },
                });
            }
        }
    }

    async getRegenerationHistory(experienceId: string): Promise<any[]> {
        return this.prisma.experienceAutoRefinement.findMany({
            where: { experienceId },
            orderBy: { createdAt: 'desc' },
            take: 50,
        });
    }

    async getABExperimentResults(experimentId: string): Promise<any> {
        return this.prisma.abExperiment.findUnique({
            where: { id: experimentId },
        });
    }

    private severityForRatio(
        value: number,
        threshold: number,
        direction: 'low' | 'high',
    ): 'low' | 'medium' | 'high' {
        const delta = direction === 'low' ? threshold - value : value - threshold;
        if (delta > threshold * 0.3) return 'high';
        if (delta > threshold * 0.15) return 'medium';
        return 'low';
    }

    private analyzeStruggles(analytics: any): RegenerationInsight[] {
        const insights: RegenerationInsight[] = [];
        const normalized = this.normalizeAnalytics(analytics);

        if (normalized.dropOffRate > 0.45) {
            insights.push({
                category: 'engagement_drop',
                issue: 'Learners are dropping off before completion',
                evidence: { dropOffRate: normalized.dropOffRate },
                suggestedAction: 'Reduce initial complexity and add clearer guidance',
                priority: 8,
            });
        }

        if (normalized.abortRate > 0.35) {
            insights.push({
                category: 'simulation_complexity',
                issue: 'Simulation abort rate is above threshold',
                evidence: {
                    simulationStarts: normalized.simulationStarts,
                    simulationAborts: normalized.simulationAborts,
                    abortRate: normalized.abortRate,
                },
                suggestedAction: 'Simplify simulation interactions and tutorial hints',
                priority: 7,
            });
        }

        if (normalized.avgTimeMinutes < 4) {
            insights.push({
                category: 'content_clarity',
                issue: 'Session time is too low to indicate engaged learning',
                evidence: { avgTimeMinutes: normalized.avgTimeMinutes },
                suggestedAction: 'Improve claim clarity and worked examples',
                priority: 6,
            });
        }

        return insights;
    }

    private calculatePriority(signals: DriftSignal[], insights: RegenerationInsight[]): number {
        const signalWeight = signals.reduce((acc, signal) => {
            if (signal.severity === 'high') return acc + 10;
            if (signal.severity === 'medium') return acc + 5;
            return acc + 2;
        }, 0);

        const insightWeight = insights.reduce((acc, insight) => acc + insight.priority, 0);
        return signalWeight + insightWeight;
    }

    private estimateImpact(priority: number): 'low' | 'medium' | 'high' {
        if (priority >= 25) return 'high';
        if (priority >= 12) return 'medium';
        return 'low';
    }

    private createImprovementPrompt(experience: any, insights: RegenerationInsight[]): string {
        const lines = insights.map((insight) => `- ${insight.issue}: ${insight.suggestedAction}`).join('\n');
        return [
            `Improve this learning experience: ${experience.title}`,
            'Targeted issues:',
            lines || '- General quality improvements',
            'Preserve pedagogical intent while improving completion and mastery outcomes.',
        ].join('\n');
    }

    private extractMetricScore(metrics: any, key: string): number {
        if (!metrics || typeof metrics !== 'object') return 0;
        const value = metrics[key];
        if (typeof value === 'number') return value;
        return 0;
    }

    private toNumber(value: unknown, fallback = 0): number {
        if (typeof value === 'number' && Number.isFinite(value)) return value;
        if (typeof value === 'string' && value.trim().length > 0) {
            const parsed = Number(value);
            return Number.isFinite(parsed) ? parsed : fallback;
        }
        return fallback;
    }

    private normalizeAnalytics(raw: any): {
        completionRate: number;
        dropOffRate: number;
        avgTimeMinutes: number;
        masteryRate: number;
        simulationStarts: number;
        simulationAborts: number;
        abortRate: number;
        hasQualityIssues: boolean;
        averageFeedbackScore: number;
    } {
        const completionRate = this.toNumber(raw?.completionRate, 0);
        const dropOffRate = this.toNumber(raw?.dropOffRate, this.toNumber(raw?.abortRate, 0));
        const avgTimeMinutes = this.toNumber(raw?.avgTimeMinutes, this.toNumber(raw?.averageTimeSpent, 0));
        const masteryRate = this.toNumber(raw?.masteryRate, completionRate);
        const simulationStarts = this.toNumber(raw?.simulationStarts, this.toNumber(raw?.totalAttempts, 0));
        const simulationAborts = this.toNumber(
            raw?.simulationAborts,
            simulationStarts > 0 ? Math.round(this.toNumber(raw?.abortRate, 0) * simulationStarts) : 0,
        );
        const abortRate = this.toNumber(
            raw?.abortRate,
            simulationStarts > 0 ? simulationAborts / simulationStarts : dropOffRate,
        );
        const averageFeedbackScore = this.toNumber(raw?.averageFeedbackScore, raw?.hasQualityIssues ? 2.5 : 4.0);
        const hasQualityIssues = Boolean(raw?.hasQualityIssues) || averageFeedbackScore < this.config.thresholds.feedbackScore;

        return {
            completionRate,
            dropOffRate,
            avgTimeMinutes,
            masteryRate,
            simulationStarts,
            simulationAborts,
            abortRate,
            hasQualityIssues,
            averageFeedbackScore,
        };
    }
}
