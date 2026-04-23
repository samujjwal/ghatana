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
import { apiClient } from '@/lib/http-client';

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

      const { data } = await apiClient.post<PermissionCheckResponse>(endpoint, request);
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

      const { data } = await apiClient.post<PermissionCheckResponse>(endpoint, request);
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
      const { data } = await apiClient.post<Record<string, boolean>>(endpoint, { checks });
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
    const arrayKey = JSON.stringify([{ permission, resource, action }]);
    return results?.[key] ?? results?.[arrayKey] ?? false;
  };

  return {
    results: results ?? {},
    isLoading,
    error,
    checkPermission,
  };
}
