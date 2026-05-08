/**
 * Content Worker Entry Point
 *
 * Configures and starts the background worker for content generation.
 * Handles dispatching jobs to specific processors.
 */

import { Worker, Job, Queue } from "bullmq";
import { PrismaClient } from "@tutorputor/core/db";
import Redis from "ioredis";
import { Logger } from "pino";
import { RealContentGenerationClient } from "./grpc/RealContentGenerationClient";
import {
  ClaimGenerationProcessor,
  type ClaimGenerationJobData,
} from "./processors/ClaimGenerationProcessor";
import {
  ExampleGenerationProcessor,
  type ExampleGenerationJobData,
} from "./processors/ExampleGenerationProcessor";
import {
  SimulationGenerationProcessor,
  type SimulationGenerationJobData,
} from "./processors/SimulationGenerationProcessor";
import {
  AnimationGenerationProcessor,
  type AnimationGenerationJobData,
} from "./processors/AnimationGenerationProcessor";
import {
  ContentValidationProcessor,
  type ContentValidationJobData,
} from "./processors/ContentValidationProcessor";
import { GenerationRequestJobProcessor } from "./processors/GenerationRequestJobProcessor";
import {
  DeadLetterQueueManager,
  createQueueOptionsWithDLQ,
} from "../../utils/dead-letter-queue";
import { JobDeduplicator } from "../../utils/job-deduplication";
import { ContentWorkerTelemetryPublisher } from "./generation-telemetry";
import { GenerationExecutionService } from "../../modules/content/generation/execution-service";
import { GenerationQueueDispatcher } from "../../modules/content/generation/queue-dispatcher";
import type { GenerationRequestExecutionJobData } from "../../modules/content/generation/queue-dispatcher";
import {
  type ContentGenerationFlags,
  loadFeatureFlags,
} from "../../config/feature-flags.js";

type BullConnection = NonNullable<ConstructorParameters<typeof Queue>[1]>["connection"];
type ContentWorkerJobData =
  | ClaimGenerationJobData
  | ExampleGenerationJobData
  | SimulationGenerationJobData
  | ContentValidationJobData
  | AnimationGenerationJobData
  | GenerationRequestExecutionJobData;

function asTypedJob<T extends ContentWorkerJobData>(job: Job): Job<T> {
  return job as unknown as Job<T>;
}

export interface ContentWorkerConfig {
  redis: {
    host: string;
    port: number;
    password?: string;
    db?: number;
  };
  grpc: {
    serverAddress: string;
    useTls: boolean;
  };
  concurrency?: number;
  logger: Logger;
  prisma?: PrismaClient;
  featureFlags?: ContentGenerationFlags;
}

export class ContentWorkerService {
  private worker: Worker | null = null;
  private producerQueue: Queue | null = null;
  private redisConnection: Redis | null = null;
  private prisma: PrismaClient;
  private logger: Logger;
  private grpcClient: RealContentGenerationClient;
  private dlqManager: DeadLetterQueueManager;
  private jobDeduplicator: JobDeduplicator;
  private telemetryPublisher: ContentWorkerTelemetryPublisher;

  // Processors
  private claimProcessor: ClaimGenerationProcessor;
  private exampleProcessor: ExampleGenerationProcessor;
  private simulationProcessor: SimulationGenerationProcessor;
  private animationProcessor: AnimationGenerationProcessor;
  private validationProcessor: ContentValidationProcessor;
  private generationRequestJobProcessor: GenerationRequestJobProcessor;

  constructor(config: ContentWorkerConfig) {
    this.logger = config.logger;
    this.prisma = config.prisma || new PrismaClient();
    const featureFlags = config.featureFlags || loadFeatureFlags();

    this.grpcClient = new RealContentGenerationClient({
      serverAddress: config.grpc.serverAddress,
      useTls: config.grpc.useTls,
      logger: this.logger,
      timeout: 5000,
      maxRetries: 3,
    });

    this.redisConnection = new (Redis as unknown as new (...args: unknown[]) => Redis)({
      host: config.redis.host,
      port: config.redis.port,
      password: config.redis.password,
      db: config.redis.db || 0,
      maxRetriesPerRequest: null,
    });

    // Initialize DLQ manager
    this.dlqManager = new DeadLetterQueueManager(
      {
        name: "content-generation-dlq",
        redis: config.redis,
      },
      this.logger,
    );

    // Initialize distributed job deduplicator (Redis + Prisma fallback)
    this.jobDeduplicator = new JobDeduplicator(this.prisma, {
      ...(this.redisConnection ? { redis: this.redisConnection } : {}),
    });
    this.telemetryPublisher = new ContentWorkerTelemetryPublisher(
      this.prisma,
      this.logger,
      this.redisConnection ?? undefined,
    );

    this.producerQueue = new Queue("content-generation", {
      connection: this.redisConnection as unknown as BullConnection,
      defaultJobOptions: createQueueOptionsWithDLQ(3, 5000),
    });
    const generationExecutionService = new GenerationExecutionService(
      this.prisma,
      this.redisConnection ?? undefined,
    );
    const generationQueueDispatcher = new GenerationQueueDispatcher(this.prisma);

    this.claimProcessor = new ClaimGenerationProcessor(
      this.grpcClient,
      this.prisma,
      this.producerQueue,
      this.logger,
      this.telemetryPublisher,
    );
    this.exampleProcessor = new ExampleGenerationProcessor(
      this.grpcClient,
      this.prisma,
      this.logger,
      this.telemetryPublisher,
    );
    this.simulationProcessor = new SimulationGenerationProcessor(
      this.grpcClient,
      this.prisma,
      this.logger,
      this.telemetryPublisher,
    );
    this.animationProcessor = new AnimationGenerationProcessor(
      this.grpcClient,
      this.prisma,
      this.logger,
      this.telemetryPublisher,
    );
    this.validationProcessor = new ContentValidationProcessor(
      this.grpcClient,
      this.prisma,
      this.logger,
      this.telemetryPublisher,
    );
    this.generationRequestJobProcessor = new GenerationRequestJobProcessor(
      this.grpcClient,
      this.prisma,
      this.logger,
      this.telemetryPublisher,
      generationExecutionService,
      generationQueueDispatcher,
      featureFlags,
    );

    this.worker = new Worker(
      "content-generation",
      async (job: Job) => {
        this.logger.info({ jobId: job.id, name: job.name }, "Received job");

        const deduplicationFingerprint =
          this.jobDeduplicator.generateFingerprint(
            job.name,
            String(
              (job.data as { experienceId?: string })?.experienceId ??
                "unknown-experience",
            ),
            String(
              (job.data as { claimRef?: string })?.claimRef ??
                String(job.id ?? "unknown-job"),
            ),
            (job.data ?? {}) as Record<string, unknown>,
          );

        const deduplication = await this.jobDeduplicator.checkDuplicate(
          deduplicationFingerprint,
        );
        if (deduplication.isDuplicate) {
          this.logger.info(
            {
              jobId: job.id,
              name: job.name,
              existingJobId: deduplication.existingJobId,
            },
            "Duplicate job detected, skipping execution",
          );
          return;
        }

        const trackedJobId = String(job.id ?? `${job.name}:${Date.now()}`);
        await this.jobDeduplicator.trackJob(
          trackedJobId,
          deduplication.fingerprint,
          job.name,
          {
            queue: "content-generation",
          },
        );
        await this.jobDeduplicator.updateJobStatus(trackedJobId, "PROCESSING");
        await this.telemetryPublisher.publishStarted(
          asTypedJob<ContentWorkerJobData>(job),
        );

        try {
          switch (job.name) {
            case "generate-claims":
              await this.claimProcessor.process(asTypedJob<ClaimGenerationJobData>(job));
              break;
            case "generate-examples":
              await this.exampleProcessor.process(asTypedJob<ExampleGenerationJobData>(job));
              break;
            case "generate-simulation":
              await this.simulationProcessor.process(asTypedJob<SimulationGenerationJobData>(job));
              break;
            case "validate-content":
              await this.validationProcessor.process(asTypedJob<ContentValidationJobData>(job));
              break;
            case "generate-animation":
              await this.animationProcessor.process(asTypedJob<AnimationGenerationJobData>(job));
              break;
            case "execute-generation-job":
              await this.generationRequestJobProcessor.process(
                asTypedJob<GenerationRequestExecutionJobData>(job),
              );
              break;
            default:
              this.logger.error(
                { jobId: job.id, name: job.name },
                "Unknown job name - treating as failure",
              );
              throw new Error(`Unknown job name: ${job.name}`);
          }

          await this.jobDeduplicator.updateJobStatus(trackedJobId, "COMPLETED");
          await this.telemetryPublisher.publishCompleted(
            asTypedJob<ContentWorkerJobData>(job),
            {
              deduplicationJobId: trackedJobId,
            },
          );
        } catch (error: unknown) {
          await this.jobDeduplicator.updateJobStatus(trackedJobId, "FAILED");
          await this.telemetryPublisher.publishFailed(
            asTypedJob<ContentWorkerJobData>(job),
            error instanceof Error ? error : new Error(String(error)),
          );
          this.logger.error(
            { jobId: job.id, err: error },
            "Job processing failed",
          );
          throw error;
        }
      },
      {
        connection: this.redisConnection as unknown as BullConnection,
        concurrency: config.concurrency || 5,
        removeOnComplete: { count: 100 },
        removeOnFail: { count: 500 },
      },
    );

    this.setupEventHandlers();
  }

  private setupEventHandlers() {
    if (!this.worker) return;

    this.worker.on("completed", (job) => {
      this.logger.info(
        { jobId: job.id, name: job.name },
        "Job completed successfully",
      );
    });

    this.worker.on("failed", async (job, err) => {
      this.logger.error({ jobId: job?.id, name: job?.name, err }, "Job failed");

      // Move to DLQ after max retries
      if (job && job.attemptsMade >= 3) {
        await this.dlqManager.moveToDLQ(job, err.message);
      }
    });

    this.worker.on("error", (err) => {
      this.logger.error({ err }, "Worker error");
    });
  }

  async healthCheck(): Promise<boolean> {
    if (!this.worker || !this.producerQueue || !this.redisConnection) {
      throw new Error("Content worker is not fully initialized");
    }

    const redis = this.redisConnection as Redis & {
      ping: () => Promise<string>;
    };
    const ping = await redis.ping();
    if (ping !== "PONG") {
      throw new Error(`Unexpected Redis ping response: ${ping}`);
    }

    return true;
  }

  async close() {
    if (this.worker) {
      await this.worker.close();
    }
    if (this.producerQueue) {
      await this.producerQueue.close();
    }
    if (this.dlqManager) {
      await this.dlqManager.close();
    }
    if (this.redisConnection) {
      const redis = this.redisConnection as Redis & {
        quit: () => Promise<void>;
      };
      await redis.quit();
    }
    // We do not close prisma here as it might be shared
  }
}
