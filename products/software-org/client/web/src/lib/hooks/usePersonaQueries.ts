/**
 * React Query hooks for persona management
 *
 * Purpose:
 * Provides React Query hooks for fetching and mutating persona preferences
 * with automatic caching, revalidation, and optimistic updates.
 *
 * Hooks:
 * - useRoleDefinitions: Fetch all available role definitions
 * - useRoleDefinition: Fetch specific role definition
 * - usePersonaPreference: Fetch user's persona preference for workspace
 * - useUpdatePersonaPreference: Mutation for updating persona preference
 * - useDeletePersonaPreference: Mutation for deleting persona preference
 * - useEffectivePermissions: Compute effective permissions from roles
 * - useHasPermission: Check if user has specific permission
 * - useHasCapability: Check if user has specific capability
 *
 * Caching Strategy:
 * - Role definitions: Cache for 1 hour (rarely change)
 * - Persona preferences: Cache for 5 minutes (user-specific)
 * - Permissions: Derived from preferences (no separate cache)
 *
 * Integration:
 * - Replaces localStorage-based persistence in usePersonaComposition
 * - Works with React Router v7 loaders for SSR
 * - Integrates with WebSocket for real-time sync (Task 16)
 */

import { useQuery, useMutation, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import * as personaService from '../api/persona.service';
import type {
    PersonaPreference,
    UpdatePersonaPreferenceInput,
} from '../api/persona.types';

/**
 * Query keys for React Query cache
 */
export const personaKeys = {
    all: ['personas'] as const,
    roles: () => [...personaKeys.all, 'roles'] as const,
    role: (roleId: string) => [...personaKeys.roles(), roleId] as const,
    preferences: () => [...personaKeys.all, 'preferences'] as const,
    preference: (workspaceId: string) => [...personaKeys.preferences(), workspaceId] as const,
    permissions: (roleIds: string[]) => [...personaKeys.all, 'permissions', roleIds.sort().join(',')] as const,
};

/**
 * Hook to fetch all available role definitions
 *
 * Caching: 1 hour (role definitions rarely change)
 *
 * @example
 * const { data: roles, isLoading } = useRoleDefinitions();
 */
export function useRoleDefinitions() {
    return useQuery({
        queryKey: personaKeys.roles(),
        queryFn: personaService.getAllRoles,
        staleTime: 1000 * 60 * 60, // 1 hour
        gcTime: 1000 * 60 * 60 * 2, // 2 hours (was cacheTime in v4)
    });
}

/**
 * Hook to fetch specific role definition by ID
 *
 * @param roleId - Role identifier
 *
 * @example
 * const { data: role } = useRoleDefinition('tech-lead');
 */
export function useRoleDefinition(roleId: string) {
    return useQuery({
        queryKey: personaKeys.role(roleId),
        queryFn: () => personaService.getRoleDefinition(roleId),
        staleTime: 1000 * 60 * 60, // 1 hour
        enabled: !!roleId,
    });
}

/**
 * Hook to fetch user's persona preference for workspace
 *
 * Caching: 5 minutes (user preferences change occasionally)
 *
 * @param workspaceId - Workspace identifier
 *
 * @example
 * const { data: preference, isLoading } = usePersonaPreference('workspace-123');
 */
export function usePersonaPreference(workspaceId: string): UseQueryResult<PersonaPreference | null> {
    return useQuery({
        queryKey: personaKeys.preference(workspaceId),
        queryFn: () => personaService.getPersonaPreference(workspaceId),
        staleTime: 1000 * 60 * 5, // 5 minutes
        gcTime: 1000 * 60 * 10, // 10 minutes
        enabled: !!workspaceId,
    });
}

/**
 * Hook to update persona preference with optimistic updates
 *
 * Features:
 * - Optimistic UI update (immediate feedback)
 * - Automatic rollback on error
 * - Cache invalidation on success
 *
 * @param workspaceId - Workspace identifier
 *
 * @example
 * const updatePreference = useUpdatePersonaPreference('workspace-123');
 * 
 * updatePreference.mutate({
 *   activeRoles: ['tech-lead', 'backend-developer'],
 *   preferences: { dashboardLayout: { type: 'grid' } }
 * });
 */
export function useUpdatePersonaPreference(workspaceId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (data: UpdatePersonaPreferenceInput) =>
            personaService.upsertPersonaPreference(workspaceId, data),

        // Optimistic update
        onMutate: async (newData) => {
            // Cancel outgoing refetches
            await queryClient.cancelQueries({
                queryKey: personaKeys.preference(workspaceId),
            });

            // Snapshot previous value
            const previousPreference = queryClient.getQueryData<PersonaPreference>(
                personaKeys.preference(workspaceId)
            );

            // Optimistically update cache
            if (previousPreference) {
                queryClient.setQueryData<PersonaPreference>(
                    personaKeys.preference(workspaceId),
                    {
                        ...previousPreference,
                        activeRoles: newData.activeRoles,
                        preferences: newData.preferences,
                        updatedAt: new Date().toISOString(),
                    }
                );
            }

            return { previousPreference };
        },

        // Rollback on error
        onError: (_error, _variables, context) => {
            if (context?.previousPreference) {
                queryClient.setQueryData(
                    personaKeys.preference(workspaceId),
                    context.previousPreference
                );
            }
        },

        // Refetch on success
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: personaKeys.preference(workspaceId),
            });
        },
    });
}

/**
 * Hook to delete persona preference
 *
 * @param workspaceId - Workspace identifier
 *
 * @example
 * const deletePreference = useDeletePersonaPreference('workspace-123');
 * deletePreference.mutate();
 */
export function useDeletePersonaPreference(workspaceId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: () => personaService.deletePersonaPreference(workspaceId),

        onSuccess: () => {
            queryClient.setQueryData(personaKeys.preference(workspaceId), null);
            queryClient.invalidateQueries({
                queryKey: personaKeys.preferences(),
            });
        },
    });
}

/**
 * Hook to compute effective permissions from active roles
 *
 * @param roleIds - Active role IDs
 *
 * @example
 * const { data: permissions } = useEffectivePermissions(['tech-lead', 'backend-developer']);
 * if (permissions?.permissions['code.approve']) {
 *   // User can approve code
 * }
 */
export function useEffectivePermissions(roleIds: string[]) {
    return useQuery({
        queryKey: personaKeys.permissions(roleIds),
        queryFn: () => personaService.resolveEffectivePermissions(roleIds),
        staleTime: 1000 * 60 * 5, // 5 minutes
        enabled: roleIds.length > 0,
    });
}

/**
 * Hook to check if user has specific permission
 *
 * @param roleIds - Active role IDs
 * @param permission - Permission to check
 * @returns true if user has permission, false otherwise
 *
 * @example
 * const canApprove = useHasPermission(['tech-lead'], 'code.approve');
 */
export function useHasPermission(roleIds: string[], permission: string): boolean {
    const { data: permissions } = useEffectivePermissions(roleIds);
    return permissions?.permissions[permission] === true;
}

/**
 * Hook to check if user has specific capability
 *
 * @param roleIds - Active role IDs
 * @param capability - Capability to check
 * @returns true if user has capability, false otherwise
 *
 * @example
 * const canDeploy = useHasCapability(['devops-engineer'], 'deployProduction');
 */
export function useHasCapability(roleIds: string[], capability: string): boolean {
    const { data: permissions } = useEffectivePermissions(roleIds);
    return permissions?.capabilities[capability] === true;
}
