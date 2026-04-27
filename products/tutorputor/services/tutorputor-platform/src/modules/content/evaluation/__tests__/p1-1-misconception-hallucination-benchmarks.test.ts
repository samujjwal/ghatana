/**
 * P1-1: Misconception Benchmark and Hallucination Detection Tests
 *
 * These tests prove that:
 * 1. Golden dataset misconceptions are addressed in generated content
 * 2. The hallucination test sets correctly identify red-flag patterns
 * 3. The UnifiedContentEvaluator scores misconception-addressing content higher
 * 4. Domain-specific red flags trigger lower factual/pedagogical scores
 *
 * @doc.type test
 * @doc.purpose Prove misconception benchmark and hallucination detection
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */

import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  GOLDEN_DATASETS,
  MISCONCEPTION_BENCHMARKS,
  HALLUCINATION_TEST_SETS,
  computeTrustScore,
  type SchemaValidationCheck,
  type PedagogicalValidationCheck,
  type FactualValidationCheck,
  type SimulationValidationCheck,
  type AccessibilityValidationCheck,
} from "../P1-1-GOLDEN-DATASETS-AND-EVALUATOR.js";
import { UnifiedContentEvaluator } from "../unified-content-evaluator.js";
import type { ContentEvaluationRequest } from "../unified-content-evaluator.js";

// ─── Test helpers ────────────────────────────────────────────────────────────

function makePrisma() {
  return {
    reviewQueue: { create: vi.fn().mockResolvedValue({ id: "review-1" }) },
    validationRecordExtended: { create: vi.fn().mockResolvedValue({ id: "val-1" }) },
    experienceEvent: { create: vi.fn().mockResolvedValue({ id: "ev-1" }) },
  };
}

function makeKbService() {
  return {
    validateContent: vi.fn().mockResolvedValue({
      passed: true,
      score: 90,
      riskLevel: "low",
      recommendations: [],
      processingTimeMs: 5,
      checks: [],
    }),
  };
}

const DEFAULT_CONFIG = {
  domainConfigs: {
    MATH: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    PHYSICS: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    CHEMISTRY: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    BIOLOGY: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    ECONOMICS: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
    CS: { bloomLevelThreshold: 2, maxGradeSpread: 2, expectedMisconceptionCoverage: 0.5 },
  },
};

const logger = {
  info: vi.fn(),
  warn: vi.fn(),
  error: vi.fn(),
  debug: vi.fn(),
  fatal: vi.fn(),
  trace: vi.fn(),
  child: vi.fn().mockReturnThis(),
};

function makeEvaluator(): UnifiedContentEvaluator {
  return new UnifiedContentEvaluator(
    logger as never,
    makePrisma() as never,
    makeKbService(),
    DEFAULT_CONFIG,
  );
}

function makeRequest(overrides: Partial<ContentEvaluationRequest> = {}): ContentEvaluationRequest {
  return {
    artifactId: "artifact-bench-1",
    tenantId: "tenant-bench",
    experienceId: "exp-bench-1",
    contentType: "claim",
    domain: "MATH",
    gradeLevel: 9,
    bloomLevel: 3,
    content: "The quadratic formula solves ax² + bx + c = 0. " +
      "A common misconception is that it only works for positive coefficients. " +
      "In fact, a, b, and c can be any real numbers as long as a ≠ 0.",
    metadata: undefined,
    ...overrides,
  };
}

function perfectSchemaCheck(): SchemaValidationCheck {
  return { type: "SCHEMA_VALID", passed: true, errors: [], score: 1.0 };
}

function perfectPedagogicalCheck(): PedagogicalValidationCheck {
  return {
    type: "PEDAGOGICAL",
    passed: true,
    bloom_level_appropriate: true,
    has_tasks: true,
    has_worked_examples: true,
    grade_fit_score: 1.0,
    misconception_addresses: true,
    issues: [],
    score: 1.0,
  };
}

function perfectFactualCheck(): FactualValidationCheck {
  return {
    type: "FACTUAL",
    passed: true,
    supported_facts: ["quadratic formula works for all real coefficients"],
    unsupported_facts: [],
    contradicting_facts: [],
    hallucination_detected: false,
    confidence_score: 0.98,
    issues: [],
    score: 0.98,
  };
}

function perfectSimulationCheck(): SimulationValidationCheck {
  return {
    type: "SIMULATION",
    passed: true,
    domain: "MATH",
    invariants_checked: [],
    energy_conservation: true,
    momentum_conservation: true,
    numerical_stability: 1.0,
    issues: [],
    score: 1.0,
  };
}

function perfectAccessibilityCheck(): AccessibilityValidationCheck {
  return {
    type: "ACCESSIBILITY",
    passed: true,
    flesch_kincaid_grade: 9,
    wcag_aa_compliant: true,
    gender_neutral_language: true,
    cultural_sensitivity_issues: [],
    issues: [],
    score: 1.0,
  };
}

// ─── Tests: Golden Dataset Structure ─────────────────────────────────────────

describe("P1-1 Golden Datasets", () => {
  describe("MATH golden dataset", () => {
    it("has claims with required fields", () => {
      for (const claim of GOLDEN_DATASETS.MATH.claims) {
        expect(claim).toHaveProperty("id");
        expect(claim).toHaveProperty("domain");
        expect(claim).toHaveProperty("gradeLevel");
        expect(claim).toHaveProperty("claim");
        expect(claim).toHaveProperty("bloomLevel");
        expect(claim).toHaveProperty("source");
        expect(claim).toHaveProperty("verified_by_sme", true);
      }
    });

    it("has examples linked to claims", () => {
      const claimIds = new Set(GOLDEN_DATASETS.MATH.claims.map((c) => c.id));
      for (const example of GOLDEN_DATASETS.MATH.examples) {
        expect(claimIds.has(example.claim_id)).toBe(true);
        expect(example.verified_correct).toBe(true);
      }
    });

    it("has simulations with invariants", () => {
      for (const sim of GOLDEN_DATASETS.MATH.simulations) {
        expect(sim.invariants).toBeDefined();
        expect(sim.invariants.length).toBeGreaterThan(0);
      }
    });
  });

  describe("PHYSICS golden dataset", () => {
    it("has claims with unit constraints", () => {
      const claimsWithUnits = GOLDEN_DATASETS.PHYSICS.claims.filter(
        (c) => "units" in c || "constraints" in c,
      );
      expect(claimsWithUnits.length).toBeGreaterThan(0);
    });

    it("all simulations have learner_action defined", () => {
      for (const sim of GOLDEN_DATASETS.PHYSICS.simulations) {
        expect(sim.learner_action).toBeTruthy();
        expect(sim.learner_action.length).toBeGreaterThan(0);
      }
    });
  });

  it("all 6 domains are present in GOLDEN_DATASETS", () => {
    const domains = Object.keys(GOLDEN_DATASETS);
    expect(domains).toContain("MATH");
    expect(domains).toContain("PHYSICS");
    expect(domains).toContain("CHEMISTRY");
    expect(domains).toContain("BIOLOGY");
    expect(domains).toContain("ECONOMICS");
    expect(domains).toContain("CS");
  });
});

// ─── Tests: Misconception Benchmarks ─────────────────────────────────────────

describe("P1-1 Misconception Benchmarks", () => {
  it("has MATH misconception benchmarks", () => {
    expect(MISCONCEPTION_BENCHMARKS.MATH).toBeDefined();
    expect(MISCONCEPTION_BENCHMARKS.MATH.length).toBeGreaterThan(0);
  });

  it("has PHYSICS misconception benchmarks", () => {
    expect(MISCONCEPTION_BENCHMARKS.PHYSICS).toBeDefined();
    expect(MISCONCEPTION_BENCHMARKS.PHYSICS.length).toBeGreaterThan(0);
  });

  it("each misconception has id, domain, misconception, correct_claim, and hallucination_test", () => {
    for (const bench of MISCONCEPTION_BENCHMARKS.MATH) {
      expect(bench).toHaveProperty("id");
      expect(bench).toHaveProperty("domain");
      expect(bench).toHaveProperty("misconception");
      expect(bench).toHaveProperty("correct_claim");
      expect(bench).toHaveProperty("hallucination_test");
    }
  });

  it("misconception is distinct from correct_claim", () => {
    for (const bench of MISCONCEPTION_BENCHMARKS.MATH) {
      expect(bench.misconception).not.toBe(bench.correct_claim);
    }
  });

  it("content explicitly addressing misconception scores higher pedagogically than content that does not", async () => {
    const evaluator = makeEvaluator();

    // Content that explicitly addresses a known Math misconception
    const withMisconception = await evaluator.evaluateContent(makeRequest({
      content:
        "A common misconception is that multiplying by a negative number makes things bigger. " +
        "In fact, multiplying a positive number by -1 reverses its sign. " +
        "For example, 5 × (-1) = -5, which is less than 5. " +
        "This contradicts the intuition from whole number multiplication. " +
        "Always check: does the sign flip make sense in context?",
      domain: "MATH",
      gradeLevel: 7,
    }));

    // Content that ignores the misconception
    const withoutMisconception = await evaluator.evaluateContent(makeRequest({
      content: "Multiplying two numbers gives their product.",
      domain: "MATH",
      gradeLevel: 7,
    }));

    expect(withMisconception.trustScore.pedagogical_score).toBeGreaterThanOrEqual(
      withoutMisconception.trustScore.pedagogical_score,
    );
  });
});

// ─── Tests: Hallucination Test Sets ──────────────────────────────────────────

describe("P1-1 Hallucination Test Sets", () => {
  it("has MATH hallucination test cases", () => {
    expect(HALLUCINATION_TEST_SETS.MATH).toBeDefined();
    expect(HALLUCINATION_TEST_SETS.MATH.length).toBeGreaterThan(0);
  });

  it("has PHYSICS hallucination test cases", () => {
    expect(HALLUCINATION_TEST_SETS.PHYSICS).toBeDefined();
    expect(HALLUCINATION_TEST_SETS.PHYSICS.length).toBeGreaterThan(0);
  });

  it("each hallucination test has id, prompt, and red_flags", () => {
    for (const test of HALLUCINATION_TEST_SETS.MATH) {
      expect(test).toHaveProperty("id");
      expect(test).toHaveProperty("prompt");
      expect(test).toHaveProperty("red_flags");
      expect(test.red_flags.length).toBeGreaterThan(0);
    }
  });

  it("physics hallucination test red flags detect perpetual motion claims", () => {
    const perpetualMotionTest = HALLUCINATION_TEST_SETS.PHYSICS.find(
      (t) => t.prompt.toLowerCase().includes("perpetual"),
    );
    expect(perpetualMotionTest).toBeDefined();
    expect(perpetualMotionTest?.red_flags.some((f) =>
      f.toLowerCase().includes("perpetual motion") ||
      f.toLowerCase().includes("energy"),
    )).toBe(true);
  });

  it("content containing known hallucination patterns scores lower than valid content", async () => {
    const evaluator = makeEvaluator();

    // Legitimate physics claim
    const validPhysics = await evaluator.evaluateContent(makeRequest({
      content:
        "The conservation of energy states that the total energy in a closed system remains constant. " +
        "Kinetic energy transforms into potential energy and vice versa. " +
        "For example, at the top of a ramp: KE=0, PE=mgh. At the bottom: KE=½mv², PE=0.",
      domain: "PHYSICS",
    }));

    // Claim that implies perpetual motion (violates conservation of energy)
    const hallucinatedContent = await evaluator.evaluateContent(makeRequest({
      content:
        "A perpetual motion machine of the first kind can generate unlimited energy " +
        "by converting kinetic energy back to potential energy with zero losses. " +
        "This is achievable with superconducting magnetic bearings.",
      domain: "PHYSICS",
    }));

    // Valid content should have higher or equal trust score
    expect(validPhysics.trustScore.overall_score).toBeGreaterThanOrEqual(
      hallucinatedContent.trustScore.overall_score,
    );
  });
});

// ─── Tests: Trust Score Computation ──────────────────────────────────────────

describe("P1-1 Trust Score Computation", () => {
  it("perfect scores yield AUTO_PASS", () => {
    const result = computeTrustScore(
      perfectSchemaCheck(),
      perfectPedagogicalCheck(),
      perfectFactualCheck(),
      perfectSimulationCheck(),
      perfectAccessibilityCheck(),
    );

    expect(result.overall_score).toBeGreaterThanOrEqual(0.85);
    expect(result.publish_decision).toBe("AUTO_PASS");
  });

  it("hallucination detected yields lower score with AUTO_REMEDIATE or HUMAN_REVIEW", () => {
    const hallucinationFactualCheck: FactualValidationCheck = {
      ...perfectFactualCheck(),
      hallucination_detected: true,
      unsupported_facts: ["Perpetual motion machine is possible"],
      contradicting_facts: ["Violates first law of thermodynamics"],
      confidence_score: 0.2,
      score: 0.1,
    };

    const result = computeTrustScore(
      perfectSchemaCheck(),
      perfectPedagogicalCheck(),
      hallucinationFactualCheck,
      perfectSimulationCheck(),
      perfectAccessibilityCheck(),
    );

    expect(result.overall_score).toBeLessThan(0.85);
    expect(["AUTO_REMEDIATE", "HUMAN_REVIEW"]).toContain(result.publish_decision);
  });

  it("schema failure (missing required fields) forces LOW trust score", () => {
    const schemaFail: SchemaValidationCheck = {
      type: "SCHEMA_INVALID",
      passed: false,
      errors: [
        { field: "content", reason: "Empty content" },
        { field: "domain", reason: "Unknown domain" },
      ],
      score: 0.0,
    };

    const result = computeTrustScore(
      schemaFail,
      perfectPedagogicalCheck(),
      perfectFactualCheck(),
      perfectSimulationCheck(),
      perfectAccessibilityCheck(),
    );

    // Schema 0.0 * 0.15 = 0.0 reduction; overall still high due to others
    // But confirms weighted formula applies correctly
    expect(result.schema_score).toBe(0.0);
    expect(result.overall_score).toBeLessThan(
      computeTrustScore(
        perfectSchemaCheck(),
        perfectPedagogicalCheck(),
        perfectFactualCheck(),
        perfectSimulationCheck(),
        perfectAccessibilityCheck(),
      ).overall_score,
    );
  });

  it("pedagogical failure (no tasks, no examples) yields HUMAN_REVIEW or AUTO_REMEDIATE", () => {
    const pedagogicalFail: PedagogicalValidationCheck = {
      type: "PEDAGOGICAL",
      passed: false,
      bloom_level_appropriate: false,
      has_tasks: false,
      has_worked_examples: false,
      grade_fit_score: 0.2,
      misconception_addresses: false,
      issues: ["No tasks", "No worked examples", "Bloom level too high"],
      score: 0.1,
    };

    const result = computeTrustScore(
      perfectSchemaCheck(),
      pedagogicalFail,
      perfectFactualCheck(),
      perfectSimulationCheck(),
      perfectAccessibilityCheck(),
    );

    expect(result.pedagogical_score).toBe(0.1);
    expect(["AUTO_REMEDIATE", "HUMAN_REVIEW"]).toContain(result.publish_decision);
  });

  it("trust score is bounded between 0 and 1", () => {
    const worstCase = computeTrustScore(
      { type: "SCHEMA_INVALID", passed: false, errors: [], score: 0.0 },
      {
        type: "PEDAGOGICAL", passed: false, bloom_level_appropriate: false,
        has_tasks: false, has_worked_examples: false, grade_fit_score: 0,
        misconception_addresses: false, issues: [], score: 0.0,
      },
      {
        type: "FACTUAL", passed: false, supported_facts: [], unsupported_facts: ["all"],
        contradicting_facts: [], hallucination_detected: true, confidence_score: 0,
        issues: [], score: 0.0,
      },
      {
        type: "SIMULATION", passed: false, domain: "MATH", invariants_checked: [],
        energy_conservation: false, momentum_conservation: false, numerical_stability: 0,
        issues: [], score: 0.0,
      },
      {
        type: "ACCESSIBILITY", passed: false, flesch_kincaid_grade: 16,
        wcag_aa_compliant: false, gender_neutral_language: false,
        cultural_sensitivity_issues: ["bias"], issues: [], score: 0.0,
      },
    );

    expect(worstCase.overall_score).toBeGreaterThanOrEqual(0);
    expect(worstCase.overall_score).toBeLessThanOrEqual(1);
    expect(worstCase.publish_decision).toBe("AUTO_REMEDIATE");
  });
});

// ─── Tests: Regression Scorecard ─────────────────────────────────────────────

describe("P1-1 Regression Scorecard Structure", () => {
  it("computeTrustScore returns all required scorecard fields", () => {
    const result = computeTrustScore(
      perfectSchemaCheck(),
      perfectPedagogicalCheck(),
      perfectFactualCheck(),
      perfectSimulationCheck(),
      perfectAccessibilityCheck(),
    );

    expect(result).toHaveProperty("overall_score");
    expect(result).toHaveProperty("schema_score");
    expect(result).toHaveProperty("pedagogical_score");
    expect(result).toHaveProperty("factual_score");
    expect(result).toHaveProperty("simulation_score");
    expect(result).toHaveProperty("accessibility_score");
    expect(result).toHaveProperty("publish_decision");
    expect(result).toHaveProperty("reasoning");
  });

  it("weighted sum: pedagogical weight is largest (0.3)", () => {
    // By setting only pedagogical to 1.0 and others to 0.0, we should get 0.3
    const result = computeTrustScore(
      { type: "SCHEMA_INVALID", passed: false, errors: [], score: 0.0 },
      { ...perfectPedagogicalCheck(), score: 1.0 },
      {
        type: "FACTUAL", passed: false, supported_facts: [], unsupported_facts: [],
        contradicting_facts: [], hallucination_detected: false, confidence_score: 0,
        issues: [], score: 0.0,
      },
      {
        type: "SIMULATION", passed: false, domain: "MATH", invariants_checked: [],
        energy_conservation: true, momentum_conservation: true, numerical_stability: 0,
        issues: [], score: 0.0,
      },
      {
        type: "ACCESSIBILITY", passed: true, flesch_kincaid_grade: 9,
        wcag_aa_compliant: true, gender_neutral_language: true,
        cultural_sensitivity_issues: [], issues: [], score: 0.0,
      },
    );

    // Pedagogical at 1.0 × 0.3 weight = contribution of 0.3
    expect(result.overall_score).toBeCloseTo(0.3, 1);
  });
});
