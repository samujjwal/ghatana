/**
 * Claim Mastery Calculator
 * 
 * Computes mastery scores for claims based on evidence collection,
 * confidence-based marking, and process metrics.
 * 
 * @doc.type class
 * @doc.purpose Calculate claim mastery from evidence
 * @doc.layer core
 * @doc.pattern Service
 */

import {
    type ConfidenceLevel,
    type ClaimMasteryScore,
    type EvidenceType,
    getCBMScore,
    normalizeCBMScore,
    CANONICAL_EVIDENCE_WEIGHTS,
} from '@ghatana/tutorputor-contracts/v1/learning-unit';

/**
 * Raw evidence record from telemetry
 */
export interface EvidenceRecord {
    evidenceId: string;
    claimId: string;
    type: EvidenceType;
    correct?: boolean;
    confidence?: ConfidenceLevel;
    goalAchieved?: boolean;
    attempts?: number;
    rmse?: number;
    tolerance?: number;
    rubricScore?: number;
    maxRubricScore?: number;
}

/**
 * Weight distribution for evidence types.
 * Uses canonical weights from contracts as the single source of truth.
 */
const EVIDENCE_WEIGHTS: Record<EvidenceType, number> = { ...CANONICAL_EVIDENCE_WEIGHTS };

export class ClaimMasteryCalculator {
    /**
     * Calculate CBM score for a single prediction.
     * Delegates to the canonical scoring function from contracts.
     */
    calculateCBMScore(correct: boolean, confidence: ConfidenceLevel): number {
        return getCBMScore(correct, confidence);
    }

    /**
     * Normalize CBM score to 0-1 range.
     * Canonical CBM range is -6 to +3, so shift by 6 and divide by 9.
     */
    normalizeCBMScore(cbmScore: number): number {
        return normalizeCBMScore(cbmScore);
    }

    /**
     * Calculate score for parameter targeting evidence
     */
    calculateParameterTargetingScore(
        goalAchieved: boolean,
        attempts: number,
        rmse: number,
        tolerance: number
    ): number {
        if (!goalAchieved) {
            // Partial credit for trying
            return 0.2;
        }

        // Fewer attempts = higher score
        const attemptPenalty = Math.max(0, 1 - (attempts - 1) * 0.1);

        // Lower RMSE = higher score
        const rmseScore = Math.max(0, 1 - rmse / tolerance);

        return (attemptPenalty + rmseScore) / 2;
    }

    /**
     * Calculate score for explanation evidence
     */
    calculateExplanationScore(rubricScore: number, maxScore: number): number {
        return rubricScore / maxScore;
    }

    /**
     * Calculate overall mastery score for a claim
     */
    calculateClaimMastery(
        learnerId: string,
        learningUnitId: string,
        claimId: string,
        evidenceRecords: EvidenceRecord[]
    ): ClaimMasteryScore {
        const relevantEvidence = evidenceRecords.filter((e) => e.claimId === claimId);

        if (relevantEvidence.length === 0) {
            return {
                learningUnitId,
                claimId,
                learnerId,
                masteryScore: 0,
                confidenceCalibration: null,
                evidenceScores: {},
                totalAttempts: 0,
                timeOnTaskSeconds: 0,
            };
        }

        const evidenceScores: Record<string, number> = {};
        const weightedScores: Array<{ weight: number; score: number }> = [];
        let totalAttempts = 0;

        for (const evidence of relevantEvidence) {
            let score = 0;

            switch (evidence.type) {
                case 'prediction_vs_outcome':
                    if (evidence.correct !== undefined && evidence.confidence) {
                        const cbm = this.calculateCBMScore(evidence.correct, evidence.confidence);
                        score = this.normalizeCBMScore(cbm);
                    }
                    break;

                case 'parameter_targeting':
                    if (evidence.goalAchieved !== undefined &&
                        evidence.attempts !== undefined &&
                        evidence.rmse !== undefined &&
                        evidence.tolerance !== undefined) {
                        score = this.calculateParameterTargetingScore(
                            evidence.goalAchieved,
                            evidence.attempts,
                            evidence.rmse,
                            evidence.tolerance
                        );
                        totalAttempts += evidence.attempts;
                    }
                    break;

                case 'explanation_quality':
                    if (evidence.rubricScore !== undefined && evidence.maxRubricScore !== undefined) {
                        score = this.calculateExplanationScore(evidence.rubricScore, evidence.maxRubricScore);
                    }
                    break;

                case 'construction_artifact':
                    // For now, use rubric scoring
                    if (evidence.rubricScore !== undefined && evidence.maxRubricScore !== undefined) {
                        score = this.calculateExplanationScore(evidence.rubricScore, evidence.maxRubricScore);
                    }
                    break;
            }

            evidenceScores[evidence.evidenceId] = score;
            weightedScores.push({
                weight: EVIDENCE_WEIGHTS[evidence.type],
                score,
            });
        }

        // Calculate weighted average
        const totalWeight = weightedScores.reduce((sum, ws) => sum + ws.weight, 0);
        const weightedSum = weightedScores.reduce((sum, ws) => sum + ws.weight * ws.score, 0);
        const masteryScore = totalWeight > 0 ? weightedSum / totalWeight : 0;

        // Calculate confidence calibration for predictions
        const predictions = relevantEvidence.filter((e) => e.type === 'prediction_vs_outcome');
        const confidenceCalibration = this.calculateCalibration(predictions);

        return {
            learningUnitId,
            claimId,
            learnerId,
            masteryScore: Math.round(masteryScore * 100) / 100,
            confidenceCalibration,
            evidenceScores,
            totalAttempts,
            timeOnTaskSeconds: 0, // To be populated from telemetry
        };
    }

    /**
     * Calculate confidence calibration index
     */
    private calculateCalibration(predictions: EvidenceRecord[]): number | null {
        if (predictions.length < 3) {
            return null; // Not enough data
        }

        const buckets: Record<ConfidenceLevel, boolean[]> = {
            high: [],
            medium: [],
            low: [],
        };

        for (const p of predictions) {
            if (p.confidence && p.correct !== undefined) {
                buckets[p.confidence].push(p.correct);
            }
        }

        const expectedAccuracy: Record<ConfidenceLevel, number> = {
            high: 0.9,
            medium: 0.6,
            low: 0.3,
        };

        const gaps: number[] = [];

        for (const level of ['high', 'medium', 'low'] as ConfidenceLevel[]) {
            const responses = buckets[level];
            if (responses.length >= 2) {
                const actualAccuracy = responses.filter(Boolean).length / responses.length;
                const gap = Math.abs(actualAccuracy - expectedAccuracy[level]);
                gaps.push(gap);
            }
        }

        if (gaps.length === 0) {
            return null;
        }

        // Calibration score: 1.0 = perfect, lower = miscalibrated
        const avgGap = gaps.reduce((a, b) => a + b, 0) / gaps.length;
        return Math.round((1.0 - avgGap) * 100) / 100;
    }
}

/**
 * Factory function
 */
export function calculateClaimMastery(
    learnerId: string,
    learningUnitId: string,
    claimId: string,
    evidenceRecords: EvidenceRecord[]
): ClaimMasteryScore {
    const calculator = new ClaimMasteryCalculator();
    return calculator.calculateClaimMastery(learnerId, learningUnitId, claimId, evidenceRecords);
}
