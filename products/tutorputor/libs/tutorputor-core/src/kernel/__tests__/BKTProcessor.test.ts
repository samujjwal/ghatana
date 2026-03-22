/**
 * BKT Processor Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test Bayesian Knowledge Tracing mastery estimation
 * @doc.layer plugin
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { BKTProcessor } from '../plugins/BKTProcessor';

// Minimal mock types matching the EvidenceProcessor contract
function makeEvidence(overrides: Record<string, unknown> = {}): any {
    return {
        type: 'answer_submission',
        claimId: 'claim-1',
        payload: { correct: true, claimId: 'claim-1' },
        ...overrides,
    };
}

function makeContext(overrides: Record<string, unknown> = {}): any {
    return { learnerId: 'learner-1', ...overrides };
}

describe('BKTProcessor', () => {
    let processor: BKTProcessor;

    beforeEach(async () => {
        processor = new BKTProcessor({
            defaultParams: { pInit: 0.3, pTransit: 0.1, pGuess: 0.2, pSlip: 0.1 },
            masteryThreshold: 0.95,
        });
        await processor.initialize();
    });

    it('has correct metadata', () => {
        expect(processor.metadata.id).toBe('bkt-processor');
        expect(processor.metadata.type).toBe('evidence_processor');
        expect(processor.metadata.tags).toContain('bayesian');
    });

    it('supports answer_submission and assessment_response events', () => {
        expect(processor.supports({ type: 'answer_submission' } as any)).toBe(true);
        expect(processor.supports({ type: 'assessment_response' } as any)).toBe(true);
        expect(processor.supports({ type: 'sim_goal_achieved' } as any)).toBe(true);
        expect(processor.supports({ type: 'video_complete' } as any)).toBe(false);
    });

    it('increases P(L) after correct responses', async () => {
        const ctx = makeContext();

        const r1 = await processor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        expect(r1.status).toBe('success');
        const p1 = processor.getPLearned('learner-1', 'claim-1');
        expect(p1).toBeGreaterThan(0.3); // Should increase from pInit

        await processor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        const p2 = processor.getPLearned('learner-1', 'claim-1');
        expect(p2).toBeGreaterThan(p1); // Should continue increasing
    });

    it('P(L) decreases after incorrect responses (relative to continued correct)', async () => {
        const ctx = makeContext();

        // One correct to establish baseline
        await processor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        const pAfterCorrect = processor.getPLearned('learner-1', 'claim-1');

        // Now incorrect
        await processor.process(ctx, makeEvidence({ payload: { correct: false, claimId: 'claim-1' } }));
        const pAfterIncorrect = processor.getPLearned('learner-1', 'claim-1');

        // P(L) should still increase due to transit, but less than if it were correct
        // The key property: after an incorrect, the posterior drops before transit lifts it
        // We verify it's lower than what two corrects would give
        const processor2 = new BKTProcessor({
            defaultParams: { pInit: 0.3, pTransit: 0.1, pGuess: 0.2, pSlip: 0.1 },
            masteryThreshold: 0.95,
        });
        await processor2.initialize();
        await processor2.process(makeContext(), makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        await processor2.process(makeContext(), makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        const pTwoCorrects = processor2.getPLearned('learner-1', 'claim-1');

        expect(pAfterIncorrect).toBeLessThan(pTwoCorrects);
    });

    it('reaches mastery after enough correct responses', async () => {
        const ctx = makeContext();

        expect(processor.isMastered('learner-1', 'claim-1')).toBe(false);

        // Feed many correct responses
        for (let i = 0; i < 30; i++) {
            await processor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        }

        expect(processor.isMastered('learner-1', 'claim-1')).toBe(true);
        expect(processor.getPLearned('learner-1', 'claim-1')).toBeGreaterThanOrEqual(0.95);
    });

    it('tracks separate skills independently', async () => {
        const ctx = makeContext();

        // claim-1: all correct
        for (let i = 0; i < 10; i++) {
            await processor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        }
        // claim-2: all incorrect
        for (let i = 0; i < 10; i++) {
            await processor.process(ctx, makeEvidence({
                type: 'answer_submission',
                claimId: 'claim-2',
                payload: { correct: false, claimId: 'claim-2' },
            }));
        }

        expect(processor.getPLearned('learner-1', 'claim-1'))
            .toBeGreaterThan(processor.getPLearned('learner-1', 'claim-2'));
    });

    it('skips events without payload', async () => {
        const result = await processor.process(makeContext(), { type: 'answer_submission', payload: null } as any);
        expect(result.status).toBe('skipped');
    });

    it('skips events without learnerId or claimId', async () => {
        const result = await processor.process(
            {} as any,
            makeEvidence({ payload: { correct: true } }) // no claimId in payload
        );
        // claimId comes from evidence.claimId which is set
        // but learnerId is missing from both evidence and context
        const result2 = await processor.process(
            {} as any,
            { type: 'answer_submission', payload: { correct: true, claimId: 'c1' } } as any,
        );
        expect(result2.status).toBe('skipped');
    });

    it('P(L) stays in [0, 1]', async () => {
        const ctx = makeContext();

        // Many incorrect responses
        for (let i = 0; i < 50; i++) {
            await processor.process(ctx, makeEvidence({ payload: { correct: false, claimId: 'claim-1' } }));
        }
        const pLow = processor.getPLearned('learner-1', 'claim-1');
        expect(pLow).toBeGreaterThanOrEqual(0);
        expect(pLow).toBeLessThanOrEqual(1);

        // Many correct responses
        for (let i = 0; i < 100; i++) {
            await processor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        }
        const pHigh = processor.getPLearned('learner-1', 'claim-1');
        expect(pHigh).toBeGreaterThanOrEqual(0);
        expect(pHigh).toBeLessThanOrEqual(1);
    });

    it('respects per-skill parameter overrides', async () => {
        const customProcessor = new BKTProcessor({
            defaultParams: { pInit: 0.3, pTransit: 0.1, pGuess: 0.2, pSlip: 0.1 },
            masteryThreshold: 0.95,
            skillParams: {
                'hard-skill': { pInit: 0.05, pTransit: 0.05 },
            },
        });
        await customProcessor.initialize();

        const ctx = makeContext();
        await customProcessor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'hard-skill' } }));
        const pHard = customProcessor.getPLearned('learner-1', 'hard-skill');

        await customProcessor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'easy-skill' } }));
        const pEasy = customProcessor.getPLearned('learner-1', 'easy-skill');

        // Hard skill starts lower (pInit=0.05) so should be lower after one correct
        expect(pHard).toBeLessThan(pEasy);
    });

    it('shutdown clears state', async () => {
        const ctx = makeContext();
        await processor.process(ctx, makeEvidence({ payload: { correct: true, claimId: 'claim-1' } }));
        expect(processor.getPLearned('learner-1', 'claim-1')).toBeGreaterThan(0.3);

        await processor.shutdown();
        // After shutdown, should return default pInit
        expect(processor.getPLearned('learner-1', 'claim-1')).toBe(0.3);
    });
});
