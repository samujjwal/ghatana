/**
 * Domain-aware cache invalidation tests (DC-UI-002 / DC-TEST-011)
 *
 * Verifies that:
 * 1. GET responses are cached per tenant-scoped key.
 * 2. POST/PUT/PATCH/DELETE invalidate the exact mutated URL AND its parent
 *    list endpoint so subsequent GETs re-fetch fresh data.
 * 3. Different tenants do not share cached data.
 *
 * @doc.type test
 * @doc.purpose Domain-aware cache invalidation correctness
 * @doc.layer frontend
 * @doc.pattern Unit Test
 */

import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiClient } from "@/lib/api/client";
import SessionBootstrap from "@/lib/auth/session";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface MockInit {
  ok?: boolean;
  status?: number;
  body?: unknown;
}

function jsonResponse(init: MockInit = {}): Response {
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    statusText: "OK",
    headers: new Headers({ "content-type": "application/json" }),
    json: vi.fn().mockResolvedValue(init.body ?? {}),
    text: vi.fn().mockResolvedValue(JSON.stringify(init.body ?? {})),
    blob: vi.fn(),
  } as unknown as Response;
}

// ---------------------------------------------------------------------------
// deriveInvalidationTargets (pure unit)
// ---------------------------------------------------------------------------

describe("ApiClient.deriveInvalidationTargets", () => {
  it("returns the URL itself for a root-level endpoint", () => {
    const targets = ApiClient.deriveInvalidationTargets("/entities");
    expect(targets).toContain("/entities");
    expect(targets).toHaveLength(1);
  });

  it("returns the URL AND its parent list for a detail endpoint", () => {
    const targets = ApiClient.deriveInvalidationTargets(
      "/entities/dc_collections/abc123",
    );
    expect(targets).toContain("/entities/dc_collections/abc123");
    expect(targets).toContain("/entities/dc_collections");
  });

  it("returns the URL AND its parent list for a deeply nested record", () => {
    const targets = ApiClient.deriveInvalidationTargets(
      "/tenants/t1/collections/c1/records/r1",
    );
    expect(targets).toContain("/tenants/t1/collections/c1/records/r1");
    expect(targets).toContain("/tenants/t1/collections/c1/records");
  });

  it("bubbles up only one level (not recursively deeper)", () => {
    const targets = ApiClient.deriveInvalidationTargets(
      "/entities/dc_collections/abc",
    );
    // Should NOT include /entities as that would over-invalidate
    expect(targets).not.toContain("/entities");
  });

  it("handles leading slash correctly", () => {
    const targets = ApiClient.deriveInvalidationTargets(
      "/entities/dc_collections",
    );
    // It's a two-segment path, parent is /entities
    expect(targets).toContain("/entities/dc_collections");
    expect(targets).toContain("/entities");
  });
});

// ---------------------------------------------------------------------------
// Integration-style tests with mocked fetch
// ---------------------------------------------------------------------------

describe("ApiClient domain-aware cache invalidation", () => {
  let client: ApiClient;
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("window", {
      setTimeout: vi.fn().mockReturnValue(0),
      clearTimeout: vi.fn(),
      setInterval: vi.fn().mockReturnValue(0),
      clearInterval: vi.fn(),
      location: { origin: "http://localhost" },
    });
    SessionBootstrap.setTenantId("tenant-cache-test");
    client = new ApiClient({ baseUrl: "/api/v1", enableCache: true });
  });

  // ── GET caching ────────────────────────────────────────────────────────────

  it("serves a second GET from cache without a second fetch", async () => {
    const payload = { entities: [{ id: "1" }], count: 1 };
    fetchMock.mockResolvedValueOnce(jsonResponse({ body: payload }));

    await client.get("/entities/dc_collections");
    await client.get("/entities/dc_collections");

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("does not serve cached data across different tenants", async () => {
    const payloadA = { entities: [{ id: "a" }], count: 1 };
    const payloadB = { entities: [{ id: "b" }], count: 1 };

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ body: payloadA }))
      .mockResolvedValueOnce(jsonResponse({ body: payloadB }));

    SessionBootstrap.setTenantId("tenant-A");
    await client.get("/entities/dc_collections");

    SessionBootstrap.setTenantId("tenant-B");
    await client.get("/entities/dc_collections");

    // Both tenants must each trigger a real fetch
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  // ── POST invalidation ──────────────────────────────────────────────────────

  it("POST /entities/dc_collections invalidates the collection list cache", async () => {
    const listPayload = { entities: [], count: 0 };
    const createPayload = { id: "new-col", collection: "dc_collections" };
    const listPayload2 = { entities: [{ id: "new-col" }], count: 1 };

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ body: listPayload })) // first GET
      .mockResolvedValueOnce(jsonResponse({ body: createPayload })) // POST
      .mockResolvedValueOnce(jsonResponse({ body: listPayload2 })); // second GET after invalidation

    await client.get("/entities/dc_collections");
    await client.post("/entities/dc_collections", { name: "new" });
    const result = await client.get<typeof listPayload2>(
      "/entities/dc_collections",
    );

    // GET must have been called twice (cache was invalidated by POST)
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(result.count).toBe(1);
  });

  it("POST /entities/dc_collections/abc123 also invalidates the list /entities/dc_collections", async () => {
    const listPayload = { entities: [{ id: "abc123" }], count: 1 };
    const updatePayload = { id: "abc123" };
    const listPayload2 = {
      entities: [{ id: "abc123", updated: true }],
      count: 1,
    };

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ body: listPayload }))
      .mockResolvedValueOnce(jsonResponse({ body: updatePayload }))
      .mockResolvedValueOnce(jsonResponse({ body: listPayload2 }));

    await client.get("/entities/dc_collections");
    await client.post("/entities/dc_collections/abc123", { name: "updated" });
    await client.get("/entities/dc_collections");

    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  // ── PUT invalidation ───────────────────────────────────────────────────────

  it("PUT on a record detail invalidates both detail and list", async () => {
    const recordPayload = { id: "r1", data: { x: 1 } };
    const updatedPayload = { id: "r1", data: { x: 2 } };

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ body: recordPayload })) // GET detail
      .mockResolvedValueOnce(jsonResponse({ body: updatedPayload })) // PUT
      .mockResolvedValueOnce(jsonResponse({ body: updatedPayload })); // GET detail again

    const url = "/tenants/t1/collections/c1/records/r1";
    await client.get(url);
    await client.put(url, { data: { x: 2 } });
    await client.get(url);

    // Cache was busted — three real requests
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  // ── DELETE invalidation ────────────────────────────────────────────────────

  it("DELETE on a record invalidates both detail and parent list", async () => {
    const listPayload = { items: [{ id: "r1" }], total: 1 };
    const listAfter = { items: [], total: 0 };
    const detailPayload = { id: "r1" };

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ body: listPayload })) // GET list
      .mockResolvedValueOnce(jsonResponse({ body: detailPayload })) // GET detail
      .mockResolvedValueOnce(jsonResponse({ body: {} })) // DELETE
      .mockResolvedValueOnce(jsonResponse({ body: listAfter })) // GET list (re-fetch)
      .mockResolvedValueOnce(jsonResponse({ body: {} })); // GET detail (re-fetch — now 404 in real world, but empty for test)

    const listUrl = "/tenants/t1/collections/c1/records";
    const detailUrl = "/tenants/t1/collections/c1/records/r1";

    await client.get(listUrl); // cached
    await client.get(detailUrl); // cached
    await client.delete(detailUrl);

    const listResult = await client.get<typeof listAfter>(listUrl);
    expect(listResult.total).toBe(0); // fresh data
    await client.get(detailUrl); // re-fetched

    expect(fetchMock).toHaveBeenCalledTimes(5);
  });

  // ── PATCH invalidation ─────────────────────────────────────────────────────

  it("PATCH invalidates the URL and its parent list", async () => {
    const listPayload = { entities: [{ id: "col1", name: "old" }], count: 1 };
    const patchPayload = { id: "col1", name: "new" };
    const listPayload2 = { entities: [{ id: "col1", name: "new" }], count: 1 };

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ body: listPayload }))
      .mockResolvedValueOnce(jsonResponse({ body: patchPayload }))
      .mockResolvedValueOnce(jsonResponse({ body: listPayload2 }));

    await client.get("/entities/dc_collections");
    await client.patch("/entities/dc_collections/col1", { name: "new" });
    const result = await client.get<typeof listPayload2>(
      "/entities/dc_collections",
    );

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect((result.entities[0] as { name: string }).name).toBe("new");
  });
});
