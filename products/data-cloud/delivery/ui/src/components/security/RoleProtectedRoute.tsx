/**
 * RoleProtectedRoute — route-level guard for the Data Cloud shell.
 *
 * Enforces route-level access control using the canonical surface registry as the
 * single source of truth. Navigation visibility and route access use the same
 * source of truth (RBAC-001).
 *
 * IMPORTANT: Shell role is for UI disclosure only, not backend authorization.
 * This guard controls which routes are accessible in the shell. Backend endpoints
 * enforce authorization independently via JWT/API key validation.
 *
 * When the required shell role for a route is not met, the user is redirected
 * to the home page with an `accessDenied` state flag so the shell can display
 * an appropriate notice.
 *
 * WS1: Removed preview lifecycle checks - RuntimeCapabilityRouteGate is now the
 * single source of truth for surface gating based on backend SurfaceRecord lifecycle.
 * This guard only enforces shell role requirements and basic route accessibility.
 *
 * @doc.type component
 * @doc.purpose Enforce route-level shell-role access control for Data Cloud
 * @doc.layer frontend
 * @doc.pattern Security Component
 */
import React from "react";
import { Navigate, Outlet, useLocation } from "react-router";
import SessionBootstrap, { type ShellRole } from "../../lib/auth/session";
import { type SurfaceSignal, useSurfaceRegistry } from "../../api/surfaces.service";

interface RoleProtectedRouteProps {
  children?: React.ReactNode;
  /**
   * Override the route path to check against the registry.
   * Defaults to the current location pathname.
   */
  routePath?: string;
  /**
   * Fallback element rendered when the shell role is insufficient.
   * When omitted, redirects to "/" with accessDenied state.
   */
  fallback?: React.ReactNode;
}

/**
 * Returns the shell role hierarchy value for comparison.
 */
const SHELL_ROLE_ORDER: Record<ShellRole, number> = {
  "primary-user": 0,
  operator: 1,
  admin: 2,
};

function shellRoleMeetsMinimum(
  current: ShellRole,
  required: ShellRole,
): boolean {
  return SHELL_ROLE_ORDER[current] >= SHELL_ROLE_ORDER[required];
}

/**
 * Resolve the canonical route from the registry for a given path.
 * Handles parameterized segments (e.g., /operations/jobs/:id → /operations/jobs).
 */
function normalizePath(value: string): string {
  if (!value.startsWith("/")) {
    return `/${value}`;
  }
  return value;
}

function resolveRoute(
  pathname: string,
  surfaces: readonly SurfaceSignal[],
): SurfaceSignal | undefined {
  const path = normalizePath(pathname);
  const byPath = new Map<string, SurfaceSignal>();
  for (const surface of surfaces) {
    if (surface.path) {
      byPath.set(normalizePath(surface.path), surface);
    }
  }

  const exact = byPath.get(path);
  if (exact) {
    return exact;
  }

  const parts = path.split("/").filter(Boolean);
  for (let len = parts.length - 1; len >= 1; len--) {
    const candidate = `/${parts.slice(0, len).join("/")}`;
    const match = byPath.get(candidate);
    if (match) {
      return match;
    }
  }

  return undefined;
}

function isPrimaryPolicyRoute(path: string): boolean {
  const normalized = normalizePath(path);
  if (normalized === "/" || normalized === "/data") {
    return true;
  }
  return normalized.startsWith("/data/");
}

/**
 * Route-level shell-role guard for Data Cloud.
 *
 * Wraps routes that require a specific shell role. If the user's current
 * shell role is below the route's `minimumShellRole`, renders the fallback
 * or redirects to the home page.
 *
 * WS1: Removed lifecycle checks - RuntimeCapabilityRouteGate is now the
 * single source of truth for surface gating based on backend SurfaceRecord.
 * This guard only enforces shell role requirements.
 *
 * Usage in routes.tsx:
 * ```tsx
 * {
 *   element: <RoleProtectedRoute />,
 *   children: [
 *     { path: '/operations', element: <OperationsConsolePage /> },
 *   ]
 * }
 * ```
 */
export function RoleProtectedRoute({
  children,
  routePath,
  fallback,
}: RoleProtectedRouteProps): React.ReactElement {
  const location = useLocation();
  const pathToCheck = routePath ?? location.pathname;
  const { data: surfaceData, isLoading } = useSurfaceRegistry();

  const snapshot = SessionBootstrap.bootstrap();
  const shellRole = snapshot.shellRole;

  if (isLoading) {
    return (
      <div className="p-4 text-sm text-gray-600 dark:text-gray-300" role="status">
        Loading route policy...
      </div>
    );
  }

  if (!surfaceData?.surfaces?.length) {
    if (!isPrimaryPolicyRoute(pathToCheck)) {
      return <Navigate to="/" replace state={{ accessDenied: true, from: pathToCheck }} />;
    }

    if (children) {
      return <>{children}</>;
    }
    return <Outlet />;
  }

  const route = resolveRoute(pathToCheck, surfaceData.surfaces);

  if (!route) {
    return <Navigate to="/" replace state={{ accessDenied: true, from: pathToCheck }} />;
  }

  if (route.targetOnly || route.readinessClass === "target-only") {
    return <Navigate to="/" replace state={{ accessDenied: true, from: pathToCheck }} />;
  }

  // Check minimum shell role requirement
  const requiredRole = (route.minimumShellRole as ShellRole) ?? "primary-user";
  const meetsMinimum = shellRoleMeetsMinimum(shellRole, requiredRole);

  if (!meetsMinimum) {
    if (fallback) {
      return <>{fallback}</>;
    }
    return (
      <Navigate
        to="/"
        replace
        state={{
          accessDenied: true,
          from: pathToCheck,
          requiredShellRole: requiredRole,
          currentShellRole: shellRole,
        }}
      />
    );
  }

  if (children) {
    return <>{children}</>;
  }

  return <Outlet />;
}
