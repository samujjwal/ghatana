/**
 * Generation Execution Service
 *
 * Fastify-side orchestration for triggering and tracking generation
 * job execution. Owns the request lifecycle: transitions the
 * GenerationRequest from PLANNED → EXECUTING → COMPLETED/FAILED
 * and updates individual GenerationJob statuses as results arrive
 * from the Java execution plane.
 *
 * @doc.type class
 * @doc.purpose Orchestrate generation execution and track job statuses
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";

type GenerationRequest = any;
type GenerationJob = any;
type GenerationRequestWithJobs = any;
type GenerationExecutionSnapshot = any;
type GenerationExecutionProgress = any;
type GenerationExecutionEvent = any;
type GenerationExecutionCostSummary = any;
type GenerationExecutionWorkerTelemetry = any;
import {
  getGenerationExecutionChannel,
  type GenerationExecutionStreamMessage,
} from "./execution-stream.js";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface JobExecutionResult {
  jobId: string;
  status: "completed" | "failed";
  outputAssetId?: string;
  outputData?: Record<string, unknown>;
  diagnostics?: Record<string, unknown>;
  errorMessage?: string;
  durationMs: number;
}

export interface ExecutionSummary {
  requestId: string;
  status: "completed" | "failed";
  totalJobs: number;
  completedJobs: number;
  failedJobs: number;
  totalDurationMs: number;
}

export interface ArtifactPostProcessResult {
  jobId: string;
  qualityScore: number;
  passedValidation: boolean;
  regenerationNeeded: boolean;
  regenerationReason?: string;
  postProcessedOutput?: Record<string, unknown>;
  qualityIssues: string[];
}

export interface AssetFamilyDefinition {
  familyId: string;
  name: string;
  description: string;
  applicableDomains: string[];
  generationJobTypes: string[];
  postProcessSteps: string[];
}

/**
 * Extended asset families beyond the standard set
 */
export const EXTENDED_ASSET_FAMILIES: AssetFamilyDefinition[] = [
  {
    familyId: "interactive_simulation",
    name: "Interactive Simulation",
    description: "Interactive, explorable simulations with parameter controls",
    applicableDomains: ["physics", "chemistry", "biology", "engineering", "mathematics"],
    generationJobTypes: ["simulation", "interactive_exercise"],
    postProcessSteps: ["validate_interactivity", "optimize_performance", "add_accessibility"],
  },
  {
    familyId: "visualization_suite",
    name: "Visualization Suite",
    description: "Animated and static visualizations for complex concepts",
    applicableDomains: ["physics", "chemistry", "biology", "astronomy", "geometry"],
    generationJobTypes: ["animation", "diagram", "infographic"],
    postProcessSteps: ["optimize_visuals", "add_annotations", "validate_clarity"],
  },
  {
    familyId: "assessment_bank",
    name: "Assessment Item Bank",
    description: "Diverse assessment items with varying difficulty and formats",
    applicableDomains: ["all"],
    generationJobTypes: ["assessment", "quiz", "exercise"],
    postProcessSteps: ["validate_answer_key", "check_difficulty_calibration", "diversity_audit"],
  },
  {
    familyId: "worked_examples",
    name: "Worked Example Library",
    description: "Step-by-step solved problems with explanations",
    applicableDomains: ["mathematics", "physics", "chemistry", "cs", "engineering"],
    generationJobTypes: ["worked_example", "tutorial"],
    postProcessSteps: ["validate_steps", "check_notation", "add_alternatives"],
  },
  {
    familyId: "adaptive_practice",
    name: "Adaptive Practice Set",
    description: "Practice problems with adaptive difficulty progression",
    applicableDomains: ["all"],
    generationJobTypes: ["practice_problem", "drill"],
    postProcessSteps: ["calibrate_difficulty", "validate_progression", "add_hints"],
  },
  {
    familyId: "concept_map",
    name: "Concept Map Network",
    description: "Visual concept relationships and prerequisite mappings",
    applicableDomains: ["all"],
    generationJobTypes: ["concept_map", "knowledge_graph"],
    postProcessSteps: ["validate_relationships", "check_completeness", "add_crosslinks"],
  },
];

export { getGenerationExecutionChannel } from "./execution-stream.js";
export type { GenerationExecutionStreamMessage } from "./execution-stream.js";

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class GenerationExecutionService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly redis?: Redis,
  ) {}

  async getExecutionSnapshot(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationExecutionSnapshot | null> {
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
      include: { jobs: true },
    });

    if (!request) {
      return null;
    }

    const mappedRequest: GenerationRequestWithJobs = {
      ...mapRequest(request),
      jobs: (request.jobs ?? []).map(mapJob),
    };

    return {
      request: mappedRequest,
      progress: buildExecutionProgress(mappedRequest),
      events: buildExecutionEvents(mappedRequest),
    };
  }

  /**
   * Begin execution of a PLANNED generation request.
   *
   * Transitions the request to EXECUTING. Jobs remain PENDING until the
   * dispatch layer enqueues dependency-ready work into BullMQ.
   */
  async startExecution(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationRequestWithJobs> {
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
      include: { jobs: true },
    });

    if (!request) {
      throw new Error(`Generation request ${requestId} not found`);
    }

    if (request.status !== "PLANNED") {
      throw new Error(
        `Cannot execute request in status ${request.status}. Must be PLANNED.`,
      );
    }

    // Transition to EXECUTING
    await (this.prisma as any).$transaction(async (tx: any) => {
      await tx.generationRequest.update({
        where: { id: requestId },
        data: { status: "EXECUTING", startedAt: new Date() },
      });
    });

    // Re-fetch with updated statuses
    const updated = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
      include: { jobs: true },
    });

    const mappedRequest = {
      ...mapRequest(updated),
      jobs: (updated.jobs ?? []).map(mapJob),
    };

    await this.publishSnapshot(tenantId, requestId);
    return mappedRequest;
  }

  /**
   * Record the result of a single job execution.
   *
   * Updates job status and output data, then checks if all jobs in
   * the request are finished to transition the request itself.
   */
  async recordJobResult(
    requestId: string,
    result: JobExecutionResult,
  ): Promise<GenerationJob> {
    const jobStatus = result.status === "completed" ? "COMPLETED" : "FAILED";

    const updatedJob = await (this.prisma as any).generationJob.update({
      where: { id: result.jobId },
      data: {
        status: jobStatus,
        progress: result.status === "completed" ? 100 : 0,
        ...(result.outputAssetId !== undefined
          ? { outputAssetId: result.outputAssetId }
          : {}),
        outputData: result.outputData ?? null,
        diagnostics: result.diagnostics ?? null,
        errorMessage: result.errorMessage ?? null,
        completedAt: new Date(),
      },
    });

    // Update request counters
    const increment =
      result.status === "completed"
        ? { completedJobs: { increment: 1 } }
        : { failedJobs: { increment: 1 } };

    await (this.prisma as any).generationRequest.update({
      where: { id: requestId },
      data: increment,
    });

    // Check if all jobs are done
    await this.maybeCompleteRequest(requestId);

    await this.publishJobResult(requestId, result);

    return mapJob(updatedJob);
  }

  /**
   * Record results for multiple jobs at once (batch callback).
   */
  async recordBatchResults(
    requestId: string,
    results: JobExecutionResult[],
  ): Promise<ExecutionSummary> {
    const startTime = Date.now();

    for (const result of results) {
      await this.recordJobResult(requestId, result);
    }

    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId },
    });

    return {
      requestId,
      status: request.status === "COMPLETED" ? "completed" : "failed",
      totalJobs: request.totalJobs,
      completedJobs: request.completedJobs,
      failedJobs: request.failedJobs,
      totalDurationMs: Date.now() - startTime,
    };
  }

  // -------------------------------------------------------------------------
  // Internal
  // -------------------------------------------------------------------------

  /**
   * Check if all jobs in the request are finished and transition
   * the request status accordingly.
   */
  private async maybeCompleteRequest(requestId: string): Promise<void> {
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId },
      include: { jobs: true },
    });

    if (!request || request.status !== "EXECUTING") return;

    const allDone = (request.jobs as any[]).every(
      (j: any) =>
        j.status === "COMPLETED" ||
        j.status === "FAILED" ||
        j.status === "CANCELLED",
    );

    if (!allDone) return;

    const anyFailed = (request.jobs as any[]).some(
      (j: any) => j.status === "FAILED",
    );

    await (this.prisma as any).generationRequest.update({
      where: { id: requestId },
      data: {
        status: anyFailed ? "FAILED" : "COMPLETED",
        completedAt: new Date(),
      },
    });
  }

  private async publishSnapshot(
    tenantId: string,
    requestId: string,
  ): Promise<void> {
    if (!this.redis) {
      return;
    }

    const snapshot = await this.getExecutionSnapshot(tenantId, requestId);
    if (!snapshot) {
      return;
    }

    await this.publishMessage(requestId, {
      kind: "snapshot",
      requestId,
      at: new Date().toISOString(),
      snapshot,
    });
  }

  private async publishJobResult(
    requestId: string,
    result: JobExecutionResult,
  ): Promise<void> {
    if (!this.redis) {
      return;
    }

    await this.publishMessage(requestId, {
      kind: "job_result",
      requestId,
      at: new Date().toISOString(),
      jobResult: result,
    });

    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId },
      select: { tenantId: true, status: true, totalJobs: true, completedJobs: true, failedJobs: true },
    });

    if (!request) {
      return;
    }

    await this.publishSnapshot(request.tenantId, requestId);

    if (request.status === "COMPLETED" || request.status === "FAILED") {
      await this.publishMessage(requestId, {
        kind: "summary",
        requestId,
        at: new Date().toISOString(),
        summary: {
          requestId,
          status: request.status === "COMPLETED" ? "completed" : "failed",
          totalJobs: request.totalJobs,
          completedJobs: request.completedJobs,
          failedJobs: request.failedJobs,
          totalDurationMs: 0,
        },
      });
    }
  }

  private async publishMessage(
    requestId: string,
    message: GenerationExecutionStreamMessage,
  ): Promise<void> {
    if (!this.redis) {
      return;
    }

    await this.redis.publish(
      getGenerationExecutionChannel(requestId),
      JSON.stringify(message),
    );
  }
}

// ---------------------------------------------------------------------------
// Mappers
// ---------------------------------------------------------------------------

function buildExecutionProgress(
  request: GenerationRequestWithJobs,
): GenerationExecutionProgress {
  const runningJobs = request.jobs.filter((job: any) => job.status === "running").length;
  const pendingJobs = request.jobs.filter((job: any) => job.status === "pending").length;
  const cancelledJobs = request.jobs.filter(
    (job: any) => job.status === "cancelled",
  ).length;
  const finishedJobs = request.completedJobs + request.failedJobs + cancelledJobs;
  const totalJobs = Math.max(request.totalJobs, request.jobs.length, 1);
  const workerTelemetry = request.jobs
    .map((job: any) => getWorkerTelemetry(job))
    .filter((value: unknown): value is GenerationExecutionWorkerTelemetry => value !== null);
  const latestWorkerTelemetry = workerTelemetry
    .slice()
    .sort((left: any, right: any) => left.at.localeCompare(right.at))
    .at(-1);
  const cost = buildExecutionCostSummary(request);

  return {
    totalJobs,
    completedJobs: request.completedJobs,
    failedJobs: request.failedJobs,
    runningJobs,
    pendingJobs,
    cancelledJobs,
    completionPercent: Math.min(100, Math.round((finishedJobs / totalJobs) * 100)),
    terminal:
      request.status === "completed" ||
      request.status === "failed" ||
      request.status === "cancelled",
    ...(cost ? { cost } : {}),
    ...(latestWorkerTelemetry?.stage
      ? { latestWorkerStage: latestWorkerTelemetry.stage }
      : {}),
    ...(latestWorkerTelemetry?.message
      ? { latestWorkerMessage: latestWorkerTelemetry.message }
      : {}),
  };
}

function buildExecutionEvents(
  request: GenerationRequestWithJobs,
): GenerationExecutionEvent[] {
  const events: GenerationExecutionEvent[] = [
    {
      type: "request_created",
      at: request.createdAt,
      requestId: request.id,
      status: request.status,
      message: `Generation request created for ${request.title}`,
    },
  ];

  if (request.plannedAt) {
    events.push({
      type: "request_planned",
      at: request.plannedAt,
      requestId: request.id,
      status: request.status,
      message: `${request.totalJobs} generation jobs were planned`,
    });
  }

  if (request.startedAt) {
    events.push({
      type: "request_started",
      at: request.startedAt,
      requestId: request.id,
      status: request.status,
      message: "Generation execution started",
    });
  }

  for (const job of request.jobs) {
    const workerTelemetry = getWorkerTelemetry(job);

    if (job.startedAt) {
      events.push({
        type: "job_started",
        at: job.startedAt,
        requestId: request.id,
        jobId: job.id,
        jobType: job.jobType,
        status: job.status,
        message: `${job.jobType} job started`,
      });
    }

    if (workerTelemetry) {
      events.push({
        type: "job_progress",
        at: workerTelemetry.at,
        requestId: request.id,
        jobId: job.id,
        jobType: job.jobType,
        status: workerTelemetry.status ?? job.status,
        message: workerTelemetry.message,
        stage: workerTelemetry.stage,
        ...(workerTelemetry.progressPercent != null
          ? { progressPercent: workerTelemetry.progressPercent }
          : {}),
        ...(workerTelemetry.diagnostics
          ? { diagnostics: workerTelemetry.diagnostics }
          : {}),
      });

      if (workerTelemetry.cost) {
        events.push({
          type: "job_cost_updated",
          at: workerTelemetry.at,
          requestId: request.id,
          jobId: job.id,
          jobType: job.jobType,
          status: workerTelemetry.status ?? job.status,
          message: `Cost updated for ${job.jobType}`,
          stage: workerTelemetry.stage,
          cost: workerTelemetry.cost,
        });
      }
    }

    if (job.completedAt && job.status === "completed") {
      events.push({
        type: "job_completed",
        at: job.completedAt,
        requestId: request.id,
        jobId: job.id,
        jobType: job.jobType,
        status: job.status,
        message: `${job.jobType} job completed`,
      });
    }

    if (job.completedAt && job.status === "failed") {
      events.push({
        type: "job_failed",
        at: job.completedAt,
        requestId: request.id,
        jobId: job.id,
        jobType: job.jobType,
        status: job.status,
        message: job.errorMessage
          ? `${job.jobType} job failed: ${job.errorMessage}`
          : `${job.jobType} job failed`,
      });
    }
  }

  if (request.completedAt && request.status === "completed") {
    events.push({
      type: "request_completed",
      at: request.completedAt,
      requestId: request.id,
      status: request.status,
      message: "Generation request completed successfully",
    });
  }

  if (request.completedAt && request.status === "failed") {
    events.push({
      type: "request_failed",
      at: request.completedAt,
      requestId: request.id,
      status: request.status,
      message: "Generation request completed with failures",
    });
  }

  return events.sort((left: any, right: any) => left.at.localeCompare(right.at));
}

function mapRequest(row: Record<string, unknown>): GenerationRequest {
  return {
    id: row.id,
    tenantId: row.tenantId,
    title: row.title,
    domain: row.domain,
    requestedBy: row.requestedBy,
    status: (row.status as string).toLowerCase() as GenerationRequest["status"],
    riskLevel: (row.riskLevel as string).toLowerCase() as any,
    reviewPath: enumToReviewPath(row.reviewPath as string),
    totalJobs: row.totalJobs,
    completedJobs: row.completedJobs,
    failedJobs: row.failedJobs,
    ...(row.description != null ? { description: row.description } : {}),
    ...(row.conceptId != null ? { conceptId: row.conceptId } : {}),
    ...(row.targetGrades != null ? { targetGrades: row.targetGrades } : {}),
    ...(row.plannedAssets != null ? { plannedAssets: row.plannedAssets } : {}),
    ...(row.artifactNeeds != null ? { artifactNeeds: row.artifactNeeds } : {}),
    ...(row.riskFactors != null ? { riskFactors: row.riskFactors } : {}),
    ...(row.estimatedCost != null ? { estimatedCost: row.estimatedCost } : {}),
    ...(row.plannedAt
      ? { plannedAt: (row.plannedAt as Date).toISOString() }
      : {}),
    ...(row.startedAt
      ? { startedAt: (row.startedAt as Date).toISOString() }
      : {}),
    ...(row.completedAt
      ? { completedAt: (row.completedAt as Date).toISOString() }
      : {}),
    createdAt: (row.createdAt as Date).toISOString(),
    updatedAt: (row.updatedAt as Date).toISOString(),
  };
}

function mapJob(row: Record<string, unknown>): GenerationJob {
  return {
    id: row.id,
    requestId: row.requestId,
    jobType: (row.jobType as string).toLowerCase() as GenerationJob["jobType"],
    status: (row.status as string).toLowerCase() as GenerationJob["status"],
    progress: row.progress,
    retryCount: row.retryCount,
    maxRetries: row.maxRetries,
    ...(row.targetRef != null ? { targetRef: row.targetRef } : {}),
    ...(row.inputPrompt != null ? { inputPrompt: row.inputPrompt } : {}),
    ...(row.parameters != null ? { parameters: row.parameters } : {}),
    ...(row.outputAssetId != null ? { outputAssetId: row.outputAssetId } : {}),
    ...(row.outputData != null ? { outputData: row.outputData } : {}),
    ...(row.diagnostics != null ? { diagnostics: row.diagnostics } : {}),
    ...(row.errorMessage != null ? { errorMessage: row.errorMessage } : {}),
    ...(row.startedAt
      ? { startedAt: (row.startedAt as Date).toISOString() }
      : {}),
    ...(row.completedAt
      ? { completedAt: (row.completedAt as Date).toISOString() }
      : {}),
    createdAt: (row.createdAt as Date).toISOString(),
    updatedAt: (row.updatedAt as Date).toISOString(),
  };
}

function enumToReviewPath(value: string): any {
  switch (value) {
    case "AUTO_PUBLISH":
      return "auto_publish";
    case "EXPERT_REVIEW":
      return "expert_review";
    default:
      return "human_review";
  }
}

function buildExecutionCostSummary(
  request: GenerationRequestWithJobs,
): GenerationExecutionCostSummary | null {
  const estimatedCost = asRecord(request.estimatedCost);
  const estimatedTokens = asNumber(estimatedCost?.totalTokens) ?? 0;
  const estimatedCostUsd =
    asNumber(estimatedCost?.estimatedSpendUsd) ??
    asNumber(estimatedCost?.estimatedCostUsd) ??
    0;

  let actualTokens = 0;
  let actualCostUsd = 0;

  for (const job of request.jobs) {
    const telemetry = getWorkerTelemetry(job);
    if (!telemetry?.cost) {
      continue;
    }

    actualTokens += telemetry.cost.actualTokens ?? 0;
    actualCostUsd += telemetry.cost.actualCostUsd ?? 0;
  }

  if (
    estimatedTokens === 0 &&
    estimatedCostUsd === 0 &&
    actualTokens === 0 &&
    actualCostUsd === 0
  ) {
    return null;
  }

  return {
    estimatedTokens,
    actualTokens,
    estimatedCostUsd: roundUsd(estimatedCostUsd),
    actualCostUsd: roundUsd(actualCostUsd),
  };
}

function getWorkerTelemetry(
  job: Pick<GenerationJob, "diagnostics">,
): GenerationExecutionWorkerTelemetry | null {
  const diagnostics = asRecord(job.diagnostics);
  const telemetry = asRecord(diagnostics?.workerTelemetry);
  if (!telemetry) {
    return null;
  }

  const at = typeof telemetry.at === "string" ? telemetry.at : null;
  const requestId =
    typeof telemetry.requestId === "string" ? telemetry.requestId : null;
  const jobId = typeof telemetry.jobId === "string" ? telemetry.jobId : null;
  const stage = typeof telemetry.stage === "string" ? telemetry.stage : null;
  const message =
    typeof telemetry.message === "string" ? telemetry.message : null;

  if (!at || !requestId || !jobId || !stage || !message) {
    return null;
  }

  const cost = asRecord(telemetry.cost);
  const telemetryDiagnostics = asRecord(telemetry.diagnostics);
  const costPayload = cost
    ? {
        ...(typeof cost.model === "string" ? { model: cost.model } : {}),
        ...(typeof cost.generationTimeMs === "number"
          ? { generationTimeMs: cost.generationTimeMs }
          : {}),
        ...(typeof cost.estimatedTokens === "number"
          ? { estimatedTokens: cost.estimatedTokens }
          : {}),
        ...(typeof cost.actualTokens === "number"
          ? { actualTokens: cost.actualTokens }
          : {}),
        ...(typeof cost.estimatedCostUsd === "number"
          ? { estimatedCostUsd: cost.estimatedCostUsd }
          : {}),
        ...(typeof cost.actualCostUsd === "number"
          ? { actualCostUsd: cost.actualCostUsd }
          : {}),
      }
    : null;

  return {
    at,
    requestId,
    jobId,
    stage,
    message,
    ...(typeof telemetry.jobType === "string"
      ? { jobType: telemetry.jobType as GenerationExecutionWorkerTelemetry["jobType"] }
      : {}),
    ...(typeof telemetry.progressPercent === "number"
      ? { progressPercent: telemetry.progressPercent }
      : {}),
    ...(typeof telemetry.status === "string"
      ? { status: telemetry.status as GenerationExecutionWorkerTelemetry["status"] }
      : {}),
    ...(telemetryDiagnostics ? { diagnostics: telemetryDiagnostics } : {}),
    ...(costPayload ? { cost: costPayload } : {}),
  } satisfies GenerationExecutionWorkerTelemetry;
}

function asRecord(value: any): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function asNumber(value: any): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function roundUsd(value: number): number {
  return Math.round(value * 10000) / 10000;
}
