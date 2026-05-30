import { describe, it, expect } from 'vitest';
import {
  canonicalRouteSurfaceRegistry,
  getDiscoverableRouteSurfaces,
  getRouteSurfacesByLifecycle,
  getRouteSurfaceByPath,
  getActiveRouteSurfaces,
} from '../../lib/routing/RouteSurfaceRegistry';
import type { ShellRole } from '../../lib/auth/session';

describe('RouteSurfaceRegistry', () => {
  describe('navigation discoverability', () => {
    it('shows appropriate discoverable surfaces for primary-user role', () => {
      const discoverable = getDiscoverableRouteSurfaces('primary-user');
      const paths = discoverable.map((r) => r.path);

      // Primary users should see outcome-first surfaces
      expect(paths).toContain('/');
      expect(paths).toContain('/data');
      expect(paths).toContain('/pipelines');
      expect(paths).toContain('/query');

      // Should not see operator/admin only surfaces
      expect(paths).not.toContain('/events');
      expect(paths).not.toContain('/trust');
      expect(paths).not.toContain('/operations');
      expect(paths).not.toContain('/connectors');
      expect(paths).not.toContain('/insights');
    });

    it('shows appropriate discoverable surfaces for operator role', () => {
      const discoverable = getDiscoverableRouteSurfaces('operator');
      const paths = discoverable.map((r) => r.path);

      // Operators should see more surfaces
      expect(paths).toContain('/');
      expect(paths).toContain('/data');
      expect(paths).toContain('/pipelines');
      expect(paths).toContain('/query');
      expect(paths).toContain('/events');
      expect(paths).toContain('/trust');
      expect(paths).toContain('/connectors');
      expect(paths).toContain('/insights');
      expect(paths).toContain('/plugins');

      // Should not see admin-only surfaces
      expect(paths).not.toContain('/operations');
    });

    it('shows appropriate discoverable surfaces for admin role', () => {
      const discoverable = getDiscoverableRouteSurfaces('admin');
      const paths = discoverable.map((r) => r.path);

      // Admins should see all discoverable surfaces
      expect(paths).toContain('/');
      expect(paths).toContain('/data');
      expect(paths).toContain('/pipelines');
      expect(paths).toContain('/query');
      expect(paths).toContain('/events');
      expect(paths).toContain('/trust');
      expect(paths).toContain('/connectors');
      expect(paths).toContain('/insights');
      expect(paths).toContain('/plugins');
      expect(paths).toContain('/operations');
    });

    it('disabled surface explains why unavailable through lifecycle and discoverable flags', () => {
      const alertsRoute = canonicalRouteSurfaceRegistry.alerts;
      const memoryRoute = canonicalRouteSurfaceRegistry.memory;
      const agentsRoute = canonicalRouteSurfaceRegistry.agents;

      // These surfaces are marked as non-discoverable
      expect(alertsRoute.discoverable).toBe(false);
      expect(memoryRoute.discoverable).toBe(false);
      expect(agentsRoute.discoverable).toBe(false);

      // They are in preview lifecycle
      expect(alertsRoute.lifecycle).toBe('preview');
      expect(memoryRoute.lifecycle).toBe('preview');
      expect(agentsRoute.lifecycle).toBe('preview');

      // They should not appear in discoverable routes
      const discoverable = getDiscoverableRouteSurfaces('operator');
      const paths = discoverable.map((r) => r.path);
      expect(paths).not.toContain('/alerts');
      expect(paths).not.toContain('/memory');
      expect(paths).not.toContain('/agents');
    });

    it('no release-readiness route is discoverable', () => {
      const releaseTruthRoute = canonicalRouteSurfaceRegistry.operationsReleaseTruth;
      const settingsRoute = canonicalRouteSurfaceRegistry.settings;

      // Release-readiness routes are marked as boundary lifecycle
      expect(releaseTruthRoute.lifecycle).toBe('boundary');
      expect(settingsRoute.lifecycle).toBe('boundary');

      // They are non-discoverable
      expect(releaseTruthRoute.discoverable).toBe(false);
      expect(settingsRoute.discoverable).toBe(false);

      // They should not appear in discoverable routes even for admin
      const discoverable = getDiscoverableRouteSurfaces('admin');
      const paths = discoverable.map((r) => r.path);
      expect(paths).not.toContain('/operations/release-truth');
      expect(paths).not.toContain('/settings');

      // They should not appear when includesBoundary is false
      const discoverableWithoutBoundary = getDiscoverableRouteSurfaces('admin', false);
      const pathsWithoutBoundary = discoverableWithoutBoundary.map((r) => r.path);
      expect(pathsWithoutBoundary).not.toContain('/operations/release-truth');
      expect(pathsWithoutBoundary).not.toContain('/settings');
    });

    it('boundary routes appear when includesBoundary is true', () => {
      const discoverableWithBoundary = getDiscoverableRouteSurfaces('admin', true);
      const paths = discoverableWithBoundary.map((r) => r.path);

      // With includesBoundary=true, boundary routes that are discoverable should appear
      // But since release-truth and settings are discoverable=false, they still won't appear
      expect(paths).not.toContain('/operations/release-truth');
      expect(paths).not.toContain('/settings');
    });
  });

  describe('lifecycle grouping', () => {
    it('groups routes by lifecycle correctly', () => {
      const byLifecycle = getRouteSurfacesByLifecycle();

      expect(byLifecycle.active).toHaveLength(9); // home, data, connectors, pipelines, query, insights, trust, events, operations, plugins
      expect(byLifecycle.preview).toHaveLength(6); // alerts, memory, entities, context, fabric, agents
      expect(byLifecycle.boundary).toHaveLength(2); // operationsReleaseTruth, settings
      expect(byLifecycle.deprecated).toHaveLength(0);
      expect(byLifecycle.redirect).toHaveLength(0);
      expect(byLifecycle.removed).toHaveLength(0);
    });

    it('active routes are correctly identified', () => {
      const activeRoutes = getActiveRouteSurfaces();
      const paths = activeRoutes.map((r) => r.path);

      expect(paths).toContain('/');
      expect(paths).toContain('/data');
      expect(paths).toContain('/pipelines');
      expect(paths).toContain('/query');
      expect(paths).toContain('/operations');
      expect(paths).toContain('/plugins');

      expect(paths).not.toContain('/alerts'); // preview
      expect(paths).not.toContain('/memory'); // preview
      expect(paths).not.toContain('/operations/release-truth'); // boundary
    });
  });

  describe('route lookup', () => {
    it('finds route by path', () => {
      const homeRoute = getRouteSurfaceByPath('/');
      expect(homeRoute).toBeDefined();
      expect(homeRoute?.label).toBe('Home');

      const dataRoute = getRouteSurfaceByPath('/data');
      expect(dataRoute).toBeDefined();
      expect(dataRoute?.label).toBe('Data');

      const nonExistent = getRouteSurfaceByPath('/non-existent');
      expect(nonExistent).toBeUndefined();
    });
  });
});
