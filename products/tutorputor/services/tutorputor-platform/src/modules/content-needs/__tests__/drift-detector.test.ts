/**
 * ContentDriftDetector Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test drift signal detection, severity classification, insights
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ContentDriftDetector, type DriftThresholds } from '../drift-detector';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {
        enrollment: { findMany: vi.fn() },
        assessmentAttempt: { findMany: vi.fn() },
        learningExperience: { findMany: vi.fn() },
        driftSignal: { create: vi.fn() },
        regenerationInsight: { create: vi.fn() },
    } as unknown as PrismaClient;
}

function makeEnrolments(count: number, statusMap: Record<string, number> = {}) {
    const enrolments: Array<{ status: string; userId: string }> = [];
    let idx = 0;
    for (const [status, n] of Object.entries(statusMap)) {
        for (let i = 0; i < n; i++) {
            enrolments.push({ status, userId: `user-${idx++}` });
        }
    }
    // Fill remaining with ACTIVE
    while (enrolments.length < count) {
        enrolments.push({ status: 'ACTIVE', userId: `user-${idx++}` });
    }
    return enrolments;
}

describe('ContentDriftDetector', () => {
    let detector: ContentDriftDetector;
    let prisma: ReturnType<typeof makeMockPrisma>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        detector = new ContentDriftDetector(prisma);
    });

    // =========================================================================
    // Skip insufficient data
    // =========================================================================
    describe('minimum learner threshold', () => {
        it('returns no signals when learner count < minLearnerCount', async () => {
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(5, { COMPLETED: 1, DROPPED: 4 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const result = await detector.scanExperience('t1', 'exp-1');

            expect(result.signals).toHaveLength(0);
            expect(result.insights).toHaveLength(0);
        });
    });

    // =========================================================================
    // Low completion signal
    // =========================================================================
    describe('low_completion signal', () => {
        it('detects low completion rate', async () => {
            // 20 learners: 5 completed, 15 active → 25% completion
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 5 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const result = await detector.scanExperience('t1', 'exp-1');

            const signal = result.signals.find((s) => s.signalType === 'low_completion');
            expect(signal).toBeDefined();
            expect(signal!.value).toBeCloseTo(0.25);
            expect(signal!.threshold).toBe(0.6);
            expect(signal!.recommendation).toBeDefined();
        });

        it('does not fire when completion rate is above threshold', async () => {
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 15 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const result = await detector.scanExperience('t1', 'exp-1');

            expect(result.signals.find((s) => s.signalType === 'low_completion')).toBeUndefined();
        });
    });

    // =========================================================================
    // High abort rate signal
    // =========================================================================
    describe('high_abort_rate signal', () => {
        it('detects high abort rate', async () => {
            // 20 learners: 10 dropped/aborted → 50% abort
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { DROPPED: 5, ABORTED: 5, COMPLETED: 10 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const result = await detector.scanExperience('t1', 'exp-1');

            const signal = result.signals.find((s) => s.signalType === 'high_abort_rate');
            expect(signal).toBeDefined();
            expect(signal!.value).toBe(0.5);
        });
    });

    // =========================================================================
    // Low mastery signal
    // =========================================================================
    describe('low_mastery signal', () => {
        it('detects low average mastery from assessment scores', async () => {
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 10 }),
            );
            // Average score 30%
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([
                { scorePercent: 20 },
                { scorePercent: 30 },
                { scorePercent: 40 },
            ]);

            const result = await detector.scanExperience('t1', 'exp-1');

            const signal = result.signals.find((s) => s.signalType === 'low_mastery');
            expect(signal).toBeDefined();
            expect(signal!.value).toBeCloseTo(0.3, 1);
        });
    });

    // =========================================================================
    // Severity classification
    // =========================================================================
    describe('severity classification', () => {
        it('classifies high severity when metric far below threshold', async () => {
            // completion = 10% vs threshold = 60% → delta = 0.5, step=0.1, >0.2 → high
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 2 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const result = await detector.scanExperience('t1', 'exp-1');

            const signal = result.signals.find((s) => s.signalType === 'low_completion');
            expect(signal).toBeDefined();
            expect(signal!.severity).toBe('high');
        });
    });

    // =========================================================================
    // Insights generation
    // =========================================================================
    describe('insights', () => {
        it('generates insights only for high-severity signals', async () => {
            // Very low completion → high severity → insight generated
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 1, DROPPED: 15 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const result = await detector.scanExperience('t1', 'exp-1');

            expect(result.insights.length).toBeGreaterThan(0);
            for (const insight of result.insights) {
                expect(insight.priority).toBe(9); // high priority
                expect(insight.category).toBeDefined();
                expect(insight.suggestedAction).toBeDefined();
            }
        });
    });

    // =========================================================================
    // Persistence
    // =========================================================================
    describe('persistence', () => {
        it('persists drift signals to database', async () => {
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 2, DROPPED: 15 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            await detector.scanExperience('t1', 'exp-1');

            expect(prisma.driftSignal.create).toHaveBeenCalled();
        });

        it('persists regeneration insights to database', async () => {
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 1, DROPPED: 15 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            await detector.scanExperience('t1', 'exp-1');

            expect(prisma.regenerationInsight.create).toHaveBeenCalled();
        });
    });

    // =========================================================================
    // Custom thresholds
    // =========================================================================
    describe('custom thresholds', () => {
        it('respects custom thresholds', async () => {
            const strictDetector = new ContentDriftDetector(prisma, {
                minCompletionRate: 0.9,
                minLearnerCount: 5,
            });

            // 10 learners, 7 completed → 70% < 90% custom threshold
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(10, { COMPLETED: 7 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const result = await strictDetector.scanExperience('t1', 'exp-1');

            expect(result.signals.find((s) => s.signalType === 'low_completion')).toBeDefined();
        });
    });

    // =========================================================================
    // scanTenant
    // =========================================================================
    describe('scanTenant', () => {
        it('scans all published experiences', async () => {
            (prisma.learningExperience.findMany as any).mockResolvedValue([
                { id: 'exp-1' },
                { id: 'exp-2' },
            ]);
            // Make both have signals
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(20, { COMPLETED: 2, DROPPED: 10 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const results = await detector.scanTenant('t1');

            expect(results.length).toBe(2);
            expect(results[0].experienceId).toBe('exp-1');
            expect(results[1].experienceId).toBe('exp-2');
        });

        it('only returns results with signals', async () => {
            (prisma.learningExperience.findMany as any).mockResolvedValue([
                { id: 'exp-1' },
                { id: 'exp-2' },
            ]);
            // exp-1: 5 learners (below minimum) → no signals
            // exp-2: 5 learners (below minimum) → no signals
            (prisma.enrollment.findMany as any).mockResolvedValue(
                makeEnrolments(5, { COMPLETED: 3 }),
            );
            (prisma.assessmentAttempt.findMany as any).mockResolvedValue([]);

            const results = await detector.scanTenant('t1');

            expect(results).toHaveLength(0);
        });
    });

    // =========================================================================
    // scanDurationMs
    // =========================================================================
    describe('timing', () => {
        it('reports scan duration', async () => {
            (prisma.enrollment.findMany as any).mockResolvedValue([]);

            const result = await detector.scanExperience('t1', 'exp-1');

            expect(result.scanDurationMs).toBeGreaterThanOrEqual(0);
        });
    });
});
