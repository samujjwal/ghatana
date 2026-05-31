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
 * P5-05: Added route lifecycle checks (user-ready, operator-preview, internal-preview,
 * target-only, disabled) to enforce runtime truth disclosure rules.
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
  type RouteLifecycle,
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
  /**
   * P5-05: Override to allow access to preview routes for this audience.
   * Must match the route's previewAudience to access operator-preview or internal-preview routes.
   */
  allowPreviewAs?: "operator" | "admin";
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
 * P5-05: Check if route lifecycle allows access for the given shell role and preview audience.
 *
 * Lifecycle rules:
 * - user-ready: Accessible to all roles that meet minimumShellRole
 * - operator-preview: Requires operator or admin role + allowPreviewAs match
 * - internal-preview: Requires admin role only
 * - target-only: Never accessible (returns false)
 * - disabled: Never accessible (returns false)
 */
function routeLifecycleAllowsAccess(
  route: RouteSurface,
  shellRole: ShellRole,
  allowPreviewAs?: "operator" | "admin",
): { allowed: boolean; reason?: string } {
  const lifecycle: RouteLifecycle = route.lifecycle;

  // Target-only and disabled routes are never accessible
  if (lifecycle === "target-only" || lifecycle === "disabled") {
    return { allowed: false, reason: `Route is ${lifecycle}` };
  }

  // User-ready routes use standard role checking
  if (lifecycle === "user-ready" || lifecycle === "active") {
    return { allowed: true };
  }

  // Operator-preview routes require operator or admin role
  if (lifecycle === "operator-preview") {
    if (shellRole !== "operator" && shellRole !== "admin") {
      return { allowed: false, reason: "Requires operator or admin role" };
    }
    if (allowPreviewAs !== "operator" && allowPreviewAs !== "admin") {
      return { allowed: false, reason: "Preview access not granted" };
    }
    return { allowed: true };
  }

  // Internal-preview routes require admin role only
  if (lifecycle === "internal-preview") {
    if (shellRole !== "admin") {
      return { allowed: false, reason: "Requires admin role" };
    }
    if (allowPreviewAs !== "admin") {
      return { allowed: false, reason: "Internal preview access not granted" };
    }
    return { allowed: true };
  }

  // Legacy lifecycle values
  if (lifecycle === "preview") {
    if (shellRole === "primary-user") {
      return { allowed: false, reason: "Preview not available to primary users" };
    }
    if (allowPreviewAs === undefined) {
      return { allowed: false, reason: "Preview access not granted" };
    }
    return { allowed: true };
  }

  // Boundary routes require explicit opt-in (handled by caller)
  if (lifecycle === "boundary") {
    return { allowed: true }; // Caller must check includesBoundary
  }

  // Deprecated/redirect/removed routes should not be accessed directly
  if (lifecycle === "deprecated" || lifecycle === "redirect" || lifecycle === "removed") {
    return { allowed: false, reason: `Route is ${lifecycle}` };
  }

  return { allowed: true };
}

/**
 * Route-level shell-role guard for Data Cloud.
 *
 * Wraps routes that require a specific shell role. If the user's current
 * shell role is below the route's `minimumShellRole`, renders the fallback
 * or redirects to the home page.
 *
 * P5-05: Extended to enforce route lifecycle state (user-ready, operator-preview,
 * internal-preview, target-only, disabled) for runtime truth disclosure.
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
  allowPreviewAs,
}: RoleProtectedRouteProps): React.ReactElement {
  const location = useLocation();
  const pathToCheck = routePath ?? location.pathname;

  const snapshot = SessionBootstrap.bootstrap();
  const shellRole = snapshot.shellRole;

  const route = resolveRoute(pathToCheck);

  if (route) {
    // Check minimum shell role requirement
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

    // P5-05: Check route lifecycle state
    const lifecycleCheck = routeLifecycleAllowsAccess(route, shellRole, allowPreviewAs);
    if (!lifecycleCheck.allowed) {
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
            lifecycle,
            reason: lifecycleCheck.reason,
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
