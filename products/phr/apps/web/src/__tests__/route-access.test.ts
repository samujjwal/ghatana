/**
 * Route access tests verify the PHR route contract access policy for all personas.
 *
 * Each route's `minimumRole` is enforced by `isRouteAllowedForRole` using the
 * PHR_ROLE_ORDER hierarchy: patient < caregiver < fchv < clinician < admin.
 *
 * @see phrRouteContracts.ts
 */
import { describe, expect, it } from 'vitest';
import { PHR_ROLE_ORDER, isRouteAllowedForRole, phrRouteContracts, type PhrRole } from '../phrRouteContracts';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** All PHR roles in ascending privilege order. */
const ALL_ROLES: readonly PhrRole[] = ['patient', 'caregiver', 'fchv', 'clinician', 'admin'];

/**
 * Return routes whose minimumRole equals the given value.
 * Type assertion needed because `phrRouteContracts` is `as const`.
 */
function routesWithMinRole(role: PhrRole): typeof phrRouteContracts {
  return phrRouteContracts.filter(
    (r) => (r as { minimumRole: string }).minimumRole === role && !isSuppressedRoute(r),
  ) as unknown as typeof phrRouteContracts;
}

function activeRoutes(): typeof phrRouteContracts {
  return phrRouteContracts.filter((route) => !isSuppressedRoute(route)) as typeof phrRouteContracts;
}

function isSuppressedRoute(route: { stability?: string; hidden?: boolean; blocked?: boolean }): boolean {
  return (
    route.hidden === true ||
    route.blocked === true ||
    route.stability === 'hidden' ||
    route.stability === 'blocked' ||
    route.stability === 'deferred' ||
    route.stability === 'removed' ||
    route.stability === 'preview'
  );
}

// ---------------------------------------------------------------------------
// Core invariant: isRouteAllowedForRole must be coherent with PHR_ROLE_ORDER
// ---------------------------------------------------------------------------

describe('isRouteAllowedForRole role hierarchy invariant', () => {
  it('every active route is accessible to admin', () => {
    for (const route of activeRoutes()) {
      expect(isRouteAllowedForRole(route, 'admin')).toBe(true);
    }
  });

  it('hidden and blocked routes are denied by default even for admin', () => {
    for (const route of phrRouteContracts.filter(isSuppressedRoute)) {
      expect(isRouteAllowedForRole(route, 'admin')).toBe(false);
    }
  });

  it('patient-minimum routes are accessible to all roles', () => {
    for (const route of routesWithMinRole('patient')) {
      for (const role of ALL_ROLES) {
        expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, role)).toBe(true);
      }
    }
  });

  it('caregiver-minimum routes are NOT accessible to patient', () => {
    for (const route of routesWithMinRole('caregiver')) {
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'patient')).toBe(false);
    }
  });

  it('caregiver-minimum routes ARE accessible to caregiver, FCHV, clinician, admin', () => {
    for (const route of routesWithMinRole('caregiver')) {
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'caregiver')).toBe(true);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'fchv')).toBe(true);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'clinician')).toBe(true);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'admin')).toBe(true);
    }
  });

  it('clinician-minimum routes are NOT accessible to patient, caregiver, or FCHV', () => {
    for (const route of routesWithMinRole('clinician')) {
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'patient')).toBe(false);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'caregiver')).toBe(false);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'fchv')).toBe(false);
    }
  });

  it('clinician-minimum routes ARE accessible to clinician and admin', () => {
    for (const route of routesWithMinRole('clinician')) {
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'clinician')).toBe(true);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'admin')).toBe(true);
    }
  });

  it('admin-minimum routes are NOT accessible to patient, caregiver, FCHV, or clinician', () => {
    for (const route of routesWithMinRole('admin')) {
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'patient')).toBe(false);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'caregiver')).toBe(false);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'fchv')).toBe(false);
      expect(isRouteAllowedForRole(route as { minimumRole: PhrRole }, 'clinician')).toBe(false);
    }
  });

  it('keeps FCHV below clinician-only routes', () => {
    const fchvOrder = PHR_ROLE_ORDER.fchv;
    const clinicianOrder = PHR_ROLE_ORDER.clinician;

    expect(fchvOrder).toBeLessThan(clinicianOrder);
  });
});

// ---------------------------------------------------------------------------
// Named route spot-checks
// ---------------------------------------------------------------------------

describe('isRouteAllowedForRole named route spot-checks', () => {
  function route(path: string): { minimumRole: PhrRole } {
    const found = phrRouteContracts.find((r) => r.path === path) as
      | { minimumRole: PhrRole }
      | undefined;
    if (!found) throw new Error(`Route not found in contract: ${path}`);
    return found;
  }

  describe('/dashboard', () => {
    it('is accessible to patient', () => expect(isRouteAllowedForRole(route('/dashboard'), 'patient')).toBe(true));
    it('is accessible to admin', () => expect(isRouteAllowedForRole(route('/dashboard'), 'admin')).toBe(true));
  });

  describe('/labs', () => {
    it('is NOT accessible to patient', () => expect(isRouteAllowedForRole(route('/labs'), 'patient')).toBe(false));
    it('is accessible to caregiver', () => expect(isRouteAllowedForRole(route('/labs'), 'caregiver')).toBe(true));
    it('is accessible to clinician', () => expect(isRouteAllowedForRole(route('/labs'), 'clinician')).toBe(true));
  });

  describe('/emergency', () => {
    it('is NOT accessible to patient', () => expect(isRouteAllowedForRole(route('/emergency'), 'patient')).toBe(false));
    it('is NOT accessible to caregiver', () => expect(isRouteAllowedForRole(route('/emergency'), 'caregiver')).toBe(false));
    it('is NOT accessible to FCHV', () => expect(isRouteAllowedForRole(route('/emergency'), 'fchv')).toBe(false));
    it('is accessible to clinician', () => expect(isRouteAllowedForRole(route('/emergency'), 'clinician')).toBe(true));
    it('is accessible to admin', () => expect(isRouteAllowedForRole(route('/emergency'), 'admin')).toBe(true));
  });

  describe('/audit', () => {
    it('is NOT accessible to patient', () => expect(isRouteAllowedForRole(route('/audit'), 'patient')).toBe(false));
    it('is NOT accessible to caregiver', () => expect(isRouteAllowedForRole(route('/audit'), 'caregiver')).toBe(false));
    it('is NOT accessible to clinician', () => expect(isRouteAllowedForRole(route('/audit'), 'clinician')).toBe(false));
    it('is accessible to admin', () => expect(isRouteAllowedForRole(route('/audit'), 'admin')).toBe(true));
  });

  describe('/release-readiness', () => {
    it('is NOT accessible to patient', () => expect(isRouteAllowedForRole(route('/release-readiness'), 'patient')).toBe(false));
    it('is NOT accessible to clinician', () => expect(isRouteAllowedForRole(route('/release-readiness'), 'clinician')).toBe(false));
    it('is accessible to admin', () => expect(isRouteAllowedForRole(route('/release-readiness'), 'admin')).toBe(true));
  });

  describe('/settings', () => {
    it('is accessible to patient', () => expect(isRouteAllowedForRole(route('/settings'), 'patient')).toBe(true));
    it('is accessible to admin', () => expect(isRouteAllowedForRole(route('/settings'), 'admin')).toBe(true));
  });
});

// ---------------------------------------------------------------------------
// Contract completeness: every route has required fields
// ---------------------------------------------------------------------------

describe('phrRouteContracts structural completeness', () => {
  it('every route has a non-empty path', () => {
    for (const route of phrRouteContracts) {
      expect(typeof route.path).toBe('string');
      expect(route.path.length).toBeGreaterThan(0);
    }
  });

  it('every route has a valid minimumRole', () => {
    const validRoles: readonly string[] = Object.keys(PHR_ROLE_ORDER);
    for (const route of phrRouteContracts) {
      expect(validRoles).toContain((route as { minimumRole: string }).minimumRole);
    }
  });

  it('every route has at least one persona', () => {
    for (const route of phrRouteContracts) {
      expect((route as { personas: readonly string[] }).personas.length).toBeGreaterThan(0);
    }
  });

  it('every route path is unique', () => {
    const paths = phrRouteContracts.map((r) => r.path);
    const unique = new Set(paths);
    expect(unique.size).toBe(paths.length);
  });

  it('route paths start with /', () => {
    for (const route of phrRouteContracts) {
      expect(route.path.startsWith('/')).toBe(true);
    }
  });
});
