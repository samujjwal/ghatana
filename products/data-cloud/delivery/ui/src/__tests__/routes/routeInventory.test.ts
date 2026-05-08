/**
 * Route Inventory Test
 *
 * Enforces the complete canonical inventory of every route, alias, gate, and role
 * requirement in routes.tsx. This test must be updated whenever a route is
 * added, removed, or re-gated.
 *
 * Coverage:
 *   - Every primary canonical route is declared
 *   - Every preview/operator route is wrapped with RoleProtectedRoute
 *   - Every preview route gated by RuntimeCapabilityRouteGate has its aliases listed
 *   - Every compatibility alias route is present
 *   - No ungated page renders inside a RoleProtectedRoute-free container
 *   - Navigate redirects exist for deprecated paths
 */
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const routesSource = readFileSync(path.resolve(__dirname, '../../routes.tsx'), 'utf8');

// ─── Canonical primary routes ──────────────────────────────────────────────────

describe('canonical primary routes', () => {
  const primaryRoutes = [
    { path: 'data',      description: 'Data Explorer' },
    { path: 'pipelines', description: 'Pipeline / Workflow Management' },
    { path: 'query',     description: 'SQL Workspace' },
    { path: 'trust',     description: 'Trust Center / Governance' },
    { path: 'insights',  description: 'Insights / Analytics' },
    { path: 'alerts',    description: 'Alerts (operator)' },
    { path: 'operations', description: 'Operations Console (operator)' },
    { path: 'events',    description: 'Event Explorer' },
    { path: 'memory',    description: 'Memory Plane Viewer' },
    { path: 'entities',  description: 'Entity Browser' },
    { path: 'context',   description: 'Context Explorer' },
    { path: 'fabric',    description: 'Data Fabric' },
    { path: 'agents',    description: 'Agent Catalog' },
    { path: 'settings',  description: 'Settings' },
    { path: 'plugins',   description: 'Plugins' },
    { path: 'connectors', description: 'Data Connectors' },
  ] as const;

  for (const { path: routePath, description } of primaryRoutes) {
    it(`declares the ${description} route at path '${routePath}'`, () => {
      expect(routesSource).toContain(`path: '${routePath}'`);
    });
  }

  it('declares index (home) route for IntelligentHub', () => {
    expect(routesSource).toContain('index: true');
    expect(routesSource).toContain('IntelligentHub');
  });

  it('declares nested sub-routes for data (new, :id, :id/edit, :id/:view)', () => {
    expect(routesSource).toContain("path: 'new'");
    expect(routesSource).toContain("path: ':id'");
    expect(routesSource).toContain("path: ':id/edit'");
    expect(routesSource).toContain("path: ':id/:view'");
  });

  it('declares nested sub-routes for pipelines (new, :id, :id/edit)', () => {
    // ':id' and ':id/edit' are reused by data; presence of SmartWorkflowBuilder distinguishes pipelines
    expect(routesSource).toContain('SmartWorkflowBuilder');
    expect(routesSource).toContain('WorkflowDesigner');
  });

  it('declares 404 catch-all route', () => {
    expect(routesSource).toContain("path: '*'");
    expect(routesSource).toContain('NotFound');
  });
});

// ─── RoleProtectedRoute gating ────────────────────────────────────────────────

describe('RoleProtectedRoute gating', () => {
  /**
   * Every page route that is not the root index or data/pipelines/query
   * must be wrapped with RoleProtectedRoute to enforce authentication.
   */
  const routesThatMustBeRoleProtected = [
    'trust',
    'insights',
    'alerts',
    'operations',
    'events',
    'memory',
    'entities',
    'context',
    'fabric',
    'agents',
    'settings',
    'plugins',
    'connectors',
  ] as const;

  for (const routePath of routesThatMustBeRoleProtected) {
    it(`wraps '${routePath}' route with RoleProtectedRoute`, () => {
      // We verify by checking that the path and RoleProtectedRoute appear in close proximity.
      // The source has RoleProtectedRoute on the same element as the path.
      const pathIndex = routesSource.indexOf(`path: '${routePath}'`);
      expect(pathIndex, `'${routePath}' route not found in routes.tsx`).toBeGreaterThan(-1);

      // Slice a window around the path declaration and verify RoleProtectedRoute is present
      const window = routesSource.slice(Math.max(0, pathIndex - 20), pathIndex + 500);
      expect(window).toContain('RoleProtectedRoute');
    });
  }

  it('has at least as many RoleProtectedRoute usages as RuntimeCapabilityRouteGate usages', () => {
    const roleCount = (routesSource.match(/RoleProtectedRoute/g) ?? []).length;
    const gateCount = (routesSource.match(/RuntimeCapabilityRouteGate/g) ?? []).length;
    // Every gated route must also be role-protected; role-protected routes may omit the gate.
    expect(roleCount).toBeGreaterThanOrEqual(gateCount);
  });
});

// ─── RuntimeCapabilityRouteGate and alias inventory ───────────────────────────

describe('RuntimeCapabilityRouteGate and Runtime Truth aliases', () => {
  const gatedRoutes: Array<{ path: string; aliases: string[] }> = [
    { path: 'alerts',     aliases: ['alert-triage', 'monitoring', 'alerts'] },
    { path: 'memory',     aliases: ['memory-plane', 'memory'] },
    { path: 'entities',   aliases: ['entity-browser', 'entities'] },
    { path: 'context',    aliases: ['context-explorer', 'context'] },
    { path: 'fabric',     aliases: ['data-fabric', 'fabric'] },
    { path: 'agents',     aliases: ['agent-catalog', 'agents'] },
    { path: 'settings',   aliases: ['settings', 'config'] },
    { path: 'connectors', aliases: ['data-connectors', 'connectors'] },
  ];

  for (const { path: routePath, aliases } of gatedRoutes) {
    it(`'${routePath}' route is gated by RuntimeCapabilityRouteGate with aliases [${aliases.join(', ')}]`, () => {
      const aliasString = `aliases={['${aliases.join("', '")}']}`; 
      expect(routesSource).toContain(aliasString);
    });

    it(`'${routePath}' RuntimeCapabilityRouteGate has a DisabledSurfacePage fallback`, () => {
      const pathIndex = routesSource.indexOf(`path: '${routePath}'`);
      expect(pathIndex).toBeGreaterThan(-1);
      const window = routesSource.slice(pathIndex, pathIndex + 600);
      expect(window).toContain('fallback');
      expect(window).toContain('DisabledSurfacePage');
    });
  }

  it('counts RuntimeCapabilityRouteGate opening JSX tags matching expected gated routes', () => {
    // Only count JSX opening tags (<RuntimeCapabilityRouteGate …) — excludes closing tags and import lines.
    const openingTags = (routesSource.match(/<RuntimeCapabilityRouteGate/g) ?? []).length;
    expect(openingTags).toBe(gatedRoutes.length);
  });
});

// ─── Compatibility alias routes ───────────────────────────────────────────────

describe('compatibility alias routes', () => {
  const compatAliases: Array<{ alias: string; isNavigate: boolean }> = [
    { alias: 'dashboard',        isNavigate: false },
    { alias: 'hub',              isNavigate: false },
    { alias: 'collections',      isNavigate: false },
    { alias: 'collections/new',  isNavigate: false },
    { alias: 'collections/:id',  isNavigate: false },
    { alias: 'collections/:id/edit', isNavigate: false },
    { alias: 'datasets',         isNavigate: false },
    { alias: 'lineage',          isNavigate: true },
    { alias: 'quality',          isNavigate: true },
    { alias: 'workflows',        isNavigate: false },
    { alias: 'workflows/new',    isNavigate: false },
    { alias: 'workflows/:id',    isNavigate: false },
    { alias: 'sql',              isNavigate: false },
    { alias: 'governance',       isNavigate: false },
    { alias: 'brain',            isNavigate: false },
    { alias: 'dashboards',       isNavigate: false },
    { alias: 'cost',             isNavigate: false },
  ] as const;

  for (const { alias, isNavigate } of compatAliases) {
    it(`declares compat alias '${alias}'`, () => {
      expect(routesSource).toContain(`path: '${alias}'`);
    });

    if (isNavigate) {
      it(`'${alias}' is a Navigate redirect (not a page render)`, () => {
        const pathIndex = routesSource.indexOf(`path: '${alias}'`);
        const window = routesSource.slice(pathIndex, pathIndex + 200);
        expect(window).toContain('<Navigate');
        expect(window).toContain('replace');
      });
    } else {
      it(`non-redirect compat alias '${alias}' is wrapped with RoleProtectedRoute`, () => {
        const pathIndex = routesSource.indexOf(`path: '${alias}'`);
        expect(pathIndex).toBeGreaterThan(-1);
        const window = routesSource.slice(pathIndex, pathIndex + 300);
        expect(window).toContain('RoleProtectedRoute');
      });
    }
  }

  it('lineage redirects to /data?view=lineage', () => {
    expect(routesSource).toContain('path: \'lineage\', element: <Navigate to="/data?view=lineage" replace />');
  });

  it('quality redirects to /data?view=quality', () => {
    expect(routesSource).toContain('path: \'quality\', element: <Navigate to="/data?view=quality" replace />');
  });
});

// ─── Route security invariants ────────────────────────────────────────────────

describe('route security invariants', () => {
  it('imports RoleProtectedRoute from security components', () => {
    expect(routesSource).toContain("import { RoleProtectedRoute }");
    expect(routesSource).toContain("security/RoleProtectedRoute");
  });

  it('imports RuntimeCapabilityRouteGate from security components', () => {
    expect(routesSource).toContain("import { RuntimeCapabilityRouteGate }");
    expect(routesSource).toContain("security/RuntimeCapabilityRouteGate");
  });

  it('wraps routes within DefaultLayout via errorElement', () => {
    expect(routesSource).toContain('DefaultLayout');
    expect(routesSource).toContain('RouteErrorBoundary');
    expect(routesSource).toContain('errorElement');
  });

  it('does not expose raw page components without withSuspense', () => {
    // Every lazy-loaded page must be wrapped by withSuspense() to avoid un-handled suspense.
    // Verify that none of the lazy pages are used without going through withSuspense.
    const lazyPages = [
      'IntelligentHub',
      'DataExplorer',
      'WorkflowsPage',
      'SqlWorkspacePage',
      'TrustCenter',
      'InsightsPage',
      'AlertsPage',
      'OperationsConsole',
      'EventExplorerPage',
      'MemoryPlaneViewerPage',
      'EntityBrowserPage',
      'ContextExplorerPage',
      'DataFabricPage',
      'AgentPluginManagerPage',
      'SettingsPage',
      'PluginsPage',
      'NotFound',
    ] as const;

    for (const page of lazyPages) {
      // Count direct usages vs withSuspense usages.
      // Every usage must be inside withSuspense(...) — no bare `element: <PageName />`
      const bareUsagePattern = new RegExp(`element:\\s*<${page}\\s*/>`, 'g');
      const bareMatches = routesSource.match(bareUsagePattern) ?? [];
      expect(bareMatches, `${page} used without withSuspense`).toHaveLength(0);
    }
  });
});
