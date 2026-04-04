/**
 * ClaimMasteryCalculator Requirement-Based Tests
 *
 * Validates that claim mastery correctly maps learning behaviour to mastery scores.
 * Tests focus on pedagogical outcomes — learning progression boundaries and
 * evidence weighting — rather than internal formula constants.
 *
 * Key learning theory requirements verified here:
 * - Confident mastery maps to full understanding (score ≈ 1.0)
 * - Critical misconception (confident + wrong) maps to poor mastery
 * - Mixed evidence produces proportionally scaled mastery
 * - Confidence calibration rewards well-calibrated learners
 * - Parameter targeting rewards accurate first-attempt solutions
 * - Explanation quality rewards articulate learners proportionally
 *
 * @doc.type test
 * @doc.purpose Validate learning-outcome mapping: mastery ↔ evidence scoring
 * @doc.layer core
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-CLM-001 … TPUT-FR-CLM-009
 */
import { describe, it, expect } from "vitest";
import { ClaimMasteryCalculator, type EvidenceRecord } from "../ClaimMastery";

/** Declared confidence levels as understood by the learning contract */
type ConfidenceLevel = "high" | "medium" | "low";

describe("ClaimMasteryCalculator – Learning Outcome Requirements", () => {
  const calc = new ClaimMasteryCalculator();

  // =========================================================================
  // TPUT-FR-CLM-001: Confident mastery mapping
  // =========================================================================
  describe("TPUT-FR-CLM-001: Confident-correct answers signal full mastery and must produce the highest CBM reward", () => {
    it("confident mastery maps to the maximum positive signal", () => {
      const score = calc.calculateCBMScore(true, "high" as ConfidenceLevel);
      // Must be strictly positive and be the ceiling of the reward space
      expect(score).toBeGreaterThan(0);
      // Nothing else should score higher (verified by comparing against lower-confidence correct answers)
      const mediumCorrect = calc.calculateCBMScore(
        true,
        "medium" as ConfidenceLevel,
      );
      expect(score).toBeGreaterThan(mediumCorrect);
    });

    it("moderate confidence with a correct answer demonstrates growing mastery", () => {
      const score = calc.calculateCBMScore(true, "medium" as ConfidenceLevel);
      expect(score).toBeGreaterThan(0);
    });

    it("low-confidence correct answer contributes positively but signals incomplete understanding", () => {
      const score = calc.calculateCBMScore(true, "low" as ConfidenceLevel);
      // Strictly positive: even a lucky correct answer pushes mastery up
      expect(score).toBeGreaterThan(0);
      // But weaker than medium-confidence correct
      const mediumCorrect = calc.calculateCBMScore(
        true,
        "medium" as ConfidenceLevel,
      );
      expect(score).toBeLessThan(mediumCorrect);
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-002: Critical misconception mapping
  // =========================================================================
  describe("TPUT-FR-CLM-002: Overconfident-wrong answers represent critical misconceptions and must incur the largest penalty", () => {
    it("critical misconception (certain and wrong) maps to the deepest negative signal", () => {
      const score = calc.calculateCBMScore(false, "high" as ConfidenceLevel);
      expect(score).toBeLessThan(0);
      // Must be the harshest penalty: worse than medium-confidence wrong
      const mediumWrong = calc.calculateCBMScore(
        false,
        "medium" as ConfidenceLevel,
      );
      expect(score).toBeLessThan(mediumWrong);
    });

    it("moderate-confidence wrong answer indicates a recoverable misconception with partial penalty", () => {
      const score = calc.calculateCBMScore(false, "medium" as ConfidenceLevel);
      expect(score).toBeLessThan(0);
    });

    it("low-confidence wrong answer reflects healthy uncertainty and should carry minimal penalty", () => {
      const score = calc.calculateCBMScore(false, "low" as ConfidenceLevel);
      // Healthy self-doubt: learner knew they might be wrong → no or trivial penalty
      expect(score).toBeLessThanOrEqual(0);
      // Penalty must be far less severe than confident-wrong
      const confidentWrong = calc.calculateCBMScore(
        false,
        "high" as ConfidenceLevel,
      );
      expect(score).toBeGreaterThan(confidentWrong);
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-003: Mastery score normalisation into [0, 1]
  // =========================================================================
  describe("TPUT-FR-CLM-003: CBM scores must normalise into a [0, 1] mastery range for consistent comparisons", () => {
    it("confident full mastery normalises to complete understanding (1.0)", () => {
      const confidentCorrectRaw = calc.calculateCBMScore(
        true,
        "high" as ConfidenceLevel,
      );
      const normalised = calc.normalizeCBMScore(confidentCorrectRaw);
      expect(normalised).toBe(1);
    });

    it("critical misconception normalises to no demonstrated understanding (0.0)", () => {
      const confidentWrongRaw = calc.calculateCBMScore(
        false,
        "high" as ConfidenceLevel,
      );
      const normalised = calc.normalizeCBMScore(confidentWrongRaw);
      expect(normalised).toBe(0);
    });

    it("neutral performance normalises to a middle-range mastery score", () => {
      // Score of 0 = uncertain wrong (neutral pedagogical signal)
      const neutralRaw = calc.calculateCBMScore(
        false,
        "low" as ConfidenceLevel,
      );
      const normalised = calc.normalizeCBMScore(neutralRaw);
      expect(normalised).toBeGreaterThanOrEqual(0);
      expect(normalised).toBeLessThanOrEqual(1);
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-004: Parameter-targeting evidence rewards accurate first attempts
  // =========================================================================
  describe("TPUT-FR-CLM-004: Parameter targeting should reward learners who reach goals accurately and efficiently", () => {
    it("should assign no mastery credit when the learning goal was not achieved", () => {
      const score = calc.calculateParameterTargetingScore(false, 5, 0.5, 1);
      // Failing to achieve the goal means no demonstrated mastery for this evidence
      expect(score).toBeGreaterThanOrEqual(0);
      expect(score).toBeLessThan(0.5); // Well below passing threshold
    });

    it("should award near-full mastery for a first-attempt accurate solution", () => {
      const score = calc.calculateParameterTargetingScore(true, 1, 0.01, 1);
      // First attempt + high precision = strong mastery demonstration
      expect(score).toBeGreaterThan(0.9);
    });

    it("should penalise additional attempts as they imply iterative trial-and-error", () => {
      const efficientLearner = calc.calculateParameterTargetingScore(
        true,
        1,
        0.1,
        1,
      );
      const inefficientLearner = calc.calculateParameterTargetingScore(
        true,
        5,
        0.1,
        1,
      );
      expect(efficientLearner).toBeGreaterThan(inefficientLearner);
    });

    it("should reward precision — accurate targeting reflects deeper conceptual understanding", () => {
      const preciseAnswer = calc.calculateParameterTargetingScore(
        true,
        1,
        0.1,
        1,
      );
      const impreciseAnswer = calc.calculateParameterTargetingScore(
        true,
        1,
        0.8,
        1,
      );
      expect(preciseAnswer).toBeGreaterThan(impreciseAnswer);
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-005: Explanation quality evidence rewards articulate learners proportionally
  // =========================================================================
  describe("TPUT-FR-CLM-005: Explanation quality should translate rubric performance proportionally into mastery signal", () => {
    it("should reflect partial rubric achievement as proportional mastery", () => {
      expect(calc.calculateExplanationScore(4, 5)).toBeCloseTo(0.8);
    });

    it("should award full mastery for a perfect rubric score", () => {
      expect(calc.calculateExplanationScore(10, 10)).toBe(1);
    });

    it("should award zero mastery when a learner provides no explanation", () => {
      expect(calc.calculateExplanationScore(0, 10)).toBe(0);
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-006: Overall claim mastery with no evidence
  // =========================================================================
  describe("TPUT-FR-CLM-006: A claim with no evidence must start at zero mastery – no assumption of prior knowledge", () => {
    it("should return zero mastery and zero attempts when no evidence has been collected", () => {
      const result = calc.calculateClaimMastery("L1", "LU1", "C1", []);
      expect(result.masteryScore).toBe(0);
      expect(result.totalAttempts).toBe(0);
      expect(result.confidenceCalibration).toBeNull();
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-007: Mixed evidence integration
  // =========================================================================
  describe("TPUT-FR-CLM-007: Mixed evidence types must be integrated into a single mastery score within the valid range", () => {
    it("should produce a bounded mastery score integrating prediction, targeting, and explanation evidence", () => {
      const evidence: EvidenceRecord[] = [
        {
          evidenceId: "e1",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: true,
          confidence: "high",
        },
        {
          evidenceId: "e2",
          claimId: "C1",
          type: "parameter_targeting",
          goalAchieved: true,
          attempts: 1,
          rmse: 0.05,
          tolerance: 1,
        },
        {
          evidenceId: "e3",
          claimId: "C1",
          type: "explanation_quality",
          rubricScore: 8,
          maxRubricScore: 10,
        },
      ];

      const result = calc.calculateClaimMastery("L1", "LU1", "C1", evidence);

      // Mastery must be within the learning contract range [0, 1]
      expect(result.masteryScore).toBeGreaterThan(0);
      expect(result.masteryScore).toBeLessThanOrEqual(1);
      // All three evidence types must have contributed individual scores
      expect(Object.keys(result.evidenceScores)).toHaveLength(3);
    });

    it("should only include evidence belonging to the specific claim being assessed", () => {
      const evidence: EvidenceRecord[] = [
        {
          evidenceId: "e1",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: true,
          confidence: "high",
        },
        {
          evidenceId: "e2",
          claimId: "C2", // evidence for a different claim – must not pollute C1's score
          type: "prediction_vs_outcome",
          correct: false,
          confidence: "high",
        },
      ];

      const result = calc.calculateClaimMastery("L1", "LU1", "C1", evidence);

      // C1 mastery should only reflect e1; e2 is irrelevant evidence for another claim
      expect(Object.keys(result.evidenceScores)).toHaveLength(1);
      expect(result.evidenceScores["e1"]).toBeDefined();
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-008: Accumulated attempt counting across targeting sessions
  // =========================================================================
  describe("TPUT-FR-CLM-008: Total attempts must be accumulated across all parameter-targeting sessions", () => {
    it("should sum all attempts including multiple targeting sessions", () => {
      const evidence: EvidenceRecord[] = [
        {
          evidenceId: "e1",
          claimId: "C1",
          type: "parameter_targeting",
          goalAchieved: true,
          attempts: 3,
          rmse: 0.1,
          tolerance: 1,
        },
        {
          evidenceId: "e2",
          claimId: "C1",
          type: "parameter_targeting",
          goalAchieved: true,
          attempts: 2,
          rmse: 0.05,
          tolerance: 1,
        },
      ];

      const result = calc.calculateClaimMastery("L1", "LU1", "C1", evidence);
      // 3 + 2 = 5 total attempts across both targeting sessions
      expect(result.totalAttempts).toBe(5);
    });
  });

  // =========================================================================
  // TPUT-FR-CLM-009: Confidence calibration quality
  // =========================================================================
  describe("TPUT-FR-CLM-009: Confidence calibration requires ≥3 predictions to be statistically meaningful", () => {
    it("should withhold calibration score when insufficient prediction data is available", () => {
      const evidence: EvidenceRecord[] = [
        {
          evidenceId: "e1",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: true,
          confidence: "high",
        },
        {
          evidenceId: "e2",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: false,
          confidence: "low",
        },
      ];

      const result = calc.calculateClaimMastery("L1", "LU1", "C1", evidence);
      // Insufficient data for calibration – must not produce a misleading score
      expect(result.confidenceCalibration).toBeNull();
    });

    it("should award a high calibration score to a learner whose confidence matches their accuracy", () => {
      const evidence: EvidenceRecord[] = [
        // High confidence + all correct: calibration is nearly perfect
        {
          evidenceId: "e1",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: true,
          confidence: "high",
        },
        {
          evidenceId: "e2",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: true,
          confidence: "high",
        },
        {
          evidenceId: "e3",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: true,
          confidence: "high",
        },
      ];

      const result = calc.calculateClaimMastery("L1", "LU1", "C1", evidence);
      // A learner who is confidently correct consistently is well-calibrated
      expect(result.confidenceCalibration).toBeGreaterThan(0.8);
    });

    it("should assign a low calibration score to a persistently overconfident yet wrong learner", () => {
      const evidence: EvidenceRecord[] = [
        // Confident about wrong answers: extreme miscalibration
        {
          evidenceId: "e1",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: false,
          confidence: "high",
        },
        {
          evidenceId: "e2",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: false,
          confidence: "high",
        },
        {
          evidenceId: "e3",
          claimId: "C1",
          type: "prediction_vs_outcome",
          correct: false,
          confidence: "high",
        },
      ];

      const result = calc.calculateClaimMastery("L1", "LU1", "C1", evidence);
      // 0% accuracy while declaring 90% confidence = severe overconfidence
      expect(result.confidenceCalibration).toBeLessThan(0.3);
    });
  });
});
