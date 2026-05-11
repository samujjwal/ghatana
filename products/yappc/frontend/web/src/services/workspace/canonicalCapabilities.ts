/**
 * Canonical Capabilities — YAPPC Web.
 *
 * Defines the canonical capability model that aligns with the backend ResourceScope
 * authorization model and YAPPC lifecycle phases. Capabilities gate UI actions based on
 * user roles, resource scope, and backend feature flags.
 *
 * ## Capability Hierarchy
 *
 * Capabilities are organized by:
 * - **Resource Scope**: TENANT, WORKSPACE, PROJECT, ARTIFACT
 * - **Lifecycle Phase**: Intent, Shape, Validate, Generate, Run, Observe, Learn, Evolve
 * - **Action Type**: read, create, update, delete, approve, reject, rollback
 *
 * ## Canonical Roles
 *
 * - OWNER: Full control over workspace and projects
 * - ADMIN: Can manage workspace settings and project access
 * - DEVELOPER: Can create and modify projects and artifacts
 * - VIEWER: Read-only access to projects and artifacts
 *
 * @doc.type module
 * @doc.purpose Canonical capability definitions aligned with backend authorization model
 * @doc.layer product
 * @doc.pattern Capability Registry
 */

// ─────────────────────────────────────────────────────────────────────────────
// Canonical Roles
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Canonical user roles in YAPPC.
 * These align with the backend RBAC model and determine capability access.
 */
export type CanonicalRole = 'OWNER' | 'ADMIN' | 'DEVELOPER' | 'VIEWER';

/**
 * Resource isolation levels matching backend ResourceScope enum.
 */
export type ResourceScope = 'TENANT' | 'WORKSPACE' | 'PROJECT' | 'ARTIFACT' | 'SYSTEM';

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle Phase Capabilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lifecycle phase capabilities.
 * Each phase has specific actions that can be performed based on user role and permissions.
 */
export type LifecyclePhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

/**
 * Action types that can be performed on lifecycle phases.
 */
export type ActionType = 'read' | 'create' | 'update' | 'delete' | 'approve' | 'reject' | 'rollback';

/**
 * Lifecycle phase capability definition.
 */
export interface LifecycleCapability {
  readonly phase: LifecyclePhase;
  readonly action: ActionType;
  readonly resourceScope: ResourceScope;
  readonly requiredRoles: readonly CanonicalRole[];
  readonly enabled: boolean; // Backend feature flag
  readonly description: string;
}

/**
 * Registry of canonical lifecycle phase capabilities.
 */
export const LIFECYCLE_CAPABILITIES: readonly LifecycleCapability[] = [
  // Intent phase
  {
    phase: 'intent',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View intent requirements and analysis',
  },
  {
    phase: 'intent',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Create intent requirements',
  },
  {
    phase: 'intent',
    action: 'update',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Update intent requirements',
  },
  {
    phase: 'intent',
    action: 'delete',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Delete intent requirements',
  },

  // Shape phase
  {
    phase: 'shape',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View shape models and architecture',
  },
  {
    phase: 'shape',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Create shape models',
  },
  {
    phase: 'shape',
    action: 'update',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Update shape models',
  },

  // Validate phase
  {
    phase: 'validate',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View validation results',
  },
  {
    phase: 'validate',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Run validation checks',
  },

  // Generate phase
  {
    phase: 'generate',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View generated artifacts',
  },
  {
    phase: 'generate',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Generate artifacts',
  },
  {
    phase: 'generate',
    action: 'approve',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Approve generated artifacts',
  },
  {
    phase: 'generate',
    action: 'reject',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Reject generated artifacts',
  },
  {
    phase: 'generate',
    action: 'rollback',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Rollback to previous artifact version',
  },

  // Run phase
  {
    phase: 'run',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View run status and results',
  },
  {
    phase: 'run',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Execute build/test/deploy',
  },
  {
    phase: 'run',
    action: 'rollback',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Rollback deployment',
  },

  // Observe phase
  {
    phase: 'observe',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View observability metrics and logs',
  },
  {
    phase: 'observe',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Collect observability data',
  },

  // Learn phase
  {
    phase: 'learn',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View learning and feedback',
  },
  {
    phase: 'learn',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Submit feedback and learning data',
  },

  // Evolve phase
  {
    phase: 'evolve',
    action: 'read',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View evolution proposals',
  },
  {
    phase: 'evolve',
    action: 'create',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Propose evolution changes',
  },
  {
    phase: 'evolve',
    action: 'approve',
    resourceScope: 'PROJECT',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Approve evolution proposals',
  },
] as const;

// ─────────────────────────────────────────────────────────────────────────────
// Workspace/Project Capabilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Workspace-level capabilities.
 */
export interface WorkspaceCapability {
  readonly name: string;
  readonly action: ActionType;
  readonly requiredRoles: readonly CanonicalRole[];
  readonly enabled: boolean;
  readonly description: string;
}

/**
 * Project-level capabilities.
 */
export interface ProjectCapability {
  readonly name: string;
  readonly action: ActionType;
  readonly requiredRoles: readonly CanonicalRole[];
  readonly enabled: boolean;
  readonly description: string;
}

/**
 * Registry of canonical workspace capabilities.
 */
export const WORKSPACE_CAPABILITIES: readonly WorkspaceCapability[] = [
  {
    name: 'workspace:read',
    action: 'read',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View workspace details',
  },
  {
    name: 'workspace:update',
    action: 'update',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Update workspace settings',
  },
  {
    name: 'workspace:delete',
    action: 'delete',
    requiredRoles: ['OWNER'],
    enabled: true,
    description: 'Delete workspace',
  },
  {
    name: 'workspace:members:read',
    action: 'read',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View workspace members',
  },
  {
    name: 'workspace:members:update',
    action: 'update',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Manage workspace members',
  },
  {
    name: 'workspace:projects:create',
    action: 'create',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Create projects in workspace',
  },
] as const;

/**
 * Registry of canonical project capabilities.
 */
export const PROJECT_CAPABILITIES: readonly ProjectCapability[] = [
  {
    name: 'project:read',
    action: 'read',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View project details',
  },
  {
    name: 'project:update',
    action: 'update',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Update project settings',
  },
  {
    name: 'project:delete',
    action: 'delete',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Delete project',
  },
  {
    name: 'project:include',
    action: 'create',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Include project in workspace',
  },
  {
    name: 'project:export',
    action: 'read',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Export project artifacts',
  },
] as const;

// ─────────────────────────────────────────────────────────────────────────────
// Artifact Capabilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Artifact-level capabilities.
 */
export interface ArtifactCapability {
  readonly name: string;
  readonly action: ActionType;
  readonly resourceScope: ResourceScope;
  readonly requiredRoles: readonly CanonicalRole[];
  readonly enabled: boolean;
  readonly description: string;
}

/**
 * Registry of canonical artifact capabilities.
 */
export const ARTIFACT_CAPABILITIES: readonly ArtifactCapability[] = [
  {
    name: 'artifact:read',
    action: 'read',
    resourceScope: 'ARTIFACT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'View artifact details',
  },
  {
    name: 'artifact:update',
    action: 'update',
    resourceScope: 'ARTIFACT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Update artifact',
  },
  {
    name: 'artifact:delete',
    action: 'delete',
    resourceScope: 'ARTIFACT',
    requiredRoles: ['OWNER', 'ADMIN'],
    enabled: true,
    description: 'Delete artifact',
  },
  {
    name: 'artifact:import',
    action: 'create',
    resourceScope: 'ARTIFACT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Import external artifacts',
  },
  {
    name: 'artifact:preview',
    action: 'read',
    resourceScope: 'ARTIFACT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'],
    enabled: true,
    description: 'Preview artifact',
  },
  {
    name: 'artifact:review',
    action: 'update',
    resourceScope: 'ARTIFACT',
    requiredRoles: ['OWNER', 'ADMIN', 'DEVELOPER'],
    enabled: true,
    description: 'Review and approve artifacts',
  },
] as const;

// ─────────────────────────────────────────────────────────────────────────────
// Capability Check Functions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Checks if a role has access to a capability.
 */
export function hasCapabilityAccess(
  role: CanonicalRole | undefined,
  requiredRoles: readonly CanonicalRole[]
): boolean {
  if (!role) return false;
  return requiredRoles.includes(role);
}

/**
 * Gets all lifecycle capabilities for a specific phase.
 */
export function getPhaseCapabilities(phase: LifecyclePhase): readonly LifecycleCapability[] {
  return LIFECYCLE_CAPABILITIES.filter((cap) => cap.phase === phase);
}

/**
 * Gets all capabilities a role can perform for a phase.
 */
export function getPhaseCapabilitiesForRole(
  phase: LifecyclePhase,
  role: CanonicalRole
): readonly LifecycleCapability[] {
  return getPhaseCapabilities(phase).filter((cap) => cap.requiredRoles.includes(role));
}

/**
 * Checks if a role can perform a specific action on a phase.
 */
export function canPerformPhaseAction(
  phase: LifecyclePhase,
  action: ActionType,
  role: CanonicalRole
): boolean {
  const capability = LIFECYCLE_CAPABILITIES.find(
    (cap) => cap.phase === phase && cap.action === action
  );
  if (!capability) return false;
  return capability.enabled && capability.requiredRoles.includes(role);
}
