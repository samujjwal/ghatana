/**
 * IRT Processor Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test Item Response Theory (2PL) ability estimation
 * @doc.layer plugin
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { IRTProcessor } from '../plugins/IRTProcessor';

function makeEvidence(overrides: Record<string, unknown> = {}): any {
    return {
        type: 'answer_submission',
        claimId: 'claim-1',
        payload: { correct: true, claimId: 'claim-1', itemId: 'item-1' },
        ...overrides,
    };
}

function makeContext(overrides: Record<string, unknown> = {}): any {
    return { learnerId: 'learner-1', ...overrides };
}

describe('IRTProcessor', () => {
    let processor: IRTProcessor;

    beforeEach(async () => {
        processor = new IRTProcessor({
            defaultDiscrimination: 1.0,
            defaultDifficulty: 0.0,
            initialAbility: 0.0,
            learningRate: 1.0,
            maxIterations: 20,
            convergenceTol: 0.001,
        });
        await processor.initialize();
    });

    it('has correct metadata', () => {
        expect(processor.metadata.id).toBe('irt-processor');
        expect(processor.metadata.type).toBe('evidence_processor');
        expect(processor.metadata.tags).toContain('irt');
    });

    it('supports answer_submission and assessment_response', () => {
        expect(processor.supports({ type: 'answer_submission' } as any)).toBe(true);
        expect(processor.supports({ type: 'assessment_response' } as any)).toBe(true);
        expect(processor.supports({ type: 'sim_goal_achieved' } as any)).toBe(false);
    });

    it('ability increases after correct responses', async () => {
        const ctx = makeContext();
        const initial = processor.getAbility('learner-1');
        expect(initial).toBe(0.0);

        await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-1' },
        }));
        const after1 = processor.getAbility('learner-1');
        expect(after1).toBeGreaterThan(0.0);

        await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-2' },
        }));
        const after2 = processor.getAbility('learner-1');
        expect(after2).toBeGreaterThan(after1);
    });

    it('ability decreases after incorrect responses', async () => {
        const ctx = makeContext();

        // First get ability above 0 with a correct answer
        await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-1' },
        }));
        const afterCorrect = processor.getAbility('learner-1');

        // Then incorrect
        await processor.process(ctx, makeEvidence({
            payload: { correct: false, claimId: 'claim-1', itemId: 'item-2' },
        }));
        const afterIncorrect = processor.getAbility('learner-1');

        expect(afterIncorrect).toBeLessThan(afterCorrect);
    });

    it('ability stays bounded in [-4, 4]', async () => {
        const ctx = makeContext();

        // Many correct responses
        for (let i = 0; i < 50; i++) {
            await processor.process(ctx, makeEvidence({
                payload: { correct: true, claimId: 'claim-1', itemId: `item-c-${i}` },
            }));
        }
        expect(processor.getAbility('learner-1')).toBeLessThanOrEqual(4);
        expect(processor.getAbility('learner-1')).toBeGreaterThanOrEqual(-4);

        // Reset and many incorrect
        await processor.initialize();
        for (let i = 0; i < 50; i++) {
            await processor.process(ctx, makeEvidence({
                payload: { correct: false, claimId: 'claim-1', itemId: `item-i-${i}` },
            }));
        }
        expect(processor.getAbility('learner-1')).toBeGreaterThanOrEqual(-4);
        expect(processor.getAbility('learner-1')).toBeLessThanOrEqual(4);
    });

    it('returns predicted probability in result', async () => {
        const ctx = makeContext();
        const result = await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-1' },
        }));

        expect(result.status).toBe('success');
        expect(result.data).toBeDefined();
        const data = result.data as Record<string, unknown>;
        expect(data.predictedProbability).toBeGreaterThanOrEqual(0);
        expect(data.predictedProbability).toBeLessThanOrEqual(1);
    });

    it('standard error decreases with more responses', async () => {
        const ctx = makeContext();

        const r1 = await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-1' },
        }));
        const se1 = (r1.data as Record<string, unknown>).standardError as number;

        const r2 = await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-2' },
        }));
        const se2 = (r2.data as Record<string, unknown>).standardError as number;

        expect(se2).toBeLessThan(se1);
    });

    it('uses pre-calibrated item bank', async () => {
        const calibrated = new IRTProcessor({
            itemBank: [
                { itemId: 'easy-item', discrimination: 1.5, difficulty: -2.0 },
                { itemId: 'hard-item', discrimination: 1.5, difficulty: 2.0 },
            ],
        });
        await calibrated.initialize();

        expect(calibrated.getItemParams('easy-item')?.difficulty).toBe(-2.0);
        expect(calibrated.getItemParams('hard-item')?.difficulty).toBe(2.0);
    });

    it('selectNextItem picks the most informative item', async () => {
        const calibrated = new IRTProcessor({
            initialAbility: 0.0,
            itemBank: [
                { itemId: 'far-easy', discrimination: 1.0, difficulty: -3.0 },
                { itemId: 'matched', discrimination: 1.0, difficulty: 0.0 },
                { itemId: 'far-hard', discrimination: 1.0, difficulty: 3.0 },
            ],
        });
        await calibrated.initialize();

        // At ability=0, the item with difficulty=0 has max information
        const next = calibrated.selectNextItem('learner-1', ['far-easy', 'matched', 'far-hard']);
        expect(next).toBe('matched');
    });

    it('selectNextItem returns null for empty list', async () => {
        const next = processor.selectNextItem('learner-1', []);
        expect(next).toBeNull();
    });

    it('skips events without payload', async () => {
        const result = await processor.process(makeContext(), { type: 'answer_submission', payload: null } as any);
        expect(result.status).toBe('skipped');
    });

    it('tracks response count', async () => {
        const ctx = makeContext();

        const r1 = await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-1' },
        }));
        expect((r1.data as Record<string, unknown>).responseCount).toBe(1);

        const r2 = await processor.process(ctx, makeEvidence({
            payload: { correct: false, claimId: 'claim-1', itemId: 'item-2' },
        }));
        expect((r2.data as Record<string, unknown>).responseCount).toBe(2);
    });

    it('shutdown clears all state', async () => {
        const ctx = makeContext();
        await processor.process(ctx, makeEvidence({
            payload: { correct: true, claimId: 'claim-1', itemId: 'item-1' },
        }));
        expect(processor.getAbility('learner-1')).not.toBe(0.0);

        await processor.shutdown();
        expect(processor.getAbility('learner-1')).toBe(0.0);
        expect(processor.getItemParams('item-1')).toBeUndefined();
    });
});
