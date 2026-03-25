/**
 * Asset Backfill Service Tests
 *
 * @doc.type test
 * @doc.purpose Verify asset backfill from legacy Module and LearningExperience roots
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  AssetBackfillService,
  type BackfillOptions,
} from "../backfill-service";

function makePrisma() {
  return {
    module: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    learningExperience: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockImplementation((args: any) => ({
        id: `ca-${Date.now()}`,
        ...args.data,
      })),
    },
    contentAssetRevision: {
      create: vi.fn().mockResolvedValue({ id: "rev-1" }),
    },
    artifactManifest: {
      create: vi.fn().mockResolvedValue({ id: "am-1" }),
    },
  };
}

function makeLogger() {
  return {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
    child: vi.fn().mockReturnThis(),
  };
}

const DEFAULT_OPTIONS: BackfillOptions = {
  tenantId: "tenant-1",
  batchSize: 10,
};

describe("AssetBackfillService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let logger: ReturnType<typeof makeLogger>;
  let service: AssetBackfillService;

  beforeEach(() => {
    prisma = makePrisma();
    logger = makeLogger();
    service = new AssetBackfillService(prisma as any, logger as any);
  });

  describe("backfillModules", () => {
    it("should return empty result when no modules exist", async () => {
      const result = await service.backfillModules(DEFAULT_OPTIONS);

      expect(result.totalProcessed).toBe(0);
      expect(result.succeeded).toBe(0);
      expect(result.failed).toBe(0);
      expect(result.mappings).toHaveLength(0);
    });

    it("should backfill a single module to ContentAsset", async () => {
      prisma.module.findMany
        .mockResolvedValueOnce([
          {
            id: "mod-1",
            tenantId: "tenant-1",
            title: "Newton's Laws",
            domain: "SCIENCE",
            status: "PUBLISHED",
            version: 2,
            authorId: "author-1",
            updatedBy: "editor-1",
            publishedAt: new Date("2026-01-01"),
          },
        ])
        .mockResolvedValueOnce([]); // cursor exhausted

      const result = await service.backfillModules(DEFAULT_OPTIONS);

      expect(result.totalProcessed).toBe(1);
      expect(result.succeeded).toBe(1);
      expect(result.failed).toBe(0);
      expect(result.mappings).toHaveLength(1);
      expect(result.mappings[0].sourceType).toBe("module");
      expect(result.mappings[0].sourceId).toBe("mod-1");
      expect(result.mappings[0].assetSlug).toBe("newton-s-laws");

      expect(prisma.contentAsset.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            assetType: "MODULE",
            legacyModuleId: "mod-1",
            status: "PUBLISHED",
          }),
        }),
      );
    });

    it("should skip already-migrated modules when skipExisting is true", async () => {
      prisma.module.findMany
        .mockResolvedValueOnce([
          {
            id: "mod-1",
            tenantId: "tenant-1",
            title: "Test",
            domain: "MATH",
            status: "DRAFT",
          },
        ])
        .mockResolvedValueOnce([]);
      prisma.contentAsset.findFirst.mockResolvedValue({ id: "ca-existing" });

      const result = await service.backfillModules({
        ...DEFAULT_OPTIONS,
        skipExisting: true,
      });

      expect(result.skipped).toBe(1);
      expect(result.succeeded).toBe(0);
      expect(prisma.contentAsset.create).not.toHaveBeenCalled();
    });

    it("should record failures without stopping the batch", async () => {
      prisma.module.findMany
        .mockResolvedValueOnce([
          {
            id: "mod-ok",
            tenantId: "tenant-1",
            title: "Good",
            domain: "MATH",
            status: "DRAFT",
            authorId: "a",
          },
          {
            id: "mod-bad",
            tenantId: "tenant-1",
            title: "Bad",
            domain: "MATH",
            status: "DRAFT",
            authorId: "a",
          },
        ])
        .mockResolvedValueOnce([]);

      prisma.contentAsset.create
        .mockResolvedValueOnce({ id: "ca-ok" })
        .mockRejectedValueOnce(new Error("Unique constraint violation"));

      const result = await service.backfillModules(DEFAULT_OPTIONS);

      expect(result.totalProcessed).toBe(2);
      expect(result.succeeded).toBe(1);
      expect(result.failed).toBe(1);
      expect(result.failures).toHaveLength(1);
      expect(result.failures[0].reason).toContain("Unique constraint");
    });

    it("should not persist in dry run mode", async () => {
      prisma.module.findMany
        .mockResolvedValueOnce([
          {
            id: "mod-1",
            tenantId: "tenant-1",
            title: "Dry Run",
            domain: "SCIENCE",
            status: "DRAFT",
          },
        ])
        .mockResolvedValueOnce([]);

      const result = await service.backfillModules({
        ...DEFAULT_OPTIONS,
        dryRun: true,
      });

      expect(result.succeeded).toBe(1);
      expect(result.mappings[0].assetId).toContain("dry-run");
      expect(prisma.contentAsset.create).not.toHaveBeenCalled();
    });
  });

  describe("backfillExperiences", () => {
    it("should return empty result when no experiences exist", async () => {
      const result = await service.backfillExperiences(DEFAULT_OPTIONS);

      expect(result.totalProcessed).toBe(0);
      expect(result.succeeded).toBe(0);
    });

    it("should backfill an experience with claims and simulation manifests", async () => {
      prisma.learningExperience.findMany
        .mockResolvedValueOnce([
          {
            id: "exp-1",
            tenantId: "tenant-1",
            title: "Projectile Motion",
            domain: "SCIENCE",
            conceptId: "physics-001",
            status: "PUBLISHED",
            version: 3,
            targetGrades: ["grade_9_12"],
            createdBy: "author-1",
            lastEditedBy: "editor-1",
            publishedAt: new Date("2026-02-01"),
            confidenceScore: 0.92,
            promptHash: "abc123",
            riskLevel: "LOW",
            intentProblem: "Students misunderstand projectile motion",
            intentMotivation: "Build intuition for parabolic trajectories",
            claims: [
              {
                claimRef: "C1",
                text: "Projectile follows parabolic path",
                bloomLevel: "UNDERSTAND",
              },
            ],
            claimSimulations: [
              {
                claimRef: "C1",
                simulationManifest: {
                  version: "2.0.0",
                  manifest: { entities: [], steps: [] },
                },
              },
            ],
            claimAnimations: [
              {
                claimRef: "C1",
                title: "Trajectory Arc",
                description: "Shows parabolic trajectory",
                type: "2d",
                duration: 10,
                config: { keyframes: [] },
              },
            ],
          },
        ])
        .mockResolvedValueOnce([]);

      const result = await service.backfillExperiences(DEFAULT_OPTIONS);

      expect(result.totalProcessed).toBe(1);
      expect(result.succeeded).toBe(1);
      expect(result.mappings[0].sourceType).toBe("experience");
      expect(result.mappings[0].sourceId).toBe("exp-1");

      // Asset created with correct mapping
      expect(prisma.contentAsset.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            assetType: "EXPLAINER",
            legacyExperienceId: "exp-1",
            status: "PUBLISHED",
            domain: "SCIENCE",
          }),
        }),
      );

      // Revision snapshot created
      expect(prisma.contentAssetRevision.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            version: 1,
            changeNote: "Backfilled from LearningExperience",
          }),
        }),
      );

      // Simulation manifest backfilled
      expect(prisma.artifactManifest.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            manifestType: "SIMULATION",
            version: "2.0.0",
            claimRef: "C1",
          }),
        }),
      );

      // Animation manifest backfilled
      expect(prisma.artifactManifest.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            manifestType: "ANIMATION",
            claimRef: "C1",
          }),
        }),
      );
    });

    it("should skip already-migrated experiences", async () => {
      prisma.learningExperience.findMany
        .mockResolvedValueOnce([
          {
            id: "exp-1",
            tenantId: "tenant-1",
            title: "Test",
            domain: "MATH",
            status: "DRAFT",
            createdBy: "a",
            claims: [],
            claimSimulations: [],
            claimAnimations: [],
          },
        ])
        .mockResolvedValueOnce([]);
      prisma.contentAsset.findFirst.mockResolvedValue({ id: "ca-existing" });

      const result = await service.backfillExperiences(DEFAULT_OPTIONS);

      expect(result.skipped).toBe(1);
      expect(prisma.contentAsset.create).not.toHaveBeenCalled();
    });
  });

  describe("backfillAll", () => {
    it("should run both module and experience backfills", async () => {
      // Both return empty for simplicity
      const result = await service.backfillAll(DEFAULT_OPTIONS);

      expect(result.modules).toBeDefined();
      expect(result.experiences).toBeDefined();
      expect(result.modules.totalProcessed).toBe(0);
      expect(result.experiences.totalProcessed).toBe(0);
    });
  });

  describe("generateReport", () => {
    it("should run backfill in dry-run mode", async () => {
      prisma.module.findMany
        .mockResolvedValueOnce([
          {
            id: "mod-1",
            tenantId: "tenant-1",
            title: "Report Test",
            domain: "MATH",
            status: "DRAFT",
          },
        ])
        .mockResolvedValueOnce([]);

      const report = await service.generateReport("tenant-1");

      expect(report.modules.succeeded).toBe(1);
      expect(report.modules.mappings[0].assetId).toContain("dry-run");
      // No actual writes
      expect(prisma.contentAsset.create).not.toHaveBeenCalled();
    });
  });

  describe("slug generation", () => {
    it("should create URL-safe slugs from titles", async () => {
      prisma.module.findMany
        .mockResolvedValueOnce([
          {
            id: "mod-1",
            tenantId: "tenant-1",
            title: "Algebra & Geometry (Advanced!)",
            domain: "MATH",
            status: "DRAFT",
            authorId: "a",
          },
        ])
        .mockResolvedValueOnce([]);

      const result = await service.backfillModules(DEFAULT_OPTIONS);

      expect(result.mappings[0].assetSlug).toBe("algebra-geometry-advanced");
    });
  });
});
