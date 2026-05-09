/**
 * Route Policy Registry
 *
 * Declarative route policy metadata for authentication, authorization, consent,
 * and governance. All protected routes must have an entry in this registry.
 *
 * @doc.type module
 * @doc.purpose Central registry for route authentication and authorization policies
 * @doc.layer platform
 * @doc.pattern Registry
 */

import type { TutorPutorPermission } from "../authz/permissionPolicy.js";

/**
 * Authentication mode for a route
 */
export type AuthMode =
  | "public" // No authentication required
  | "jwt" // JWT authentication required
  | "jwt_or_trusted_proxy" // JWT or trusted proxy header (dev/staging only)
  | "worker_jwt" // Worker JWT authentication required
  | "mtls" // mTLS authentication required;

/**
 * Tenant scoping mode for a route
 */
export type TenantMode =
  | "none" // No tenant scoping
  | "required" // Tenant context required
  | "optional"; // Tenant context optional

/**
 * Consent requirement for a route
 */
export type ConsentMode =
  | "none" // No consent required
  | "ai_processing" // AI processing consent required
  | "telemetry" // Telemetry consent required
  | "social" // Social features consent required;

/**
 * Route policy metadata
 */
export interface RoutePolicy {
  /** Authentication mode */
  authMode: AuthMode;
  /** Tenant scoping mode */
  tenantMode: TenantMode;
  /** Consent requirement */
  consentMode: ConsentMode;
  /** Required permissions (if any) */
  permissions?: TutorPutorPermission[];
  /** Allowed roles (if any) */
  allowedRoles?: string[];
  /** Route owner for governance */
  owner: string;
  /** Test owner for test coverage */
  testOwner: string;
  /** Whether route is in the OpenAPI contract */
  inOpenAPI: boolean;
  /** Whether route has a typed client */
  hasTypedClient: boolean;
}

/**
 * Registry of route policies keyed by route path and method
 */
const routePolicyRegistry = new Map<string, Map<string, RoutePolicy>>();

/**
 * Register a route policy
 */
export function registerRoutePolicy(
  method: string,
  path: string,
  policy: RoutePolicy,
): void {
  const normalizedPath = normalizePath(path);
  const methodUpper = method.toUpperCase();

  if (!routePolicyRegistry.has(normalizedPath)) {
    routePolicyRegistry.set(normalizedPath, new Map());
  }

  routePolicyRegistry.get(normalizedPath)!.set(methodUpper, policy);
}

/**
 * Get route policy for a given method and path
 */
export function getRoutePolicy(
  method: string,
  path: string,
): RoutePolicy | undefined {
  const normalizedPath = normalizePath(path);
  const methodUpper = method.toUpperCase();
  return routePolicyRegistry.get(normalizedPath)?.get(methodUpper);
}

/**
 * Check if a route is public (no auth required)
 */
export function isPublicRoute(method: string, path: string): boolean {
  const policy = getRoutePolicy(method, path);
  return policy?.authMode === "public";
}

/**
 * Normalize path for registry lookup
 */
function normalizePath(path: string): string {
  // Remove trailing slashes
  return path.replace(/\/+$/, "");
}

/**
 * Public route allowlist - these routes never require authentication
 */
export const PUBLIC_ROUTES = [
  { method: "GET", path: "/health" },
  { method: "GET", path: "/ready" },
  { method: "GET", path: "/metrics" },
  { method: "POST", path: "/api/v1/auth/sso/login/:providerId" },
  { method: "GET", path: "/api/v1/auth/sso/callback/:providerId" },
  { method: "GET", path: "/api/v1/auth/jwks" },
] as const;

/**
 * Check if a route is in the public allowlist
 */
export function isInPublicAllowlist(method: string, path: string): boolean {
  return PUBLIC_ROUTES.some(
    (route) =>
      route.method === method.toUpperCase() &&
      pathMatch(path, route.path),
  );
}

/**
 * Simple path matching with parameter support
 */
function pathMatch(actualPath: string, pattern: string): boolean {
  const patternSegments = pattern.split("/");
  const actualSegments = actualPath.split("/");

  if (patternSegments.length !== actualSegments.length) {
    return false;
  }

  for (let i = 0; i < patternSegments.length; i++) {
    const patternSeg = patternSegments[i];
    const actualSeg = actualSegments[i];

    if (!patternSeg || !actualSeg) {
      return false;
    }

    // Pattern segment starting with : is a parameter
    if (patternSeg.startsWith(":")) {
      continue;
    }

    if (patternSeg !== actualSeg) {
      return false;
    }
  }

  return true;
}

/**
 * Get all registered routes for validation
 */
export function getAllRegisteredRoutes(): Array<{
  method: string;
  path: string;
  policy: RoutePolicy;
}> {
  const routes: Array<{ method: string; path: string; policy: RoutePolicy }> = [];

  for (const [path, methods] of routePolicyRegistry.entries()) {
    for (const [method, policy] of methods.entries()) {
      routes.push({ method, path, policy });
    }
  }

  return routes;
}

/**
 * Validate route policy completeness
 * Throws if required fields are missing
 */
export function validateRoutePolicy(policy: RoutePolicy, path: string): void {
  if (!policy.owner) {
    throw new Error(`Route ${path} missing owner`);
  }

  if (!policy.testOwner) {
    throw new Error(`Route ${path} missing testOwner`);
  }

  // Public routes don't need other checks
  if (policy.authMode === "public") {
    return;
  }

  // Protected routes must be in OpenAPI and have typed client
  if (!policy.inOpenAPI) {
    throw new Error(`Protected route ${path} must be in OpenAPI contract`);
  }

  if (!policy.hasTypedClient) {
    throw new Error(`Protected route ${path} must have typed client`);
  }
}
