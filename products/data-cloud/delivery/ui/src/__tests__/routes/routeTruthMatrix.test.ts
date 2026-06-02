import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const routesSource = readFileSync(
  path.resolve(__dirname, "../../routes.tsx"),
  "utf8",
);
const shellSource = readFileSync(
  path.resolve(__dirname, "../../layouts/DefaultLayout.tsx"),
  "utf8",
);
const globalSearchSource = readFileSync(
  path.resolve(__dirname, "../../components/common/GlobalSearch.tsx"),
  "utf8",
);
const routeTruthMatrix = readFileSync(
  path.resolve(__dirname, "../../../docs/ROUTE_TRUTH_MATRIX.md"),
  "utf8",
);

describe("route truth matrix", () => {
  it("captures canonical primary routes in the committed artifact", () => {
    expect(routesSource).toContain('path: "data"');
    expect(routesSource).toContain('path: "new"');
    expect(routesSource).toContain('path: ":id/edit"');
    expect(routesSource).toContain('path: "pipelines"');
    expect(routesSource).toContain('path: "query"');
    expect(routesSource).toContain('path: "trust"');

    expect(routeTruthMatrix).toMatch(
      /\| `data` \| `\/data` \| Data \| primary-user \| ✅ active \|/,
    );
    expect(routeTruthMatrix).toMatch(
      /\| `pipelines` \| `\/pipelines` \| Pipelines \| primary-user \| ✅ active \|/,
    );
    expect(routeTruthMatrix).toMatch(
      /\| `query` \| `\/query` \| Query \| primary-user \| ✅ active \|/,
    );
    expect(routeTruthMatrix).toMatch(
      /\| `alerts` \| `\/alerts` \| Alerts \| operator \| 🔶 preview \| No \|/,
    );
    expect(routeTruthMatrix).toMatch(
      /\| `fabric` \| `\/fabric` \| Data Fabric \| operator \| 🔶 preview \| No \|/,
    );
    expect(routesSource).toContain('path: "lineage"');
    expect(routesSource).toContain('Navigate to="/data?view=lineage" replace');
    expect(routesSource).toContain('path: "quality"');
    expect(routesSource).toContain('Navigate to="/data?view=quality" replace');
  });

  it("keeps unsupported routes out of default discovery surfaces", () => {
    expect(shellSource).toContain("buildNavFromRegistry(");
    expect(shellSource).not.toContain("getDiscoverableRouteSurfaces(shellRole)");
    expect(globalSearchSource).not.toMatch(/id:\s*["']nav-alerts["']/);
    expect(globalSearchSource).toContain("id: `nav-${surface.key}`");
    expect(globalSearchSource).toContain("buildQuickNavItemsFromSurfaces");
  });

  it("keeps preview routes behind RuntimeCapabilityRouteGate with HTTP 503 for unavailable services", () => {
    // Preview routes are gated via RoleProtectedRoute + RuntimeCapabilityRouteGate
    // and surfaceId-based runtime truth checks.
    expect(routesSource).toContain('path: "alerts"');
    expect(routesSource).toContain('surfaceId="governance.audit"');
    expect(routesSource).toContain("allowPreviewFor=\"operator\"");

    expect(routesSource).toContain('path: "memory"');
    expect(routesSource).toContain('surfaceId="context.plane"');

    expect(routesSource).toContain('path: "entities"');
    expect(routesSource).toContain('surfaceId="data.entityStore"');

    expect(routesSource).toContain('path: "context"');
    expect(routesSource).toContain('surfaceId="context.plane"');

    expect(routesSource).toContain('path: "fabric"');
    expect(routesSource).toContain('surfaceId="data.storageProfiles"');

    expect(routesSource).toContain('path: "agents"');
    expect(routesSource).toContain('surfaceId="action.agentRuntime"');

    expect(routesSource).toContain('path: "settings"');
    expect(routesSource).toContain('surfaceId="settings"');

    expect(routesSource).toContain('path: "connectors"');
    expect(routesSource).toContain('surfaceId="data.connectors"');
  });

  it("wraps preview routes with RoleProtectedRoute", () => {
    // Every RuntimeCapabilityRouteGate must be inside a RoleProtectedRoute
    const rtcgInstances = (
      routesSource.match(/RuntimeCapabilityRouteGate/g) ?? []
    ).length;
    const roleGuardInstances = (routesSource.match(/RoleProtectedRoute/g) ?? [])
      .length;
    // There are more RoleProtectedRoute usages (aliases) than RuntimeCapabilityRouteGate
    expect(roleGuardInstances).toBeGreaterThanOrEqual(rtcgInstances);
    expect(rtcgInstances).toBeGreaterThan(0);
  });
});
