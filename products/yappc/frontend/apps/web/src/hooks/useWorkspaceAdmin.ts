/**
 * useWorkspaceAdmin Hook
 *
 * Provides workspace administration functionality including:
 * - Member management (invite, remove, update)
 * - Role and persona management
 * - Permission checking
 * - Integration with WorkspaceService and AuditService
 *
 * @doc.type hook
 * @doc.purpose Workspace admin state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
    selectedWorkspaceAtom,
    currentMembershipAtom,
    userPermissionsAtom,
    currentUserAtom,
} from '@/stores/user.store';
import type {
    Workspace,
    WorkspaceMember,
    PersonaType,
    WorkspacePermission,
} from '@ghatana/yappc-auth/rbac';
import { WorkspaceRole, ROLE_PERMISSIONS } from '@ghatana/yappc-auth/rbac';
import { getWorkspaceService } from '../state/atoms';

// ============================================================================
// Types
// ============================================================================

interface UseWorkspaceAdminResult {
    /** Current workspace data */
    workspace: Workspace | null;
    /** List of workspace members */
    members: WorkspaceMember[];
    /** Current user's membership in the workspace */
    currentMembership: WorkspaceMember | null;
    /** Loading state */
    isLoading: boolean;
    /** Error message if any */
    error: string | null;
    /** Invite a new member to the workspace */
    inviteMember: (email: string, role: WorkspaceRole, personas: PersonaType[]) => Promise<void>;
    /** Remove a member from the workspace */
    removeMember: (userId: string) => Promise<void>;
    /** Update a member's role */
    updateMemberRole: (userId: string, role: WorkspaceRole) => Promise<void>;
    /** Update a member's personas */
    updateMemberPersonas: (userId: string, personas: PersonaType[]) => Promise<void>;
    /** Refresh workspace data */
    refresh: () => Promise<void>;
    /** Check if current user has a specific permission */
    hasPermission: (permission: WorkspacePermission) => boolean;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Hook for workspace administration functionality.
 */
export function useWorkspaceAdmin(): UseWorkspaceAdminResult {
    const workspaceId = useAtomValue(selectedWorkspaceAtom);
    const currentUser = useAtomValue(currentUserAtom);
    const [currentMembership, setCurrentMembership] = useAtom(currentMembershipAtom);
    const setUserPermissions = useSetAtom(userPermissionsAtom);

    const [workspace, setWorkspace] = useState<Workspace | null>(null);
    const [members, setMembers] = useState<WorkspaceMember[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const workspaceService = useMemo(() => getWorkspaceService(), []);

    /**
     * Load workspace data and members.
     */
    const loadWorkspaceData = useCallback(async () => {
        if (!workspaceId) {
            setWorkspace(null);
            setMembers([]);
            setCurrentMembership(null);
            setUserPermissions([]);
            return;
        }

        setIsLoading(true);
        setError(null);

        try {
            // Get workspace details
            const ws = workspaceService.getWorkspace(workspaceId);
            if (!ws) {
                throw new Error('Workspace not found');
            }
            setWorkspace(ws);

            // Get members
            const membersList = workspaceService.getWorkspaceMembers(workspaceId);
            setMembers(membersList);

            // Find current user's membership
            if (currentUser?.id) {
                const myMembership = membersList.find((m: WorkspaceMember) => m.userId === currentUser.id);
                setCurrentMembership(myMembership ?? null);

                // Set permissions based on role
                if (myMembership) {
                    const role = myMembership.role as WorkspaceRole;
                    const rolePermissions = ROLE_PERMISSIONS[role] ?? [];
                    setUserPermissions(rolePermissions);
                } else {
                    setUserPermissions([]);
                }
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load workspace');
        } finally {
            setIsLoading(false);
        }
    }, [workspaceId, currentUser?.id, workspaceService, setCurrentMembership, setUserPermissions]);

    /**
     * Initialize and refresh on workspace change.
     */
    useEffect(() => {
        loadWorkspaceData();
    }, [loadWorkspaceData]);

    /**
     * Invite a new member to the workspace.
     */
    const inviteMember = useCallback(
        async (email: string, role: WorkspaceRole, personas: PersonaType[]) => {
            if (!workspaceId || !currentUser?.id) {
                throw new Error('No workspace selected');
            }

            setError(null);
            try {
                // Generate a temporary user ID (in production, this would be from the backend)
                const tempUserId = `invited-${Date.now()}`;

                await workspaceService.addMember(workspaceId, {
                    userId: tempUserId,
                    email,
                    role,
                    personas,
                    invitedBy: currentUser.id,
                });

                // Refresh member list
                const updatedMembers = workspaceService.getWorkspaceMembers(workspaceId);
                setMembers(updatedMembers);
            } catch (err) {
                const message = err instanceof Error ? err.message : 'Failed to invite member';
                setError(message);
                throw new Error(message);
            }
        },
        [workspaceId, currentUser?.id, workspaceService]
    );

    /**
     * Remove a member from the workspace.
     */
    const removeMember = useCallback(
        async (userId: string) => {
            if (!workspaceId || !currentUser?.id) {
                throw new Error('No workspace selected');
            }

            setError(null);
            try {
                await workspaceService.removeMember(workspaceId, userId, currentUser.id);

                // Refresh member list
                const updatedMembers = workspaceService.getWorkspaceMembers(workspaceId);
                setMembers(updatedMembers);
            } catch (err) {
                const message = err instanceof Error ? err.message : 'Failed to remove member';
                setError(message);
                throw new Error(message);
            }
        },
        [workspaceId, currentUser?.id, workspaceService]
    );

    /**
     * Update a member's role.
     */
    const updateMemberRole = useCallback(
        async (userId: string, role: WorkspaceRole) => {
            if (!workspaceId || !currentUser?.id) {
                throw new Error('No workspace selected');
            }

            setError(null);
            try {
                await workspaceService.updateMemberRole(workspaceId, userId, role, currentUser.id);

                // Refresh member list
                const updatedMembers = workspaceService.getWorkspaceMembers(workspaceId);
                setMembers(updatedMembers);
            } catch (err) {
                const message = err instanceof Error ? err.message : 'Failed to update role';
                setError(message);
                throw new Error(message);
            }
        },
        [workspaceId, currentUser?.id, workspaceService]
    );

    /**
     * Update a member's personas.
     */
    const updateMemberPersonas = useCallback(
        async (userId: string, personas: PersonaType[]) => {
            if (!workspaceId || !currentUser?.id) {
                throw new Error('No workspace selected');
            }

            setError(null);
            try {
                await workspaceService.updateMemberPersonas(workspaceId, userId, personas, currentUser.id);

                // Refresh member list
                const updatedMembers = workspaceService.getWorkspaceMembers(workspaceId);
                setMembers(updatedMembers);

                // Update current user's membership if it was modified
                if (userId === currentUser.id) {
                    const myMembership = updatedMembers.find((m: WorkspaceMember) => m.userId === currentUser.id);
                    setCurrentMembership(myMembership ?? null);
                }
            } catch (err) {
                const message = err instanceof Error ? err.message : 'Failed to update personas';
                setError(message);
                throw new Error(message);
            }
        },
        [workspaceId, currentUser?.id, workspaceService, setCurrentMembership]
    );

    /**
     * Check if current user has a specific permission.
     */
    const hasPermission = useCallback(
        (permission: WorkspacePermission) => {
            if (!currentMembership) return false;
            const rolePermissions = ROLE_PERMISSIONS[currentMembership.role] ?? [];
            return rolePermissions.includes(permission);
        },
        [currentMembership]
    );

    return {
        workspace,
        members,
        currentMembership,
        isLoading,
        error,
        inviteMember,
        removeMember,
        updateMemberRole,
        updateMemberPersonas,
        refresh: loadWorkspaceData,
        hasPermission,
    };
}

export default useWorkspaceAdmin;
