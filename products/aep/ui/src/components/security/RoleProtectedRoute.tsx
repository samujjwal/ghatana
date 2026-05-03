/**
 * Role-protected route guard for the AEP console.
 *
 * Extends ProtectedRoute with route-level RBAC. Navigates to a
 * fallback page when the user is authenticated but lacks the required role.
 *
 * @doc.type component
 * @doc.purpose Enforce route-level role-based access control
 * @doc.layer frontend
 */
import React from "react";
import { Navigate, Outlet, useLocation } from "react-router";
import { useAuth } from "@/context/AuthContext";
import type { UserRole } from "@/lib/routing/RouteCapabilityRegistry";
import { canAccessRoute } from "@/lib/routing/RouteCapabilityRegistry";

interface RoleProtectedRouteProps {
  children?: React.ReactNode;
  /** Required roles — user must have at least one */
  requiredRoles?: UserRole[];
  /** Route path to check against the registry (defaults to current location) */
  routePath?: string;
  /** Optional fallback element when access is denied */
  fallback?: React.ReactNode;
}

export function RoleProtectedRoute({
  children,
  requiredRoles,
  routePath,
  fallback,
}: RoleProtectedRouteProps) {
  const { isAuthenticated, isVerifyingAuth, roles, hasAnyRole } = useAuth();
  const location = useLocation();

  if (isVerifyingAuth) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-white text-sm text-gray-500 dark:bg-gray-950 dark:text-gray-300">
        Verifying access…
      </div>
    );
  }

  if (!isAuthenticated) {
    const redirectTarget = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" replace state={{ from: redirectTarget }} />;
  }

  // If specific roles are provided, check them
  if (requiredRoles && requiredRoles.length > 0) {
    const hasRequiredRole = hasAnyRole(requiredRoles);
    if (!hasRequiredRole) {
      if (fallback) {
        return <>{fallback}</>;
      }
      return (
        <Navigate
          to="/operate"
          replace
          state={{
            accessDenied: true,
            from: location.pathname,
            requiredRoles,
          }}
        />
      );
    }
  }

  // If a routePath is provided, also check registry-based capability
  const pathToCheck = routePath ?? location.pathname;
  const effectiveRole =
    roles.find((r) => ["admin", "operator", "viewer"].includes(r)) ?? "viewer";
  const registryAllows = canAccessRoute(
    effectiveRole as UserRole,
    pathToCheck
  );
  if (!registryAllows) {
    if (fallback) {
      return <>{fallback}</>;
    }
    return (
      <Navigate
        to="/operate"
        replace
        state={{ accessDenied: true, from: pathToCheck }}
      />
    );
  }

  if (children) {
    return <>{children}</>;
  }

  return <Outlet />;
}
