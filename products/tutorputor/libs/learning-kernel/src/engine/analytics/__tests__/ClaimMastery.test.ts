/**
 * ClaimMastery Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test claim mastery calculation, evidence weighting, calibration
 * @doc.layer core
 * @doc.pattern UnitTest
 */
import { describe, it, expect } from 'vitest';
import { ClaimMasteryCalculator, type EvidenceRecord } from '../ClaimMastery';

describe('ClaimMasteryCalculator', () => {
    const calc = new ClaimMasteryCalculator();

    // =========================================================================
    // CBM Score
    // =========================================================================
    describe('calculateCBMScore', () => {
        it('correct + high = +3', () => {
            expect(calc.calculateCBMScore(true, 'high')).toBe(3);
        });

        it('correct + medium = +2', () => {
            expect(calc.calculateCBMScore(true, 'medium')).toBe(2);
        });

        it('correct + low = +1', () => {
            expect(calc.calculateCBMScore(true, 'low')).toBe(1);
        });

        it('incorrect + high = -6', () => {
            expect(calc.calculateCBMScore(false, 'high')).toBe(-6);
        });

        it('incorrect + medium = -2', () => {
            expect(calc.calculateCBMScore(false, 'medium')).toBe(-2);
        });

        it('incorrect + low = 0', () => {
            expect(calc.calculateCBMScore(false, 'low')).toBe(0);
        });
    });

    // =========================================================================
    // Normalize CBM Score
    // =========================================================================
    describe('normalizeCBMScore', () => {
        it('normalizes +3 to 1.0', () => {
            expect(calc.normalizeCBMScore(3)).toBe(1);
        });

        it('normalizes -6 to 0', () => {
            expect(calc.normalizeCBMScore(-6)).toBe(0);
        });

        it('normalizes 0 to approximately 0.67', () => {
            const normalized = calc.normalizeCBMScore(0);
            expect(normalized).toBeCloseTo(0.67, 1);
        });
    });

    // =========================================================================
    // Parameter Targeting Score
    // =========================================================================
    describe('calculateParameterTargetingScore', () => {
        it('returns 0.2 when goal not achieved', () => {
            expect(calc.calculateParameterTargetingScore(false, 5, 0.5, 1)).toBe(0.2);
        });

        it('returns high score for first-attempt accurate targeting', () => {
            const score = calc.calculateParameterTargetingScore(true, 1, 0.01, 1);
            expect(score).toBeGreaterThan(0.9);
        });

        it('penalizes more attempts', () => {
            const fast = calc.calculateParameterTargetingScore(true, 1, 0.1, 1);
            const slow = calc.calculateParameterTargetingScore(true, 5, 0.1, 1);
            expect(fast).toBeGreaterThan(slow);
        });

        it('penalizes higher RMSE', () => {
            const accurate = calc.calculateParameterTargetingScore(true, 1, 0.1, 1);
            const inaccurate = calc.calculateParameterTargetingScore(true, 1, 0.8, 1);
            expect(accurate).toBeGreaterThan(inaccurate);
        });
    });

    // =========================================================================
    // Explanation Score
    // =========================================================================
    describe('calculateExplanationScore', () => {
        it('returns ratio of rubric to max', () => {
            expect(calc.calculateExplanationScore(4, 5)).toBeCloseTo(0.8);
        });

        it('returns 1.0 for perfect score', () => {
            expect(calc.calculateExplanationScore(10, 10)).toBe(1);
        });

        it('returns 0 for zero rubric score', () => {
            expect(calc.calculateExplanationScore(0, 10)).toBe(0);
        });
    });

    // =========================================================================
    // Full Claim Mastery
    // =========================================================================
    describe('calculateClaimMastery', () => {
        it('returns zero mastery with no evidence', () => {
            const result = calc.calculateClaimMastery('L1', 'LU1', 'C1', []);
            expect(result.masteryScore).toBe(0);
            expect(result.totalAttempts).toBe(0);
            expect(result.confidenceCalibration).toBeNull();
        });

        it('calculates weighted mastery from mixed evidence types', () => {
            const evidence: EvidenceRecord[] = [
                {
                    evidenceId: 'e1',
                    claimId: 'C1',
                    type: 'prediction_vs_outcome',
                    correct: true,
                    confidence: 'high',
                },
                {
                    evidenceId: 'e2',
                    claimId: 'C1',
                    type: 'parameter_targeting',
                    goalAchieved: true,
                    attempts: 1,
                    rmse: 0.05,
                    tolerance: 1,
                },
                {
                    evidenceId: 'e3',
                    claimId: 'C1',
                    type: 'explanation_quality',
                    rubricScore: 8,
                    maxRubricScore: 10,
                },
            ];

            const result = calc.calculateClaimMastery('L1', 'LU1', 'C1', evidence);

            expect(result.masteryScore).toBeGreaterThan(0);
            expect(result.masteryScore).toBeLessThanOrEqual(1);
            expect(Object.keys(result.evidenceScores)).toHaveLength(3);
        });

        it('filters evidence by claimId', () => {
            const evidence: EvidenceRecord[] = [
                {
                    evidenceId: 'e1',
                    claimId: 'C1',
                    type: 'prediction_vs_outcome',
                    correct: true,
                    confidence: 'high',
                },
                {
                    evidenceId: 'e2',
                    claimId: 'C2', // different claim
                    type: 'prediction_vs_outcome',
                    correct: false,
                    confidence: 'high',
                },
            ];

            const result = calc.calculateClaimMastery('L1', 'LU1', 'C1', evidence);

            // Should only include e1
            expect(Object.keys(result.evidenceScores)).toHaveLength(1);
            expect(result.evidenceScores['e1']).toBeDefined();
        });

        it('accumulates attempts from parameter targeting', () => {
            const evidence: EvidenceRecord[] = [
                {
                    evidenceId: 'e1',
                    claimId: 'C1',
                    type: 'parameter_targeting',
                    goalAchieved: true,
                    attempts: 3,
                    rmse: 0.1,
                    tolerance: 1,
                },
                {
                    evidenceId: 'e2',
                    claimId: 'C1',
                    type: 'parameter_targeting',
                    goalAchieved: true,
                    attempts: 2,
                    rmse: 0.05,
                    tolerance: 1,
                },
            ];

            const result = calc.calculateClaimMastery('L1', 'LU1', 'C1', evidence);
            expect(result.totalAttempts).toBe(5);
        });
    });

    // =========================================================================
    // Confidence Calibration
    // =========================================================================
    describe('confidence calibration', () => {
        it('returns null with fewer than 3 predictions', () => {
            const evidence: EvidenceRecord[] = [
                { evidenceId: 'e1', claimId: 'C1', type: 'prediction_vs_outcome', correct: true, confidence: 'high' },
                { evidenceId: 'e2', claimId: 'C1', type: 'prediction_vs_outcome', correct: false, confidence: 'low' },
            ];

            const result = calc.calculateClaimMastery('L1', 'LU1', 'C1', evidence);
            expect(result.confidenceCalibration).toBeNull();
        });

        it('returns ~1.0 for well-calibrated learner', () => {
            const evidence: EvidenceRecord[] = [
                // High confidence, all correct (expected ~90% correct)
                { evidenceId: 'e1', claimId: 'C1', type: 'prediction_vs_outcome', correct: true, confidence: 'high' },
                { evidenceId: 'e2', claimId: 'C1', type: 'prediction_vs_outcome', correct: true, confidence: 'high' },
                { evidenceId: 'e3', claimId: 'C1', type: 'prediction_vs_outcome', correct: true, confidence: 'high' },
            ];

            const result = calc.calculateClaimMastery('L1', 'LU1', 'C1', evidence);
            // 100% accuracy at high confidence (expected 90%) → small gap = high calibration
            expect(result.confidenceCalibration).toBeGreaterThan(0.8);
        });

        it('returns lower score for overconfident learner', () => {
            const evidence: EvidenceRecord[] = [
                { evidenceId: 'e1', claimId: 'C1', type: 'prediction_vs_outcome', correct: false, confidence: 'high' },
                { evidenceId: 'e2', claimId: 'C1', type: 'prediction_vs_outcome', correct: false, confidence: 'high' },
                { evidenceId: 'e3', claimId: 'C1', type: 'prediction_vs_outcome', correct: false, confidence: 'high' },
            ];

            const result = calc.calculateClaimMastery('L1', 'LU1', 'C1', evidence);
            // 0% accuracy at high confidence (expected 90%) → large gap
            expect(result.confidenceCalibration).toBeLessThan(0.3);
        });
    });
});
