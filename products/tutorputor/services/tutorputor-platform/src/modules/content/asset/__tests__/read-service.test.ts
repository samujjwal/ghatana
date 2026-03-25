/**
 * Content Asset Read Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify asset read APIs: list, detail, related, revisions
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { ContentAssetReadService } from "../read-service";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const TENANT_ID = "tenant-read-1";
const ASSET_ID = "asset-001";
const NOW = new Date("2025-06-01T00:00:00Z");

function makeRawAsset(overrides: Record<string, unknown> = {}) {
  return {
    id: ASSET_ID,
    tenantId: TENANT_ID,
    slug: "newtons-second-law",
    title: "Newton's Second Law",
    assetType: "EXPLAINER",
    domain: "physics",
    conceptId: "concept-force",
    status: "PUBLISHED",
    currentVersion: 2,
    qualityScore: 0.92,
    reviewState: null,
    semanticIndexStatus: "INDEXED",
    searchableText: "force mass acceleration",
    tags: ["mechanics", "forces"],
    targetGrades: ["grade-9", "grade-10"],
    difficultyLevel: "intermediate",
    authorId: "author-1",
    lastEditedBy: "editor-1",
    publishedAt: NOW,
    createdAt: NOW,
    updatedAt: NOW,
    promptHash: "abc123",
    riskLevel: "LOW",
    confidenceScore: 0.95,
    legacyModuleId: "mod-1",
    legacyExperienceId: "exp-1",
    ...overrides,
  };
}

function makeRawBlock(overrides: Record<string, unknown> = {}) {
  return {
    id: "block-1",
    assetId: ASSET_ID,
    blockRef: "blk-intro",
    blockType: "TEXT_EXPLAINER",
    orderIndex: 0,
    title: "Introduction",
    payload: { html: "<p>Forces</p>" },
    claimRefs: ["claim-1"],
    evidenceRefs: ["ev-1"],
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides,
  };
}

function makeRawManifest(overrides: Record<string, unknown> = {}) {
  return {
    id: "manifest-1",
    assetId: ASSET_ID,
    manifestType: "SIMULATION",
    version: "1.0.0",
    claimRef: "claim-1",
    manifest: { engine: "matter-js" },
    schema: "sim-v1",
    isValid: true,
    validationErrors: null,
    generatedBy: "ai",
    generationId: "gen-1",
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides,
  };
}

function makeRawRevision(overrides: Record<string, unknown> = {}) {
  return {
    id: "rev-1",
    assetId: ASSET_ID,
    version: 2,
    changeNote: "Updated explainer text",
    changeDiff: '{"title":["old","new"]}',
    snapshot: { title: "Newton's Second Law" },
    qualityScore: 0.92,
    validationId: "val-1",
    createdBy: "author-1",
    createdAt: NOW,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Prisma mock
// ---------------------------------------------------------------------------

function makePrisma() {
  return {
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
      count: vi.fn().mockResolvedValue(0),
    },
    contentBlock: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    artifactManifest: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    contentAssetRevision: {
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
    },
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ContentAssetReadService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: ContentAssetReadService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new ContentAssetReadService(prisma as any);
  });

  // =========================================================================
  // getAssetDetail
  // =========================================================================

  describe("getAssetDetail", () => {
    it("returns null when asset does not exist", async () => {
      const result = await service.getAssetDetail(TENANT_ID, "nonexistent");
      expect(result).toBeNull();
      expect(prisma.contentAsset.findFirst).toHaveBeenCalledWith({
        where: { id: "nonexistent", tenantId: TENANT_ID },
      });
    });

    it("returns full detail with blocks, manifests, and revision", async () => {
      const rawAsset = makeRawAsset();
      const rawBlock = makeRawBlock();
      const rawManifest = makeRawManifest();
      const rawRevision = makeRawRevision();

      prisma.contentAsset.findFirst.mockResolvedValue(rawAsset);
      prisma.contentBlock.findMany.mockResolvedValue([rawBlock]);
      prisma.artifactManifest.findMany.mockResolvedValue([rawManifest]);
      prisma.contentAssetRevision.findFirst.mockResolvedValue(rawRevision);

      const result = await service.getAssetDetail(TENANT_ID, ASSET_ID);

      expect(result).not.toBeNull();
      expect(result!.asset.id).toBe(ASSET_ID);
      expect(result!.asset.assetType).toBe("explainer"); // lowercase mapping
      expect(result!.asset.status).toBe("published"); // lowercase mapping
      expect(result!.asset.title).toBe("Newton's Second Law");
      expect(result!.asset.qualityScore).toBe(0.92);

      expect(result!.blocks).toHaveLength(1);
      expect(result!.blocks[0].blockRef).toBe("blk-intro");
      expect(result!.blocks[0].blockType).toBe("text_explainer");

      expect(result!.manifests).toHaveLength(1);
      expect(result!.manifests[0].manifestType).toBe("simulation");
      expect(result!.manifests[0].isValid).toBe(true);

      expect(result!.currentRevision).not.toBeNull();
      expect(result!.currentRevision!.version).toBe(2);
      expect(result!.currentRevision!.changeNote).toBe(
        "Updated explainer text",
      );
    });

    it("returns null revision when no matching revision exists", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());
      prisma.contentAssetRevision.findFirst.mockResolvedValue(null);

      const result = await service.getAssetDetail(TENANT_ID, ASSET_ID);
      expect(result!.currentRevision).toBeNull();
    });

    it("fetches blocks ordered by orderIndex ascending", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());

      await service.getAssetDetail(TENANT_ID, ASSET_ID);

      expect(prisma.contentBlock.findMany).toHaveBeenCalledWith({
        where: { assetId: ASSET_ID },
        orderBy: { orderIndex: "asc" },
      });
    });
  });

  // =========================================================================
  // listAssets
  // =========================================================================

  describe("listAssets", () => {
    it("returns empty list for no assets", async () => {
      const result = await service.listAssets({ tenantId: TENANT_ID });
      expect(result.assets).toHaveLength(0);
      expect(result.total).toBe(0);
    });

    it("applies default pagination (limit 20, offset 0)", async () => {
      await service.listAssets({ tenantId: TENANT_ID });

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          take: 20,
          skip: 0,
          orderBy: { updatedAt: "desc" },
        }),
      );
    });

    it("applies custom pagination", async () => {
      await service.listAssets({ tenantId: TENANT_ID, limit: 5, offset: 10 });

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          take: 5,
          skip: 10,
        }),
      );
    });

    it("filters by assetType (uppercase mapped)", async () => {
      await service.listAssets({
        tenantId: TENANT_ID,
        assetType: "explainer" as any,
      });

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            tenantId: TENANT_ID,
            assetType: "EXPLAINER",
          }),
        }),
      );
    });

    it("filters by status (uppercase mapped)", async () => {
      await service.listAssets({
        tenantId: TENANT_ID,
        status: "published" as any,
      });

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            status: "PUBLISHED",
          }),
        }),
      );
    });

    it("filters by domain", async () => {
      await service.listAssets({ tenantId: TENANT_ID, domain: "physics" });

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            domain: "physics",
          }),
        }),
      );
    });

    it("applies search text filter on title and searchableText", async () => {
      await service.listAssets({ tenantId: TENANT_ID, search: "newton" });

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            OR: [
              { title: { contains: "newton" } },
              { searchableText: { contains: "newton" } },
            ],
          }),
        }),
      );
    });

    it("returns correct total count alongside assets", async () => {
      prisma.contentAsset.findMany.mockResolvedValue([makeRawAsset()]);
      prisma.contentAsset.count.mockResolvedValue(42);

      const result = await service.listAssets({ tenantId: TENANT_ID });
      expect(result.assets).toHaveLength(1);
      expect(result.total).toBe(42);
    });

    it("maps returned assets to lowercase types", async () => {
      prisma.contentAsset.findMany.mockResolvedValue([
        makeRawAsset({ assetType: "MODULE", status: "DRAFT" }),
      ]);
      prisma.contentAsset.count.mockResolvedValue(1);

      const result = await service.listAssets({ tenantId: TENANT_ID });
      expect(result.assets[0].assetType).toBe("module");
      expect(result.assets[0].status).toBe("draft");
    });
  });

  // =========================================================================
  // getRelatedAssets
  // =========================================================================

  describe("getRelatedAssets", () => {
    it("returns empty when source asset not found", async () => {
      const result = await service.getRelatedAssets(TENANT_ID, "nonexistent");
      expect(result).toHaveLength(0);
    });

    it("queries for related assets by domain and concept", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());
      prisma.contentAsset.findMany.mockResolvedValue([]);

      await service.getRelatedAssets(TENANT_ID, ASSET_ID);

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            tenantId: TENANT_ID,
            id: { not: ASSET_ID },
            status: "PUBLISHED",
          }),
          take: 10,
          orderBy: { qualityScore: "desc" },
        }),
      );
    });

    it("applies custom limit", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());
      prisma.contentAsset.findMany.mockResolvedValue([]);

      await service.getRelatedAssets(TENANT_ID, ASSET_ID, 5);

      expect(prisma.contentAsset.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ take: 5 }),
      );
    });

    it("labels same-concept relations correctly", async () => {
      const source = makeRawAsset({ conceptId: "concept-force" });
      const related = makeRawAsset({
        id: "asset-002",
        conceptId: "concept-force",
        domain: "physics",
      });

      prisma.contentAsset.findFirst.mockResolvedValue(source);
      prisma.contentAsset.findMany.mockResolvedValue([related]);

      const result = await service.getRelatedAssets(TENANT_ID, ASSET_ID);
      expect(result).toHaveLength(1);
      expect(result[0].relation).toBe("same_concept");
    });

    it("labels same-domain relations when concept differs", async () => {
      const source = makeRawAsset({ conceptId: "concept-force" });
      const related = makeRawAsset({
        id: "asset-003",
        conceptId: "concept-energy",
        domain: "physics",
      });

      prisma.contentAsset.findFirst.mockResolvedValue(source);
      prisma.contentAsset.findMany.mockResolvedValue([related]);

      const result = await service.getRelatedAssets(TENANT_ID, ASSET_ID);
      expect(result).toHaveLength(1);
      expect(result[0].relation).toBe("same_domain");
    });
  });

  // =========================================================================
  // getRevisionHistory
  // =========================================================================

  describe("getRevisionHistory", () => {
    it("returns empty when asset not found", async () => {
      const result = await service.getRevisionHistory(TENANT_ID, "nonexistent");
      expect(result).toHaveLength(0);
    });

    it("returns revisions ordered by version desc", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());
      prisma.contentAssetRevision.findMany.mockResolvedValue([
        makeRawRevision({ version: 2 }),
        makeRawRevision({ id: "rev-0", version: 1 }),
      ]);

      const result = await service.getRevisionHistory(TENANT_ID, ASSET_ID);

      expect(result).toHaveLength(2);
      expect(result[0].version).toBe(2);
      expect(result[1].version).toBe(1);

      expect(prisma.contentAssetRevision.findMany).toHaveBeenCalledWith({
        where: { assetId: ASSET_ID },
        orderBy: { version: "desc" },
      });
    });

    it("maps revision fields correctly", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());
      prisma.contentAssetRevision.findMany.mockResolvedValue([
        makeRawRevision(),
      ]);

      const result = await service.getRevisionHistory(TENANT_ID, ASSET_ID);
      const rev = result[0];

      expect(rev.id).toBe("rev-1");
      expect(rev.assetId).toBe(ASSET_ID);
      expect(rev.changeNote).toBe("Updated explainer text");
      expect(rev.snapshot).toEqual({ title: "Newton's Second Law" });
      expect(rev.qualityScore).toBe(0.92);
      expect(rev.validationId).toBe("val-1");
      expect(rev.createdBy).toBe("author-1");
      expect(rev.createdAt).toBe("2025-06-01T00:00:00.000Z");
    });
  });

  // =========================================================================
  // Mapping edge cases
  // =========================================================================

  describe("mapping edge cases", () => {
    it("handles null optional fields gracefully", async () => {
      const rawAsset = makeRawAsset({
        conceptId: null,
        qualityScore: null,
        semanticIndexStatus: null,
        tags: null,
        difficultyLevel: null,
        lastEditedBy: null,
        publishedAt: null,
        promptHash: null,
        confidenceScore: null,
        legacyModuleId: null,
        legacyExperienceId: null,
      });

      prisma.contentAsset.findFirst.mockResolvedValue(rawAsset);
      prisma.contentAssetRevision.findFirst.mockResolvedValue(null);

      const result = await service.getAssetDetail(TENANT_ID, ASSET_ID);
      const asset = result!.asset;

      expect(asset.conceptId).toBeUndefined();
      expect(asset.qualityScore).toBeUndefined();
      expect(asset.semanticIndexStatus).toBeUndefined();
      expect(asset.difficultyLevel).toBeUndefined();
      expect(asset.lastEditedBy).toBeUndefined();
      expect(asset.publishedAt).toBeUndefined();
      expect(asset.promptHash).toBeUndefined();
      expect(asset.confidenceScore).toBeUndefined();
      expect(asset.legacyModuleId).toBeUndefined();
      expect(asset.legacyExperienceId).toBeUndefined();
    });

    it("handles block with null optional fields", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());
      prisma.contentBlock.findMany.mockResolvedValue([
        makeRawBlock({ title: null, claimRefs: null, evidenceRefs: null }),
      ]);

      const result = await service.getAssetDetail(TENANT_ID, ASSET_ID);
      const block = result!.blocks[0];

      expect(block.title).toBeUndefined();
      expect(block.claimRefs).toBeUndefined();
      expect(block.evidenceRefs).toBeUndefined();
    });

    it("handles manifest with null optional fields", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeRawAsset());
      prisma.artifactManifest.findMany.mockResolvedValue([
        makeRawManifest({
          claimRef: null,
          schema: null,
          generatedBy: null,
          generationId: null,
        }),
      ]);

      const result = await service.getAssetDetail(TENANT_ID, ASSET_ID);
      const manifest = result!.manifests[0];

      expect(manifest.claimRef).toBeUndefined();
      expect(manifest.schema).toBeUndefined();
      expect(manifest.generatedBy).toBeUndefined();
      expect(manifest.generationId).toBeUndefined();
    });
  });
});
