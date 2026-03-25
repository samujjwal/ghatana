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
import type {
  PublishAssetInput,
  PublishResult,
} from "@tutorputor/contracts/v1/content-studio";

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class PublishService {
  constructor(private readonly prisma: PrismaClient) {}

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
    const asset = await (this.prisma as any).contentAsset.findFirst({
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
    if (!input.bypassEvaluationCheck) {
      const latestEval = await (this.prisma as any).evaluationRecord.findFirst({
        where: { tenantId, assetId: input.assetId },
        orderBy: { createdAt: "desc" },
      });

      if (!latestEval) {
        return {
          assetId: input.assetId,
          published: false,
          reason:
            "No evaluation record found. Run evaluation before publishing.",
        };
      }

      if (latestEval.recommendation === "BLOCK") {
        return {
          assetId: input.assetId,
          published: false,
          reason:
            "Evaluation recommends blocking this asset. Review issues before publishing.",
        };
      }
    }

    // Validate manifests for structured content types
    const MANIFEST_TYPES = ["simulation", "animation", "assessment", "example"];
    if (MANIFEST_TYPES.includes((asset.assetType ?? "").toLowerCase())) {
      const manifestData = asset.manifestData as any;
      if (!manifestData) {
        return {
          assetId: input.assetId,
          published: false,
          reason: `${asset.assetType} assets require a valid manifest before publishing.`,
        };
      }
      if (!manifestData.id) {
        return {
          assetId: input.assetId,
          published: false,
          reason: "Manifest missing required field: id",
        };
      }
    }

    // Publish
    await (this.prisma as any).contentAsset.update({
      where: { id: input.assetId },
      data: {
        status: "PUBLISHED",
        semanticIndexStatus: "PENDING",
        recommendationStatus: "STALE",
      },
    });

    return {
      assetId: input.assetId,
      published: true,
      publishedAt: new Date().toISOString(),
      semanticIndexStatus: "pending",
      recommendationStatus: "stale",
      semanticIndexQueued: true,
      recommendationRecomputeQueued: true,
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
    const evaluations = await (this.prisma as any).evaluationRecord.findMany({
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
