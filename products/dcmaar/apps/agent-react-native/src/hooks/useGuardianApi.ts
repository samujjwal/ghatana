/**
 * React Query Hooks for Guardian API
 *
 * TanStack Query (React Query) hooks for backend API integration.
 * Handles caching, background refetching, stale-while-revalidate patterns.
 *
 * Features:
 * - Automatic caching and background updates
 * - Optimistic updates support
 * - Error boundary integration
 * - Retry on failure with exponential backoff
 * - Mutations for create/update/delete operations
 *
 * @see ../services/guardianApi.ts for API client
 */

import { useQuery, useMutation, useQueryClient, UseQueryResult, UseMutationResult } from 'react-query';
import {
  guardianApi,
  AppData,
  PolicyData,
  RecommendationData,
  DeviceStatusData,
  PaginatedResponse,
  AppFilters,
} from '../services/guardianApi';

// ============================================================
// Query Options & Keys
// ============================================================

export const appsQueryKeys = {
  all: ['apps'] as const,
  lists: () => [...appsQueryKeys.all, 'list'] as const,
  list: (tenantId: string, filters?: AppFilters) => [...appsQueryKeys.lists(), tenantId, filters] as const,
  details: () => [...appsQueryKeys.all, 'detail'] as const,
  detail: (tenantId: string, appId: string) => [...appsQueryKeys.details(), tenantId, appId] as const,
};

export const policiesQueryKeys = {
  all: ['policies'] as const,
  lists: () => [...policiesQueryKeys.all, 'list'] as const,
  list: (tenantId: string) => [...policiesQueryKeys.lists(), tenantId] as const,
  details: () => [...policiesQueryKeys.all, 'detail'] as const,
  detail: (tenantId: string, policyId: string) => [...policiesQueryKeys.details(), tenantId, policyId] as const,
};

export const recommendationsQueryKeys = {
  all: ['recommendations'] as const,
  lists: () => [...recommendationsQueryKeys.all, 'list'] as const,
  list: (tenantId: string) => [...recommendationsQueryKeys.lists(), tenantId] as const,
  details: () => [...recommendationsQueryKeys.all, 'detail'] as const,
  detail: (tenantId: string, recommendationId: string) =>
    [...recommendationsQueryKeys.details(), tenantId, recommendationId] as const,
};

export const devicesQueryKeys = {
  all: ['devices'] as const,
  lists: () => [...devicesQueryKeys.all, 'list'] as const,
  list: (tenantId: string) => [...devicesQueryKeys.lists(), tenantId] as const,
  details: () => [...devicesQueryKeys.all, 'detail'] as const,
  detail: (tenantId: string, deviceId: string) => [...devicesQueryKeys.details(), tenantId, deviceId] as const,
};

// ============================================================
// Query Options
// ============================================================

const defaultQueryOptions = {
  staleTime: 5 * 60 * 1000, // 5 minutes
  cacheTime: 10 * 60 * 1000, // 10 minutes
  retry: 2,
  retryDelay: (attemptIndex: number) => Math.min(1000 * 2 ** attemptIndex, 30000),
};

// ============================================================
// App Queries
// ============================================================

/**
 * Hook to fetch list of monitored apps
 *
 * @param tenantId - Tenant ID
 * @param filters - Optional filters
 * @param enabled - Whether query should run
 */
export function useApps(
  tenantId: string,
  filters?: AppFilters,
  enabled: boolean = true
): UseQueryResult<PaginatedResponse<AppData>, Error> {
  return useQuery({
    queryKey: appsQueryKeys.list(tenantId, filters),
    queryFn: () => guardianApi.getApps(tenantId, filters),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId,
  });
}

/**
 * Hook to fetch single app details
 *
 * @param tenantId - Tenant ID
 * @param appId - App ID
 * @param enabled - Whether query should run
 */
export function useApp(
  tenantId: string,
  appId: string,
  enabled: boolean = true
): UseQueryResult<AppData, Error> {
  return useQuery({
    queryKey: appsQueryKeys.detail(tenantId, appId),
    queryFn: () => guardianApi.getApp(tenantId, appId),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId && !!appId,
  });
}

/**
 * Hook to update app monitoring status
 *
 * @param tenantId - Tenant ID
 */
export function useUpdateApp(tenantId: string): UseMutationResult<
  AppData,
  Error,
  { appId: string; data: Partial<AppData> }
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ appId, data }) => guardianApi.updateApp(tenantId, appId, data),
    onMutate: async ({ appId, data }) => {
      // Cancel outgoing refetches
      await queryClient.cancelQueries(appsQueryKeys.detail(tenantId, appId));

      // Snapshot previous value
      const previousApp = queryClient.getQueryData<AppData>(
        appsQueryKeys.detail(tenantId, appId)
      );

      // Optimistic update
      if (previousApp) {
        queryClient.setQueryData(appsQueryKeys.detail(tenantId, appId), {
          ...previousApp,
          ...data,
        });
      }

      return { previousApp };
    },
    onError: (_error, variables, context) => {
      // Rollback on error
      if (context?.previousApp) {
        queryClient.setQueryData(
          appsQueryKeys.detail(tenantId, variables.appId),
          context.previousApp
        );
      }
    },
    onSuccess: (updatedApp, { appId }) => {
      // Update single item
      queryClient.setQueryData(appsQueryKeys.detail(tenantId, appId), updatedApp);

      // Invalidate list queries to refetch
      queryClient.invalidateQueries(appsQueryKeys.lists());
    },
  });
}

// ============================================================
// Policy Queries
// ============================================================

/**
 * Hook to fetch list of policies
 *
 * @param tenantId - Tenant ID
 * @param enabled - Whether query should run
 */
export function usePolicies(
  tenantId: string,
  enabled: boolean = true
): UseQueryResult<PaginatedResponse<PolicyData>, Error> {
  return useQuery({
    queryKey: policiesQueryKeys.list(tenantId),
    queryFn: () => guardianApi.getPolicies(tenantId),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId,
  });
}

/**
 * Hook to fetch single policy details
 *
 * @param tenantId - Tenant ID
 * @param policyId - Policy ID
 * @param enabled - Whether query should run
 */
export function usePolicy(
  tenantId: string,
  policyId: string,
  enabled: boolean = true
): UseQueryResult<PolicyData, Error> {
  return useQuery({
    queryKey: policiesQueryKeys.detail(tenantId, policyId),
    queryFn: () => guardianApi.getPolicy(tenantId, policyId),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId && !!policyId,
  });
}

/**
 * Hook to create new policy
 *
 * @param tenantId - Tenant ID
 */
export function useCreatePolicy(tenantId: string): UseMutationResult<
  PolicyData,
  Error,
  Omit<PolicyData, 'id' | 'createdAt' | 'updatedAt'>
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (policy) => guardianApi.createPolicy(tenantId, policy),
    onSuccess: (newPolicy) => {
      // Invalidate list to refetch
      queryClient.invalidateQueries(policiesQueryKeys.lists());

      // Cache the new policy
      queryClient.setQueryData(
        policiesQueryKeys.detail(tenantId, newPolicy.id),
        newPolicy
      );
    },
  });
}

/**
 * Hook to update existing policy
 *
 * @param tenantId - Tenant ID
 */
export function useUpdatePolicy(tenantId: string): UseMutationResult<
  PolicyData,
  Error,
  { policyId: string; data: Partial<PolicyData> }
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ policyId, data }) => guardianApi.updatePolicy(tenantId, policyId, data),
    onMutate: async ({ policyId, data }) => {
      await queryClient.cancelQueries(policiesQueryKeys.detail(tenantId, policyId));

      const previousPolicy = queryClient.getQueryData<PolicyData>(
        policiesQueryKeys.detail(tenantId, policyId)
      );

      if (previousPolicy) {
        queryClient.setQueryData(policiesQueryKeys.detail(tenantId, policyId), {
          ...previousPolicy,
          ...data,
        });
      }

      return { previousPolicy };
    },
    onError: (_error, variables, context) => {
      if (context?.previousPolicy) {
        queryClient.setQueryData(
          policiesQueryKeys.detail(tenantId, variables.policyId),
          context.previousPolicy
        );
      }
    },
    onSuccess: (updatedPolicy, { policyId }) => {
      queryClient.setQueryData(
        policiesQueryKeys.detail(tenantId, policyId),
        updatedPolicy
      );
      queryClient.invalidateQueries(policiesQueryKeys.lists());
    },
  });
}

/**
 * Hook to delete policy
 *
 * @param tenantId - Tenant ID
 */
export function useDeletePolicy(tenantId: string): UseMutationResult<
  void,
  Error,
  string
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (policyId) => guardianApi.deletePolicy(tenantId, policyId),
    onSuccess: () => {
      queryClient.invalidateQueries(policiesQueryKeys.lists());
    },
  });
}

// ============================================================
// Recommendation Queries
// ============================================================

/**
 * Hook to fetch recommendations
 *
 * @param tenantId - Tenant ID
 * @param enabled - Whether query should run
 */
export function useRecommendations(
  tenantId: string,
  enabled: boolean = true
): UseQueryResult<RecommendationData[], Error> {
  return useQuery({
    queryKey: recommendationsQueryKeys.list(tenantId),
    queryFn: () => guardianApi.getRecommendations(tenantId),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId,
  });
}

/**
 * Hook to fetch single recommendation
 *
 * @param tenantId - Tenant ID
 * @param recommendationId - Recommendation ID
 * @param enabled - Whether query should run
 */
export function useRecommendation(
  tenantId: string,
  recommendationId: string,
  enabled: boolean = true
): UseQueryResult<RecommendationData, Error> {
  return useQuery({
    queryKey: recommendationsQueryKeys.detail(tenantId, recommendationId),
    queryFn: () => guardianApi.getRecommendation(tenantId, recommendationId),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId && !!recommendationId,
  });
}

/**
 * Hook to dismiss recommendation
 *
 * @param tenantId - Tenant ID
 */
export function useDismissRecommendation(tenantId: string): UseMutationResult<
  void,
  Error,
  string
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (recommendationId) =>
      guardianApi.dismissRecommendation(tenantId, recommendationId),
    onSuccess: () => {
      queryClient.invalidateQueries(recommendationsQueryKeys.lists());
    },
  });
}

// ============================================================
// Device Queries
// ============================================================

/**
 * Hook to fetch device status
 *
 * @param tenantId - Tenant ID
 * @param deviceId - Device ID
 * @param enabled - Whether query should run
 */
export function useDeviceStatus(
  tenantId: string,
  deviceId: string,
  enabled: boolean = true
): UseQueryResult<DeviceStatusData, Error> {
  return useQuery({
    queryKey: devicesQueryKeys.detail(tenantId, deviceId),
    queryFn: () => guardianApi.getDeviceStatus(tenantId, deviceId),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId && !!deviceId,
  });
}

/**
 * Hook to fetch all devices for tenant
 *
 * @param tenantId - Tenant ID
 * @param enabled - Whether query should run
 */
export function useDevices(
  tenantId: string,
  enabled: boolean = true
): UseQueryResult<DeviceStatusData[], Error> {
  return useQuery({
    queryKey: devicesQueryKeys.list(tenantId),
    queryFn: () => guardianApi.getDevices(tenantId),
    ...defaultQueryOptions,
    enabled: enabled && !!tenantId,
  });
}

/**
 * Hook to sync device
 *
 * @param tenantId - Tenant ID
 */
export function useSyncDevice(tenantId: string): UseMutationResult<
  DeviceStatusData,
  Error,
  string
> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (deviceId) => guardianApi.syncDevice(tenantId, deviceId),
    onSuccess: (updatedStatus, deviceId) => {
      queryClient.setQueryData(
        devicesQueryKeys.detail(tenantId, deviceId),
        updatedStatus
      );
      queryClient.invalidateQueries(devicesQueryKeys.lists());
    },
  });
}

// ============================================================
// Health Check
// ============================================================

/**
 * Hook for API health check
 *
 * @param enabled - Whether query should run
 */
export function useHealthCheck(
  enabled: boolean = true
): UseQueryResult<{ status: 'healthy' | 'degraded' | 'unhealthy' }, Error> {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => guardianApi.healthCheck(),
    staleTime: 1 * 60 * 1000, // 1 minute
    cacheTime: 5 * 60 * 1000, // 5 minutes
    retry: 1,
    enabled,
  });
}
