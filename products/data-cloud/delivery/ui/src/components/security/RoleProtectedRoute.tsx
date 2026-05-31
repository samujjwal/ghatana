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
 * @doc.type component
 * @doc.purpose Enforce route-level shell-role access control for Data Cloud
 * @doc.layer frontend
 * @doc.pattern Security Component
 */
import React from "react";
import { Navigate, Outlet, useLocation } from "react-router";
import SessionBootstrap, { type ShellRole } from "../../lib/auth/session";
import {
  getRouteSurfaceByPath,
  type RouteSurface,
} from "../../lib/routing/RouteSurfaceRegistry";

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
function resolveRoute(pathname: string): RouteSurface | undefined {
  // Try exact match first
  const exact = getRouteSurfaceByPath(pathname);
  if (exact) return exact;

  // Try stripping trailing segments to find a parent route
  const parts = pathname.split("/").filter(Boolean);
  for (let len = parts.length - 1; len >= 1; len--) {
    const candidate = "/" + parts.slice(0, len).join("/");
    const match = getRouteSurfaceByPath(candidate);
    if (match) return match;
  }

  return undefined;
}

/**
 * Route-level shell-role guard for Data Cloud.
 *
 * Wraps routes that require a specific shell role. If the user's current
 * shell role is below the route's `minimumShellRole`, renders the fallback
 * or redirects to the home page.
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

  const snapshot = SessionBootstrap.bootstrap();
  const shellRole = snapshot.shellRole;

  const route = resolveRoute(pathToCheck);

  if (route) {
    const meetsMinimum = shellRoleMeetsMinimum(
      shellRole,
      route.minimumShellRole,
    );

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
            requiredShellRole: route.minimumShellRole,
            currentShellRole: shellRole,
          }}
        />
      );
    }
  }

  if (children) {
    return <>{children}</>;
  }

  return <Outlet />;
}
