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
import type {
  CreateGenerationRequestInput,
  GenerationRequest,
  GenerationRequestWithJobs,
  GenerationJob,
  GenerationJobType,
  PlannedAssetDescriptor,
  GenerationCostEstimate,
  PlanningResult,
  ReviewPath,
} from "@tutorputor/contracts/v1/content-studio";
import type { RiskLevel } from "@tutorputor/contracts/v1/types";

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
 */
const VISUAL_DOMAINS = new Set([
  "physics",
  "chemistry",
  "biology",
  "geometry",
  "astronomy",
  "engineering",
]);

/**
 * Rough token estimates per job type (for cost estimation).
 */
const TOKEN_ESTIMATES: Record<GenerationJobType, number> = {
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
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Create a new generation request in DRAFT status.
   */
  async createRequest(
    input: CreateGenerationRequestInput,
  ): Promise<GenerationRequest> {
    const row = await (this.prisma as any).generationRequest.create({
      data: {
        tenantId: input.tenantId,
        title: input.title,
        description: input.description ?? null,
        domain: input.domain,
        conceptId: input.conceptId ?? null,
        targetGrades: input.targetGrades ?? null,
        requestedBy: input.requestedBy,
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
    const row = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
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
    if (options.status) where.status = options.status.toUpperCase();

    const [rows, total] = await Promise.all([
      (this.prisma as any).generationRequest.findMany({
        where,
        orderBy: { createdAt: "desc" },
        take: options.limit ?? 20,
        skip: options.offset ?? 0,
      }),
      (this.prisma as any).generationRequest.count({ where }),
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
   * corresponding GenerationJob records.
   */
  async planRequest(
    tenantId: string,
    requestId: string,
  ): Promise<PlanningResult> {
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
    });

    if (!request) {
      throw new Error(`Generation request ${requestId} not found`);
    }

    if (request.status !== "DRAFT") {
      throw new Error(
        `Cannot plan request in status ${request.status}. Must be DRAFT.`,
      );
    }

    // Update status to PLANNING
    await (this.prisma as any).generationRequest.update({
      where: { id: requestId },
      data: { status: "PLANNING" },
    });

    // Determine what to generate
    const plannedAssets = this.determinePlannedAssets(request);
    const artifactNeeds = this.computeArtifactNeeds(plannedAssets);
    const riskAssessment = this.assessRisk(request);
    const reviewPath = this.determineReviewPath(riskAssessment.riskLevel);
    const estimatedCost = this.estimateCost(plannedAssets);

    // Create jobs for each planned asset
    const jobData = plannedAssets.map((planned) => ({
      requestId,
      jobType: planned.jobType.toUpperCase(),
      targetRef: planned.targetRef,
      inputPrompt: planned.description,
      parameters: { estimatedTokens: planned.estimatedTokens },
      status: "PENDING",
    }));

    // Persist jobs and update request atomically
    await (this.prisma as any).$transaction(async (tx: any) => {
      for (const job of jobData) {
        await tx.generationJob.create({ data: job });
      }
      await tx.generationRequest.update({
        where: { id: requestId },
        data: {
          status: "PLANNED",
          plannedAssets: JSON.parse(JSON.stringify(plannedAssets)),
          artifactNeeds: JSON.parse(JSON.stringify(artifactNeeds)),
          riskLevel: riskAssessment.riskLevel.toUpperCase(),
          riskFactors: riskAssessment.riskFactors,
          reviewPath: reviewPathToEnum(reviewPath),
          estimatedCost: JSON.parse(JSON.stringify(estimatedCost)),
          totalJobs: plannedAssets.length,
          plannedAt: new Date(),
        },
      });
    });

    return {
      requestId,
      plannedAssets,
      artifactNeeds,
      riskLevel: riskAssessment.riskLevel,
      riskFactors: riskAssessment.riskFactors,
      reviewPath,
      estimatedCost,
      totalJobs: plannedAssets.length,
    };
  }

  /**
   * Cancel a request that hasn't completed yet.
   */
  async cancelRequest(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationRequest> {
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
    });

    if (!request) {
      throw new Error(`Generation request ${requestId} not found`);
    }

    const terminalStatuses = ["COMPLETED", "FAILED", "CANCELLED"];
    if (terminalStatuses.includes(request.status)) {
      throw new Error(
        `Cannot cancel request in terminal status ${request.status}`,
      );
    }

    const row = await (this.prisma as any).generationRequest.update({
      where: { id: requestId },
      data: { status: "CANCELLED" },
    });

    // Cancel any pending/running jobs
    await (this.prisma as any).generationJob.updateMany({
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
  private determinePlannedAssets(request: any): PlannedAssetDescriptor[] {
    const assets: PlannedAssetDescriptor[] = [];
    const domain = (request.domain ?? "").toLowerCase();
    const title = request.title ?? "";

    // Standard artifact set
    for (const jobType of STANDARD_ARTIFACT_SET) {
      assets.push({
        jobType,
        targetRef: `${request.id}/${jobType}`,
        description: `Generate ${jobType} for "${title}" in ${domain}`,
        estimatedTokens: TOKEN_ESTIMATES[jobType],
      });
    }

    // Visual domains get simulation and animation
    if (VISUAL_DOMAINS.has(domain)) {
      assets.push({
        jobType: "simulation",
        targetRef: `${request.id}/simulation`,
        description: `Generate interactive simulation for "${title}" in ${domain}`,
        estimatedTokens: TOKEN_ESTIMATES.simulation,
        dependsOn: [`${request.id}/claim`],
      });
      assets.push({
        jobType: "animation",
        targetRef: `${request.id}/animation`,
        description: `Generate animation for "${title}" in ${domain}`,
        estimatedTokens: TOKEN_ESTIMATES.animation,
        dependsOn: [`${request.id}/claim`],
      });
    }

    // Evaluation job always comes last (depends on all others)
    const dependsOn = assets.map((a) => a.targetRef);
    assets.push({
      jobType: "evaluation",
      targetRef: `${request.id}/evaluation`,
      description: `Evaluate generated content for "${title}"`,
      estimatedTokens: TOKEN_ESTIMATES.evaluation,
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
  private assessRisk(request: any): {
    riskLevel: RiskLevel;
    riskFactors: string[];
  } {
    const factors: string[] = [];
    const title = (
      (request.title ?? "") +
      " " +
      (request.description ?? "")
    ).toLowerCase();
    const domain = (request.domain ?? "").toLowerCase();

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
   */
  private determineReviewPath(riskLevel: RiskLevel): ReviewPath {
    switch (riskLevel) {
      case "critical":
      case "high":
        return "expert_review";
      case "medium":
        return "human_review";
      case "low":
        return "auto_publish";
      default:
        return "human_review";
    }
  }

  /**
   * Estimate cost for planned assets.
   */
  private estimateCost(
    planned: PlannedAssetDescriptor[],
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
    const estimatedDurationMs = Math.ceil((totalTokens / 1000) * 2000);

    return { totalTokens, embeddingCalls, llmCalls, estimatedDurationMs };
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function isYoungLearnerGrade(grade: string): boolean {
  const lower = grade.toLowerCase();
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
}

function mapRequest(row: any): GenerationRequest {
  return {
    id: row.id,
    tenantId: row.tenantId,
    title: row.title,
    description: row.description ?? undefined,
    domain: row.domain,
    conceptId: row.conceptId ?? undefined,
    targetGrades: row.targetGrades ?? undefined,
    requestedBy: row.requestedBy,
    status: (row.status as string).toLowerCase() as GenerationRequest["status"],
    plannedAssets: row.plannedAssets ?? undefined,
    artifactNeeds: row.artifactNeeds ?? undefined,
    riskLevel: (row.riskLevel as string).toLowerCase() as RiskLevel,
    riskFactors: row.riskFactors ?? undefined,
    reviewPath: enumToReviewPath(row.reviewPath as string),
    estimatedCost: row.estimatedCost ?? undefined,
    totalJobs: row.totalJobs,
    completedJobs: row.completedJobs,
    failedJobs: row.failedJobs,
    plannedAt: row.plannedAt
      ? (row.plannedAt as Date).toISOString()
      : undefined,
    startedAt: row.startedAt
      ? (row.startedAt as Date).toISOString()
      : undefined,
    completedAt: row.completedAt
      ? (row.completedAt as Date).toISOString()
      : undefined,
    createdAt: (row.createdAt as Date).toISOString(),
    updatedAt: (row.updatedAt as Date).toISOString(),
  };
}

function mapJob(row: any): GenerationJob {
  return {
    id: row.id,
    requestId: row.requestId,
    jobType: (row.jobType as string).toLowerCase() as GenerationJob["jobType"],
    targetRef: row.targetRef ?? undefined,
    inputPrompt: row.inputPrompt ?? undefined,
    parameters: row.parameters ?? undefined,
    status: (row.status as string).toLowerCase() as GenerationJob["status"],
    progress: row.progress,
    outputAssetId: row.outputAssetId ?? undefined,
    outputData: row.outputData ?? undefined,
    diagnostics: row.diagnostics ?? undefined,
    errorMessage: row.errorMessage ?? undefined,
    retryCount: row.retryCount,
    maxRetries: row.maxRetries,
    startedAt: row.startedAt
      ? (row.startedAt as Date).toISOString()
      : undefined,
    completedAt: row.completedAt
      ? (row.completedAt as Date).toISOString()
      : undefined,
    createdAt: (row.createdAt as Date).toISOString(),
    updatedAt: (row.updatedAt as Date).toISOString(),
  };
}

function enumToReviewPath(value: string): ReviewPath {
  switch (value) {
    case "AUTO_PUBLISH":
      return "auto_publish";
    case "EXPERT_REVIEW":
      return "expert_review";
    default:
      return "human_review";
  }
}
