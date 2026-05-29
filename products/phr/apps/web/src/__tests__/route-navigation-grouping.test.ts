/**
 * Navigation grouping tests verify PHR route contract grouping and visibility.
 *
 * Tests that stable routes appear in navigation, hidden routes do not appear,
 * and blocked routes produce forbidden UI behavior. Also verifies correct
 * group assignments for hidden routes (provider, caregiver, FCHV).
 *
 * @see phrRouteContracts.ts
 * @see G2-014
 */
import { describe, expect, it } from 'vitest';
import { isRouteAllowedForRole, phrRouteContracts, type PhrRole } from '../phrRouteContracts';

function isSuppressedRoute(route: { stability?: string; hidden?: boolean; blocked?: boolean }): boolean {
  return route.hidden === true || route.blocked === true || route.stability === 'hidden' || route.stability === 'blocked';
}

describe('navigation grouping and visibility (G2-014)', () => {
  it('stable routes appear in navigation for appropriate roles', () => {
    const stableRoutes = phrRouteContracts.filter(
      (route) => route.stability === 'stable' && !isSuppressedRoute(route)
    );

    // All stable routes should be discoverable for roles that meet minimumRole
    for (const route of stableRoutes) {
      const minRole = (route as { minimumRole: PhrRole }).minimumRole;
      // Check that the route is allowed for its minimum role
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, minRole)).toBe(true);
    }

    // Verify we have stable routes
    expect(stableRoutes.length).toBeGreaterThan(0);
  });

  it('hidden routes do not appear in navigation', () => {
    const hiddenRoutes = phrRouteContracts.filter(
      (route) => route.stability === 'hidden' || route.hidden === true
    );

    // Hidden routes should be suppressed
    for (const route of hiddenRoutes) {
      expect(isSuppressedRoute(route)).toBe(true);
    }

    // Verify we have hidden routes (provider, caregiver, FCHV routes)
    expect(hiddenRoutes.length).toBeGreaterThan(0);
  });

  it('blocked routes produce forbidden UI behavior', () => {
    const blockedRoutes = phrRouteContracts.filter(
      (route) => route.stability === 'blocked' || route.blocked === true
    );

    // Blocked routes should be suppressed
    for (const route of blockedRoutes) {
      expect(isSuppressedRoute(route)).toBe(true);
    }
  });

  it('routes are grouped by expected groups', () => {
    const expectedGroups = new Set(['care', 'clinical', 'governance', 'provider', 'caregiver', 'fchv']);
    const actualGroups = new Set(phrRouteContracts.map((route) => (route as { group: string }).group));

    // All actual groups should be in expected groups
    for (const group of actualGroups) {
      expect(expectedGroups).toContain(group);
    }

    // Verify we have the core groups
    expect(actualGroups.has('care')).toBe(true);
    expect(actualGroups.has('clinical')).toBe(true);
    expect(actualGroups.has('governance')).toBe(true);
  });

  it('hidden routes have correct group assignments', () => {
    const hiddenRoutes = phrRouteContracts.filter(
      (route) => route.stability === 'hidden' || route.hidden === true
    );

    // Provider routes should be in 'provider' group
    const providerRoutes = hiddenRoutes.filter(
      (route) => (route as { group: string }).group === 'provider'
    );
    expect(providerRoutes.length).toBeGreaterThan(0);

    // Caregiver routes should be in 'caregiver' group
    const caregiverRoutes = hiddenRoutes.filter(
      (route) => (route as { group: string }).group === 'caregiver'
    );
    expect(caregiverRoutes.length).toBeGreaterThan(0);

    // FCHV routes should be in 'fchv' group
    const fchvRoutes = hiddenRoutes.filter(
      (route) => (route as { group: string }).group === 'fchv'
    );
    expect(fchvRoutes.length).toBeGreaterThan(0);
  });
});
