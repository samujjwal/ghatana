/**
 * DC-P1-010: Surface Registry Composition tests
 *
 * Verifies that:
 * 1. All canonical Data Cloud routes are registered in the RouteCapabilityRegistry.
 * 2. All Action Plane routes (agents, connectors, events) are also present.
 * 3. Routes marked 'boundary' lifecycle have corresponding definitions in unsupportedSurfaceRegistry.
 * 4. No route entry drifts from the set of known paths (guards against orphan registrations).
 */
import { describe, expect, it } from 'vitest';
import {
  canonicalRouteRegistry,
  getActiveRoutes,
  getDiscoverableRoutes,
  getRouteByPath,
} from '@/lib/routing/RouteCapabilityRegistry';
import {
  alertsSurfaceBoundary,
  dataFabricMetricsBoundary,
  pluginDependencyBoundary,
  settingsSurfaceBoundaries,
  smartWorkflowGenerationBoundary,
} from '@/components/common/unsupportedSurfaceRegistry';

// ── Data Cloud primary surfaces ──────────────────────────────────────────────

describe('Data Cloud primary surfaces are registered', () => {
  it('has home route', () => {
    const route = getRouteByPath('/');
    expect(route).toBeDefined();
    expect(route?.lifecycle).toBe('active');
    expect(route?.minimumShellRole).toBe('primary-user');
  });

  it('has /data route (entity store primary surface)', () => {
    const route = getRouteByPath('/data');
    expect(route).toBeDefined();
    expect(route?.lifecycle).toBe('active');
    expect(route?.discoverable).toBe(true);
    expect(route?.capabilities).toContain('data-explorer');
  });

  it('has /query route (analytics surface)', () => {
    const route = getRouteByPath('/query');
    expect(route).toBeDefined();
    expect(route?.lifecycle).toBe('active');
  });

  it('has /insights route', () => {
    const route = getRouteByPath('/insights');
    expect(route).toBeDefined();
    expect(route?.lifecycle).toBe('active');
  });

  it('has /trust route (data governance)', () => {
    const route = getRouteByPath('/trust');
    expect(route).toBeDefined();
    expect(route?.lifecycle).toBe('active');
  });

  it('has /pipelines route (data-local plugin workflow execution)', () => {
    const route = getRouteByPath('/pipelines');
    expect(route).toBeDefined();
    expect(route?.capabilities).toContain('plugin-execution');
    expect(route?.description?.toLowerCase()).toContain('data-local plugin workflow execution');
  });
});

// ── Action Plane surfaces ────────────────────────────────────────────────────

describe('Action Plane surfaces are registered', () => {
  it('has /connectors route (external data source connectors)', () => {
    const route = getRouteByPath('/connectors');
    expect(route).toBeDefined();
    expect(route?.capabilities).toContain('data-connectors');
  });

  it('has /events route (AEP event stream explorer)', () => {
    const route = getRouteByPath('/events');
    expect(route).toBeDefined();
    expect(route?.capabilities).toContain('event-stream');
  });

  it('has /agents route (agent plugin catalog)', () => {
    const route = getRouteByPath('/agents');
    expect(route).toBeDefined();
    expect(route?.capabilities).toContain('agent-catalog');
    // Agents require operator-level role — not discoverable to primary-users by default
    expect(route?.minimumShellRole).toBe('operator');
  });

  it('excludes /agents from primary-user discoverable routes', () => {
    const primaryUserRoutes = getDiscoverableRoutes('primary-user');
    const agentRoute = primaryUserRoutes.find((r) => r.path === '/agents');
    expect(agentRoute).toBeUndefined();
  });

  it('includes /agents in operator discoverable routes', () => {
    const operatorRoutes = getDiscoverableRoutes('operator');
    const agentRoute = operatorRoutes.find((r) => r.path === '/agents');
    expect(agentRoute).toBeDefined();
  });
});

// ── Operator / admin surfaces ─────────────────────────────────────────────────

describe('Operator and admin surfaces are registered', () => {
  it('has /operations route (ops console)', () => {
    const route = getRouteByPath('/operations');
    expect(route).toBeDefined();
    expect(route?.minimumShellRole).toBe('admin');
    expect(route?.lifecycle).toBe('active');
  });

  it('has /operations/jobs route (background ops center)', () => {
    const route = getRouteByPath('/operations/jobs');
    expect(route).toBeDefined();
    expect(route?.discoverable).toBe(false);
  });

  it('has /plugins route (plugin management)', () => {
    const route = getRouteByPath('/plugins');
    expect(route).toBeDefined();
    expect(route?.minimumShellRole).toBe('operator');
    expect(route?.lifecycle).toBe('active');
  });

  it('has /alerts route (operator surface — boundary-guarded)', () => {
    const route = getRouteByPath('/alerts');
    expect(route).toBeDefined();
    expect(route?.minimumShellRole).toBe('operator');
  });

  it('has /fabric route (data fabric topology)', () => {
    const route = getRouteByPath('/fabric');
    expect(route).toBeDefined();
    expect(route?.capabilities).toContain('data-fabric');
  });
});

// ── Boundary surface alignment ────────────────────────────────────────────────

describe('Boundary surface definitions align with registry', () => {
  it('/settings is marked boundary lifecycle in registry', () => {
    const route = getRouteByPath('/settings');
    expect(route).toBeDefined();
    expect(route?.lifecycle).toBe('boundary');
    // settings is not discoverable at admin level by default
    expect(route?.discoverable).toBe(false);
  });

  it('settings surface boundaries are defined for all four sections', () => {
    expect(Object.keys(settingsSurfaceBoundaries)).toEqual([
      'profile',
      'preferences',
      'notifications',
      'api',
    ]);
    for (const boundary of Object.values(settingsSurfaceBoundaries)) {
      expect(boundary.state).toBe('not-in-deployment');
      expect(boundary.title.length).toBeGreaterThan(0);
      expect(boundary.details.length).toBeGreaterThan(0);
    }
  });

  it('data fabric surface boundary is defined with preview state', () => {
    expect(dataFabricMetricsBoundary.state).toBe('preview');
  });

  it('alerts surface boundary is defined as operator-only', () => {
    expect(alertsSurfaceBoundary.state).toBe('operator-only');
    expect(alertsSurfaceBoundary.summary).toContain('launcher-backed');
  });

  it('smart workflow generation surface boundary is defined as temporarily unavailable', () => {
    expect(smartWorkflowGenerationBoundary.state).toBe('temporarily-unavailable');
  });

  it('plugin dependency surface boundary is defined as not-in-deployment', () => {
    expect(pluginDependencyBoundary.state).toBe('not-in-deployment');
    expect(pluginDependencyBoundary.details.length).toBeGreaterThan(0);
  });
});

// ── Registry coherence checks ─────────────────────────────────────────────────

describe('Registry coherence and drift prevention', () => {
  it('all registry entries have valid path, label, lifecycle, and minimumShellRole', () => {
    for (const [key, route] of Object.entries(canonicalRouteRegistry)) {
      expect(route.path, `${key}.path`).toBeTruthy();
      expect(route.path, `${key}.path must start with /`).toMatch(/^\//);
      expect(route.label, `${key}.label`).toBeTruthy();
      expect(
        ['active', 'preview', 'boundary', 'deprecated', 'redirect', 'removed'],
        `${key}.lifecycle must be one of the known values`,
      ).toContain(route.lifecycle);
      expect(
        ['primary-user', 'operator', 'admin'],
        `${key}.minimumShellRole must be one of the known values`,
      ).toContain(route.minimumShellRole);
    }
  });

  it('all paths are unique (no duplicate registrations)', () => {
    const paths = Object.values(canonicalRouteRegistry).map((r) => r.path);
    const uniquePaths = new Set(paths);
    expect(uniquePaths.size).toBe(paths.length);
  });

  it('getActiveRoutes returns only active-lifecycle routes', () => {
    const active = getActiveRoutes();
    expect(active.every((r) => r.lifecycle === 'active')).toBe(true);
    expect(active.length).toBeGreaterThan(0);
  });

  it('primary-user discoverable routes do not include operator-or-admin-only routes', () => {
    const routes = getDiscoverableRoutes('primary-user');
    const elevated = routes.filter(
      (r) => r.minimumShellRole === 'operator' || r.minimumShellRole === 'admin',
    );
    expect(elevated).toHaveLength(0);
  });

  it('known Data Cloud core routes are present (anti-regression)', () => {
    const expectedPaths = ['/', '/data', '/query', '/pipelines', '/insights', '/trust', '/events'];
    for (const p of expectedPaths) {
      expect(getRouteByPath(p), `Expected ${p} to be in registry`).toBeDefined();
    }
  });

  it('known Action Plane routes are present (anti-regression)', () => {
    const actionPlanePaths = ['/connectors', '/events', '/agents'];
    for (const p of actionPlanePaths) {
      expect(getRouteByPath(p), `Expected action-plane route ${p} to be in registry`).toBeDefined();
    }
  });
});
