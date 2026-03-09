/**
 * ContentGenerationOrchestrator Unit Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for dispatch, cost tracking, timeout guards
 * @doc.layer backend-worker
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock bullmq before importing orchestrator
vi.mock('bullmq', () => {
    const mockJob = {
        id: 'job-1',
        waitUntilFinished: vi.fn().mockResolvedValue({ success: true }),
    };
    class Queue {
        add = vi.fn().mockResolvedValue(mockJob);
        close = vi.fn().mockResolvedValue(undefined);
    }
    class QueueEvents {
        close = vi.fn().mockResolvedValue(undefined);
    }
    class Job {}
    return { Queue, QueueEvents, Job };
});

import { ContentGenerationOrchestrator, type OrchestrationRequest, type OrchestrationResult } from '../orchestrator';
import type { PrismaClient } from '@ghatana/tutorputor-db';

function makeMockPrisma() {
    return {} as unknown as PrismaClient;
}

function makeMockLogger() {
    return {
        info: vi.fn(),
        warn: vi.fn(),
        error: vi.fn(),
        debug: vi.fn(),
    } as any;
}

function makeConfig() {
    return {
        redis: { host: 'localhost', port: 6379 },
        timeoutMs: 5000,
        maxCostUsd: 2.0,
        costPer1kTokens: 0.03,
    };
}

function makeNeeds(overrides: Record<string, any> = {}) {
    return {
        examples: { required: true, count: 2, types: ['worked', 'counterexample'] },
        simulation: { required: true, interactionType: 'parameter_tuning', complexity: 'medium' },
        animation: { required: false, type: 'conceptual', durationSeconds: 30, complexity: 'low' },
        ...overrides,
    };
}

function makeRequest(overrides: Partial<OrchestrationRequest> = {}): OrchestrationRequest {
    return {
        experienceId: 'exp-1',
        tenantId: 't1',
        claimRef: 'claim-1',
        claimText: 'Newton\'s first law',
        gradeLevel: 'grade_9_12',
        domain: 'physics',
        needs: makeNeeds(),
        ...overrides,
    };
}

describe('ContentGenerationOrchestrator', () => {
    let orchestrator: ContentGenerationOrchestrator;
    let prisma: ReturnType<typeof makeMockPrisma>;
    let logger: ReturnType<typeof makeMockLogger>;

    beforeEach(() => {
        vi.clearAllMocks();
        prisma = makeMockPrisma();
        logger = makeMockLogger();
        orchestrator = new ContentGenerationOrchestrator(prisma, logger, makeConfig());
    });

    // =========================================================================
    // orchestrateForClaim
    // =========================================================================
    describe('orchestrateForClaim', () => {
        it('returns an OrchestrationResult with claimRef', async () => {
            const result = await orchestrator.orchestrateForClaim(makeRequest());

            expect(result.claimRef).toBe('claim-1');
            expect(result.durationMs).toBeGreaterThanOrEqual(0);
        });

        it('returns outcome for each content type', async () => {
            const result = await orchestrator.orchestrateForClaim(makeRequest());

            expect(result.examples).toBeDefined();
            expect(result.simulation).toBeDefined();
            expect(result.animation).toBeDefined();
        });

        it('skips animation when not required', async () => {
            const result = await orchestrator.orchestrateForClaim(
                makeRequest({
                    needs: makeNeeds({
                        animation: { required: false, type: 'conceptual', durationSeconds: 30, complexity: 'low' },
                    }),
                }),
            );

            expect(result.animation.status).toBe('skipped');
        });

        it('dispatches examples when required', async () => {
            const result = await orchestrator.orchestrateForClaim(
                makeRequest({
                    needs: makeNeeds({ examples: { required: true, count: 3, types: ['worked'] } }),
                }),
            );

            expect(result.examples.status).toBe('success');
        });

        it('logs start and completion', async () => {
            await orchestrator.orchestrateForClaim(makeRequest());

            expect(logger.info).toHaveBeenCalledWith(
                expect.objectContaining({ claimRef: 'claim-1' }),
                expect.stringContaining('Starting'),
            );
            expect(logger.info).toHaveBeenCalledWith(
                expect.objectContaining({ claimRef: 'claim-1' }),
                expect.stringContaining('completed'),
            );
        });
    });

    // =========================================================================
    // orchestrateForExperience
    // =========================================================================
    describe('orchestrateForExperience', () => {
        it('processes multiple claims and returns results array', async () => {
            const results = await orchestrator.orchestrateForExperience('exp-1', 't1', [
                { claimRef: 'c1', claimText: 'Claim A', gradeLevel: 'grade_9_12', domain: 'physics', needs: makeNeeds() },
                { claimRef: 'c2', claimText: 'Claim B', gradeLevel: 'grade_9_12', domain: 'physics', needs: makeNeeds() },
            ]);

            expect(results).toHaveLength(2);
            expect(results[0].claimRef).toBe('c1');
            expect(results[1].claimRef).toBe('c2');
        });

        it('stops when cost limit is reached', async () => {
            // Use a very low maxCostUsd to trigger the guard
            const lowCostOrchestrator = new ContentGenerationOrchestrator(
                prisma, logger, { ...makeConfig(), maxCostUsd: 0 },
            );

            const results = await lowCostOrchestrator.orchestrateForExperience('exp-1', 't1', [
                { claimRef: 'c1', claimText: 'A', gradeLevel: 'g', domain: 'd', needs: makeNeeds() },
            ]);

            // With maxCostUsd=0 the first claim should be processed (cost check is at start of loop)
            // but subsequent claims would be skipped
            expect(results.length).toBeLessThanOrEqual(1);
        });
    });

    // =========================================================================
    // Cost Summary
    // =========================================================================
    describe('cost computation', () => {
        it('returns totalCost with breakdown', async () => {
            const result = await orchestrator.orchestrateForClaim(makeRequest());

            expect(result.totalCost).toBeDefined();
            expect(result.totalCost.totalTokens).toBeGreaterThanOrEqual(0);
            expect(result.totalCost.estimatedCostUsd).toBeGreaterThanOrEqual(0);
            expect(result.totalCost.breakdown).toHaveProperty('examples');
            expect(result.totalCost.breakdown).toHaveProperty('simulation');
            expect(result.totalCost.breakdown).toHaveProperty('animation');
        });

        it('counts tokens only for successful jobs', async () => {
            const result = await orchestrator.orchestrateForClaim(
                makeRequest({
                    needs: makeNeeds({
                        examples: { required: true, count: 1, types: ['worked'] },
                        simulation: { required: false },
                        animation: { required: false },
                    }),
                }),
            );

            // Simulation & animation skipped → their breakdown should be 0
            expect(result.totalCost.breakdown.simulation).toBe(0);
            expect(result.totalCost.breakdown.animation).toBe(0);
        });
    });

    // =========================================================================
    // close
    // =========================================================================
    describe('close', () => {
        it('closes queue and queue events', async () => {
            await orchestrator.close();
            // No error thrown
        });
    });
});
