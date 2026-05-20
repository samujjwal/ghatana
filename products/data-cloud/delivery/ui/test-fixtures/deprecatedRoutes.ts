/**
 * Deprecated API route helpers for frontend mock adapters.
 *
 * @doc.type config
 * @doc.purpose Centralize deprecated route warnings and redirect headers for mock layers
 * @doc.layer frontend
 * @doc.pattern Adapter
 */

import { emitDataCloudDiagnostic } from "../src/diagnostics";

export const COLLECTION_RUNTIME_OPENAPI_PATHS = [
  "/api/v1/entities/{collection}",
  "/api/v1/entities/{collection}/{id}",
  "/api/v1/collections/{id}/cost-report",
  "/api/v1/collections/{id}/migrate",
] as const;

export const DEPRECATED_COLLECTION_ROUTE_REDIRECTS = [
  {
    legacyPath: "/api/v1/collections",
    canonicalPath: "/api/v1/entities/dc_collections",
    openApiPath: "/api/v1/entities/{collection}",
  },
  {
    legacyPath: "/api/v1/collections/{id}",
    canonicalPath: "/api/v1/entities/dc_collections/{id}",
    openApiPath: "/api/v1/entities/{collection}/{id}",
  },
] as const;

// DC-P1.12: Removed compatibility /api/v1/capabilities routes; use canonical /api/v1/surfaces only
interface DeprecatedRouteRedirect {
  readonly legacyPath: string;
  readonly canonicalPath: string;
  readonly openApiPath: string;
}

export const DEPRECATED_RUNTIME_TRUTH_ROUTE_REDIRECTS: readonly DeprecatedRouteRedirect[] =
  [];

export function formatDeprecatedRouteWarning(
  legacyPath: string,
  canonicalPath: string,
): string {
  return `[DEPRECATED API ROUTE] ${legacyPath} is deprecated; use ${canonicalPath}`;
}

export function warnDeprecatedRoute(
  legacyPath: string,
  canonicalPath: string,
): string {
  const warning = formatDeprecatedRouteWarning(legacyPath, canonicalPath);
  emitDataCloudDiagnostic("deprecatedRoutes", "warn", warning, {
    legacyPath,
    canonicalPath,
  });
  return warning;
}

export function buildDeprecatedRouteHeaders(
  legacyPath: string,
  canonicalPath: string,
  redirectLocation: string,
): Record<string, string> {
  const warning = formatDeprecatedRouteWarning(legacyPath, canonicalPath);
  return {
    Location: redirectLocation,
    Warning: `299 - "${warning}"`,
    "X-Deprecated-Route": legacyPath,
    "X-Replacement-Route": canonicalPath,
  };
}
