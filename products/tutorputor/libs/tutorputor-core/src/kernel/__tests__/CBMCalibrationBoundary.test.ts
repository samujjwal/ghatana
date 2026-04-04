/**
 * CBM Processor – Calibration Index Boundary Tests
 *
 * The audit report identified incomplete testing of the calibration index
 * boundary at 0.3 (the viva trigger threshold) and gaps in Brier score
 * edge-case coverage.
 *
 * This file targets those specific gaps:
 *  1. Calibration index boundary at exactly 0.3 – no viva trigger below,
 *     viva trigger above.
 *  2. Underconfidence detection – calibration < −0.3 triggers a viva for a
 *     different reason (underconfidence vs overconfidence).
 *  3. Brier score edge cases – all-correct high-confidence → near 0,
 *     all-wrong high-confidence → near 1, mixed scenarios.
 *  4. Viva trigger fires only after ≥ 3 items.
 *  5. Per-custom-config matrix values propagate correctly.
 *
 * All tests validate pedagogical theory (expected behaviour), NOT the
 * internal formula implementation.
 *
 * @doc.type test
 * @doc.purpose CBM calibration index boundary validation and Brier score edge cases
 * @doc.layer plugin
 * @doc.pattern UnitTest
 *
 * Requirement IDs: TPUT-FR-CBM-005 (session aggregation),
 *                  TPUT-FR-CBM-006 (viva trigger threshold),
 *                  TPUT-FR-CBM-008 (underconfidence detection),
 *                  TPUT-FR-CBM-009 (Brier score accuracy)
 */
import { describe, it, expect, beforeEach } from "vitest";
import { CBMProcessor } from "../plugins/CBMProcessor";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeAnswer(opts: {
  correct: boolean;
  confidence: number;
  claimId?: string;
  evidenceId?: string;
}): any {
  const eid =
    opts.evidenceId ?? `evidence-${Math.random().toString(36).slice(2)}`;
  return {
    type: "answer_submission",
    evidenceId: eid,
    claimId: opts.claimId ?? "claim-x",
    payload: {
      correct: opts.correct,
      confidence: opts.confidence,
      claimId: opts.claimId ?? "claim-x",
      evidenceId: eid,
    },
  };
}

function makeContext(): any {
  return { learnerId: "learner-1", data: {} };
}

/**
 * Process `n` identical answer events through the processor.
 * Returns the aggregate metrics from the context after all events.
 */
async function processN(
  processor: CBMProcessor,
  n: number,
  correct: boolean,
  confidence: number,
): Promise<Record<string, unknown>> {
  const ctx = makeContext();
  for (let i = 0; i < n; i++) {
    await processor.process(
      ctx,
      makeAnswer({ correct, confidence, evidenceId: `ev-${i}` }),
    );
  }
  return ctx.data["cbm.aggregate.metrics"] as Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// TPUT-FR-CBM-008 / TPUT-FR-CBM-006: Calibration index boundary at 0.3
// ---------------------------------------------------------------------------

describe("CBMProcessor – TPUT-FR-CBM-006 (viva trigger threshold boundary)", () => {
  let processor: CBMProcessor;

  beforeEach(async () => {
    processor = new CBMProcessor();
    await processor.initialize();
  });

  it("does NOT trigger a viva when calibration index is exactly 0.3 after 3 items (boundary is exclusive)", async () => {
    // Exact boundary case:
    // one incorrect answer at 0.9 confidence => delta = 0.9
    // two correct answers at 1.0 confidence => delta = 0.0 each
    // calibrationIndex = (0.9 + 0 + 0) / 3 = 0.3 exactly
    const ctx = makeContext();
    await processor.process(
      ctx,
      makeAnswer({ correct: false, confidence: 0.9, evidenceId: "ev-0" }),
    );
    await processor.process(
      ctx,
      makeAnswer({ correct: true, confidence: 1.0, evidenceId: "ev-1" }),
    );
    await processor.process(
      ctx,
      makeAnswer({ correct: true, confidence: 1.0, evidenceId: "ev-2" }),
    );

    const metrics = ctx.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;
    expect(metrics).toBeDefined();
    expect(metrics.calibrationIndex as number).toBeCloseTo(0.3, 5);
    expect(ctx.data["cbm.trigger.viva"]).toBeUndefined();
  });

  it("triggers a viva when calibration index exceeds 0.3 after 3 overconfident-wrong answers", async () => {
    // 3 high-confidence wrong answers: delta = 0.9 - 0 = 0.9 per item → CI = 0.9 > 0.3
    const ctx = makeContext();
    for (let i = 0; i < 3; i++) {
      await processor.process(
        ctx,
        makeAnswer({ correct: false, confidence: 0.9, evidenceId: `ev-${i}` }),
      );
    }

    const vivaTrigger = ctx.data["cbm.trigger.viva"] as
      | Record<string, unknown>
      | undefined;
    expect(vivaTrigger).toBeDefined();
    expect(vivaTrigger!.reason).toBe("overconfidence");
    expect(vivaTrigger!.calibrationIndex as number).toBeGreaterThan(0.3);
  });

  it("does NOT trigger a viva after only 2 items even if calibration index exceeds 0.3", async () => {
    // Minimum for trigger is 3 items
    const ctx = makeContext();
    for (let i = 0; i < 2; i++) {
      await processor.process(
        ctx,
        makeAnswer({ correct: false, confidence: 0.95, evidenceId: `ev-${i}` }),
      );
    }

    // No viva yet with only 2 items
    expect(ctx.data["cbm.trigger.viva"]).toBeUndefined();
  });

  it("triggers a viva immediately when item count reaches 3 and calibration crosses threshold", async () => {
    const ctx = makeContext();

    // 2 items: no trigger yet
    await processor.process(
      ctx,
      makeAnswer({ correct: false, confidence: 0.9, evidenceId: "ev-0" }),
    );
    await processor.process(
      ctx,
      makeAnswer({ correct: false, confidence: 0.9, evidenceId: "ev-1" }),
    );
    expect(ctx.data["cbm.trigger.viva"]).toBeUndefined();

    // 3rd item: trigger must fire
    await processor.process(
      ctx,
      makeAnswer({ correct: false, confidence: 0.9, evidenceId: "ev-2" }),
    );
    expect(ctx.data["cbm.trigger.viva"]).toBeDefined();
  });

  it("viva trigger metadata includes the calibration threshold value", async () => {
    const ctx = makeContext();
    for (let i = 0; i < 3; i++) {
      await processor.process(
        ctx,
        makeAnswer({ correct: false, confidence: 0.9, evidenceId: `ev-${i}` }),
      );
    }

    const vivaTrigger = ctx.data["cbm.trigger.viva"] as Record<string, unknown>;
    expect(typeof vivaTrigger.threshold).toBe("number");
    expect(vivaTrigger.threshold).toBeCloseTo(0.3, 5);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CBM-008: Underconfidence detection
// ---------------------------------------------------------------------------

describe("CBMProcessor – TPUT-FR-CBM-008 (underconfidence detection)", () => {
  let processor: CBMProcessor;

  beforeEach(async () => {
    processor = new CBMProcessor();
    await processor.initialize();
  });

  it("detects underconfidence when learner always answers correctly with very low confidence", async () => {
    // 3 answers: correct=true, confidence=0.1 → delta = 0.1 - 1 = -0.9 → CI = -0.9 < -0.3
    const ctx = makeContext();
    for (let i = 0; i < 3; i++) {
      await processor.process(
        ctx,
        makeAnswer({ correct: true, confidence: 0.1, evidenceId: `ev-${i}` }),
      );
    }

    const metrics = ctx.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;
    expect(metrics.calibrationIndex as number).toBeLessThan(-0.3);

    const vivaTrigger = ctx.data["cbm.trigger.viva"] as
      | Record<string, unknown>
      | undefined;
    expect(vivaTrigger).toBeDefined();
    expect(vivaTrigger!.reason).toBe("underconfidence");
  });

  it("calibration index is negative for consistently underconfident but correct learners", async () => {
    const ctx = makeContext();
    // Always correct but always guessing (low confidence)
    for (let i = 0; i < 5; i++) {
      await processor.process(
        ctx,
        makeAnswer({ correct: true, confidence: 0.15, evidenceId: `ev-${i}` }),
      );
    }

    const metrics = ctx.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;
    expect(metrics.calibrationIndex as number).toBeLessThan(0);
  });

  it("well-calibrated learner has calibration index near zero (high confidence + correct)", async () => {
    // confidence=1.0, correct=true → delta = 1.0 - 1 = 0.0 → CI = 0
    const ctx = makeContext();
    for (let i = 0; i < 5; i++) {
      await processor.process(
        ctx,
        makeAnswer({ correct: true, confidence: 1.0, evidenceId: `ev-${i}` }),
      );
    }

    const metrics = ctx.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;
    expect(Math.abs(metrics.calibrationIndex as number)).toBeLessThan(0.01);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CBM-009: Brier score edge cases
// ---------------------------------------------------------------------------

describe("CBMProcessor – TPUT-FR-CBM-009 (Brier score edge cases)", () => {
  let processor: CBMProcessor;

  beforeEach(async () => {
    processor = new CBMProcessor();
    await processor.initialize();
  });

  it("Brier score approaches 0 when learner is perfectly calibrated (high confidence + all correct)", async () => {
    // Perfect: confidence=1.0, correct=true → (1.0 - 1)² = 0 per item
    const metrics = await processN(processor, 5, true, 1.0);
    expect(metrics.brierScore as number).toBeCloseTo(0, 4);
  });

  it("Brier score approaches 1 when all answers are high-confidence wrong", async () => {
    // Worst case: confidence=1.0, correct=false → (1.0 - 0)² = 1.0 per item
    const metrics = await processN(processor, 5, false, 1.0);
    expect(metrics.brierScore as number).toBeCloseTo(1.0, 4);
  });

  it("Brier score is 0.25 for medium confidence (0.5) answers regardless of correctness", async () => {
    // Correct: (0.5 - 1)² = 0.25; Wrong: (0.5 - 0)² = 0.25
    const metricsCorrect = await processN(processor, 4, true, 0.5);
    const metricsWrong = await processN(processor, 4, false, 0.5);

    expect(metricsCorrect.brierScore as number).toBeCloseTo(0.25, 3);
    expect(metricsWrong.brierScore as number).toBeCloseTo(0.25, 3);
  });

  it("Brier score for mixed answers is the mean of individual squared errors", async () => {
    const ctx = makeContext();
    // Item 1: confidence=0.9, correct=true  → (0.9-1)² = 0.01
    // Item 2: confidence=0.9, correct=false → (0.9-0)² = 0.81
    // Expected mean Brier = (0.01 + 0.81) / 2 = 0.41
    await processor.process(
      ctx,
      makeAnswer({ correct: true, confidence: 0.9, evidenceId: "ev-0" }),
    );
    await processor.process(
      ctx,
      makeAnswer({ correct: false, confidence: 0.9, evidenceId: "ev-1" }),
    );

    const metrics = ctx.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;
    expect(metrics.brierScore as number).toBeCloseTo(0.41, 3);
  });

  it("Brier score increases when items with large prediction errors are added", async () => {
    const ctxGood = makeContext();
    const ctxBad = makeContext();

    // Good calibration: confidence=1.0, correct=true
    for (let i = 0; i < 3; i++) {
      await processor.process(
        ctxGood,
        makeAnswer({ correct: true, confidence: 1.0, evidenceId: `ev-${i}` }),
      );
    }

    // Bad calibration: confidence=1.0, correct=false
    for (let i = 0; i < 3; i++) {
      await processor.process(
        ctxBad,
        makeAnswer({ correct: false, confidence: 1.0, evidenceId: `ev-${i}` }),
      );
    }

    const goodMetrics = ctxGood.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;
    const badMetrics = ctxBad.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;

    expect(badMetrics.brierScore as number).toBeGreaterThan(
      goodMetrics.brierScore as number,
    );
  });

  it("Brier score is defined and finite after a single item", async () => {
    const ctx = makeContext();
    await processor.process(
      ctx,
      makeAnswer({ correct: true, confidence: 0.7, evidenceId: "ev-0" }),
    );

    const metrics = ctx.data["cbm.aggregate.metrics"] as Record<
      string,
      unknown
    >;
    expect(metrics.brierScore).toBeDefined();
    expect(Number.isFinite(metrics.brierScore as number)).toBe(true);
    // (0.7 - 1)^2 = 0.09
    expect(metrics.brierScore as number).toBeCloseTo(0.09, 4);
  });
});

// ---------------------------------------------------------------------------
// TPUT-FR-CBM-005: Custom config propagation
// ---------------------------------------------------------------------------

describe("CBMProcessor – TPUT-FR-CBM-005 (custom config matrix)", () => {
  it("uses overridden scoring values when a custom config is supplied", async () => {
    const custom = new CBMProcessor({
      correctHighConfidence: 10,
      incorrectHighConfidence: -20,
    });
    await custom.initialize();

    const ctx = makeContext();
    const result = await custom.process(
      ctx,
      makeAnswer({ correct: true, confidence: 0.9, evidenceId: "ev-0" }),
    );

    expect(result.status).toBe("success");
    expect(result.data!.score).toBe(10);
  });

  it("applies overridden penalty for overconfident wrong answers from custom config", async () => {
    const custom = new CBMProcessor({
      incorrectHighConfidence: -50,
    });
    await custom.initialize();

    const ctx = makeContext();
    const result = await custom.process(
      ctx,
      makeAnswer({ correct: false, confidence: 0.9, evidenceId: "ev-0" }),
    );

    expect(result.data!.score).toBe(-50);
  });
});
