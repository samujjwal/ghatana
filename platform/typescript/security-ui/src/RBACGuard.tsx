/**
 * RBACGuard - Generic Role-Based Access Control guard component
 * 
 * @doc.type component
 * @doc.purpose Enforce role-based access control for UI components
 * @doc.layer frontend
 * @doc.pattern Security Component
 */

import React, { useState, useEffect, ReactNode } from 'react';

/**
 * Permission check response
 */
interface PermissionCheckResponse {
  granted: boolean;
  reason?: string;
}

/**
 * RBAC Guard props
 */
export interface RBACGuardProps {
  permission: string;
  resource?: string;
  action?: string;
  endpoint?: string;
  fallback?: ReactNode;
  loadingFallback?: ReactNode;
  children: ReactNode;
}

/**
 * Permission check hook
 */
export function usePermission(
  permission: string,
  resource?: string,
  action?: string,
  endpoint: string = '/api/v1/auth/check-permission'
) {
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const checkPermission = async () => {
      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            permission,
            resource,
            action,
          }),
        });

        if (!response.ok) {
          throw new Error('Permission check failed');
        }

        const result: PermissionCheckResponse = await response.json();
        setHasPermission(result.granted);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unknown error');
        setHasPermission(false);
      } finally {
        setIsLoading(false);
      }
    };

    checkPermission();
  }, [permission, resource, action, endpoint]);

  return {
    hasPermission: !!hasPermission,
    isLoading,
    error,
  };
}

/**
 * RBAC Guard Component
 */
export function RBACGuard({
  permission,
  resource,
  action,
  endpoint,
  fallback = null,
  loadingFallback = <div>Loading...</div>,
  children,
}: RBACGuardProps) {
  const { hasPermission, isLoading } = usePermission(permission, resource, action, endpoint);

  if (isLoading) {
    return <>{loadingFallback}</>;
  }

  if (!hasPermission) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
