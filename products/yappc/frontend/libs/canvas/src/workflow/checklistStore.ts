/**
 * Feature 2.19: Ready-for-Dev Workflow
 * 
 * Implements a checklist-based workflow system for marking canvas documents as
 * "ready for development". Provides task management, gating conditions, spec
 * bundle export, and status notifications.
 * 
 * Core features:
 * - Checklist management (create, update, complete tasks)
 * - Workflow gating (prevent transitions until criteria met)
 * - Spec bundle export (JSON/PDF with all requirements)
 * - Status tracking and change notifications
 * - Template support for common workflows
 * - Task dependencies and validation
 * 
 * @module workflow/checklistStore
 */

/**
 * Task priority levels
 */
export type TaskPriority = 'low' | 'medium' | 'high' | 'critical';

/**
 * Task status values
 */
export type TaskStatus = 'pending' | 'in-progress' | 'completed' | 'blocked';

/**
 * Workflow stage values
 */
export type WorkflowStage = 'draft' | 'review' | 'approved' | 'ready-for-dev' | 'in-development' | 'completed';

/**
 * Represents a single checklist task
 */
export interface ChecklistTask {
  /** Unique task identifier */
  id: string;
  
  /** Task title/name */
  title: string;
  
  /** Optional detailed description */
  description?: string;
  
  /** Current status */
  status: TaskStatus;
  
  /** Priority level */
  priority: TaskPriority;
  
  /** Task order in the list */
  order: number;
  
  /** Whether this task is required for approval */
  required: boolean;
  
  /** IDs of tasks that must be completed first */
  dependsOn?: string[];
  
  /** User assigned to this task */
  assignee?: string;
  
  /** Due date (ISO timestamp) */
  dueDate?: string;
  
  /** Completion timestamp */
  completedAt?: string;
  
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Checklist template for common workflows
 */
export interface ChecklistTemplate {
  /** Template identifier */
  id: string;
  
  /** Template name */
  name: string;
  
  /** Template description */
  description: string;
  
  /** Predefined tasks */
  tasks: Omit<ChecklistTask, 'id' | 'order'>[];
}

/**
 * Workflow validation rule
 */
export interface ValidationRule {
  /** Rule identifier */
  id: string;
  
  /** Rule description */
  description: string;
  
  /** Validation function that returns true if valid */
  validate: (state: WorkflowState) => boolean;
  
  /** Error message if validation fails */
  errorMessage: string;
}

/**
 * Workflow state change notification
 */
export interface WorkflowNotification {
  /** Notification ID */
  id: string;
  
  /** Notification type */
  type: 'task-completed' | 'task-blocked' | 'stage-changed' | 'approval-required' | 'ready-for-dev';
  
  /** Notification message */
  message: string;
  
  /** Related task ID (if applicable) */
  taskId?: string;
  
  /** Timestamp */
  timestamp: string;
  
  /** Whether notification has been read */
  read: boolean;
}

/**
 * Spec bundle export format
 */
export interface SpecBundle {
  /** Bundle version */
  version: string;
  
  /** Workflow metadata */
  metadata: {
    title: string;
    stage: WorkflowStage;
    exportedAt: string;
    exportedBy?: string;
  };
  
  /** All checklist tasks */
  tasks: ChecklistTask[];
  
  /** Completion statistics */
  statistics: {
    totalTasks: number;
    completedTasks: number;
    requiredTasks: number;
    completedRequiredTasks: number;
    completionPercentage: number;
  };
  
  /** Canvas document reference */
  canvasReference?: string;
  
  /** Additional notes */
  notes?: string;
}

/**
 * Main workflow state
 */
export interface WorkflowState {
  /** All tasks indexed by ID */
  tasks: Map<string, ChecklistTask>;
  
  /** Ordered list of task IDs */
  taskOrder: string[];
  
  /** Current workflow stage */
  stage: WorkflowStage;
  
  /** Workflow metadata */
  metadata: {
    title: string;
    createdAt: string;
    updatedAt: string;
    createdBy?: string;
  };
  
  /** Notifications */
  notifications: WorkflowNotification[];
  
  /** Validation rules */
  validationRules: ValidationRule[];
  
  /** Whether workflow is locked (read-only) */
  locked: boolean;
}

/**
 * Options for creating a workflow state
 */
export interface CreateWorkflowOptions {
  title: string;
  createdBy?: string;
  template?: ChecklistTemplate;
}

/**
 * Creates a new workflow state
 */
export function createWorkflowState(options: CreateWorkflowOptions): WorkflowState {
  const now = new Date().toISOString();
  
  const state: WorkflowState = {
    tasks: new Map(),
    taskOrder: [],
    stage: 'draft',
    metadata: {
      title: options.title,
      createdAt: now,
      updatedAt: now,
      createdBy: options.createdBy,
    },
    notifications: [],
    validationRules: [],
    locked: false,
  };
  
  // Apply template if provided
  if (options.template) {
    let order = 0;
    for (const taskTemplate of options.template.tasks) {
      const taskId = `task-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
      const task: ChecklistTask = {
        ...taskTemplate,
        id: taskId,
        order: order++,
      };
      state.tasks.set(taskId, task);
      state.taskOrder.push(taskId);
    }
  }
  
  return state;
}

/**
 * Creates a new checklist task
 */
export function createTask(
  state: WorkflowState,
  options: Omit<ChecklistTask, 'id' | 'order' | 'status'>
): WorkflowState {
  if (state.locked) {
    return state;
  }
  
  const taskId = `task-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
  const task: ChecklistTask = {
    ...options,
    id: taskId,
    order: state.taskOrder.length,
    status: 'pending',
  };
  
  return {
    ...state,
    tasks: new Map(state.tasks).set(taskId, task),
    taskOrder: [...state.taskOrder, taskId],
    metadata: {
      ...state.metadata,
      updatedAt: new Date().toISOString(),
    },
  };
}

/**
 * Gets a task by ID
 */
export function getTask(state: WorkflowState, taskId: string): ChecklistTask | undefined {
  return state.tasks.get(taskId);
}

/**
 * Gets all tasks in order
 */
export function getAllTasks(state: WorkflowState): ChecklistTask[] {
  return state.taskOrder.map((id) => state.tasks.get(id)!).filter(Boolean);
}

/**
 * Updates task properties
 */
export function updateTask(
  state: WorkflowState,
  taskId: string,
  updates: Partial<Omit<ChecklistTask, 'id' | 'order'>>
): WorkflowState {
  if (state.locked) {
    return state;
  }
  
  const task = state.tasks.get(taskId);
  if (!task) {
    return state;
  }
  
  const updatedTask: ChecklistTask = {
    ...task,
    ...updates,
  };
  
  const newTasks = new Map(state.tasks);
  newTasks.set(taskId, updatedTask);
  
  return {
    ...state,
    tasks: newTasks,
    metadata: {
      ...state.metadata,
      updatedAt: new Date().toISOString(),
    },
  };
}

/**
 * Marks a task as completed
 */
export function completeTask(state: WorkflowState, taskId: string): WorkflowState {
  const task = state.tasks.get(taskId);
  if (!task || state.locked) {
    return state;
  }
  
  // Check dependencies
  if (task.dependsOn && task.dependsOn.length > 0) {
    const unmetDeps = task.dependsOn.filter((depId) => {
      const dep = state.tasks.get(depId);
      return !dep || dep.status !== 'completed';
    });
    
    if (unmetDeps.length > 0) {
      // Cannot complete due to unmet dependencies
      return state;
    }
  }
  
  const now = new Date().toISOString();
  let newState = updateTask(state, taskId, {
    status: 'completed',
    completedAt: now,
  });
  
  // Add notification
  const notification: WorkflowNotification = {
    id: `notif-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    type: 'task-completed',
    message: `Task "${task.title}" completed`,
    taskId,
    timestamp: now,
    read: false,
  };
  
  newState = {
    ...newState,
    notifications: [...newState.notifications, notification],
  };
  
  // Check if all required tasks are completed
  if (isReadyForDev(newState)) {
    const readyNotification: WorkflowNotification = {
      id: `notif-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      type: 'ready-for-dev',
      message: 'All required tasks completed. Ready for development!',
      timestamp: now,
      read: false,
    };
    
    newState = {
      ...newState,
      notifications: [...newState.notifications, readyNotification],
    };
  }
  
  return newState;
}

/**
 * Deletes a task
 */
export function deleteTask(state: WorkflowState, taskId: string): WorkflowState {
  if (state.locked) {
    return state;
  }
  
  const newTasks = new Map(state.tasks);
  newTasks.delete(taskId);
  
  const newOrder = state.taskOrder.filter((id) => id !== taskId);
  
  // Update order indices
  newOrder.forEach((id, index) => {
    const task = newTasks.get(id);
    if (task) {
      newTasks.set(id, { ...task, order: index });
    }
  });
  
  // Remove task from dependencies
  newTasks.forEach((task) => {
    if (task.dependsOn && task.dependsOn.includes(taskId)) {
      newTasks.set(task.id, {
        ...task,
        dependsOn: task.dependsOn.filter((id) => id !== taskId),
      });
    }
  });
  
  return {
    ...state,
    tasks: newTasks,
    taskOrder: newOrder,
    metadata: {
      ...state.metadata,
      updatedAt: new Date().toISOString(),
    },
  };
}

/**
 * Reorders tasks
 */
export function reorderTasks(state: WorkflowState, newOrder: string[]): WorkflowState {
  if (state.locked) {
    return state;
  }
  
  // Validate that all task IDs exist
  if (newOrder.length !== state.taskOrder.length) {
    return state;
  }
  
  for (const id of newOrder) {
    if (!state.tasks.has(id)) {
      return state;
    }
  }
  
  // Update order indices
  const newTasks = new Map(state.tasks);
  newOrder.forEach((id, index) => {
    const task = newTasks.get(id);
    if (task) {
      newTasks.set(id, { ...task, order: index });
    }
  });
  
  return {
    ...state,
    tasks: newTasks,
    taskOrder: newOrder,
    metadata: {
      ...state.metadata,
      updatedAt: new Date().toISOString(),
    },
  };
}

/**
 * Changes the workflow stage
 */
export function changeStage(state: WorkflowState, newStage: WorkflowStage): WorkflowState {
  if (state.locked) {
    return state;
  }
  
  // Validate stage transition
  if (!canTransitionToStage(state, newStage)) {
    return state;
  }
  
  const now = new Date().toISOString();
  const notification: WorkflowNotification = {
    id: `notif-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    type: 'stage-changed',
    message: `Workflow stage changed from "${state.stage}" to "${newStage}"`,
    timestamp: now,
    read: false,
  };
  
  return {
    ...state,
    stage: newStage,
    notifications: [...state.notifications, notification],
    metadata: {
      ...state.metadata,
      updatedAt: now,
    },
  };
}

/**
 * Checks if workflow can transition to a new stage
 */
export function canTransitionToStage(state: WorkflowState, targetStage: WorkflowStage): boolean {
  const stageOrder: WorkflowStage[] = ['draft', 'review', 'approved', 'ready-for-dev', 'in-development', 'completed'];
  const currentIndex = stageOrder.indexOf(state.stage);
  const targetIndex = stageOrder.indexOf(targetStage);
  
  // Can only move forward one stage at a time (or backward any amount)
  if (targetIndex > currentIndex + 1) {
    return false;
  }
  
  // Special rules for certain stages
  if (targetStage === 'ready-for-dev' && !isReadyForDev(state)) {
    return false;
  }
  
  // Run validation rules
  for (const rule of state.validationRules) {
    if (!rule.validate(state)) {
      return false;
    }
  }
  
  return true;
}

/**
 * Checks if workflow is ready for development
 */
export function isReadyForDev(state: WorkflowState): boolean {
  const requiredTasks = getAllTasks(state).filter((task) => task.required);
  
  if (requiredTasks.length === 0) {
    return false;
  }
  
  return requiredTasks.every((task) => task.status === 'completed');
}

/**
 * Adds a validation rule
 */
export function addValidationRule(state: WorkflowState, rule: ValidationRule): WorkflowState {
  return {
    ...state,
    validationRules: [...state.validationRules, rule],
  };
}

/**
 * Removes a validation rule
 */
export function removeValidationRule(state: WorkflowState, ruleId: string): WorkflowState {
  return {
    ...state,
    validationRules: state.validationRules.filter((rule) => rule.id !== ruleId),
  };
}

/**
 * Validates current workflow state against all rules
 */
export function validateWorkflow(state: WorkflowState): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  
  for (const rule of state.validationRules) {
    if (!rule.validate(state)) {
      errors.push(rule.errorMessage);
    }
  }
  
  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Marks a notification as read
 */
export function markNotificationRead(state: WorkflowState, notificationId: string): WorkflowState {
  return {
    ...state,
    notifications: state.notifications.map((notif) =>
      notif.id === notificationId ? { ...notif, read: true } : notif
    ),
  };
}

/**
 * Gets unread notifications
 */
export function getUnreadNotifications(state: WorkflowState): WorkflowNotification[] {
  return state.notifications.filter((notif) => !notif.read);
}

/**
 * Clears all notifications
 */
export function clearNotifications(state: WorkflowState): WorkflowState {
  return {
    ...state,
    notifications: [],
  };
}

/**
 * Locks the workflow (makes it read-only)
 */
export function lockWorkflow(state: WorkflowState): WorkflowState {
  return {
    ...state,
    locked: true,
  };
}

/**
 * Unlocks the workflow
 */
export function unlockWorkflow(state: WorkflowState): WorkflowState {
  return {
    ...state,
    locked: false,
  };
}

/**
 * Gets workflow completion statistics
 */
export function getWorkflowStatistics(state: WorkflowState) {
  const allTasks = getAllTasks(state);
  const completedTasks = allTasks.filter((task) => task.status === 'completed');
  const requiredTasks = allTasks.filter((task) => task.required);
  const completedRequiredTasks = requiredTasks.filter((task) => task.status === 'completed');
  
  const totalTasks = allTasks.length;
  const completionPercentage = totalTasks > 0 ? (completedTasks.length / totalTasks) * 100 : 0;
  
  return {
    totalTasks,
    completedTasks: completedTasks.length,
    requiredTasks: requiredTasks.length,
    completedRequiredTasks: completedRequiredTasks.length,
    completionPercentage,
    pendingTasks: allTasks.filter((task) => task.status === 'pending').length,
    inProgressTasks: allTasks.filter((task) => task.status === 'in-progress').length,
    blockedTasks: allTasks.filter((task) => task.status === 'blocked').length,
    criticalTasks: allTasks.filter((task) => task.priority === 'critical').length,
  };
}

/**
 * Exports workflow as a spec bundle
 */
export function exportSpecBundle(state: WorkflowState, options?: {
  canvasReference?: string;
  notes?: string;
  exportedBy?: string;
}): SpecBundle {
  const statistics = getWorkflowStatistics(state);
  
  return {
    version: '1.0.0',
    metadata: {
      title: state.metadata.title,
      stage: state.stage,
      exportedAt: new Date().toISOString(),
      exportedBy: options?.exportedBy,
    },
    tasks: getAllTasks(state),
    statistics: {
      totalTasks: statistics.totalTasks,
      completedTasks: statistics.completedTasks,
      requiredTasks: statistics.requiredTasks,
      completedRequiredTasks: statistics.completedRequiredTasks,
      completionPercentage: statistics.completionPercentage,
    },
    canvasReference: options?.canvasReference,
    notes: options?.notes,
  };
}

/**
 * Exports spec bundle as JSON string
 */
export function exportSpecBundleJSON(state: WorkflowState, options?: Parameters<typeof exportSpecBundle>[1]): string {
  return JSON.stringify(exportSpecBundle(state, options), null, 2);
}

/**
 * Imports workflow from a spec bundle
 */
export function importSpecBundle(bundle: SpecBundle): WorkflowState {
  const state = createWorkflowState({
    title: bundle.metadata.title,
  });
  
  let newState = {
    ...state,
    stage: bundle.metadata.stage,
  };
  
  // Import tasks
  for (const taskData of bundle.tasks) {
    const task: ChecklistTask = {
      ...taskData,
      // Generate new ID to avoid conflicts
      id: `task-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    };
    
    newState = {
      ...newState,
      tasks: new Map(newState.tasks).set(task.id, task),
      taskOrder: [...newState.taskOrder, task.id],
    };
  }
  
  return newState;
}

/**
 * Searches tasks by title or description
 */
export function searchTasks(state: WorkflowState, query: string): ChecklistTask[] {
  const lowerQuery = query.toLowerCase();
  
  return getAllTasks(state).filter((task) => {
    const titleMatch = task.title.toLowerCase().includes(lowerQuery);
    const descMatch = task.description?.toLowerCase().includes(lowerQuery);
    return titleMatch || descMatch;
  });
}

/**
 * Filters tasks by status
 */
export function filterTasksByStatus(state: WorkflowState, status: TaskStatus): ChecklistTask[] {
  return getAllTasks(state).filter((task) => task.status === status);
}

/**
 * Filters tasks by priority
 */
export function filterTasksByPriority(state: WorkflowState, priority: TaskPriority): ChecklistTask[] {
  return getAllTasks(state).filter((task) => task.priority === priority);
}

/**
 * Gets tasks that are blocked by dependencies
 */
export function getBlockedTasks(state: WorkflowState): ChecklistTask[] {
  return getAllTasks(state).filter((task) => {
    if (!task.dependsOn || task.dependsOn.length === 0) {
      return false;
    }
    
    // Check if any dependencies are not completed
    return task.dependsOn.some((depId) => {
      const dep = state.tasks.get(depId);
      return !dep || dep.status !== 'completed';
    });
  });
}

/**
 * Gets tasks that can be started (no blocking dependencies)
 */
export function getReadyTasks(state: WorkflowState): ChecklistTask[] {
  return getAllTasks(state).filter((task) => {
    if (task.status !== 'pending') {
      return false;
    }
    
    if (!task.dependsOn || task.dependsOn.length === 0) {
      return true;
    }
    
    // Check if all dependencies are completed
    return task.dependsOn.every((depId) => {
      const dep = state.tasks.get(depId);
      return dep && dep.status === 'completed';
    });
  });
}

/**
 * Common checklist templates
 */
export const CHECKLIST_TEMPLATES: Record<string, ChecklistTemplate> = {
  'basic-design': {
    id: 'basic-design',
    name: 'Basic Design Checklist',
    description: 'Standard design workflow checklist',
    tasks: [
      {
        title: 'Define user stories',
        status: 'pending',
        priority: 'high',
        required: true,
      },
      {
        title: 'Create wireframes',
        status: 'pending',
        priority: 'high',
        required: true,
      },
      {
        title: 'Design high-fidelity mockups',
        status: 'pending',
        priority: 'medium',
        required: true,
      },
      {
        title: 'Document component specifications',
        status: 'pending',
        priority: 'medium',
        required: true,
      },
      {
        title: 'Review with stakeholders',
        status: 'pending',
        priority: 'high',
        required: true,
      },
    ],
  },
  'feature-spec': {
    id: 'feature-spec',
    name: 'Feature Specification',
    description: 'Checklist for feature specifications',
    tasks: [
      {
        title: 'Define feature requirements',
        status: 'pending',
        priority: 'critical',
        required: true,
      },
      {
        title: 'Identify edge cases',
        status: 'pending',
        priority: 'high',
        required: true,
      },
      {
        title: 'Define API contracts',
        status: 'pending',
        priority: 'high',
        required: true,
      },
      {
        title: 'Create data models',
        status: 'pending',
        priority: 'high',
        required: true,
      },
      {
        title: 'Define test scenarios',
        status: 'pending',
        priority: 'medium',
        required: true,
      },
      {
        title: 'Document acceptance criteria',
        status: 'pending',
        priority: 'critical',
        required: true,
      },
    ],
  },
};
