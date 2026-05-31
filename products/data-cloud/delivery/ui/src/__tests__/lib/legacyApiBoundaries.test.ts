import {
  AI_ENRICHMENT_SUGGESTION_BOUNDARY_MESSAGE,
  AI_QUERY_RECOMMENDATIONS_BOUNDARY_MESSAGE,
} from "@/lib/runtime-boundaries";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("../../lib/api/client", () => ({
  apiClient: mockApiClient,
}));

vi.mock("../../lib/api/collections", () => ({
  collectionsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("../../lib/api/workflows", () => ({
  workflowsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    execute: vi.fn(),
    getExecutions: vi.fn(),
  },
}));

vi.mock("../../lib/api/collection-data-client", () => ({
  collectionDataClient: {
    setBaseURL: vi.fn(),
    setTenantId: vi.fn(),
    listRecords: vi.fn(),
    createRecord: vi.fn(),
    updateRecord: vi.fn(),
    deleteRecord: vi.fn(),
  },
}));

import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import {
  convertNLToSQL,
  detectAnomalies,
  getEnrichmentSuggestions,
  getPipelineOptimisationHints,
  getQueryRecommendations,
  getSchemaSuggestions,
} from "../../lib/api/ai";

describe("canonical AI helper boundaries", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApiClient.post.mockResolvedValue({ ok: true });
    mockApiClient.get.mockResolvedValue([]);
  });

  it("routes supported AI helpers to canonical OpenAPI paths", async () => {
    await convertNLToSQL(TEST_TENANT_ID, {
      query: "orders by revenue",
      collectionName: "orders",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/analytics/suggest",
      { query: "orders by revenue", collectionName: "orders" },
      { params: { tenantId: TEST_TENANT_ID } },
    );

    await getSchemaSuggestions(TEST_TENANT_ID, {
      collectionName: "orders",
      currentSchema: { id: "string" },
      sampleData: [{ id: "1" }],
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/entities/orders/suggest",
      { currentSchema: { id: "string" }, sampleData: [{ id: "1" }] },
      { params: { tenantId: TEST_TENANT_ID } },
    );

    mockApiClient.post.mockResolvedValueOnce([
      {
        id: "anom-1",
        type: "spike",
        severity: "warning",
        metric: "count",
        timestamp: "2026-04-15T10:00:00Z",
        value: 120,
        expectedValue: 80,
        deviation: 40,
        description: "Entity count spiked above the expected baseline.",
        suggestedAction: "Inspect recent ingestion jobs.",
      },
    ]);
    await detectAnomalies(TEST_TENANT_ID, {
      collectionName: "orders",
      metrics: ["count"],
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/entities/orders/anomalies",
      { collectionName: "orders", metrics: ["count"] },
      { params: { tenantId: TEST_TENANT_ID } },
    );

    mockApiClient.post.mockResolvedValueOnce({
      pipelineId: "wf-1",
      hints: [
        {
          type: "performance",
          title: "Reduce repeated enrichment lookups",
          description:
            "Cache repeated upstream calls to reduce end-to-end latency.",
          confidence: 0.92,
          impact: "high",
          fallback: false,
        },
      ],
      generatedAt: "2026-04-14T10:35:00Z",
    });
    await getPipelineOptimisationHints("wf-1");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/action/pipelines/wf-1/optimise-hint",
      {},
    );
  });

  it("fails explicitly for unsupported legacy AI helpers", async () => {
    await expect(
      getEnrichmentSuggestions(TEST_TENANT_ID, {
        collectionName: "orders",
        entityId: "1",
      }),
    ).rejects.toThrow(AI_ENRICHMENT_SUGGESTION_BOUNDARY_MESSAGE);

    await expect(
      getQueryRecommendations(TEST_TENANT_ID, "orders", "select *"),
    ).rejects.toThrow(AI_QUERY_RECOMMENDATIONS_BOUNDARY_MESSAGE);
  });
});
