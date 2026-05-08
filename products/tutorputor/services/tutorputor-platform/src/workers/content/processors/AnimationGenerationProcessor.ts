/**
 * Animation Generation Processor - Generates animation specs for claims.
 *
 * @doc.type class
 * @doc.purpose Process animation generation jobs with manifest validation
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
import {
  AnimationManifest,
} from "@tutorputor/contracts/v1/artifact-manifests";

function toInputJsonValue(value: unknown): Prisma.InputJsonValue {
  return value as Prisma.InputJsonValue;
}

export interface AnimationGenerationJobData extends CorrelatedGenerationJobData {
  experienceId: string;
  tenantId: string;
  claimRef: string;
  claimText: string;
  animationType: string;
  durationSeconds: number;
  complexity?: string;
  domain?: string;
  gradeLevel?: string;
}

export class AnimationGenerationProcessor {
  constructor(
    private grpcClient: RealContentGenerationClient,
    private prisma: PrismaClient,
    private logger: Logger,
    private telemetry?: ContentWorkerTelemetryPublisher,
  ) {}

  async process(job: Job<AnimationGenerationJobData>): Promise<void> {
    const {
      experienceId,
      tenantId,
      claimRef,
      claimText,
      animationType,
      durationSeconds,
      complexity,
      domain,
      gradeLevel,
    } = job.data;

    this.logger.info(
      { jobId: job.id, experienceId, claimRef },
      "Processing animation generation job",
    );

    try {
      await this.telemetry?.publishForJob(job, {
        stage: "grpc_request_started",
        message: "Submitting animation generation request",
        progressPercent: 20,
        status: "running",
      });

      const requestId = crypto.randomUUID();
      const response = await this.grpcClient.generateAnimation({
        requestId,
        tenantId,
        claimText,
        claimRef,
        animationType,
        durationSeconds,
        domain: domain ?? "TECH",
        gradeLevel: gradeLevel ?? "GRADE_6_8",
        context: {},
      });

      const animation =
        (response?.animation as Record<string, unknown> | undefined) ??
        (response?.manifest as Record<string, unknown> | undefined) ??
        null;
      if (!animation) {
        throw new Error("No animation specification returned");
      }

      const responseCost =
        ContentWorkerTelemetryPublisher.extractCostFromMetadata(
          response?.metadata,
        );
      await this.telemetry?.publishForJob(job, {
        stage: "grpc_response_received",
        message: "Animation generation response received",
        progressPercent: 55,
        status: "running",
        ...(responseCost ? { cost: responseCost } : {}),
        diagnostics: {
          animationId: animation.animation_id ?? animation.animationId ?? null,
          keyframeCount: Array.isArray(animation.keyframes)
            ? animation.keyframes.length
            : 0,
        },
      });

      const persistedDuration = this.resolveDuration(
        animation,
        durationSeconds,
      );
      const manifestId = String(
        animation.manifestId ??
          animation.manifest_id ??
          animation.animation_id ??
          animation.animationId ??
          `manifest-${experienceId}-${claimRef}`,
      );
      const manifestVersion = String(animation.version ?? "1.0.0");
      const persistedType = this.mapAnimationType(
        String(animation.type ?? animationType),
      );
      const persistedTitle = String(
        animation.title || `Animation for ${claimRef}`,
      );
      const persistedDescription = String(animation.description || "");
      const persistedConfig = toInputJsonValue({
        animationId: animation.animation_id ?? animation.animationId ?? null,
        complexity: complexity ?? "medium",
        keyframes: Array.isArray(animation.keyframes)
          ? animation.keyframes
          : [],
        metadata: response?.validation ?? null,
        raw: animation,
      });

      await this.prisma.claimAnimation.upsert({
        where: {
          experienceId_claimRef_variantKey: {
            experienceId,
            claimRef,
            variantKey: "primary",
          },
        },
        create: {
          experienceId,
          claimRef,
          manifestId,
          manifestVersion,
          variantKey: "primary",
          isPrimary: true,
          title: persistedTitle,
          description: persistedDescription,
          type: persistedType,
          duration: persistedDuration,
          config: persistedConfig,
        },
        update: {
          manifestId,
          manifestVersion,
          isPrimary: true,
          title: persistedTitle,
          description: persistedDescription,
          type: persistedType,
          duration: persistedDuration,
          config: persistedConfig,
        },
      });

      this.logger.info(
        { jobId: job.id, experienceId, claimRef, type: persistedType },
        "Animation generation job completed",
      );

      await this.telemetry?.publishForJob(job, {
        stage: "persistence_completed",
        message: "Animation artifact persisted",
        progressPercent: 90,
        status: "running",
        diagnostics: {
          experienceId,
          claimRef,
          type: persistedType,
        },
      });
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      this.logger.error(
        { jobId: job.id, experienceId, claimRef, error: errorMessage },
        "Animation generation job failed",
      );
      throw error;
    }
  }

  private resolveDuration(
    animation: Record<string, unknown>,
    fallback: number,
  ): number {
    const raw =
      animation.duration_seconds ?? animation.durationSeconds ?? fallback;
    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed > 0
      ? Math.round(parsed)
      : Math.max(1, fallback);
  }

  private mapAnimationType(rawType: string): string {
    const normalized = String(rawType || "")
      .trim()
      .toUpperCase();
    switch (normalized) {
      case "3D":
      case "THREE_D":
        return "3d";
      case "TIMELINE":
        return "timeline";
      case "2D":
      case "TWO_D":
      default:
        return "2d";
    }
  }
}
