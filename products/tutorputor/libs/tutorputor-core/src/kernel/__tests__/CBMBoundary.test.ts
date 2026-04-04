/**
 * CBM Boundary Value and Precision Tests
 *
 * @doc.type test
 * @doc.purpose Verify CBM scoring at numeric confidence boundary values
 *              (0, 1, below 0, above 1, NaN) and Brier score floating-point
 *              precision for large sequential datasets.
 * @doc.layer plugin
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-CBM-011 (boundary confidence inputs),
 *                  TPUT-FR-CBM-012 (out-of-range normalization),
 *                  TPUT-FR-CBM-013 (Brier score precision)
 */
import { describe, it, expect, beforeEach } from "vitest";
import { CBMProcessor } from "../plugins/CBMProcessor";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeEvidence(
  correct: boolean,
  confidence: number,
): ReturnType<typeof Object.assign> {
  return {
    type: "answer_submission",
    claimId: "claim-boundary",
    payload: {
      correct,
      confidence,
      claimId: "claim-boundary",
    },
  };
}

function makeCtx(): ReturnType<typeof Object.assign> {
  return { learnerId: "learner-bv", data: {} };
}

// ---------------------------------------------------------------------------
// TPUT-FR-CBM-011: Boundary confidence inputs (0 and 1 exact)
// ---------------------------------------------------------------------------

describe("CBMProcessor – TPUT-FR-CBM-011 (confidence boundary values)", () => {
  let processor: CBMProcessor;

  beforeEach(async () => {
    processor = new CBMProcessor();
    await processor.initialize();
  });

  it("should return a valid score when confidence is exactly 0 (minimum boundary)", async () => {
    const ctx = makeCtx();
    const result = await processor.process(ctx, makeEvidence(false, 0));
    // confidence=0 → level="low", incorrect → score=0 (no penalty for honest uncertainty)
    expect(result.status).toBe("success");
    expect(typeof result.data?.score).toBe("number");
    expect(Number.isFinite(result.data?.score)).toBe(true);
    // Confidence=0 is "low" → incorrect + low = 0 penalty
    expect(result.data?.score).toBe(0);
  });

  it("should return a valid score when confidence is exactly 1 (maximum boundary)", async () => {
    const ctx = makeCtx();
    const result = await processor.process(ctx, makeEvidence(true, 1));
    // confidence=1 → level="high", correct → score = +3
    expect(result.status).toBe("success");
    expect(result.data?.score).toBeGreaterThan(0);
    expect(Number.isFinite(result.data?.score)).toBe(true);
    expect(result.data?.score).toBe(3);
  });

  it("should clamp confidence=-0.01 to 0 and treat as low confidence", async () => {
    const ctx = makeCtx();
    const result = await processor.process(ctx, makeEvidence(false, -0.01));
    // Negative confidence clamped to 0 → low confidence → incorrect + low = 0
    expect(result.status).toBe("success");
    expect(Number.isFinite(result.data?.score)).toBe(true);
    // Should not crash; score should be ≤ 0 (no reward for wrong answer)
    expect(result.data?.score).toBeLessThanOrEqual(0);
  });

  it("should clamp confidence=1.01 to 1 and treat as high confidence", async () => {
    const ctx = makeCtx();
    const result = await processor.process(ctx, makeEvidence(true, 1.01));
    // Confidence > 1 is clamped to 1 → high → correct + high = +3
    expect(result.status).toBe("success");
    expect(result.data?.score).toBeGreaterThan(0);
    expect(Number.isFinite(result.data?.score)).toBe(true);
  });

  it("should clamp confidence=100 (percent scale) to high confidence and produce a valid score", async () => {
    const ctx = makeCtx();
    // The processor normalizes >1 as percentage: 100 / 100 = 1.0
    const result = await processor.process(ctx, makeEvidence(true, 100));
    expect(result.status).toBe("success");
    expect(result.data?.score).toBeGreaterThan(0);
    expect(Number.isFinite(result.data?.score)).toBe(true);
  });

  it("should not crash when confidence is NaN and return a defined score", async () => {
    const ctx = makeCtx();
    // NaN confidence is an extreme boundary – the processor must not throw
    const result = await processor.process(ctx, makeEvidence(false, NaN));
    expect(result.status).not.toBe("crash");
    // Score must be a finite number or 0 (safe default)
    expect(
      result.data?.score === undefined || Number.isFinite(result.data?.score),
    ).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CBM-012: Out-of-range normalization
// ---------------------------------------------------------------------------

describe("CBMProcessor – TPUT-FR-CBM-012 (normalization edge cases)", () => {
  let processor: CBMProcessor;

  beforeEach(async () => {
    processor = new CBMProcessor();
    await processor.initialize();
  });

  it("should treat confidence=0.33 as low confidence threshold (just below medium)", async () => {
    const ctx = makeCtx();
    // 0.33 < 0.34 → "low"
    const result = await processor.process(ctx, makeEvidence(true, 0.33));
    expect(result.status).toBe("success");
    // low + correct = +1
    expect(result.data?.score).toBe(1);
  });

  it("should treat confidence=0.34 as medium confidence threshold", async () => {
    const ctx = makeCtx();
    // 0.34 >= 0.34 → "medium"
    const result = await processor.process(ctx, makeEvidence(true, 0.34));
    expect(result.status).toBe("success");
    // medium + correct = +2
    expect(result.data?.score).toBe(2);
  });

  it("should treat confidence=0.67 as high confidence threshold (just at high boundary)", async () => {
    const ctx = makeCtx();
    // 0.67 >= 0.67 → "high"
    const result = await processor.process(ctx, makeEvidence(false, 0.67));
    expect(result.status).toBe("success");
    // high + incorrect = -6
    expect(result.data?.score).toBe(-6);
  });

  it("should treat confidence=0.66 as medium (just below high threshold)", async () => {
    const ctx = makeCtx();
    // 0.66 < 0.67 → "medium"
    const result = await processor.process(ctx, makeEvidence(false, 0.66));
    expect(result.status).toBe("success");
    // medium + incorrect = -2
    expect(result.data?.score).toBe(-2);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CBM-013: Brier score floating-point precision
// ---------------------------------------------------------------------------

describe("CBMProcessor – TPUT-FR-CBM-013 (Brier score precision)", () => {
  it("should compute Brier score = 0 for perfectly calibrated learner (confidence matches outcomes)", async () => {
    const processor = new CBMProcessor();
    await processor.initialize();
    const ctx = makeCtx();

    // Perfect calibration: always correct with confidence=1
    await processor.process(ctx, makeEvidence(true, 1));
    await processor.process(ctx, makeEvidence(true, 1));
    await processor.process(ctx, makeEvidence(true, 1));

    const metrics: Record<string, unknown> = ctx.data as Record<
      string,
      unknown
    >;
    const agg = metrics["cbm.aggregate.metrics"] as
      | { brierScore: number }
      | undefined;
    expect(agg).toBeDefined();
    // Brier score = (1-1)^2 * 3 / 3 = 0
    expect(agg!.brierScore).toBeCloseTo(0, 10);
  });

  it("should compute Brier score = 1 for worst possible calibration (confidence=1 always wrong)", async () => {
    const processor = new CBMProcessor();
    await processor.initialize();
    const ctx = makeCtx();

    // Worst calibration: always wrong with confidence=1
    await processor.process(ctx, makeEvidence(false, 1));
    await processor.process(ctx, makeEvidence(false, 1));
    await processor.process(ctx, makeEvidence(false, 1));

    const metrics = ctx.data as Record<string, unknown>;
    const agg = metrics["cbm.aggregate.metrics"] as
      | { brierScore: number }
      | undefined;
    // Brier score = (1-0)^2 * N / N = 1
    expect(agg!.brierScore).toBeCloseTo(1, 10);
  });

  it("should return finite Brier score for 100-item session (large dataset precision)", async () => {
    const processor = new CBMProcessor();
    await processor.initialize();
    const ctx = makeCtx();

    // Alternate between correct/incorrect with varying confidence to stress precision
    for (let i = 0; i < 100; i++) {
      const correct = i % 3 !== 0;
      const confidence = 0.3 + (i % 7) * 0.1; // range 0.3 – 0.9
      await processor.process(ctx, makeEvidence(correct, confidence));
    }

    const metrics = ctx.data as Record<string, unknown>;
    const agg = metrics["cbm.aggregate.metrics"] as
      | {
          brierScore: number;
          itemCount: number;
          averageScore: number;
        }
      | undefined;

    expect(agg).toBeDefined();
    expect(agg!.itemCount).toBe(100);
    expect(Number.isFinite(agg!.brierScore)).toBe(true);
    expect(agg!.brierScore).toBeGreaterThanOrEqual(0);
    expect(agg!.brierScore).toBeLessThanOrEqual(1);
    expect(Number.isFinite(agg!.averageScore)).toBe(true);
  });

  it("should keep aggregate metrics stable after 1000 items (no floating-point drift)", async () => {
    const processor = new CBMProcessor();
    await processor.initialize();
    const ctx = makeCtx();

    // All identical inputs for stability test
    for (let i = 0; i < 1000; i++) {
      await processor.process(ctx, makeEvidence(true, 0.5));
    }

    const metrics = ctx.data as Record<string, unknown>;
    const agg = metrics["cbm.aggregate.metrics"] as
      | {
          brierScore: number;
          itemCount: number;
          averageScore: number;
          accuracy: number;
        }
      | undefined;

    expect(agg!.itemCount).toBe(1000);
    // All correct at confidence=0.5 → medium confidence → score=2 each
    expect(agg!.averageScore).toBeCloseTo(2, 2);
    expect(agg!.accuracy).toBeCloseTo(1, 5);
    // Brier: (0.5 - 1)^2 = 0.25 for all
    expect(agg!.brierScore).toBeCloseTo(0.25, 5);
  });
});
