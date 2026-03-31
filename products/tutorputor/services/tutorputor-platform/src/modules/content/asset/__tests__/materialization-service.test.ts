import { beforeEach, describe, expect, it, vi } from "vitest";
import { AssetMaterializationService } from "../materialization-service";

function makePrisma() {
  return {
    contentAsset: {
      findFirst: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockImplementation((args: any) => ({
        id: "asset-1",
        currentVersion: args.data.currentVersion,
        slug: args.data.slug,
        ...args.data,
      })),
      update: vi.fn().mockResolvedValue({ id: "asset-1", currentVersion: 2 }),
    },
    contentBlock: {
      create: vi.fn().mockResolvedValue({ id: "block-1" }),
      deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
    },
    artifactManifest: {
      create: vi.fn().mockResolvedValue({ id: "manifest-1" }),
      deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
    },
    contentAssetRevision: {
      create: vi.fn().mockResolvedValue({ id: "rev-1" }),
    },
  };
}

describe("AssetMaterializationService", () => {
  let prisma: ReturnType<typeof makePrisma>;
  let service: AssetMaterializationService;

  beforeEach(() => {
    prisma = makePrisma();
    service = new AssetMaterializationService(prisma as never);
  });

  it("materializes worked-example output into a canonical asset with blocks and manifest", async () => {
    const result = await service.materializeJobOutput({
      tenantId: "tenant-1",
      requestId: "req-1",
      jobId: "job-1",
      jobType: "worked_example",
      requestTitle: "Newton's Laws",
      requestDescription: "Understand inertia",
      domain: "physics",
      conceptId: "concept-1",
      targetGrades: ["grade_9_12"],
      requestedBy: "author-1",
      targetRef: "req-1/worked_example",
      outputData: {
        examples: [
          {
            title: "Seatbelt Example",
            description: "A driver stops suddenly.",
            content: "The passenger keeps moving forward.",
            solution_content: "Seatbelts counter inertia.",
          },
        ],
      },
    });

    expect(result.assetId).toBe("asset-1");
    expect(result.assetType).toBe("example_set");
    expect(result.manifestCount).toBe(1);
    expect(prisma.contentAsset.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          assetType: "EXAMPLE_SET",
          domain: "SCIENCE",
          status: "DRAFT",
        }),
      }),
    );
    expect(prisma.contentBlock.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          blockType: "WORKED_EXAMPLE",
        }),
      }),
    );
    expect(prisma.artifactManifest.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          manifestType: "WORKED_EXAMPLE",
          generationId: "req-1",
        }),
      }),
    );
  });

  it("updates an existing asset by replacing blocks/manifests and bumping the revision", async () => {
    prisma.contentAsset.findFirst.mockResolvedValue({
      id: "asset-1",
      currentVersion: 1,
      slug: "existing-simulation",
    });

    const result = await service.materializeJobOutput({
      tenantId: "tenant-1",
      requestId: "req-1",
      jobId: "job-2",
      jobType: "simulation",
      requestTitle: "Projectile Motion",
      domain: "science",
      targetGrades: ["grade_9_12"],
      requestedBy: "author-1",
      targetRef: "req-1/simulation",
      existingAssetId: "asset-1",
      outputData: {
        simulations: [
          {
            id: "sim-1",
            title: "Projectile Motion Lab",
            description: "Adjust velocity and launch angle.",
          },
        ],
      },
    });

    expect(result.created).toBe(false);
    expect(result.currentVersion).toBe(2);
    expect(prisma.contentAsset.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: "asset-1" },
        data: expect.objectContaining({
          currentVersion: 2,
          assetType: "SIMULATION",
        }),
      }),
    );
    expect(prisma.contentBlock.deleteMany).toHaveBeenCalledWith({
      where: { assetId: "asset-1" },
    });
    expect(prisma.artifactManifest.deleteMany).toHaveBeenCalledWith({
      where: { assetId: "asset-1" },
    });
    expect(prisma.contentAssetRevision.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          assetId: "asset-1",
          version: 2,
        }),
      }),
    );
  });
});
