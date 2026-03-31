/**
 * Worker Generation Telemetry
 *
 * Bridges worker-side progress and cost signals into the shared generation
 * execution stream without duplicating route-specific logic.
 *
 * @doc.type module
 * @doc.purpose Publish worker-originated execution telemetry for correlated generation jobs
 * @doc.layer backend-worker
 * @doc.pattern Publisher
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type Redis from "ioredis";
import type { Logger } from "pino";

type GenerationJobStatus = "pending" | "running" | "completed" | "failed";
type GenerationExecutionWorkerTelemetry = {
  at: string;
  requestId: string;
  jobId: string;
  stage: string;
  message: string;
  jobType?: string;
  progressPercent?: number;
  status?: GenerationJobStatus;
  diagnostics?: Record<string, unknown>;
  cost?: {
    model?: string;
    estimatedTokens?: number;
    actualTokens?: number;
    estimatedCostUsd?: number;
    actualCostUsd?: number;
    generationTimeMs?: number;
  };
};
import {
  getGenerationExecutionChannel,
  type GenerationExecutionStreamMessage,
} from "../../modules/content/generation/execution-stream.js";

const DEFAULT_COST_PER_1K_TOKENS = 0.002;

export interface CorrelatedGenerationJobData {
  generationRequestId?: string;
  generationJobId?: string;
}

export interface WorkerGenerationTelemetryInput {
  stage: string;
  message: string;
  progressPercent?: number;
  status?: GenerationJobStatus;
  diagnostics?: Record<string, unknown>;
  cost?: {
    model?: string;
    estimatedTokens?: number;
    actualTokens?: number;
    estimatedCostUsd?: number;
    actualCostUsd?: number;
    generationTimeMs?: number;
  };
}

export interface GenerationMetadataLike {
  model_name?: unknown;
  tokens_used?: unknown;
  generation_time_ms?: unknown;
}

type TelemetryJobEnvelope<T extends CorrelatedGenerationJobData> = {
  id?: unknown;
  name?: unknown;
  attemptsMade?: unknown;
  data: T;
};

export class ContentWorkerTelemetryPublisher {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
    private readonly redis?: Redis | null,
  ) {}

  async publishForJob<T extends CorrelatedGenerationJobData>(
    job: TelemetryJobEnvelope<T>,
    input: WorkerGenerationTelemetryInput,
  ): Promise<void> {
    const generationJobId =
      typeof job.data.generationJobId === "string"
        ? job.data.generationJobId
        : null;
    const generationRequestId =
      typeof job.data.generationRequestId === "string"
        ? job.data.generationRequestId
        : null;

    if (!generationJobId && !generationRequestId) {
      return;
    }

    const persistedTelemetry = await this.persistTelemetry(
      generationJobId,
      generationRequestId,
      job,
      input,
    );

    if (!persistedTelemetry || !this.redis) {
      return;
    }

    const message: GenerationExecutionStreamMessage = {
      kind: "telemetry",
      requestId: persistedTelemetry.requestId,
      at: persistedTelemetry.at,
      telemetry: persistedTelemetry,
    };

    await this.redis.publish(
      getGenerationExecutionChannel(persistedTelemetry.requestId),
      JSON.stringify(message),
    );
  }

  async publishStarted<T extends CorrelatedGenerationJobData>(
    job: TelemetryJobEnvelope<T>,
    message = "Worker accepted generation job",
  ): Promise<void> {
    await this.publishForJob(job, {
      stage: "worker_started",
      message,
      progressPercent: 5,
      status: "running",
      diagnostics: {
        queueJobName: typeof job.name === "string" ? job.name : "unknown",
        queueJobId: job.id != null ? String(job.id) : "unknown",
        attemptsMade:
          typeof job.attemptsMade === "number" ? job.attemptsMade : 0,
      },
    });
  }

  async publishCompleted<T extends CorrelatedGenerationJobData>(
    job: TelemetryJobEnvelope<T>,
    diagnostics?: Record<string, unknown>,
  ): Promise<void> {
    await this.publishForJob(job, {
      stage: "worker_completed",
      message: "Worker finished generation job",
      progressPercent: 100,
      status: "completed",
      ...(diagnostics ? { diagnostics } : {}),
    });
  }

  async publishFailed<T extends CorrelatedGenerationJobData>(
    job: TelemetryJobEnvelope<T>,
    error: Error,
  ): Promise<void> {
    await this.publishForJob(job, {
      stage: "worker_failed",
      message: error.message || "Worker generation job failed",
      progressPercent: 100,
      status: "failed",
      diagnostics: {
        queueJobName: typeof job.name === "string" ? job.name : "unknown",
        queueJobId: job.id != null ? String(job.id) : "unknown",
        attemptsMade:
          typeof job.attemptsMade === "number" ? job.attemptsMade : 0,
      },
    });
  }

  static extractCostFromMetadata(
    metadata: GenerationMetadataLike | null | undefined,
  ): WorkerGenerationTelemetryInput["cost"] | undefined {
    if (!metadata) {
      return undefined;
    }

    const actualTokens = toFiniteNumber(metadata.tokens_used);
    const generationTimeMs = toFiniteNumber(metadata.generation_time_ms);
    const model =
      typeof metadata.model_name === "string" && metadata.model_name.length > 0
        ? metadata.model_name
        : undefined;

    if (actualTokens == null && generationTimeMs == null && !model) {
      return undefined;
    }

    const actualCostUsd =
      actualTokens != null
        ? roundUsd((actualTokens / 1000) * DEFAULT_COST_PER_1K_TOKENS)
        : undefined;

    return {
      ...(model ? { model } : {}),
      ...(actualTokens != null ? { actualTokens } : {}),
      ...(actualCostUsd != null ? { actualCostUsd } : {}),
      ...(generationTimeMs != null ? { generationTimeMs } : {}),
    };
  }

  private async persistTelemetry(
    generationJobId: string | null,
    generationRequestId: string | null,
    job: TelemetryJobEnvelope<CorrelatedGenerationJobData>,
    input: WorkerGenerationTelemetryInput,
  ): Promise<GenerationExecutionWorkerTelemetry | null> {
    const at = new Date().toISOString();
    const queueJobId = job.id != null ? String(job.id) : "unknown";

    const existingJob =
      generationJobId == null
        ? null
        : await (this.prisma as any).generationJob.findUnique({
            where: { id: generationJobId },
            select: {
              id: true,
              requestId: true,
              jobType: true,
              progress: true,
              diagnostics: true,
              startedAt: true,
            },
          });

    const requestId = generationRequestId ?? existingJob?.requestId ?? null;
    if (!requestId) {
      this.logger.warn(
        {
          generationJobId,
          queueJobId,
          queueJobName: job.name,
        },
        "Skipping generation telemetry publish because request correlation is missing",
      );
      return null;
    }

    const telemetry = {
      at,
      requestId,
      jobId: generationJobId ?? queueJobId,
      stage: input.stage,
      message: input.message,
      ...(existingJob?.jobType
        ? {
            jobType: String(existingJob.jobType).toLowerCase() as GenerationExecutionWorkerTelemetry["jobType"],
          }
        : {}),
      ...(input.progressPercent != null
        ? { progressPercent: input.progressPercent }
        : {}),
      ...(input.status ? { status: input.status } : {}),
      ...(input.diagnostics ? { diagnostics: input.diagnostics } : {}),
      ...(input.cost
        ? {
            cost: {
              ...(input.cost.model ? { model: input.cost.model } : {}),
              ...(input.cost.estimatedTokens != null
                ? { estimatedTokens: input.cost.estimatedTokens }
                : {}),
              ...(input.cost.actualTokens != null
                ? { actualTokens: input.cost.actualTokens }
                : {}),
              ...(input.cost.estimatedCostUsd != null
                ? { estimatedCostUsd: input.cost.estimatedCostUsd }
                : {}),
              ...(input.cost.actualCostUsd != null
                ? { actualCostUsd: input.cost.actualCostUsd }
                : {}),
              ...(input.cost.generationTimeMs != null
                ? { generationTimeMs: input.cost.generationTimeMs }
                : {}),
            },
          }
        : {}),
    } as GenerationExecutionWorkerTelemetry;

    if (generationJobId && existingJob) {
      const diagnostics = mergeDiagnostics(existingJob.diagnostics, telemetry, job);
      const progressPercent =
        input.progressPercent != null
          ? clampProgress(input.progressPercent)
          : existingJob.progress;

      await (this.prisma as any).generationJob.update({
        where: { id: generationJobId },
        data: {
          progress: progressPercent,
          diagnostics,
          ...(existingJob.startedAt == null &&
          (input.status === "running" || progressPercent > 0)
            ? { startedAt: new Date(at) }
            : {}),
        },
      });
    }

    return telemetry;
  }
}

function mergeDiagnostics(
  existing: unknown,
  telemetry: GenerationExecutionWorkerTelemetry,
  job: Pick<TelemetryJobEnvelope<CorrelatedGenerationJobData>, "id" | "name" | "attemptsMade">,
): Record<string, unknown> {
  const diagnostics = asRecord(existing) ?? {};

  return {
    ...diagnostics,
    workerTelemetry: telemetry,
    queue: {
      queueJobId: job.id != null ? String(job.id) : "unknown",
      queueJobName: typeof job.name === "string" ? job.name : "unknown",
      attemptsMade:
        typeof job.attemptsMade === "number" ? job.attemptsMade : 0,
    },
  };
}

function asRecord(value: any): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function clampProgress(value: number): number {
  return Math.max(0, Math.min(100, Math.round(value)));
}

function toFiniteNumber(value: any): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function roundUsd(value: number): number {
  return Math.round(value * 10000) / 10000;
}
