/**
 * Persona State Management
 *
 * Jotai atoms for managing persona context across the application.
 * Provides type-safe persona information, permissions, and role checks.
 *
 * @package @ghatana/software-org-web
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

/**
 * Persona types representing different roles in the organization.
 * Maps to Java Layer enum: EXECUTIVE (owner/executive), MANAGEMENT (manager), INDIVIDUAL_CONTRIBUTOR (ic)
 * Admin is a cross-cutting role for system administration.
 * root_user is a special superuser with unrestricted access to all entry points.
 */
export type PersonaType = 'owner' | 'executive' | 'manager' | 'ic' | 'admin' | 'root_user';

/**
 * Hierarchy layer for organizational structure.
 * Aligns with Java Layer enum in virtual-org-framework.
 * 'root' is the highest level, above organization.
 */
export type HierarchyLayer = 'root' | 'organization' | 'executive' | 'management' | 'operations' | 'contributor';

/**
 * Maps PersonaType to HierarchyLayer for consistent hierarchy handling.
 */
export const PERSONA_TO_LAYER: Record<PersonaType, HierarchyLayer> = {
  root_user: 'root',
  owner: 'organization',
  executive: 'executive',
  manager: 'management',
  admin: 'operations',
  ic: 'contributor',
};

/**
 * Hierarchy level for authority comparison (higher = more authority).
 * Aligns with Java Layer.getLevel() values.
 * root_user has the highest authority level (5).
 */
export const HIERARCHY_LEVELS: Record<HierarchyLayer, number> = {
  root: 5,
  organization: 4,
  executive: 3,
  management: 2,
  operations: 2, // Same level as management but different scope
  contributor: 1,
};

/**
 * Root user permissions - grants unrestricted access to all entry points
 */
export const ROOT_USER_PERMISSIONS = [
  'entrypoint:root:access',
  'entrypoint:bypass:all',
  'org:restructure',
  'department:restructure',
  'team:restructure',
  'users:manage',
  'users:invite',
  'users:remove',
  'permissions:manage',
  'audit:view',
  'approvals:restructure',
  'approvals:budget',
  'system:manage',
  'system:health:view',
  '*', // Wildcard permission for root_user
] as const;

/**
 * Default root_user persona for development/testing
 */
export const ROOT_USER_PERSONA: Persona = {
  id: 'root_user',
  type: 'root_user',
  layer: 'root',
  name: 'Root User',
  email: 'root@ghatana.io',
  permissions: [...ROOT_USER_PERMISSIONS],
  metadata: {
    isRootUser: true,
    createdAt: new Date().toISOString(),
  },
};

/**
 * Persona interface representing a user's organizational role and context
 */
export interface Persona {
  /** Unique identifier for the persona */
  id: string;

  /** Type of persona (owner, executive, manager, ic, admin) */
  type: PersonaType;

  /** Hierarchy layer derived from persona type */
  layer?: HierarchyLayer;

  /** Display name */
  name: string;

  /** Email address */
  email: string;

  /** Department ID (if applicable) */
  departmentId?: string;

  /** Team ID (if applicable) */
  teamId?: string;

  /** List of permissions granted to this persona */
  permissions: string[];

  /** Avatar URL (optional) */
  avatarUrl?: string;

  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Current active persona
 *
 * This is the primary atom that holds the current user's persona information.
 * All other persona-related atoms derive from this.
 * Persisted to localStorage for session continuity.
 */
export const currentPersonaAtom = atomWithStorage<Persona | null>('current-persona', null);

/**
 * Current persona type
 *
 * Derived atom that extracts just the persona type for easy access.
 */
export const personaTypeAtom = atom((get) => {
  const persona = get(currentPersonaAtom);
  return persona?.type ?? null;
});

/**
 * Current persona permissions
 *
 * Derived atom that provides easy access to the permissions array.
 */
export const personaPermissionsAtom = atom((get) => {
  const persona = get(currentPersonaAtom);
  return persona?.permissions ?? [];
});

/**
 * Is current persona an Owner?
 */
export const isOwnerAtom = atom((get) => get(personaTypeAtom) === 'owner');

/**
 * Is current persona an Executive (CTO, CPO, etc.)?
 */
export const isExecutiveAtom = atom((get) => get(personaTypeAtom) === 'executive');

/**
 * Is current persona a Manager?
 */
export const isManagerAtom = atom((get) => get(personaTypeAtom) === 'manager');

/**
 * Is current persona an Individual Contributor?
 */
export const isICAtom = atom((get) => get(personaTypeAtom) === 'ic');

/**
 * Is current persona an Admin?
 */
export const isAdminAtom = atom((get) => get(personaTypeAtom) === 'admin');

/**
 * Is current persona a Root User (superuser with unrestricted access)?
 */
export const isRootUserAtom = atom((get) => {
  const personaType = get(personaTypeAtom);
  const permissions = get(personaPermissionsAtom);
  
  // Check by type
  if (personaType === 'root_user') return true;
  
  // Check by permissions (wildcard or root access)
  return permissions.includes('*') || permissions.includes('entrypoint:root:access');
});

/**
 * Current persona's hierarchy layer
 */
export const personaLayerAtom = atom((get) => {
  const personaType = get(personaTypeAtom);
  if (!personaType) return null;
  return PERSONA_TO_LAYER[personaType];
});

/**
 * Current persona's hierarchy level (for authority comparison)
 */
export const personaLevelAtom = atom((get) => {
  const layer = get(personaLayerAtom);
  if (!layer) return 0;
  return HIERARCHY_LEVELS[layer];
});

/**
 * Is current persona in a leadership role (owner, executive, or manager)?
 */
export const isLeadershipAtom = atom((get) => {
  const level = get(personaLevelAtom);
  return level >= HIERARCHY_LEVELS.management;
});

/**
 * Persona context summary
 *
 * Provides a complete summary of the current persona context for debugging
 * or display purposes.
 */
export const personaContextAtom = atom((get) => {
  const persona = get(currentPersonaAtom);
  if (!persona) return null;

  return {
    persona,
    type: get(personaTypeAtom),
    layer: get(personaLayerAtom),
    level: get(personaLevelAtom),
    permissions: get(personaPermissionsAtom),
    isOwner: get(isOwnerAtom),
    isExecutive: get(isExecutiveAtom),
    isManager: get(isManagerAtom),
    isIC: get(isICAtom),
    isAdmin: get(isAdminAtom),
    isRootUser: get(isRootUserAtom),
    isLeadership: get(isLeadershipAtom),
  };
});

