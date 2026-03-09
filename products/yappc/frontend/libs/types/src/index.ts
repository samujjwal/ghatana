/**
 * Unified type definitions for YAPPC App Creator.
 *
 * <p><b>Purpose</b><br>
 * Provides canonical type definitions for all YAPPC features including Canvas, PageBuilder,
 * Designer, and core platform entities. This is the single source of truth for all type
 * definitions across the YAPPC product.
 *
 * <p><b>Organization</b><br>
 * Types are organized by feature domain:
 * - Core entities (User, Workspace, Project)
 * - Canvas types (CanvasNode, CanvasEdge, CanvasDocument)
 * - PageBuilder types (Page, Component, Layout)
 * - Designer types (Design, Theme, Token)
 * - Collaboration types (CollaborationSession, Presence)
 *
 * <p><b>Usage</b><br>
 * Import types from this module for all YAPPC features. Never duplicate type definitions
 * in individual feature modules.
 *
 * @doc.type module
 * @doc.purpose Unified type definitions for YAPPC
 * @doc.layer product
 * @doc.pattern Value Object
 */

// ============================================================================
// CORE ENTITIES
// ============================================================================

/**
 * Represents a user in the YAPPC system.
 *
 * <p><b>Purpose</b><br>
 * Identifies and stores user information for authentication, authorization, and collaboration.
 *
 * @doc.type interface
 * @doc.purpose User identity and profile
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface User {
  /** Unique identifier for the user */
  id: string;
  /** User's display name */
  name: string;
  /** User's email address */
  email: string;
  /** Optional avatar URL */
  avatar?: string;
  /** User's role in the system */
  role?: 'admin' | 'editor' | 'viewer';
  /** Timestamp when user was created */
  createdAt: string;
  /** Timestamp when user was last updated */
  updatedAt: string;
}

/**
 * Represents a workspace containing projects and resources.
 *
 * <p><b>Purpose</b><br>
 * Provides organizational boundary for grouping related projects and managing access control.
 *
 * @doc.type interface
 * @doc.purpose Workspace container for projects
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Workspace {
  /** Unique identifier for the workspace */
  id: string;
  /** Workspace name */
  name: string;
  /** Optional workspace description */
  description?: string;
  /** ID of the workspace owner */
  ownerId: string;
  /** List of member IDs in the workspace */
  members?: string[];
  /** Timestamp when workspace was created */
  createdAt: string;
  /** Timestamp when workspace was last updated */
  updatedAt: string;
}

/**
 * Represents a project within a workspace.
 *
 * <p><b>Purpose</b><br>
 * Top-level container for YAPPC artifacts including canvas documents, pages, and components.
 *
 * @doc.type interface
 * @doc.purpose Project container
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Project {
  /** Unique identifier for the project */
  id: string;
  /** ID of the workspace this project belongs to */
  workspaceId: string;
  /** Project name */
  name: string;
  /** Optional project description */
  description?: string;
  /** Type of project */
  type: ProjectType;
  /** Target platforms for this project */
  targets: ProjectTarget[];
  /** Current status of the project */
  status: ProjectStatus;
  /** Timestamp when project was created */
  createdAt: string;
  /** Timestamp when project was last updated */
  updatedAt: string;
}

/**
 * Enumeration of supported project types.
 *
 * @doc.type type
 * @doc.purpose Project type classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type ProjectType = 'UI' | 'Backend' | 'Mobile' | 'Desktop' | 'Full-Stack';

/**
 * Enumeration of deployment targets.
 *
 * @doc.type type
 * @doc.purpose Deployment target specification
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type ProjectTarget = 'web' | 'mobile' | 'desktop' | 'api' | 'database';

/**
 * Enumeration of project lifecycle states.
 *
 * @doc.type type
 * @doc.purpose Project status classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type ProjectStatus = 'draft' | 'active' | 'completed' | 'archived';

/**
 * Represents a task or work item.
 *
 * <p><b>Purpose</b><br>
 * Tracks work items, assignments, and progress within a project.
 *
 * @doc.type interface
 * @doc.purpose Work item tracking
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Task {
  /** Unique identifier for the task */
  id: string;
  /** ID of the project this task belongs to */
  projectId: string;
  /** Task title */
  title: string;
  /** Optional task description */
  description?: string;
  /** Current status of the task */
  status: TaskStatus;
  /** ID of the user assigned to this task */
  assigneeId?: string;
  /** Timestamp when task was created */
  createdAt: string;
  /** Timestamp when task was last updated */
  updatedAt: string;
  /** Optional due date for the task */
  dueDate?: string;
}

/**
 * Enumeration of task lifecycle states.
 *
 * @doc.type type
 * @doc.purpose Task status classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type TaskStatus = 'todo' | 'in_progress' | 'blocked' | 'done';

/**
 * Represents a comment on a task or document.
 *
 * <p><b>Purpose</b><br>
 * Enables threaded discussions and feedback on work items.
 *
 * @doc.type interface
 * @doc.purpose Comment and discussion
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Comment {
  /** Unique identifier for the comment */
  id: string;
  /** ID of the task this comment belongs to */
  taskId: string;
  /** ID of the user who authored the comment */
  authorId: string;
  /** Comment content */
  content: string;
  /** Optional attachments */
  attachments?: Attachment[];
  /** Timestamp when comment was created */
  createdAt: string;
  /** Timestamp when comment was last updated */
  updatedAt: string;
}

/**
 * Represents a file attachment.
 *
 * <p><b>Purpose</b><br>
 * Stores metadata for files attached to comments or tasks.
 *
 * @doc.type interface
 * @doc.purpose File attachment metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Attachment {
  /** Unique identifier for the attachment */
  id: string;
  /** File name */
  name: string;
  /** URL to access the file */
  url: string;
  /** Type of attachment */
  type: 'image' | 'document' | 'video' | 'other';
  /** File size in bytes */
  size: number;
}

/**
 * Represents a user notification.
 *
 * <p><b>Purpose</b><br>
 * Notifies users of important events and updates.
 *
 * @doc.type interface
 * @doc.purpose User notification
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Notification {
  /** Unique identifier for the notification */
  id: string;
  /** ID of the user receiving the notification */
  userId: string;
  /** Type of notification */
  type: NotificationType;
  /** Notification title */
  title: string;
  /** Notification message */
  message: string;
  /** Whether the notification has been read */
  read: boolean;
  /** Optional URL for action */
  actionUrl?: string;
  /** Timestamp when notification was created */
  createdAt: string;
}

/**
 * Enumeration of notification types.
 *
 * @doc.type type
 * @doc.purpose Notification type classification
 * @doc.layer product
 * @doc.pattern Value Object
 */
export type NotificationType = 'mention' | 'assignment' | 'comment' | 'status_change' | 'system';

// ============================================================================
// CANVAS TYPES
// ============================================================================

/**
 * Represents a canvas document containing nodes and edges.
 *
 * <p><b>Purpose</b><br>
 * Top-level container for canvas-based diagrams, flowcharts, and visual designs.
 *
 * @doc.type interface
 * @doc.purpose Canvas document container
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CanvasDocument {
  /** Unique identifier for the canvas document */
  id: string;
  /** ID of the project this canvas belongs to */
  projectId: string;
  /** Canvas name */
  name: string;
  /** Optional canvas description */
  description?: string;
  /** Array of nodes in the canvas */
  nodes: CanvasNode[];
  /** Array of edges connecting nodes */
  edges: CanvasEdge[];
  /** Canvas viewport settings */
  viewport?: CanvasViewport;
  /** Timestamp when canvas was created */
  createdAt: string;
  /** Timestamp when canvas was last updated */
  updatedAt: string;
}

/**
 * Represents a node in a canvas diagram.
 *
 * <p><b>Purpose</b><br>
 * Visual element in a canvas that can represent components, concepts, or entities.
 *
 * @doc.type interface
 * @doc.purpose Canvas node element
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CanvasNode {
  /** Unique identifier for the node */
  id: string;
  /** Type of node (component, shape, text, etc.) */
  type: string;
  /** Node label or display text */
  label: string;
  /** X coordinate of the node */
  x: number;
  /** Y coordinate of the node */
  y: number;
  /** Node width */
  width?: number;
  /** Node height */
  height?: number;
  /** Optional node data/properties */
  data?: Record<string, unknown>;
  /** Optional styling properties */
  style?: Record<string, unknown>;
}

/**
 * Represents an edge connecting two nodes in a canvas.
 *
 * <p><b>Purpose</b><br>
 * Visual connection between canvas nodes representing relationships or flow.
 *
 * @doc.type interface
 * @doc.purpose Canvas edge connection
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CanvasEdge {
  /** Unique identifier for the edge */
  id: string;
  /** ID of the source node */
  source: string;
  /** ID of the target node */
  target: string;
  /** Optional edge label */
  label?: string;
  /** Optional edge data/properties */
  data?: Record<string, unknown>;
  /** Optional styling properties */
  style?: Record<string, unknown>;
}

/**
 * Represents canvas viewport settings.
 *
 * <p><b>Purpose</b><br>
 * Stores viewport zoom and pan information for canvas rendering.
 *
 * @doc.type interface
 * @doc.purpose Canvas viewport configuration
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CanvasViewport {
  /** Zoom level (1.0 = 100%) */
  zoom: number;
  /** X pan offset */
  x: number;
  /** Y pan offset */
  y: number;
}

// ============================================================================
// PAGE BUILDER TYPES
// ============================================================================

/**
 * Represents a page in the page builder.
 *
 * <p><b>Purpose</b><br>
 * Container for page-level content and components in the page builder.
 *
 * @doc.type interface
 * @doc.purpose Page builder page
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Page {
  /** Unique identifier for the page */
  id: string;
  /** ID of the project this page belongs to */
  projectId: string;
  /** Page name */
  name: string;
  /** Optional page description */
  description?: string;
  /** Page route/path */
  path: string;
  /** Array of components on this page */
  components: PageComponent[];
  /** Page layout type */
  layout?: 'grid' | 'flex' | 'absolute';
  /** Timestamp when page was created */
  createdAt: string;
  /** Timestamp when page was last updated */
  updatedAt: string;
}

/**
 * Represents a component in the page builder.
 *
 * <p><b>Purpose</b><br>
 * Reusable UI component with properties and children.
 *
 * @doc.type interface
 * @doc.purpose Page builder component
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PageComponent {
  /** Unique identifier for the component */
  id: string;
  /** Component type (Button, Input, Card, etc.) */
  type: string;
  /** Component display name */
  name: string;
  /** Component props/properties */
  props?: Record<string, unknown>;
  /** Child components */
  children?: PageComponent[];
  /** Styling properties */
  style?: Record<string, unknown>;
  /** Optional component metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Represents a layout template.
 *
 * <p><b>Purpose</b><br>
 * Reusable layout structure for pages.
 *
 * @doc.type interface
 * @doc.purpose Layout template
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface Layout {
  /** Unique identifier for the layout */
  id: string;
  /** Layout name */
  name: string;
  /** Layout description */
  description?: string;
  /** Layout structure */
  structure: PageComponent;
  /** Timestamp when layout was created */
  createdAt: string;
  /** Timestamp when layout was last updated */
  updatedAt: string;
}

// ============================================================================
// COLLABORATION TYPES
// ============================================================================

/**
 * Represents a collaboration session.
 *
 * <p><b>Purpose</b><br>
 * Tracks active collaboration sessions for real-time editing.
 *
 * @doc.type interface
 * @doc.purpose Collaboration session
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CollaborationSession {
  /** Unique identifier for the session */
  id: string;
  /** ID of the document being edited */
  documentId: string;
  /** Array of active participants */
  participants: CollaborationParticipant[];
  /** Session start time */
  startedAt: string;
  /** Optional session end time */
  endedAt?: string;
}

/**
 * Represents a participant in a collaboration session.
 *
 * <p><b>Purpose</b><br>
 * Tracks user presence and activity in collaborative editing.
 *
 * @doc.type interface
 * @doc.purpose Collaboration participant
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface CollaborationParticipant {
  /** ID of the user */
  userId: string;
  /** User's display name */
  name: string;
  /** User's avatar color for presence indicator */
  color: string;
  /** User's current cursor position */
  cursorPosition?: { x: number; y: number };
  /** Timestamp when user joined */
  joinedAt: string;
}

/**
 * Represents a change in a collaborative document.
 *
 * <p><b>Purpose</b><br>
 * Records changes made by users for undo/redo and audit trails.
 *
 * @doc.type interface
 * @doc.purpose Document change record
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface DocumentChange {
  /** Unique identifier for the change */
  id: string;
  /** ID of the document being changed */
  documentId: string;
  /** ID of the user making the change */
  userId: string;
  /** Type of change (create, update, delete) */
  type: 'create' | 'update' | 'delete';
  /** Path to the changed property */
  path: string;
  /** Previous value */
  oldValue?: unknown;
  /** New value */
  newValue?: unknown;
  /** Timestamp when change was made */
  timestamp: string;
}

// ============================================================================
// REGISTRY TYPES
// ============================================================================

/**
 * Re-export all registry types from registry module.
 * These are the canonical types for all registry systems.
 */
export * from './registry';

// ============================================================================
// TASK TYPES
// ============================================================================

/**
 * Re-export all task types from tasks module.
 * These are the canonical types for the task execution framework.
 */
export * from './tasks';

// ============================================================================
// WORKFLOW TYPES
// ============================================================================

/**
 * Re-export all workflow types from workflow module.
 * These are the canonical types for the Unified Workflow UX system.
 */
export * from './workflow';

// ============================================================================
// AI TYPES
// ============================================================================

/**
 * Re-export all AI types from ai module.
 * These are the canonical types for AI features including predictions,
 * recommendations, anomaly detection, and intelligent suggestions.
 */
export * from './ai';

// ============================================================================
// CONFIGURATION TYPES
// ============================================================================

/**
 * Task domain configuration loaded from YAML files.
 */
export interface TaskDomain {
  id: string;
  name: string;
  description: string;
  order: number;
  taskCount: number;
  icon: string;
  color: string;
  file: string;
  tasks: TaskDefinition[];
}

/**
 * Individual task definition within a domain.
 */
export interface TaskDefinition {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  subtasks: Subtask[];
  tools: Tool[];
  collaborators: string[];
  dependencies: string[];
  successCriteria: string[];
  estimatedDuration: string;
}

/**
 * Subtask definition.
 */
export interface Subtask {
  id: string;
  name: string;
  description: string;
}

/**
 * Tool definition for tasks.
 */
export interface Tool {
  name: string;
  purpose: string;
}

/**
 * Workflow configuration loaded from YAML files.
 */
export interface Workflow {
  id: string;
  name: string;
  description: string;
  lifecycleStages: string[];
  phases: WorkflowPhase[];
}

/**
 * Workflow phase definition.
 */
export interface WorkflowPhase {
  id: string;
  name: string;
  description: string;
  stages: string[];
  tasks: string[];
  estimatedDuration: string;
}

/**
 * Lifecycle configuration loaded from YAML files.
 */
export interface LifecycleConfig {
  stages: LifecycleStage[];
  transitions: LifecycleTransition[];
}

/**
 * Lifecycle stage definition.
 */
export interface LifecycleStage {
  id: string;
  name: string;
  description: string;
  order: number;
  icon: string;
  color: string;
  requiredArtifacts: string[];
  outputArtifacts: string[];
}

/**
 * Lifecycle transition definition.
 */
export interface LifecycleTransition {
  from: string;
  to: string;
  conditions: string[];
  autoTrigger: boolean;
}

/**
 * Agent capabilities configuration loaded from YAML files.
 */
export interface AgentCapabilities {
  capabilities: Capability[];
  mappings: Record<string, string[]>;
}

/**
 * Individual capability definition.
 */
export interface Capability {
  id: string;
  name: string;
  description: string;
  category: string;
  prerequisites: string[];
  parameters: Record<string, unknown>;
}

// ============================================================================
// CONSOLIDATED: State management (from @ghatana/yappc-canvas)
// ============================================================================
export * from '../../state/src';

