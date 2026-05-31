import { beforeEach, describe, expect, it, vi } from "vitest";
import { CONTEXT_SURFACE_BOUNDARY_MESSAGE } from "../../lib/runtime-boundaries";

const { mockApiClient, mockIsContextSurfaceEnabled } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  mockIsContextSurfaceEnabled: vi.fn(() => true),
}));

vi.mock("../../lib/api/client", () => ({
  apiClient: mockApiClient,
}));
vi.mock("../../lib/feature-gates", () => ({
  isContextSurfaceEnabled: mockIsContextSurfaceEnabled,
}));

import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import {
  deleteContextKey,
  getCollectionContext,
  getContext,
  getContextSnapshot,
  putContextEntries,
  type CollectionContextResponse,
  type ContextResponse,
  type ContextSnapshot,
  type UpsertContextResponse,
} from "../../lib/api/context";

describe("context layer API client (P3.1.2)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsContextSurfaceEnabled.mockReturnValue(true);
  });

  it("fails closed before network calls when context surface gate is disabled", async () => {
    mockIsContextSurfaceEnabled.mockReturnValue(false);

    await expect(getContext()).rejects.toThrow(
      CONTEXT_SURFACE_BOUNDARY_MESSAGE,
    );
    await expect(putContextEntries({ locale: "en-US" })).rejects.toThrow(
      CONTEXT_SURFACE_BOUNDARY_MESSAGE,
    );

    expect(mockApiClient.get).not.toHaveBeenCalled();
    expect(mockApiClient.put).not.toHaveBeenCalled();
    expect(mockApiClient.delete).not.toHaveBeenCalled();
  });

  // ─── getContext ────────────────────────────────────────────────────────────

  it("calls GET /context and returns the response", async () => {
    const mockResponse: ContextResponse = {
      tenantId: TEST_TENANT_ID,
      entries: { locale: "en-US", "feature.dark-mode": true },
      count: 2,
      version: 3,
      requestId: "req-001",
    };
    mockApiClient.get.mockResolvedValueOnce(mockResponse);

    const result = await getContext();

    expect(mockApiClient.get).toHaveBeenCalledWith("/context");
    expect(result).toEqual(mockResponse);
  });

  it("calls GET /context/:collection and returns the unified collection context", async () => {
    const mockResponse: CollectionContextResponse = {
      collection: "orders",
      tenantId: "tenant-a",
      requestId: "req-ctx-orders",
      generatedAt: "2026-04-18T09:00:00Z",
      generationTimeMs: 12,
      schema: {
        fields: [
          { name: "customerId", type: "string", required: true },
          { name: "email", type: "string", required: false },
        ],
      },
      lineage: {
        upstream: ["raw_orders"],
        downstream: ["invoice_snapshots"],
      },
      governance: {
        retentionTier: "compliance",
        complianceStatus: "active",
        piiFields: ["email"],
      },
      freshness: {
        sampledAt: "2026-04-18T09:00:00Z",
        lastEntityUpdatedAt: "2026-04-18T08:58:00Z",
      },
      statisticalProfile: {
        entityCount: 2,
        sampleSize: 2,
        nullRates: { customerId: 0, email: 0.5 },
        topValues: {
          customerId: [{ value: "cust-1", count: 2 }],
          email: [{ value: "null", count: 1 }],
        },
      },
      relationships: [
        {
          id: "edge-1",
          source: "orders",
          target: "customers",
          type: "BELONGS_TO",
        },
      ],
    };
    mockApiClient.get.mockResolvedValueOnce(mockResponse);

    const result = await getCollectionContext("orders");

    expect(mockApiClient.get).toHaveBeenCalledWith("/context/orders", {
      params: undefined,
    });
    expect(result.lineage.upstream).toEqual(["raw_orders"]);
    expect(result.governance.piiFields).toEqual(["email"]);
  });

  it("passes the optional relationship depth when fetching collection context", async () => {
    const mockResponse: CollectionContextResponse = {
      collection: "orders",
      tenantId: "tenant-a",
      requestId: "req-ctx-depth",
      generatedAt: "2026-04-18T09:00:00Z",
      generationTimeMs: 8,
      schema: { fields: [] },
      lineage: { upstream: [], downstream: [] },
      governance: {
        retentionTier: "standard",
        complianceStatus: "default",
        piiFields: [],
      },
      freshness: { sampledAt: "2026-04-18T09:00:00Z" },
      statisticalProfile: {
        entityCount: 0,
        sampleSize: 0,
        nullRates: {},
        topValues: {},
      },
      relationshipDepth: 3,
      relationships: [],
    };
    mockApiClient.get.mockResolvedValueOnce(mockResponse);

    const result = await getCollectionContext("orders", { depth: 3 });

    expect(mockApiClient.get).toHaveBeenCalledWith("/context/orders", {
      params: { depth: 3 },
    });
    expect(result.relationshipDepth).toBe(3);
  });

  // ─── putContextEntries ─────────────────────────────────────────────────────

  it("calls PUT /context with an entries wrapper", async () => {
    const mockResponse: UpsertContextResponse = {
      tenantId: "tenant-a",
      upserted: 2,
      version: 4,
      updatedAt: "2026-04-15T10:00:00Z",
      requestId: "req-002",
    };
    mockApiClient.put.mockResolvedValueOnce(mockResponse);

    const result = await putContextEntries({ theme: "dark", locale: "fr-FR" });

    expect(mockApiClient.put).toHaveBeenCalledWith("/context", {
      entries: { theme: "dark", locale: "fr-FR" },
    });
    expect(result.upserted).toBe(2);
    expect(result.version).toBe(4);
  });

  // ─── deleteContextKey ──────────────────────────────────────────────────────

  it("calls DELETE /context/keys/:key with encoded key", async () => {
    mockApiClient.delete.mockResolvedValueOnce(undefined);

    await deleteContextKey("feature.dark-mode");

    expect(mockApiClient.delete).toHaveBeenCalledWith(
      "/context/keys/feature.dark-mode",
    );
  });

  it("encodes special characters in the key", async () => {
    mockApiClient.delete.mockResolvedValueOnce(undefined);

    await deleteContextKey("key with spaces");

    expect(mockApiClient.delete).toHaveBeenCalledWith(
      "/context/keys/key%20with%20spaces",
    );
  });

  // ─── getContextSnapshot ────────────────────────────────────────────────────

  it("calls GET /context/snapshot and returns versioned snapshot", async () => {
    const mockSnapshot: ContextSnapshot = {
      tenantId: "tenant-a",
      version: 5,
      count: 3,
      createdAt: "2026-04-12T08:00:00Z",
      snapshotAt: "2026-04-15T10:05:00Z",
      entries: { locale: "de-DE", theme: "light", "beta.feature": false },
      requestId: "req-003",
    };
    mockApiClient.get.mockResolvedValueOnce(mockSnapshot);

    const result = await getContextSnapshot();

    expect(mockApiClient.get).toHaveBeenCalledWith("/context/snapshot");
    expect(result.version).toBe(5);
    expect(result.count).toBe(3);
    expect(result.entries).toMatchObject({ locale: "de-DE" });
  });
});
