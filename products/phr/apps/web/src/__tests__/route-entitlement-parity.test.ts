/**
 * Route Entitlement Parity Test
 *
 * Verifies that the web route contracts (phrRouteContracts.ts) and backend
 * entitlement routes (PhrEntitlementRoutes.java) are in sync. This test
 * ensures no drift between frontend and backend route definitions.
 *
 * The backend route list is extracted from PhrEntitlementRoutes.java and
 * compared against the frontend phrRouteContracts array.
 */

import { phrRouteContracts } from '../phrRouteContracts';

// Backend routes from PhrEntitlementRoutes.java (phrRoutesFor method)
// This list must be kept in sync with the Java implementation
const BACKEND_ROUTES = [
  '/dashboard',
  '/records',
  '/consents',
  '/appointments',
  '/settings',
  '/labs',
  '/medications',
  '/conditions',
  '/observations',
  '/immunizations',
  '/documents',
  '/documents/upload',
  '/documents/:docId/ocr',
  '/timeline',
  '/profile',
  '/records/:recordId',
  '/notifications',
  '/forbidden',
  '/not-found',
  '/emergency',
  '/emergency/reviews',
  '/release-readiness',
  '/audit',
  '/provider/dashboard',
  '/provider/patients',
  '/caregiver/dependents',
  '/fchv/dashboard',
] as const;

type BackendRoute = (typeof BACKEND_ROUTES)[number];
type FrontendRoute = (typeof phrRouteContracts)[number]['path'];

describe('Route Entitlement Parity', () => {
  it('should have all backend routes present in frontend contracts', () => {
    const frontendPaths = new Set<FrontendRoute>(
      phrRouteContracts.map((r) => r.path)
    );

    const missingRoutes: BackendRoute[] = [];
    for (const backendRoute of BACKEND_ROUTES) {
      if (!frontendPaths.has(backendRoute as FrontendRoute)) {
        missingRoutes.push(backendRoute);
      }
    }

    if (missingRoutes.length > 0) {
      throw new Error(
        `Backend routes missing from frontend contracts: ${missingRoutes.join(', ')}\n` +
          'Update phrRouteContracts.ts to include these routes or remove them from PhrEntitlementRoutes.java'
      );
    }
  });

  it('should have all frontend routes present in backend', () => {
    const backendPaths = new Set<BackendRoute>(BACKEND_ROUTES);
    const extraRoutes: FrontendRoute[] = [];

    for (const contract of phrRouteContracts) {
      if (!backendPaths.has(contract.path as BackendRoute)) {
        extraRoutes.push(contract.path);
      }
    }

    if (extraRoutes.length > 0) {
      throw new Error(
        `Frontend routes missing from backend: ${extraRoutes.join(', ')}\n` +
          'Update PhrEntitlementRoutes.java to include these routes or remove them from phrRouteContracts.ts'
      );
    }
  });

  it('should have matching route counts', () => {
    const frontendCount = phrRouteContracts.length;
    const backendCount = BACKEND_ROUTES.length;

    if (frontendCount !== backendCount) {
      throw new Error(
        `Route count mismatch: frontend has ${frontendCount} routes, backend has ${backendCount} routes`
      );
    }
  });

  it('should have consistent minimumRole for common routes', () => {
    // Define expected minimum roles for key routes
    const expectedRoles: Record<string, string> = {
      '/dashboard': 'patient',
      '/records': 'patient',
      '/consents': 'patient',
      '/appointments': 'patient',
      '/settings': 'patient',
      '/labs': 'caregiver',
      '/medications': 'caregiver',
      '/conditions': 'patient',
      '/observations': 'caregiver',
      '/immunizations': 'patient',
      '/documents': 'patient',
      '/documents/upload': 'patient',
      '/documents/:docId/ocr': 'patient',
      '/timeline': 'patient',
      '/profile': 'patient',
      '/records/:recordId': 'patient',
      '/notifications': 'patient',
      '/emergency': 'clinician',
      '/emergency/reviews': 'admin',
      '/release-readiness': 'admin',
      '/audit': 'admin',
      '/provider/dashboard': 'clinician',
      '/provider/patients': 'clinician',
      '/caregiver/dependents': 'caregiver',
      '/fchv/dashboard': 'caregiver',
    };

    const mismatches: string[] = [];
    for (const contract of phrRouteContracts) {
      const expected = expectedRoles[contract.path];
      if (expected && contract.minimumRole !== expected) {
        mismatches.push(
          `${contract.path}: expected minimumRole '${expected}', got '${contract.minimumRole}'`
        );
      }
    }

    if (mismatches.length > 0) {
      throw new Error(
        `Minimum role mismatches:\n${mismatches.join('\n')}\n` +
          'Update either phrRouteContracts.ts or PhrEntitlementRoutes.java to align roles'
      );
    }
  });

  it('should have lifecycle metadata for all routes', () => {
    const routesWithoutLifecycle: string[] = [];

    for (const contract of phrRouteContracts) {
      if (!contract.lifecycle) {
        routesWithoutLifecycle.push(contract.path);
      }
    }

    if (routesWithoutLifecycle.length > 0) {
      throw new Error(
        `Routes missing lifecycle metadata: ${routesWithoutLifecycle.join(', ')}\n` +
          'Add lifecycle metadata to these routes in phrRouteContracts.ts'
      );
    }
  });

  it('should have featureFlag only on experimental routes', () => {
    const featureFlaggedRoutes = phrRouteContracts.filter((r) => r.featureFlag);

    for (const route of featureFlaggedRoutes) {
      if (route.lifecycle?.stability !== 'experimental') {
        throw new Error(
          `Route ${route.path} has featureFlag but stability is '${route.lifecycle?.stability}'. ` +
          'Feature-flagged routes should have stability: "experimental"'
        );
      }
    }
  });
});
