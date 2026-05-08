/**
 * Generation Planner Service
 *
 * Makes generation explicit, typed, and governable before content
 * execution begins. Handles creation, planning, and lifecycle
 * management of generation requests and their associated jobs.
 *
 * Planning logic determines:
 *  - What assets/artifacts to generate (asset graph)
 *  - Risk level and risk factors
 *  - Review routing path
 *  - Cost estimation
 *
 * @doc.type class
 * @doc.purpose Generation request lifecycle and planning logic
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import { Prisma } from "@tutorputor/core/db";
import type { RiskLevel } from "@tutorputor/contracts/v1/types";
import {
  IntelligentContentCache,
  type CachedPlanningBlueprint,
} from "../cache/intelligent-cache.js";
import { CostAwareGenerationRouter } from "../routing/cost-aware-router.js";
import type Redis from "ioredis";
import {
  type ContentGenerationFlags,
  isFeatureEnabled,
  loadFeatureFlags,
} from "../../../config/feature-flags.js";

import type {
  GenerationJobType,
  GenerationRequestConfig,
  CreateGenerationRequestInput,
  PlannedAssetDescriptor,
  GenerationCostEstimate,
  GenerationRoutingDecision,
  GenerationJob,
  GenerationRequest,
  GenerationRequestWithJobs,
  PlanningResult,
  ReviewPath,
} from "../types.js";
import {
  buildTenantScopedWhere,
  assertSameTenant,
} from "../../policy/resource-access-helpers.js";
import {
  mapRequest,
  mapJob,
  enumToReviewPath,
  statusToDb,
  jobTypeToDb,
  riskLevelToDb,
  normalizeDomain,
  normalizeGrade,
} from "./generation-mappers.js";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/**
 * Artifact types to generate per domain asset.
 * Each concept-level generation request produces this standard set.
 */
const STANDARD_ARTIFACT_SET: GenerationJobType[] = [
  "claim",
  "explainer",
  "worked_example",
  "assessment",
];

/**
 * Extended artifact set for domains that benefit from visual content.
 * Updated to include all TutorPutor target domains for visual-first pedagogy.
 */
const VISUAL_DOMAINS = new Set([
  "math",
  "science",
  "tech",
  "engineering",
  "medicine",
  "health",
  "business",
  "management",
  "economics",
  "computer_science",
  "interdisciplinary",
]);

/**
 * Rough token estimates per job type (for cost estimation).
 */
const TOKEN_ESTIMATES: Record<string, number> = {
  claim: 500,
  explainer: 2000,
  worked_example: 1500,
  simulation: 3000,
  animation: 2500,
  assessment: 1800,
  evaluation: 800,
};

/**
 * Risk keywords that elevate risk level.
 */
const HIGH_RISK_KEYWORDS = [
  "medical",
  "health",
  "safety",
  "legal",
  "financial",
  "chemical",
  "hazard",
];

const MEDIUM_RISK_KEYWORDS = [
  "controversial",
  "political",
  "religious",
  "sensitive",
];

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class GenerationPlannerService {
  private readonly cache: IntelligentContentCache;
  private readonly router = new CostAwareGenerationRouter();

  constructor(
    private readonly prisma: PrismaClient,
    private readonly featureFlags: ContentGenerationFlags = loadFeatureFlags(),
    redis?: Redis,
  ) {
    this.cache = new IntelligentContentCache(redis);
  }

  /**
   * Create a new generation request in DRAFT status.
   */
  async createRequest(
    input: CreateGenerationRequestInput,
  ): Promise<GenerationRequest> {
    const row = await this.prisma.generationRequest.create({
      data: {
        tenantId: input.tenantId,
        title: input.title,
        description: input.description ?? null,
        domain: input.domain,
        conceptId: input.conceptId ?? null,
        targetGrades:
          input.targetGrades != null
            ? (input.targetGrades as Prisma.InputJsonValue)
            : Prisma.JsonNull,
        requestedBy: input.requestedBy,
        requestConfig:
          input.requestConfig != null
            ? (input.requestConfig as Prisma.InputJsonValue)
            : Prisma.JsonNull,
        status: "DRAFT",
      },
    });
    return mapRequest(row);
  }

  /**
   * Retrieve a generation request with all its jobs.
   */
  async getRequest(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationRequestWithJobs | null> {
    const row = await this.prisma.generationRequest.findFirst({
      where: buildTenantScopedWhere(tenantId, requestId),
      include: { jobs: true },
    });
    if (!row) return null;
    return {
      ...mapRequest(row),
      jobs: (row.jobs ?? []).map(mapJob),
    };
  }

  /**
   * List generation requests for a tenant, optionally filtered by status.
   */
  async listRequests(
    tenantId: string,
    options: { status?: string; limit?: number; offset?: number } = {},
  ): Promise<{ items: GenerationRequest[]; total: number }> {
    const where: Record<string, unknown> = { tenantId };
    if (options.status) where.status = statusToDb(options.status);

    const [rows, total] = await Promise.all([
      this.prisma.generationRequest.findMany({
        where,
        orderBy: { createdAt: "desc" },
        take: options.limit ?? 20,
        skip: options.offset ?? 0,
      }),
      this.prisma.generationRequest.count({ where }),
    ]);

    return {
      items: rows.map(mapRequest),
      total,
    };
  }

  /**
   * Run the planning phase for a generation request.
   *
   * Determines:
   *  - Which artifacts to generate (plannedAssets)
   *  - How many of each type (artifactNeeds)
   *  - Risk level and factors
   *  - Review routing path
   *  - Cost estimation
   *
   * Transitions the request from DRAFT to PLANNED and creates
   * corresponding GenerationJob records in a single atomic transaction.
   *
   * If planning fails, transitions to FAILED_PLANNING with error details
   * and creates an audit event for observability.
   */
  async planRequest(
    tenantId: string,
    requestId: string,
  ): Promise<PlanningResult> {
    const request = await this.prisma.generationRequest.findFirst({
      where: buildTenantScopedWhere(tenantId, requestId),
    });

    if (!request) {
      throw new Error(`Generation request ${requestId} not found`);
    }

    assertSameTenant(tenantId, request.tenantId, "plan generation request");

    if (request.status !== "DRAFT") {
      throw new Error(
        `Cannot plan request in status ${request.status}. Must be DRAFT.`,
      );
    }

    // Perform all planning logic first (no side effects)
    const requestConfig = this.normalizeRequestConfig(
      (request.requestConfig as GenerationRequestConfig | null) ?? null,
    );
    const cacheRequest = {
      domain: request.domain,
      title: request.title,
      requestConfig,
      ...(request.conceptId ? { conceptId: request.conceptId } : {}),
      ...((request.targetGrades as string[] | null)?.length
        ? { targetGrades: request.targetGrades as string[] }
        : {}),
    };
    const cacheKey = this.cache.generatePlanningCacheKey(cacheRequest);
    const cachedBlueprint = await this.cache.getPlanningBlueprint(cacheKey);
    const blueprint = cachedBlueprint
      ? this.rehydrateBlueprint(request.id, cachedBlueprint)
      : this.buildBlueprint(request, requestConfig);
    const routingDecision = this.router.routeRequest(
      {
        cacheKey,
        estimatedTokens: blueprint.estimatedCost.totalTokens,
        requestConfig,
        cacheAvailable: cachedBlueprint !== null,
      },
      {
        remainingDailyBudgetUsd: requestConfig.maxBudgetUsd ?? 5,
      },
    );
    const plannedAssets = blueprint.plannedAssets;
    const artifactNeeds = blueprint.artifactNeeds;
    const riskAssessment = {
      riskLevel: blueprint.riskLevel,
      riskFactors: blueprint.riskFactors,
    };
    const reviewPath = blueprint.reviewPath;
    const estimatedCost = {
      ...blueprint.estimatedCost,
      estimatedSpendUsd: routingDecision.estimatedSpendUsd,
      ...(routingDecision.useCache
        ? {
            cacheSavingsUsd:
              blueprint.estimatedCost.estimatedSpendUsd ??
              routingDecision.estimatedSpendUsd,
        }
        : {}),
    };

    // Create jobs for each planned asset
    const jobData = plannedAssets.map((planned) => ({
      requestId,
      jobType: jobTypeToDb(planned.jobType) as
        | "CLAIM"
        | "EXPLAINER"
        | "WORKED_EXAMPLE"
        | "SIMULATION"
        | "ANIMATION"
        | "ASSESSMENT"
        | "EVALUATION",
      targetRef: planned.targetRef,
      inputPrompt: planned.description,
      parameters: {
        estimatedTokens: planned.estimatedTokens,
        dependsOn: planned.dependsOn ?? [],
      },
      status: "PENDING" as "PENDING",
    }));

    try {
      // Execute entire planning workflow in a single atomic transaction
      // This ensures DRAFT → PLANNED transition and job creation are atomic
      // If any step fails, the transaction rolls back and status returns to DRAFT
      await this.prisma.$transaction(async (tx) => {
        // First, transition to PLANNING to indicate planning is in progress
        await tx.generationRequest.update({
          where: { id: requestId },
          data: { status: "PLANNING" },
        });

        // Create jobs
        for (const job of jobData) {
          await tx.generationJob.create({ data: job });
        }

        // Transition to PLANNED with all planning data
        await tx.generationRequest.update({
          where: { id: requestId },
          data: {
            status: "PLANNED",
            plannedAssets: JSON.parse(JSON.stringify(plannedAssets)),
            artifactNeeds: JSON.parse(JSON.stringify(artifactNeeds)),
            riskLevel: riskLevelToDb(riskAssessment.riskLevel) as "CRITICAL" | "HIGH" | "MEDIUM" | "LOW",
            riskFactors: riskAssessment.riskFactors,
            reviewPath: reviewPathToEnum(reviewPath) as
              | "AUTO_PUBLISH"
              | "HUMAN_REVIEW"
              | "EXPERT_REVIEW",
            estimatedCost: JSON.parse(JSON.stringify(estimatedCost)),
            routingDecision: JSON.parse(JSON.stringify(routingDecision)),
            totalJobs: plannedAssets.length,
            plannedAt: new Date(),
          },
        });
      });

      // Cache the blueprint (outside transaction for performance)
      if (!cachedBlueprint) {
        const storableBlueprint: CachedPlanningBlueprint = {
          plannedAssets: this.stripRequestScopedRefs(plannedAssets),
          artifactNeeds,
          riskLevel: riskAssessment.riskLevel,
          riskFactors: riskAssessment.riskFactors,
          reviewPath,
          estimatedCost,
          routingDecision,
        };

        if (this.cache.shouldCacheBlueprint(storableBlueprint)) {
          await this.cache.setPlanningBlueprint(cacheKey, storableBlueprint);
        }
      }

      return {
        requestId,
        plannedAssets,
        artifactNeeds,
        riskLevel: riskAssessment.riskLevel,
        riskFactors: riskAssessment.riskFactors,
        reviewPath,
        estimatedCost,
        routingDecision,
        totalJobs: plannedAssets.length,
      };
    } catch (error) {
      // If transaction fails, transition to FAILED_PLANNING with error details
      // This ensures requests don't get stuck in PLANNING state even if transaction rolls back
      const errorMessage = error instanceof Error ? error.message : "Unknown planning error";
      
      try {
        await this.prisma.generationRequest.update({
          where: { id: requestId },
          data: {
            status: "FAILED_PLANNING",
            artifactNeeds: {
              error: errorMessage,
              failedAt: new Date().toISOString(),
            } as Prisma.InputJsonValue,
          },
        });

        // Create audit event for observability
        await this.prisma.experienceEvent.create({
          data: {
            experienceId: requestId,
            eventType: "PLANNING_FAILED",
            actorId: request.requestedBy,
            metadata: {
              error: errorMessage,
              failedAt: new Date().toISOString(),
            } as Prisma.InputJsonValue,
          } as never,
        });
      } catch (updateError) {
        // Log but don't throw if status update fails - the original error is more important
        console.error(`Failed to update request status to FAILED_PLANNING: ${updateError}`);
      }

      // Re-throw the original error so callers can handle it
      throw new Error(`Planning failed for request ${requestId}: ${errorMessage}`);
    }
  }

  /**
   * Cancel a request that hasn't completed yet.
   */
  async cancelRequest(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationRequest> {
    const request = await this.prisma.generationRequest.findFirst({
      where: buildTenantScopedWhere(tenantId, requestId),
    });

    if (!request) {
      throw new Error(`Generation request ${requestId} not found`);
    }

    assertSameTenant(tenantId, request.tenantId, "cancel generation request");

    const terminalStatuses = ["COMPLETED", "FAILED", "CANCELLED"];
    if (terminalStatuses.includes(request.status)) {
      throw new Error(
        `Cannot cancel request in terminal status ${request.status}`,
      );
    }

    const row = await this.prisma.generationRequest.update({
      where: { id: requestId },
      data: { status: "CANCELLED" },
    });

    // Cancel any pending/running jobs
    await this.prisma.generationJob.updateMany({
      where: {
        requestId,
        status: { in: ["PENDING", "RUNNING"] },
      },
      data: { status: "CANCELLED" },
    });

    return mapRequest(row);
  }

  // -------------------------------------------------------------------------
  // Planning Logic
  // -------------------------------------------------------------------------

  /**
   * Determine which assets/artifacts should be generated.
   */
  private determinePlannedAssets(request: {
    id: string;
    domain?: string | null;
    title?: string | null;
    description?: string | null;
    targetGrades?: unknown;
  }): PlannedAssetDescriptor[] {
    const assets: PlannedAssetDescriptor[] = [];
    const domain = normalizeDomain(request.domain);
    const title = request.title ?? "";

    // Standard artifact set
    for (const jobType of STANDARD_ARTIFACT_SET) {
      assets.push({
        jobType,
        targetRef: `${request.id}/${jobType}`,
        description: `Generate ${jobType} for "${title}" in ${domain}`,
        estimatedTokens: TOKEN_ESTIMATES[jobType] ?? 0,
      });
    }

    // Visual domains get simulation and animation
    if (VISUAL_DOMAINS.has(domain)) {
      assets.push({
        jobType: "simulation",
        targetRef: `${request.id}/simulation`,
        description: `Generate interactive simulation for "${title}" in ${domain}`,
        estimatedTokens: TOKEN_ESTIMATES.simulation ?? 0,
        dependsOn: [`${request.id}/claim`],
      });
      assets.push({
        jobType: "animation",
        targetRef: `${request.id}/animation`,
        description: `Generate animation for "${title}" in ${domain}`,
        estimatedTokens: TOKEN_ESTIMATES.animation ?? 0,
        dependsOn: [`${request.id}/claim`],
      });
    }

    // Evaluation job always comes last (depends on all others)
    const dependsOn = assets.map((a) => a.targetRef);
    assets.push({
      jobType: "evaluation",
      targetRef: `${request.id}/evaluation`,
      description: `Evaluate generated content for "${title}"`,
      estimatedTokens: TOKEN_ESTIMATES.evaluation ?? 0,
      dependsOn,
    });

    return assets;
  }

  /**
   * Compute artifact count needs from planned assets.
   */
  private computeArtifactNeeds(
    planned: PlannedAssetDescriptor[],
  ): Record<string, number> {
    const needs: Record<string, number> = {};
    for (const asset of planned) {
      needs[asset.jobType] = (needs[asset.jobType] ?? 0) + 1;
    }
    return needs;
  }

  /**
   * Assess the risk level of a generation request based on domain,
   * title content, and grade targeting.
   */
  private assessRisk(request: {
    id?: string;
    domain?: string | null;
    title?: string | null;
    description?: string | null;
    targetGrades?: unknown;
  }): {
    riskLevel: RiskLevel;
    riskFactors: string[];
  } {
    const factors: string[] = [];
    const title = (
      (request.title ?? "") +
      " " +
      (request.description ?? "")
    ).toLowerCase();
    const domain = normalizeDomain(request.domain);

    // Check domain and title for high-risk keywords
    for (const kw of HIGH_RISK_KEYWORDS) {
      if (title.includes(kw) || domain.includes(kw)) {
        factors.push(`Contains high-risk keyword: "${kw}"`);
      }
    }

    // Check for medium-risk keywords
    for (const kw of MEDIUM_RISK_KEYWORDS) {
      if (title.includes(kw) || domain.includes(kw)) {
        factors.push(`Contains sensitive keyword: "${kw}"`);
      }
    }

    // Young learners increase risk
    const grades = request.targetGrades as string[] | null;
    if (grades && grades.some((g: string) => isYoungLearnerGrade(g))) {
      factors.push("Targets young learners — requires additional scrutiny");
    }

    // Determine level based on factors
    let riskLevel: RiskLevel = "low";
    const highCount = factors.filter((f) => f.includes("high-risk")).length;
    const medCount = factors.filter((f) => f.includes("sensitive")).length;

    if (highCount >= 2) {
      riskLevel = "critical";
    } else if (highCount >= 1) {
      riskLevel = "high";
    } else if (medCount >= 1 || factors.length > 0) {
      riskLevel = "medium";
    }

    return { riskLevel, riskFactors: factors };
  }

  /**
   * Determine the review path based on risk level.
   *
   * AUTO_PUBLISH is disabled until semantic validation, golden datasets,
   * and human-review bypass criteria are production-proven.
   *
   * All content requires human review regardless of risk level until
   * the validation infrastructure is production-ready.
   */
  private determineReviewPath(riskLevel: RiskLevel): ReviewPath {
    switch (riskLevel) {
      case "critical":
      case "high":
        return "expert_review";
      case "medium":
      case "low":
      default:
        return "human_review";
    }
  }

  /**
   * Estimate cost for planned assets.
   */
  private estimateCost(
    planned: PlannedAssetDescriptor[],
    requestConfig: GenerationRequestConfig,
  ): GenerationCostEstimate {
    let totalTokens = 0;
    let llmCalls = 0;
    let embeddingCalls = 0;

    for (const asset of planned) {
      totalTokens += asset.estimatedTokens ?? TOKEN_ESTIMATES[asset.jobType];
      llmCalls += 1;
      if (asset.jobType !== "evaluation") {
        embeddingCalls += 1; // Each generated artifact needs embedding
      }
    }

    // Rough duration estimate: ~2s per 1000 tokens
    const estimatedDurationMs = Math.ceil(
      (totalTokens / 1000) * (requestConfig.urgent ? 1200 : 2000),
    );

    const baselineSpendUsd = Number(
      (
        (totalTokens / 1000) *
        ((requestConfig.minQualityScore ?? 0.75) >= 0.9 ? 0.04 : 0.01)
      ).toFixed(4),
    );

    return {
      totalTokens,
      embeddingCalls,
      llmCalls,
      estimatedDurationMs,
      estimatedSpendUsd: baselineSpendUsd,
      cacheSavingsUsd: 0,
    };
  }

  private normalizeRequestConfig(
    requestConfig: GenerationRequestConfig | null,
  ): GenerationRequestConfig {
    return {
      minQualityScore: clamp(requestConfig?.minQualityScore ?? 0.75, 0.5, 1),
      urgent: requestConfig?.urgent ?? false,
      learnerArchetype:
        requestConfig?.learnerArchetype?.trim() ||
        this.cache.classifyLearnerArchetype(requestConfig ?? undefined),
      ...(requestConfig?.maxBudgetUsd !== undefined
        ? { maxBudgetUsd: Math.max(requestConfig.maxBudgetUsd, 0) }
        : {}),
    };
  }

  private buildBlueprint(
    request: {
      id: string;
      domain?: string | null;
      title?: string | null;
      description?: string | null;
      targetGrades?: unknown;
    },
    requestConfig: GenerationRequestConfig,
  ): {
    plannedAssets: PlannedAssetDescriptor[];
    artifactNeeds: Record<string, number>;
    riskLevel: RiskLevel;
    riskFactors: string[];
    reviewPath: ReviewPath;
    estimatedCost: GenerationCostEstimate;
  } {
    const plannedAssets = this.determinePlannedAssets(request);
    const artifactNeeds = this.computeArtifactNeeds(plannedAssets);
    const { riskLevel, riskFactors } = this.assessRisk(request);
    const reviewPath = this.determineReviewPath(riskLevel);
    const estimatedCost = this.estimateCost(plannedAssets, requestConfig);

    return {
      plannedAssets,
      artifactNeeds,
      riskLevel,
      riskFactors,
      reviewPath,
      estimatedCost,
    };
  }

  private rehydrateBlueprint(
    requestId: string,
    cachedBlueprint: {
      plannedAssets: PlannedAssetDescriptor[];
      artifactNeeds: Record<string, number>;
      riskLevel: RiskLevel;
      riskFactors: string[];
      reviewPath: ReviewPath;
      estimatedCost: GenerationCostEstimate;
    },
  ): {
    plannedAssets: PlannedAssetDescriptor[];
    artifactNeeds: Record<string, number>;
    riskLevel: RiskLevel;
    riskFactors: string[];
    reviewPath: ReviewPath;
    estimatedCost: GenerationCostEstimate;
  } {
    return {
      ...cachedBlueprint,
      plannedAssets: cachedBlueprint.plannedAssets.map((asset) => ({
        ...asset,
        targetRef: this.restoreRequestScopedRef(requestId, asset.targetRef),
        ...(asset.dependsOn
          ? {
              dependsOn: asset.dependsOn.map((dependency) =>
                this.restoreRequestScopedRef(requestId, dependency),
              ),
            }
          : {}),
      })),
    };
  }

  private stripRequestScopedRefs(
    plannedAssets: PlannedAssetDescriptor[],
  ): PlannedAssetDescriptor[] {
    return plannedAssets.map((asset) => ({
      ...asset,
      targetRef: stripRequestRef(asset.targetRef),
      ...(asset.dependsOn
        ? { dependsOn: asset.dependsOn.map(stripRequestRef) }
        : {}),
    }));
  }

  private restoreRequestScopedRef(requestId: string, ref: string): string {
    return ref.startsWith("__REQUEST__/")
      ? ref.replace("__REQUEST__/", `${requestId}/`)
      : ref;
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function isYoungLearnerGrade(grade: string): boolean {
  const lower = normalizeGrade(grade);
  return (
    lower.includes("k") ||
    lower.includes("kindergarten") ||
    lower.includes("1st") ||
    lower.includes("2nd") ||
    lower.includes("3rd") ||
    lower.includes("grade 1") ||
    lower.includes("grade 2") ||
    lower.includes("grade 3") ||
    /^[1-3]$/.test(lower.trim())
  );
}

function reviewPathToEnum(path: ReviewPath): string {
  switch (path) {
    case "auto_publish":
      return "AUTO_PUBLISH";
    case "human_review":
      return "HUMAN_REVIEW";
    case "expert_review":
      return "EXPERT_REVIEW";
  }
  return "HUMAN_REVIEW";
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function stripRequestRef(ref: string): string {
  const slashIndex = ref.indexOf("/");
  return slashIndex === -1 ? ref : `__REQUEST__/${ref.slice(slashIndex + 1)}`;
}
