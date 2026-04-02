/**
 * Content Asset Read Service
 *
 * Provides read-only access to canonical content assets. Serves
 * discovery, asset detail, related assets, and admin authoring reads
 * from the canonical ContentAsset model.
 *
 * @doc.type class
 * @doc.purpose Read-only access to canonical content assets
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type {
  ContentAssetType,
  ContentAssetStatus,
  ContentAsset,
  ContentAssetRevision,
  ContentBlock,
  ArtifactManifest,
} from "../types.js";

// ---------------------------------------------------------------------------
// Query types
// ---------------------------------------------------------------------------

export interface AssetListFilters {
  tenantId: string;
  assetType?: ContentAssetType;
  status?: ContentAssetStatus;
  domain?: string;
  authorId?: string;
  search?: string;
  limit?: number;
  offset?: number;
}

export interface AssetDetail {
  asset: ContentAsset;
  blocks: ContentBlock[];
  manifests: ArtifactManifest[];
  currentRevision: ContentAssetRevision | null;
}

export interface AssetListResult {
  assets: ContentAsset[];
  total: number;
}

export interface RelatedAsset {
  asset: ContentAsset;
  relation: "same_domain" | "same_concept" | "prerequisite" | "follow_up";
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class ContentAssetReadService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Get a single asset by ID with full detail.
   */
  async getAssetDetail(
    tenantId: string,
    assetId: string,
  ): Promise<AssetDetail | null> {
    const asset = await this.prisma.contentAsset.findFirst({
      where: { id: assetId, tenantId },
    });

    if (!asset) return null;

    const [blocks, manifests, revision] = await Promise.all([
      this.prisma.contentBlock.findMany({
        where: { assetId },
        orderBy: { orderIndex: "asc" },
      }),
      this.prisma.artifactManifest.findMany({
        where: { assetId },
      }),
      this.prisma.contentAssetRevision.findFirst({
        where: { assetId, version: asset.currentVersion },
      }),
    ]);

    return {
      asset: this.mapAsset(asset),
      blocks: blocks.map((b: Record<string, unknown>) => this.mapBlock(b)),
      manifests: manifests.map((m: Record<string, unknown>) =>
        this.mapManifest(m),
      ),
      currentRevision: revision ? this.mapRevision(revision) : null,
    };
  }

  /**
   * List assets with filtering and pagination.
   */
  async listAssets(filters: AssetListFilters): Promise<AssetListResult> {
    const where: Record<string, unknown> = { tenantId: filters.tenantId };

    if (filters.assetType) where.assetType = filters.assetType.toUpperCase();
    if (filters.status) where.status = filters.status.toUpperCase();
    if (filters.domain) where.domain = filters.domain;
    if (filters.authorId) where.authorId = filters.authorId;
    if (filters.search) {
      where.OR = [
        { title: { contains: filters.search } },
        { searchableText: { contains: filters.search } },
      ];
    }

    const [assets, total] = await Promise.all([
      this.prisma.contentAsset.findMany({
        where,
        take: filters.limit ?? 20,
        skip: filters.offset ?? 0,
        orderBy: { updatedAt: "desc" },
      }),
      this.prisma.contentAsset.count({ where }),
    ]);

    return {
      assets: assets.map((a: Record<string, unknown>) => this.mapAsset(a)),
      total,
    };
  }

  /**
   * Get related assets based on domain and concept.
   */
  async getRelatedAssets(
    tenantId: string,
    assetId: string,
    limit = 10,
  ): Promise<RelatedAsset[]> {
    const asset = await this.prisma.contentAsset.findFirst({
      where: { id: assetId, tenantId },
    });

    if (!asset) return [];

    const related = await this.prisma.contentAsset.findMany({
      where: {
        tenantId,
        id: { not: assetId },
        status: "PUBLISHED",
        OR: [
          { domain: asset.domain },
          ...(asset.conceptId ? [{ conceptId: asset.conceptId }] : []),
        ],
      },
      take: limit,
      orderBy: { qualityScore: "desc" },
    });

    return related.map((r: Record<string, unknown>) => ({
      asset: this.mapAsset(r),
      relation:
        r.conceptId === asset.conceptId
          ? ("same_concept" as const)
          : ("same_domain" as const),
    }));
  }

  /**
   * Get revision history for an asset.
   */
  async getRevisionHistory(
    tenantId: string,
    assetId: string,
  ): Promise<ContentAssetRevision[]> {
    const asset = await this.prisma.contentAsset.findFirst({
      where: { id: assetId, tenantId },
    });

    if (!asset) return [];

    const revisions = await this.prisma.contentAssetRevision.findMany({
      where: { assetId },
      orderBy: { version: "desc" },
    });

    return revisions.map((r: Record<string, unknown>) => this.mapRevision(r));
  }

  // -------------------------------------------------------------------------
  // Mapping helpers
  // -------------------------------------------------------------------------

  private mapAsset(raw: Record<string, unknown>): ContentAsset {
    const semanticIndexStatus = this.normalizeSemanticIndexStatus(
      raw.semanticIndexStatus,
    );
    const recommendationStatus = this.normalizeRecommendationStatus(
      raw.recommendationStatus,
    );
    const publishedAt =
      raw.publishedAt instanceof Date ? raw.publishedAt.toISOString() : undefined;

    return {
      id: String(raw.id),
      tenantId: String(raw.tenantId),
      slug: String(raw.slug),
      title: String(raw.title),
      assetType: String(raw.assetType).toLowerCase() as ContentAssetType,
      domain: String(raw.domain),
      status: String(raw.status).toLowerCase() as ContentAssetStatus,
      currentVersion: Number(raw.currentVersion),
      targetGrades: Array.isArray(raw.targetGrades)
        ? raw.targetGrades.filter((grade): grade is string => typeof grade === "string")
        : [],
      authorId: String(raw.authorId),
      createdAt: (raw.createdAt as Date).toISOString(),
      updatedAt: (raw.updatedAt as Date).toISOString(),
      riskLevel: (
        (raw.riskLevel as string | null | undefined) ?? "LOW"
      ).toUpperCase() as ContentAsset["riskLevel"],
      ...(typeof raw.conceptId === "string" ? { conceptId: raw.conceptId } : {}),
      ...(typeof raw.qualityScore === "number"
        ? { qualityScore: raw.qualityScore }
        : {}),
      ...(semanticIndexStatus ? { semanticIndexStatus } : {}),
      ...(recommendationStatus ? { recommendationStatus } : {}),
      ...(Array.isArray(raw.tags)
        ? {
            tags: raw.tags.filter((tag): tag is string => typeof tag === "string"),
          }
        : {}),
      ...(typeof raw.difficultyLevel === "string"
        ? { difficultyLevel: raw.difficultyLevel }
        : {}),
      ...(typeof raw.lastEditedBy === "string"
        ? { lastEditedBy: raw.lastEditedBy }
        : {}),
      ...(publishedAt ? { publishedAt } : {}),
      ...(typeof raw.promptHash === "string" ? { promptHash: raw.promptHash } : {}),
      ...(typeof raw.confidenceScore === "number"
        ? { confidenceScore: raw.confidenceScore }
        : {}),
      ...(typeof raw.legacyModuleId === "string"
        ? { legacyModuleId: raw.legacyModuleId }
        : {}),
      ...(typeof raw.legacyExperienceId === "string"
        ? { legacyExperienceId: raw.legacyExperienceId }
        : {}),
      ...(typeof raw.reviewState === "string" ? { reviewState: raw.reviewState } : {}),
      ...(typeof raw.searchableText === "string"
        ? { searchableText: raw.searchableText }
        : {}),
    };
  }

  private mapBlock(raw: Record<string, unknown>): ContentBlock {
    return {
      id: String(raw.id),
      assetId: String(raw.assetId),
      blockRef: String(raw.blockRef),
      blockType: String(raw.blockType).toLowerCase() as ContentBlock["blockType"],
      orderIndex: Number(raw.orderIndex),
      payload: this.asJsonRecord(raw.payload),
      createdAt: (raw.createdAt as Date).toISOString(),
      updatedAt: (raw.updatedAt as Date).toISOString(),
      ...(typeof raw.title === "string" ? { title: raw.title } : {}),
      ...(Array.isArray(raw.claimRefs)
        ? {
            claimRefs: raw.claimRefs.filter(
              (ref): ref is string => typeof ref === "string",
            ),
          }
        : {}),
      ...(Array.isArray(raw.evidenceRefs)
        ? {
            evidenceRefs: raw.evidenceRefs.filter(
              (ref): ref is string => typeof ref === "string",
            ),
          }
        : {}),
    };
  }

  private mapManifest(raw: Record<string, unknown>): ArtifactManifest {
    const validationErrors = Array.isArray(raw.validationErrors)
      ? raw.validationErrors.filter(
          (error): error is string => typeof error === "string",
        )
      : undefined;
    const generatedBy =
      typeof raw.generatedBy === "string"
        ? (raw.generatedBy as ArtifactManifest["generatedBy"])
        : undefined;

    return {
      id: String(raw.id),
      assetId: String(raw.assetId),
      manifestType: (
        String(raw.manifestType)
      ).toLowerCase() as ArtifactManifest["manifestType"],
      version: String(raw.version),
      manifest: this.asJsonRecord(raw.manifest),
      isValid: Boolean(raw.isValid),
      createdAt: (raw.createdAt as Date).toISOString(),
      updatedAt: (raw.updatedAt as Date).toISOString(),
      ...(typeof raw.claimRef === "string" ? { claimRef: raw.claimRef } : {}),
      ...(typeof raw.schema === "string" ? { schema: raw.schema } : {}),
      ...(validationErrors ? { validationErrors } : {}),
      ...(generatedBy ? { generatedBy } : {}),
      ...(typeof raw.generationId === "string"
        ? { generationId: raw.generationId }
        : {}),
    };
  }

  private mapRevision(raw: Record<string, unknown>): ContentAssetRevision {
    return {
      id: String(raw.id),
      assetId: String(raw.assetId),
      version: Number(raw.version),
      snapshot: this.asJsonRecord(raw.snapshot),
      createdBy: String(raw.createdBy),
      createdAt: (raw.createdAt as Date).toISOString(),
      ...(typeof raw.changeNote === "string" ? { changeNote: raw.changeNote } : {}),
      ...(typeof raw.qualityScore === "number"
        ? { qualityScore: raw.qualityScore }
        : {}),
      ...(typeof raw.validationId === "string"
        ? { validationId: raw.validationId }
        : {}),
    };
  }

  private asJsonRecord(value: unknown): Record<string, unknown> {
    return value && typeof value === "object" && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {};
  }

  private normalizeSemanticIndexStatus(
    value: unknown,
  ): ContentAsset["semanticIndexStatus"] | undefined {
    if (typeof value !== "string") {
      return undefined;
    }

    const normalized = value.toLowerCase();
    if (
      normalized === "pending" ||
      normalized === "indexed" ||
      normalized === "stale"
    ) {
      return normalized;
    }

    return undefined;
  }

  private normalizeRecommendationStatus(
    value: unknown,
  ): ContentAsset["recommendationStatus"] | undefined {
    if (typeof value !== "string") {
      return undefined;
    }

    const normalized = value.toLowerCase();
    if (
      normalized === "pending" ||
      normalized === "computed" ||
      normalized === "stale"
    ) {
      return normalized;
    }

    return undefined;
  }
}
