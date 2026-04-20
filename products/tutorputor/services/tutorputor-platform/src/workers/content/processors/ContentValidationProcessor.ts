/**
 * Content Validation Processor - Validates content using 4C framework.
 *
 * @doc.type class
 * @doc.purpose Process content validation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from "bullmq";
import { PrismaClient } from "@tutorputor/core/db";
import { Logger } from "pino";
import { RealContentGenerationClient } from "../grpc/RealContentGenerationClient";
import * as crypto from "crypto";
import {
  type CorrelatedGenerationJobData,
  ContentWorkerTelemetryPublisher,
} from "../generation-telemetry";

export interface ContentValidationJobData extends CorrelatedGenerationJobData {
  experienceId: string;
  tenantId: string;
  checkCorrectness: boolean;
  checkCompleteness: boolean;
  checkConcreteness: boolean;
  checkConciseness: boolean;
  minConfidenceThreshold: number;
}

export class ContentValidationProcessor {
  constructor(
    private grpcClient: RealContentGenerationClient,
    private prisma: PrismaClient,
    private logger: Logger,
    private telemetry?: ContentWorkerTelemetryPublisher,
  ) {}

  async process(job: Job<ContentValidationJobData>): Promise<void> {
    const {
      experienceId,
      checkCorrectness,
      checkCompleteness,
      checkConcreteness,
      checkConciseness,
      minConfidenceThreshold,
    } = job.data;

    this.logger.info(
      { jobId: job.id, experienceId },
      "Processing content validation job",
    );

    try {
      await this.telemetry?.publishForJob(job, {
        stage: "validation_loading",
        message: "Loading experience content for validation",
        progressPercent: 10,
        status: "running",
      });

      // Fetch experience with all content
      const experience = await this.prisma.learningExperience.findUnique({
        where: { id: experienceId },
        include: {
          claims: {
            include: {
              examples: true,
              simulations: {
                include: {
                  simulationManifest: true,
                },
              },
            },
          },
          experienceTasks: true,
        },
      });

      if (!experience) {
        throw new Error(`Experience not found: ${experienceId}`);
      }

      const requestId = crypto.randomUUID();

      const experienceDetails = experience as {
        title?: string;
        description?: string;
        domain?: string;
        claims: Array<{ text?: string }>;
      };

      const response = await this.grpcClient.validateContent({
        requestId,
        tenantId: job.data.tenantId,
        experienceId,
        title: String(experienceDetails.title ?? `Experience ${experienceId}`),
        description: String(experienceDetails.description ?? ""),
        claimTexts: experienceDetails.claims.map(
          (claim) => String(claim.text ?? ""),
        ),
        domain: String(experienceDetails.domain ?? "TECH"),
      });

      const normalizedIssues = Array.isArray(response.issues)
        ? response.issues
        : [];
      const fallbackScore = Number(
        (response as { overall_score?: number }).overall_score ?? 0,
      );
      const overallScore =
        Number.isFinite(response.overallScore) && response.overallScore > 0
          ? response.overallScore
          : fallbackScore;
      const responseStatus = String(
        (response as { status?: string }).status ?? "",
      ).toUpperCase();
      const passed =
        response.canPublish ||
        responseStatus === "VALID" ||
        responseStatus === "PASS";
      const issueMessages = normalizedIssues.map((issue) => issue.message);
      const suggestions = normalizedIssues
        .map((issue) => issue.suggestion)
        .filter((suggestion): suggestion is string => suggestion.length > 0);
      const issueCount =
        response.issueCount > 0 ? response.issueCount : normalizedIssues.length;

      this.logger.info(
        {
          jobId: job.id,
          passed,
          score: overallScore,
        },
        "Content validated successfully",
      );

      const responseCost =
        ContentWorkerTelemetryPublisher.extractCostFromMetadata(
          response.metadata,
        );
      await this.telemetry?.publishForJob(job, {
        stage: "validation_completed",
        message: "Validation response received",
        progressPercent: 70,
        status: "running",
        ...(responseCost ? { cost: responseCost } : {}),
        diagnostics: {
          passed,
          overallScore,
        },
      });

      // Store validation results
      const overallStatus = passed ? "PASS" : "FAIL";
      const rawScore = Number(overallScore || 0);
      const score =
        rawScore <= 1 ? Math.round(rawScore * 100) : Math.round(rawScore);

      await this.prisma.validationRecord.create({
        data: {
          experienceId,
          overallStatus: overallStatus,

          authorityScore: score,
          accuracyScore: score,
          usefulnessScore: score,
          harmlessnessScore: score,
          accessibilityScore: score,
          gradefitScore: score,

          issues: issueMessages,
          suggestions,
          validatedAt: new Date(),
        },
      });

      if (!passed) {
        this.logger.warn(
          {
            jobId: job.id,
            experienceId,
            issuesCount: issueCount,
          },
          "Experience validation failed - needs improvement",
        );
      }

      this.logger.info(
        { jobId: job.id, experienceId },
        "Content validation job completed",
      );

      await this.telemetry?.publishForJob(job, {
        stage: "validation_persisted",
        message: "Validation record persisted",
        progressPercent: 90,
        status: "running",
        diagnostics: {
          experienceId,
          passed,
          issueCount,
        },
      });
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : String(error);
      this.logger.error(
        { jobId: job.id, experienceId, error: errorMessage },
        "Content validation job failed",
      );
      throw error;
    }
  }
}
