/**
 * CBM Canonical Scoring Regression Tests
 *
 * Verifies the Gardner-Medwin 2006 CBM+ scoring matrix values
 * and the normalization/utility functions remain correct.
 *
 * @doc.type test
 * @doc.purpose Regression test for canonical CBM scoring constants
 * @doc.layer contracts
 * @doc.pattern UnitTest
 */

import { describe, it, expect } from 'vitest';
import {
    CANONICAL_CBM_SCORING,
    getCBMScore,
    normalizeCBMScore,
} from '../v1/learning-unit';

describe('CANONICAL_CBM_SCORING', () => {
    it('correct + high confidence = +3', () => {
        expect(CANONICAL_CBM_SCORING.correctHighConfidence).toBe(3);
    });

    it('correct + medium confidence = +2', () => {
        expect(CANONICAL_CBM_SCORING.correctMediumConfidence).toBe(2);
    });

    it('correct + low confidence = +1', () => {
        expect(CANONICAL_CBM_SCORING.correctLowConfidence).toBe(1);
    });

    it('incorrect + high confidence = -6', () => {
        expect(CANONICAL_CBM_SCORING.incorrectHighConfidence).toBe(-6);
    });

    it('incorrect + medium confidence = -2', () => {
        expect(CANONICAL_CBM_SCORING.incorrectMediumConfidence).toBe(-2);
    });

    it('incorrect + low confidence = 0', () => {
        expect(CANONICAL_CBM_SCORING.incorrectLowConfidence).toBe(0);
    });
});

describe('getCBMScore', () => {
    it('returns correct scores for all 6 combinations', () => {
        expect(getCBMScore(true, 'high')).toBe(3);
        expect(getCBMScore(true, 'medium')).toBe(2);
        expect(getCBMScore(true, 'low')).toBe(1);
        expect(getCBMScore(false, 'high')).toBe(-6);
        expect(getCBMScore(false, 'medium')).toBe(-2);
        expect(getCBMScore(false, 'low')).toBe(0);
    });

    it('incentivizes correct + high confidence the most', () => {
        const best = getCBMScore(true, 'high');
        expect(best).toBeGreaterThan(getCBMScore(true, 'medium'));
        expect(best).toBeGreaterThan(getCBMScore(true, 'low'));
    });

    it('penalizes incorrect + high confidence the most', () => {
        const worst = getCBMScore(false, 'high');
        expect(worst).toBeLessThan(getCBMScore(false, 'medium'));
        expect(worst).toBeLessThan(getCBMScore(false, 'low'));
    });

    it('incorrect + low confidence is neutral (0)', () => {
        expect(getCBMScore(false, 'low')).toBe(0);
    });

    it('asymmetry: penalty for wrong+high > reward for correct+high', () => {
        const reward = getCBMScore(true, 'high');   // +3
        const penalty = getCBMScore(false, 'high');  // -6
        expect(Math.abs(penalty)).toBeGreaterThan(reward);
    });
});

describe('normalizeCBMScore', () => {
    it('normalizes -6 to 0', () => {
        expect(normalizeCBMScore(-6)).toBeCloseTo(0);
    });

    it('normalizes +3 to 1', () => {
        expect(normalizeCBMScore(3)).toBeCloseTo(1);
    });

    it('normalizes 0 to approximately 0.667', () => {
        // 0 is 6 out of 9 range units from -6
        expect(normalizeCBMScore(0)).toBeCloseTo(6 / 9, 3);
    });

    it('returns values in [0, 1] for all valid scores', () => {
        for (const score of [-6, -2, 0, 1, 2, 3]) {
            const normalized = normalizeCBMScore(score);
            expect(normalized).toBeGreaterThanOrEqual(0);
            expect(normalized).toBeLessThanOrEqual(1);
        }
    });

    it('is monotonically increasing', () => {
        const scores = [-6, -2, 0, 1, 2, 3];
        const normalized = scores.map(normalizeCBMScore);
        for (let i = 1; i < normalized.length; i++) {
            expect(normalized[i]).toBeGreaterThan(normalized[i - 1]!);
        }
    });

    it('clamps values below -6 to 0', () => {
        expect(normalizeCBMScore(-10)).toBe(0);
    });

    it('clamps values above +3 to 1', () => {
        expect(normalizeCBMScore(5)).toBe(1);
    });
});
