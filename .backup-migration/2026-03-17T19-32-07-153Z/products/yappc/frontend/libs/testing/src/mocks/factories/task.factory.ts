/**
 * Task factory for generating test task data
 */

import { faker } from '../faker-shim';
import { createProject } from './project.factory';

import type { Task, Project, User } from '@ghatana/yappc-types';

/**
 *
 */
export interface TaskFactoryOptions {
  /** Override task ID */
  id?: string;
  /** Override task title */
  title?: string;
  /** Override task description */
  description?: string;
  /** Override task project */
  project?: Project;
  /** Override task assignee */
  assignee?: User | null;
  /** Override task creator */
  creator?: User;
  /** Override task status */
  status?: 'todo' | 'in_progress' | 'review' | 'done';
  /** Override task priority */
  priority?: 'low' | 'medium' | 'high' | 'urgent';
  /** Override task due date */
  dueDate?: Date | null;
  /** Override task creation date */
  createdAt?: Date;
  /** Override task update date */
  updatedAt?: Date;
}

/**
 * Create a test task with realistic data
 *
 * @param options - Override default task properties
 * @returns A task object for testing
 */
export function createTask(options: TaskFactoryOptions = {}): Task {
  const project = options.project || createProject();
  const assigneeId = options.assignee ? options.assignee.id : undefined;
  const title = options.title || faker.lorem.sentence().replace(/\.$/, '');

  return {
    id: options.id || faker.string.uuid(),
    projectId: project.id,
    title,
    description: options.description || faker.lorem.paragraph(),
    status:
      (options.status === 'review' ? 'in_progress' : (options.status as unknown)) ||
      'todo',
    assigneeId: assigneeId || undefined,
    dueDate: options.dueDate
      ? options.dueDate instanceof Date
        ? options.dueDate.toISOString()
        : String(options.dueDate)
      : undefined,
    createdAt: (options.createdAt || faker.date.past()).toISOString(),
    updatedAt: (options.updatedAt || faker.date.recent()).toISOString(),
  } as Task;
}

/**
 * Create multiple test tasks
 *
 * @param count - Number of tasks to create
 * @param baseOptions - Base options to apply to all tasks
 * @returns Array of task objects for testing
 */
export function createTasks(
  count: number,
  baseOptions: TaskFactoryOptions = {}
): Task[] {
  return Array.from({ length: count }, () => createTask({ ...baseOptions }));
}

/**
 * Create tasks for a specific project
 *
 * @param project - Project to create tasks for
 * @param count - Number of tasks to create
 * @param baseOptions - Base options to apply to all tasks
 * @returns Array of task objects for testing
 */
export function createTasksForProject(
  project: Project,
  count: number,
  baseOptions: TaskFactoryOptions = {}
): Task[] {
  return createTasks(count, { ...baseOptions, project });
}

/**
 * Create a task with a specific status
 *
 * @param status - Task status
 * @param options - Override default task properties
 * @returns A task object with the specified status for testing
 */
export function createTaskWithStatus(
  status: 'todo' | 'in_progress' | 'review' | 'done',
  options: TaskFactoryOptions = {}
): Task {
  return createTask({
    status,
    ...options,
  });
}

/**
 * Create a task with a specific priority
 *
 * @param priority - Task priority
 * @param options - Override default task properties
 * @returns A task object with the specified priority for testing
 */
export function createTaskWithPriority(
  priority: 'low' | 'medium' | 'high' | 'urgent',
  options: TaskFactoryOptions = {}
): Task {
  return createTask({
    priority,
    ...options,
  });
}
