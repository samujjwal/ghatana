/**
 * PersonaGuard Component
 *
 * Route guard that protects routes based on persona type and permissions.
 * Uses @ghatana/ui ProtectedRoute as the base and adds persona-level authorization.
 * Also integrates with entry point access control.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState, useEffect } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ProtectedRoute } from '@ghatana/ui';
import { usePersona } from '@/hooks/usePersona';
import { useEntryPoints } from '@/hooks/useEntryPoints';
import type { PersonaType } from '@/state/atoms/persona.atoms';

export interface PersonaGuardProps {
  /** Child components to render if authorized */
  children: React.ReactNode;

  /** List of allowed persona types for this route */
  allowedPersonas: PersonaType[];

  /** Optional: Specific permissions required (in addition to persona type) */
  requiredPermissions?: string[];

  /** Optional: Custom unauthorized redirect path */
  unauthorizedRedirect?: string;

  /** Optional: Custom unauthenticated redirect path */
  unauthenticatedRedirect?: string;

  /** Optional: Require ALL permissions (default) or ANY permission */
  permissionMode?: 'all' | 'any';

  /** Optional: Entry point ID to check access against */
  entryPointId?: string;

  /** Optional: Check entry point access for current route automatically */
  checkEntryPointAccess?: boolean;
}

/**
 * PersonaGuard Component
 *
 * Provides two-level route protection:
 * 1. Authentication check (via platform ProtectedRoute)
 * 2. Persona-based authorization check
 * 3. Optional permission check
 *
 * @example
 * ```tsx
 * // Simple persona check
 * <PersonaGuard allowedPersonas={['owner', 'admin']}>
 *   <AdminPage />
 * </PersonaGuard>
 *
 * // With permission requirement
 * <PersonaGuard
 *   allowedPersonas={['owner', 'manager']}
 *   requiredPermissions={['org:restructure']}
 * >
 *   <RestructurePage />
 * </PersonaGuard>
 * ```
 */
export function PersonaGuard({
  children,
  allowedPersonas,
  requiredPermissions = [],
  unauthorizedRedirect = '/',
  unauthenticatedRedirect = '/login',
  permissionMode = 'all',
  entryPointId,
  checkEntryPointAccess: checkEntryPoint = false,
}: PersonaGuardProps) {
  const location = useLocation();
  const {
    persona,
    personaType,
    hasPermission,
    hasAllPermissions,
    hasAnyPermission,
    isAuthenticated,
    isRootUser,
  } = usePersona();
  const { canAccess, canAccessRoute, checkAccess } = useEntryPoints();

  // Track hydration state - atomWithStorage returns null until client hydration
  const [isHydrated, setIsHydrated] = useState(false);

  useEffect(() => {
    setIsHydrated(true);
  }, []);

  /**
   * Check if persona has required permissions
   */
  const hasRequiredPermissions = (): boolean => {
    // Root users always have permissions
    if (isRootUser) return true;

    if (requiredPermissions.length === 0) return true;

    return permissionMode === 'all'
      ? hasAllPermissions(requiredPermissions)
      : hasAnyPermission(requiredPermissions);
  };

  /**
   * Check if persona type is allowed
   */
  const isPersonaAllowed = (): boolean => {
    // Root users are always allowed
    if (isRootUser) return true;

    if (!personaType) return false;
    return allowedPersonas.includes(personaType);
  };

  /**
   * Check entry point access
   */
  const hasEntryPointAccess = (): boolean => {
    // Root users always have entry point access
    if (isRootUser) return true;

    // Check specific entry point if provided
    if (entryPointId) {
      return canAccess(entryPointId);
    }

    // Check current route if enabled
    if (checkEntryPoint) {
      return canAccessRoute(location.pathname);
    }

    // If no entry point check requested, allow access
    return true;
  };

  /**
   * Render unauthorized page
   */
  const renderUnauthorized = () => (
    <div className="flex items-center justify-center min-h-screen bg-slate-50 dark:bg-neutral-800">
      <div className="max-w-md p-8 bg-white rounded-lg shadow-lg text-center">
        <div className="mb-4 text-red-600">
          <svg
            className="w-16 h-16 mx-auto"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>
        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
          Access Denied
        </h2>
        <p className="text-slate-600 dark:text-neutral-400 mb-6">
          You don't have permission to access this page.
          {personaType && (
            <span className="block mt-2 text-sm">
              Current persona: <strong>{personaType}</strong>
            </span>
          )}
        </p>
        <button
          onClick={() => (window.location.href = unauthorizedRedirect)}
          className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition"
        >
          Go to Dashboard
        </button>
      </div>
    </div>
  );

  /**
   * Render loading state during hydration
   */
  const renderLoading = () => (
    <div className="flex items-center justify-center min-h-screen bg-slate-50 dark:bg-slate-900">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <p className="text-slate-600 dark:text-neutral-400">Loading...</p>
      </div>
    </div>
  );

  // Show loading state during SSR/hydration - atomWithStorage returns null until hydrated
  if (!isHydrated) {
    return renderLoading();
  }

  // Use platform ProtectedRoute for authentication check
  return (
    <ProtectedRoute
      isAuthenticated={() => isAuthenticated}
      fallback={
        <Navigate
          to={unauthenticatedRedirect}
          state={{ from: location }}
          replace
        />
      }
    >
      {/* Persona-level authorization check */}
      {!isPersonaAllowed() ? (
        renderUnauthorized()
      ) : !hasRequiredPermissions() ? (
        renderUnauthorized()
      ) : !hasEntryPointAccess() ? (
        renderUnauthorized()
      ) : (
        children
      )}
    </ProtectedRoute>
  );
}

/**
 * Higher-order component version of PersonaGuard
 *
 * @example
 * ```tsx
 * const ProtectedAdminPage = withPersonaGuard(AdminPage, ['owner', 'admin']);
 * ```
 */
export function withPersonaGuard<P extends object = object>(
  Component: React.ComponentType<P>,
  allowedPersonas: PersonaType[],
  requiredPermissions?: string[]
) {
  return function GuardedComponent(props: P) {
    return (
      <PersonaGuard
        allowedPersonas={allowedPersonas}
        requiredPermissions={requiredPermissions}
      >
        <Component {...props} />
      </PersonaGuard>
    );
  };
}

/**
 * Utility function to check if current persona can access a route
 * Useful for conditional rendering or navigation logic
 *
 * @example
 * ```tsx
 * const canAccessAdmin = useCanAccessRoute(['owner', 'admin']);
 * {canAccessAdmin && <AdminLink />}
 * ```
 */
export function useCanAccessRoute(
  allowedPersonas: PersonaType[],
  requiredPermissions: string[] = []
): boolean {
  const { personaType, hasAllPermissions, isRootUser } = usePersona();

  // Root users always have access
  if (isRootUser) return true;

  if (!personaType) return false;
  if (!allowedPersonas.includes(personaType)) return false;
  if (requiredPermissions.length === 0) return true;

  return hasAllPermissions(requiredPermissions);
}

/**
 * Entry Point Guard Component
 *
 * Route guard that protects routes based on entry point access rules.
 * Use this for routes that should be controlled by entry point configuration.
 *
 * @example
 * ```tsx
 * <EntryPointGuard entryPointId="admin-dashboard">
 *   <AdminDashboard />
 * </EntryPointGuard>
 * ```
 */
export function EntryPointGuard({
  children,
  entryPointId,
  unauthorizedRedirect = '/',
  unauthenticatedRedirect = '/login',
}: {
  children: React.ReactNode;
  entryPointId: string;
  unauthorizedRedirect?: string;
  unauthenticatedRedirect?: string;
}) {
  const { canAccess, checkAccess } = useEntryPoints();
  const { isAuthenticated, isRootUser, personaType } = usePersona();
  const location = useLocation();
  const [isHydrated, setIsHydrated] = useState(false);

  useEffect(() => {
    setIsHydrated(true);
  }, []);

  const accessResult = checkAccess(entryPointId);

  const renderUnauthorized = () => (
    <div className="flex items-center justify-center min-h-screen bg-slate-50 dark:bg-neutral-800">
      <div className="max-w-md p-8 bg-white rounded-lg shadow-lg text-center">
        <div className="mb-4 text-red-600">
          <svg
            className="w-16 h-16 mx-auto"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>
        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
          Access Denied
        </h2>
        <p className="text-slate-600 dark:text-neutral-400 mb-4">
          {accessResult.reason || "You don't have permission to access this entry point."}
        </p>
        {personaType && (
          <p className="text-sm text-slate-500 dark:text-neutral-500 mb-6">
            Current persona: <strong>{personaType}</strong>
          </p>
        )}
        <button
          onClick={() => (window.location.href = unauthorizedRedirect)}
          className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition"
        >
          Go to Dashboard
        </button>
      </div>
    </div>
  );

  const renderLoading = () => (
    <div className="flex items-center justify-center min-h-screen bg-slate-50 dark:bg-slate-900">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <p className="text-slate-600 dark:text-neutral-400">Loading...</p>
      </div>
    </div>
  );

  if (!isHydrated) {
    return renderLoading();
  }

  return (
    <ProtectedRoute
      isAuthenticated={() => isAuthenticated}
      fallback={
        <Navigate
          to={unauthenticatedRedirect}
          state={{ from: location }}
          replace
        />
      }
    >
      {accessResult.allowed ? children : renderUnauthorized()}
    </ProtectedRoute>
  );
}

