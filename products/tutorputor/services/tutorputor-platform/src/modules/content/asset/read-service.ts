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

  private mapAsset(raw: any): ContentAsset {
    return {
      id: raw.id as string,
      tenantId: raw.tenantId as string,
      slug: raw.slug as string,
      title: raw.title as string,
      assetType: (raw.assetType as string).toLowerCase() as ContentAssetType,
      domain: raw.domain as string,
      conceptId: (raw.conceptId as string) ?? undefined,
      status: (raw.status as string).toLowerCase() as ContentAssetStatus,
      currentVersion: raw.currentVersion as number,
      qualityScore: (raw.qualityScore as number) ?? undefined,
      semanticIndexStatus:
        ((
          raw.semanticIndexStatus as string | null | undefined
        )?.toLowerCase() as ContentAsset["semanticIndexStatus"]) ?? undefined,
      recommendationStatus:
        ((
          raw.recommendationStatus as string | null | undefined
        )?.toLowerCase() as ContentAsset["recommendationStatus"]) ?? undefined,
      tags: (raw.tags as string[]) ?? undefined,
      targetGrades: Array.isArray(raw.targetGrades)
        ? (raw.targetGrades as string[])
        : [],
      difficultyLevel: (raw.difficultyLevel as string) ?? undefined,
      authorId: raw.authorId as string,
      lastEditedBy: (raw.lastEditedBy as string) ?? undefined,
      publishedAt: raw.publishedAt
        ? (raw.publishedAt as Date).toISOString()
        : undefined,
      createdAt: (raw.createdAt as Date).toISOString(),
      updatedAt: (raw.updatedAt as Date).toISOString(),
      promptHash: (raw.promptHash as string) ?? undefined,
      riskLevel: (
        (raw.riskLevel as string | null | undefined) ?? "LOW"
      ).toUpperCase() as ContentAsset["riskLevel"],
      confidenceScore: (raw.confidenceScore as number) ?? undefined,
      legacyModuleId: (raw.legacyModuleId as string) ?? undefined,
      legacyExperienceId: (raw.legacyExperienceId as string) ?? undefined,
    };
  }

  private mapBlock(raw: any): ContentBlock {
    return {
      id: raw.id as string,
      assetId: raw.assetId as string,
      blockRef: raw.blockRef as string,
      blockType: (
        raw.blockType as string
      ).toLowerCase() as ContentBlock["blockType"],
      orderIndex: raw.orderIndex as number,
      title: (raw.title as string) ?? undefined,
      payload: (raw.payload as Record<string, unknown>) ?? {},
      claimRefs: (raw.claimRefs as string[]) ?? undefined,
      evidenceRefs: (raw.evidenceRefs as string[]) ?? undefined,
      createdAt: (raw.createdAt as Date).toISOString(),
      updatedAt: (raw.updatedAt as Date).toISOString(),
    };
  }

  private mapManifest(raw: any): ArtifactManifest {
    return {
      id: raw.id as string,
      assetId: raw.assetId as string,
      manifestType: (
        raw.manifestType as string
      ).toLowerCase() as ArtifactManifest["manifestType"],
      version: raw.version as string,
      claimRef: (raw.claimRef as string) ?? undefined,
      manifest: (raw.manifest as Record<string, unknown>) ?? {},
      schema: (raw.schema as string) ?? undefined,
      isValid: raw.isValid as boolean,
      validationErrors: undefined,
      generatedBy:
        (raw.generatedBy as ArtifactManifest["generatedBy"]) ?? undefined,
      generationId: (raw.generationId as string) ?? undefined,
      createdAt: (raw.createdAt as Date).toISOString(),
      updatedAt: (raw.updatedAt as Date).toISOString(),
    };
  }

  private mapRevision(raw: any): ContentAssetRevision {
    return {
      id: raw.id as string,
      assetId: raw.assetId as string,
      version: raw.version as number,
      changeNote: (raw.changeNote as string) ?? undefined,
      snapshot: (raw.snapshot as Record<string, unknown>) ?? {},
      qualityScore: (raw.qualityScore as number) ?? undefined,
      validationId: (raw.validationId as string) ?? undefined,
      createdBy: raw.createdBy as string,
      createdAt: (raw.createdAt as Date).toISOString(),
    };
  }
}
