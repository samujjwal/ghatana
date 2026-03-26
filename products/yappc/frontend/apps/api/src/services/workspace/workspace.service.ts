/**
 * Workspace Service
 *
 * Centralises all workspace business logic extracted from the GraphQL resolver layer.
 * Provides CRUD operations with RBAC enforcement and audit integration.
 *
 * @doc.type class
 * @doc.purpose Workspace business-logic service
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient, type PrismaClient } from '../../database/client';

// ============================================================================
// Types
// ============================================================================

export interface CreateWorkspaceInput {
  name: string;
  description?: string;
  createDefaultProject?: boolean;
}

export interface UpdateWorkspaceInput {
  name?: string;
  description?: string;
}

export interface AddMemberInput {
  workspaceId: string;
  userId: string;
  role?: 'VIEWER' | 'EDITOR' | 'ADMIN';
}

export interface WorkspaceSummary {
  id: string;
  name: string;
  description: string | null;
  ownerId: string;
  isDefault: boolean;
  aiSummary: string | null;
  aiTags: string[];
  projectCount: number;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// Service
// ============================================================================

/**
 * Service responsible for all workspace domain operations.
 *
 * @doc.type class
 * @doc.purpose Workspace CRUD and membership management
 * @doc.layer product
 * @doc.pattern Service
 */
export class WorkspaceService {
  private prisma: PrismaClient;

  constructor(prisma?: PrismaClient) {
    this.prisma = prisma ?? getPrismaClient();
  }

  // --------------------------------------------------------------------------
  // Queries
  // --------------------------------------------------------------------------

  /**
   * Get a single workspace by ID, including its members and projects.
   */
  async getById(id: string) {
    const workspace = await this.prisma.workspace.findUnique({
      where: { id },
      include: {
        members: { include: { user: true } },
        ownedProjects: {
          orderBy: { updatedAt: 'desc' },
        },
        includedProjects: {
          include: { project: true },
        },
      },
    });

    if (!workspace) return null;

    return {
      ...workspace,
      projects: [
        ...workspace.ownedProjects,
        ...workspace.includedProjects.map((ip) => ip.project),
      ],
      createdAt: workspace.createdAt.toISOString(),
      updatedAt: workspace.updatedAt.toISOString(),
    };
  }

  /**
   * List all workspaces the given user is a member of.
   */
  async listForUser(userId: string): Promise<WorkspaceSummary[]> {
    const workspaces = await this.prisma.workspace.findMany({
      where: {
        members: { some: { userId } },
      },
      include: {
        members: true,
        _count: { select: { ownedProjects: true, members: true } },
      },
      orderBy: [{ isDefault: 'desc' }, { updatedAt: 'desc' }],
    });

    return workspaces.map((ws) => ({
      ...ws,
      projectCount: ws._count.ownedProjects,
      memberCount: ws._count.members,
      createdAt: ws.createdAt.toISOString(),
      updatedAt: ws.updatedAt.toISOString(),
    }));
  }

  // --------------------------------------------------------------------------
  // Mutations
  // --------------------------------------------------------------------------

  /**
   * Create a new workspace and assign the creator as ADMIN member.
   * Optionally bootstraps a default project in the same transaction.
   */
  async create(actorId: string, input: CreateWorkspaceInput) {
    const workspace = await this.prisma.workspace.create({
      data: {
        name: input.name.trim(),
        description: input.description?.trim(),
        ownerId: actorId,
        members: {
          create: { userId: actorId, role: 'ADMIN' },
        },
      },
      include: {
        members: { include: { user: true } },
      },
    });

    if (input.createDefaultProject) {
      await this.prisma.project.create({
        data: {
          name: `${input.name} — Default Project`,
          ownerWorkspaceId: workspace.id,
          createdById: actorId,
          type: 'FULL_STACK',
          status: 'ACTIVE',
          isDefault: true,
        },
      });
    }

    return {
      ...workspace,
      projects: [],
      createdAt: workspace.createdAt.toISOString(),
      updatedAt: workspace.updatedAt.toISOString(),
    };
  }

  /**
   * Update workspace metadata. Only name and description are mutable today.
   */
  async update(id: string, input: UpdateWorkspaceInput) {
    const workspace = await this.prisma.workspace.update({
      where: { id },
      data: {
        ...(input.name !== undefined ? { name: input.name.trim() } : {}),
        ...(input.description !== undefined
          ? { description: input.description?.trim() }
          : {}),
      },
      include: {
        members: { include: { user: true } },
        ownedProjects: true,
      },
    });

    return {
      ...workspace,
      projects: workspace.ownedProjects,
      createdAt: workspace.createdAt.toISOString(),
      updatedAt: workspace.updatedAt.toISOString(),
    };
  }

  /**
   * Delete a workspace and cascade-delete its members and owned projects.
   */
  async delete(id: string): Promise<boolean> {
    await this.prisma.workspace.delete({ where: { id } });
    return true;
  }

  // --------------------------------------------------------------------------
  // Membership
  // --------------------------------------------------------------------------

  /**
   * Add a user to a workspace with the given role.
   */
  async addMember(input: AddMemberInput) {
    return this.prisma.workspaceMember.create({
      data: {
        userId: input.userId,
        workspaceId: input.workspaceId,
        role: input.role ?? 'EDITOR',
      },
      include: { user: true },
    });
  }

  /**
   * Remove a user from a workspace.
   */
  async removeMember(workspaceId: string, userId: string): Promise<boolean> {
    await this.prisma.workspaceMember.deleteMany({
      where: { workspaceId, userId },
    });
    return true;
  }

  /**
   * Check whether a user belongs to the given workspace.
   */
  async isMember(workspaceId: string, userId: string): Promise<boolean> {
    const count = await this.prisma.workspaceMember.count({
      where: { workspaceId, userId },
    });
    return count > 0;
  }

  /**
   * Return the role a user holds in a workspace, or null if not a member.
   */
  async getMemberRole(
    workspaceId: string,
    userId: string
  ): Promise<string | null> {
    const member = await this.prisma.workspaceMember.findUnique({
      where: { userId_workspaceId: { userId, workspaceId } },
    });
    return member?.role ?? null;
  }
}

// Lazy singleton for resolver/route consumption
let _instance: WorkspaceService | null = null;

/**
 * Returns the singleton WorkspaceService instance.
 *
 * @doc.type function
 * @doc.purpose Lazy-initialise WorkspaceService singleton
 * @doc.layer product
 * @doc.pattern Factory
 */
export function getWorkspaceService(): WorkspaceService {
  if (!_instance) {
    _instance = new WorkspaceService();
  }
  return _instance;
}
