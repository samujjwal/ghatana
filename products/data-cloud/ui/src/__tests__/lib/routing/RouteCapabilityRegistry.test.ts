import { describe, expect, it } from 'vitest';
import {
  canonicalRouteRegistry,
  getDiscoverableRoutes,
  getRouteByPath,
  getActiveRoutes,
  type RouteCapability,
} from '@/lib/routing/RouteCapabilityRegistry';
import type { ShellRole } from '@/lib/auth/session';

describe('RouteCapabilityRegistry', () => {
  describe('canonicalRouteRegistry', () => {
    it('contains expected canonical routes', () => {
      expect(canonicalRouteRegistry).toHaveProperty('home');
      expect(canonicalRouteRegistry).toHaveProperty('data');
      expect(canonicalRouteRegistry).toHaveProperty('insights');
      expect(canonicalRouteRegistry).toHaveProperty('settings');
    });

    it('has valid route entries with required fields', () => {
      Object.values(canonicalRouteRegistry).forEach((route: RouteCapability) => {
        expect(route.path).toBeDefined();
        expect(route.path.startsWith('/')).toBe(true);
        expect(route.label).toBeTruthy();
        expect(route.lifecycle).toBeOneOf(['active', 'preview', 'deprecated', 'redirect', 'removed', 'boundary']);
        expect(route.minimumShellRole).toBeOneOf(['primary-user', 'operator', 'admin']);
      });
    });

    it('has unique paths', () => {
      const paths = Object.values(canonicalRouteRegistry).map((r) => r.path);
      const uniquePaths = new Set(paths);
      expect(uniquePaths.size).toBe(paths.length);
    });
  });

  describe('getDiscoverableRoutes', () => {
    it('returns only discoverable routes for primary-user', () => {
      const routes = getDiscoverableRoutes('primary-user');
      expect(routes.every((r) => r.discoverable)).toBe(true);
    });

    it('returns more routes for admin than primary-user', () => {
      const primary = getDiscoverableRoutes('primary-user');
      const admin = getDiscoverableRoutes('admin');
      expect(admin.length).toBeGreaterThanOrEqual(primary.length);
    });

    it('excludes routes requiring higher roles', () => {
      const primary = getDiscoverableRoutes('primary-user');
      const opsRoutes = primary.filter((r) => r.minimumShellRole === 'operator' || r.minimumShellRole === 'admin');
      expect(opsRoutes.length).toBe(0);
    });
  });

  describe('getRouteByPath', () => {
    it('finds route by exact path', () => {
      const route = getRouteByPath('/insights');
      expect(route).toBeDefined();
      expect(route?.label).toBe('Insights');
    });

    it('returns undefined for unknown paths', () => {
      expect(getRouteByPath('/nonexistent')).toBeUndefined();
    });
  });

  describe('getActiveRoutes', () => {
    it('returns only routes with active lifecycle', () => {
      const routes = getActiveRoutes();
      expect(routes.every((r) => r.lifecycle === 'active')).toBe(true);
      expect(routes.length).toBeGreaterThan(0);
    });
  });
});
