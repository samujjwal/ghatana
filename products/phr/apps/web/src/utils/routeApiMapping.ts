/**
 * Route to API endpoint mapping based on PHR route contract.
 *
 * This utility provides a centralized way to map frontend routes to their
 * corresponding backend API endpoints as defined in the route contract.
 *
 * @doc.type utility
 * @doc.purpose Route to API endpoint mapping
 * @doc.layer frontend
 */

import routeContractJson from '../../config/phr-route-contract.json?raw';

interface RouteContract {
  schemaVersion: string;
  product: string;
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
}

const routeContract = JSON.parse(routeContractJson) as RouteContract;

/**
 * Get the API endpoint for a given route path.
 *
 * @param path - The frontend route path (e.g., '/dashboard', '/records')
 * @returns The API endpoint string or null if not found
 *
 * @example
 * ```ts
 * const endpoint = getRouteApiEndpoint('/dashboard');
 * // Returns: '/api/v1/dashboard'
 * ```
 */
export function getRouteApiEndpoint(path: string): string | null {
  const route = routeContract.routes.find((r) => r.path === path);
  return route?.apiEndpoint ?? null;
}

/**
 * Get the policy ID for a given route path.
 *
 * @param path - The frontend route path
 * @returns The policy ID string or null if not found
 */
export function getRoutePolicyId(path: string): string | null {
  const route = routeContract.routes.find((r) => r.path === path);
  return route?.policyId ?? null;
}

/**
 * Get the minimum role required for a given route path.
 *
 * @param path - The frontend route path
 * @returns The minimum role string or null if not found
 */
export function getRouteMinimumRole(path: string): string | null {
  const route = routeContract.routes.find((r) => r.path === path);
  return route?.minimumRole ?? null;
}

/**
 * Check if a route is stable (production-ready).
 *
 * @param path - The frontend route path
 * @returns True if the route is marked as stable
 */
export function isRouteStable(path: string): boolean {
  const route = routeContract.routes.find((r) => r.path === path);
  return route?.stability === 'stable';
}

/**
 * Check if a route is hidden (not yet production-ready).
 *
 * @param path - The frontend route path
 * @returns True if the route is marked as hidden
 */
export function isRouteHidden(path: string): boolean {
  const route = routeContract.routes.find((r) => r.path === path);
  return route?.stability === 'hidden';
}

/**
 * Get all stable routes.
 *
 * @returns Array of stable route paths
 */
export function getStableRoutes(): string[] {
  return routeContract.routes
    .filter((r) => r.stability === 'stable')
    .map((r) => r.path);
}

/**
 * Get all hidden routes.
 *
 * @returns Array of hidden route paths
 */
export function getHiddenRoutes(): string[] {
  return routeContract.routes
    .filter((r) => r.stability === 'hidden')
    .map((r) => r.path);
}
