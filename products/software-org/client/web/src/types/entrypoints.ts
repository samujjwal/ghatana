/**
 * Entry Point Type Definitions
 *
 * Defines the entry point system for role-based access control at
 * different organizational layers and for different personas.
 *
 * <p><b>Purpose</b><br>
 * Entry points are the main access points for different user personas
 * at different organizational levels. Each entry point is protected
 * based on the user's role and permissions.
 *
 * <p><b>Hierarchy</b><br>
 * - Organization Level: CEO, Owners, root_user
 * - Executive Level: CTO, CPO, VPs
 * - Management Level: Directors, Team Leads
 * - Operations Level: Admins, DevOps
 * - Contributor Level: Engineers, Analysts
 *
 * @package @ghatana/software-org-web
 * @doc.type types
 * @doc.purpose Entry point access control type definitions
 * @doc.layer product
 * @doc.pattern Type Definition
 */

import type { PersonaType, HierarchyLayer } from '@/state/atoms/persona.atoms';

// =============================================================================
// Core Entry Point Types
// =============================================================================

/**
 * Organization layer that an entry point belongs to
 */
export type OrganizationLayer = 
  | 'organization'  // Top-level org (CEO, Owners)
  | 'department'    // Department level (VPs, Directors)
  | 'team'          // Team level (Team Leads, Managers)
  | 'individual';   // Individual level (ICs, Contributors)

/**
 * Access control mode for entry points
 */
export type AccessMode =
  | 'role-based'    // Access based on persona type
  | 'permission-based'  // Access based on specific permissions
  | 'layer-based'   // Access based on hierarchy layer
  | 'unrestricted'; // No access restrictions (e.g., root_user)

/**
 * Entry point visibility scope
 */
export type EntryPointScope =
  | 'global'        // Visible across the entire organization
  | 'department'    // Visible within a department
  | 'team'          // Visible within a team
  | 'personal';     // Visible only to the individual

/**
 * Access rule definition for an entry point
 */
export interface AccessRule {
  /** Access control mode */
  mode: AccessMode;

  /** Allowed persona types (for role-based) */
  allowedPersonas?: PersonaType[];

  /** Allowed hierarchy layers (for layer-based) */
  allowedLayers?: HierarchyLayer[];

  /** Required permissions (for permission-based) */
  requiredPermissions?: string[];

  /** Permission check mode: 'all' requires all permissions, 'any' requires at least one */
  permissionMode?: 'all' | 'any';

  /** Custom access check function (for advanced rules) */
  customCheck?: (context: EntryPointAccessContext) => boolean;
}

/**
 * Context provided to custom access check functions
 */
export interface EntryPointAccessContext {
  persona: {
    id: string;
    type: PersonaType;
    layer: HierarchyLayer;
    permissions: string[];
    departmentId?: string;
    teamId?: string;
  } | null;
  isRootUser: boolean;
  currentDepartmentId?: string;
  currentTeamId?: string;
}

/**
 * Entry point definition
 */
export interface EntryPoint {
  /** Unique identifier */
  id: string;

  /** Display name */
  name: string;

  /** Short description */
  description: string;

  /** Route path */
  route: string;

  /** Icon (emoji or icon component name) */
  icon: string;

  /** Organization layer this entry point belongs to */
  organizationLayer: OrganizationLayer;

  /** Target personas this entry point is designed for */
  targetPersonas: PersonaType[];

  /** Access rules for this entry point */
  accessRules: AccessRule;

  /** Visibility scope */
  scope: EntryPointScope;

  /** Display order within its category */
  order: number;

  /** Whether this is a primary entry point for the persona */
  isPrimary?: boolean;

  /** Badge count (e.g., pending items) - dynamic */
  badgeKey?: string;

  /** Parent entry point ID (for hierarchical organization) */
  parentId?: string;

  /** Child entry point IDs */
  childIds?: string[];

  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Entry point category for grouping
 */
export interface EntryPointCategory {
  /** Category ID */
  id: string;

  /** Category display name */
  name: string;

  /** Category description */
  description: string;

  /** Category icon */
  icon: string;

  /** Organization layer this category belongs to */
  layer: OrganizationLayer;

  /** Entry point IDs in this category */
  entryPointIds: string[];

  /** Display order */
  order: number;
}

// =============================================================================
// Root User Types
// =============================================================================

/**
 * Special root_user persona that has unrestricted access
 */
export const ROOT_USER_TYPE = 'root_user' as const;

/**
 * Extended persona type including root_user
 */
export type ExtendedPersonaType = PersonaType | typeof ROOT_USER_TYPE;

/**
 * Root user check function type
 */
export type IsRootUser = (personaId?: string, permissions?: string[]) => boolean;

// =============================================================================
// Entry Point Registry Types
// =============================================================================

/**
 * Entry point registry for managing all entry points
 */
export interface EntryPointRegistry {
  /** All entry points indexed by ID */
  entryPoints: Record<string, EntryPoint>;

  /** Categories for organizing entry points */
  categories: Record<string, EntryPointCategory>;

  /** Get entry points for a specific persona and layer */
  getEntryPointsForPersona: (
    personaType: ExtendedPersonaType,
    layer?: HierarchyLayer,
    context?: EntryPointAccessContext
  ) => EntryPoint[];

  /** Get entry points for a specific organization layer */
  getEntryPointsForLayer: (
    orgLayer: OrganizationLayer,
    context?: EntryPointAccessContext
  ) => EntryPoint[];

  /** Check if user can access an entry point */
  canAccess: (
    entryPointId: string,
    context: EntryPointAccessContext
  ) => boolean;

  /** Get all entry points (for root_user) */
  getAllEntryPoints: () => EntryPoint[];

  /** Get entry point by route */
  getEntryPointByRoute: (route: string) => EntryPoint | undefined;
}

// =============================================================================
// Access Check Results
// =============================================================================

/**
 * Result of an access check
 */
export interface AccessCheckResult {
  /** Whether access is allowed */
  allowed: boolean;

  /** Reason for denial (if not allowed) */
  reason?: string;

  /** Suggested entry point if current is not accessible */
  suggestedEntryPoint?: EntryPoint;

  /** Required upgrades to gain access */
  requiredUpgrades?: {
    personas?: PersonaType[];
    permissions?: string[];
    layers?: HierarchyLayer[];
  };
}

// =============================================================================
// Default Permissions for Entry Points
// =============================================================================

/**
 * Standard permissions for entry point access
 */
export const ENTRY_POINT_PERMISSIONS = {
  // View permissions
  VIEW_ORGANIZATION: 'entrypoint:org:view',
  VIEW_DEPARTMENT: 'entrypoint:department:view',
  VIEW_TEAM: 'entrypoint:team:view',
  VIEW_INDIVIDUAL: 'entrypoint:individual:view',

  // Admin permissions
  MANAGE_ENTRY_POINTS: 'entrypoint:manage',
  CONFIGURE_ACCESS: 'entrypoint:access:configure',

  // Special permissions
  ROOT_ACCESS: 'entrypoint:root:access',
  BYPASS_ACCESS_CONTROL: 'entrypoint:bypass:all',
} as const;

export type EntryPointPermission = typeof ENTRY_POINT_PERMISSIONS[keyof typeof ENTRY_POINT_PERMISSIONS];
