/**
 * Example Generation Processor - Generates concrete examples for claims.
 *
 * @doc.type class
 * @doc.purpose Process example generation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from "bullmq";
import { Prisma, PrismaClient } from "@tutorputor/core/db";
import { Logger } from "pino";
import { RealContentGenerationClient } from "../grpc/RealContentGenerationClient";
import * as crypto from "crypto";
import {
  type CorrelatedGenerationJobData,
  ContentWorkerTelemetryPublisher,
} from "../generation-telemetry";

export interface ExampleGenerationJobData extends CorrelatedGenerationJobData {
  experienceId: string;
  tenantId: string;
  claimRef: string;
  claimText: string;
  gradeLevel: string;
  domain: string;
  types: string[];
  count: number;
}

export class ExampleGenerationProcessor {
  constructor(
    private grpcClient: RealContentGenerationClient,
    private prisma: PrismaClient,
    private logger: Logger,
    private telemetry?: ContentWorkerTelemetryPublisher,
  ) {}

  async process(job: Job<ExampleGenerationJobData>): Promise<void> {
    const {
      experienceId,
      tenantId,
      claimRef,
      claimText,
      gradeLevel,
      domain,
      types,
      count,
    } = job.data;

    this.logger.info(
      { jobId: job.id, experienceId, claimRef },
      "Processing example generation job",
    );

    try {
      await this.telemetry?.publishForJob(job, {
        stage: "grpc_request_started",
        message: "Submitting example generation request",
        progressPercent: 20,
        status: "running",
      });

      // Call Java agent to generate examples
      const requestId = crypto.randomUUID();
      const response = await this.grpcClient.generateExamples({
        requestId,
        tenantId,
        claimText,
        claimRef,
        gradeLevel,
        domain,
        types,
        count,
      });

      this.logger.info(
        { jobId: job.id, examplesCount: response.examples?.length || 0 },
        "Examples generated successfully",
      );

      const responseCost =
        ContentWorkerTelemetryPublisher.extractCostFromMetadata(
          response.metadata,
        );
      await this.telemetry?.publishForJob(job, {
        stage: "grpc_response_received",
        message: "Example generation response received",
        progressPercent: 55,
        status: "running",
        ...(responseCost ? { cost: responseCost } : {}),
        diagnostics: {
          examplesCount: response.examples?.length || 0,
        },
      });

      await this.prisma.claimExample.deleteMany({
        where: { experienceId, claimRef },
      });

      // Store examples in database
      let orderIndex = 0;
      for (const example of response.examples || []) {
        const manifestId = String(
          example.example_id ?? example.exampleId ?? `${claimRef}-${orderIndex + 1}`,
        );
        const content = Prisma.JsonNull;
        const payload = {
          problemStatement:
            example.problem_statement ??
            example.title ??
            claimText,
          solution:
            example.solution_content ??
            example.content ??
            null,
          keyPoints: example.key_learning_points ?? example.tags ?? [],
          realWorldConnection: example.real_world_connection ?? null,
        } satisfies Record<string, unknown>;

        await this.prisma.claimExample.create({
          data: {
            experienceId,
            claimRef,
            manifestId,
            manifestVersion: "1.0.0",
            type: String(example.type),
            title: String(example.title ?? `Example ${orderIndex + 1}`),
            description: String(example.description ?? ""),
            content: payload as Prisma.InputJsonValue,
            difficulty: "INTERMEDIATE", // Default
            orderIndex: Number(example.order_index ?? orderIndex),
          },
        });

        this.logger.info(
          { jobId: job.id, claimRef, exampleId: example.example_id },
          "Example stored in database",
        );

        orderIndex += 1;
      }

      this.logger.info(
        {
          jobId: job.id,
          experienceId,
          claimRef,
          examplesCount: response.examples?.length || 0,
        },
        "Example generation job completed",
      );

      await this.telemetry?.publishForJob(job, {
        stage: "persistence_completed",
        message: "Example artifacts persisted",
        progressPercent: 90,
        status: "running",
        diagnostics: {
          examplesStored: response.examples?.length || 0,
          experienceId,
          claimRef,
        },
      });
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      this.logger.error(
        { jobId: job.id, experienceId, claimRef, error: errorMessage },
        "Example generation job failed",
      );
      throw error;
    }
  }
}
