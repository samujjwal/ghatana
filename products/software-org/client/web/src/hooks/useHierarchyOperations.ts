/**
 * useHierarchyOperations Hook
 *
 * React hook for managing organizational hierarchy operations.
 * Provides functions for creating, approving, rejecting, and tracking
 * hierarchy changes with optimistic updates and real-time sync.
 *
 * @package @ghatana/software-org-web
 */

import { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    createHierarchyOperation,
    listHierarchyOperations,
    getPendingApprovals,
    approveOperation,
    rejectOperation,
    cancelOperation,
    validateOperation,
    createDepartment,
    createTeam,
    moveTeam,
    promotePerson,
    transferPerson,
    mergeTeams,
    type HierarchyOperation,
    type HierarchyTargetType,
    type CreateHierarchyOperationInput,
    type ValidationResult,
} from '@/services/api/hierarchyApi';
import { usePersona } from './usePersona';
import type { HierarchyLayer } from '@/state/atoms/persona.atoms';

// Query keys for React Query
export const HIERARCHY_QUERY_KEYS = {
    operations: ['hierarchy', 'operations'] as const,
    operation: (id: string) => ['hierarchy', 'operations', id] as const,
    pendingApprovals: ['hierarchy', 'pending-approvals'] as const,
};

/**
 * Hook return type
 */
export interface UseHierarchyOperationsReturn {
    // Query state
    operations: HierarchyOperation[];
    pendingApprovals: HierarchyOperation[];
    isLoading: boolean;
    error: Error | null;

    // Mutations
    createOperation: (input: CreateHierarchyOperationInput) => Promise<HierarchyOperation>;
    approve: (operationId: string, comment?: string) => Promise<HierarchyOperation>;
    reject: (operationId: string, reason: string) => Promise<HierarchyOperation>;
    cancel: (operationId: string) => Promise<boolean>;
    validate: (input: CreateHierarchyOperationInput) => Promise<ValidationResult>;

    // Convenience operations
    createNewDepartment: (params: { name: string; parentId: string; description?: string }) => Promise<HierarchyOperation>;
    createNewTeam: (params: { name: string; departmentId: string; description?: string }) => Promise<HierarchyOperation>;
    moveTeamToDepartment: (teamId: string, newDepartmentId: string) => Promise<HierarchyOperation>;
    promotePersonToRole: (params: {
        personId: string;
        currentRole: string;
        currentLayer: HierarchyLayer;
        newRole: string;
        newLayer: HierarchyLayer;
        justification?: string;
    }) => Promise<HierarchyOperation>;
    transferPersonToTeam: (personId: string, newTeamId: string) => Promise<HierarchyOperation>;
    mergeTeamsInto: (params: {
        sourceTeamIds: string[];
        targetName: string;
        departmentId: string;
    }) => Promise<HierarchyOperation>;

    // Helpers
    canApprove: (operation: HierarchyOperation) => boolean;
    canInitiate: (targetType: HierarchyTargetType) => boolean;
    refetch: () => void;
}

/**
 * useHierarchyOperations Hook
 *
 * Provides comprehensive hierarchy operation management with
 * React Query for caching and real-time updates.
 *
 * @example
 * ```tsx
 * function RestructurePage() {
 *   const {
 *     operations,
 *     pendingApprovals,
 *     createNewDepartment,
 *     approve,
 *     canApprove,
 *   } = useHierarchyOperations();
 *
 *   const handleCreateDepartment = async () => {
 *     await createNewDepartment({
 *       name: 'New Engineering',
 *       parentId: 'org-001',
 *     });
 *   };
 *
 *   return (
 *     <div>
 *       {pendingApprovals.map(op => (
 *         <div key={op.operationId}>
 *           {op.type} - {op.targetType}
 *           {canApprove(op) && (
 *             <button onClick={() => approve(op.operationId)}>
 *               Approve
 *             </button>
 *           )}
 *         </div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useHierarchyOperations(): UseHierarchyOperationsReturn {
    const queryClient = useQueryClient();
    const { layer, level, isLeadership } = usePersona();
    const [error, setError] = useState<Error | null>(null);

    // Query for all operations
    const operationsQuery = useQuery({
        queryKey: HIERARCHY_QUERY_KEYS.operations,
        queryFn: () => listHierarchyOperations(),
        staleTime: 30000, // 30 seconds
    });

    // Query for pending approvals
    const pendingApprovalsQuery = useQuery({
        queryKey: HIERARCHY_QUERY_KEYS.pendingApprovals,
        queryFn: () => getPendingApprovals(),
        staleTime: 10000, // 10 seconds - more frequent for approvals
        enabled: isLeadership, // Only fetch if user can approve
    });

    // Mutation for creating operations
    const createMutation = useMutation({
        mutationFn: createHierarchyOperation,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.operations });
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.pendingApprovals });
        },
        onError: (err: Error) => setError(err),
    });

    // Mutation for approving operations
    const approveMutation = useMutation({
        mutationFn: ({ operationId, comment }: { operationId: string; comment?: string }) =>
            approveOperation(operationId, comment),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.operations });
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.pendingApprovals });
        },
        onError: (err: Error) => setError(err),
    });

    // Mutation for rejecting operations
    const rejectMutation = useMutation({
        mutationFn: ({ operationId, reason }: { operationId: string; reason: string }) =>
            rejectOperation(operationId, reason),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.operations });
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.pendingApprovals });
        },
        onError: (err: Error) => setError(err),
    });

    // Mutation for cancelling operations
    const cancelMutation = useMutation({
        mutationFn: cancelOperation,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.operations });
            queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.pendingApprovals });
        },
        onError: (err: Error) => setError(err),
    });

    /**
     * Check if current user can approve an operation
     */
    const canApprove = useCallback(
        (operation: HierarchyOperation): boolean => {
            if (!layer || !level) return false;
            if (operation.status !== 'AWAITING_APPROVAL') return false;

            // Map target type to minimum approval layer
            const minLevelMap: Record<HierarchyTargetType, number> = {
                ORGANIZATION: 4, // organization level
                DEPARTMENT: 3, // executive level
                TEAM: 2, // management level
                ROLE: 2, // management level
                PERSON: 2, // management level
            };

            const minLevel = minLevelMap[operation.targetType] || 2;
            return level >= minLevel;
        },
        [layer, level]
    );

    /**
     * Check if current user can initiate operations for a target type
     */
    const canInitiate = useCallback(
        (_targetType: HierarchyTargetType): boolean => {
            if (!level) return false;

            // Anyone can initiate, but approval requirements vary
            // This just checks if user has any authority
            return level >= 1;
        },
        [level]
    );

    /**
     * Refetch all queries
     */
    const refetch = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.operations });
        queryClient.invalidateQueries({ queryKey: HIERARCHY_QUERY_KEYS.pendingApprovals });
    }, [queryClient]);

    return {
        // Query state
        operations: operationsQuery.data || [],
        pendingApprovals: pendingApprovalsQuery.data || [],
        isLoading: operationsQuery.isLoading || pendingApprovalsQuery.isLoading,
        error: error || operationsQuery.error || pendingApprovalsQuery.error || null,

        // Mutations
        createOperation: createMutation.mutateAsync,
        approve: (operationId, comment) =>
            approveMutation.mutateAsync({ operationId, comment }),
        reject: (operationId, reason) =>
            rejectMutation.mutateAsync({ operationId, reason }),
        cancel: cancelMutation.mutateAsync,
        validate: validateOperation,

        // Convenience operations
        createNewDepartment: createDepartment,
        createNewTeam: createTeam,
        moveTeamToDepartment: moveTeam,
        promotePersonToRole: promotePerson,
        transferPersonToTeam: transferPerson,
        mergeTeamsInto: mergeTeams,

        // Helpers
        canApprove,
        canInitiate,
        refetch,
    };
}

export default useHierarchyOperations;
