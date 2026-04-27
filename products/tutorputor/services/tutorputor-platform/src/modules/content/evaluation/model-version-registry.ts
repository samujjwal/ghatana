/**
 * Model Version and Prompt Version Registry
 *
 * Tracks every model version and prompt version used in content generation.
 * Enables regression scorecards to be correlated to specific generation runs,
 * driving quality-over-time analysis and rollback decisions.
 *
 * @doc.type class
 * @doc.purpose Track model and prompt versions to support content quality regression analysis
 * @doc.layer product
 * @doc.pattern Registry
 */

import type { Logger } from "pino";
import type { PrismaClient } from "@tutorputor/core/db";
import type { RegressionScorecard } from "./P1-1-GOLDEN-DATASETS-AND-EVALUATOR.js";

// ─── Types ────────────────────────────────────────────────────────────────────

export interface ModelVersionEntry {
  id: string;
  modelFamily: string;       // e.g. "gpt-4", "claude-3-opus", "llama-3"
  modelVersion: string;      // e.g. "gpt-4-0125-preview"
  provider: string;          // e.g. "openai", "anthropic", "ollama"
  contextWindowTokens: number;
  capabilities: ModelCapability[];
  isActive: boolean;
  deprecatedAt: string | undefined;
  registeredAt: string;
  notes: string | undefined;
}

export type ModelCapability =
  | "text_generation"
  | "code_generation"
  | "math_reasoning"
  | "simulation_generation"
  | "explanation_generation"
  | "assessment_generation";

export interface PromptVersionEntry {
  id: string;
  promptKey: string;         // stable logical key, e.g. "claim_generator_v2"
  version: string;           // semver, e.g. "2.3.1"
  domain: string | undefined;             // scoped domain or "ALL"
  contentType: string | undefined;        // e.g. "claim", "example", "simulation"
  promptTemplate: string;    // the actual prompt template with placeholders
  exampleOutput: string | undefined;      // canonical example for regression tests
  parentVersion: string | undefined;      // previous version for changelog
  changelog: string;         // human-readable change notes
  isActive: boolean;
  createdAt: string;
  createdBy: string | undefined;
}

export interface GenerationRunEntry {
  id: string;
  tenantId: string;
  generationRequestId: string;
  modelVersionId: string;
  promptVersionId: string;
  domain: string;
  contentType: string;
  artifactCount: number;
  successCount: number;
  failureCount: number;
  avgTrustScore: number | undefined;
  hallucinationCount: number;
  autoPassCount: number;
  humanReviewCount: number;
  autoRemediateCount: number;
  durationMs: number | undefined;
  runAt: string;
  metadata: Record<string, unknown> | undefined;
}

export interface RegressionScorecardEntry {
  id: string;
  modelVersionId: string;
  promptVersionId: string;
  domain: string;
  periodStart: string;
  periodEnd: string;
  scorecard: RegressionScorecard;
  createdAt: string;
}

export interface RegisterModelVersionRequest {
  modelFamily: string;
  modelVersion: string;
  provider: string;
  contextWindowTokens: number;
  capabilities: ModelCapability[];
  notes: string | undefined;
}

export interface RegisterPromptVersionRequest {
  promptKey: string;
  version: string;
  domain: string | undefined;
  contentType: string | undefined;
  promptTemplate: string;
  exampleOutput: string | undefined;
  parentVersion: string | undefined;
  changelog: string;
  createdBy: string | undefined;
}

export interface LogGenerationRunRequest {
  tenantId: string;
  generationRequestId: string;
  modelVersionId: string;
  promptVersionId: string;
  domain: string;
  contentType: string;
  artifactCount: number;
  successCount: number;
  failureCount: number;
  avgTrustScore: number | undefined;
  hallucinationCount: number;
  autoPassCount: number;
  humanReviewCount: number;
  autoRemediateCount: number;
  durationMs: number | undefined;
  metadata: Record<string, unknown> | undefined;
}

// ─── In-memory stores (replace with Prisma tables once schema migrations added) ──

function createDefaultModelVersions(): Map<string, ModelVersionEntry> {
  const store = new Map<string, ModelVersionEntry>();
  const now = new Date().toISOString();

  const defaultModels: Omit<ModelVersionEntry, "id" | "registeredAt">[] = [
    {
      modelFamily: "gpt-4",
      modelVersion: "gpt-4-turbo-2024-04-09",
      provider: "openai",
      contextWindowTokens: 128_000,
      capabilities: ["text_generation", "code_generation", "math_reasoning", "explanation_generation"],
      isActive: true,
      deprecatedAt: undefined,
      notes: "Default production model",
    },
    {
      modelFamily: "claude-3",
      modelVersion: "claude-3-opus-20240229",
      provider: "anthropic",
      contextWindowTokens: 200_000,
      capabilities: ["text_generation", "code_generation", "math_reasoning", "simulation_generation", "explanation_generation"],
      isActive: true,
      deprecatedAt: undefined,
      notes: "High-context window for simulation generation",
    },
    {
      modelFamily: "llama-3",
      modelVersion: "llama-3-8b-instruct",
      provider: "ollama",
      contextWindowTokens: 8_192,
      capabilities: ["text_generation", "explanation_generation"],
      isActive: true,
      deprecatedAt: undefined,
      notes: "Local fallback for offline/dev deployments",
    },
  ];

  for (const model of defaultModels) {
    const id = `mv-${model.modelFamily}-${model.modelVersion}`.replace(/[^a-z0-9-]/g, "-");
    store.set(id, { ...model, id, registeredAt: now });
  }

  return store;
}

function createDefaultPromptVersions(): Map<string, PromptVersionEntry> {
  const store = new Map<string, PromptVersionEntry>();
  const now = new Date().toISOString();

  const defaultPrompts: Omit<PromptVersionEntry, "id" | "createdAt">[] = [
    {
      promptKey: "claim_generator",
      version: "1.0.0",
      domain: undefined,
      contentType: "claim",
      promptTemplate:
        "Generate a pedagogically accurate claim for domain {{domain}} at grade level {{gradeLevel}} " +
        "aligned to Bloom's level {{bloomLevel}}. Include evidence grounding and address common misconceptions.",
      exampleOutput: undefined,
      parentVersion: undefined,
      changelog: "Initial prompt for claim generation",
      isActive: true,
      createdBy: undefined,
    },
    {
      promptKey: "example_generator",
      version: "1.0.0",
      domain: undefined,
      contentType: "example",
      promptTemplate:
        "Generate a worked example for the claim: {{claim}}. " +
        "Show step-by-step reasoning at grade {{gradeLevel}}. Include common mistakes to avoid.",
      exampleOutput: undefined,
      parentVersion: undefined,
      changelog: "Initial prompt for worked example generation",
      isActive: true,
      createdBy: undefined,
    },
    {
      promptKey: "simulation_manifest_generator",
      version: "1.0.0",
      domain: undefined,
      contentType: "simulation",
      promptTemplate:
        "Generate a simulation manifest JSON for domain {{domain}}, demonstrating concept: {{concept}}. " +
        "The simulation must have: title, description, parameters (with min/max/default), states, " +
        "invariants, and deterministic replay seed. Grade level: {{gradeLevel}}.",
      exampleOutput: undefined,
      parentVersion: undefined,
      changelog: "Initial prompt for simulation manifest generation",
      isActive: true,
      createdBy: undefined,
    },
    {
      promptKey: "misconception_benchmark",
      version: "1.0.0",
      domain: undefined,
      contentType: "claim",
      promptTemplate:
        "Given the known misconception '{{misconception}}' for domain {{domain}}, generate content " +
        "that correctly addresses this misconception and explicitly refutes it with evidence.",
      exampleOutput: undefined,
      parentVersion: undefined,
      changelog: "Prompt for misconception-addressing content generation",
      isActive: true,
      createdBy: undefined,
    },
  ];

  for (const prompt of defaultPrompts) {
    const id = `pv-${prompt.promptKey}-${prompt.version}`.replace(/[^a-z0-9-]/g, "-");
    store.set(id, { ...prompt, id, createdAt: now });
  }

  return store;
}

// ─── Service ──────────────────────────────────────────────────────────────────

/**
 * Registry for model versions, prompt versions, generation run logs, and regression scorecards.
 * Uses in-memory stores backed by structured logs until Prisma tables are added via migration.
 */
export class ModelVersionRegistry {
  private readonly modelVersionStore: Map<string, ModelVersionEntry>;
  private readonly promptVersionStore: Map<string, PromptVersionEntry>;
  private readonly generationRunStore: GenerationRunEntry[];
  private readonly regressionScorecardStore: RegressionScorecardEntry[];

  constructor(
    private readonly logger: Logger,
    // prisma reserved for future persistence migration
    private readonly _prisma: PrismaClient,
  ) {
    this.modelVersionStore = createDefaultModelVersions();
    this.promptVersionStore = createDefaultPromptVersions();
    this.generationRunStore = [];
    this.regressionScorecardStore = [];
  }

  // ─── Model Versions ───────────────────────────────────────────────────────

  registerModelVersion(req: RegisterModelVersionRequest): ModelVersionEntry {
    const id = `mv-${req.modelFamily}-${req.modelVersion}`.replace(/[^a-z0-9-]/g, "-");

    if (this.modelVersionStore.has(id)) {
      this.logger.warn({ id, modelVersion: req.modelVersion }, "Model version already registered; skipping");
      return this.modelVersionStore.get(id)!;
    }

    const entry: ModelVersionEntry = {
      id,
      ...req,
      isActive: true,
      deprecatedAt: undefined,
      registeredAt: new Date().toISOString(),
    };

    this.modelVersionStore.set(id, entry);
    this.logger.info({ id, modelVersion: req.modelVersion, provider: req.provider }, "Registered model version");
    return entry;
  }

  deprecateModelVersion(id: string): void {
    const entry = this.modelVersionStore.get(id);
    if (!entry) {
      throw new Error(`Model version not found: ${id}`);
    }
    entry.isActive = false;
    entry.deprecatedAt = new Date().toISOString();
    this.logger.info({ id }, "Deprecated model version");
  }

  getActiveModelVersions(): ModelVersionEntry[] {
    return Array.from(this.modelVersionStore.values()).filter((m) => m.isActive);
  }

  getModelVersion(id: string): ModelVersionEntry | undefined {
    return this.modelVersionStore.get(id);
  }

  // ─── Prompt Versions ──────────────────────────────────────────────────────

  registerPromptVersion(req: RegisterPromptVersionRequest): PromptVersionEntry {
    const id = `pv-${req.promptKey}-${req.version}`.replace(/[^a-z0-9-]/g, "-");

    if (this.promptVersionStore.has(id)) {
      this.logger.warn({ id, promptKey: req.promptKey, version: req.version }, "Prompt version already registered; skipping");
      return this.promptVersionStore.get(id)!;
    }

    // Deactivate prior versions of same promptKey+domain+contentType
    for (const [, existing] of this.promptVersionStore) {
      if (
        existing.promptKey === req.promptKey &&
        existing.domain === req.domain &&
        existing.contentType === req.contentType &&
        existing.isActive
      ) {
        existing.isActive = false;
        this.logger.info(
          { id: existing.id, version: existing.version },
          "Deactivated prior prompt version",
        );
      }
    }

    const entry: PromptVersionEntry = {
      id,
      ...req,
      isActive: true,
      createdAt: new Date().toISOString(),
    };

    this.promptVersionStore.set(id, entry);
    this.logger.info({ id, promptKey: req.promptKey, version: req.version }, "Registered prompt version");
    return entry;
  }

  getActivePromptVersion(
    promptKey: string,
    domain: string | undefined,
    contentType: string | undefined,
  ): PromptVersionEntry | undefined {
    for (const [, entry] of this.promptVersionStore) {
      if (
        entry.promptKey === promptKey &&
        entry.isActive &&
        (entry.domain === undefined || entry.domain === domain) &&
        (entry.contentType === undefined || entry.contentType === contentType)
      ) {
        return entry;
      }
    }
    return undefined;
  }

  listPromptVersions(promptKey: string): PromptVersionEntry[] {
    return Array.from(this.promptVersionStore.values())
      .filter((p) => p.promptKey === promptKey)
      .sort((a, b) => {
        const timeDiff = b.createdAt.localeCompare(a.createdAt);
        if (timeDiff !== 0) return timeDiff;
        // Tiebreak by version string descending ("2.0.0" > "1.0.0")
        return b.version.localeCompare(a.version, undefined, { numeric: true });
      });
  }

  // ─── Generation Run Logging ───────────────────────────────────────────────

  logGenerationRun(req: LogGenerationRunRequest): GenerationRunEntry {
    const id = `run-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    const entry: GenerationRunEntry = {
      id,
      ...req,
      runAt: new Date().toISOString(),
    };

    this.generationRunStore.push(entry);

    this.logger.info(
      {
        id,
        domain: req.domain,
        modelVersionId: req.modelVersionId,
        promptVersionId: req.promptVersionId,
        artifactCount: req.artifactCount,
        avgTrustScore: req.avgTrustScore,
        autoPassCount: req.autoPassCount,
      },
      "Logged generation run",
    );

    return entry;
  }

  getGenerationRunsForRequest(generationRequestId: string): GenerationRunEntry[] {
    return this.generationRunStore.filter((r) => r.generationRequestId === generationRequestId);
  }

  // ─── Regression Scorecards ────────────────────────────────────────────────

  saveRegressionScorecard(
    modelVersionId: string,
    promptVersionId: string,
    domain: string,
    periodStart: Date,
    periodEnd: Date,
    scorecard: RegressionScorecard,
  ): RegressionScorecardEntry {
    const id = `sc-${domain}-${modelVersionId}-${Date.now()}`;
    const entry: RegressionScorecardEntry = {
      id,
      modelVersionId,
      promptVersionId,
      domain,
      periodStart: periodStart.toISOString(),
      periodEnd: periodEnd.toISOString(),
      scorecard,
      createdAt: new Date().toISOString(),
    };

    this.regressionScorecardStore.push(entry);

    this.logger.info(
      {
        id,
        domain,
        modelVersionId,
        avgTrustScore: scorecard.avg_trust_score,
        passCount: scorecard.pass_count,
        testCount: scorecard.test_count,
        trend: scorecard.trend,
      },
      "Saved regression scorecard",
    );

    return entry;
  }

  /**
   * Compute a regression scorecard from the generation run history.
   * Aggregates runs for a given model/prompt pair over the given period.
   */
  computeRegressionScorecard(
    modelVersionId: string,
    promptVersionId: string,
    domain: string,
    periodStart: Date,
    periodEnd: Date,
  ): RegressionScorecard {
    const startIso = periodStart.toISOString();
    const endIso = periodEnd.toISOString();

    const runs = this.generationRunStore.filter(
      (r) =>
        r.modelVersionId === modelVersionId &&
        r.promptVersionId === promptVersionId &&
        r.domain === domain &&
        r.runAt >= startIso &&
        r.runAt <= endIso,
    );

    const testCount = runs.reduce((sum, r) => sum + r.artifactCount, 0);
    const passCount = runs.reduce((sum, r) => sum + r.successCount, 0);
    const autoPassCount = runs.reduce((sum, r) => sum + r.autoPassCount, 0);
    const humanReviewCount = runs.reduce((sum, r) => sum + r.humanReviewCount, 0);
    const autoRemediateCount = runs.reduce((sum, r) => sum + r.autoRemediateCount, 0);
    const hallucinationCount = runs.reduce((sum, r) => sum + r.hallucinationCount, 0);

    const scoredRuns = runs.filter((r) => r.avgTrustScore !== undefined);
    const avgTrustScore =
      scoredRuns.length > 0
        ? scoredRuns.reduce((sum, r) => sum + (r.avgTrustScore ?? 0), 0) / scoredRuns.length
        : 0;

    const hallucinationRate = testCount > 0 ? hallucinationCount / testCount : 0;
    const autoPassRate = testCount > 0 ? autoPassCount / testCount : 0;
    const humanReviewRate = testCount > 0 ? humanReviewCount / testCount : 0;
    const autoRemediateRate = testCount > 0 ? autoRemediateCount / testCount : 0;

    // Compute trend by comparing to previous period
    const trend = this.computeTrend(
      modelVersionId,
      promptVersionId,
      domain,
      avgTrustScore,
    );

    return {
      model_version: modelVersionId,
      prompt_version: promptVersionId,
      timestamp: new Date().toISOString(),
      domain,
      test_count: testCount,
      pass_count: passCount,
      avg_trust_score: avgTrustScore,
      hallucination_rate: hallucinationRate,
      auto_pass_rate: autoPassRate,
      human_review_rate: humanReviewRate,
      auto_remediate_rate: autoRemediateRate,
      trend,
    };
  }

  getLatestScorecardForDomain(domain: string): RegressionScorecardEntry | undefined {
    const domain_scorecards = this.regressionScorecardStore
      .filter((s) => s.domain === domain)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return domain_scorecards[0];
  }

  listScorecards(domain: string | undefined): RegressionScorecardEntry[] {
    return this.regressionScorecardStore
      .filter((s) => domain === undefined || s.domain === domain)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  // ─── Private helpers ──────────────────────────────────────────────────────

  private computeTrend(
    modelVersionId: string,
    promptVersionId: string,
    domain: string,
    currentAvgScore: number,
  ): "improving" | "stable" | "degrading" {
    const previous = this.regressionScorecardStore
      .filter(
        (s) =>
          s.modelVersionId === modelVersionId &&
          s.promptVersionId === promptVersionId &&
          s.domain === domain,
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];

    if (!previous) return "stable";

    const delta = currentAvgScore - previous.scorecard.avg_trust_score;
    if (delta > 0.03) return "improving";
    if (delta < -0.03) return "degrading";
    return "stable";
  }
}

