import { describe, expect, it } from "vitest";
import {
  createRouteContractGenerator,
  parseProductRouteContract,
  validateProductRouteContract,
  type ProductRouteContract,
} from "../../index.js";

function makeRouteContract(overrides: Partial<ProductRouteContract> = {}): ProductRouteContract {
  return {
    version: "2026.05.28",
    roleOrder: {
      patient: 10,
      clinician: 50,
      admin: 100,
    },
    routes: [
      {
        path: "/dashboard",
        label: "Dashboard",
        description: "Patient dashboard",
        group: "dashboard",
        minimumRole: "patient",
        stability: "stable",
        metadata: {
          apiEndpoint: "/api/phr/dashboard",
          policyId: "phr.dashboard.read",
          testId: "phr-dashboard-route",
        },
        apiContractId: "phr.api.dashboard.v1",
        dtoSchemaId: "phr.dto.dashboard.v1",
        pluginDependencies: ["kernel:policy", "kernel:audit"],
        auditRequirement: "phi-access",
        phiSensitivity: "phi",
        cachePolicy: "private-session",
        offlinePolicy: "encrypted-ttl",
        actions: [
          {
            id: "refresh-dashboard",
            label: "Refresh",
            endpoint: "/api/phr/dashboard",
            method: "GET",
            policyId: "phr.dashboard.read",
          },
        ],
      },
      {
        path: "/provider/worklist",
        label: "Provider worklist",
        description: "Provider-only worklist",
        group: "care",
        minimumRole: "clinician",
        stability: "hidden",
        metadata: {
          featureFlag: "phr.provider.worklist",
        },
      },
      {
        path: "/admin/release",
        label: "Release cockpit",
        description: "Release readiness cockpit",
        group: "governance",
        minimumRole: "admin",
        stability: "blocked",
      },
      {
        path: "/appointments/follow-up",
        label: "Follow-up appointments",
        description: "Deferred follow-up appointment scheduling",
        group: "care",
        minimumRole: "patient",
        stability: "deferred",
      },
      {
        path: "/legacy/documents",
        label: "Legacy documents",
        description: "Removed document workflow",
        group: "care",
        minimumRole: "patient",
        stability: "removed",
      },
    ],
    ...overrides,
  };
}

describe("ProductRouteContractSchema", () => {
  it("parses route contracts with supported lifecycle route states", () => {
    const contract = parseProductRouteContract(makeRouteContract());

    expect(contract.routes.map((route) => route.stability)).toEqual([
      "stable",
      "hidden",
      "blocked",
      "deferred",
      "removed",
    ]);
    expect(validateProductRouteContract(contract)).toBe(true);
    expect(contract.routes[0]).toMatchObject({
      apiContractId: "phr.api.dashboard.v1",
      dtoSchemaId: "phr.dto.dashboard.v1",
      pluginDependencies: ["kernel:policy", "kernel:audit"],
      auditRequirement: "phi-access",
      phiSensitivity: "phi",
      cachePolicy: "private-session",
      offlinePolicy: "encrypted-ttl",
    });
  });

  it("rejects stable routes that omit apiEndpoint, policyId, or testId metadata", () => {
    const invalidContract = makeRouteContract({
      routes: [
        {
          path: "/records",
          label: "Records",
          description: "Patient records",
          group: "care",
          minimumRole: "patient",
          stability: "stable",
          metadata: {
            apiEndpoint: "/api/phr/records",
          },
        },
      ],
    });

    expect(validateProductRouteContract(invalidContract)).toBe(false);
    expect(() => parseProductRouteContract(invalidContract)).toThrow(/policyId, testId/);
  });

  it("rejects duplicate paths and roles outside the contract role order", () => {
    const invalidContract = makeRouteContract({
      routes: [
        {
          path: "/dashboard",
          label: "Dashboard",
          description: "Patient dashboard",
          group: "dashboard",
          minimumRole: "patient",
          stability: "stable",
          metadata: {
            apiEndpoint: "/api/phr/dashboard",
            policyId: "phr.dashboard.read",
            testId: "phr-dashboard-route",
          },
        },
        {
          path: "/dashboard",
          label: "Duplicate dashboard",
          description: "Duplicate route",
          group: "dashboard",
          minimumRole: "superuser",
          stability: "hidden",
        },
      ],
    });

    expect(validateProductRouteContract(invalidContract)).toBe(false);
    expect(() => parseProductRouteContract(invalidContract)).toThrow(/Duplicate route path/);
  });
});

describe("RouteContractGenerator", () => {
  it("emits TS, backend entitlement, docs, and capability projections from one contract", () => {
    const generated = createRouteContractGenerator(makeRouteContract()).generateAll();

    expect(generated.tsManifest.routes[0]?.metadata?.apiEndpoint).toBe("/api/phr/dashboard");
    expect(generated.backendEntitlement.routes[0]).toMatchObject({
      path: "/dashboard",
      apiEndpoint: "/api/phr/dashboard",
      policyId: "phr.dashboard.read",
      testId: "phr-dashboard-route",
      apiContractId: "phr.api.dashboard.v1",
      dtoSchemaId: "phr.dto.dashboard.v1",
      auditRequirement: "phi-access",
      phiSensitivity: "phi",
      cachePolicy: "private-session",
      offlinePolicy: "encrypted-ttl",
    });
    expect(generated.backendEntitlement.routes[0]?.pluginDependencies).toEqual(["kernel:policy", "kernel:audit"]);
    expect(generated.routeDocs.routes[0]?.metadata?.testId).toBe("phr-dashboard-route");
    expect(generated.routeDocs.routes[0]?.metadata?.apiContractId).toBe("phr.api.dashboard.v1");
    expect(generated.routeCapabilities.capabilities).toContainEqual(
      expect.objectContaining({
        path: "/dashboard",
        directLinkAllowed: true,
        discoverable: true,
        apiContractId: "phr.api.dashboard.v1",
      }),
    );
  });

  it("marks non-active route capabilities as not direct-link accessible", () => {
    const generated = createRouteContractGenerator(makeRouteContract()).generateRouteCapabilities();

    expect(generated.capabilities).toContainEqual(
      expect.objectContaining({
        path: "/provider/worklist",
        directLinkAllowed: false,
        discoverable: false,
      }),
    );
    expect(generated.capabilities).toContainEqual(
      expect.objectContaining({
        path: "/admin/release",
        directLinkAllowed: false,
        discoverable: false,
      }),
    );
    expect(generated.capabilities).toContainEqual(
      expect.objectContaining({
        path: "/appointments/follow-up",
        directLinkAllowed: false,
        discoverable: false,
      }),
    );
    expect(generated.capabilities).toContainEqual(
      expect.objectContaining({
        path: "/legacy/documents",
        directLinkAllowed: false,
        discoverable: false,
      }),
    );
  });
});
