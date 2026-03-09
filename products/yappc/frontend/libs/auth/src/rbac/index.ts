/**
 * RBAC Module Exports
 *
 * @doc.type module
 * @doc.purpose Role-Based Access Control exports
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

// Core RBAC types
export type {
    Permission,
    Role,
    UserRole,
    AccessControlList,
    AuthorizationContext,
    AuthorizationDecision,
} from './types';

// Workspace-specific RBAC
export {
    WorkspaceRole,
    ROLE_PERMISSIONS,
    hasPermission,
    getPermissions,
    canManageRole,
    // New persona types and utilities
    PERSONA_HIERARCHY,
    PERSONA_CATEGORIES,
    PERSONA_DEFINITIONS,
    PERSONA_CAPABILITIES,
    getPersonasByCategory,
    getPersonasAtLevel,
    hasHigherAuthority,
    hasCapability,
    getCapabilities,
    getPersonasWithCapability,
} from './workspace.types';

export type {
    WorkspaceMember,
    WorkspaceInvitation,
    Workspace,
    WorkspaceSettings,
    PersonaType,
    PersonaCategory,
    PersonaMetadata,
    PersonaCapability,
    Permission as WorkspacePermission,
} from './workspace.types';

// RBAC utilities
export * from './utils';
