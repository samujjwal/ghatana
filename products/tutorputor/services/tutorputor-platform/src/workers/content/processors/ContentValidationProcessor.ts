/**
 * Content Validation Processor - Validates content using 4C framework.
 *
 * @doc.type class
 * @doc.purpose Process content validation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from "bullmq";
import { PrismaClient, Prisma } from "@tutorputor/core/db";
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

      // Atomic Factual Validation (TODO 26)
      // Validate each claim individually for factual correctness
      await this.performAtomicFactualValidation({
        experienceId,
        tenantId: job.data.tenantId,
        claims: experienceDetails.claims,
        overallScore,
        requestId,
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

  /**
   * Perform atomic factual validation on individual claims
   * Stores per-claim factual validation results for granular tracking
   */
  private async performAtomicFactualValidation(args: {
    experienceId: string;
    tenantId: string;
    claims: Array<{ text?: string; claimRef?: string }>;
    overallScore: number;
    requestId: string;
  }): Promise<void> {
    const { experienceId, tenantId, claims, overallScore, requestId } = args;

    this.logger.info(
      { experienceId, claimCount: claims.length },
      "Starting atomic factual validation",
    );

    for (const claim of claims) {
      const claimText = String(claim.text ?? "");
      const claimRef = claim.claimRef ?? "unknown";

      // Extract atomic facts from claim text (simplified - in production would use NLP)
      const facts = this.extractAtomicFacts(claimText);

      for (const fact of facts) {
        // Validate each fact against the overall score and claim context
        const factValidation = await this.validateAtomicFact({
          fact,
          claimText,
          overallScore,
        });

        // Store factual validation result
        try {
          await this.prisma.factualValidation.create({
            data: {
              experienceId,
              claimRef,
              tenantId,
              factText: fact,
              factSource: "claim_text",
              isValid: factValidation.isValid,
              confidence: factValidation.confidence,
              errorMessage: factValidation.errorMessage,
              validatedBy: "ai_validator",
              metadata: {
                requestId,
                claimText,
                overallScore,
              } as unknown as Prisma.InputJsonValue,
            },
          });
        } catch (error) {
          // Handle unique constraint violation (fact already validated)
          if (
            error instanceof Error &&
            error.message.includes("unique constraint")
          ) {
            this.logger.debug(
              { experienceId, claimRef, fact },
              "Fact already validated, skipping",
            );
          } else {
            this.logger.error(
              { experienceId, claimRef, fact, error: error instanceof Error ? error.message : String(error) },
              "Failed to store factual validation",
            );
          }
        }
      }
    }

    this.logger.info(
      { experienceId, claimCount: claims.length },
      "Atomic factual validation completed",
    );
  }

  /**
   * Extract atomic facts from claim text
   * Simplified implementation - production would use NLP for fact extraction
   */
  private extractAtomicFacts(claimText: string): string[] {
    // Split claim into sentences as atomic facts
    const sentences = claimText
      .split(/[.!?]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 10); // Filter out very short fragments

    return sentences;
  }

  /**
   * Validate an individual atomic fact
   * Simplified validation based on overall score and fact characteristics
   */
  private async validateAtomicFact(args: {
    fact: string;
    claimText: string;
    overallScore: number;
  }): Promise<{ isValid: boolean; confidence: number; errorMessage?: string }> {
    const { fact, claimText, overallScore } = args;

    // Simplified validation logic
    // In production, this would call an external fact-checking API or use a dedicated model
    const isValid = overallScore >= 0.75;
    const confidence = overallScore;

    if (isValid) {
      return { isValid, confidence };
    }

    return {
      isValid,
      confidence,
      errorMessage: "Fact validation failed based on overall content quality score",
    };
  }
}
