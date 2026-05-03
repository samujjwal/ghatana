/**
 * Workspace API Routes
 *
 * Dead simple REST API for workspace management with rule-based assistance.
 * Workspaces are containers - project ownership determines permissions.
 *
 * @doc.type router
 * @doc.purpose Workspace CRUD operations
 * @doc.layer product
 * @doc.pattern REST API
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import prisma from '../db';
import { requirePermission } from '../middleware/rbac.middleware';
import {
  requireWorkspaceMember,
  requireWorkspaceOwner,
} from '../middleware/resource-auth.middleware';

// ============================================================================
// Types
// ============================================================================

interface CreateWorkspaceBody {
  name: string;
  description?: string;
  createDefaultProject?: boolean;
  personaSelections?: string[];
  defaultProject?: {
    name: string;
    description?: string;
    type: 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';
  };
}

interface UpdateWorkspaceBody {
  name?: string;
  description?: string;
}

interface WorkspaceParams {
  workspaceId: string;
}

// ============================================================================
// Rule-based Assistance Helpers
// ============================================================================

/**
 * Generate a rule-based summary for workspace based on its projects.
 */
async function generateWorkspaceSummary(workspaceId: string): Promise<string> {
  const projects = await prisma.project.findMany({
    where: { ownerWorkspaceId: workspaceId },
    select: { name: true, type: true, status: true },
  });

  if (projects.length === 0) {
    return 'Empty workspace ready for new projects';
  }

  const types = [...new Set(projects.map((p: { type: string }) => p.type))];
  const activeCount = projects.filter(
    (p: { status: string }) => p.status === 'ACTIVE'
  ).length;

  return `${projects.length} projects (${activeCount} active) focusing on ${types.join(', ').toLowerCase()}`;
}

/**
 * Generate rule-based tags for workspace.
 */
async function generateWorkspaceTags(workspaceId: string): Promise<string[]> {
  const projects = await prisma.project.findMany({
    where: { ownerWorkspaceId: workspaceId },
    select: { type: true, name: true },
  });

  const tags: string[] = [];

  // Add type-based tags
  const types = [...new Set(projects.map((p: { type: string }) => p.type))];
  types.forEach((type) => {
    switch (type) {
      case 'UI':
        tags.push('frontend');
        break;
      case 'BACKEND':
        tags.push('api');
        break;
      case 'MOBILE':
        tags.push('mobile');
        break;
      case 'FULL_STACK':
        tags.push('fullstack');
        break;
    }
  });

  // Add activity-based tags
  if (projects.length > 5) tags.push('large');
  if (projects.length === 1) tags.push('focused');

  return tags.slice(0, 5);
}

/**
 * Suggest workspace name based on deterministic naming rules.
 */
function suggestWorkspaceName(existingNames: string[]): string {
  const suggestions = [
    'Product Development',
    'Client Projects',
    'Personal',
    'Experiments',
    'Team Workspace',
    'Platform Core',
  ];

  const available = suggestions.filter(
    (s) => !existingNames.some((n) => n.toLowerCase() === s.toLowerCase())
  );

  return available[0] || `Workspace ${existingNames.length + 1}`;
}

function normalizePersonaSelections(personaSelections?: string[]): string[] {
  if (!personaSelections?.length) {
    return [];
  }

  return [...new Set(personaSelections.map((persona) => `persona:${persona}`))];
}

// ============================================================================
// Routes
// ============================================================================

export default async function workspaceRoutes(fastify: FastifyInstance) {
  /**
   * GET /api/workspaces
   * List all workspaces for current user
   *
   */
  fastify.get(
    '/workspaces',
    async (request: FastifyRequest, reply: FastifyReply) => {
      try {
        request.log.info({ event: 'workspace.list.request' }, 'GET /workspaces');

        if (!request.user?.userId) {
          request.log.warn({ event: 'workspace.list.unauthorized' }, 'No user in request');
          return reply.status(401).send({ error: 'Unauthorized' });
        }

        const userId = request.user.userId;
        request.log.info({ event: 'workspace.list.query', userId }, 'Querying workspaces');

        // Check database connection before querying
        try {
          await prisma.$queryRaw`SELECT 1`;
        } catch (connectionError) {
          request.log.error(
            { event: 'workspace.list.db_error', error: connectionError instanceof Error ? connectionError.message : String(connectionError) },
            'Database connection failed'
          );
          return reply.status(503).send({
            error: 'Database service unavailable',
            message:
              'The database connection failed. Please ensure the database service is running.',
            details:
              connectionError instanceof Error
                ? connectionError.message
                : String(connectionError),
          });
        }

        const workspaces = await prisma.workspace.findMany({
          where: {
            members: {
              some: { userId },
            },
          },
          include: {
            _count: {
              select: { ownedProjects: true },
            },
          },
          orderBy: [{ isDefault: 'desc' }, { updatedAt: 'desc' }],
        });
        request.log.info({ event: 'workspace.list.result', count: workspaces.length }, 'Workspaces found');

        const response = {
          workspaces: workspaces.map(
            (ws: { _count: { ownedProjects: number } }) => ({
              ...ws,
              projectCount: ws._count.ownedProjects,
            })
          ),
        };
        return reply.send(response);
      } catch (error) {
        request.log.error({ event: 'workspace.list.error', error: error instanceof Error ? error.message : String(error) }, 'GET /workspaces failed');
        return reply.status(500).send({ error: String(error) });
      }
    }
  );

  /**
   * GET /api/workspaces/:workspaceId
   * Get single workspace with projects
   */
  fastify.get<{ Params: WorkspaceParams }>(
    '/workspaces/:workspaceId',
    { preHandler: requireWorkspaceMember() },
    async (request, reply) => {
      try {
        const { workspaceId } = request.params;

        // Check database connection before querying
        try {
          await prisma.$queryRaw`SELECT 1`;
        } catch (connectionError) {
          request.log.error(
            { event: 'workspace.get.db_error', workspaceId, error: connectionError instanceof Error ? connectionError.message : String(connectionError) },
            'Database connection failed'
          );
          return reply.status(503).send({
            error: 'Database service unavailable',
            message:
              'The database connection failed. Please ensure the database service is running.',
            details:
              connectionError instanceof Error
                ? connectionError.message
                : String(connectionError),
          });
        }

        const workspace = await prisma.workspace.findUnique({
          where: { id: workspaceId },
          include: {
            ownedProjects: {
              orderBy: [{ isDefault: 'desc' }, { updatedAt: 'desc' }],
            },
            includedProjects: {
              include: {
                project: true,
              },
            },
          },
        });

        if (!workspace) {
          return reply.status(404).send({ error: 'Workspace not found' });
        }

        return reply.send({
          workspace: {
            ...workspace,
            includedProjects: workspace.includedProjects.map(
              (ip: { project: Record<string, unknown>; addedAt: Date }) => ({
                ...ip.project,
                isOwned: false,
                addedAt: ip.addedAt,
              })
            ),
          },
        });
      } catch (error) {
        request.log.error(
          { event: 'workspace.get.error', error: error instanceof Error ? error.message : String(error) },
          'GET /workspaces/:workspaceId failed'
        );
        return reply.status(500).send({ error: String(error) });
      }
    }
  );

  /**
   * POST /api/workspaces
   * Create new workspace with optional default project
   */
  fastify.post<{ Body: CreateWorkspaceBody }>(
    '/workspaces',
    { preHandler: requirePermission('workspace', 'create') },
    async (request, reply) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const {
        name,
        description,
        createDefaultProject = true,
        personaSelections,
        defaultProject,
      } = request.body;
      const userId = request.user.userId;

      // Check if user already has workspaces
      const existingWorkspaceCount = await prisma.workspace.count({
        where: { ownerId: userId },
      });

      // Only set as default if this is the user's first workspace
      const isDefault = existingWorkspaceCount === 0;

      // Create workspace
      const workspace = await prisma.workspace.create({
        data: {
          name,
          description,
          ownerId: userId,
          isDefault,
          aiSummary: 'New workspace ready for projects',
          aiTags: normalizePersonaSelections(personaSelections),
          members: {
            create: {
              userId,
              role: 'ADMIN',
            },
          },
        },
      });

      // Create default project if requested
      if (createDefaultProject) {
        await prisma.project.create({
          data: {
            name: defaultProject?.name ?? 'Untitled Project',
            description: defaultProject?.description,
            ownerWorkspaceId: workspace.id,
            createdById: userId,
            type: defaultProject?.type ?? 'FULL_STACK',
            status: 'DRAFT',
            lifecyclePhase: 'INTENT',
            isDefault: true,
            aiNextActions: ['Set up project structure', 'Define requirements'],
          },
        });
      }

      return reply.status(201).send({ workspace });
    }
  );

  /**
   * PATCH /api/workspaces/:workspaceId
   * Update workspace
   */
  fastify.patch<{ Params: WorkspaceParams; Body: UpdateWorkspaceBody }>(
    '/workspaces/:workspaceId',
    { preHandler: [requirePermission('workspace', 'update'), requireWorkspaceOwner()] },
    async (request, reply) => {
      const { workspaceId } = request.params;
      const { name, description } = request.body;

      const workspace = await prisma.workspace.update({
        where: { id: workspaceId },
        data: {
          ...(name && { name }),
          ...(description !== undefined && { description }),
        },
      });

      return reply.send({ workspace });
    }
  );

  /**
   * DELETE /api/workspaces/:workspaceId
   * Delete workspace (cascades to owned projects)
   */
  fastify.delete<{ Params: WorkspaceParams }>(
    '/workspaces/:workspaceId',
    { preHandler: [requirePermission('workspace', 'delete'), requireWorkspaceOwner()] },
    async (request, reply) => {
      const { workspaceId } = request.params;

      // Prevent deleting default workspace
      const workspace = await prisma.workspace.findUnique({
        where: { id: workspaceId },
      });

      if (!workspace) {
        return reply.status(404).send({ error: 'Workspace not found' });
      }

      if (workspace.isDefault) {
        return reply
          .status(400)
          .send({ error: 'Cannot delete default workspace' });
      }

      await prisma.workspace.delete({
        where: { id: workspaceId },
      });

      return reply.status(204).send();
    }
  );

  /**
   * GET /api/workspaces/suggest-name
   * AI suggests workspace name
   */
  fastify.get('/workspaces/suggest-name', async (request, reply) => {
    if (!request.user?.userId) {
      return reply.status(401).send({ error: 'Unauthorized' });
    }

    const userId = request.user.userId;

    const existing = await prisma.workspace.findMany({
      where: { ownerId: userId },
      select: { name: true },
    });

    const suggestion = suggestWorkspaceName(
      existing.map((w: { name: string }) => w.name)
    );

    return reply.send({ suggestion });
  });

  /**
   * POST /api/workspaces/:workspaceId/refresh-ai
   * Regenerate AI summary and tags
   */
  fastify.post<{ Params: WorkspaceParams }>(
    '/workspaces/:workspaceId/refresh-ai',
    { preHandler: requirePermission('ai', 'ai_generate') },
    async (request, reply) => {
      const { workspaceId } = request.params;

      const [aiSummary, aiTags] = await Promise.all([
        generateWorkspaceSummary(workspaceId),
        generateWorkspaceTags(workspaceId),
      ]);

      const workspace = await prisma.workspace.update({
        where: { id: workspaceId },
        data: { aiSummary, aiTags },
      });

      return reply.send({ workspace });
    }
  );
}

