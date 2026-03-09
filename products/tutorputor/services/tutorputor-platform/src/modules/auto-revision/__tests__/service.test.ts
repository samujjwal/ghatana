/**
 * Auto-Revision Service Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for drift detection, A/B experiments, regeneration
 * @doc.layer platform
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AutoRevisionService } from '../service';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        learningExperience: {
            findUnique: vi.fn(),
            findMany: vi.fn(),
            update: vi.fn(),
        },
        experienceAnalytics: {
            findUnique: vi.fn(),
            update: vi.fn(),
        },
        experienceAutoRefinement: {
            create: vi.fn(),
            findMany: vi.fn(),
            findFirst: vi.fn(),
            update: vi.fn(),
        },
        $queryRaw: vi.fn(),
    } as unknown as PrismaClient;
}

function makeMockContentStudio() {
    return {
        refineExperience: vi.fn().mockResolvedValue({
            success: true,
            experience: {
                id: 'exp-1',
                version: 2,
                status: 'draft',
            },
        }),
    };
}

function makeAnalytics(overrides: Record<string, unknown> = {}) {
    return {
        experienceId: 'exp-1',
        completionRate: 0.8,
        abortRate: 0.1,
        averageTimeSpent: 15,
        masteryRate: 0.85,
        averageFeedbackScore: 4.0,
        totalAttempts: 200,
        ...overrides,
    };
}

describe('AutoRevisionService', () => {
    let service: AutoRevisionService;
    let prisma: ReturnType<typeof makeMockPrisma>;
    let contentStudio: ReturnType<typeof makeMockContentStudio>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        contentStudio = makeMockContentStudio();
        service = new AutoRevisionService(prisma, contentStudio as any);
    });

    // =========================================================================
    // Drift Detection
    // =========================================================================
    describe('detectDrift', () => {
        it('returns empty array when all metrics are healthy', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(makeAnalytics());

            const signals = await service.detectDrift('exp-1');
            expect(signals).toHaveLength(0);
        });

        it('detects low completion rate', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ completionRate: 0.4 }),
            );

            const signals = await service.detectDrift('exp-1');

            expect(signals.some(s => s.type === 'low_completion')).toBe(true);
            const signal = signals.find(s => s.type === 'low_completion')!;
            expect(signal.value).toBe(0.4);
            expect(signal.threshold).toBe(0.6);
        });

        it('detects high abort rate', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ abortRate: 0.5 }),
            );

            const signals = await service.detectDrift('exp-1');

            expect(signals.some(s => s.type === 'high_abort_rate')).toBe(true);
            const signal = signals.find(s => s.type === 'high_abort_rate')!;
            expect(signal.value).toBe(0.5);
        });

        it('detects engagement drop (low time spent)', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ averageTimeSpent: 2 }),
            );

            const signals = await service.detectDrift('exp-1');

            expect(signals.some(s => s.type === 'engagement_drop')).toBe(true);
        });

        it('detects low mastery rate', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ masteryRate: 0.4 }),
            );

            const signals = await service.detectDrift('exp-1');

            expect(signals.some(s => s.type === 'low_mastery')).toBe(true);
        });

        it('detects negative feedback', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ averageFeedbackScore: 2.0 }),
            );

            const signals = await service.detectDrift('exp-1');

            expect(signals.some(s => s.type === 'negative_feedback')).toBe(true);
        });

        it('detects multiple drift signals simultaneously', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({
                    completionRate: 0.3,
                    abortRate: 0.6,
                    masteryRate: 0.4,
                }),
            );

            const signals = await service.detectDrift('exp-1');

            expect(signals.length).toBeGreaterThanOrEqual(3);
        });

        it('throws when analytics not found', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(null);

            await expect(service.detectDrift('nonexistent')).rejects.toThrow('Analytics not found');
        });
    });

    // =========================================================================
    // Severity Classification
    // =========================================================================
    describe('severity classification', () => {
        it('classifies high severity for very low completion', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ completionRate: 0.1 }), // Far below 0.6 threshold
            );

            const signals = await service.detectDrift('exp-1');
            const signal = signals.find(s => s.type === 'low_completion')!;
            expect(signal.severity).toBe('high');
        });

        it('classifies medium severity for moderately low completion', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ completionRate: 0.45 }),
            );

            const signals = await service.detectDrift('exp-1');
            const signal = signals.find(s => s.type === 'low_completion')!;
            expect(['medium', 'low']).toContain(signal.severity);
        });
    });

    // =========================================================================
    // monitorDrift
    // =========================================================================
    describe('monitorDrift', () => {
        it('returns empty array when no published experiences', async () => {
            (prisma.learningExperience.findMany as any).mockResolvedValue([]);

            const candidates = await service.monitorDrift();
            expect(candidates).toHaveLength(0);
        });

        it('skips experiences without analytics', async () => {
            (prisma.learningExperience.findMany as any).mockResolvedValue([
                { id: 'exp-1', analytics: null },
            ]);

            const candidates = await service.monitorDrift();
            expect(candidates).toHaveLength(0);
        });

        it('queues candidates with high severity signals', async () => {
            (prisma.learningExperience.findMany as any).mockResolvedValue([
                {
                    id: 'exp-1',
                    analytics: makeAnalytics({ completionRate: 0.1, abortRate: 0.8 }),
                },
            ]);
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ completionRate: 0.1, abortRate: 0.8 }),
            );
            // queueForRegeneration calls experienceAutoRefinement.create
            (prisma.experienceAutoRefinement.create as any).mockResolvedValue({});

            const candidates = await service.monitorDrift();

            expect(candidates.length).toBeGreaterThanOrEqual(1);
            if (candidates.length > 0) {
                expect(candidates[0].experienceId).toBe('exp-1');
                expect(candidates[0].signals.length).toBeGreaterThanOrEqual(1);
            }
        });
    });

    // =========================================================================
    // generateImprovedVersion
    // =========================================================================
    describe('generateImprovedVersion', () => {
        it('calls content studio for regeneration', async () => {
            (prisma.learningExperience.findUnique as any).mockResolvedValue({
                id: 'exp-1',
                claims: [{ id: 'c1', text: 'Claim 1' }],
                evidence: [],
                tasks: [],
                gradeAdaptation: { gradeRange: 'grade_9_12' },
                keywords: ['physics'],
            });
            (prisma.experienceAutoRefinement.create as any).mockResolvedValue({});

            const result = await service.generateImprovedVersion('exp-1', [
                {
                    category: 'content_difficulty',
                    issue: 'Too hard',
                    evidence: {},
                    suggestedAction: 'Simplify',
                    priority: 8,
                },
            ]);

            expect(contentStudio.refineExperience).toHaveBeenCalled();
        });

        it('throws for non-existent experience', async () => {
            (prisma.learningExperience.findUnique as any).mockResolvedValue(null);

            await expect(
                service.generateImprovedVersion('nonexistent', []),
            ).rejects.toThrow('Experience not found');
        });
    });

    // =========================================================================
    // Drift signal recommendations
    // =========================================================================
    describe('signal recommendations', () => {
        it('low_completion has content difficulty recommendation', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ completionRate: 0.3 }),
            );

            const signals = await service.detectDrift('exp-1');
            const signal = signals.find(s => s.type === 'low_completion')!;
            expect(signal.recommendation).toBeTruthy();
            expect(signal.recommendation.length).toBeGreaterThan(0);
        });

        it('high_abort_rate has simplification recommendation', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ abortRate: 0.5 }),
            );

            const signals = await service.detectDrift('exp-1');
            const signal = signals.find(s => s.type === 'high_abort_rate')!;
            expect(signal.recommendation).toContain('Simplify');
        });

        it('negative_feedback has quality recommendation', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ averageFeedbackScore: 1.5 }),
            );

            const signals = await service.detectDrift('exp-1');
            const signal = signals.find(s => s.type === 'negative_feedback')!;
            expect(signal.recommendation).toContain('feedback');
        });
    });

    // =========================================================================
    // Signal detectedAt
    // =========================================================================
    describe('signal metadata', () => {
        it('includes detectedAt timestamp', async () => {
            const before = new Date();
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ completionRate: 0.3 }),
            );

            const signals = await service.detectDrift('exp-1');
            const after = new Date();

            expect(signals[0].detectedAt.getTime()).toBeGreaterThanOrEqual(before.getTime());
            expect(signals[0].detectedAt.getTime()).toBeLessThanOrEqual(after.getTime());
        });

        it('includes metric name and threshold', async () => {
            (prisma.experienceAnalytics.findUnique as any).mockResolvedValue(
                makeAnalytics({ abortRate: 0.5 }),
            );

            const signals = await service.detectDrift('exp-1');
            const signal = signals.find(s => s.type === 'high_abort_rate')!;

            expect(signal.metric).toBe('abortRate');
            expect(signal.threshold).toBe(0.3);
            expect(signal.value).toBe(0.5);
        });
    });
});
