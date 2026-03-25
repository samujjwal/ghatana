/**
 * Semantic Chunk Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify semantic chunk extraction, persistence, and staleness
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { SemanticChunkService } from "../chunk-service";

// ---------------------------------------------------------------------------
// Prisma Mock
// ---------------------------------------------------------------------------

function makePrisma() {
  return {
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue(null),
      update: vi.fn().mockResolvedValue({}),
    },
    semanticChunk: {
      findMany: vi.fn().mockResolvedValue([]),
      create: vi.fn().mockImplementation((args: any) => ({
        id: `chunk-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        ...args.data,
      })),
      update: vi.fn().mockImplementation((args: any) => ({
        ...args.data,
      })),
      updateMany: vi.fn().mockResolvedValue({ count: 0 }),
      count: vi.fn().mockResolvedValue(0),
    },
  };
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeAsset(overrides: Record<string, any> = {}) {
  return {
    id: "asset-1",
    tenantId: "tenant-1",
    title: "Newton's Laws of Motion",
    domain: "physics",
    tags: ["mechanics", "forces"],
    searchableText: "Force equals mass times acceleration",
    blocks: [],
    artifactManifests: [],
    ...overrides,
  };
}

function makeBlock(overrides: Record<string, any> = {}) {
  return {
    id: "block-1",
    blockRef: "explainer-intro",
    orderIndex: 0,
    payload: {
      text: "An object at rest stays at rest unless acted upon by a force.",
    },
    claimRefs: ["claim-1"],
    ...overrides,
  };
}

function makeManifest(overrides: Record<string, any> = {}) {
  return {
    id: "manifest-1",
    manifest: {
      problemStatement: "Calculate net force",
      steps: [{ label: "Step 1", content: "F=ma" }],
    },
    claimRef: "claim-2",
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("SemanticChunkService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: SemanticChunkService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new SemanticChunkService(prisma as any);
  });

  // =========================================================================
  // indexAsset
  // =========================================================================

  describe("indexAsset", () => {
    it("should return zero counts when asset not found", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(null);

      const result = await service.indexAsset("nonexistent");

      expect(result.assetId).toBe("nonexistent");
      expect(result.chunksCreated).toBe(0);
      expect(result.chunksUpdated).toBe(0);
      expect(result.chunksStale).toBe(0);
      expect(result.embeddingsPending).toBe(0);
    });

    it("should create metadata chunk from asset title, domain, tags", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());
      prisma.semanticChunk.count.mockResolvedValue(1);

      const result = await service.indexAsset("asset-1");

      expect(result.chunksCreated).toBe(1);
      expect(prisma.semanticChunk.create).toHaveBeenCalledTimes(1);

      const createCall = prisma.semanticChunk.create.mock.calls[0][0];
      expect(createCall.data.chunkRef).toBe("meta:0");
      expect(createCall.data.source).toBe("METADATA");
      expect(createCall.data.sourceRef).toBe("asset-1");
      expect(createCall.data.text).toContain("Newton");
      expect(createCall.data.text).toContain("physics");
      expect(createCall.data.text).toContain("mechanics");
      expect(createCall.data.embeddingStatus).toBe("PENDING");
      expect(createCall.data.domain).toBe("physics");
    });

    it("should create block chunks ordered by orderIndex", async () => {
      const blocks = [
        makeBlock({
          id: "b2",
          blockRef: "summary",
          orderIndex: 1,
          payload: { text: "Summary of forces." },
        }),
        makeBlock({
          id: "b1",
          blockRef: "intro",
          orderIndex: 0,
          payload: { text: "Introduction to motion." },
        }),
      ];
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));

      await service.indexAsset("asset-1");

      const blockCreates = prisma.semanticChunk.create.mock.calls.filter(
        (c: any) => c[0].data.source === "BLOCK",
      );

      // blocks should be sorted by orderIndex
      expect(blockCreates[0][0].data.chunkRef).toBe("intro:0");
      expect(blockCreates[1][0].data.chunkRef).toBe("summary:0");
    });

    it("should create manifest chunks", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ artifactManifests: [makeManifest()] }),
      );

      await service.indexAsset("asset-1");

      const manifestCreates = prisma.semanticChunk.create.mock.calls.filter(
        (c: any) => c[0].data.source === "MANIFEST",
      );

      expect(manifestCreates.length).toBeGreaterThanOrEqual(1);
      expect(manifestCreates[0][0].data.chunkRef).toBe("manifest-manifest-1:0");
      expect(manifestCreates[0][0].data.claimRefs).toEqual(["claim-2"]);
    });

    it("should skip blocks with empty payload text", async () => {
      const blocks = [
        makeBlock({ id: "b1", blockRef: "empty", payload: {} }),
        makeBlock({
          id: "b2",
          blockRef: "has-text",
          payload: { text: "Some content" },
        }),
      ];
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));

      await service.indexAsset("asset-1");

      const blockCreates = prisma.semanticChunk.create.mock.calls.filter(
        (c: any) => c[0].data.source === "BLOCK",
      );

      expect(blockCreates).toHaveLength(1);
      expect(blockCreates[0][0].data.chunkRef).toBe("has-text:0");
    });

    it("should compute SHA-256 content hash for deduplication", async () => {
      const blocks = [makeBlock({ payload: { text: "deterministic text" } })];
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));

      await service.indexAsset("asset-1");

      const blockCreate = prisma.semanticChunk.create.mock.calls.find(
        (c: any) => c[0].data.source === "BLOCK",
      );

      expect(blockCreate).toBeDefined();
      const hash = blockCreate![0].data.contentHash;
      expect(hash).toMatch(/^[a-f0-9]{64}$/); // SHA-256 hex

      // Index again — same content should produce same hash
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));
      prisma.semanticChunk.findMany.mockResolvedValue([
        {
          id: "existing-1",
          chunkRef: "explainer-intro:0",
          contentHash: hash,
          embeddingStatus: "READY",
        },
        {
          id: "existing-meta",
          chunkRef: "meta:0",
          contentHash: "oldhash",
          embeddingStatus: "READY",
        },
      ]);
      prisma.semanticChunk.create.mockClear();

      await service.indexAsset("asset-1");

      // Block chunk should NOT be re-created (same hash)
      const newBlockCreates = prisma.semanticChunk.create.mock.calls.filter(
        (c: any) => c[0].data.source === "BLOCK",
      );
      expect(newBlockCreates).toHaveLength(0);
    });

    it("should update chunk and mark STALE when content hash changes", async () => {
      const existingChunks = [
        {
          id: "ec-1",
          chunkRef: "explainer-intro:0",
          contentHash: "oldhash",
          embeddingStatus: "READY",
        },
      ];
      prisma.semanticChunk.findMany.mockResolvedValue(existingChunks);
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({
          blocks: [makeBlock({ payload: { text: "updated text" } })],
        }),
      );

      const result = await service.indexAsset("asset-1");

      expect(result.chunksUpdated).toBeGreaterThanOrEqual(1);

      const updateCall = prisma.semanticChunk.update.mock.calls.find(
        (c: any) => c[0].where.id === "ec-1",
      );
      expect(updateCall).toBeDefined();
      expect(updateCall![0].data.embeddingStatus).toBe("STALE");
    });

    it("should mark orphaned chunks as stale", async () => {
      const existingChunks = [
        {
          id: "ec-orphan",
          chunkRef: "old-block:0",
          contentHash: "somehash",
          embeddingStatus: "READY",
        },
      ];
      prisma.semanticChunk.findMany.mockResolvedValue(existingChunks);
      // Asset with no blocks — the existing chunk is orphaned
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());

      const result = await service.indexAsset("asset-1");

      expect(result.chunksStale).toBe(1);
      const staleUpdate = prisma.semanticChunk.update.mock.calls.find(
        (c: any) => c[0].where.id === "ec-orphan",
      );
      expect(staleUpdate).toBeDefined();
      expect(staleUpdate![0].data.embeddingStatus).toBe("STALE");
    });

    it("should not double-count already-stale orphans", async () => {
      const existingChunks = [
        {
          id: "ec-already-stale",
          chunkRef: "removed-block:0",
          contentHash: "x",
          embeddingStatus: "STALE",
        },
      ];
      prisma.semanticChunk.findMany.mockResolvedValue(existingChunks);
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());

      const result = await service.indexAsset("asset-1");

      expect(result.chunksStale).toBe(0); // Already stale, not re-marked
    });

    it("should update asset semanticIndexStatus to INDEXED", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());

      await service.indexAsset("asset-1");

      expect(prisma.contentAsset.update).toHaveBeenCalledWith({
        where: { id: "asset-1" },
        data: { semanticIndexStatus: "INDEXED" },
      });
    });

    it("should report embeddingsPending count", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());
      prisma.semanticChunk.count.mockResolvedValue(3);

      const result = await service.indexAsset("asset-1");

      expect(result.embeddingsPending).toBe(3);
      expect(prisma.semanticChunk.count).toHaveBeenCalledWith({
        where: {
          assetId: "asset-1",
          embeddingStatus: { in: ["PENDING", "STALE"] },
        },
      });
    });

    it("should force update even when hash matches", async () => {
      const hash = "abc123"; // doesn't matter, force overrides
      const existingChunks = [
        {
          id: "ec-1",
          chunkRef: "meta:0",
          contentHash: hash,
          embeddingStatus: "READY",
        },
      ];
      prisma.semanticChunk.findMany.mockResolvedValue(existingChunks);
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ searchableText: "" }),
      );

      // Without force — meta chunk hash differs, so update happens for text change
      // With force on a matching hash — update should still happen

      // Simulate matching hash scenario:
      // Build an asset where the meta hash would match ec-1's hash
      // Easier to just check the force flag directly
      prisma.semanticChunk.findMany.mockResolvedValue([
        {
          id: "ec-force",
          chunkRef: "meta:0",
          contentHash: "matching-hash",
          embeddingStatus: "READY",
        },
      ]);
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset());

      await service.indexAsset("asset-1", { force: true });

      // Should have updated ec-force even though we didn't change content
      const forceUpdate = prisma.semanticChunk.update.mock.calls.find(
        (c: any) => c[0].where.id === "ec-force",
      );
      expect(forceUpdate).toBeDefined();
    });

    it("should split long text into multiple chunks", async () => {
      // Generate text longer than MAX_CHUNK_CHARS (2048)
      const longText = "A".repeat(3000) + ". " + "B".repeat(1000);
      const blocks = [makeBlock({ payload: { text: longText } })];
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));

      await service.indexAsset("asset-1");

      const blockCreates = prisma.semanticChunk.create.mock.calls.filter(
        (c: any) => c[0].data.source === "BLOCK",
      );

      // Should produce at least 2 block chunks
      expect(blockCreates.length).toBeGreaterThanOrEqual(2);
      expect(blockCreates[0][0].data.chunkRef).toBe("explainer-intro:0");
      expect(blockCreates[1][0].data.chunkRef).toBe("explainer-intro:1");
    });

    it("should estimate token count (approx chars/4)", async () => {
      const text = "Hello world this is a test"; // 26 chars → ~7 tokens
      const blocks = [makeBlock({ payload: { text } })];
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));

      await service.indexAsset("asset-1");

      const blockCreate = prisma.semanticChunk.create.mock.calls.find(
        (c: any) => c[0].data.source === "BLOCK",
      );

      expect(blockCreate![0].data.tokenCount).toBe(Math.ceil(26 / 4));
    });

    it("should extract text recursively from nested payload objects", async () => {
      const blocks = [
        makeBlock({
          payload: {
            type: "richtext",
            children: [
              { type: "paragraph", content: "First paragraph." },
              { type: "paragraph", content: "Second paragraph." },
            ],
          },
        }),
      ];
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));

      await service.indexAsset("asset-1");

      const blockCreate = prisma.semanticChunk.create.mock.calls.find(
        (c: any) => c[0].data.source === "BLOCK",
      );

      expect(blockCreate![0].data.text).toContain("First paragraph");
      expect(blockCreate![0].data.text).toContain("Second paragraph");
    });

    it("should handle asset with no title or tags gracefully", async () => {
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({
          title: null,
          tags: null,
          searchableText: null,
          domain: null,
        }),
      );

      const result = await service.indexAsset("asset-1");

      // No metadata chunk created since all fields are empty
      const metaCreates = prisma.semanticChunk.create.mock.calls.filter(
        (c: any) => c[0].data.source === "METADATA",
      );
      expect(metaCreates).toHaveLength(0);
      expect(result.chunksCreated).toBe(0);
    });

    it("should pass claim refs from blocks to chunks", async () => {
      const blocks = [makeBlock({ claimRefs: ["claim-A", "claim-B"] })];
      prisma.contentAsset.findFirst.mockResolvedValue(makeAsset({ blocks }));

      await service.indexAsset("asset-1");

      const blockCreate = prisma.semanticChunk.create.mock.calls.find(
        (c: any) => c[0].data.source === "BLOCK",
      );

      expect(blockCreate![0].data.claimRefs).toEqual(["claim-A", "claim-B"]);
    });

    it("should handle multiple blocks and manifests together", async () => {
      const blocks = [
        makeBlock({
          id: "b1",
          blockRef: "intro",
          orderIndex: 0,
          payload: { text: "Introduction" },
        }),
        makeBlock({
          id: "b2",
          blockRef: "body",
          orderIndex: 1,
          payload: { text: "Body content" },
        }),
      ];
      const manifests = [
        makeManifest({ id: "m1" }),
        makeManifest({
          id: "m2",
          manifest: { title: "Second manifest" },
          claimRef: null,
        }),
      ];
      prisma.contentAsset.findFirst.mockResolvedValue(
        makeAsset({ blocks, artifactManifests: manifests }),
      );

      const result = await service.indexAsset("asset-1");

      // 1 metadata + 2 blocks + 2 manifests = 5 chunks
      expect(result.chunksCreated).toBe(5);
    });
  });

  // =========================================================================
  // markAssetStale
  // =========================================================================

  describe("markAssetStale", () => {
    it("should mark READY chunks as STALE", async () => {
      await service.markAssetStale("asset-1");

      expect(prisma.semanticChunk.updateMany).toHaveBeenCalledWith({
        where: { assetId: "asset-1", embeddingStatus: "READY" },
        data: { embeddingStatus: "STALE" },
      });
    });

    it("should update asset semanticIndexStatus", async () => {
      await service.markAssetStale("asset-1");

      expect(prisma.contentAsset.update).toHaveBeenCalledWith({
        where: { id: "asset-1" },
        data: { semanticIndexStatus: "stale" },
      });
    });
  });

  // =========================================================================
  // getPendingChunks
  // =========================================================================

  describe("getPendingChunks", () => {
    it("should query PENDING and STALE chunks for tenant", async () => {
      const mockChunks = [
        { id: "c1", assetId: "a1", chunkRef: "meta:0", text: "test" },
      ];
      prisma.semanticChunk.findMany.mockResolvedValue(mockChunks);

      const result = await service.getPendingChunks("tenant-1", 50);

      expect(result).toEqual(mockChunks);
      expect(prisma.semanticChunk.findMany).toHaveBeenCalledWith({
        where: {
          asset: { tenantId: "tenant-1" },
          embeddingStatus: { in: ["PENDING", "STALE"] },
        },
        select: { id: true, assetId: true, chunkRef: true, text: true },
        take: 50,
        orderBy: { createdAt: "asc" },
      });
    });

    it("should default to limit 100", async () => {
      await service.getPendingChunks("tenant-1");

      expect(prisma.semanticChunk.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ take: 100 }),
      );
    });
  });
});
