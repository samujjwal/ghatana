/**
 * Publish Service
 *
 * Implements closed-loop publish and reindex (P4.4). Transitions a ContentAsset
 * to PUBLISHED, marks semantic index and recommendation status as stale so
 * downstream background workers will recompute.
 *
 * @doc.type class
 * @doc.purpose Publish evaluated assets and trigger downstream recompute
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";

interface PublishAssetInput {
  assetId: string;
  bypassEvaluationCheck?: boolean;
}

interface PublishResult {
  assetId: string;
  published: boolean;
  reason?: string;
  semanticIndexStatus?: string;
  recommendationStatus?: string;
  semanticIndexQueued?: boolean;
  recommendationRecomputeQueued?: boolean;
  qualityPredictionApplied?: boolean;
  outcomeAnalysisApplied?: boolean;
  recommendationRefresh?: {
    bootstrapCreated: number;
    bootstrapSkipped: number;
    processedAssets: number;
    updatedEdges: number;
    skippedEdges: number;
  };
}
import { RecommendationService } from "../recommendation/recommendation-service.js";
import { AssetOutcomeService } from "../recommendation/asset-outcome-service.js";
import { ContentQualityMLPipeline } from "../quality-ml/pipeline.js";

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class PublishService {
  private readonly recommendationService: RecommendationService;
  private readonly outcomeService: AssetOutcomeService;
  private readonly qualityPipeline: ContentQualityMLPipeline;

  constructor(private readonly prisma: PrismaClient) {
    this.recommendationService = new RecommendationService(prisma);
    this.outcomeService = new AssetOutcomeService(prisma);
    this.qualityPipeline = new ContentQualityMLPipeline(prisma);
  }

  /**
   * Publish an asset that has passed evaluation.
   *
   * Validates:
   *  - Asset exists and belongs to tenant
   *  - Asset has a passing evaluation (recommendation != "block")
   *  - Manifests are valid for structured types (simulation, animation, assessment)
   *
   * Then:
   *  - Sets status → "PUBLISHED"
   *  - Marks semanticIndexStatus → "PENDING" (background worker will reindex)
   *  - Marks recommendationStatus → "STALE" (background worker will recompute)
   */
  async publishAsset(
    tenantId: string,
    publishedBy: string,
    input: PublishAssetInput,
  ): Promise<PublishResult> {
    // Fetch the asset
    const asset = await this.prisma.contentAsset.findFirst({
      where: { id: input.assetId, tenantId },
    });

    if (!asset) {
      return {
        assetId: input.assetId,
        published: false,
        reason: "Asset not found",
      };
    }

    // Validate that there is a passing evaluation (if not bypassed)
    const latestEval = await this.prisma.evaluationRecord.findFirst({
      where: { tenantId, assetId: input.assetId },
      orderBy: { createdAt: "desc" },
    });

    let manifests: Array<{
      id: string;
      manifestType?: string | null;
      version?: string | null;
      claimRef?: string | null;
      isValid: boolean;
      generationId?: string | null;
      generatedBy?: string | null;
    }> = [];

    const evaluationForProvenance: {
      id: string;
      recommendation: string;
      overallScore?: number | null;
      diagnostics?: unknown;
      generationRequestId?: string | null;
    } | null = latestEval
      ? {
          id: latestEval.id,
          recommendation: latestEval.recommendation,
          overallScore: latestEval.overallScore,
          diagnostics: latestEval.diagnostics,
          generationRequestId: latestEval.generationRequestId,
        }
      : null;

    if (!input.bypassEvaluationCheck) {
      if (!evaluationForProvenance) {
        return {
          assetId: input.assetId,
          published: false,
          reason:
            "No evaluation record found. Run evaluation before publishing.",
        };
      }

      if (evaluationForProvenance.recommendation === "BLOCK") {
        return {
          assetId: input.assetId,
          published: false,
          reason:
            "Evaluation recommends blocking this asset. Review issues before publishing.",
        };
      }
    }

    // Validate manifests for structured content types
    const MANIFEST_TYPES = [
      "simulation",
      "animation",
      "assessment",
      "example_set",
    ];
    if (MANIFEST_TYPES.includes((asset.assetType ?? "").toLowerCase())) {
      manifests = await this.prisma.artifactManifest.findMany({
        where: { assetId: input.assetId },
      });
      if (manifests.length === 0) {
        return {
          assetId: input.assetId,
          published: false,
          reason: `${asset.assetType} assets require a valid manifest before publishing.`,
        };
      }
      const invalidManifest = manifests.find((manifest) => !manifest.isValid);
      if (invalidManifest) {
        return {
          assetId: input.assetId,
          published: false,
          reason: "One or more attached manifests failed validation",
        };
      }
    }

    await this.qualityPipeline.applyPrediction(tenantId, input.assetId);
    const publishedAt = new Date();

    // Publish
    await this.prisma.contentAsset.update({
      where: { id: input.assetId },
      data: {
        status: "PUBLISHED",
        publishedAt,
        semanticIndexStatus: "PENDING",
        recommendationStatus: "STALE",
      },
    });

    const revisionSnapshot = {
      title: asset.title,
      assetType: asset.assetType,
      domain: asset.domain,
      status: "PUBLISHED",
      targetGrades: asset.targetGrades,
      difficultyLevel: asset.difficultyLevel ?? null,
      promptHash: asset.promptHash ?? null,
      qualityScore: asset.qualityScore ?? null,
      reviewState: asset.reviewState ?? null,
      evaluation:
        evaluationForProvenance == null
          ? null
          : {
              evaluationId: evaluationForProvenance.id,
              recommendation: evaluationForProvenance.recommendation,
              overallScore: evaluationForProvenance.overallScore ?? null,
              generationRequestId:
                evaluationForProvenance.generationRequestId ?? null,
              diagnostics: evaluationForProvenance.diagnostics ?? null,
            },
      manifests: manifests.map((manifest) => ({
        id: manifest.id,
        manifestType: manifest.manifestType ?? null,
        version: manifest.version ?? null,
        claimRef: manifest.claimRef ?? null,
        isValid: manifest.isValid,
        generationId: manifest.generationId ?? null,
        generatedBy: manifest.generatedBy ?? null,
      })),
      publishedBy,
      publishedAt: publishedAt.toISOString(),
    };

    await Promise.all([
      (this.prisma as PrismaClient & {
        contentAssetRevision?: { create: (args: unknown) => Promise<unknown> };
        auditLog?: { create: (args: unknown) => Promise<unknown> };
      }).contentAssetRevision?.create({
        data: {
          assetId: input.assetId,
          version:
            typeof asset.currentVersion === "number" && asset.currentVersion > 0
              ? asset.currentVersion
              : 1,
          changeNote: "Published asset provenance snapshot",
          snapshot: revisionSnapshot,
          qualityScore:
            asset.qualityScore ?? evaluationForProvenance?.overallScore ?? null,
          validationId: evaluationForProvenance?.id ?? null,
          createdBy: publishedBy,
        },
      }),
      (this.prisma as PrismaClient & {
        contentAssetRevision?: { create: (args: unknown) => Promise<unknown> };
        auditLog?: { create: (args: unknown) => Promise<unknown> };
      }).auditLog?.create({
        data: {
          tenantId,
          actorId: publishedBy,
          action: "content_asset_published",
          resourceType: "ContentAsset",
          resourceId: input.assetId,
          outcome: "success",
          metadata: JSON.stringify(revisionSnapshot),
        },
      }),
    ]);

    const bootstrap = await this.recommendationService.bootstrapEdges(
      tenantId,
      input.assetId,
    );
    const refresh = await this.recommendationService.recomputeOutcomeAwareEdges(
      tenantId,
      {
        sourceAssetId: input.assetId,
        limit: 1,
      },
    );
    await this.outcomeService.analyzeAsset(tenantId, input.assetId, {
      apply: true,
      recomputeRecommendations: false,
    });

    return {
      assetId: input.assetId,
      published: true,
      semanticIndexStatus: "pending",
      recommendationStatus: "computed",
      semanticIndexQueued: true,
      recommendationRecomputeQueued: true,
      qualityPredictionApplied: true,
      outcomeAnalysisApplied: true,
      recommendationRefresh: {
        bootstrapCreated: bootstrap.created,
        bootstrapSkipped: bootstrap.skipped,
        processedAssets: refresh.processedAssets,
        updatedEdges: refresh.updatedEdges,
        skippedEdges: refresh.skippedEdges,
      },
    };
  }

  /**
   * Bulk publish all assets in a generation request that passed evaluation.
   */
  async publishByGenerationRequest(
    tenantId: string,
    publishedBy: string,
    generationRequestId: string,
  ): Promise<{ published: number; skipped: number; results: PublishResult[] }> {
    // Find all passing evaluation records for this request that have an assetId
    const evaluations = await this.prisma.evaluationRecord.findMany({
      where: {
        tenantId,
        generationRequestId,
        recommendation: { not: "BLOCK" },
        assetId: { not: null },
      },
    });

    const results: PublishResult[] = [];
    let published = 0;
    let skipped = 0;

    for (const ev of evaluations) {
      if (!ev.assetId) {
        skipped++;
        continue;
      }

      const result = await this.publishAsset(tenantId, publishedBy, {
        assetId: ev.assetId,
        bypassEvaluationCheck: true, // already validated above
      });
      results.push(result);
      if (result.published) {
        published++;
      } else {
        skipped++;
      }
    }

    return { published, skipped, results };
  }
}
