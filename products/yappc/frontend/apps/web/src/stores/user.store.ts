import { atom } from 'jotai';
import { User } from '@/types/dashboard';
import type { WorkspaceMember, PersonaType, WorkspacePermission, WorkspaceRole } from '@ghatana/yappc-auth/rbac';

/**
 * Current authenticated user atom.
 */
export const currentUserAtom = atom<User | null>(null);

/**
 * User preferences atom.
 */
export const userPreferencesAtom = atom({
  theme: 'light' as 'light' | 'dark',
  language: 'en',
  timezone: 'UTC',
  notificationsEnabled: true,
});

/**
 * Selected tenant atom.
 */
export const selectedTenantAtom = atom<string | null>(null);

/**
 * Selected workspace atom.
 */
export const selectedWorkspaceAtom = atom<string | null>(null);

/**
 * Current workspace membership (includes role and personas).
 */
export const currentMembershipAtom = atom<WorkspaceMember | null>(null);

/**
 * Active persona for the current user in the current workspace.
 */
export const activePersonaAtom = atom<PersonaType>('developer');

/**
 * Current user's permissions in the active workspace.
 */
export const userPermissionsAtom = atom<WorkspacePermission[]>([]);

/**
 * Derived atom: Check if user has a specific permission.
 */
export const hasPermissionAtom = atom((get) => {
  const permissions = get(userPermissionsAtom);
  return (permission: WorkspacePermission) => permissions.includes(permission);
});

/**
 * Derived atom: Get user's role in current workspace.
 */
export const currentRoleAtom = atom<WorkspaceRole | null>((get) => {
  const membership = get(currentMembershipAtom);
  return membership?.role ?? null;
});

/**
 * Derived atom: Get available personas for current user.
 */
export const availablePersonasAtom = atom<PersonaType[]>((get) => {
  const membership = get(currentMembershipAtom);
  return membership?.personas ?? [];
});
