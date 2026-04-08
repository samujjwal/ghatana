/**
 * RBACGuard — Role-Based Access Control guard component.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/security-ui
 * after validation in AEP and Data Cloud.
 *
 * @doc.type component
 * @doc.purpose Conditionally render content based on user permissions
 * @doc.layer frontend
 * @doc.pattern Security Component
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';

/**
 * Permission check request
 */
interface PermissionCheckRequest {
  permission: string;
  resource?: string;
  action?: 'read' | 'write' | 'delete' | 'admin';
}

/**
 * Permission check response
 */
interface PermissionCheckResponse {
  granted: boolean;
  reason?: string;
}

/**
 * RBACGuard component props
 */
interface RBACGuardProps {
  permission: string;
  resource?: string;
  action?: 'read' | 'write' | 'delete' | 'admin';
  fallback?: React.ReactNode;
  children: React.ReactNode;
  /**
   * Optional: Loading state to show while checking permission
   */
  loadingFallback?: React.ReactNode;
  /**
   * Optional: Custom endpoint for permission check
   */
  endpoint?: string;
}

/**
 * RBACGuard component
 *
 * Conditionally renders children based on user permissions. Queries the
 * permission check endpoint and only renders children if permission is granted.
 * Shows fallback or loading state as configured.
 */
export const RBACGuard: React.FC<RBACGuardProps> = ({
  permission,
  resource,
  action,
  fallback = null,
  children,
  loadingFallback = null,
  endpoint = '/api/v1/auth/check-permission',
}) => {
  const { data: hasPermission, isLoading, error } = useQuery({
    queryKey: ['rbac', 'permission', permission, resource, action],
    queryFn: async (): Promise<boolean> => {
      const request: PermissionCheckRequest = {
        permission,
        resource,
        action,
      };

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        // If permission check fails, deny access for safety
        console.error('Permission check failed:', response.statusText);
        return false;
      }

      const data: PermissionCheckResponse = await response.json();
      return data.granted;
    },
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
    retry: false, // Don't retry permission checks
  });

  if (isLoading) {
    return <>{loadingFallback}</>;
  }

  if (error || !hasPermission) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
};

/**
 * Hook for checking permissions without rendering UI
 */
export function usePermission(
  permission: string,
  resource?: string,
  action?: 'read' | 'write' | 'delete' | 'admin',
  endpoint = '/api/v1/auth/check-permission'
) {
  const { data: hasPermission, isLoading, error, refetch } = useQuery({
    queryKey: ['rbac', 'permission', permission, resource, action],
    queryFn: async (): Promise<boolean> => {
      const request: PermissionCheckRequest = {
        permission,
        resource,
        action,
      };

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        console.error('Permission check failed:', response.statusText);
        return false;
      }

      const data: PermissionCheckResponse = await response.json();
      return data.granted;
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  return {
    hasPermission: hasPermission ?? false,
    isLoading,
    error,
    refetch,
  };
}

/**
 * Hook for checking multiple permissions at once
 */
export function usePermissions(
  checks: Array<{ permission: string; resource?: string; action?: 'read' | 'write' | 'delete' | 'admin' }>,
  endpoint = '/api/v1/auth/check-permission'
) {
  const { data: results, isLoading, error } = useQuery({
    queryKey: ['rbac', 'permissions', checks],
    queryFn: async (): Promise<Record<string, boolean>> => {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ checks }),
      });

      if (!response.ok) {
        throw new Error(`Permission check failed: ${response.statusText}`);
      }

      const data: Record<string, boolean> = await response.json();
      return data;
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  });

  const checkPermission = (
    permission: string,
    resource?: string,
    action?: 'read' | 'write' | 'delete' | 'admin'
  ): boolean => {
    const key = JSON.stringify({ permission, resource, action });
    return results?.[key] ?? false;
  };

  return {
    results: results ?? {},
    isLoading,
    error,
    checkPermission,
  };
}
