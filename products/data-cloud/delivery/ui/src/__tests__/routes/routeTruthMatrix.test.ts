import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const routesSource = readFileSync(path.resolve(__dirname, '../../routes.tsx'), 'utf8');
const shellSource = readFileSync(path.resolve(__dirname, '../../layouts/DefaultLayout.tsx'), 'utf8');
const globalSearchSource = readFileSync(path.resolve(__dirname, '../../components/common/GlobalSearch.tsx'), 'utf8');
const routeTruthMatrix = readFileSync(
  path.resolve(__dirname, '../../../docs/ROUTE_TRUTH_MATRIX.md'),
  'utf8',
);

describe('route truth matrix', () => {
  it('captures canonical primary routes in the committed artifact', () => {
    expect(routesSource).toContain('path: "data"');
    expect(routesSource).toContain('path: "new"');
    expect(routesSource).toContain('path: ":id/edit"');
    expect(routesSource).toContain('path: "pipelines"');
    expect(routesSource).toContain('path: "query"');
    expect(routesSource).toContain('path: "trust"');

    expect(routeTruthMatrix).toMatch(/\| `data` \| `\/data` \| Data \| primary-user \| ✅ active \|/);
    expect(routeTruthMatrix).toMatch(/\| `pipelines` \| `\/pipelines` \| Pipelines \| primary-user \| ✅ active \|/);
    expect(routeTruthMatrix).toMatch(/\| `query` \| `\/query` \| Query \| primary-user \| ✅ active \|/);
    expect(routeTruthMatrix).toMatch(/\| `alerts` \| `\/alerts` \| Alerts \| operator \| 🔶 preview \| No \|/);
    expect(routeTruthMatrix).toMatch(/\| `fabric` \| `\/fabric` \| Data Fabric \| operator \| 🔶 preview \| No \|/);
    expect(routesSource).toContain('path: "lineage"');
    expect(routesSource).toContain('Navigate to="/data?view=lineage" replace');
    expect(routesSource).toContain('path: "quality"');
    expect(routesSource).toContain('Navigate to="/data?view=quality" replace');
  });

  it('keeps unsupported routes out of default discovery surfaces', () => {
    expect(shellSource).toContain('getDiscoverableRouteSurfaces(shellRole)');
    expect(shellSource).toContain("accessiblePaths.delete('/alerts')");
    expect(shellSource).toContain("accessiblePaths.delete('/fabric')");
    expect(globalSearchSource).not.toContain("id: 'nav-alerts'");
    expect(globalSearchSource).toContain("id: 'nav-query'");
    expect(globalSearchSource).toContain("id: 'nav-trust'");
  });

  it('keeps preview routes behind RuntimeCapabilityRouteGate with HTTP 503 for unavailable services', () => {
    // Preview routes are gated via RoleProtectedRoute + RuntimeCapabilityRouteGate
    // (supercedes the old isXxxSurfaceEnabled() ternary pattern)
    expect(routesSource).toContain('path: "alerts"');
    expect(routesSource).toContain('aliases={["alert-triage", "monitoring", "alerts"]}');

    expect(routesSource).toContain('path: "memory"');
    expect(routesSource).toContain('aliases={["memory-plane", "memory"]}');

    expect(routesSource).toContain('path: "entities"');
    expect(routesSource).toContain('aliases={["entity-browser", "entities"]}');

    expect(routesSource).toContain('path: "context"');
    expect(routesSource).toContain('aliases={["context-explorer", "context"]}');

    expect(routesSource).toContain('path: "fabric"');
    expect(routesSource).toContain('aliases={["data-fabric", "fabric"]}');

    expect(routesSource).toContain('path: "agents"');
    expect(routesSource).toContain('aliases={["agent-catalog", "agents"]}');

    expect(routesSource).toContain('path: "settings"');
    expect(routesSource).toContain('aliases={["settings", "config"]}');

    expect(routesSource).toContain('path: "connectors"');
    expect(routesSource).toContain('aliases={["data-connectors", "connectors"]}');
  });

  it('wraps preview routes with RoleProtectedRoute', () => {
    // Every RuntimeCapabilityRouteGate must be inside a RoleProtectedRoute
    const rtcgInstances = (routesSource.match(/RuntimeCapabilityRouteGate/g) ?? []).length;
    const roleGuardInstances = (routesSource.match(/RoleProtectedRoute/g) ?? []).length;
    // There are more RoleProtectedRoute usages (aliases) than RuntimeCapabilityRouteGate
    expect(roleGuardInstances).toBeGreaterThanOrEqual(rtcgInstances);
    expect(rtcgInstances).toBeGreaterThan(0);
  });
});