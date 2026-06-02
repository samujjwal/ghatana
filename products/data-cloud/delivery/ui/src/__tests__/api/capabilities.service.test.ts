import { TEST_TENANT_ID } from "@/__tests__/test-utils/tenants";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { apiClientGet } = vi.hoisted(() => ({
  apiClientGet: vi.fn(),
}));

vi.mock("../../lib/api/client", () => ({
  apiClient: {
    get: apiClientGet,
  },
}));

import {
  fetchSurfaceRegistry,
  getSurfaceSignal,
} from "../../api/surfaces.service";

describe("surfaces.service canonical behavior", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("normalizes surface statuses and preserves canonical endpoint", async () => {
    apiClientGet.mockResolvedValue({
      data: {
        surfaces: [
          {
            surfaceId: "analytics",
            path: "/insights",
            state: "ACTIVE",
            status: "ACTIVE",
            ownerPlane: "intelligence",
            requiredDependencies: [],
            dependencyProbes: [],
            tenantScope: "tenant",
            runtimeProfile: "local",
            lastCheckedAt: "2026-04-17T10:00:00Z",
            evidence: {},
            limitations: "",
            actionsAllowed: [],
            sortOrder: 10,
          },
          {
            surfaceId: "trino",
            path: "/query",
            state: "NOT_CONFIGURED",
            status: "NOT_CONFIGURED",
            ownerPlane: "data",
            requiredDependencies: [],
            dependencyProbes: [],
            tenantScope: "tenant",
            runtimeProfile: "local",
            lastCheckedAt: "2026-04-17T10:00:00Z",
            evidence: {},
            limitations: "",
            actionsAllowed: [],
            sortOrder: 20,
          },
        ],
        count: 2,
        generatedAt: "2026-04-17T10:00:00Z",
      },
      meta: {
        requestId: "req-surfaces",
        tenantId: TEST_TENANT_ID,
        timestamp: "2026-04-17T10:00:00Z",
        apiVersion: "v1",
      },
    });

    const snapshot = await fetchSurfaceRegistry();

    expect(apiClientGet).toHaveBeenCalledWith("/surfaces");
    expect(snapshot.requestId).toBe("req-surfaces");
    expect(snapshot.surfaces).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ key: "analytics", status: "LIVE" }),
        expect.objectContaining({ key: "trino", status: "MISCONFIGURED" }),
      ]),
    );
  });

  it("finds a surface by key or path alias", () => {
    const signal = getSurfaceSignal(
      [
        {
          key: "data.connectors",
          path: "/connectors",
          label: "Data Connectors",
          status: "LIVE",
          summary: "LIVE",
          ownerPlane: "data",
          requiredDependencies: [],
          dependencyProbes: [],
          tenantScope: "tenant",
          runtimeProfile: "production",
          limitations: "",
          actionsAllowed: [],
          rawValue: {},
        },
      ],
      ["/connectors"],
    );

    expect(signal?.key).toBe("data.connectors");
  });
});
