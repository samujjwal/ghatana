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
import type {
  GenerationRequest,
  GenerationJob,
  GenerationRequestWithJobs,
} from "@tutorputor/contracts/v1/content-studio";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface JobExecutionResult {
  jobId: string;
  status: "completed" | "failed";
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

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class GenerationExecutionService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Begin execution of a PLANNED generation request.
   *
   * Transitions the request to EXECUTING and marks all PENDING jobs
   * as RUNNING.
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

      await tx.generationJob.updateMany({
        where: { requestId, status: "PENDING" },
        data: { status: "RUNNING", startedAt: new Date() },
      });
    });

    // Re-fetch with updated statuses
    const updated = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
      include: { jobs: true },
    });

    return {
      ...mapRequest(updated),
      jobs: (updated.jobs ?? []).map(mapJob),
    };
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
}

// ---------------------------------------------------------------------------
// Mappers
// ---------------------------------------------------------------------------

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
    riskLevel: (row.riskLevel as string).toLowerCase() as any,
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
