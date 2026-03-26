/**
 * Canvas Service
 *
 * Centralises all canvas document and page business logic extracted from the
 * GraphQL resolver layer. Provides CRUD operations for both CanvasDocument
 * and Page entities, with JSON content serialisation handled transparently.
 *
 * @doc.type class
 * @doc.purpose Canvas document and page business-logic service
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient, type PrismaClient } from '../../database/client';

// ============================================================================
// Types
// ============================================================================

export interface CreateDocumentInput {
  projectId: string;
  name: string;
  description?: string;
  content?: unknown;
}

export interface UpdateDocumentInput {
  name?: string;
  description?: string;
  content?: unknown;
}

export interface CreatePageInput {
  projectId: string;
  name: string;
  path: string;
  layout?: string;
  content?: unknown;
}

export interface UpdatePageInput {
  name?: string;
  path?: string;
  layout?: string;
  content?: unknown;
}

// ============================================================================
// Helpers
// ============================================================================

function toISOString(date: Date): string {
  return date.toISOString();
}

function parseContent(raw: unknown): unknown {
  if (!raw) return {};
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw;
  } catch {
    return {};
  }
}

// ============================================================================
// Service
// ============================================================================

/**
 * Service responsible for canvas document and page domain operations.
 *
 * @doc.type class
 * @doc.purpose Canvas CRUD with JSON content serialisation
 * @doc.layer product
 * @doc.pattern Service
 */
export class CanvasService {
  private prisma: PrismaClient;

  constructor(prisma?: PrismaClient) {
    this.prisma = prisma ?? getPrismaClient();
  }

  // --------------------------------------------------------------------------
  // Canvas Documents — Queries
  // --------------------------------------------------------------------------

  /**
   * Get a single canvas document by ID, including its project and creator.
   */
  async getDocument(id: string) {
    const doc = await this.prisma.canvasDocument.findUnique({
      where: { id },
      include: {
        project: true,
        createdBy: true,
      },
    });

    if (!doc) return null;

    return {
      ...doc,
      content: parseContent(doc.content),
      createdAt: toISOString(doc.createdAt),
      updatedAt: toISOString(doc.updatedAt),
    };
  }

  /**
   * List all canvas documents for a project, ordered by last update.
   */
  async listDocuments(projectId: string) {
    const docs = await this.prisma.canvasDocument.findMany({
      where: { projectId },
      orderBy: { updatedAt: 'desc' },
    });

    return docs.map((d) => ({
      ...d,
      content: parseContent(d.content),
      createdAt: toISOString(d.createdAt),
      updatedAt: toISOString(d.updatedAt),
    }));
  }

  // --------------------------------------------------------------------------
  // Canvas Documents — Mutations
  // --------------------------------------------------------------------------

  /**
   * Create a new canvas document owned by the given user acting on a project.
   * Content is stored as a JSON string; the raw value is returned on creation.
   */
  async createDocument(actorId: string, input: CreateDocumentInput) {
    const doc = await this.prisma.canvasDocument.create({
      data: {
        projectId: input.projectId,
        createdById: actorId,
        name: input.name,
        description: input.description,
        content: input.content ? JSON.stringify(input.content) : '{}',
      },
    });

    return {
      ...doc,
      content: input.content ?? {},
      createdAt: toISOString(doc.createdAt),
      updatedAt: toISOString(doc.updatedAt),
    };
  }

  /**
   * Update mutable fields of a canvas document.
   * Content is only updated when explicitly provided.
   */
  async updateDocument(id: string, input: UpdateDocumentInput) {
    const doc = await this.prisma.canvasDocument.update({
      where: { id },
      data: {
        ...(input.name !== undefined ? { name: input.name } : {}),
        ...(input.description !== undefined
          ? { description: input.description }
          : {}),
        ...(input.content !== undefined
          ? { content: JSON.stringify(input.content) }
          : {}),
      },
    });

    return {
      ...doc,
      content: parseContent(doc.content),
      createdAt: toISOString(doc.createdAt),
      updatedAt: toISOString(doc.updatedAt),
    };
  }

  /**
   * Delete a canvas document by ID.
   */
  async deleteDocument(id: string): Promise<boolean> {
    await this.prisma.canvasDocument.delete({ where: { id } });
    return true;
  }

  // --------------------------------------------------------------------------
  // Pages — Queries
  // --------------------------------------------------------------------------

  /**
   * Get a single page by ID, including its project and creator.
   */
  async getPage(id: string) {
    const page = await this.prisma.page.findUnique({
      where: { id },
      include: {
        project: true,
        createdBy: true,
      },
    });

    if (!page) return null;

    return {
      ...page,
      content: parseContent(page.content),
      createdAt: toISOString(page.createdAt),
      updatedAt: toISOString(page.updatedAt),
    };
  }

  /**
   * List all pages for a project, ordered by path for deterministic rendering.
   */
  async listPages(projectId: string) {
    const pages = await this.prisma.page.findMany({
      where: { projectId },
      orderBy: { path: 'asc' },
    });

    return pages.map((p) => ({
      ...p,
      content: parseContent(p.content),
      createdAt: toISOString(p.createdAt),
      updatedAt: toISOString(p.updatedAt),
    }));
  }

  /**
   * Find a page by its project and URL path. Returns null if not found.
   */
  async pageByPath(projectId: string, path: string) {
    const page = await this.prisma.page.findFirst({
      where: { projectId, path },
      include: {
        project: true,
        createdBy: true,
      },
    });

    if (!page) return null;

    return {
      ...page,
      content: parseContent(page.content),
      createdAt: toISOString(page.createdAt),
      updatedAt: toISOString(page.updatedAt),
    };
  }

  // --------------------------------------------------------------------------
  // Pages — Mutations
  // --------------------------------------------------------------------------

  /**
   * Create a new page in the given project with the specified path.
   */
  async createPage(actorId: string, input: CreatePageInput) {
    const page = await this.prisma.page.create({
      data: {
        projectId: input.projectId,
        createdById: actorId,
        name: input.name,
        path: input.path,
        layout: input.layout ?? 'default',
        content: input.content ? JSON.stringify(input.content) : '{}',
      },
    });

    return {
      ...page,
      content: input.content ?? {},
      createdAt: toISOString(page.createdAt),
      updatedAt: toISOString(page.updatedAt),
    };
  }

  /**
   * Update mutable fields of a page.
   * Content is only updated when explicitly provided.
   */
  async updatePage(id: string, input: UpdatePageInput) {
    const page = await this.prisma.page.update({
      where: { id },
      data: {
        ...(input.name !== undefined ? { name: input.name } : {}),
        ...(input.path !== undefined ? { path: input.path } : {}),
        ...(input.layout !== undefined ? { layout: input.layout } : {}),
        ...(input.content !== undefined
          ? { content: JSON.stringify(input.content) }
          : {}),
      },
    });

    return {
      ...page,
      content: parseContent(page.content),
      createdAt: toISOString(page.createdAt),
      updatedAt: toISOString(page.updatedAt),
    };
  }

  /**
   * Delete a page by ID.
   */
  async deletePage(id: string): Promise<boolean> {
    await this.prisma.page.delete({ where: { id } });
    return true;
  }
}

// ============================================================================
// Singleton factory
// ============================================================================

let _canvasService: CanvasService | undefined;

/**
 * Returns the process-scoped CanvasService singleton.
 *
 * @doc.type function
 * @doc.purpose Singleton factory for CanvasService
 * @doc.layer product
 * @doc.pattern Factory
 */
export function getCanvasService(): CanvasService {
  _canvasService ??= new CanvasService();
  return _canvasService;
}
