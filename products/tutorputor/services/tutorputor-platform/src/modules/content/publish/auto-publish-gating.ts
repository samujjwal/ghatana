/**
 * Auto-Publish Gating Service
 *
 * Task 4.8: Implement Safe Auto-Publish Gating
 *
 * @doc.type module
 * @doc.purpose Safe auto-publish with guardrails
 * @doc.layer service
 * @doc.pattern GatingService
 */

import type { Logger } from "pino";
import type { PrismaClient } from "@tutorputor/core/db";
import {
  FActScoreEvaluator,
  type FActScoreResult,
} from "../evaluation/factscore-evaluator";
import {
  IndependentGeneratedContentValidator,
  type GeneratedContentValidationResult,
} from "../evaluation/independent-validator-service";
import type { EvidenceBundle } from "../../knowledge-base/evidence-bundle";
import { KnowledgeBaseServiceImpl } from "../../knowledge-base/service";

export interface AutoPublishDecision {
  canAutoPublish: boolean;
  reasons: string[];
  confidence: number;
  requiresHumanReview: boolean;
  reviewReasons?: string[];
  validatorVersion?: string;
  auditLogId?: string;
  reviewQueueId?: string;
}

export interface AutoPublishEvaluationRequest {
  tenantId: string;
  actorId: string;
  experienceId: string;
  artifactId: string;
  artifactType: "example" | "animation" | "simulation";
  content: string;
  evidenceBundle: EvidenceBundle;
  domain: string;
  gradeRange: string;
}

export interface PublishThresholds {
  factScoreMin: number;
  evidenceCoverageMin: number;
  bundleConfidenceMin: number;
  noContradictions: boolean;
  notNovelDomain: boolean;
  notPolicySensitive: boolean;
}

export class AutoPublishGatingService {
  private readonly defaultThresholds: PublishThresholds = {
    factScoreMin: 0.95,
    evidenceCoverageMin: 0.9,
    bundleConfidenceMin: 0.85,
    noContradictions: true,
    notNovelDomain: true,
    notPolicySensitive: true,
  };

  private readonly factScoreEvaluator: FActScoreEvaluator;
  private readonly validatorService: IndependentGeneratedContentValidator;
  private readonly thresholdOverrides?: Partial<PublishThresholds>;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
    options?: {
      thresholds?: Partial<PublishThresholds>;
      validatorService?: IndependentGeneratedContentValidator;
      factScoreEvaluator?: Pick<FActScoreEvaluator, "evaluate">;
    },
  ) {
    this.thresholdOverrides = options?.thresholds;
    this.factScoreEvaluator =
      options?.factScoreEvaluator ?? new FActScoreEvaluator(logger);
    this.validatorService =
      options?.validatorService ??
      new IndependentGeneratedContentValidator(
        prisma,
        new KnowledgeBaseServiceImpl(prisma),
      );
  }

  async evaluateForAutoPublish(
    request: AutoPublishEvaluationRequest,
  ): Promise<AutoPublishDecision> {
    const thresholds = {
      ...this.defaultThresholds,
      ...this.thresholdOverrides,
    };
    const reasons: string[] = [];
    const reviewReasons: string[] = [];

    const validationResult = await this.validatorService.validateGeneratedContent({
      tenantId: request.tenantId,
      experienceId: request.experienceId,
      actorId: request.actorId,
      assetId: request.artifactId,
      content: request.content,
      contentType: request.artifactType,
      domain: request.domain,
      gradeRange: request.gradeRange,
      metadata: {
        source: "auto_publish_gating",
      },
    });

    if (validationResult.overallStatus !== "PASS") {
      reasons.push(
        `Independent validator returned ${validationResult.overallStatus} (${validationResult.score}/100)`,
      );
      reviewReasons.push("Independent validation requires reviewer confirmation");
    }

    const factScoreResult = await this.factScoreEvaluator.evaluate(
      request.content,
      request.evidenceBundle,
    );
    if (factScoreResult.precision < thresholds.factScoreMin) {
      reasons.push(
        `FActScore ${factScoreResult.precision} below threshold ${thresholds.factScoreMin}`,
      );
      reviewReasons.push("Fact verification failed");
    }

    if (request.evidenceBundle.coverageScore < thresholds.evidenceCoverageMin) {
      reasons.push(
        `Evidence coverage ${request.evidenceBundle.coverageScore} below threshold ${thresholds.evidenceCoverageMin}`,
      );
      reviewReasons.push("Insufficient evidence coverage");
    }

    if (request.evidenceBundle.bundleConfidence < thresholds.bundleConfidenceMin) {
      reasons.push(
        `Bundle confidence ${request.evidenceBundle.bundleConfidence} below threshold ${thresholds.bundleConfidenceMin}`,
      );
      reviewReasons.push("Low evidence confidence");
    }

    if (
      thresholds.noContradictions &&
      request.evidenceBundle.contradictionDetected
    ) {
      reasons.push("Contradictions detected in evidence bundle");
      reviewReasons.push("Evidence contradictions require resolution");
    }

    if (
      thresholds.notNovelDomain &&
      (await this.isNovelDomainPattern(request.domain, request.artifactType))
    ) {
      reasons.push("Novel domain pattern detected");
      reviewReasons.push("Novel content requires expert review");
    }

    if (
      thresholds.notPolicySensitive &&
      this.isPolicySensitive(request.domain)
    ) {
      reasons.push("Policy-sensitive domain detected");
      reviewReasons.push("Policy-sensitive content requires review");
    }

    const confidence = Math.min(
      this.calculateConfidence(
        factScoreResult,
        request.evidenceBundle,
        reasons.length,
      ),
      validationResult.score / 100,
    );

    const canAutoPublish =
      validationResult.overallStatus === "PASS" &&
      reasons.length === 0 &&
      confidence >= 0.95;

    let reviewQueueId = validationResult.reviewQueueId;
    if (!reviewQueueId && reviewReasons.length > 0) {
      const reviewQueue = await this.prisma.reviewQueue.create({
        data: {
          tenantId: request.tenantId,
          experienceId: request.experienceId,
          priority: this.isPolicySensitive(request.domain) ? 90 : 60,
          riskLevel: confidence < 0.5 ? "HIGH" : "MEDIUM",
          triggerReason: this.isPolicySensitive(request.domain)
            ? "policy_violation"
            : confidence < 0.5
              ? "high_risk"
              : "low_confidence",
          metadata: {
            artifactId: request.artifactId,
            artifactType: request.artifactType,
            reasons,
            reviewReasons,
            confidence,
            validatorVersion: validationResult.validatorVersion,
          },
        },
      });
      reviewQueueId = reviewQueue.id;
    }

    this.logger.info(
      {
        artifactId: request.artifactId,
        canAutoPublish,
        reasonCount: reasons.length,
        confidence,
      },
      "Auto-publish evaluation complete",
    );

    const auditLogId = await this.logDecision(
      request,
      validationResult,
      canAutoPublish,
      reasons,
      confidence,
      thresholds,
    );

    return {
      canAutoPublish,
      reasons,
      confidence,
      requiresHumanReview: reviewReasons.length > 0,
      reviewReasons: reviewReasons.length > 0 ? reviewReasons : undefined,
      validatorVersion: validationResult.validatorVersion,
      auditLogId,
      ...(reviewQueueId ? { reviewQueueId } : {}),
    };
  }

  private async isNovelDomainPattern(
    domain: string,
    _artifactType: string,
  ): Promise<boolean> {
    const count = await this.prisma.claimExample.count({
      where: {
        claim: {
          experience: {
            domain: domain.toUpperCase() as never,
          },
        },
        validationStatus: "valid",
      },
    });

    return count < 5;
  }

  private isPolicySensitive(domain: string): boolean {
    const sensitiveDomains = ["health", "safety", "legal", "medical"];
    return sensitiveDomains.some((candidate) =>
      domain.toLowerCase().includes(candidate),
    );
  }

  private calculateConfidence(
    factScore: FActScoreResult,
    evidenceBundle: EvidenceBundle,
    violationCount: number,
  ): number {
    let confidence = 1.0;

    confidence -= (1 - factScore.precision) * 0.3;
    confidence -= (1 - evidenceBundle.coverageScore) * 0.2;
    confidence -= (1 - evidenceBundle.bundleConfidence) * 0.2;
    confidence -= violationCount * 0.1;

    return Math.max(0, Math.min(1, confidence));
  }

  private async logDecision(
    request: AutoPublishEvaluationRequest,
    validationResult: GeneratedContentValidationResult,
    canAutoPublish: boolean,
    reasons: string[],
    confidence: number,
    thresholds: PublishThresholds,
  ): Promise<string> {
    const auditLog = await this.prisma.auditLog.create({
      data: {
        tenantId: request.tenantId,
        actorId: request.actorId,
        action: "AUTO_PUBLISH_EVALUATED",
        resourceType: "content_asset",
        resourceId: request.artifactId,
        outcome: canAutoPublish ? "success" : "manual_review",
        metadata: JSON.stringify({
          artifactType: request.artifactType,
          experienceId: request.experienceId,
          domain: request.domain,
          canAutoPublish,
          reasons,
          confidence,
          thresholds,
          validatorVersion: validationResult.validatorVersion,
          validationStatus: validationResult.overallStatus,
          validationScore: validationResult.score,
        }),
      },
    });

    this.logger.info(
      {
        artifactId: request.artifactId,
        auditLogId: auditLog.id,
        canAutoPublish,
      },
      "Auto-publish decision logged",
    );

    return auditLog.id;
  }

  getThresholds(): PublishThresholds {
    return { ...this.defaultThresholds, ...this.thresholdOverrides };
  }

  updateThresholds(newThresholds: Partial<PublishThresholds>): void {
    Object.assign(this.thresholdOverrides || {}, newThresholds);
    this.logger.info({ newThresholds }, "Publish thresholds updated");
  }
}
