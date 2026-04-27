/**
 * Model Version Registry and Regression Scorecard Tests
 *
 * Verifies:
 * 1. Model version registration and deprecation
 * 2. Prompt version registration and lifecycle (active/inactive)
 * 3. Generation run logging and aggregation
 * 4. Regression scorecard computation with trend detection
 *
 * @doc.type test
 * @doc.purpose Prove model/prompt version registry and regression scorecard correctness
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  ModelVersionRegistry,
  type ModelCapability,
  type RegisterModelVersionRequest,
  type RegisterPromptVersionRequest,
  type LogGenerationRunRequest,
} from "../model-version-registry.js";

// ─── Test helpers ──────────────────────────────────────────────────────────────

const logger = {
  info: vi.fn(),
  warn: vi.fn(),
  error: vi.fn(),
  debug: vi.fn(),
  fatal: vi.fn(),
  trace: vi.fn(),
  child: vi.fn().mockReturnThis(),
};

const prismaStub = {} as never;

function makeRegistry(): ModelVersionRegistry {
  return new ModelVersionRegistry(logger as never, prismaStub);
}

function makeModelRequest(overrides: Partial<RegisterModelVersionRequest> = {}): RegisterModelVersionRequest {
  return {
    modelFamily: "test-model",
    modelVersion: "test-model-v1-0",
    provider: "test-provider",
    contextWindowTokens: 8_192,
    capabilities: ["text_generation"] as ModelCapability[],
    notes: undefined,
    ...overrides,
  };
}

function makePromptRequest(overrides: Partial<RegisterPromptVersionRequest> = {}): RegisterPromptVersionRequest {
  return {
    promptKey: "test_claim_generator",
    version: "1.0.0",
    domain: "MATH",
    contentType: "claim",
    promptTemplate: "Generate a claim about {{topic}} for grade {{gradeLevel}}.",
    exampleOutput: undefined,
    parentVersion: undefined,
    changelog: "Initial version",
    createdBy: "test-user",
    ...overrides,
  };
}

function makeRunRequest(overrides: Partial<LogGenerationRunRequest> = {}): LogGenerationRunRequest {
  return {
    tenantId: "tenant-1",
    generationRequestId: "req-1",
    modelVersionId: "mv-test-model-test-model-v1-0",
    promptVersionId: "pv-test-claim-generator-1-0-0",
    domain: "MATH",
    contentType: "claim",
    artifactCount: 10,
    successCount: 9,
    failureCount: 1,
    avgTrustScore: 0.88,
    hallucinationCount: 0,
    autoPassCount: 7,
    humanReviewCount: 2,
    autoRemediateCount: 1,
    durationMs: 3_000,
    metadata: undefined,
    ...overrides,
  };
}

// ─── Tests: Model Versions ────────────────────────────────────────────────────

describe("ModelVersionRegistry - Model Versions", () => {
  let registry: ModelVersionRegistry;

  beforeEach(() => {
    registry = makeRegistry();
  });

  it("registers a new model version", () => {
    const entry = registry.registerModelVersion(makeModelRequest());

    expect(entry.id).toBeTruthy();
    expect(entry.modelVersion).toBe("test-model-v1-0");
    expect(entry.provider).toBe("test-provider");
    expect(entry.isActive).toBe(true);
    expect(entry.deprecatedAt).toBeUndefined();
    expect(entry.registeredAt).toBeTruthy();
  });

  it("returns the same entry when registering the same version twice (idempotent)", () => {
    const first = registry.registerModelVersion(makeModelRequest());
    const second = registry.registerModelVersion(makeModelRequest());

    expect(first.id).toBe(second.id);
    expect(logger.warn).toHaveBeenCalledWith(
      expect.objectContaining({ id: first.id }),
      expect.stringContaining("already registered"),
    );
  });

  it("getActiveModelVersions returns pre-seeded defaults plus registered", () => {
    registry.registerModelVersion(makeModelRequest());
    const active = registry.getActiveModelVersions();

    // Pre-seeded defaults include gpt-4, claude-3, llama-3
    expect(active.length).toBeGreaterThan(1);
    const ids = active.map((m) => m.modelFamily);
    expect(ids).toContain("gpt-4");
  });

  it("deprecateModelVersion marks the version inactive", () => {
    const entry = registry.registerModelVersion(makeModelRequest());
    registry.deprecateModelVersion(entry.id);

    const retrieved = registry.getModelVersion(entry.id);
    expect(retrieved?.isActive).toBe(false);
    expect(retrieved?.deprecatedAt).toBeTruthy();
  });

  it("deprecated version no longer appears in getActiveModelVersions", () => {
    const entry = registry.registerModelVersion(makeModelRequest());
    registry.deprecateModelVersion(entry.id);

    const active = registry.getActiveModelVersions();
    expect(active.find((m) => m.id === entry.id)).toBeUndefined();
  });

  it("throws when deprecating a non-existent model version", () => {
    expect(() => registry.deprecateModelVersion("nonexistent-id")).toThrow(
      "Model version not found",
    );
  });

  it("registered model has correct capabilities", () => {
    const entry = registry.registerModelVersion(
      makeModelRequest({
        capabilities: ["text_generation", "math_reasoning", "code_generation"],
      }),
    );

    expect(entry.capabilities).toContain("text_generation");
    expect(entry.capabilities).toContain("math_reasoning");
    expect(entry.capabilities).toContain("code_generation");
  });
});

// ─── Tests: Prompt Versions ───────────────────────────────────────────────────

describe("ModelVersionRegistry - Prompt Versions", () => {
  let registry: ModelVersionRegistry;

  beforeEach(() => {
    registry = makeRegistry();
  });

  it("registers a new prompt version", () => {
    const entry = registry.registerPromptVersion(makePromptRequest());

    expect(entry.id).toBeTruthy();
    expect(entry.promptKey).toBe("test_claim_generator");
    expect(entry.version).toBe("1.0.0");
    expect(entry.isActive).toBe(true);
    expect(entry.createdAt).toBeTruthy();
  });

  it("returns same entry when registering same version twice (idempotent)", () => {
    const first = registry.registerPromptVersion(makePromptRequest());
    const second = registry.registerPromptVersion(makePromptRequest());

    expect(first.id).toBe(second.id);
  });

  it("registering a new version deactivates the previous active version", () => {
    const v1 = registry.registerPromptVersion(makePromptRequest({ version: "1.0.0" }));
    expect(v1.isActive).toBe(true);

    registry.registerPromptVersion(makePromptRequest({ version: "2.0.0", parentVersion: "1.0.0" }));

    // v1 should now be inactive (it was deactivated when v2 was registered)
    const allVersions = registry.listPromptVersions("test_claim_generator");
    const v1Entry = allVersions.find((p) => p.version === "1.0.0");
    expect(v1Entry?.isActive).toBe(false);
  });

  it("getActivePromptVersion returns the active version for matching key/domain/type", () => {
    registry.registerPromptVersion(makePromptRequest());

    const active = registry.getActivePromptVersion(
      "test_claim_generator",
      "MATH",
      "claim",
    );

    expect(active).toBeDefined();
    expect(active?.version).toBe("1.0.0");
  });

  it("listPromptVersions returns versions sorted latest-first", () => {
    registry.registerPromptVersion(makePromptRequest({ version: "1.0.0" }));
    registry.registerPromptVersion(makePromptRequest({ version: "2.0.0", parentVersion: "1.0.0" }));

    const versions = registry.listPromptVersions("test_claim_generator");
    expect(versions[0].version).toBe("2.0.0");
  });

  it("prompt template is preserved", () => {
    const template = "Custom template {{domain}} {{topic}}";
    const entry = registry.registerPromptVersion(
      makePromptRequest({ promptTemplate: template }),
    );
    expect(entry.promptTemplate).toBe(template);
  });
});

// ─── Tests: Generation Run Logging ───────────────────────────────────────────

describe("ModelVersionRegistry - Generation Run Logging", () => {
  let registry: ModelVersionRegistry;

  beforeEach(() => {
    registry = makeRegistry();
  });

  it("logs a generation run with all fields", () => {
    const run = registry.logGenerationRun(makeRunRequest());

    expect(run.id).toBeTruthy();
    expect(run.tenantId).toBe("tenant-1");
    expect(run.artifactCount).toBe(10);
    expect(run.avgTrustScore).toBe(0.88);
    expect(run.runAt).toBeTruthy();
  });

  it("getGenerationRunsForRequest returns runs for that request", () => {
    registry.logGenerationRun(makeRunRequest({ generationRequestId: "req-A" }));
    registry.logGenerationRun(makeRunRequest({ generationRequestId: "req-A" }));
    registry.logGenerationRun(makeRunRequest({ generationRequestId: "req-B" }));

    const runsA = registry.getGenerationRunsForRequest("req-A");
    expect(runsA.length).toBe(2);
    expect(runsA.every((r) => r.generationRequestId === "req-A")).toBe(true);
  });

  it("runs for different requests are independent", () => {
    registry.logGenerationRun(makeRunRequest({ generationRequestId: "req-X" }));

    const runsY = registry.getGenerationRunsForRequest("req-Y");
    expect(runsY.length).toBe(0);
  });
});

// ─── Tests: Regression Scorecards ────────────────────────────────────────────

describe("ModelVersionRegistry - Regression Scorecards", () => {
  let registry: ModelVersionRegistry;

  const MODEL_ID = "mv-test-model-test-model-v1-0";
  const PROMPT_ID = "pv-test-claim-generator-1-0-0";

  beforeEach(() => {
    registry = makeRegistry();
    // Register model + prompt for baseline
    registry.registerModelVersion(makeModelRequest());
    registry.registerPromptVersion(makePromptRequest());
  });

  it("computeRegressionScorecard returns zero counts when no runs exist", () => {
    const now = new Date();
    const earlier = new Date(now.getTime() - 86400000);

    const scorecard = registry.computeRegressionScorecard(
      MODEL_ID,
      PROMPT_ID,
      "MATH",
      earlier,
      now,
    );

    expect(scorecard.test_count).toBe(0);
    expect(scorecard.pass_count).toBe(0);
    expect(scorecard.avg_trust_score).toBe(0);
    expect(scorecard.trend).toBe("stable");
  });

  it("computeRegressionScorecard aggregates runs within period", () => {
    const now = new Date();
    const earlier = new Date(now.getTime() - 86400000);

    registry.logGenerationRun(makeRunRequest({
      modelVersionId: MODEL_ID,
      promptVersionId: PROMPT_ID,
      artifactCount: 10,
      successCount: 9,
      avgTrustScore: 0.90,
      autoPassCount: 8,
      humanReviewCount: 1,
      autoRemediateCount: 1,
      hallucinationCount: 0,
    }));

    registry.logGenerationRun(makeRunRequest({
      modelVersionId: MODEL_ID,
      promptVersionId: PROMPT_ID,
      artifactCount: 5,
      successCount: 4,
      avgTrustScore: 0.80,
      autoPassCount: 3,
      humanReviewCount: 2,
      autoRemediateCount: 0,
      hallucinationCount: 1,
    }));

    const scorecard = registry.computeRegressionScorecard(
      MODEL_ID,
      PROMPT_ID,
      "MATH",
      earlier,
      now,
    );

    expect(scorecard.test_count).toBe(15);
    expect(scorecard.pass_count).toBe(13);
    expect(scorecard.auto_pass_count).toBeUndefined(); // not in RegressionScorecard directly
    expect(scorecard.avg_trust_score).toBeCloseTo(0.85, 1);
    expect(scorecard.hallucination_rate).toBeCloseTo(1 / 15, 2);
  });

  it("saveRegressionScorecard persists and listScorecards returns it", () => {
    const now = new Date();
    const earlier = new Date(now.getTime() - 86400000);
    const scorecard = registry.computeRegressionScorecard(
      MODEL_ID,
      PROMPT_ID,
      "MATH",
      earlier,
      now,
    );

    const entry = registry.saveRegressionScorecard(
      MODEL_ID,
      PROMPT_ID,
      "MATH",
      earlier,
      now,
      scorecard,
    );

    expect(entry.id).toBeTruthy();
    expect(entry.domain).toBe("MATH");

    const all = registry.listScorecards("MATH");
    expect(all.length).toBeGreaterThan(0);
    expect(all.find((s) => s.id === entry.id)).toBeDefined();
  });

  it("getLatestScorecardForDomain returns the most recent entry", () => {
    const now = new Date();
    const earlier = new Date(now.getTime() - 86400000);
    const scorecard = registry.computeRegressionScorecard(MODEL_ID, PROMPT_ID, "PHYSICS", earlier, now);

    const saved = registry.saveRegressionScorecard(MODEL_ID, PROMPT_ID, "PHYSICS", earlier, now, scorecard);
    const latest = registry.getLatestScorecardForDomain("PHYSICS");

    expect(latest).toBeDefined();
    expect(latest?.id).toBe(saved.id);
  });

  it("trend shows 'improving' when current avg_trust_score is significantly higher", () => {
    const now = new Date();
    const earlier = new Date(now.getTime() - 86400000);
    const veryEarly = new Date(now.getTime() - 2 * 86400000);

    // Save a baseline low scorecard
    registry.saveRegressionScorecard(
      MODEL_ID,
      PROMPT_ID,
      "MATH",
      veryEarly,
      earlier,
      {
        model_version: MODEL_ID,
        prompt_version: PROMPT_ID,
        timestamp: veryEarly.toISOString(),
        domain: "MATH",
        test_count: 10,
        pass_count: 7,
        avg_trust_score: 0.60,
        hallucination_rate: 0.1,
        auto_pass_rate: 0.5,
        human_review_rate: 0.3,
        auto_remediate_rate: 0.2,
        trend: "stable",
      },
    );

    // Log high-quality runs
    registry.logGenerationRun(makeRunRequest({
      modelVersionId: MODEL_ID,
      promptVersionId: PROMPT_ID,
      artifactCount: 10,
      successCount: 10,
      avgTrustScore: 0.95,
      autoPassCount: 9,
      humanReviewCount: 1,
      autoRemediateCount: 0,
      hallucinationCount: 0,
    }));

    const scorecard = registry.computeRegressionScorecard(MODEL_ID, PROMPT_ID, "MATH", earlier, now);
    expect(scorecard.trend).toBe("improving");
  });

  it("trend shows 'degrading' when current avg_trust_score drops significantly", () => {
    const now = new Date();
    const earlier = new Date(now.getTime() - 86400000);
    const veryEarly = new Date(now.getTime() - 2 * 86400000);

    // Save a baseline high scorecard
    registry.saveRegressionScorecard(
      MODEL_ID,
      PROMPT_ID,
      "MATH",
      veryEarly,
      earlier,
      {
        model_version: MODEL_ID,
        prompt_version: PROMPT_ID,
        timestamp: veryEarly.toISOString(),
        domain: "MATH",
        test_count: 10,
        pass_count: 9,
        avg_trust_score: 0.90,
        hallucination_rate: 0.0,
        auto_pass_rate: 0.8,
        human_review_rate: 0.1,
        auto_remediate_rate: 0.1,
        trend: "stable",
      },
    );

    // Log low-quality runs
    registry.logGenerationRun(makeRunRequest({
      modelVersionId: MODEL_ID,
      promptVersionId: PROMPT_ID,
      artifactCount: 10,
      successCount: 5,
      avgTrustScore: 0.50,
      autoPassCount: 3,
      humanReviewCount: 4,
      autoRemediateCount: 3,
      hallucinationCount: 2,
    }));

    const scorecard = registry.computeRegressionScorecard(MODEL_ID, PROMPT_ID, "MATH", earlier, now);
    expect(scorecard.trend).toBe("degrading");
  });
});
