import { z } from 'zod';

// Permission and Role Schemas - Phase 6 Implementation
export const UserRoleSchema = z.enum(['owner', 'admin', 'editor', 'viewer', 'commenter']);

export const PermissionScopeSchema = z.object({
  canRead: z.boolean(),
  canEdit: z.boolean(),
  canDelete: z.boolean(),
  canComment: z.boolean(),
  canShare: z.boolean(),
  canInvite: z.boolean(),
  canManagePermissions: z.boolean(),
  canExport: z.boolean(),
});

export const UserPermissionSchema = z.object({
  userId: z.string(),
  email: z.string().email(),
  role: UserRoleSchema,
  permissions: PermissionScopeSchema,
  grantedBy: z.string(),
  grantedAt: z.string().datetime(),
  expiresAt: z.string().datetime().optional(),
});

export const ShareTokenSchema = z.object({
  token: z.string().min(1),
  canvasId: z.string(),
  permissions: PermissionScopeSchema,
  createdBy: z.string(),
  createdAt: z.string().datetime(),
  expiresAt: z.string().datetime().optional(),
  maxUses: z.number().optional(),
  currentUses: z.number().default(0),
  isActive: z.boolean().default(true),
});

export const PermissionRequestSchema = z.object({
  action: z.enum(['read', 'edit', 'delete', 'comment', 'share', 'invite', 'manage', 'export']),
  resourceType: z.enum(['canvas', 'node', 'edge', 'comment']),
  resourceId: z.string(),
  userId: z.string(),
});

export const PermissionResponseSchema = z.object({
  allowed: z.boolean(),
  reason: z.string().optional(),
  requiredRole: UserRoleSchema.optional(),
});

// Permission management API schemas
export const CreatePermissionRequestSchema = z.object({
  canvasId: z.string(),
  userId: z.string(),
  role: UserRoleSchema,
  permissions: PermissionScopeSchema.optional(),
  expiresAt: z.string().datetime().optional(),
});

export const UpdatePermissionRequestSchema = z.object({
  role: UserRoleSchema.optional(),
  permissions: PermissionScopeSchema.optional(),
  expiresAt: z.string().datetime().optional(),
});

export const CreateShareTokenRequestSchema = z.object({
  canvasId: z.string(),
  permissions: PermissionScopeSchema,
  expiresAt: z.string().datetime().optional(),
  maxUses: z.number().optional(),
});

// Type inference
/**
 *
 */
export type UserRole = z.infer<typeof UserRoleSchema>;
/**
 *
 */
export type PermissionScope = z.infer<typeof PermissionScopeSchema>;
/**
 *
 */
export type UserPermission = z.infer<typeof UserPermissionSchema>;
/**
 *
 */
export type ShareToken = z.infer<typeof ShareTokenSchema>;
/**
 *
 */
export type PermissionRequest = z.infer<typeof PermissionRequestSchema>;
/**
 *
 */
export type PermissionResponse = z.infer<typeof PermissionResponseSchema>;
/**
 *
 */
export type CreatePermissionRequest = z.infer<typeof CreatePermissionRequestSchema>;
/**
 *
 */
export type UpdatePermissionRequest = z.infer<typeof UpdatePermissionRequestSchema>;
/**
 *
 */
export type CreateShareTokenRequest = z.infer<typeof CreateShareTokenRequestSchema>;

// Permission helpers
export const getDefaultPermissions = (role: UserRole): PermissionScope => {
  switch (role) {
    case 'owner':
      return {
        canRead: true,
        canEdit: true,
        canDelete: true,
        canComment: true,
        canShare: true,
        canInvite: true,
        canManagePermissions: true,
        canExport: true,
      };
    case 'admin':
      return {
        canRead: true,
        canEdit: true,
        canDelete: true,
        canComment: true,
        canShare: true,
        canInvite: true,
        canManagePermissions: true,
        canExport: true,
      };
    case 'editor':
      return {
        canRead: true,
        canEdit: true,
        canDelete: false,
        canComment: true,
        canShare: false,
        canInvite: false,
        canManagePermissions: false,
        canExport: true,
      };
    case 'commenter':
      return {
        canRead: true,
        canEdit: false,
        canDelete: false,
        canComment: true,
        canShare: false,
        canInvite: false,
        canManagePermissions: false,
        canExport: false,
      };
    case 'viewer':
      return {
        canRead: true,
        canEdit: false,
        canDelete: false,
        canComment: false,
        canShare: false,
        canInvite: false,
        canManagePermissions: false,
        canExport: false,
      };
    default:
      return {
        canRead: false,
        canEdit: false,
        canDelete: false,
        canComment: false,
        canShare: false,
        canInvite: false,
        canManagePermissions: false,
        canExport: false,
      };
  }
};

export const hasPermission = (
  userPermissions: PermissionScope,
  action: PermissionRequest['action']
): boolean => {
  switch (action) {
    case 'read':
      return userPermissions.canRead;
    case 'edit':
      return userPermissions.canEdit;
    case 'delete':
      return userPermissions.canDelete;
    case 'comment':
      return userPermissions.canComment;
    case 'share':
      return userPermissions.canShare;
    case 'invite':
      return userPermissions.canInvite;
    case 'manage':
      return userPermissions.canManagePermissions;
    case 'export':
      return userPermissions.canExport;
    default:
      return false;
  }
};

export const canPerformAction = (
  userRole: UserRole,
  action: PermissionRequest['action'],
  customPermissions?: PermissionScope
): boolean => {
  const permissions = customPermissions || getDefaultPermissions(userRole);
  return hasPermission(permissions, action);
};