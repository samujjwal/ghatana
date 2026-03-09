/**
 * usePersona Hook
 *
 * Custom React hook for accessing and managing persona state.
 * Provides convenient access to persona information, permissions,
 * and role-based helpers.
 *
 * @package @ghatana/software-org-web
 */

import { useAtom, useAtomValue } from 'jotai';
import {
  currentPersonaAtom,
  personaTypeAtom,
  personaPermissionsAtom,
  personaLayerAtom,
  personaLevelAtom,
  isOwnerAtom,
  isExecutiveAtom,
  isManagerAtom,
  isICAtom,
  isAdminAtom,
  isRootUserAtom,
  isLeadershipAtom,
  personaContextAtom,
  ROOT_USER_PERSONA,
  type Persona,
  type PersonaType,
  type HierarchyLayer,
  HIERARCHY_LEVELS,
} from '@/state/atoms/persona.atoms';

/**
 * Permission constants for common operations
 */
export const PERMISSIONS = {
  // Org structure permissions
  RESTRUCTURE_ORG: 'org:restructure',
  RESTRUCTURE_DEPARTMENT: 'department:restructure',
  RESTRUCTURE_TEAM: 'team:restructure',

  // User management permissions
  MANAGE_USERS: 'users:manage',
  INVITE_USERS: 'users:invite',
  REMOVE_USERS: 'users:remove',

  // Access control permissions
  MANAGE_PERMISSIONS: 'permissions:manage',
  VIEW_AUDIT_LOG: 'audit:view',

  // Approval permissions
  APPROVE_RESTRUCTURE: 'approvals:restructure',
  APPROVE_BUDGET: 'approvals:budget',

  // System permissions
  MANAGE_SYSTEM: 'system:manage',
  VIEW_SYSTEM_HEALTH: 'system:health:view',
} as const;

/**
 * Hook return type
 */
export interface UsePersonaReturn {
  /** Current persona object */
  persona: Persona | null;

  /** Function to set/update persona */
  setPersona: (persona: Persona | null) => void;

  /** Current persona type */
  personaType: PersonaType | null;

  /** Is owner? */
  isOwner: boolean;

  /** Is executive (CTO, CPO, etc.)? */
  isExecutive: boolean;

  /** Is manager? */
  isManager: boolean;

  /** Is individual contributor? */
  isIC: boolean;

  /** Is admin? */
  isAdmin: boolean;

  /** Is root user (superuser with unrestricted access)? */
  isRootUser: boolean;

  /** Is in a leadership role (owner, executive, or manager)? */
  isLeadership: boolean;

  /** Current hierarchy layer */
  layer: HierarchyLayer | null;

  /** Current hierarchy level (for authority comparison) */
  level: number;

  /** Check if persona has specific permission */
  hasPermission: (permission: string) => boolean;

  /** Check if persona has any of the permissions */
  hasAnyPermission: (permissions: string[]) => boolean;

  /** Check if persona has all of the permissions */
  hasAllPermissions: (permissions: string[]) => boolean;

  /** Can restructure organization? */
  canRestructure: () => boolean;

  /** Can manage users? */
  canManageUsers: () => boolean;

  /** Can approve requests? */
  canApprove: () => boolean;

  /** Check if persona has higher authority than another layer */
  hasHigherAuthority: (otherLayer: HierarchyLayer) => boolean;

  /** Is authenticated? */
  isAuthenticated: boolean;

  /** Login as root user (for development/testing) */
  loginAsRootUser: () => void;

  /** Complete persona context */
  context: {
    persona: Persona;
    type: PersonaType | null;
    layer: HierarchyLayer | null;
    level: number;
    permissions: string[];
    isOwner: boolean;
    isExecutive: boolean;
    isManager: boolean;
    isIC: boolean;
    isAdmin: boolean;
    isRootUser: boolean;
    isLeadership: boolean;
  } | null;
}

/**
 * usePersona Hook
 *
 * Provides access to persona state and permission checking utilities.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { persona, isOwner, hasPermission, canRestructure } = usePersona();
 *
 *   if (!persona) return <Login />;
 *
 *   return (
 *     <div>
 *       <h1>Welcome, {persona.name}</h1>
 *       {isOwner && <OwnerControls />}
 *       {canRestructure() && <RestructureButton />}
 *     </div>
 *   );
 * }
 * ```
 */
export function usePersona(): UsePersonaReturn {
  const [persona, setPersona] = useAtom(currentPersonaAtom);
  const personaType = useAtomValue(personaTypeAtom);
  const permissions = useAtomValue(personaPermissionsAtom);
  const layer = useAtomValue(personaLayerAtom);
  const level = useAtomValue(personaLevelAtom);
  const isOwner = useAtomValue(isOwnerAtom);
  const isExecutive = useAtomValue(isExecutiveAtom);
  const isManager = useAtomValue(isManagerAtom);
  const isIC = useAtomValue(isICAtom);
  const isAdmin = useAtomValue(isAdminAtom);
  const isRootUser = useAtomValue(isRootUserAtom);
  const isLeadership = useAtomValue(isLeadershipAtom);
  const context = useAtomValue(personaContextAtom);

  /**
   * Check if persona has a specific permission
   * Root users have all permissions via wildcard
   */
  const hasPermission = (permission: string): boolean => {
    if (isRootUser || permissions.includes('*')) return true;
    return permissions.includes(permission);
  };

  /**
   * Check if persona has any of the specified permissions
   */
  const hasAnyPermission = (perms: string[]): boolean => {
    if (isRootUser || permissions.includes('*')) return true;
    return perms.some((perm) => permissions.includes(perm));
  };

  /**
   * Check if persona has all of the specified permissions
   */
  const hasAllPermissions = (perms: string[]): boolean => {
    if (isRootUser || permissions.includes('*')) return true;
    return perms.every((perm) => permissions.includes(perm));
  };

  /**
   * Check if persona can restructure organization/departments/teams
   */
  const canRestructure = (): boolean => {
    // Root users and owners can always restructure
    if (isRootUser || isOwner) return true;

    // Executives can restructure their departments
    if (isExecutive && hasAnyPermission([
      PERMISSIONS.RESTRUCTURE_ORG,
      PERMISSIONS.RESTRUCTURE_DEPARTMENT,
    ])) {
      return true;
    }

    // Managers can restructure their department/team
    if (isManager && hasAnyPermission([
      PERMISSIONS.RESTRUCTURE_DEPARTMENT,
      PERMISSIONS.RESTRUCTURE_TEAM,
    ])) {
      return true;
    }

    // Check for explicit org restructure permission
    return hasPermission(PERMISSIONS.RESTRUCTURE_ORG);
  };

  /**
   * Check if persona can manage users
   */
  const canManageUsers = (): boolean => {
    return isRootUser || isOwner || isExecutive || isAdmin || hasPermission(PERMISSIONS.MANAGE_USERS);
  };

  /**
   * Check if persona can approve requests
   */
  const canApprove = (): boolean => {
    return isRootUser || isOwner || isExecutive || isManager || hasAnyPermission([
      PERMISSIONS.APPROVE_RESTRUCTURE,
      PERMISSIONS.APPROVE_BUDGET,
    ]);
  };

  /**
   * Check if persona has higher authority than another layer
   */
  const hasHigherAuthority = (otherLayer: HierarchyLayer): boolean => {
    return level > HIERARCHY_LEVELS[otherLayer];
  };

  /**
   * Login as root user (for development/testing)
   */
  const loginAsRootUser = (): void => {
    setPersona(ROOT_USER_PERSONA);
  };

  return {
    persona,
    setPersona,
    personaType,
    layer,
    level,
    isOwner,
    isExecutive,
    isManager,
    isIC,
    isAdmin,
    isRootUser,
    isLeadership,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    canRestructure,
    canManageUsers,
    canApprove,
    hasHigherAuthority,
    isAuthenticated: persona !== null,
    loginAsRootUser,
    context,
  };
}

/**
 * Mock personas for development/testing
 */
export const MOCK_PERSONAS = {
  owner: {
    id: 'owner-1',
    type: 'owner' as PersonaType,
    name: 'Alice Owner',
    email: 'alice@example.com',
    permissions: [
      PERMISSIONS.RESTRUCTURE_ORG,
      PERMISSIONS.MANAGE_USERS,
      PERMISSIONS.MANAGE_PERMISSIONS,
      PERMISSIONS.APPROVE_RESTRUCTURE,
      PERMISSIONS.APPROVE_BUDGET,
      PERMISSIONS.VIEW_AUDIT_LOG,
    ],
  },
  executive: {
    id: 'executive-1',
    type: 'executive' as PersonaType,
    name: 'Eve CTO',
    email: 'eve@example.com',
    departmentId: 'engineering',
    permissions: [
      PERMISSIONS.RESTRUCTURE_DEPARTMENT,
      PERMISSIONS.MANAGE_USERS,
      PERMISSIONS.APPROVE_RESTRUCTURE,
      PERMISSIONS.APPROVE_BUDGET,
      PERMISSIONS.VIEW_AUDIT_LOG,
    ],
  },
  manager: {
    id: 'manager-1',
    type: 'manager' as PersonaType,
    name: 'Bob Manager',
    email: 'bob@example.com',
    departmentId: 'eng-dept',
    teamId: 'backend-team',
    permissions: [
      PERMISSIONS.RESTRUCTURE_DEPARTMENT,
      PERMISSIONS.RESTRUCTURE_TEAM,
      PERMISSIONS.INVITE_USERS,
      PERMISSIONS.APPROVE_RESTRUCTURE,
    ],
  },
  ic: {
    id: 'ic-1',
    type: 'ic' as PersonaType,
    name: 'Charlie Developer',
    email: 'charlie@example.com',
    departmentId: 'eng-dept',
    teamId: 'backend-team',
    permissions: [],
  },
  admin: {
    id: 'admin-1',
    type: 'admin' as PersonaType,
    name: 'Diana Admin',
    email: 'diana@example.com',
    permissions: [
      PERMISSIONS.MANAGE_SYSTEM,
      PERMISSIONS.VIEW_SYSTEM_HEALTH,
      PERMISSIONS.MANAGE_USERS,
      PERMISSIONS.MANAGE_PERMISSIONS,
      PERMISSIONS.VIEW_AUDIT_LOG,
    ],
  },
  root_user: ROOT_USER_PERSONA,
} as const;

