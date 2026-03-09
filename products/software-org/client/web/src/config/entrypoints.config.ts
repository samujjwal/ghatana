/**
 * Entry Points Registry Configuration
 *
 * Defines all entry points for different personas and organizational layers.
 * This is the single source of truth for navigation access control.
 *
 * <p><b>Purpose</b><br>
 * Provides a centralized registry of all application entry points with
 * their access rules, making it easy to:
 * - Control who can access what
 * - Show relevant navigation for each persona
 * - Support root_user with full access
 *
 * <p><b>Organization Structure</b><br>
 * Entry points are organized by:
 * 1. Organization Layer (org, department, team, individual)
 * 2. Target Persona (owner, executive, manager, ic, admin, root_user)
 * 3. Functional Area (operate, build, observe, admin)
 *
 * @package @ghatana/software-org-web
 * @doc.type configuration
 * @doc.purpose Entry point registry and access control
 * @doc.layer product
 * @doc.pattern Registry Pattern
 */

import type {
  EntryPoint,
  EntryPointCategory,
  EntryPointRegistry,
  EntryPointAccessContext,
  AccessCheckResult,
  ExtendedPersonaType,
  OrganizationLayer,
} from '@/types/entrypoints';
import type { HierarchyLayer, PersonaType } from '@/state/atoms/persona.atoms';
import { ENTRY_POINT_PERMISSIONS, ROOT_USER_TYPE } from '@/types/entrypoints';

// =============================================================================
// Entry Point Definitions
// =============================================================================

/**
 * All entry points organized by functional area
 */
export const ENTRY_POINTS: Record<string, EntryPoint> = {
  // =========================================================================
  // ORGANIZATION LEVEL - Owner/CEO Entry Points
  // =========================================================================
  
  'org-dashboard': {
    id: 'org-dashboard',
    name: 'Organization Dashboard',
    description: 'Strategic overview of the entire organization',
    route: '/',
    icon: '🏢',
    organizationLayer: 'organization',
    targetPersonas: ['owner'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['owner'],
    },
    scope: 'global',
    order: 1,
    isPrimary: true,
  },

  'org-overview': {
    id: 'org-overview',
    name: 'Organization Overview',
    description: 'Complete organizational structure and hierarchy',
    route: '/admin/organization',
    icon: '📊',
    organizationLayer: 'organization',
    targetPersonas: ['owner', 'admin'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['owner', 'admin'],
    },
    scope: 'global',
    order: 2,
  },

  'org-kpis': {
    id: 'org-kpis',
    name: 'Organization KPIs',
    description: 'Key performance indicators across the organization',
    route: '/observe/metrics',
    icon: '📈',
    organizationLayer: 'organization',
    targetPersonas: ['owner', 'executive'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['owner', 'executive'],
    },
    scope: 'global',
    order: 3,
  },

  'org-security': {
    id: 'org-security',
    name: 'Security & Compliance',
    description: 'Organization-wide security posture and compliance',
    route: '/admin/security',
    icon: '🔒',
    organizationLayer: 'organization',
    targetPersonas: ['owner', 'admin'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['owner', 'admin'],
    },
    scope: 'global',
    order: 4,
  },

  'org-reports': {
    id: 'org-reports',
    name: 'Executive Reports',
    description: 'High-level reports for leadership',
    route: '/observe/reports',
    icon: '📄',
    organizationLayer: 'organization',
    targetPersonas: ['owner', 'executive'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['owner', 'executive'],
    },
    scope: 'global',
    order: 5,
  },

  // =========================================================================
  // DEPARTMENT LEVEL - Executive/VP Entry Points
  // =========================================================================

  'dept-dashboard': {
    id: 'dept-dashboard',
    name: 'Department Dashboard',
    description: 'Department-level operations and metrics',
    route: '/',
    icon: '🏛️',
    organizationLayer: 'department',
    targetPersonas: ['executive'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['executive', 'owner'],
    },
    scope: 'department',
    order: 1,
    isPrimary: true,
  },

  'dept-stages': {
    id: 'dept-stages',
    name: 'DevSecOps Stages',
    description: 'DevSecOps pipeline stages and work items',
    route: '/operate/stages',
    icon: '🔄',
    organizationLayer: 'department',
    targetPersonas: ['executive', 'manager'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['executive', 'manager', 'owner'],
    },
    scope: 'department',
    order: 2,
  },

  'dept-workflows': {
    id: 'dept-workflows',
    name: 'Workflows',
    description: 'Automation workflows for the department',
    route: '/build/workflows',
    icon: '🔗',
    organizationLayer: 'department',
    targetPersonas: ['executive', 'manager'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['executive', 'manager', 'owner'],
    },
    scope: 'department',
    order: 3,
  },

  'dept-agents': {
    id: 'dept-agents',
    name: 'AI Agents',
    description: 'AI agents configured for department operations',
    route: '/build/agents',
    icon: '🤖',
    organizationLayer: 'department',
    targetPersonas: ['executive', 'manager'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['executive', 'manager', 'owner'],
    },
    scope: 'department',
    order: 4,
  },

  'dept-ml': {
    id: 'dept-ml',
    name: 'ML Observatory',
    description: 'Machine learning model performance',
    route: '/observe/ml',
    icon: '🔬',
    organizationLayer: 'department',
    targetPersonas: ['executive', 'manager'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['executive', 'manager', 'owner'],
    },
    scope: 'department',
    order: 5,
  },

  // =========================================================================
  // TEAM LEVEL - Manager Entry Points
  // =========================================================================

  'team-dashboard': {
    id: 'team-dashboard',
    name: 'Team Dashboard',
    description: 'Team operations and daily activities',
    route: '/',
    icon: '👥',
    organizationLayer: 'team',
    targetPersonas: ['manager'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['manager', 'executive', 'owner'],
    },
    scope: 'team',
    order: 1,
    isPrimary: true,
  },

  'team-queue': {
    id: 'team-queue',
    name: 'Work Queue',
    description: 'Pending tasks and approvals for the team',
    route: '/operate/queue',
    icon: '📋',
    organizationLayer: 'team',
    targetPersonas: ['manager', 'ic'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['manager', 'ic', 'executive', 'owner'],
    },
    scope: 'team',
    order: 2,
  },

  'team-incidents': {
    id: 'team-incidents',
    name: 'Incidents',
    description: 'Active incidents and response actions',
    route: '/operate/incidents',
    icon: '🚨',
    organizationLayer: 'team',
    targetPersonas: ['manager', 'ic'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['manager', 'ic', 'executive', 'owner'],
    },
    scope: 'team',
    order: 3,
  },

  'team-simulator': {
    id: 'team-simulator',
    name: 'Simulator',
    description: 'Test events and scenarios',
    route: '/build/simulator',
    icon: '⚡',
    organizationLayer: 'team',
    targetPersonas: ['manager', 'ic'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['manager', 'ic', 'executive', 'owner'],
    },
    scope: 'team',
    order: 4,
  },

  // =========================================================================
  // INDIVIDUAL LEVEL - IC Entry Points
  // =========================================================================

  'ic-dashboard': {
    id: 'ic-dashboard',
    name: 'My Dashboard',
    description: 'Personal workspace and tasks',
    route: '/',
    icon: '👤',
    organizationLayer: 'individual',
    targetPersonas: ['ic'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['ic', 'manager', 'executive', 'owner'],
    },
    scope: 'personal',
    order: 1,
    isPrimary: true,
  },

  'ic-queue': {
    id: 'ic-queue',
    name: 'My Queue',
    description: 'My assigned tasks and work items',
    route: '/operate/queue',
    icon: '📋',
    organizationLayer: 'individual',
    targetPersonas: ['ic'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['ic', 'manager', 'executive', 'owner'],
    },
    scope: 'personal',
    order: 2,
  },

  'ic-workflows': {
    id: 'ic-workflows',
    name: 'Workflows',
    description: 'View and execute workflows',
    route: '/build/workflows',
    icon: '🔗',
    organizationLayer: 'individual',
    targetPersonas: ['ic'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['ic', 'manager', 'executive', 'owner'],
    },
    scope: 'personal',
    order: 3,
  },

  // =========================================================================
  // ADMIN Entry Points (Cross-cutting)
  // =========================================================================

  'admin-dashboard': {
    id: 'admin-dashboard',
    name: 'Admin Dashboard',
    description: 'System administration and configuration',
    route: '/',
    icon: '⚙️',
    organizationLayer: 'organization',
    targetPersonas: ['admin', 'root_user'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['admin', 'owner', 'root_user'],
    },
    scope: 'global',
    order: 1,
    isPrimary: true,
  },

  'admin-organization': {
    id: 'admin-organization',
    name: 'Organization Management',
    description: 'Manage departments, teams, and personas',
    route: '/admin/organization',
    icon: '🏢',
    organizationLayer: 'organization',
    targetPersonas: ['admin'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['admin', 'owner'],
    },
    scope: 'global',
    order: 2,
  },

  'admin-security': {
    id: 'admin-security',
    name: 'Security Settings',
    description: 'Access control, audit logs, and security policies',
    route: '/admin/security',
    icon: '🔐',
    organizationLayer: 'organization',
    targetPersonas: ['admin'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['admin', 'owner'],
    },
    scope: 'global',
    order: 3,
  },

  'admin-settings': {
    id: 'admin-settings',
    name: 'System Settings',
    description: 'System configuration and preferences',
    route: '/admin/settings',
    icon: '🔧',
    organizationLayer: 'organization',
    targetPersonas: ['admin'],
    accessRules: {
      mode: 'role-based',
      allowedPersonas: ['admin', 'owner'],
    },
    scope: 'global',
    order: 4,
  },
};

// =============================================================================
// Category Definitions
// =============================================================================

export const ENTRY_POINT_CATEGORIES: Record<string, EntryPointCategory> = {
  organization: {
    id: 'organization',
    name: 'Organization',
    description: 'Organization-wide entry points',
    icon: '🏢',
    layer: 'organization',
    entryPointIds: ['org-dashboard', 'org-overview', 'org-kpis', 'org-security', 'org-reports'],
    order: 1,
  },
  department: {
    id: 'department',
    name: 'Department',
    description: 'Department-level entry points',
    icon: '🏛️',
    layer: 'department',
    entryPointIds: ['dept-dashboard', 'dept-stages', 'dept-workflows', 'dept-agents', 'dept-ml'],
    order: 2,
  },
  team: {
    id: 'team',
    name: 'Team',
    description: 'Team-level entry points',
    icon: '👥',
    layer: 'team',
    entryPointIds: ['team-dashboard', 'team-queue', 'team-incidents', 'team-simulator'],
    order: 3,
  },
  individual: {
    id: 'individual',
    name: 'Individual',
    description: 'Personal entry points',
    icon: '👤',
    layer: 'individual',
    entryPointIds: ['ic-dashboard', 'ic-queue', 'ic-workflows'],
    order: 4,
  },
  admin: {
    id: 'admin',
    name: 'Administration',
    description: 'System administration entry points',
    icon: '⚙️',
    layer: 'organization',
    entryPointIds: ['admin-dashboard', 'admin-organization', 'admin-security', 'admin-settings'],
    order: 5,
  },
};

// =============================================================================
// Access Control Functions
// =============================================================================

/**
 * Check if context represents a root user
 */
export function isRootUser(context: EntryPointAccessContext): boolean {
  if (context.isRootUser) return true;
  if (!context.persona) return false;
  
  // Check for root access permission
  return context.persona.permissions.includes(ENTRY_POINT_PERMISSIONS.ROOT_ACCESS) ||
         context.persona.permissions.includes(ENTRY_POINT_PERMISSIONS.BYPASS_ACCESS_CONTROL);
}

/**
 * Check if a persona can access an entry point
 */
export function checkEntryPointAccess(
  entryPoint: EntryPoint,
  context: EntryPointAccessContext
): AccessCheckResult {
  // Root users have unrestricted access
  if (isRootUser(context)) {
    return { allowed: true };
  }

  // No persona means no access
  if (!context.persona) {
    return {
      allowed: false,
      reason: 'Authentication required',
    };
  }

  const { accessRules } = entryPoint;

  // Handle unrestricted access
  if (accessRules.mode === 'unrestricted') {
    return { allowed: true };
  }

  // Handle role-based access
  if (accessRules.mode === 'role-based' && accessRules.allowedPersonas) {
    const allowed = accessRules.allowedPersonas.includes(context.persona.type);
    if (!allowed) {
      return {
        allowed: false,
        reason: `Access restricted to: ${accessRules.allowedPersonas.join(', ')}`,
        requiredUpgrades: {
          personas: accessRules.allowedPersonas,
        },
      };
    }
    return { allowed: true };
  }

  // Handle layer-based access
  if (accessRules.mode === 'layer-based' && accessRules.allowedLayers) {
    const allowed = accessRules.allowedLayers.includes(context.persona.layer);
    if (!allowed) {
      return {
        allowed: false,
        reason: `Access restricted to hierarchy levels: ${accessRules.allowedLayers.join(', ')}`,
        requiredUpgrades: {
          layers: accessRules.allowedLayers,
        },
      };
    }
    return { allowed: true };
  }

  // Handle permission-based access
  if (accessRules.mode === 'permission-based' && accessRules.requiredPermissions) {
    const mode = accessRules.permissionMode || 'all';
    const userPermissions = context.persona.permissions;

    let allowed: boolean;
    if (mode === 'all') {
      allowed = accessRules.requiredPermissions.every(p => userPermissions.includes(p));
    } else {
      allowed = accessRules.requiredPermissions.some(p => userPermissions.includes(p));
    }

    if (!allowed) {
      return {
        allowed: false,
        reason: `Missing required permissions`,
        requiredUpgrades: {
          permissions: accessRules.requiredPermissions.filter(p => !userPermissions.includes(p)),
        },
      };
    }
    return { allowed: true };
  }

  // Handle custom access check
  if (accessRules.customCheck) {
    const allowed = accessRules.customCheck(context);
    if (!allowed) {
      return {
        allowed: false,
        reason: 'Custom access check failed',
      };
    }
    return { allowed: true };
  }

  // Default: deny access
  return {
    allowed: false,
    reason: 'No access rule matched',
  };
}

// =============================================================================
// Registry Implementation
// =============================================================================

/**
 * Create the entry point registry
 */
export function createEntryPointRegistry(): EntryPointRegistry {
  return {
    entryPoints: ENTRY_POINTS,
    categories: ENTRY_POINT_CATEGORIES,

    getEntryPointsForPersona(
      personaType: ExtendedPersonaType,
      _layer?: HierarchyLayer,
      context?: EntryPointAccessContext
    ): EntryPoint[] {
      // Root user gets all entry points
      if (personaType === ROOT_USER_TYPE) {
        return Object.values(ENTRY_POINTS).sort((a, b) => a.order - b.order);
      }

      const effectiveContext: EntryPointAccessContext = context || {
        persona: null,
        isRootUser: false,
      };

      return Object.values(ENTRY_POINTS)
        .filter(ep => {
          // Include if this persona is a target
          if (!ep.targetPersonas.includes(personaType as PersonaType)) {
            return false;
          }
          // Check access rules
          return checkEntryPointAccess(ep, effectiveContext).allowed;
        })
        .sort((a, b) => a.order - b.order);
    },

    getEntryPointsForLayer(
      orgLayer: OrganizationLayer,
      context?: EntryPointAccessContext
    ): EntryPoint[] {
      const effectiveContext: EntryPointAccessContext = context || {
        persona: null,
        isRootUser: false,
      };

      // Root users get all entry points for the layer
      if (isRootUser(effectiveContext)) {
        return Object.values(ENTRY_POINTS)
          .filter(ep => ep.organizationLayer === orgLayer)
          .sort((a, b) => a.order - b.order);
      }

      return Object.values(ENTRY_POINTS)
        .filter(ep => {
          if (ep.organizationLayer !== orgLayer) return false;
          return checkEntryPointAccess(ep, effectiveContext).allowed;
        })
        .sort((a, b) => a.order - b.order);
    },

    canAccess(entryPointId: string, context: EntryPointAccessContext): boolean {
      const entryPoint = ENTRY_POINTS[entryPointId];
      if (!entryPoint) return false;
      return checkEntryPointAccess(entryPoint, context).allowed;
    },

    getAllEntryPoints(): EntryPoint[] {
      return Object.values(ENTRY_POINTS).sort((a, b) => a.order - b.order);
    },

    getEntryPointByRoute(route: string): EntryPoint | undefined {
      return Object.values(ENTRY_POINTS).find(ep => ep.route === route);
    },
  };
}

// =============================================================================
// Singleton Registry Instance
// =============================================================================

let registryInstance: EntryPointRegistry | null = null;

/**
 * Get the entry point registry singleton
 */
export function getEntryPointRegistry(): EntryPointRegistry {
  if (!registryInstance) {
    registryInstance = createEntryPointRegistry();
  }
  return registryInstance;
}

// =============================================================================
// Convenience Functions
// =============================================================================

/**
 * Get all entry points grouped by category
 */
export function getEntryPointsByCategory(): Record<string, EntryPoint[]> {
  const registry = getEntryPointRegistry();
  const result: Record<string, EntryPoint[]> = {};

  for (const [categoryId, category] of Object.entries(ENTRY_POINT_CATEGORIES)) {
    result[categoryId] = category.entryPointIds
      .map(id => registry.entryPoints[id])
      .filter(Boolean)
      .sort((a, b) => a.order - b.order);
  }

  return result;
}

/**
 * Get primary entry points for each persona type
 */
export function getPrimaryEntryPoints(): Record<string, EntryPoint | undefined> {
  const result: Record<string, EntryPoint | undefined> = {};
  
  for (const ep of Object.values(ENTRY_POINTS)) {
    if (ep.isPrimary) {
      for (const persona of ep.targetPersonas) {
        if (!result[persona]) {
          result[persona] = ep;
        }
      }
    }
  }

  return result;
}
