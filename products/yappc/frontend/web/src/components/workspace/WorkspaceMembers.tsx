/**
 * Workspace Members Component
 *
 * Manages workspace membership: list members, invite users, update roles, remove members.
 * Restricted to ADMIN/OWNER roles for mutation operations.
 *
 * @doc.type component
 * @doc.purpose Workspace member management UI
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import {
  Users as MembersIcon,
  UserPlus as InviteIcon,
  Trash2 as RemoveIcon,
  Crown as OwnerIcon,
  Shield as AdminIcon,
  User as UserIcon,
  Eye as ViewerIcon,
  Search as SearchIcon,
  X as CloseIcon,
} from 'lucide-react';
import {
  Typography,
  Button,
  Box,
  Card,
  CardContent,
  Chip,
} from '@ghatana/design-system';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import {
  useQuery,
  useMutation,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';

// ============================================================================
// Types
// ============================================================================

export type WorkspaceMemberRole = 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';

export interface WorkspaceMember {
  id: string;
  userId: string;
  workspaceId: string;
  role: WorkspaceMemberRole;
  joinedAt: string;
  updatedAt: string;
  user: {
    id: string;
    name: string;
    email: string;
    role: string;
    createdAt: string;
  };
}

export interface WorkspaceMembersProps {
  workspaceId: string;
  currentUserRole: WorkspaceMemberRole;
  className?: string;
}

interface MembersResponse {
  members: WorkspaceMember[];
}

interface MemberResponse {
  member: WorkspaceMember;
}

// ============================================================================
// Role display helpers
// ============================================================================

function getRoleIcon(role: WorkspaceMemberRole): ReactNode {
  switch (role) {
    case 'OWNER':
      return <OwnerIcon className="h-3.5 w-3.5" aria-hidden="true" />;
    case 'ADMIN':
      return <AdminIcon className="h-3.5 w-3.5" aria-hidden="true" />;
    case 'EDITOR':
      return <UserIcon className="h-3.5 w-3.5" aria-hidden="true" />;
    case 'VIEWER':
      return <ViewerIcon className="h-3.5 w-3.5" aria-hidden="true" />;
  }
}

function getRoleColor(role: WorkspaceMemberRole): string {
  const map: Record<WorkspaceMemberRole, string> = {
    OWNER: 'bg-info-bg text-info-color dark:bg-info-bg dark:text-info-color',
    ADMIN: 'bg-info-bg text-info-color dark:bg-info-bg dark:text-info-color',
    EDITOR: 'bg-success-bg text-success-color dark:bg-success-bg dark:text-success-color',
    VIEWER: 'bg-surface-muted text-fg-muted dark:bg-surface-muted dark:text-fg-muted',
  };
  return map[role];
}

const ROLES: WorkspaceMemberRole[] = ['VIEWER', 'EDITOR', 'ADMIN', 'OWNER'];

function canManageMembers(role: WorkspaceMemberRole): boolean {
  return role === 'ADMIN' || role === 'OWNER';
}

// ============================================================================
// Invite Panel
// ============================================================================

interface InvitePanelProps {
  workspaceId: string;
  onInvited: () => void;
  onClose: () => void;
}

function InvitePanel({ workspaceId, onInvited, onClose }: InvitePanelProps): ReactNode {
  const [userSearch, setUserSearch] = useState('');
  const [selectedRole, setSelectedRole] = useState<WorkspaceMemberRole>('EDITOR');

  const searchQuery = useQuery({
    queryKey: ['users', 'search', userSearch],
    queryFn: async () => {
      if (!userSearch.trim()) return { users: [] };
      const response = await fetch(
        `/api/v1/users?search=${encodeURIComponent(userSearch.trim())}`
      );
      if (!response.ok) {
        const message = await readErrorResponse(response, 'User search failed');
        throw new Error(message);
      }
      return parseJsonResponse<{ users: Array<{ id: string; name: string; email: string }> }>(
        response,
        'WorkspaceMembers.search'
      );
    },
    enabled: userSearch.trim().length >= 2,
    staleTime: 10_000,
  });

  const inviteMutation = useMutation({
    mutationFn: async ({ userId }: { userId: string }) => {
      const response = await fetch(`/api/v1/workspaces/${workspaceId}/members`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, role: selectedRole }),
      });
      if (!response.ok) {
        const message = await readErrorResponse(response, 'Failed to add member');
        throw new Error(message);
      }
      return parseJsonResponse<MemberResponse>(response, 'WorkspaceMembers.invite');
    },
    onSuccess: () => {
      onInvited();
    },
  });

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setUserSearch(e.target.value);
  }, []);

  const handleRoleChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedRole(e.target.value as WorkspaceMemberRole);
  }, []);

  const users = searchQuery.data?.users ?? [];

  return (
    <Card className="border-2 border-info-border dark:border-info-border">
      <CardContent className="p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <InviteIcon className="h-4 w-4 text-info-color" aria-hidden="true" />
            <Typography variant="body2" className="font-semibold">
              Invite Member
            </Typography>
          </div>
          <Button
            size="sm"
            variant="ghost"
            onClick={onClose}
            aria-label="Close invite panel"
            className="text-fg-muted hover:text-fg-muted dark:hover:text-fg-muted"
          >
            <CloseIcon className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>

        <div className="flex gap-2 mb-3">
          {/* Search input */}
          <div className="relative flex-1">
            <SearchIcon
              className="h-4 w-4 absolute left-2.5 top-1/2 -translate-y-1/2 text-fg-muted"
              aria-hidden="true"
            />
            <Input
              type="text"
              value={userSearch}
              onChange={handleSearchChange}
              placeholder="Search by name or email…"
              aria-label="Search users"
              className="w-full pl-8 pr-3 py-1.5 text-sm border rounded-md dark:bg-surface dark:border-border focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Role selector */}
          <Select
            value={selectedRole}
            onChange={handleRoleChange}
            aria-label="Select role"
            className="text-sm border rounded-md px-2 py-1.5 dark:bg-surface dark:border-border focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {ROLES.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </Select>
        </div>

        {/* Search results */}
        {searchQuery.isLoading && (
          <Typography variant="caption" className="text-fg-muted">
            Searching…
          </Typography>
        )}
        {users.length > 0 && (
          <div className="divide-y dark:divide-gray-700 border dark:border-border rounded-md">
            {users.map((user) => (
              <div
                key={user.id}
                className="flex items-center justify-between px-3 py-2 hover:bg-surface-muted dark:hover:bg-surface/40"
              >
                <div>
                  <Typography variant="body2" className="text-sm font-medium">
                    {user.name}
                  </Typography>
                  <Typography variant="caption" className="text-fg-muted text-xs">
                    {user.email}
                  </Typography>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => inviteMutation.mutate({ userId: user.id })}
                  disabled={inviteMutation.isPending}
                  aria-label={`Add ${user.name} as ${selectedRole}`}
                  className="text-xs"
                >
                  Add
                </Button>
              </div>
            ))}
          </div>
        )}

        {userSearch.trim().length >= 2 &&
          !searchQuery.isLoading &&
          users.length === 0 && (
            <Typography variant="caption" className="text-fg-muted">
              No users found matching &quot;{userSearch}&quot;
            </Typography>
          )}

        {inviteMutation.isError && (
          <Typography variant="caption" className="text-destructive mt-2">
            {inviteMutation.error instanceof Error
              ? inviteMutation.error.message
              : 'Failed to add member'}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Member Row
// ============================================================================

interface MemberRowProps {
  member: WorkspaceMember;
  canManage: boolean;
  onUpdateRole: (userId: string, role: WorkspaceMemberRole) => void;
  onRemove: (userId: string) => void;
  isMutating: boolean;
}

function MemberRow({
  member,
  canManage,
  onUpdateRole,
  onRemove,
  isMutating,
}: MemberRowProps): ReactNode {
  const handleRoleChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => {
      onUpdateRole(member.userId, e.target.value as WorkspaceMemberRole);
    },
    [onUpdateRole, member.userId]
  );

  const handleRemove = useCallback(() => {
    onRemove(member.userId);
  }, [onRemove, member.userId]);

  return (
    <div className="flex items-center gap-3 px-4 py-3 hover:bg-surface-muted dark:hover:bg-surface/40">
      {/* Avatar placeholder */}
      <div
        className="h-8 w-8 rounded-full bg-gradient-to-br from-blue-400 to-purple-500 flex items-center justify-center text-white text-sm font-bold flex-shrink-0"
        aria-hidden="true"
      >
        {member.user.name.charAt(0).toUpperCase()}
      </div>

      {/* User info */}
      <div className="flex-1 min-w-0">
        <Typography variant="body2" className="text-sm font-medium truncate">
          {member.user.name}
        </Typography>
        <Typography variant="caption" className="text-fg-muted text-xs truncate">
          {member.user.email}
        </Typography>
      </div>

      {/* Role badge / selector */}
      {canManage && member.role !== 'OWNER' ? (
        <Select
          value={member.role}
          onChange={handleRoleChange}
          disabled={isMutating}
          aria-label={`Role for ${member.user.name}`}
          className="text-xs border rounded-md px-1.5 py-1 dark:bg-surface dark:border-border focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {ROLES.map((role) => (
            <option key={role} value={role}>
              {role}
            </option>
          ))}
        </Select>
      ) : (
        <span
          className={`flex items-center gap-1 text-xs font-medium px-2 py-1 rounded-full ${getRoleColor(member.role)}`}
          aria-label={`Role: ${member.role}`}
        >
          {getRoleIcon(member.role)}
          {member.role}
        </span>
      )}

      {/* Remove button */}
      {canManage && member.role !== 'OWNER' && (
        <Button
          size="sm"
          variant="ghost"
          onClick={handleRemove}
          disabled={isMutating}
          aria-label={`Remove ${member.user.name}`}
          className="text-destructive hover:text-destructive hover:bg-destructive-bg dark:hover:bg-destructive-bg p-1.5"
        >
          <RemoveIcon className="h-4 w-4" aria-hidden="true" />
        </Button>
      )}
    </div>
  );
}

// ============================================================================
// WorkspaceMembers Component
// ============================================================================

async function fetchMembers(workspaceId: string): Promise<WorkspaceMember[]> {
  const response = await fetch(`/api/v1/workspaces/${workspaceId}/members`);
  if (!response.ok) {
    const message = await readErrorResponse(response, 'Failed to load members');
    throw new Error(message);
  }
  const data = await parseJsonResponse<MembersResponse>(response, 'WorkspaceMembers');
  return data.members;
}

/**
 * WorkspaceMembers — Full member management panel for a workspace.
 */
export function WorkspaceMembers({
  workspaceId,
  currentUserRole,
  className = '',
}: WorkspaceMembersProps): ReactNode {
  const queryClient: QueryClient = useQueryClient();
  const [showInvite, setShowInvite] = useState(false);

  const {
    data: members = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['workspaces', workspaceId, 'members'],
    queryFn: () => fetchMembers(workspaceId),
    staleTime: 30_000,
  });

  const updateRoleMutation = useMutation({
    mutationFn: async ({ userId, role }: { userId: string; role: WorkspaceMemberRole }) => {
      const response = await fetch(
        `/api/v1/workspaces/${workspaceId}/members/${userId}/role`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ role }),
        }
      );
      if (!response.ok) {
        const message = await readErrorResponse(response, 'Failed to update role');
        throw new Error(message);
      }
      return parseJsonResponse<MemberResponse>(response, 'WorkspaceMembers.updateRole');
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['workspaces', workspaceId, 'members'],
      });
    },
  });

  const removeMutation = useMutation({
    mutationFn: async ({ userId }: { userId: string }) => {
      const response = await fetch(
        `/api/v1/workspaces/${workspaceId}/members/${userId}`,
        { method: 'DELETE' }
      );
      if (!response.ok) {
        const message = await readErrorResponse(response, 'Failed to remove member');
        throw new Error(message);
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['workspaces', workspaceId, 'members'],
      });
    },
  });

  const handleUpdateRole = useCallback(
    (userId: string, role: WorkspaceMemberRole) => {
      updateRoleMutation.mutate({ userId, role });
    },
    [updateRoleMutation]
  );

  const handleRemove = useCallback(
    (userId: string) => {
      removeMutation.mutate({ userId });
    },
    [removeMutation]
  );

  const handleInvited = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ['workspaces', workspaceId, 'members'],
    });
    setShowInvite(false);
  }, [queryClient, workspaceId]);

  const isMutating = updateRoleMutation.isPending || removeMutation.isPending;
  const canManage = canManageMembers(currentUserRole);

  if (isLoading) {
    return (
      <Box className={`p-6 ${className}`}>
        <div
          className="flex items-center justify-center gap-2 text-fg-muted"
          role="status"
          aria-label="Loading members"
        >
          <MembersIcon className="h-5 w-5 animate-pulse" aria-hidden="true" />
          <Typography variant="body2">Loading members…</Typography>
        </div>
      </Box>
    );
  }

  if (isError) {
    return (
      <Box className={`p-6 ${className}`}>
        <div className="flex items-center gap-2 text-destructive dark:text-destructive" role="alert">
          <MembersIcon className="h-5 w-5" aria-hidden="true" />
          <Typography variant="body2">
            {error instanceof Error ? error.message : 'Failed to load workspace members'}
          </Typography>
        </div>
      </Box>
    );
  }

  return (
    <Box className={`flex flex-col gap-4 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <MembersIcon className="h-5 w-5 text-fg-muted" aria-hidden="true" />
          <Typography variant="h6" className="font-semibold">
            Members
          </Typography>
          <Chip label={`${members.length}`} size="small" aria-label={`${members.length} members`} />
        </div>

        {canManage && (
          <Button
            size="sm"
            variant={showInvite ? 'outline' : 'default'}
            onClick={() => setShowInvite((prev) => !prev)}
            aria-expanded={showInvite}
            aria-label={showInvite ? 'Cancel invite' : 'Invite member'}
          >
            {showInvite ? (
              <>
                <CloseIcon className="h-4 w-4 mr-1" aria-hidden="true" />
                Cancel
              </>
            ) : (
              <>
                <InviteIcon className="h-4 w-4 mr-1" aria-hidden="true" />
                Invite Member
              </>
            )}
          </Button>
        )}
      </div>

      {/* Invite panel */}
      {showInvite && canManage && (
        <InvitePanel
          workspaceId={workspaceId}
          onInvited={handleInvited}
          onClose={() => setShowInvite(false)}
        />
      )}

      {/* Members list */}
      <Card>
        <CardContent className="p-0">
          {members.length === 0 ? (
            <div className="p-8 text-center">
              <MembersIcon className="h-10 w-10 text-fg-muted mx-auto mb-3" aria-hidden="true" />
              <Typography variant="body1" className="text-fg-muted">
                No members yet
              </Typography>
            </div>
          ) : (
            <div className="divide-y dark:divide-gray-700">
              {members.map((member) => (
                <MemberRow
                  key={member.id}
                  member={member}
                  canManage={canManage}
                  onUpdateRole={handleUpdateRole}
                  onRemove={handleRemove}
                  isMutating={isMutating}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Mutation errors */}
      {updateRoleMutation.isError && (
        <Typography variant="caption" className="text-destructive">
          {updateRoleMutation.error instanceof Error
            ? updateRoleMutation.error.message
            : 'Failed to update role'}
        </Typography>
      )}
      {removeMutation.isError && (
        <Typography variant="caption" className="text-destructive">
          {removeMutation.error instanceof Error
            ? removeMutation.error.message
            : 'Failed to remove member'}
        </Typography>
      )}
    </Box>
  );
}
