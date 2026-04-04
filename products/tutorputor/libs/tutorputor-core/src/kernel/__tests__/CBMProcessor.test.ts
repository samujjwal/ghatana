/**
 * CBMProcessor Requirement-Based Tests
 *
 * Validates that the CBM processor correctly enforces pedagogical theory:
 * - Confident correct answers demonstrate mastery and earn full reward
 * - Confident wrong answers are the most harmful form of miscalibration
 * - Uncertain correct answers show partial understanding
 * - Uncertain wrong answers are penalised least (healthy self-doubt)
 * - Calibration feedback must trigger when overconfidence persists across ≥3 items
 *
 * @doc.type test
 * @doc.purpose Validate CBM pedagogical outcomes: mastery reward, miscalibration penalty, viva triggers
 * @doc.layer plugin
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-CBM-001 … TPUT-FR-CBM-010
 */
import { describe, it, expect, beforeEach } from "vitest";
import { CBMProcessor } from "../plugins/CBMProcessor";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Pedagogical confidence thresholds used across the test suite. */
const CONFIDENCE = {
  /** Learner is sure – high stakes, strong signal of mastery or misconception. */
  highCertainty: 0.9,
  /** Learner is unsure but has a preference – moderate signal. */
  moderateCertainty: 0.5,
  /** Learner is guessing – low information value, penalty should be minimal. */
  lowCertainty: 0.2,
} as const;

/**
 * Pedagogical boundaries for CBM scores.
 * Tests validate ranges rather than magic constants so they survive
 * minor formula tweaks that preserve the learning theory contract.
 */
const CBM_SCORE_BOUNDS = {
  /** Confident mastery: highest achievable reward – only high-confidence correct earns this */
  confidentCorrect: { min: 3, max: Infinity },
  /** Partial credit: understood but under-confident (medium or low confidence correct) */
  uncertainCorrect: { min: 0, max: 3 },
  /** Critical misconception: largest penalty, must be negative */
  confidentWrong: { max: -4 },
  /** Productive struggle: moderate penalty */
  moderatelyWrong: { min: -4, max: 0 },
  /** Healthy uncertainty: no penalty for legitimate doubt */
  uncertainWrong: { min: -1, max: 1 },
} as const;

function makeAnswerEvidence(opts: {
  correct: boolean;
  confidence: number;
  claimId?: string;
}): any {
  return {
    type: "answer_submission",
    claimId: opts.claimId ?? "claim-1",
    payload: {
      correct: opts.correct,
      confidence: opts.confidence,
      claimId: opts.claimId ?? "claim-1",
    },
  };
}

function makeLearnerContext(overrides: Record<string, unknown> = {}): any {
  return {
    learnerId: "learner-1",
    data: {},
    ...overrides,
  };
}

describe("CBMProcessor – Pedagogical Requirements", () => {
  let processor: CBMProcessor;

  beforeEach(async () => {
    processor = new CBMProcessor();
    await processor.initialize();
  });

  // =========================================================================
  // TPUT-FR-CBM-001: Plugin identity and event routing
  // =========================================================================
  describe("TPUT-FR-CBM-001: Plugin identity and event routing", () => {
    it("should be identifiable as the CBM evidence processor", () => {
      expect(processor.metadata.id).toBe("cbm-processor");
      expect(processor.metadata.type).toBe("evidence_processor");
      expect(processor.metadata.tags).toContain("cbm");
    });

    it("should accept answer_submission events that carry mastery signal", () => {
      expect(processor.supports({ type: "answer_submission" } as any)).toBe(
        true,
      );
    });

    it("should ignore events that carry no CBM signal (e.g. video_complete)", () => {
      expect(processor.supports({ type: "video_complete" } as any)).toBe(false);
    });
  });

  // =========================================================================
  // TPUT-FR-CBM-002: Mastery reward – confident correct answers
  // =========================================================================
  describe("TPUT-FR-CBM-002: Confident correct answers demonstrate mastery and earn the highest reward", () => {
    it("should award maximum positive reward when a learner is certain and correct", async () => {
      const ctx = makeLearnerContext();
      const result = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.highCertainty,
        }),
      );

      expect(result.status).toBe("success");
      expect(result.data?.score).toBeGreaterThanOrEqual(
        CBM_SCORE_BOUNDS.confidentCorrect.min,
      );
    });

    it("should award moderate reward when a learner is partially confident and correct", async () => {
      const ctx = makeLearnerContext();
      const result = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.moderateCertainty,
        }),
      );

      const score: number = result.data?.score;
      expect(score).toBeGreaterThan(0);
      expect(score).toBeLessThan(CBM_SCORE_BOUNDS.confidentCorrect.min);
    });

    it("should award minimal positive reward when an uncertain learner happens to be correct", async () => {
      const ctx = makeLearnerContext();
      const result = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.lowCertainty,
        }),
      );

      const score: number = result.data?.score;
      // Uncertain-correct carries the weakest mastery signal – still positive or zero
      expect(score).toBeGreaterThanOrEqual(
        CBM_SCORE_BOUNDS.uncertainCorrect.min,
      );
      expect(score).toBeLessThanOrEqual(CBM_SCORE_BOUNDS.uncertainCorrect.max);
    });
  });

  // =========================================================================
  // TPUT-FR-CBM-003: Misconception penalty – overconfident wrong answers
  // =========================================================================
  describe("TPUT-FR-CBM-003: Overconfident wrong answers represent critical misconceptions and incur the largest penalty", () => {
    it("should apply the largest negative penalty when a learner is certain yet wrong", async () => {
      const ctx = makeLearnerContext();
      const result = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.highCertainty,
        }),
      );

      expect(result.data?.score).toBeLessThanOrEqual(
        CBM_SCORE_BOUNDS.confidentWrong.max,
      );
    });

    it("should apply a moderate penalty when a learner has medium confidence and is wrong", async () => {
      const ctx = makeLearnerContext();
      const result = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.moderateCertainty,
        }),
      );

      const score: number = result.data?.score;
      expect(score).toBeGreaterThanOrEqual(
        CBM_SCORE_BOUNDS.moderatelyWrong.min,
      );
      expect(score).toBeLessThanOrEqual(CBM_SCORE_BOUNDS.moderatelyWrong.max);
    });

    it("should apply no meaningful penalty when an uncertain learner is wrong (healthy self-doubt)", async () => {
      const ctx = makeLearnerContext();
      const result = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.lowCertainty,
        }),
      );

      const score: number = result.data?.score;
      expect(score).toBeGreaterThanOrEqual(CBM_SCORE_BOUNDS.uncertainWrong.min);
      expect(score).toBeLessThanOrEqual(CBM_SCORE_BOUNDS.uncertainWrong.max);
    });
  });

  // =========================================================================
  // TPUT-FR-CBM-004: Reward ordering (monotonicity requirement)
  // =========================================================================
  describe("TPUT-FR-CBM-004: Reward ordering must reflect learning theory monotonicity", () => {
    it("should reward confident-correct more than uncertain-correct (confidence encouragement)", async () => {
      const ctxHigh = makeLearnerContext();
      const ctxLow = makeLearnerContext();

      const high = await processor.process(
        ctxHigh,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.highCertainty,
        }),
      );
      const low = await processor.process(
        ctxLow,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.lowCertainty,
        }),
      );

      expect(high.data?.score).toBeGreaterThan(low.data?.score);
    });

    it("should penalise confident-wrong more than uncertain-wrong (overconfidence deterrence)", async () => {
      const ctxHigh = makeLearnerContext();
      const ctxLow = makeLearnerContext();

      const high = await processor.process(
        ctxHigh,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.highCertainty,
        }),
      );
      const low = await processor.process(
        ctxLow,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.lowCertainty,
        }),
      );

      expect(high.data?.score).toBeLessThan(low.data?.score);
    });

    it("should score any correct answer above its wrong counterpart at the same confidence level", async () => {
      const ctxCorrect = makeLearnerContext();
      const ctxWrong = makeLearnerContext();

      const correct = await processor.process(
        ctxCorrect,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.moderateCertainty,
        }),
      );
      const wrong = await processor.process(
        ctxWrong,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.moderateCertainty,
        }),
      );

      expect(correct.data?.score).toBeGreaterThan(wrong.data?.score);
    });
  });

  // =========================================================================
  // TPUT-FR-CBM-005: Session-level evidence aggregation
  // =========================================================================
  describe("TPUT-FR-CBM-005: Session evidence accumulates to build a holistic mastery picture", () => {
    it("should track the total number of answered items across the session", async () => {
      const ctx = makeLearnerContext();

      await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.highCertainty,
        }),
      );
      await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.highCertainty,
        }),
      );

      const metrics = ctx.data["cbm.aggregate.metrics"];
      expect(metrics).toBeDefined();
      expect(metrics.itemCount).toBe(2);
    });

    it("should accumulate the net score representing overall learning trajectory", async () => {
      const ctx = makeLearnerContext();

      const r1 = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.highCertainty,
        }),
      );
      const r2 = await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.highCertainty,
        }),
      );

      const expectedTotal: number =
        (r1.data?.score ?? 0) + (r2.data?.score ?? 0);
      const metrics = ctx.data["cbm.aggregate.metrics"];

      expect(metrics.totalScore).toBe(expectedTotal);
    });

    it("should compute Brier score to measure probabilistic forecast accuracy", async () => {
      const ctx = makeLearnerContext();

      // Correct with high confidence (0.9): Brier = (0.9 - 1)^2 = 0.01
      await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.highCertainty,
        }),
      );

      const metrics = ctx.data["cbm.aggregate.metrics"];
      expect(metrics.brierScore).toBeCloseTo(0.01, 2);
    });

    it("should compute calibration index of zero for a perfectly calibrated learner", async () => {
      const ctx = makeLearnerContext();

      // Perfect calibration: declared 100% confidence, answer correct (delta = 0)
      await processor.process(
        ctx,
        makeAnswerEvidence({ correct: true, confidence: 1.0 }),
      );

      const metrics = ctx.data["cbm.aggregate.metrics"];
      expect(metrics.calibrationIndex).toBeCloseTo(0, 1);
    });
  });

  // =========================================================================
  // TPUT-FR-CBM-006: Viva trigger – sustained overconfidence detection
  // =========================================================================
  describe("TPUT-FR-CBM-006: Sustained overconfidence must trigger a viva review to surface misconceptions", () => {
    it("should flag a learner for oral viva assessment after three consecutive overconfident wrong answers", async () => {
      const ctx = makeLearnerContext();

      // Persistent overconfidence pattern: certain about wrong answers three times in a row
      for (let i = 0; i < 3; i++) {
        await processor.process(
          ctx,
          makeAnswerEvidence({
            correct: false,
            confidence: CONFIDENCE.highCertainty,
            claimId: `claim-${i}`,
          }),
        );
      }

      const vivaTrigger = ctx.data["cbm.trigger.viva"];
      expect(vivaTrigger).toBeDefined();
      // The trigger must identify overconfidence as the pedagogical reason
      expect(vivaTrigger.reason).toBe("overconfidence");
      // Calibration gap must exceed the critical threshold for diagnostic follow-up
      expect(vivaTrigger.calibrationIndex).toBeGreaterThan(0.3);
    });

    it("should not trigger viva prematurely – fewer than three items is insufficient evidence of a pattern", async () => {
      const ctx = makeLearnerContext();

      // Two overconfident wrong answers: not yet a confirmed pattern
      await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.highCertainty,
          claimId: "claim-1",
        }),
      );
      await processor.process(
        ctx,
        makeAnswerEvidence({
          correct: false,
          confidence: CONFIDENCE.highCertainty,
          claimId: "claim-2",
        }),
      );

      expect(ctx.data["cbm.trigger.viva"]).toBeUndefined();
    });

    it("should not flag a well-calibrated learner who is sometimes wrong with appropriate uncertainty", async () => {
      const ctx = makeLearnerContext();

      // Uncertain wrong answers: the learner knows they do not know
      for (let i = 0; i < 3; i++) {
        await processor.process(
          ctx,
          makeAnswerEvidence({
            correct: false,
            confidence: CONFIDENCE.lowCertainty,
            claimId: `claim-${i}`,
          }),
        );
      }

      // Low confidence + wrong is not overconfidence; no viva should trigger
      expect(ctx.data["cbm.trigger.viva"]).toBeUndefined();
    });
  });

  // =========================================================================
  // TPUT-FR-CBM-007: Boundary and fault-tolerance requirements
  // =========================================================================
  describe("TPUT-FR-CBM-007: The processor must handle malformed events without crashing the session", () => {
    it("should gracefully skip events that carry no answer payload", async () => {
      const result = await processor.process(makeLearnerContext(), {
        type: "answer_submission",
        payload: null,
      } as any);
      expect(result.status).toBe("skipped");
    });

    it("should return an error status instead of throwing when the processing context is corrupt", async () => {
      const corruptContext = null as any;
      const result = await processor.process(
        corruptContext,
        makeAnswerEvidence({
          correct: true,
          confidence: CONFIDENCE.highCertainty,
        }),
      );
      expect(result.status).toBe("error");
    });
  });
});
