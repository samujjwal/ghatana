/**
 * Common Domain Types
 *
 * Core types shared across the YAPPC frontend — mirrors the Prisma schema
 * shapes returned over GraphQL (dates serialised to ISO strings).
 *
 * @module types/common
 * @doc.type module
 * @doc.purpose Shared domain type definitions
 * @doc.layer product
 * @doc.pattern Value Object
 */

// ============================================================================
// Enums (mirrors Prisma enums)
// ============================================================================

export type UserRole = 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';

export type ProjectType =
  | 'FULL_STACK'
  | 'FRONTEND'
  | 'BACKEND'
  | 'MOBILE'
  | 'DATA'
  | 'INFRASTRUCTURE'
  | 'OTHER';

export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED' | 'COMPLETED';

export type ProjectLifecyclePhase =
  | 'PLANNING'
  | 'DEVELOPMENT'
  | 'TESTING'
  | 'STAGING'
  | 'PRODUCTION'
  | 'MAINTENANCE'
  | 'DEPRECATED';

export type TaskStatus =
  | 'TODO'
  | 'IN_PROGRESS'
  | 'IN_REVIEW'
  | 'DONE'
  | 'CANCELLED';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

// ============================================================================
// User
// ============================================================================

/**
 * Represents an authenticated YAPPC user.
 */
export interface User {
  id: string;
  email: string;
  name?: string | null;
  avatarUrl?: string | null;
  role?: UserRole;
  createdAt?: string;
  updatedAt?: string;
}

// ============================================================================
// Workspace
// ============================================================================

/**
 * Represents an organisational workspace container.
 */
export interface Workspace {
  id: string;
  name: string;
  description?: string | null;
  ownerId: string;
  isDefault?: boolean;
  aiSummary?: string | null;
  aiTags?: string[];
  createdAt: string;
  updatedAt: string;
}

/**
 * A member of a workspace with their assigned role.
 */
export interface WorkspaceMember {
  id: string;
  userId: string;
  workspaceId: string;
  role: UserRole;
  user?: Pick<User, 'id' | 'email' | 'name' | 'avatarUrl'>;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// Project
// ============================================================================

/**
 * Represents a project within a workspace.
 */
export interface Project {
  id: string;
  workspaceId: string;
  name: string;
  description?: string | null;
  type: ProjectType;
  status: ProjectStatus;
  lifecyclePhase?: ProjectLifecyclePhase | null;
  isDefault?: boolean;
  aiSummary?: string | null;
  aiNextActions?: string[] | null;
  aiHealthScore?: number | null;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// Task
// ============================================================================

/**
 * Represents a task within a project.
 */
export interface Task {
  id: string;
  projectId: string;
  title: string;
  description?: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeId?: string | null;
  dueDate?: string | null;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// Canvas
// ============================================================================

/**
 * Represents a canvas document inside a project.
 */
export interface CanvasDocument {
  id: string;
  projectId: string;
  title: string;
  content?: unknown;
  createdAt: string;
  updatedAt: string;
}

/**
 * A saved snapshot of canvas content.
 */
export interface CanvasVersion {
  id: string;
  canvasId: string;
  version: number;
  content: unknown;
  changeType: 'MANUAL_SAVE' | 'AUTO_SAVE' | 'RESTORE' | 'MERGE';
  changedBy: string;
  changeSummary?: string | null;
  baseVersion?: number | null;
  mergedFrom?: number | null;
  createdAt: string;
}
