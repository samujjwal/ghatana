/**
 * Generation Queue Dispatcher
 *
 * Bridges planned generation requests into the BullMQ execution plane while
 * respecting planner-defined dependencies.
 *
 * @doc.type class
 * @doc.purpose Dispatch ready generation jobs into the content worker queue
 * @doc.layer product
 * @doc.pattern Dispatcher
 */

import type { PrismaClient } from "@tutorputor/core/db";

type GenerationJob = {
  id: string;
  jobType: string;
  status: string;
  maxRetries?: number;
  progress: number;
  diagnostics?: Record<string, unknown>;
  dependencies?: string[];
  targetRef?: string;
  inputPrompt?: string;
  outputAssetId?: string;
  parameters?: Record<string, unknown>;
};

type GenerationRequestWithJobs = {
  id: string;
  tenantId: string;
  requestedBy: string;
  title: string;
  description?: string;
  domain: string;
  conceptId?: string;
  targetGrades?: string[];
  requestConfig?: Record<string, unknown>;
  jobs: GenerationJob[];
};
import {
  getContentGenerationQueue,
  type ContentGenerationQueueLike,
} from "../queue/content-generation-queue.js";
import type { JobExecutionResult } from "./execution-service.js";

export interface GenerationRequestExecutionJobData {
  generationRequestId: string;
  generationJobId: string;
  tenantId: string;
  requestedBy: string;
  requestTitle: string;
  requestDescription?: string;
  domain: string;
  conceptId?: string;
  targetGrades: string[];
  requestConfig?: Record<string, unknown>;
  generationJobType: GenerationJob["jobType"];
  targetRef?: string;
  inputPrompt?: string;
  outputAssetId?: string;
  parameters?: Record<string, unknown>;
}

export interface GenerationDispatchSummary {
  requestId: string;
  queuedJobs: Array<{
    generationJobId: string;
    queueJobId: string;
    jobType: GenerationJob["jobType"];
  }>;
  blockedJobs: string[];
  skippedJobs: string[];
}

export class GenerationQueueDispatcher {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly queue: ContentGenerationQueueLike = getContentGenerationQueue(),
  ) {}

  async dispatchReadyJobs(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationDispatchSummary> {
    const request = await this.getRequest(tenantId, requestId);
    if (!request) {
      throw new Error(`Generation request ${requestId} not found`);
    }

    const queuedJobs: GenerationDispatchSummary["queuedJobs"] = [];
    const blockedJobs: string[] = [];
    const skippedJobs: string[] = [];

    for (const job of request.jobs) {
      if (!isDispatchableStatus(job.status)) {
        skippedJobs.push(job.id);
        continue;
      }

      if (hasQueueDispatch(job)) {
        skippedJobs.push(job.id);
        continue;
      }

      const dependencyState = getDependencyState(request.jobs, job);
      if (dependencyState === "blocked") {
        blockedJobs.push(job.id);
        continue;
      }
      if (dependencyState === "waiting") {
        skippedJobs.push(job.id);
        continue;
      }

      const payload = buildQueuePayload(request, job);
      const queueJobId = `execute-generation-job:${request.id}:${job.id}`;
      const enqueued = await this.queue.add(
        "execute-generation-job",
        payload,
        {
          jobId: queueJobId,
          attempts: Math.max(1, job.maxRetries || 1),
          backoff: { type: "exponential", delay: 2000 },
          removeOnComplete: 100,
          removeOnFail: 200,
        },
      );

      await (this.prisma as any).generationJob.update({
        where: { id: job.id },
        data: {
          status: "RUNNING",
          startedAt: new Date(),
          progress: Math.max(job.progress, 5),
          diagnostics: mergeJobDiagnostics(job.diagnostics, {
            queueDispatch: {
              queueJobId:
                enqueued.id != null ? String(enqueued.id) : queueJobId,
              enqueuedAt: new Date().toISOString(),
              dispatcher: "generation_queue_dispatcher",
            },
          }),
        },
      });

      queuedJobs.push({
        generationJobId: job.id,
        queueJobId: enqueued.id != null ? String(enqueued.id) : queueJobId,
        jobType: job.jobType,
      });
    }

    return {
      requestId,
      queuedJobs,
      blockedJobs,
      skippedJobs,
    };
  }

  async collectDependencyFailureResults(
    tenantId: string,
    requestId: string,
  ): Promise<JobExecutionResult[]> {
    const request = await this.getRequest(tenantId, requestId);
    if (!request) {
      return [];
    }

    const results: JobExecutionResult[] = [];

    for (const job of request.jobs) {
      if (!isDispatchableStatus(job.status)) {
        continue;
      }

      const failedDependencies = getFailedDependencyRefs(request.jobs, job);
      if (failedDependencies.length === 0) {
        continue;
      }

      results.push({
        jobId: job.id,
        status: "failed",
        durationMs: 0,
        errorMessage: `Blocked by failed dependencies: ${failedDependencies.join(", ")}`,
        diagnostics: {
          dependencyFailure: {
            failedDependencies,
          },
        },
      });
    }

    return results;
  }

  private async getRequest(
    tenantId: string,
    requestId: string,
  ): Promise<GenerationRequestWithJobs | null> {
    const row = await (this.prisma as any).generationRequest.findFirst({
      where: { id: requestId, tenantId },
      include: { jobs: true },
    });

    if (!row) {
      return null;
    }

    return {
      id: row.id,
      tenantId: row.tenantId,
      title: row.title,
      ...(row.description != null ? { description: row.description } : {}),
      domain: row.domain,
      ...(row.conceptId != null ? { conceptId: row.conceptId } : {}),
      ...(row.targetGrades != null ? { targetGrades: row.targetGrades } : {}),
      requestedBy: row.requestedBy,
      ...(row.requestConfig != null ? { requestConfig: row.requestConfig } : {}),
      status: String(row.status).toLowerCase(),
      riskLevel: String(row.riskLevel).toLowerCase(),
      reviewPath: mapReviewPath(row.reviewPath),
      totalJobs: row.totalJobs,
      completedJobs: row.completedJobs,
      failedJobs: row.failedJobs,
      ...(row.plannedAssets != null ? { plannedAssets: row.plannedAssets } : {}),
      ...(row.artifactNeeds != null ? { artifactNeeds: row.artifactNeeds } : {}),
      ...(row.riskFactors != null ? { riskFactors: row.riskFactors } : {}),
      ...(row.estimatedCost != null ? { estimatedCost: row.estimatedCost } : {}),
      ...(row.routingDecision != null
        ? { routingDecision: row.routingDecision }
        : {}),
      ...(row.plannedAt ? { plannedAt: row.plannedAt.toISOString() } : {}),
      ...(row.startedAt ? { startedAt: row.startedAt.toISOString() } : {}),
      ...(row.completedAt ? { completedAt: row.completedAt.toISOString() } : {}),
      createdAt: row.createdAt.toISOString(),
      updatedAt: row.updatedAt.toISOString(),
      jobs: (row.jobs ?? []).map((job: any) => ({
        id: job.id,
        requestId: job.requestId,
        jobType: String(job.jobType).toLowerCase(),
        status: String(job.status).toLowerCase(),
        progress: job.progress,
        retryCount: job.retryCount,
        maxRetries: job.maxRetries,
        ...(job.targetRef != null ? { targetRef: job.targetRef } : {}),
        ...(job.inputPrompt != null ? { inputPrompt: job.inputPrompt } : {}),
        ...(job.parameters != null ? { parameters: job.parameters } : {}),
        ...(job.outputAssetId != null
          ? { outputAssetId: job.outputAssetId }
          : {}),
        ...(job.outputData != null ? { outputData: job.outputData } : {}),
        ...(job.diagnostics != null ? { diagnostics: job.diagnostics } : {}),
        ...(job.errorMessage != null
          ? { errorMessage: job.errorMessage }
          : {}),
        ...(job.startedAt ? { startedAt: job.startedAt.toISOString() } : {}),
        ...(job.completedAt
          ? { completedAt: job.completedAt.toISOString() }
          : {}),
        createdAt: job.createdAt.toISOString(),
        updatedAt: job.updatedAt.toISOString(),
      })),
    } as GenerationRequestWithJobs;
  }
}

function buildQueuePayload(
  request: GenerationRequestWithJobs,
  job: GenerationJob,
): GenerationRequestExecutionJobData {
  return {
    generationRequestId: request.id,
    generationJobId: job.id,
    tenantId: request.tenantId,
    requestedBy: request.requestedBy,
    requestTitle: request.title,
    ...(request.description ? { requestDescription: request.description } : {}),
    domain: request.domain,
    ...(request.conceptId ? { conceptId: request.conceptId } : {}),
    targetGrades: request.targetGrades ?? [],
    ...(request.requestConfig ? { requestConfig: request.requestConfig } : {}),
    generationJobType: job.jobType,
    ...(job.targetRef ? { targetRef: job.targetRef } : {}),
    ...(job.inputPrompt ? { inputPrompt: job.inputPrompt } : {}),
    ...(job.outputAssetId ? { outputAssetId: job.outputAssetId } : {}),
    ...(job.parameters ? { parameters: job.parameters } : {}),
  } as GenerationRequestExecutionJobData;
}

function getDependencyRefs(job: GenerationJob): string[] {
  const parameters = asRecord(job.parameters);
  const dependsOn = parameters?.["dependsOn"];
  return Array.isArray(dependsOn)
    ? dependsOn.filter((value): value is string => typeof value === "string")
    : [];
}

function getDependencyState(
  jobs: GenerationJob[],
  job: GenerationJob,
): "ready" | "waiting" | "blocked" {
  const dependencyRefs = getDependencyRefs(job);
  if (dependencyRefs.length === 0) {
    return "ready";
  }

  let waiting = false;

  for (const dependencyRef of dependencyRefs) {
    const dependencyJob = jobs.find((candidate: any) => candidate.targetRef === dependencyRef);
    if (!dependencyJob) {
      waiting = true;
      continue;
    }
    if (dependencyJob.status === "failed" || dependencyJob.status === "cancelled") {
      return "blocked";
    }
    if (dependencyJob.status !== "completed") {
      waiting = true;
    }
  }

  return waiting ? "waiting" : "ready";
}

function getFailedDependencyRefs(
  jobs: GenerationJob[],
  job: GenerationJob,
): string[] {
  return getDependencyRefs(job).filter((dependencyRef: any) => {
    const dependencyJob = jobs.find((candidate: any) => candidate.targetRef === dependencyRef);
    return (
      dependencyJob != null &&
      (dependencyJob.status === "failed" || dependencyJob.status === "cancelled")
    );
  });
}

function hasQueueDispatch(job: GenerationJob): boolean {
  const diagnostics = asRecord(job.diagnostics);
  const queueDispatch = asRecord(diagnostics?.["queueDispatch"]);
  return queueDispatch != null;
}

function isDispatchableStatus(status: GenerationJob["status"]): boolean {
  return status === "pending" || status === "running";
}

function mergeJobDiagnostics(
  existing: unknown,
  next: Record<string, unknown>,
): Record<string, unknown> {
  const diagnostics = asRecord(existing) ?? {};
  return {
    ...diagnostics,
    ...next,
  };
}

function asRecord(value: any): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function mapReviewPath(value: string): "auto_publish" | "expert_review" | "human_review" {
  switch (value) {
    case "AUTO_PUBLISH":
      return "auto_publish";
    case "EXPERT_REVIEW":
      return "expert_review";
    default:
      return "human_review";
  }
}
