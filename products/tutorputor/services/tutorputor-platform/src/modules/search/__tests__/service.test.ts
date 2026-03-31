import { beforeEach, describe, expect, it, vi } from "vitest";

import { SearchServiceImpl } from "../service.js";

describe("SearchServiceImpl", () => {
  let prisma: any;
  let service: SearchServiceImpl;

  beforeEach(() => {
    prisma = {
      module: {
        findMany: vi.fn().mockResolvedValue([
          {
            id: "module-1",
            slug: "physics-basics",
            title: "Physics Basics",
            description: "Intro to forces and motion",
            category: "science",
            difficulty: "beginner",
            tags: ["physics", "motion"],
          },
        ]),
      },
      learningPath: {
        findMany: vi.fn().mockResolvedValue([]),
      },
      thread: {
        findMany: vi.fn().mockResolvedValue([]),
      },
      $queryRaw: vi.fn().mockResolvedValue([{ "1": 1 }]),
    };

    service = new SearchServiceImpl(prisma);
  });

  it("can be imported without errors", async () => {
    const mod = await import("../../search/index.js").catch(() => null);
    expect(mod).not.toBeNull();
  });

  it("returns module results with slug metadata for learner navigation", async () => {
    const response = await service.search({
      tenantId: "tenant-1" as any,
      query: "physics basics",
    });

    expect(response.results).toHaveLength(1);
    expect(response.results[0]).toMatchObject({
      id: "module-1",
      type: "module",
      title: "Physics Basics",
      metadata: expect.objectContaining({
        slug: "physics-basics",
      }),
    });
    expect(response.results[0].score).toBeGreaterThan(0);
  });

  it("uses the slug as autocomplete identifier when present", async () => {
    const suggestions = await service.autocomplete(
      "tenant-1" as any,
      "physics",
      5,
    );

    expect(suggestions).toEqual([
      {
        text: "Physics Basics",
        type: "module",
        id: "physics-basics",
      },
    ]);
  });

  it("returns empty results when no modules match", async () => {
    prisma.module.findMany.mockResolvedValue([]);

    const response = await service.search({
      tenantId: "tenant-1" as any,
      query: "nonexistent",
    });

    expect(response.results).toEqual([]);
    expect(response.total).toBe(0);
  });
});
