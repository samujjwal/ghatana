/**
 * Collaboration System - Phase 6 Implementation
 * Real-time collaboration features for canvas system
 */

// Server and types
export * from './server';

// Hooks for collaboration
export * from './hooks';

// Comment system components
export * from './comment-components';

// Common collaboration utilities
export const COLLABORATION_EVENTS = {
  PERMISSION_CHANGED: 'collaboration:permission:changed',
  USER_JOINED: 'collaboration:user:joined',
  USER_LEFT: 'collaboration:user:left',
  COMMENT_ADDED: 'collaboration:comment:added',
  COMMENT_UPDATED: 'collaboration:comment:updated',
  COMMENT_DELETED: 'collaboration:comment:deleted',
} as const;

// Type exports for external use
export type {
  // Server types
  CollaborationServer,
  CanvasPermission,
  ExtendedShareToken,
  CreatePermissionRequest,
} from './server';

export type {
  // Hook types
  UsePermissionsConfig,
  UsePermissionsReturn,
  UseShareTokensConfig,
  UseShareTokensReturn,
  UseUserPresenceConfig,
  UseUserPresenceReturn,
  UserPresence,
} from './hooks';

export type {
  // Component types
  CommentInputProps,
  CommentItemProps,
  CommentThreadProps,
} from './comment-components';