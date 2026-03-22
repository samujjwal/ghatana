/**
 * Canonical Learning Unit Type Definitions
 *
 * This is the single source of truth for Learning Unit structures across:
 * - CMS authoring
 * - Simulation generation
 * - AI tutoring
 * - Assessment
 * - Telemetry
 * - Credentials
 *
 * @doc.type module
 * @doc.purpose Canonical Learning Unit type definitions
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */
/**
 * Canonical CBM+ scoring matrix (Gardner-Medwin, 2006).
 *
 * This is the SINGLE SOURCE OF TRUTH for CBM scoring across the platform.
 * All scoring implementations MUST use these values.
 *
 * Range: -6 to +3
 *   correct + high confidence   = +3 (reward for well-calibrated knowledge)
 *   correct + medium confidence = +2
 *   correct + low confidence    = +1
 *   incorrect + high confidence = -6 (strong penalty for overconfidence)
 *   incorrect + medium confidence = -2
 *   incorrect + low confidence  =  0 (no penalty for honest uncertainty)
 */
export const CANONICAL_CBM_SCORING = Object.freeze({
    correctHighConfidence: 3,
    correctMediumConfidence: 2,
    correctLowConfidence: 1,
    incorrectHighConfidence: -6,
    incorrectMediumConfidence: -2,
    incorrectLowConfidence: 0,
});
/**
 * Canonical evidence type weights for mastery calculation.
 * Used by ClaimMasteryAggregator and all scoring paths.
 */
export const CANONICAL_EVIDENCE_WEIGHTS = Object.freeze({
    prediction_vs_outcome: 0.3,
    parameter_targeting: 0.5,
    explanation_quality: 0.2,
    construction_artifact: 0.4,
    observation: 0.2,
    diagnosis: 0.4,
});
/**
 * Utility: get CBM score from the canonical matrix.
 */
export function getCBMScore(correct, confidence) {
    if (correct) {
        switch (confidence) {
            case "high":
                return CANONICAL_CBM_SCORING.correctHighConfidence;
            case "medium":
                return CANONICAL_CBM_SCORING.correctMediumConfidence;
            case "low":
                return CANONICAL_CBM_SCORING.correctLowConfidence;
        }
    }
    else {
        switch (confidence) {
            case "high":
                return CANONICAL_CBM_SCORING.incorrectHighConfidence;
            case "medium":
                return CANONICAL_CBM_SCORING.incorrectMediumConfidence;
            case "low":
                return CANONICAL_CBM_SCORING.incorrectLowConfidence;
        }
    }
}
/**
 * Normalize a raw CBM score to 0-1 range.
 * CBM range is -6 to +3, so shift by 6 and divide by 9.
 */
export function normalizeCBMScore(rawScore) {
    return Math.max(0, Math.min(1, (rawScore + 6) / 9));
}
//# sourceMappingURL=learning-unit.js.map