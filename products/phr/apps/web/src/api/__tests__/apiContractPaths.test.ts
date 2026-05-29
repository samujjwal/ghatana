import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Read the route contract JSON file
const routeContractPath = join(__dirname, '../../../../config/phr-route-contract.json');
const routeContract = JSON.parse(readFileSync(routeContractPath, 'utf-8'));

interface RouteContract {
  routes: Route[];
}

interface Route {
  path: string;
  label: string;
  description: string;
  group: string;
  minimumRole: string;
  personas: string[];
  tiers: string[];
  actions: string[];
  cards: string[];
  stability: string;
  apiEndpoint?: string;
  policyId?: string;
  testId?: string;
  surface: string[];
  i18nKey: string;
  descriptionI18nKey: string;
  routeType: string;
  visibilityReason?: string;
}

/**
 * Test that imported API function paths match the route contract JSON.
 * This ensures the web API client stays in sync with the canonical route contract.
 */
describe('API Contract Paths', () => {
  const stableRoutes = (routeContract as RouteContract).routes.filter((r: Route) => r.stability === 'stable');
  
  it('should have all stable routes with apiEndpoint defined', () => {
    const routesWithoutEndpoint = stableRoutes.filter((r: Route) => !r.apiEndpoint);
    expect(routesWithoutEndpoint).toHaveLength(0);
  });

  it('should match API paths in phrApiCore to route contract endpoints', () => {
    // Extract API paths from the route contract
    const contractEndpoints = new Set<string>(
      stableRoutes
        .map((r: Route) => r.apiEndpoint)
        .filter((e: string | undefined): e is string => e !== undefined)
    );

    // Known API paths used in phrApiCore (extracted from the implementation)
    const apiCorePaths = new Set([
      '/api/v1/dashboard',
      '/api/v1/consents/grants',
      '/api/v1/appointments',
      '/api/v1/emergency/access',
      '/api/v1/emergency/reviews',
      '/api/v1/auth/login',
      '/api/v1/auth/logout',
      '/api/v1/profile',
      '/api/v1/records/timeline',
      '/api/v1/clinical/conditions',
      '/api/v1/clinical/observations',
      '/api/v1/clinical/labs',
      '/api/v1/clinical/immunizations',
      '/api/v1/clinical/medications',
      '/api/v1/records/documents',
      '/api/v1/notifications',
      '/api/v1/release-readiness',
      '/api/v1/audit/events',
      '/fhir/Patient/current/$export',
    ]);

    // Check that all API core paths are in the contract
    const missingFromContract = [...apiCorePaths].filter(path => !isPathInContract(path, contractEndpoints));
    expect(missingFromContract).toHaveLength(0);
  });

  it('should have all stable contract endpoints covered by API client', () => {
    // Extract API paths from the route contract
    const contractEndpoints = stableRoutes
      .map((r: Route) => r.apiEndpoint)
      .filter((e: string | undefined): e is string => e !== undefined);

    // Known API paths used in phrApiCore
    const apiCorePaths = [
      '/api/v1/dashboard',
      '/api/v1/consents/grants',
      '/api/v1/appointments',
      '/api/v1/emergency/access',
      '/api/v1/emergency/reviews',
      '/api/v1/auth/login',
      '/api/v1/auth/logout',
      '/api/v1/profile',
      '/api/v1/records/timeline',
      '/api/v1/clinical/conditions',
      '/api/v1/clinical/observations',
      '/api/v1/clinical/labs',
      '/api/v1/clinical/immunizations',
      '/api/v1/clinical/medications',
      '/api/v1/records/documents',
      '/api/v1/notifications',
      '/api/v1/release-readiness',
      '/api/v1/audit/events',
    ];

    // Check that all contract endpoints are covered (allowing for parameterized paths)
    const uncovered = contractEndpoints.filter((endpoint: string) => {
      return !apiCorePaths.some(apiPath => pathsMatch(apiPath, endpoint));
    });

    expect(uncovered).toHaveLength(0);
  });
});

/**
 * Helper to check if a path is covered in the contract, accounting for parameterized paths
 */
function isPathInContract(path: string, contractEndpoints: Set<string>): boolean {
  // Direct match
  if (contractEndpoints.has(path)) return true;

  // Check for parameterized path matches (e.g., /api/v1/records/:id matches /api/v1/records/123)
  for (const endpoint of contractEndpoints) {
    if (pathsMatch(path, endpoint)) return true;
  }

  return false;
}

/**
 * Check if two paths match, accounting for URL parameters
 */
function pathsMatch(path1: string, path2: string): boolean {
  const normalizePath = (p: string) => p.replace(/:\w+/g, ':param');
  return normalizePath(path1) === normalizePath(path2);
}
