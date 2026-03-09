/**
 * Canvas document repository for database operations on canvas documents.
 *
 * <p><b>Purpose</b><br>
 * Provides data access operations for CanvasDocument entities, including specialized
 * queries for canvas management and content updates.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const canvasRepo = new CanvasRepository(prisma);
 * const docs = await canvasRepo.findByProject('proj-123');
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Canvas document data access
 * @doc.layer product
 * @doc.pattern Repository
 */

import { PrismaClient, CanvasDocument } from '@prisma/client';
import { BaseRepository } from '../repository.base';

/**
 * Repository for CanvasDocument entity operations.
 *
 * <p><b>Purpose</b><br>
 * Provides specialized queries for canvas document management including filtering by
 * project and content updates.
 *
 * @doc.type class
 * @doc.purpose Canvas document repository
 * @doc.layer product
 * @doc.pattern Repository
 */
export class CanvasRepository extends BaseRepository<CanvasDocument, 'canvasDocument'> {
  /**
   * Creates a new CanvasRepository instance.
   *
   * @param prisma - Prisma client instance
   */
  constructor(prisma: PrismaClient) {
    super(prisma, 'canvasDocument');
  }

  /**
   * Finds all canvas documents in a project.
   *
   * <p><b>Purpose</b><br>
   * Retrieves all canvas documents belonging to a specific project.
   *
   * @param projectId - The project ID
   * @param options - Query options
   * @returns Array of canvas documents
   *
   * @doc.type method
   * @doc.purpose Find documents by project
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findByProject(
    projectId: string,
    options?: { skip?: number; take?: number }
  ): Promise<CanvasDocument[]> {
    return this.findMany({ projectId }, options);
  }

  /**
   * Finds canvas documents created by a user.
   *
   * <p><b>Purpose</b><br>
   * Retrieves canvas documents created by a specific user.
   *
   * @param createdById - The user ID
   * @returns Array of canvas documents
   *
   * @doc.type method
   * @doc.purpose Find documents by creator
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findByCreator(createdById: string): Promise<CanvasDocument[]> {
    return this.findMany({ createdById });
  }

  /**
   * Updates canvas document content.
   *
   * <p><b>Purpose</b><br>
   * Updates the content (nodes, edges, viewport) of a canvas document.
   *
   * @param documentId - The document ID
   * @param content - The new content
   * @returns The updated document
   *
   * @doc.type method
   * @doc.purpose Update document content
   * @doc.layer product
   * @doc.pattern Repository
   */
  async updateContent(
    documentId: string,
    content: Record<string, unknown>
  ): Promise<CanvasDocument> {
    return this.update({ id: documentId }, { content });
  }

  /**
   * Counts canvas documents in a project.
   *
   * <p><b>Purpose</b><br>
   * Returns the count of canvas documents in a project.
   *
   * @param projectId - The project ID
   * @returns Count of documents
   *
   * @doc.type method
   * @doc.purpose Count documents
   * @doc.layer product
   * @doc.pattern Repository
   */
  async countByProject(projectId: string): Promise<number> {
    return this.count({ projectId });
  }
}
