import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
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

import { lineageService } from "../../api/lineage.service";

describe("lineageService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("maps canonical lineage graph responses into the UI graph model", async () => {
    mockApiClient.get.mockResolvedValue({
      collection: "orders",
      tenantId: TEST_TENANT_ID,
      direction: "BOTH",
      timestamp: "2026-04-15T12:00:00Z",
      dag: {
        nodes: [
          {
            id: "orders",
            type: "DATASET",
            name: "orders",
            role: "root",
            metadata: {},
          },
          {
            id: "orders-raw",
            type: "DATASET",
            name: "orders_raw",
            role: "upstream",
            metadata: {},
          },
        ],
        edges: [
          { source: "orders-raw", target: "orders", type: "DERIVES_FROM" },
        ],
      },
      upstreamCount: 1,
      downstreamCount: 0,
    });

    const graph = await lineageService.getLineage("orders");

    expect(mockApiClient.get).toHaveBeenCalledWith("/lineage/orders", {
      params: { direction: "BOTH" },
    });
    expect(graph).toEqual({
      nodes: [
        {
          id: "orders",
          type: "DATASET",
          name: "orders",
          metadata: { role: "root" },
        },
        {
          id: "orders-raw",
          type: "DATASET",
          name: "orders_raw",
          metadata: { role: "upstream" },
        },
      ],
      edges: [{ source: "orders-raw", target: "orders", type: "DERIVES_FROM" }],
      rootNode: "orders",
    });
  });

  it("maps canonical impact responses into the UI impact model", async () => {
    mockApiClient.get.mockResolvedValue({
      collection: "orders",
      tenantId: TEST_TENANT_ID,
      impactLevel: "MEDIUM",
      affectedCount: 2,
      affectedCollections: ["orders_dashboard", "orders_ml_features"],
      timestamp: "2026-04-15T12:01:00Z",
    });

    const impact = await lineageService.getImpactAnalysis("orders");

    expect(mockApiClient.get).toHaveBeenCalledWith("/lineage/orders/impact");
    expect(impact.affectedDatasets).toBe(2);
    expect(impact.details[0]).toEqual({
      id: "orders_dashboard",
      type: "DATASET",
      name: "orders_dashboard",
      impact: "DIRECT",
      distance: 1,
    });
  });
});
