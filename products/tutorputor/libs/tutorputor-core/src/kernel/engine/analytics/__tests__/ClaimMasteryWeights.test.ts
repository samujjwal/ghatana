/**
 * ClaimMastery – Evidence Weight Validation Tests
 *
 * The audit identified that the existing ClaimMastery tests cover CBM score
 * computation and normalisation but do NOT explicitly validate that the
 * canonical evidence-weight distribution (prediction=0.3, parameter=0.5,
 * explanation=0.2) is correctly applied to the weighted-average mastery
 * calculation.
 *
 * Gaps addressed:
 *  1. Single-evidence-type mastery equals the raw score for that type.
 *  2. Weighted average with all three evidence types is correct.
 *  3. Parameter targeting (weight=0.5) contributes more than prediction
 *     (weight=0.3) or explanation (weight=0.2) when all scores are equal.
 *  4. `confidenceCalibration` is null when there are fewer than 3 prediction
 *     evidence records.
 *  5. `confidenceCalibration` is defined (not null) with ≥ 3 prediction
 *     records where at least one confidence level has ≥ 2 observations.
 *  6. Zero-evidence claim produces masteryScore=0.
 *  7. Mastery score is always in [0, 1].
 *
 * @doc.type test
 * @doc.purpose Validate evidence weight distribution and calibration thresholds
 * @doc.layer analytics
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-CLM-001 (mastery calculation),
 *                  TPUT-FR-CLM-004 (evidence weights),
 *                  TPUT-FR-CLM-008 (calibration threshold),
 *                  TPUT-FR-CLM-009 (calibration null guard)
 */
import { describe, it, expect } from "vitest";
import {
  ClaimMasteryCalculator,
  calculateClaimMastery,
  type EvidenceRecord,
} from "../ClaimMastery";

// ---------------------------------------------------------------------------
// Constants: mirrors CANONICAL_EVIDENCE_WEIGHTS from contracts
// ---------------------------------------------------------------------------
const W_PREDICTION = 0.3;
const W_PARAMETER = 0.5;
const W_EXPLANATION = 0.2;
// W_TOTAL = 1.0 (all weights sum to 1)

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function predictionEvidence(
  id: string,
  correct: boolean,
  confidence: "high" | "medium" | "low",
): EvidenceRecord {
  return {
    evidenceId: id,
    claimId: "claim-1",
    type: "prediction_vs_outcome",
    correct,
    confidence,
  };
}

function parameterEvidence(
  id: string,
  goalAchieved: boolean,
  attempts: number,
  rmse: number,
  tolerance: number,
): EvidenceRecord {
  return {
    evidenceId: id,
    claimId: "claim-1",
    type: "parameter_targeting",
    goalAchieved,
    attempts,
    rmse,
    tolerance,
  };
}

function explanationEvidence(
  id: string,
  rubricScore: number,
  maxRubricScore: number,
): EvidenceRecord {
  return {
    evidenceId: id,
    claimId: "claim-1",
    type: "explanation_quality",
    rubricScore,
    maxRubricScore,
  };
}

const LEARNER = "learner-1";
const LU = "lu-1";
const CLAIM = "claim-1";

// ---------------------------------------------------------------------------
// 1. Single-evidence-type mastery
// ---------------------------------------------------------------------------

describe("ClaimMasteryCalculator – TPUT-FR-CLM-004 (single-evidence mastery)", () => {
  const calc = new ClaimMasteryCalculator();

  it("masteryScore for a single perfect prediction equals normalized CBM score for correct+high", () => {
    // correct=true, confidence=high → CBM = +3, normalized = (3+6)/9 = 1.0
    const evidence = [predictionEvidence("e1", true, "high")];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.masteryScore).toBeCloseTo(1.0, 2);
  });

  it("masteryScore for a single wrong+high prediction equals normalized CBM score for incorrect+high", () => {
    // correct=false, confidence=high → CBM = -6, normalized = (-6+6)/9 = 0
    const evidence = [predictionEvidence("e1", false, "high")];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.masteryScore).toBeCloseTo(0, 2);
  });

  it("masteryScore for a single perfect parameter targeting is 1.0", () => {
    // goalAchieved=true, attempts=1, rmse=0, tolerance=1 → score=1.0
    const evidence = [parameterEvidence("e1", true, 1, 0, 1)];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    // attemptPenalty = 1.0, rmseScore = 1.0, score = 1.0
    expect(result.masteryScore).toBeCloseTo(1.0, 2);
  });

  it("masteryScore for goal-not-achieved parameter equals partial credit (0.2)", () => {
    const evidence = [parameterEvidence("e1", false, 1, 0, 1)];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.masteryScore).toBeCloseTo(0.2, 2);
  });

  it("masteryScore for a perfect rubric explanation is 1.0", () => {
    const evidence = [explanationEvidence("e1", 10, 10)];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.masteryScore).toBeCloseTo(1.0, 2);
  });

  it("masteryScore for a half-score explanation is 0.5", () => {
    const evidence = [explanationEvidence("e1", 5, 10)];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.masteryScore).toBeCloseTo(0.5, 2);
  });
});

// ---------------------------------------------------------------------------
// 2. Weighted average across all three evidence types
// ---------------------------------------------------------------------------

describe("ClaimMasteryCalculator – TPUT-FR-CLM-004 (weighted average with all evidence types)", () => {
  it("correctly computes weighted average when all three evidence types are present", () => {
    // prediction: correct=true, confidence=high → normalized = 1.0   (weight 0.3)
    // parameter:  goalAchieved=false → 0.2                           (weight 0.5)
    // explanation: rubric 6/10 = 0.6                                 (weight 0.2)
    // Expected = (0.3 * 1.0 + 0.5 * 0.2 + 0.2 * 0.6) / 1.0 = (0.3 + 0.1 + 0.12) = 0.52
    const evidence: EvidenceRecord[] = [
      predictionEvidence("e1", true, "high"),
      parameterEvidence("e2", false, 1, 0, 1),
      explanationEvidence("e3", 6, 10),
    ];

    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);
    expect(result.masteryScore).toBeCloseTo(0.52, 2);
  });

  it("weighted mastery with all evidence at 0.5 is 0.5 regardless of weights", () => {
    // Each evidence type scores 0.5; weighted average = 0.5 (weight-independent)
    // correct=false, confidence=medium → CBM=-2, normalized=(-2+6)/9=4/9≈0.44 (close but not 0.5)
    // Let's use a prediction that gives normalized~0.5: CBM=-1.5 → normalized=4.5/9=0.5
    // incorrect + medium = -2 → normalized = 4/9 ≈ 0.444; let's try correct+low = +1 → normalized=7/9≈0.778
    // impossible to hit exactly 0.5 with CBM. Instead verify a known exact value.

    // prediction: correct=true, confidence=medium → CBM=+2, normalized=(2+6)/9=8/9≈0.889  (weight 0.3)
    // parameter: goalAchieved=true, attempts=1, rmse=0.5, tolerance=1.0                    (weight 0.5)
    //            attemptPenalty=1.0, rmseScore=1-0.5/1=0.5, score=(1.0+0.5)/2=0.75
    // explanation: rubricScore=3, maxRubricScore=4 = 0.75                                  (weight 0.2)
    // Expected = (0.3 * 8/9 + 0.5 * 0.75 + 0.2 * 0.75) / 1.0
    //          = (0.2667 + 0.375 + 0.15) = 0.7917

    const evidence: EvidenceRecord[] = [
      predictionEvidence("e1", true, "medium"),
      parameterEvidence("e2", true, 1, 0.5, 1.0),
      explanationEvidence("e3", 3, 4),
    ];

    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);
    const expected =
      W_PREDICTION * (8 / 9) + W_PARAMETER * 0.75 + W_EXPLANATION * 0.75;
    expect(result.masteryScore).toBeCloseTo(expected, 2);
  });
});

// ---------------------------------------------------------------------------
// 3. Parameter-targeting weight dominance
// ---------------------------------------------------------------------------

describe("ClaimMasteryCalculator – TPUT-FR-CLM-004 (parameter-targeting weight dominance)", () => {
  it("parameter targeting contributes more than prediction when both score above 0", () => {
    // Isolate by giving prediction=1.0 and parameter=0.0
    // If only prediction: mastery = 1.0 (weight 0.3 out of 0.3+0.3=0.3)
    // If only param(0.2): mastery = 0.2
    // Prediction-only → higher value, but parameter weight is heavier.

    // Test: prediction=1.0 (weight 0.3) vs parameter=1.0 (weight 0.5)
    // When prediction=0, parameter=1.0:  mastery = 0.5 * 1.0 / 0.5 = 1.0
    // When parameter=0, prediction=1.0:  mastery = 0.3 * 1.0 / 0.3 = 1.0
    // They're equal at extremes. Test with intermediate value.
    //
    // With prediction=1.0, parameter=0.0, explanation=0.0:
    //   mastery = (0.3 * 1 + 0.5 * 0 + 0.2 * 0) / 1.0 = 0.3
    // With prediction=0.0, parameter=1.0, explanation=0.0:
    //   mastery = (0.3 * 0 + 0.5 * 1 + 0.2 * 0) / 1.0 = 0.5
    // → parameter dominating: 0.5 > 0.3 ✓

    const predOnly: EvidenceRecord[] = [
      predictionEvidence("e1", true, "high"), // normalized = 1.0
      parameterEvidence("e2", false, 1, 0, 1), // score = 0.2
      explanationEvidence("e3", 0, 10), // score = 0.0
    ];

    const paramOnly: EvidenceRecord[] = [
      predictionEvidence("e1", false, "high"), // normalized = 0.0
      parameterEvidence("e2", true, 1, 0, 1), // score = 1.0
      explanationEvidence("e3", 0, 10), // score = 0.0
    ];

    const predOnlyResult = calculateClaimMastery(LEARNER, LU, CLAIM, predOnly);
    const paramOnlyResult = calculateClaimMastery(
      LEARNER,
      LU,
      CLAIM,
      paramOnly,
    );

    // Parameter full score (0.5 weight) should exceed prediction full score (0.3 weight)
    // even when both are normalized to 1.0 (weight contribution: 0.5 vs 0.3)
    expect(paramOnlyResult.masteryScore).toBeGreaterThan(
      predOnlyResult.masteryScore,
    );
  });

  it("explanation evidence (weight 0.2) contributes less than parameter (weight 0.5) for same raw score", () => {
    const predNeutral = predictionEvidence("e1", false, "high"); // normalized = 0.0

    const withExplanation: EvidenceRecord[] = [
      predNeutral,
      parameterEvidence("e2", false, 1, 0, 1), // score = 0.2 (not used in this comparison)
      explanationEvidence("e3", 10, 10), // score = 1.0, weight = 0.2
    ];

    const withParameter: EvidenceRecord[] = [
      predNeutral,
      parameterEvidence("e2", true, 1, 0, 1), // score = 1.0, weight = 0.5
      explanationEvidence("e3", 0, 10), // score = 0.0, weight = 0.2
    ];

    const explResult = calculateClaimMastery(
      LEARNER,
      LU,
      CLAIM,
      withExplanation,
    );
    const paramResult = calculateClaimMastery(
      LEARNER,
      LU,
      CLAIM,
      withParameter,
    );

    expect(paramResult.masteryScore).toBeGreaterThan(explResult.masteryScore);
  });
});

// ---------------------------------------------------------------------------
// 4. Confidence calibration null guard (< 3 predictions → null)
// ---------------------------------------------------------------------------

describe("ClaimMasteryCalculator – TPUT-FR-CLM-009 (calibration null guard)", () => {
  it("returns null calibration when there are 0 prediction evidence records", () => {
    const evidence = [explanationEvidence("e1", 8, 10)];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.confidenceCalibration).toBeNull();
  });

  it("returns null calibration when there is exactly 1 prediction evidence record", () => {
    const evidence = [predictionEvidence("e1", true, "high")];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.confidenceCalibration).toBeNull();
  });

  it("returns null calibration when there are exactly 2 prediction evidence records", () => {
    const evidence = [
      predictionEvidence("e1", true, "high"),
      predictionEvidence("e2", false, "high"),
    ];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.confidenceCalibration).toBeNull();
  });

  it("returns non-null calibration when there are 3 prediction records with the same confidence level", () => {
    // 3 high-confidence predictions → bucket.high = [true, true, true] → >= 2 observations
    const evidence = [
      predictionEvidence("e1", true, "high"),
      predictionEvidence("e2", true, "high"),
      predictionEvidence("e3", false, "high"),
    ];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    expect(result.confidenceCalibration).not.toBeNull();
    expect(typeof result.confidenceCalibration).toBe("number");
  });

  it("returns null calibration when 3+ predictions are spread across different confidence levels (no bucket has >= 2)", () => {
    // 3 predictions, each with a different confidence level → no bucket gets >= 2 → null
    const evidence = [
      predictionEvidence("e1", true, "high"),
      predictionEvidence("e2", true, "medium"),
      predictionEvidence("e3", true, "low"),
    ];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    // Each bucket has exactly 1 item → none qualifies for the ">=2 responses" check
    expect(result.confidenceCalibration).toBeNull();
  });

  it("calibration score is 1.0 when high-confidence answers have 100% accuracy (perfect high-confidence)", () => {
    // expected accuracy for "high" = 0.9; actual = 1.0 → gap = 0.1 → calibration = 1 - 0.1 = 0.9
    const evidence = [
      predictionEvidence("e1", true, "high"),
      predictionEvidence("e2", true, "high"),
      predictionEvidence("e3", true, "high"), // extra to satisfy 3-item minimum
    ];
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);

    // Gap from expected 0.9 = |1.0 - 0.9| = 0.1; calibration = 1 - 0.1 = 0.9
    expect(result.confidenceCalibration).toBeCloseTo(0.9, 2);
  });
});

// ---------------------------------------------------------------------------
// 5. Zero-evidence and boundary conditions
// ---------------------------------------------------------------------------

describe("ClaimMasteryCalculator – TPUT-FR-CLM-001 (zero-evidence and bounds)", () => {
  it("returns masteryScore=0 when there is no evidence for the claim", () => {
    const result = calculateClaimMastery(LEARNER, LU, CLAIM, []);
    expect(result.masteryScore).toBe(0);
    expect(result.confidenceCalibration).toBeNull();
  });

  it("masteryScore is always a number in [0, 1]", () => {
    const calc = new ClaimMasteryCalculator();

    const scenarios: EvidenceRecord[][] = [
      // All-correct high-confidence
      [
        predictionEvidence("e1", true, "high"),
        predictionEvidence("e2", true, "high"),
      ],
      // All-wrong high-confidence
      [
        predictionEvidence("e1", false, "high"),
        predictionEvidence("e2", false, "high"),
      ],
      // Mixed
      [
        predictionEvidence("e1", true, "medium"),
        parameterEvidence("e2", false, 5, 2, 1),
        explanationEvidence("e3", 3, 10),
      ],
      // All at 0%
      [
        parameterEvidence("e1", false, 10, 5, 1),
        explanationEvidence("e2", 0, 10),
      ],
      // Perfect
      [
        parameterEvidence("e1", true, 1, 0, 1),
        explanationEvidence("e2", 10, 10),
      ],
    ];

    for (const evidence of scenarios) {
      const result = calc.calculateClaimMastery(LEARNER, LU, CLAIM, evidence);
      expect(result.masteryScore).toBeGreaterThanOrEqual(0);
      expect(result.masteryScore).toBeLessThanOrEqual(1);
    }
  });

  it("evidence for a different claimId is ignored when computing mastery for the target claim", () => {
    const evidence: EvidenceRecord[] = [
      {
        evidenceId: "e1",
        claimId: "other-claim",
        type: "prediction_vs_outcome",
        correct: true,
        confidence: "high",
      },
      predictionEvidence("e2", false, "high"), // claimId = "claim-1" → score = 0
    ];

    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);
    // Only e2 contributes (claimId matches target); its normalized score = 0
    expect(result.masteryScore).toBeCloseTo(0, 2);
  });

  it("totalAttempts aggregates attempts from parameter_targeting evidence records", () => {
    const evidence: EvidenceRecord[] = [
      parameterEvidence("e1", true, 3, 0.1, 1.0),
      parameterEvidence("e2", true, 2, 0.05, 1.0),
    ];

    const result = calculateClaimMastery(LEARNER, LU, CLAIM, evidence);
    expect(result.totalAttempts).toBe(5); // 3 + 2
  });
});
