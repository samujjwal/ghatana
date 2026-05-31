import type { SurfaceSignal } from "@/api/surfaces.service";
import { generateRouteActionGates } from "@/lib/routing/RuntimeRouteActionGateGenerator";
import { describe, expect, it } from "vitest";

describe("generateRouteActionGates", () => {
  it("generates route/action gates from runtime capability signals", () => {
    const capabilities: SurfaceSignal[] = [
      {
        key: "alert-triage",
        label: "Alert Triage",
        status: "LIVE",
        summary: "ACTIVE",
        ownerPlane: "test",
        requiredDependencies: [],
        dependencyProbes: [],
        tenantScope: "tenant",
        runtimeProfile: "test",
        limitations: "",
        actionsAllowed: [],
        rawValue: { status: "ACTIVE" },
      },
      {
        key: "event-stream",
        label: "Event Stream",
        status: "DEGRADED",
        summary: "DEGRADED",
        ownerPlane: "test",
        requiredDependencies: [],
        dependencyProbes: [],
        tenantScope: "tenant",
        runtimeProfile: "test",
        limitations: "",
        actionsAllowed: [],
        rawValue: { status: "DEGRADED" },
      },
      {
        key: "agentCatalog",
        label: "Agent Catalog",
        status: "UNAVAILABLE",
        summary: "UNAVAILABLE",
        ownerPlane: "test",
        requiredDependencies: [],
        dependencyProbes: [],
        tenantScope: "tenant",
        runtimeProfile: "test",
        limitations: "",
        actionsAllowed: [],
        rawValue: { status: "UNAVAILABLE" },
      },
    ];

    const generated = generateRouteActionGates(capabilities);
    const alertsRoute = generated.find((route) => route.path === "/alerts");
    const eventsRoute = generated.find((route) => route.path === "/events");

    expect(generated.length).toBeGreaterThan(0);
    expect(alertsRoute?.status).toBe("active");
    expect(
      eventsRoute?.actions.some((action) => action.status === "degraded"),
    ).toBe(true);
  });

  it("returns unknown status when route capabilities are not present", () => {
    const generated = generateRouteActionGates([]);
    const connectorsRoute = generated.find(
      (route) => route.path === "/connectors",
    );

    expect(connectorsRoute).toBeDefined();
    expect(connectorsRoute?.status).toBe("unknown");
  });
});
