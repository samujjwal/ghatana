/**
 * Page repository for database operations on pages.
 *
 * <p><b>Purpose</b><br>
 * Provides data access operations for Page entities, including specialized
 * queries for page management and routing.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const pageRepo = new PageRepository(prisma);
 * const pages = await pageRepo.findByProject('proj-123');
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Page data access
 * @doc.layer product
 * @doc.pattern Repository
 */

import { PrismaClient, Page } from '@prisma/client';
import { BaseRepository } from '../repository.base';

/**
 * Repository for Page entity operations.
 *
 * <p><b>Purpose</b><br>
 * Provides specialized queries for page management including filtering by
 * project and path-based lookups.
 *
 * @doc.type class
 * @doc.purpose Page repository
 * @doc.layer product
 * @doc.pattern Repository
 */
export class PageRepository extends BaseRepository<Page, 'page'> {
  /**
   * Creates a new PageRepository instance.
   *
   * @param prisma - Prisma client instance
   */
  constructor(prisma: PrismaClient) {
    super(prisma, 'page');
  }

  /**
   * Finds all pages in a project.
   *
   * <p><b>Purpose</b><br>
   * Retrieves all pages belonging to a specific project.
   *
   * @param projectId - The project ID
   * @param options - Query options
   * @returns Array of pages
   *
   * @doc.type method
   * @doc.purpose Find pages by project
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findByProject(
    projectId: string,
    options?: { skip?: number; take?: number }
  ): Promise<Page[]> {
    return this.findMany({ projectId }, options);
  }

  /**
   * Finds a page by project and path.
   *
   * <p><b>Purpose</b><br>
   * Retrieves a specific page by its project and path.
   *
   * @param projectId - The project ID
   * @param path - The page path
   * @returns The page or null if not found
   *
   * @doc.type method
   * @doc.purpose Find page by path
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findByPath(projectId: string, path: string): Promise<Page | null> {
    return this.findOne({ projectId_path: { projectId, path } });
  }

  /**
   * Finds pages created by a user.
   *
   * <p><b>Purpose</b><br>
   * Retrieves pages created by a specific user.
   *
   * @param createdById - The user ID
   * @returns Array of pages
   *
   * @doc.type method
   * @doc.purpose Find pages by creator
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findByCreator(createdById: string): Promise<Page[]> {
    return this.findMany({ createdById });
  }

  /**
   * Updates page content.
   *
   * <p><b>Purpose</b><br>
   * Updates the content (components, layout) of a page.
   *
   * @param pageId - The page ID
   * @param content - The new content
   * @returns The updated page
   *
   * @doc.type method
   * @doc.purpose Update page content
   * @doc.layer product
   * @doc.pattern Repository
   */
  async updateContent(pageId: string, content: Record<string, unknown>): Promise<Page> {
    return this.update({ id: pageId }, { content });
  }

  /**
   * Counts pages in a project.
   *
   * <p><b>Purpose</b><br>
   * Returns the count of pages in a project.
   *
   * @param projectId - The project ID
   * @returns Count of pages
   *
   * @doc.type method
   * @doc.purpose Count pages
   * @doc.layer product
   * @doc.pattern Repository
   */
  async countByProject(projectId: string): Promise<number> {
    return this.count({ projectId });
  }
}
