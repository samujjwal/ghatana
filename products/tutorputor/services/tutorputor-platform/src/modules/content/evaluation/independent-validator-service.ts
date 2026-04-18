/**
 * Independent Generated Content Validator
 *
 * Validates generated educational artifacts through a separate validation
 * boundary, persists versioned validation records, and escalates risky output
 * to the human review queue.
 *
 * @doc.type class
 * @doc.purpose Independently validate generated educational content before publish
 * @doc.layer product
 * @doc.pattern Service
 */

import { createHash } from "node:crypto";
import type { PrismaClient } from "@tutorputor/core/db";
import type {
  KnowledgeBaseService,
  ValidationCheck,
} from "../../knowledge-base/service";

export const INDEPENDENT_VALIDATOR_VERSION =
  "independent-content-validator@2026-04-18";

export type GeneratedContentType =
  | "claim"
  | "example"
  | "explanation"
  | "task"
  | "simulation"
  | "animation";

export interface GeneratedContentValidationRequest {
  tenantId: string;
  experienceId: string;
  actorId: string;
  content: string;
  contentType: GeneratedContentType;
  domain: string;
  gradeRange: string;
  assetId?: string;
  metadata?: Record<string, unknown>;
}

export interface GeneratedContentValidationResult {
  validationRecordId: string;
  validatorVersion: string;
  inputHash: string;
  overallStatus: "PASS" | "WARN" | "FAIL";
  score: number;
  riskLevel: "low" | "medium" | "high";
  requiresHumanReview: boolean;
  reviewQueueId?: string;
  checks: ValidationCheck[];
  recommendations: string[];
}

type ValidationCheckType = ValidationCheck["type"];

function mapContentType(
  contentType: GeneratedContentType,
): "claim" | "example" | "explanation" | "task" {
  if (contentType === "simulation" || contentType === "animation") {
    return "explanation";
  }

  return contentType;
}

function getCheckScore(
  checks: ValidationCheck[],
  type: ValidationCheckType,
  fallback: number,
): number {
  const check = checks.find((candidate) => candidate.type === type);
  return check?.score ?? fallback;
}

function computeOverallStatus(
  score: number,
  riskLevel: "low" | "medium" | "high",
): "PASS" | "WARN" | "FAIL" {
  if (riskLevel === "high" || score < 70) {
    return "FAIL";
  }
  if (riskLevel === "medium" || score < 85) {
    return "WARN";
  }
  return "PASS";
}

function mapRiskLevelToPriority(riskLevel: "low" | "medium" | "high"): number {
  if (riskLevel === "high") {
    return 90;
  }
  if (riskLevel === "medium") {
    return 60;
  }
  return 30;
}

function mapRiskLevel(
  riskLevel: "low" | "medium" | "high",
): "LOW" | "MEDIUM" | "HIGH" {
  if (riskLevel === "high") {
    return "HIGH";
  }
  if (riskLevel === "medium") {
    return "MEDIUM";
  }
  return "LOW";
}

function buildInputHash(request: GeneratedContentValidationRequest): string {
  return createHash("sha256")
    .update(
      JSON.stringify({
        content: request.content,
        contentType: request.contentType,
        domain: request.domain,
        gradeRange: request.gradeRange,
      }),
    )
    .digest("hex");
}

export class IndependentGeneratedContentValidator {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly knowledgeBaseService: Pick<
      KnowledgeBaseService,
      "validateContent"
    >,
    private readonly validatorsVersion = INDEPENDENT_VALIDATOR_VERSION,
  ) {}

  async validateGeneratedContent(
    request: GeneratedContentValidationRequest,
  ): Promise<GeneratedContentValidationResult> {
    const validation = await this.knowledgeBaseService.validateContent({
      content: request.content,
      contentType: mapContentType(request.contentType),
      domain: request.domain,
      gradeRange: request.gradeRange,
    });

    const inputHash = buildInputHash(request);
    const authorityScore = getCheckScore(
      validation.checks,
      "factual_accuracy",
      validation.score,
    );
    const usefulnessScore = Math.round(
      (getCheckScore(validation.checks, "completeness", validation.score) +
        getCheckScore(
          validation.checks,
          "pedagogical_soundness",
          validation.score,
        )) /
        2,
    );
    const accessibilityScore = getCheckScore(
      validation.checks,
      "clarity",
      validation.score,
    );
    const gradefitScore = getCheckScore(
      validation.checks,
      "age_appropriateness",
      validation.score,
    );
    const harmlessnessScore = Math.round(
      (accessibilityScore + gradefitScore) / 2,
    );
    const overallStatus = computeOverallStatus(
      validation.score,
      validation.riskLevel,
    );

    const validationRecord = await this.prisma.validationRecordExtended.create({
      data: {
        experienceId: request.experienceId,
        inputHash,
        validatorsVersion: this.validatorsVersion,
        authorityScore,
        accuracyScore: authorityScore,
        usefulnessScore,
        harmlessnessScore,
        accessibilityScore,
        gradefitScore,
        overallStatus,
        checks: validation.checks,
        issues: validation.checks
          .filter((check) => !check.passed)
          .map((check) => ({
            type: check.type,
            message: check.message,
            score: check.score,
            severity:
              check.type === "factual_accuracy" ? "error" : "warning",
          })),
        suggestions: validation.recommendations,
        simulationHealthReport:
          request.contentType === "simulation"
            ? {
                riskLevel: validation.riskLevel,
                validatorVersion: this.validatorsVersion,
                contentType: request.contentType,
              }
            : undefined,
      },
    });

    let reviewQueueId: string | undefined;
    const requiresHumanReview = overallStatus !== "PASS";

    if (requiresHumanReview) {
      const reviewQueue = await this.prisma.reviewQueue.create({
        data: {
          tenantId: request.tenantId,
          experienceId: request.experienceId,
          priority: mapRiskLevelToPriority(validation.riskLevel),
          riskLevel: mapRiskLevel(validation.riskLevel),
          triggerReason:
            validation.riskLevel === "high" ? "high_risk" : "low_confidence",
          metadata: {
            assetId: request.assetId,
            contentType: request.contentType,
            validatorVersion: this.validatorsVersion,
            validationRecordId: validationRecord.id,
            score: validation.score,
            recommendations: validation.recommendations,
            ...(request.metadata ? { requestMetadata: request.metadata } : {}),
          },
        },
      });

      reviewQueueId = reviewQueue.id;

      await this.prisma.experienceEvent.create({
        data: {
          experienceId: request.experienceId,
          eventType: "REVIEW_SUBMITTED",
          actorId: request.actorId,
          metadata: {
            reviewQueueId,
            validatorVersion: this.validatorsVersion,
            overallStatus,
            riskLevel: validation.riskLevel,
          },
        } as never,
      });
    } else {
      await this.prisma.experienceEvent.create({
        data: {
          experienceId: request.experienceId,
          eventType: "VALIDATED",
          actorId: request.actorId,
          metadata: {
            validatorVersion: this.validatorsVersion,
            overallStatus,
            score: validation.score,
          },
        } as never,
      });
    }

    if (request.assetId) {
      await this.prisma.contentAsset.update({
        where: { id: request.assetId },
        data: {
          confidenceScore: validation.score / 100,
          riskLevel: mapRiskLevel(validation.riskLevel),
          reviewState: JSON.stringify({
            source: "independent-validator",
            validatorVersion: this.validatorsVersion,
            overallStatus,
            requiresHumanReview,
            validationRecordId: validationRecord.id,
            reviewQueueId,
          }),
        },
      });
    }

    return {
      validationRecordId: validationRecord.id,
      validatorVersion: this.validatorsVersion,
      inputHash,
      overallStatus,
      score: validation.score,
      riskLevel: validation.riskLevel,
      requiresHumanReview,
      ...(reviewQueueId ? { reviewQueueId } : {}),
      checks: validation.checks,
      recommendations: validation.recommendations,
    };
  }
}