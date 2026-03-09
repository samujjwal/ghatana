/**
 * Workspace factory for generating test workspace data
 */

import { faker } from '../faker-shim';
import { createUser } from './user.factory';

import type { Workspace, User } from '@ghatana/yappc-types';

/**
 *
 */
export interface WorkspaceFactoryOptions {
  /** Override workspace ID */
  id?: string;
  /** Override workspace name */
  name?: string;
  /** Override workspace description */
  description?: string;
  /** Override workspace owner */
  owner?: User;
  /** Override workspace members */
  members?: User[];
  /** Override workspace creation date */
  createdAt?: Date;
  /** Override workspace update date */
  updatedAt?: Date;
}

/**
 * Create a test workspace with realistic data
 *
 * @param options - Override default workspace properties
 * @returns A workspace object for testing
 */
export function createWorkspace(
  options: WorkspaceFactoryOptions = {}
): Workspace {
  const owner = options.owner || createUser();
  const name = options.name || faker.company.name();

  return {
    id: options.id || faker.string.uuid(),
    name,
    description: options.description || faker.company.catchPhrase(),
    ownerId: owner.id,
    createdAt: (options.createdAt || faker.date.past()).toISOString(),
    updatedAt: (options.updatedAt || faker.date.recent()).toISOString(),
  };
}

/**
 * Create multiple test workspaces
 *
 * @param count - Number of workspaces to create
 * @param baseOptions - Base options to apply to all workspaces
 * @returns Array of workspace objects for testing
 */
export function createWorkspaces(
  count: number,
  baseOptions: WorkspaceFactoryOptions = {}
): Workspace[] {
  return Array.from({ length: count }, (_, index) =>
    createWorkspace({
      ...baseOptions,
      // Allow overriding specific workspaces in the array
      ...((baseOptions as unknown)[`workspace${index}`] || {}),
    })
  );
}

/**
 * Create a workspace with multiple members
 *
 * @param memberCount - Number of members to include
 * @param options - Override default workspace properties
 * @returns A workspace object with multiple members for testing
 */
export function createWorkspaceWithMembers(
  memberCount: number,
  options: WorkspaceFactoryOptions = {}
): Workspace {
  const owner = options.owner || createUser();
  const additionalMembers = Array.from({ length: memberCount - 1 }, () =>
    createUser()
  );
  const members = [owner, ...additionalMembers];

  return createWorkspace({
    ...options,
    owner,
    members,
  });
}
