/**
 * @ghatana/sso-client — RBAC sub-module
 *
 * Platform-level Role-Based Access Control utilities migrated from `@yappc/auth`.
 *
 * @doc.type module
 * @doc.purpose Role definitions, permissions, and authorization utilities
 * @doc.layer platform
 * @doc.pattern Barrel Export
 */

// Core RBAC interfaces (Policy, Role, UserRole, AuthorizationContext, etc.)
export type {
  Permission,
  Role,
  UserRole,
  AccessControlList,
  AuthorizationContext,
  AuthorizationDecision,
  Policy,
  PolicyRule,
} from './types';
export * from './utils';

// Workspace role / persona types and enums
// Note: workspace.types.ts also exports `Permission` as a type alias.
// We avoid re-exporting it here to prevent ambiguity — use rbac/types.ts Permission.
export {
  WorkspaceRole,
  ROLE_PERMISSIONS,
  hasPermission,
  getPermissions,
  canManageRole,
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
} from './workspace.types';
