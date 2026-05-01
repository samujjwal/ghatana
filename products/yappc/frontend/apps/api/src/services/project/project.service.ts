/**
 * Project Service
 *
 * Centralises all project business logic extracted from the GraphQL resolver layer.
 * Provides CRUD operations, AI field management, and lifecycle phase transitions.
 *
 * @doc.type class
 * @doc.purpose Project business-logic service
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient, type PrismaClient } from '../../database/client';
import { type ProjectType, type ProjectStatus, type LifecyclePhase } from '@prisma/client';

// ============================================================================
// Types
// ============================================================================

export interface CreateProjectInput {
  workspaceId: string;
  name: string;
  description?: string;
  type?: ProjectType;
}

export interface UpdateProjectInput {
  name?: string;
  description?: string;
  type?: ProjectType;
  status?: ProjectStatus;
  lifecyclePhase?: LifecyclePhase;
  aiSummary?: string;
  aiNextActions?: string[];
  aiHealthScore?: number;
}

export interface ProjectSummary {
  id: string;
  workspaceId: string;
  name: string;
  description: string | null;
  type: string;
  status: string;
  lifecyclePhase: string;
  isDefault: boolean;
  aiSummary: string | null;
  aiNextActions: string[];
  aiHealthScore: number | null;
  documentCount: number;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// Service
// ============================================================================

/**
 * Service responsible for all project domain operations.
 *
 * @doc.type class
 * @doc.purpose Project CRUD, lifecycle management, and AI field updates
 * @doc.layer product
 * @doc.pattern Service
 */
export class ProjectService {
  private prisma: PrismaClient;

  constructor(prisma?: PrismaClient) {
    this.prisma = prisma ?? getPrismaClient();
  }

  // --------------------------------------------------------------------------
  // Queries
  // --------------------------------------------------------------------------

  /**
   * Get a single project by ID with its documents and pages.
   */
  async getById(id: string) {
    const project = await this.prisma.project.findUnique({
      where: { id },
      include: {
        ownerWorkspace: true,
        createdBy: true,
        documents: { orderBy: { updatedAt: 'desc' } },
        pages: { orderBy: { path: 'asc' } },
      },
    });

    if (!project) return null;

    return {
      ...project,
      workspaceId: project.ownerWorkspaceId,
      createdAt: project.createdAt.toISOString(),
      updatedAt: project.updatedAt.toISOString(),
    };
  }

  /**
   * List all projects owned by a workspace.
   */
  async listByWorkspace(workspaceId: string): Promise<ProjectSummary[]> {
    const projects = await this.prisma.project.findMany({
      where: { ownerWorkspaceId: workspaceId },
      include: {
        _count: { select: { documents: true } },
      },
      orderBy: [{ isDefault: 'desc' }, { updatedAt: 'desc' }],
    });

    return projects.map((p) => ({
      ...p,
      workspaceId: p.ownerWorkspaceId,
      documentCount: p._count.documents,
      createdAt: p.createdAt.toISOString(),
      updatedAt: p.updatedAt.toISOString(),
    }));
  }

  // --------------------------------------------------------------------------
  // Mutations
  // --------------------------------------------------------------------------

  /**
   * Create a new project within a workspace.
   */
  async create(actorId: string, input: CreateProjectInput) {
    const project = await this.prisma.project.create({
      data: {
        name: input.name.trim(),
        description: input.description?.trim(),
        ownerWorkspaceId: input.workspaceId,
        createdById: actorId,
        type: input.type ?? ('FULL_STACK' as ProjectType),
        status: 'ACTIVE' as ProjectStatus,
      },
      include: {
        ownerWorkspace: true,
        createdBy: true,
      },
    });

    return {
      ...project,
      workspaceId: project.ownerWorkspaceId,
      documents: [],
      pages: [],
      createdAt: project.createdAt.toISOString(),
      updatedAt: project.updatedAt.toISOString(),
    };
  }

  /**
   * Update project fields.  Only defined (non-undefined) properties are applied.
   */
  async update(id: string, input: UpdateProjectInput) {
    const project = await this.prisma.project.update({
      where: { id },
      data: {
        ...(input.name !== undefined ? { name: input.name.trim() } : {}),
        ...(input.description !== undefined
          ? { description: input.description?.trim() }
          : {}),
        ...(input.type !== undefined ? { type: input.type } : {}),
        ...(input.status !== undefined ? { status: input.status } : {}),
        ...(input.lifecyclePhase !== undefined ? { lifecyclePhase: input.lifecyclePhase } : {}),
        ...(input.aiSummary !== undefined
          ? { aiSummary: input.aiSummary }
          : {}),
        ...(input.aiNextActions !== undefined
          ? { aiNextActions: input.aiNextActions }
          : {}),
        ...(input.aiHealthScore !== undefined
          ? { aiHealthScore: input.aiHealthScore }
          : {}),
      },
      include: {
        ownerWorkspace: true,
        createdBy: true,
        documents: true,
        pages: true,
      },
    });

    return {
      ...project,
      workspaceId: project.ownerWorkspaceId,
      createdAt: project.createdAt.toISOString(),
      updatedAt: project.updatedAt.toISOString(),
    };
  }

  /**
   * Delete a project and all its associated content via cascade.
   */
  async delete(id: string): Promise<boolean> {
    await this.prisma.project.delete({ where: { id } });
    return true;
  }

  // --------------------------------------------------------------------------
  // AI field helpers
  // --------------------------------------------------------------------------

  /**
   * Patch AI-generated fields on a project without touching user-editable fields.
   */
  async updateAIFields(
    id: string,
    fields: {
      aiSummary?: string;
      aiNextActions?: string[];
      aiHealthScore?: number;
    }
  ) {
    return this.prisma.project.update({
      where: { id },
      data: fields,
    });
  }
}

// Lazy singleton for resolver/route consumption
let _instance: ProjectService | null = null;

/**
 * Returns the singleton ProjectService instance.
 *
 * @doc.type function
 * @doc.purpose Lazy-initialise ProjectService singleton
 * @doc.layer product
 * @doc.pattern Factory
 */
export function getProjectService(): ProjectService {
  if (!_instance) {
    _instance = new ProjectService();
  }
  return _instance;
}
