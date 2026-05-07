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
    expect(routesSource).toContain("path: 'data'");
    expect(routesSource).toContain("path: 'new'");
    expect(routesSource).toContain("path: ':id/edit'");
    expect(routesSource).toContain("path: 'pipelines'");
    expect(routesSource).toContain("path: 'query'");
    expect(routesSource).toContain("path: 'trust'");

    expect(routeTruthMatrix).toContain('| `data` | `/data` | Data | primary-user | ✅ active |');
    expect(routeTruthMatrix).toContain('| `pipelines` | `/pipelines` | Pipelines | primary-user | ✅ active |');
    expect(routeTruthMatrix).toContain('| `query` | `/query` | Query | primary-user | ✅ active |');
    expect(routeTruthMatrix).toContain('| `alerts` | `/alerts` | Alerts | operator | 🚧 boundary |');
    expect(routeTruthMatrix).toContain('| `fabric` | `/fabric` | Data Fabric | operator | 🚧 boundary |');
    expect(routesSource).toContain("path: 'lineage', element: <Navigate to=\"/data?view=lineage\" replace />");
    expect(routesSource).toContain("path: 'quality', element: <Navigate to=\"/data?view=quality\" replace />");
  });

  it('keeps unsupported routes out of default discovery surfaces', () => {
    expect(shellSource).toContain("label: 'Alerts'");
    expect(shellSource).toContain("minimumShellRole: 'operator'");
    expect(shellSource).not.toContain("label: 'Fabric'");
    expect(globalSearchSource).not.toContain("id: 'nav-alerts'");
    expect(globalSearchSource).toContain("id: 'nav-query'");
    expect(globalSearchSource).toContain("id: 'nav-trust'");
  });

  it('keeps preview routes fail-closed behind feature gate fallbacks', () => {
    expect(routesSource).toContain("path: 'alerts'");
    expect(routesSource).toContain("element: isAlertsSurfaceEnabled() ? withSuspense(AlertsPage) : withSuspense(NotFound)");
    expect(routesSource).toContain("path: 'memory'");
    expect(routesSource).toContain("element: isMemorySurfaceEnabled() ? withSuspense(MemoryPlaneViewerPage) : withSuspense(NotFound)");
    expect(routesSource).toContain("path: 'entities'");
    expect(routesSource).toContain("element: isEntityBrowserSurfaceEnabled() ? withSuspense(EntityBrowserPage) : withSuspense(NotFound)");
    expect(routesSource).toContain("path: 'context'");
    expect(routesSource).toContain("element: isContextSurfaceEnabled() ? withSuspense(ContextExplorerPage) : withSuspense(NotFound)");
    expect(routesSource).toContain("path: 'fabric'");
    expect(routesSource).toContain("element: isFabricSurfaceEnabled() ? withSuspense(DataFabricPage) : withSuspense(NotFound)");
    expect(routesSource).toContain("path: 'agents'");
    expect(routesSource).toContain("element: isAgentCatalogSurfaceEnabled() ? withSuspense(AgentPluginManagerPage) : withSuspense(NotFound)");
    expect(routesSource).toContain("path: 'settings'");
    expect(routesSource).toContain("element: isSettingsSurfaceEnabled() ? withSuspense(SettingsPage) : withSuspense(NotFound)");
  });
});