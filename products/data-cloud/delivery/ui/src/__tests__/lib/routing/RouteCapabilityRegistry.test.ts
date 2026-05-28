import { describe, expect, it } from 'vitest';
import {
  canonicalRouteSurfaceRegistry,
  getDiscoverableRouteSurfaces,
  getRouteSurfaceByPath,
  getActiveRouteSurfaces,
  type RouteSurface,
} from '@/lib/routing/RouteSurfaceRegistry';

describe('RouteSurfaceRegistry', () => {
  describe('canonicalRouteSurfaceRegistry', () => {
    it('contains expected canonical routes', () => {
      expect(canonicalRouteSurfaceRegistry).toHaveProperty('home');
      expect(canonicalRouteSurfaceRegistry).toHaveProperty('data');
      expect(canonicalRouteSurfaceRegistry).toHaveProperty('insights');
      expect(canonicalRouteSurfaceRegistry).toHaveProperty('settings');
    });

    it('has valid route entries with required fields', () => {
      Object.values(canonicalRouteSurfaceRegistry).forEach((route: RouteSurface) => {
        expect(route.path).toBeDefined();
        expect(route.path.startsWith('/')).toBe(true);
        expect(route.label).toBeTruthy();
        expect(route.lifecycle).toBeOneOf(['active', 'preview', 'deprecated', 'redirect', 'removed', 'boundary']);
        expect(route.minimumShellRole).toBeOneOf(['primary-user', 'operator', 'admin']);
      });
    });

    it('has unique paths', () => {
      const paths = Object.values(canonicalRouteSurfaceRegistry).map((r) => r.path);
      const uniquePaths = new Set(paths);
      expect(uniquePaths.size).toBe(paths.length);
    });
  });

  describe('getDiscoverableRouteSurfaces', () => {
    it('returns only discoverable routes for primary-user', () => {
      const routes = getDiscoverableRouteSurfaces('primary-user');
      expect(routes.every((r) => r.discoverable)).toBe(true);
    });

    it('returns more routes for admin than primary-user', () => {
      const primary = getDiscoverableRouteSurfaces('primary-user');
      const admin = getDiscoverableRouteSurfaces('admin');
      expect(admin.length).toBeGreaterThanOrEqual(primary.length);
    });

    it('excludes routes requiring higher roles', () => {
      const primary = getDiscoverableRouteSurfaces('primary-user');
      const opsRoutes = primary.filter((r) => r.minimumShellRole === 'operator' || r.minimumShellRole === 'admin');
      expect(opsRoutes.length).toBe(0);
    });
  });

  describe('getRouteSurfaceByPath', () => {
    it('finds route by exact path', () => {
      const route = getRouteSurfaceByPath('/insights');
      expect(route).toBeDefined();
      expect(route?.label).toBe('Insights');
    });

    it('returns undefined for unknown paths', () => {
      expect(getRouteSurfaceByPath('/nonexistent')).toBeUndefined();
    });

    it('keeps /pipelines route aligned with data-local plugin execution terminology', () => {
      const route = getRouteSurfaceByPath('/pipelines');
      expect(route).toBeDefined();
      expect(route?.label).toBe('Pipelines');
      expect(route?.capabilities).toContain('plugin-execution');
      expect(route?.description?.toLowerCase()).toContain('data-local plugin workflow execution');
    });
  });

  describe('getActiveRouteSurfaces', () => {
    it('returns only routes with active lifecycle', () => {
      const routes = getActiveRouteSurfaces();
      expect(routes.every((r) => r.lifecycle === 'active')).toBe(true);
      expect(routes.length).toBeGreaterThan(0);
    });
  });
});
