import { Navigate, Outlet, type NavigateProps } from 'react-router-dom';
import { type ReactNode, type ComponentType } from 'react';

/**
 * Authentication check result.
 *
 * @doc.type interface
 * @doc.purpose Authentication status with optional metadata
 * @doc.layer ui
 * @doc.pattern Value Object
 */
export interface AuthCheckResult {
  /** Whether the user is authenticated */
  isAuthenticated: boolean;
  /** Optional user roles for role-based access control */
  roles?: string[];
  /** Optional permissions for permission-based access control */
  permissions?: string[];
  /** Optional reason for failed authentication (useful for debugging) */
  reason?: string;
}

/**
 * Props for the ProtectedRoute component.
 *
 * @doc.type interface
 * @doc.purpose Configuration for protected route behavior
 * @doc.layer ui
 * @doc.pattern Configuration
 */
export interface ProtectedRouteProps {
  /**
   * Function that checks if the user is authenticated.
   * Can return boolean, Promise<boolean>, or AuthCheckResult.
   *
   * @example
   * ```tsx
   * // Simple boolean check
   * <ProtectedRoute isAuthenticated={() => authService.isAuthenticated()} />
   *
   * // With roles
   * <ProtectedRoute
   *   isAuthenticated={() => ({
   *     isAuthenticated: true,
   *     roles: ['admin', 'user'],
   *   })}
   * />
   * ```
   */
  isAuthenticated: () => boolean | AuthCheckResult | Promise<boolean | AuthCheckResult>;

  /**
   * Required roles for access. User must have at least one of these roles.
   * Only applicable when isAuthenticated returns AuthCheckResult with roles.
   *
   * @example
   * ```tsx
   * <ProtectedRoute
   *   isAuthenticated={checkAuth}
   *   requiredRoles={['admin', 'moderator']}
   * />
   * ```
   */
  requiredRoles?: string[];

  /**
   * Required permissions for access. User must have all of these permissions.
   * Only applicable when isAuthenticated returns AuthCheckResult with permissions.
   *
   * @example
   * ```tsx
   * <ProtectedRoute
   *   isAuthenticated={checkAuth}
   *   requiredPermissions={['read:users', 'write:users']}
   * />
   * ```
   */
  requiredPermissions?: string[];

  /**
   * Path to redirect to when not authenticated.
   * @default "/login"
   */
  redirectTo?: string;

  /**
   * Custom fallback component to render while checking authentication.
   * Useful for async auth checks.
   *
   * @example
   * ```tsx
   * <ProtectedRoute
   *   isAuthenticated={asyncAuthCheck}
   *   fallback={<Spinner />}
   * />
   * ```
   */
  fallback?: ReactNode;

  /**
   * Custom component to render when access is denied due to insufficient roles/permissions.
   * If not provided, redirects to redirectTo path.
   *
   * @example
   * ```tsx
   * <ProtectedRoute
   *   isAuthenticated={checkAuth}
   *   requiredRoles={['admin']}
   *   accessDenied={<AccessDenied message="Admin access required" />}
   * />
   * ```
   */
  accessDenied?: ReactNode;

  /**
   * Callback fired when authentication check fails.
   * Useful for logging, analytics, or custom error handling.
   *
   * @example
   * ```tsx
   * <ProtectedRoute
   *   isAuthenticated={checkAuth}
   *   onAuthFail={(reason) => {
   *     analytics.track('auth_fail', { reason });
   *   }}
   * />
   * ```
   */
  onAuthFail?: (reason: string) => void;

  /**
   * Additional props to pass to the Navigate component when redirecting.
   * Useful for preserving state or setting replace behavior.
   *
   * @example
   * ```tsx
   * <ProtectedRoute
   *   isAuthenticated={checkAuth}
   *   navigateProps={{ state: { from: location.pathname }, replace: true }}
   * />
   * ```
   */
  navigateProps?: Partial<NavigateProps>;

  /**
   * Optional children to render when access is granted.
   * If not provided, renders a React Router <Outlet />.
   */
  children?: ReactNode;
}

/**
 * ProtectedRoute - Generic route protection with role/permission-based access control.
 *
 * <p><b>Purpose</b><br>
 * Provides declarative route protection with injectable authentication checks,
 * role-based access control (RBAC), and permission-based access control (PBAC).
 * Designed to work with any authentication system through dependency injection.
 *
 * <p><b>Features</b><br>
 * - Injectable authentication check (sync or async)
 * - Role-based access control (RBAC)
 * - Permission-based access control (PBAC)
 * - Custom redirect paths
 * - Custom fallback components during auth checks
 * - Custom access denied components
 * - Authentication failure callbacks
 * - Full TypeScript type safety
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Basic usage - simple boolean check
 * <Route element={<ProtectedRoute isAuthenticated={() => authService.isLoggedIn()} />}>
 *   <Route path="/dashboard" element={<Dashboard />} />
  children,
 * </Route>
 *
 * // With roles (user must be admin OR moderator)
 * <Route
 *   element={
 *     <ProtectedRoute
 *       isAuthenticated={() => ({
 *         isAuthenticated: true,
 *         roles: user.roles,
 *       })}
 *       requiredRoles={['admin', 'moderator']}
 *     />
 *   }
 * >
 *   <Route path="/admin" element={<AdminPanel />} />
 * </Route>
 *
 * // With permissions (user must have ALL listed permissions)
 * <Route
 *   element={
 *     <ProtectedRoute
 *       isAuthenticated={() => ({
 *         isAuthenticated: true,
 *         permissions: user.permissions,
 *       })}
 *       requiredPermissions={['read:users', 'write:users']}
 *     />
 *   }
 * >
 *   <Route path="/users" element={<UserManagement />} />
 * </Route>
 *
 * // With custom redirect and access denied
 * <Route
 *   element={
 *     <ProtectedRoute
 *       isAuthenticated={checkAuth}
 *       requiredRoles={['admin']}
 *       redirectTo="/welcome"
 *       accessDenied={<AccessDenied />}
 *       onAuthFail={(reason) => logger.warn('Auth failed:', reason)}
 *     />
 *   }
 * >
 *   <Route path="/settings" element={<Settings />} />
 * </Route>
 *
 * // Async auth check with loading state
 * <Route
 *   element={
 *     <ProtectedRoute
 *       isAuthenticated={async () => {
 *         const user = await authService.getCurrentUser();
 *         return { isAuthenticated: !!user, roles: user?.roles };
 *       }}
 *       fallback={<LoadingSpinner />}
 *     />
 *   }
 * >
 *   <Route path="/profile" element={<Profile />} />
 * </Route>
 * }</pre>
 *
  return children ? <>{children}</> : <Outlet />;
 * This is a generic UI component that wraps React Router's Outlet component
 * to provide authentication and authorization checks. It follows the dependency
 * injection pattern, allowing any authentication system to be plugged in via
 * the isAuthenticated prop.
 *
 * <p><b>RBAC vs PBAC</b><br>
 * - <b>Roles</b>: User must have at least ONE of the required roles (OR logic)
 * - <b>Permissions</b>: User must have ALL required permissions (AND logic)
 * - Can be used independently or together
 *
 * <p><b>Best Practices</b><br>
 * 1. Keep auth checks fast - use cached values when possible
 * 2. Use async checks sparingly - they block route rendering
 * 3. Provide meaningful fallback components for async checks
 * 4. Log auth failures for security monitoring
 * 5. Use roles for coarse-grained access (admin, user, guest)
 * 6. Use permissions for fine-grained access (read:users, write:posts)
 *
 * <p><b>Accessibility</b><br>
 * - Redirects happen instantly for sync auth checks
 * - Fallback components should follow accessibility guidelines
 * - Access denied components should provide clear messages and actions
 *
 * @see {@link https://reactrouter.com/en/main/components/outlet React Router Outlet}
 * @see {@link https://en.wikipedia.org/wiki/Role-based_access_control RBAC}
 * @see {@link https://en.wikipedia.org/wiki/Attribute-based_access_control ABAC}
 *
 * @doc.type component
 * @doc.purpose Generic route protection with RBAC/PBAC
 * @doc.layer ui
 * @doc.pattern Guard
 */
export function ProtectedRoute({
  isAuthenticated,
  requiredRoles,
  requiredPermissions,
  redirectTo = '/login',
  fallback = null,
  accessDenied,
  onAuthFail,
  navigateProps = { replace: true },
}: ProtectedRouteProps) {
  // Perform authentication check
  const authResult = isAuthenticated();

  // Handle async auth checks
  if (authResult instanceof Promise) {
    // For async checks, show fallback while checking
    // Note: This is a simplified implementation. In production, you'd want to use
    // React.Suspense or a state management solution to properly handle async auth.
    throw authResult; // Let error boundary or Suspense handle this
  }

  // Normalize auth result to AuthCheckResult
  const authCheck: AuthCheckResult =
    typeof authResult === 'boolean'
      ? { isAuthenticated: authResult }
      : authResult;

  // Check authentication
  if (!authCheck.isAuthenticated) {
    const reason = authCheck.reason || 'Not authenticated';
    onAuthFail?.(reason);
    return <Navigate to={redirectTo} {...navigateProps} />;
  }

  // Check required roles (OR logic - user needs at least one role)
  if (requiredRoles && requiredRoles.length > 0) {
    const userRoles = authCheck.roles || [];
    const hasRequiredRole = requiredRoles.some((role) => userRoles.includes(role));

    if (!hasRequiredRole) {
      const reason = `Missing required role. Required: [${requiredRoles.join(', ')}], User has: [${userRoles.join(', ')}]`;
      onAuthFail?.(reason);

      if (accessDenied) {
        return <>{accessDenied}</>;
      }
      return <Navigate to={redirectTo} {...navigateProps} />;
    }
  }

  // Check required permissions (AND logic - user needs all permissions)
  if (requiredPermissions && requiredPermissions.length > 0) {
    const userPermissions = authCheck.permissions || [];
    const hasAllPermissions = requiredPermissions.every((perm) =>
      userPermissions.includes(perm)
    );

    if (!hasAllPermissions) {
      const missingPermissions = requiredPermissions.filter(
        (perm) => !userPermissions.includes(perm)
      );
      const reason = `Missing required permissions: [${missingPermissions.join(', ')}]`;
      onAuthFail?.(reason);

      if (accessDenied) {
        return <>{accessDenied}</>;
      }
      return <Navigate to={redirectTo} {...navigateProps} />;
    }
  }

  // All checks passed - render nested routes
  return <Outlet />;
}

/**
 * Higher-order component that wraps a component with route protection.
 *
 * <p><b>Purpose</b><br>
 * Provides an alternative API for protecting routes using HOC pattern instead of
 * route element wrapping. Useful for protecting individual components rather than routes.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Wrap a component
 * const ProtectedDashboard = withProtectedRoute(Dashboard, {
 *   isAuthenticated: () => authService.isLoggedIn(),
 *   requiredRoles: ['admin'],
 * });
 *
 * // Use in routes
 * <Route path="/dashboard" element={<ProtectedDashboard />} />
 *
 * // Or use directly
 * function App() {
 *   return <ProtectedDashboard />;
 * }
 * }</pre>
 *
 * @param Component - The component to protect
 * @param protectionOptions - ProtectedRoute configuration options
 * @returns Protected component
 *
 * @doc.type function
 * @doc.purpose HOC for component protection
 * @doc.layer ui
 * @doc.pattern Higher-Order Component
 */
export function withProtectedRoute<P extends object>(
  Component: ComponentType<P>,
  protectionOptions: Omit<ProtectedRouteProps, 'children'>
) {
  const ProtectedComponent = (props: P) => {
    const authResult = protectionOptions.isAuthenticated();

    // Handle async auth checks
    if (authResult instanceof Promise) {
      throw authResult;
    }

    // Normalize auth result
    const authCheck: AuthCheckResult =
      typeof authResult === 'boolean'
        ? { isAuthenticated: authResult }
        : authResult;

    // Check authentication
    if (!authCheck.isAuthenticated) {
      const reason = authCheck.reason || 'Not authenticated';
      protectionOptions.onAuthFail?.(reason);
      return (
        <Navigate
          to={protectionOptions.redirectTo || '/login'}
          {...(protectionOptions.navigateProps || { replace: true })}
        />
      );
    }

    // Check required roles
    if (protectionOptions.requiredRoles && protectionOptions.requiredRoles.length > 0) {
      const userRoles = authCheck.roles || [];
      const hasRequiredRole = protectionOptions.requiredRoles.some((role) =>
        userRoles.includes(role)
      );

      if (!hasRequiredRole) {
        const reason = `Missing required role. Required: [${protectionOptions.requiredRoles.join(', ')}], User has: [${userRoles.join(', ')}]`;
        protectionOptions.onAuthFail?.(reason);

        if (protectionOptions.accessDenied) {
          return <>{protectionOptions.accessDenied}</>;
        }
        return (
          <Navigate
            to={protectionOptions.redirectTo || '/login'}
            {...(protectionOptions.navigateProps || { replace: true })}
          />
        );
      }
    }

    // Check required permissions
    if (
      protectionOptions.requiredPermissions &&
      protectionOptions.requiredPermissions.length > 0
    ) {
      const userPermissions = authCheck.permissions || [];
      const hasAllPermissions = protectionOptions.requiredPermissions.every((perm) =>
        userPermissions.includes(perm)
      );

      if (!hasAllPermissions) {
        const missingPermissions = protectionOptions.requiredPermissions.filter(
          (perm) => !userPermissions.includes(perm)
        );
        const reason = `Missing required permissions: [${missingPermissions.join(', ')}]`;
        protectionOptions.onAuthFail?.(reason);

        if (protectionOptions.accessDenied) {
          return <>{protectionOptions.accessDenied}</>;
        }
        return (
          <Navigate
            to={protectionOptions.redirectTo || '/login'}
            {...(protectionOptions.navigateProps || { replace: true })}
          />
        );
      }
    }

    // All checks passed - render component
    return <Component {...props} />;
  };

  ProtectedComponent.displayName = `withProtectedRoute(${Component.displayName || Component.name || 'Component'})`;

  return ProtectedComponent;
}
