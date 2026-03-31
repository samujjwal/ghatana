import { describe, expect, it, vi } from "vitest";
import { paginate } from "./pagination.js";

describe("paginate", () => {
  it("returns trimmed items and the next cursor when more items exist", async () => {
    const findMany = vi.fn().mockResolvedValue([
      { id: "a", createdAt: new Date("2026-03-30T00:00:00Z") },
      { id: "b", createdAt: new Date("2026-03-29T00:00:00Z") },
      { id: "c", createdAt: new Date("2026-03-28T00:00:00Z") },
    ]);
    const count = vi.fn().mockResolvedValue(3);

    const result = await paginate(
      { findMany, count },
      { tenantId: "tenant-1" },
      { take: 2 },
      { orderField: "createdAt", includeTotalCount: true },
    );

    expect(result.items.map((item) => item.id)).toEqual(["a", "b"]);
    expect(result.hasMore).toBe(true);
    expect(result.nextCursor).toBe("b");
    expect(result.totalCount).toBe(3);
    expect(findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        take: 3,
        orderBy: { createdAt: "desc" },
      }),
    );
  });

  it("respects cursor pagination defaults", async () => {
    const findMany = vi.fn().mockResolvedValue([{ id: "b" }]);

    const result = await paginate(
      { findMany },
      { tenantId: "tenant-1" },
      { cursor: "a", take: 1 },
      { orderField: "id" },
    );

    expect(result.hasMore).toBe(false);
    expect(result.nextCursor).toBeUndefined();
    expect(findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        cursor: { id: "a" },
        skip: 1,
      }),
    );
  });
});
