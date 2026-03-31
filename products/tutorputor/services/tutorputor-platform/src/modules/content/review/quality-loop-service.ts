/**
 * Generation Quality Loop Service
 *
 * Closes the gap between generation, evaluation, review, regeneration, and
 * publishing by turning evaluation outcomes into explicit next actions.
 *
 * @doc.type class
 * @doc.purpose Orchestrate post-generation evaluation, review gating, and regeneration signals
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type {
  EvaluationRecord,
  GenerationQualityLoopSummary,
  RegenerationTrigger,
  ReviewPath,
} from "@tutorputor/contracts/v1/content-studio";
import { RegenerationCandidateService } from "../candidates/candidate-service.js";
import { EvaluationService } from "../evaluation/evaluation-service.js";
import { PublishService } from "../publish/publish-service.js";
import { GenerationReviewService } from "./review-service.js";

type PersistedRequest = {
  id: string;
  reviewPath: string;
};

export class GenerationQualityLoopService {
  private readonly evaluationService: EvaluationService;
  private readonly candidateService: RegenerationCandidateService;
  private readonly publishService: PublishService;
  private readonly reviewService: GenerationReviewService;

  constructor(private readonly prisma: PrismaClient) {
    this.evaluationService = new EvaluationService(prisma);
    this.candidateService = new RegenerationCandidateService(prisma);
    this.publishService = new PublishService(prisma);
    this.reviewService = new GenerationReviewService(prisma);
  }

  async processRequestOutcome(
    tenantId: string,
    generationRequestId: string,
    options?: {
      autoPublish?: boolean;
      actorId?: string;
    },
  ): Promise<GenerationQualityLoopSummary> {
    const request = await (this.prisma as any).generationRequest.findFirst({
      where: { id: generationRequestId, tenantId },
      select: { id: true, reviewPath: true },
    });

    if (!request) {
      throw new Error(`Generation request ${generationRequestId} not found`);
    }

    const reviewPath = mapReviewPath(request);
    const evaluation = await this.evaluationService.evaluateGenerationRequest(
      tenantId,
      generationRequestId,
    );
    const records = await this.evaluationService.getEvaluationsByRequest(
      tenantId,
      generationRequestId,
    );

    await this.ensureRegenerationCandidates(tenantId, records);

    const eligibleAssetIds = unique(
      records
        .filter(
          (record: any) =>
            record.assetId != null && record.recommendation !== "block",
        )
        .map((record: any) => record.assetId as string),
    );
    const blockedAssetIds = unique(
      records
        .filter(
          (record: any) =>
            record.assetId != null && record.recommendation === "block",
        )
        .map((record: any) => record.assetId as string),
    );

    let latestDecision = await this.reviewService.getLatestDecision(
      tenantId,
      generationRequestId,
    );

    const shouldAutoPublish =
      (options?.autoPublish ?? true) &&
      reviewPath === "auto_publish" &&
      evaluation.recommendation === "auto_publish" &&
      eligibleAssetIds.length > 0;

    let publishResult:
      | GenerationQualityLoopSummary["publishResult"]
      | undefined;
    if (shouldAutoPublish) {
      publishResult = await this.publishService.publishByGenerationRequest(
        tenantId,
        options?.actorId ?? "system:quality-loop",
        generationRequestId,
      );
    } else if (
      evaluation.recommendation === "manual_review" ||
      reviewPath !== "auto_publish"
    ) {
      latestDecision = await this.reviewService.ensurePendingDecision(
        tenantId,
        generationRequestId,
        buildPendingReviewNote(reviewPath, evaluation.recommendation),
      );
    }

    const openCandidates = await this.candidateService.listOpenCandidates(
      tenantId,
    );

    return {
      requestId: generationRequestId,
      reviewPath,
      evaluation,
      ...(latestDecision ? { latestDecision } : {}),
      openCandidates: openCandidates.filter(
        (candidate: any) =>
          candidate.generationRequestId === generationRequestId ||
          blockedAssetIds.includes(candidate.assetId) ||
          eligibleAssetIds.includes(candidate.assetId),
      ),
      nextAction: deriveNextAction({
        evaluationRecommendation: evaluation.recommendation,
        reviewPath,
        autoPublished: Boolean(publishResult && publishResult.published > 0),
        eligibleAssetIds,
        blockedAssetIds,
      }),
      autoPublished: Boolean(publishResult && publishResult.published > 0),
      ...(publishResult ? { publishResult } : {}),
      publishedAssetIds: publishResult
        ? publishResult.results
            .filter((result: any) => result.published)
            .map((result: any) => result.assetId)
        : [],
      eligibleAssetIds,
      blockedAssetIds,
    };
  }

  private async ensureRegenerationCandidates(
    tenantId: string,
    records: EvaluationRecord[],
  ): Promise<void> {
    for (const record of records) {
      if (!record.assetId) {
        continue;
      }

      const candidateConfig = buildCandidateConfig(record);
      if (!candidateConfig) {
        continue;
      }

      const existing = await (this.prisma as any).regenerationCandidate.findFirst(
        {
          where: {
            tenantId,
            assetId: record.assetId,
            trigger: candidateConfig.trigger.toUpperCase(),
            status: { in: ["OPEN", "QUEUED", "IN_PROGRESS"] },
          },
        },
      );

      if (existing) {
        continue;
      }

      const assetType = inferAssetType(record);
      await this.candidateService.createCandidate(tenantId, {
        assetId: record.assetId,
        ...(assetType ? { assetType } : {}),
        trigger: candidateConfig.trigger,
        severity: candidateConfig.severity,
        reason: candidateConfig.reason,
        evidence: candidateConfig.evidence,
        priority: candidateConfig.priority,
      });
    }
  }
}

function mapReviewPath(request: PersistedRequest): ReviewPath {
  switch (request.reviewPath) {
    case "AUTO_PUBLISH":
      return "auto_publish";
    case "EXPERT_REVIEW":
      return "expert_review";
    case "HUMAN_REVIEW":
    default:
      return "human_review";
  }
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

function inferAssetType(record: EvaluationRecord): string | undefined {
  const jobType = record.diagnostics?.["jobType"];
  return typeof jobType === "string" ? jobType : undefined;
}

function buildCandidateConfig(record: EvaluationRecord):
  | {
      trigger: RegenerationTrigger;
      severity: "medium" | "high" | "critical";
      reason: string;
      evidence: Record<string, unknown>;
      priority: number;
    }
  | undefined {
  if (!record.assetId) {
    return undefined;
  }

  const issues = record.issues ?? [];
  const safetyIssue = issues.find((issue: any) => issue.dimension === "safety");

  if (record.recommendation === "block") {
    return {
      trigger: safetyIssue ? "safety_concern" : "low_evaluation_score",
      severity: safetyIssue ? "critical" : "high",
      reason: safetyIssue
        ? "Evaluation blocked publishing because of a safety concern"
        : "Evaluation blocked publishing because content quality failed release thresholds",
      evidence: {
        evaluationId: record.id,
        overallScore: record.overallScore ?? null,
        issues,
      },
      priority: safetyIssue ? 95 : 85,
    };
  }

  if (record.recommendation === "manual_review") {
    return {
      trigger: "low_evaluation_score",
      severity: "medium",
      reason: "Evaluation requires manual review before release",
      evidence: {
        evaluationId: record.id,
        overallScore: record.overallScore ?? null,
        issues,
      },
      priority: 65,
    };
  }

  return undefined;
}

function deriveNextAction(input: {
  evaluationRecommendation: "auto_publish" | "manual_review" | "block";
  reviewPath: ReviewPath;
  autoPublished: boolean;
  eligibleAssetIds: string[];
  blockedAssetIds: string[];
}): GenerationQualityLoopSummary["nextAction"] {
  if (input.autoPublished) {
    return "auto_published";
  }
  if (input.evaluationRecommendation === "block" || input.blockedAssetIds.length > 0) {
    return "regeneration_required";
  }
  if (input.eligibleAssetIds.length === 0) {
    return "awaiting_assets";
  }
  if (
    input.evaluationRecommendation === "manual_review" ||
    input.reviewPath !== "auto_publish"
  ) {
    return "ready_for_manual_review";
  }
  return "ready_for_publish";
}

function buildPendingReviewNote(
  reviewPath: ReviewPath,
  recommendation: "auto_publish" | "manual_review" | "block",
): string {
  if (recommendation === "manual_review") {
    return "Evaluation requires manual review before release.";
  }
  if (reviewPath === "expert_review") {
    return "Request was planned for expert review before release.";
  }
  return "Request is awaiting reviewer decision.";
}
