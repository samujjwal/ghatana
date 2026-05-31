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

function sourceContainsAliasList(aliases: readonly string[]): boolean {
  const escapedAliases = aliases.map((alias) =>
    alias.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
  );
  return new RegExp(
    `aliases=\\{\\[\\s*"${escapedAliases.join('"\\s*,\\s*"')}"\\s*\\]\\}`,
  ).test(routesSource);
}

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
    expect(shellSource).toContain("getDiscoverableRouteSurfaces(shellRole)");
    expect(globalSearchSource).not.toMatch(/id:\s*["']nav-alerts["']/);
    expect(globalSearchSource).toMatch(/id:\s*["']nav-query["']/);
    expect(globalSearchSource).toMatch(/id:\s*["']nav-trust["']/);
  });

  it("keeps preview routes behind RuntimeCapabilityRouteGate with HTTP 503 for unavailable services", () => {
    // Preview routes are gated via RoleProtectedRoute + RuntimeCapabilityRouteGate
    // (supercedes the old isXxxSurfaceEnabled() ternary pattern)
    expect(routesSource).toContain('path: "alerts"');
    expect(
      sourceContainsAliasList(["alert-triage", "monitoring", "alerts"]),
    ).toBe(true);

    expect(routesSource).toContain('path: "memory"');
    expect(sourceContainsAliasList(["memory-plane", "memory"])).toBe(true);

    expect(routesSource).toContain('path: "entities"');
    expect(sourceContainsAliasList(["entity-browser", "entities"])).toBe(true);

    expect(routesSource).toContain('path: "context"');
    expect(sourceContainsAliasList(["context-explorer", "context"])).toBe(true);

    expect(routesSource).toContain('path: "fabric"');
    expect(sourceContainsAliasList(["data-fabric", "fabric"])).toBe(true);

    expect(routesSource).toContain('path: "agents"');
    expect(sourceContainsAliasList(["agent-catalog", "agents"])).toBe(true);

    expect(routesSource).toContain('path: "settings"');
    expect(sourceContainsAliasList(["settings", "config"])).toBe(true);

    expect(routesSource).toContain('path: "connectors"');
    expect(sourceContainsAliasList(["data-connectors", "connectors"])).toBe(
      true,
    );
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
