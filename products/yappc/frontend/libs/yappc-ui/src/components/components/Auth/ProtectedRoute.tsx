/**
 * Protected Route Component
 * 
 * Production-grade route protection with role-based access control
 * 
 * @module ui/components/Auth/ProtectedRoute
 * @doc.type component
 * @doc.purpose Route authorization and access control
 * @doc.layer ui
 */

import React, { type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spinner } from '../Loading';

// ============================================================================
// Types
// ============================================================================

export interface ProtectedRouteProps {
  /** Content to render when authorized */
  children: ReactNode;
  
  /** Whether user is authenticated */
  isAuthenticated: boolean;
  
  /** Whether authentication is being checked */
  isLoading?: boolean;
  
  /** Required roles (user must have at least one) */
  requiredRoles?: string[];
  
  /** Required permissions (user must have at least one) */
  requiredPermissions?: string[];
  
  /** User's current roles */
  userRoles?: string[];
  
  /** User's current permissions */
  userPermissions?: string[];
  
  /** Redirect path when not authenticated */
  redirectTo?: string;
  
  /** Redirect path when not authorized */
  unauthorizedRedirectTo?: string;
  
  /** Custom fallback component when loading */
  loadingFallback?: ReactNode;
  
  /** Custom fallback component when unauthorized */
  unauthorizedFallback?: ReactNode;
}

// ============================================================================
// Component
// ============================================================================

/**
 * Protected route component with role-based access control
 * 
 * @example Basic authentication check
 * ```tsx
 * <ProtectedRoute isAuthenticated={isAuthenticated}>
 *   <Dashboard />
 * </ProtectedRoute>
 * ```
 * 
 * @example Role-based access
 * ```tsx
 * <ProtectedRoute
 *   isAuthenticated={isAuthenticated}
 *   requiredRoles={['admin', 'moderator']}
 *   userRoles={user?.roles}
 * >
 *   <AdminPanel />
 * </ProtectedRoute>
 * ```
 * 
 * @example Permission-based access
 * ```tsx
 * <ProtectedRoute
 *   isAuthenticated={isAuthenticated}
 *   requiredPermissions={['write', 'delete']}
 *   userPermissions={user?.permissions}
 * >
 *   <ContentEditor />
 * </ProtectedRoute>
 * ```
 */
export function ProtectedRoute({
  children,
  isAuthenticated,
  isLoading = false,
  requiredRoles = [],
  requiredPermissions = [],
  userRoles = [],
  userPermissions = [],
  redirectTo = '/login',
  unauthorizedRedirectTo = '/unauthorized',
  loadingFallback,
  unauthorizedFallback,
}: ProtectedRouteProps): React.JSX.Element {
  const location = useLocation();

  // Show loading state
  if (isLoading) {
    if (loadingFallback) {
      return <>{loadingFallback}</>;
    }
    return <Spinner size="lg" centered fullscreen />;
  }

  // Check authentication
  if (!isAuthenticated) {
    return <Navigate to={redirectTo} state={{ from: location }} replace />;
  }

  // Check role-based access
  if (requiredRoles.length > 0) {
    const hasRequiredRole = requiredRoles.some((role) =>
      userRoles.includes(role)
    );

    if (!hasRequiredRole) {
      if (unauthorizedFallback) {
        return <>{unauthorizedFallback}</>;
      }
      return (
        <Navigate
          to={unauthorizedRedirectTo}
          state={{ from: location, reason: 'insufficient_roles' }}
          replace
        />
      );
    }
  }

  // Check permission-based access
  if (requiredPermissions.length > 0) {
    const hasRequiredPermission = requiredPermissions.some((permission) =>
      userPermissions.includes(permission)
    );

    if (!hasRequiredPermission) {
      if (unauthorizedFallback) {
        return <>{unauthorizedFallback}</>;
      }
      return (
        <Navigate
          to={unauthorizedRedirectTo}
          state={{ from: location, reason: 'insufficient_permissions' }}
          replace
        />
      );
    }
  }

  // Render protected content
  return <>{children}</>;
}

/**
 * HOC for protecting routes with authentication and authorization
 * 
 * @example
 * ```tsx
 * const ProtectedDashboard = withProtectedRoute(Dashboard, {
 *   requiredRoles: ['user'],
 * });
 * ```
 */
export function withProtectedRoute<P extends object>(
  Component: React.ComponentType<P>,
  protectionConfig: Omit<ProtectedRouteProps, 'children' | 'isAuthenticated' | 'isLoading'>
) {
  return function ProtectedComponent(props: P & {
    isAuthenticated: boolean;
    isLoading?: boolean;
  }) {
    const { isAuthenticated, isLoading, ...componentProps } = props;
    
    return (
      <ProtectedRoute
        isAuthenticated={isAuthenticated}
        isLoading={isLoading}
        {...protectionConfig}
      >
        <Component {...(componentProps as P)} />
      </ProtectedRoute>
    );
  };
}

/**
 * Hook-based route protection utility
 * Returns boolean indicating if user has access
 */
export function useRouteAccess({
  isAuthenticated,
  requiredRoles = [],
  requiredPermissions = [],
  userRoles = [],
  userPermissions = [],
}: Omit<ProtectedRouteProps, 'children' | 'redirectTo' | 'unauthorizedRedirectTo'>): {
  hasAccess: boolean;
  reason?: 'not_authenticated' | 'insufficient_roles' | 'insufficient_permissions';
} {
  if (!isAuthenticated) {
    return { hasAccess: false, reason: 'not_authenticated' };
  }

  if (requiredRoles.length > 0) {
    const hasRequiredRole = requiredRoles.some((role) =>
      userRoles.includes(role)
    );
    if (!hasRequiredRole) {
      return { hasAccess: false, reason: 'insufficient_roles' };
    }
  }

  if (requiredPermissions.length > 0) {
    const hasRequiredPermission = requiredPermissions.some((permission) =>
      userPermissions.includes(permission)
    );
    if (!hasRequiredPermission) {
      return { hasAccess: false, reason: 'insufficient_permissions' };
    }
  }

  return { hasAccess: true };
}
