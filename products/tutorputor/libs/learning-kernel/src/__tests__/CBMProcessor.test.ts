/**
 * CBMProcessor Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test Confidence-Based Marking scoring, aggregation, viva triggers
 * @doc.layer plugin
 * @doc.pattern UnitTest
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { CBMProcessor } from '../plugins/CBMProcessor';

function makeEvidence(overrides: Record<string, unknown> = {}): any {
    return {
        type: 'answer_submission',
        claimId: 'claim-1',
        payload: {
            correct: true,
            confidence: 0.9,
            claimId: 'claim-1',
        },
        ...overrides,
    };
}

function makeContext(overrides: Record<string, unknown> = {}): any {
    return {
        learnerId: 'learner-1',
        data: {},
        ...overrides,
    };
}

describe('CBMProcessor', () => {
    let processor: CBMProcessor;

    beforeEach(async () => {
        processor = new CBMProcessor();
        await processor.initialize();
    });

    // =========================================================================
    // Metadata
    // =========================================================================
    it('has correct metadata', () => {
        expect(processor.metadata.id).toBe('cbm-processor');
        expect(processor.metadata.type).toBe('evidence_processor');
        expect(processor.metadata.tags).toContain('cbm');
    });

    it('supports answer_submission events', () => {
        expect(processor.supports({ type: 'answer_submission' } as any)).toBe(true);
        expect(processor.supports({ type: 'video_complete' } as any)).toBe(false);
    });

    // =========================================================================
    // Canonical CBM Scoring Matrix
    // =========================================================================
    describe('CBM scoring matrix', () => {
        it('correct + high confidence = +3', async () => {
            const ctx = makeContext();
            const result = await processor.process(ctx, makeEvidence({
                payload: { correct: true, confidence: 0.9, claimId: 'claim-1' },
            }));
            expect(result.status).toBe('success');
            expect(result.data?.score).toBe(3);
        });

        it('correct + medium confidence = +2', async () => {
            const ctx = makeContext();
            const result = await processor.process(ctx, makeEvidence({
                payload: { correct: true, confidence: 0.5, claimId: 'claim-1' },
            }));
            expect(result.data?.score).toBe(2);
        });

        it('correct + low confidence = +1', async () => {
            const ctx = makeContext();
            const result = await processor.process(ctx, makeEvidence({
                payload: { correct: true, confidence: 0.2, claimId: 'claim-1' },
            }));
            expect(result.data?.score).toBe(1);
        });

        it('incorrect + high confidence = -6', async () => {
            const ctx = makeContext();
            const result = await processor.process(ctx, makeEvidence({
                payload: { correct: false, confidence: 0.9, claimId: 'claim-1' },
            }));
            expect(result.data?.score).toBe(-6);
        });

        it('incorrect + medium confidence = -2', async () => {
            const ctx = makeContext();
            const result = await processor.process(ctx, makeEvidence({
                payload: { correct: false, confidence: 0.5, claimId: 'claim-1' },
            }));
            expect(result.data?.score).toBe(-2);
        });

        it('incorrect + low confidence = 0', async () => {
            const ctx = makeContext();
            const result = await processor.process(ctx, makeEvidence({
                payload: { correct: false, confidence: 0.2, claimId: 'claim-1' },
            }));
            expect(result.data?.score).toBe(0);
        });
    });

    // =========================================================================
    // Aggregation
    // =========================================================================
    describe('aggregation', () => {
        it('accumulates session-level CBM items', async () => {
            const ctx = makeContext();

            await processor.process(ctx, makeEvidence({
                payload: { correct: true, confidence: 0.9, claimId: 'claim-1' },
            }));
            await processor.process(ctx, makeEvidence({
                payload: { correct: false, confidence: 0.9, claimId: 'claim-1' },
            }));

            const metrics = ctx.data['cbm.aggregate.metrics'];
            expect(metrics).toBeDefined();
            expect(metrics.itemCount).toBe(2);
            expect(metrics.totalScore).toBe(3 + (-6)); // +3 and -6
        });

        it('computes Brier score', async () => {
            const ctx = makeContext();

            // Correct with high confidence: (0.9 - 1)^2 = 0.01
            await processor.process(ctx, makeEvidence({
                payload: { correct: true, confidence: 0.9, claimId: 'claim-1' },
            }));

            const metrics = ctx.data['cbm.aggregate.metrics'];
            expect(metrics.brierScore).toBeCloseTo(0.01, 2);
        });

        it('computes calibration index', async () => {
            const ctx = makeContext();

            // Perfect calibration: correct=true, confidence=1.0 → delta=0
            await processor.process(ctx, makeEvidence({
                payload: { correct: true, confidence: 1.0, claimId: 'claim-1' },
            }));

            const metrics = ctx.data['cbm.aggregate.metrics'];
            expect(metrics.calibrationIndex).toBeCloseTo(0, 1);
        });
    });

    // =========================================================================
    // Viva trigger
    // =========================================================================
    describe('viva trigger', () => {
        it('triggers viva on significant miscalibration after 3+ items', async () => {
            const ctx = makeContext();

            // 3 overconfident wrong: confidence=0.9, correct=false → delta = 0.9-0 = 0.9
            for (let i = 0; i < 3; i++) {
                await processor.process(ctx, makeEvidence({
                    payload: { correct: false, confidence: 0.9, claimId: `claim-${i}` },
                }));
            }

            const vivaTrigger = ctx.data['cbm.trigger.viva'];
            expect(vivaTrigger).toBeDefined();
            expect(vivaTrigger.reason).toBe('overconfidence');
            expect(vivaTrigger.calibrationIndex).toBeGreaterThan(0.3);
        });

        it('does not trigger viva with fewer than 3 items', async () => {
            const ctx = makeContext();

            await processor.process(ctx, makeEvidence({
                payload: { correct: false, confidence: 0.9, claimId: 'claim-1' },
            }));
            await processor.process(ctx, makeEvidence({
                payload: { correct: false, confidence: 0.9, claimId: 'claim-2' },
            }));

            expect(ctx.data['cbm.trigger.viva']).toBeUndefined();
        });
    });

    // =========================================================================
    // Edge cases
    // =========================================================================
    describe('edge cases', () => {
        it('skips events without payload', async () => {
            const result = await processor.process(
                makeContext(),
                { type: 'answer_submission', payload: null } as any,
            );
            expect(result.status).toBe('skipped');
        });

        it('returns error status on exception', async () => {
            const badCtx = null as any; // Will cause error
            const result = await processor.process(
                badCtx,
                makeEvidence(),
            );
            expect(result.status).toBe('error');
        });
    });
});
