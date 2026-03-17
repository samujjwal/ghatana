/**
 * Project factory for generating test project data
 */

import { faker } from '../faker-shim';
import { createWorkspace } from './workspace.factory';

import type { Project, Workspace, User } from '@ghatana/yappc-types';

/**
 *
 */
export interface ProjectFactoryOptions {
  /** Override project ID */
  id?: string;
  /** Override project name */
  name?: string;
  /** Override project description */
  description?: string;
  /** Override project workspace */
  workspace?: Workspace;
  /** Override project owner */
  owner?: User;
  /** Override project status */
  status?: 'active' | 'archived' | 'completed';
  /** Override project creation date */
  createdAt?: Date;
  /** Override project update date */
  updatedAt?: Date;
}

/**
 * Create a test project with realistic data
 *
 * @param options - Override default project properties
 * @returns A project object for testing
 */
export function createProject(options: ProjectFactoryOptions = {}): Project {
  const workspace = options.workspace || createWorkspace();
  const name = options.name || faker.commerce.productName();

  return {
    id: options.id || faker.string.uuid(),
    workspaceId: workspace.id,
    name,
    description: options.description || faker.lorem.paragraph(),
    type: 'UI',
    targets: ['web'],
    status: (options.status as unknown) || 'active',
    createdAt: (options.createdAt || faker.date.past()).toISOString(),
    updatedAt: (options.updatedAt || faker.date.recent()).toISOString(),
  } as Project;
}

/**
 * Create multiple test projects
 *
 * @param count - Number of projects to create
 * @param baseOptions - Base options to apply to all projects
 * @returns Array of project objects for testing
 */
export function createProjects(
  count: number,
  baseOptions: ProjectFactoryOptions = {}
): Project[] {
  return Array.from({ length: count }, () =>
    createProject({
      ...baseOptions,
    })
  );
}

/**
 * Create projects for a specific workspace
 *
 * @param workspace - Workspace to create projects for
 * @param count - Number of projects to create
 * @param baseOptions - Base options to apply to all projects
 * @returns Array of project objects for testing
 */
export function createProjectsForWorkspace(
  workspace: Workspace,
  count: number,
  baseOptions: ProjectFactoryOptions = {}
): Project[] {
  return createProjects(count, {
    ...baseOptions,
    workspace,
  });
}

/**
 * Create an archived project
 *
 * @param options - Override default project properties
 * @returns An archived project object for testing
 */
export function createArchivedProject(
  options: ProjectFactoryOptions = {}
): Project {
  return createProject({
    status: 'archived',
    ...options,
  });
}

/**
 * Create a completed project
 *
 * @param options - Override default project properties
 * @returns A completed project object for testing
 */
export function createCompletedProject(
  options: ProjectFactoryOptions = {}
): Project {
  return createProject({
    status: 'completed',
    ...options,
  });
}
