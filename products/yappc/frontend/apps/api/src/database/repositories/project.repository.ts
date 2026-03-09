/**
 * Project repository for database operations on projects.
 *
 * <p><b>Purpose</b><br>
 * Provides data access operations for Project entities, including specialized
 * queries for project management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const projectRepo = new ProjectRepository(prisma);
 * const projects = await projectRepo.findByWorkspace('ws-123');
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Project data access
 * @doc.layer product
 * @doc.pattern Repository
 */

import { PrismaClient, Project } from '@prisma/client';
import { BaseRepository } from '../repository.base';

/**
 * Repository for Project entity operations.
 *
 * <p><b>Purpose</b><br>
 * Provides specialized queries for project management including filtering by
 * workspace, status, and type.
 *
 * @doc.type class
 * @doc.purpose Project repository
 * @doc.layer product
 * @doc.pattern Repository
 */
export class ProjectRepository extends BaseRepository<Project, 'project'> {
  /**
   * Creates a new ProjectRepository instance.
   *
   * @param prisma - Prisma client instance
   */
  constructor(prisma: PrismaClient) {
    super(prisma, 'project');
  }

  /**
   * Finds all projects in a workspace.
   *
   * <p><b>Purpose</b><br>
   * Retrieves all projects belonging to a specific workspace.
   *
   * @param workspaceId - The workspace ID
   * @param options - Query options
   * @returns Array of projects
   *
   * @doc.type method
   * @doc.purpose Find projects by workspace
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findByWorkspace(
    workspaceId: string,
    options?: { skip?: number; take?: number }
  ): Promise<Project[]> {
    return this.findMany({ workspaceId }, options);
  }

  /**
   * Finds active projects in a workspace.
   *
   * <p><b>Purpose</b><br>
   * Retrieves only active projects in a workspace.
   *
   * @param workspaceId - The workspace ID
   * @returns Array of active projects
   *
   * @doc.type method
   * @doc.purpose Find active projects
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findActiveByWorkspace(workspaceId: string): Promise<Project[]> {
    return this.findMany({
      workspaceId,
      status: 'ACTIVE',
    });
  }

  /**
   * Finds projects by type.
   *
   * <p><b>Purpose</b><br>
   * Retrieves projects of a specific type.
   *
   * @param workspaceId - The workspace ID
   * @param type - The project type
   * @returns Array of projects
   *
   * @doc.type method
   * @doc.purpose Find projects by type
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findByType(workspaceId: string, type: string): Promise<Project[]> {
    return this.findMany({
      workspaceId,
      type,
    });
  }

  /**
   * Counts projects in a workspace.
   *
   * <p><b>Purpose</b><br>
   * Returns the count of projects in a workspace.
   *
   * @param workspaceId - The workspace ID
   * @returns Count of projects
   *
   * @doc.type method
   * @doc.purpose Count projects
   * @doc.layer product
   * @doc.pattern Repository
   */
  async countByWorkspace(workspaceId: string): Promise<number> {
    return this.count({ workspaceId });
  }

  /**
   * Archives a project.
   *
   * <p><b>Purpose</b><br>
   * Changes a project's status to ARCHIVED.
   *
   * @param projectId - The project ID
   * @returns The archived project
   *
   * @doc.type method
   * @doc.purpose Archive project
   * @doc.layer product
   * @doc.pattern Repository
   */
  async archive(projectId: string): Promise<Project> {
    return this.update({ id: projectId }, { status: 'ARCHIVED' });
  }
}
