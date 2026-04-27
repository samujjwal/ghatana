/**
 * Planning API Routes
 *
 * REST API for sprint planning and backlog management.
 * - Sprint CRUD (project-scoped)
 * - Backlog item listing, create, move to/from sprint
 * - Workspace member management
 *
 * @doc.type router
 * @doc.purpose Sprint planning and workspace membership operations
 * @doc.layer product
 * @doc.pattern REST API
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import prisma from '../db';
import {
  requireWorkspaceMember,
  requireWorkspaceOwner,
} from '../middleware/resource-auth.middleware';

// ============================================================================
// Types
// ============================================================================

type SprintStatus = 'PLANNING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

interface SprintParams {
  projectId: string;
  sprintId: string;
}

interface CreateSprintBody {
  name: string;
  goal?: string;
  startDate?: string;
  endDate?: string;
  capacity?: number;
}

interface UpdateSprintBody {
  name?: string;
  goal?: string;
  status?: SprintStatus;
  startDate?: string;
  endDate?: string;
  capacity?: number;
}

interface MoveItemBody {
  itemId: string;
  sprintId: string | null;
}

interface WorkspaceMemberParams {
  workspaceId: string;
  userId: string;
}

interface AddMemberBody {
  userId: string;
  role?: 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';
}

interface UpdateMemberRoleBody {
  role: 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';
}

// ============================================================================
// Sprint Routes
// ============================================================================

export default async function planningRoutes(fastify: FastifyInstance): Promise<void> {
  /**
   * GET /api/planning/:projectId/sprints
   * List all sprints for a project
   */
  fastify.get<{ Params: { projectId: string } }>(
    '/planning/:projectId/sprints',
    async (request: FastifyRequest<{ Params: { projectId: string } }>, reply: FastifyReply) => {
      const { projectId } = request.params;

      const sprints = await prisma.sprint.findMany({
        where: { projectId },
        include: {
          _count: { select: { items: true } },
        },
        orderBy: [{ status: 'asc' }, { startDate: 'asc' }, { createdAt: 'desc' }],
      });

      return reply.send({
        sprints: sprints.map((s) => ({
          ...s,
          itemCount: s._count.items,
          startDate: s.startDate?.toISOString() ?? null,
          endDate: s.endDate?.toISOString() ?? null,
          createdAt: s.createdAt.toISOString(),
          updatedAt: s.updatedAt.toISOString(),
        })),
      });
    }
  );

  /**
   * GET /api/planning/:projectId/sprints/:sprintId
   * Get a single sprint with its items
   */
  fastify.get<{ Params: SprintParams }>(
    '/planning/:projectId/sprints/:sprintId',
    async (request: FastifyRequest<{ Params: SprintParams }>, reply: FastifyReply) => {
      const { projectId, sprintId } = request.params;

      const sprint = await prisma.sprint.findFirst({
        where: { id: sprintId, projectId },
        include: {
          items: {
            include: {
              owners: { include: { user: { select: { id: true, name: true, email: true } } } },
            },
            orderBy: [{ priority: 'asc' }, { updatedAt: 'desc' }],
          },
        },
      });

      if (!sprint) {
        return reply.status(404).send({ error: 'Sprint not found' });
      }

      return reply.send({
        sprint: {
          ...sprint,
          startDate: sprint.startDate?.toISOString() ?? null,
          endDate: sprint.endDate?.toISOString() ?? null,
          createdAt: sprint.createdAt.toISOString(),
          updatedAt: sprint.updatedAt.toISOString(),
          items: sprint.items.map((item) => ({
            ...item,
            startDate: item.startDate?.toISOString() ?? null,
            dueDate: item.dueDate?.toISOString() ?? null,
            completedAt: item.completedAt?.toISOString() ?? null,
            predictedDueDate: item.predictedDueDate?.toISOString() ?? null,
            createdAt: item.createdAt.toISOString(),
            updatedAt: item.updatedAt.toISOString(),
          })),
        },
      });
    }
  );

  /**
   * POST /api/planning/:projectId/sprints
   * Create a new sprint
   */
  fastify.post<{ Params: { projectId: string }; Body: CreateSprintBody }>(
    '/planning/:projectId/sprints',
    async (
      request: FastifyRequest<{ Params: { projectId: string }; Body: CreateSprintBody }>,
      reply: FastifyReply
    ) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { projectId } = request.params;
      const { name, goal, startDate, endDate, capacity } = request.body;

      if (!name?.trim()) {
        return reply.status(400).send({ error: 'Sprint name is required' });
      }

      // Verify project exists
      const project = await prisma.project.findUnique({ where: { id: projectId } });
      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      const sprint = await prisma.sprint.create({
        data: {
          projectId,
          name: name.trim(),
          goal: goal?.trim(),
          startDate: startDate ? new Date(startDate) : null,
          endDate: endDate ? new Date(endDate) : null,
          capacity,
          status: 'PLANNING',
        },
      });

      return reply.status(201).send({
        sprint: {
          ...sprint,
          startDate: sprint.startDate?.toISOString() ?? null,
          endDate: sprint.endDate?.toISOString() ?? null,
          createdAt: sprint.createdAt.toISOString(),
          updatedAt: sprint.updatedAt.toISOString(),
        },
      });
    }
  );

  /**
   * PATCH /api/planning/:projectId/sprints/:sprintId
   * Update sprint details
   */
  fastify.patch<{ Params: SprintParams; Body: UpdateSprintBody }>(
    '/planning/:projectId/sprints/:sprintId',
    async (
      request: FastifyRequest<{ Params: SprintParams; Body: UpdateSprintBody }>,
      reply: FastifyReply
    ) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { projectId, sprintId } = request.params;
      const { name, goal, status, startDate, endDate, capacity } = request.body;

      const sprint = await prisma.sprint.findFirst({ where: { id: sprintId, projectId } });
      if (!sprint) {
        return reply.status(404).send({ error: 'Sprint not found' });
      }

      const updated = await prisma.sprint.update({
        where: { id: sprintId },
        data: {
          ...(name !== undefined && { name: name.trim() }),
          ...(goal !== undefined && { goal: goal?.trim() }),
          ...(status !== undefined && { status }),
          ...(startDate !== undefined && { startDate: startDate ? new Date(startDate) : null }),
          ...(endDate !== undefined && { endDate: endDate ? new Date(endDate) : null }),
          ...(capacity !== undefined && { capacity }),
        },
      });

      return reply.send({
        sprint: {
          ...updated,
          startDate: updated.startDate?.toISOString() ?? null,
          endDate: updated.endDate?.toISOString() ?? null,
          createdAt: updated.createdAt.toISOString(),
          updatedAt: updated.updatedAt.toISOString(),
        },
      });
    }
  );

  /**
   * GET /api/planning/:projectId/backlog
   * List backlog items (items with no sprint assigned)
   */
  fastify.get<{ Params: { projectId: string }; Querystring: { type?: string; status?: string } }>(
    '/planning/:projectId/backlog',
    async (
      request: FastifyRequest<{
        Params: { projectId: string };
        Querystring: { type?: string; status?: string };
      }>,
      reply: FastifyReply
    ) => {
      const { projectId } = request.params;
      const { type, status } = request.query;

      // Backlog items are items in any phase for this project's workflows, without a sprint
      const items = await prisma.item.findMany({
        where: {
          sprintId: null,
          phase: {
            // Phase items linked to workflows of this project
            OR: [{ key: { contains: projectId } }],
          },
          ...(type && { type: type as 'FEATURE' | 'STORY' | 'TASK' | 'BUG' | 'EPIC' | 'SPIKE' | 'SECURITY_ISSUE' | 'TECH_DEBT' }),
          ...(status && { status: status as 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'IN_REVIEW' | 'COMPLETED' | 'ARCHIVED' }),
        },
        include: {
          owners: { include: { user: { select: { id: true, name: true, email: true } } } },
        },
        orderBy: [{ priority: 'asc' }, { updatedAt: 'desc' }],
        take: 100,
      });

      return reply.send({
        items: items.map((item) => ({
          ...item,
          startDate: item.startDate?.toISOString() ?? null,
          dueDate: item.dueDate?.toISOString() ?? null,
          completedAt: item.completedAt?.toISOString() ?? null,
          predictedDueDate: item.predictedDueDate?.toISOString() ?? null,
          createdAt: item.createdAt.toISOString(),
          updatedAt: item.updatedAt.toISOString(),
        })),
      });
    }
  );

  /**
   * PATCH /api/planning/:projectId/items/move
   * Move an item to a sprint (or back to backlog when sprintId is null)
   */
  fastify.patch<{ Params: { projectId: string }; Body: MoveItemBody }>(
    '/planning/:projectId/items/move',
    async (
      request: FastifyRequest<{ Params: { projectId: string }; Body: MoveItemBody }>,
      reply: FastifyReply
    ) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { itemId, sprintId } = request.body;

      if (!itemId) {
        return reply.status(400).send({ error: 'itemId is required' });
      }

      // Validate sprint belongs to project if provided
      if (sprintId) {
        const sprint = await prisma.sprint.findFirst({
          where: { id: sprintId, projectId: request.params.projectId },
        });
        if (!sprint) {
          return reply.status(404).send({ error: 'Sprint not found' });
        }
      }

      const updated = await prisma.item.update({
        where: { id: itemId },
        data: { sprintId: sprintId ?? null },
      });

      return reply.send({
        item: {
          ...updated,
          startDate: updated.startDate?.toISOString() ?? null,
          dueDate: updated.dueDate?.toISOString() ?? null,
          completedAt: updated.completedAt?.toISOString() ?? null,
          predictedDueDate: updated.predictedDueDate?.toISOString() ?? null,
          createdAt: updated.createdAt.toISOString(),
          updatedAt: updated.updatedAt.toISOString(),
        },
      });
    }
  );

  // ============================================================================
  // Workspace Member Routes
  // ============================================================================

  /**
   * GET /api/workspaces/:workspaceId/members
   * List all members of a workspace with user details
   */
  fastify.get<{ Params: { workspaceId: string } }>(
    '/workspaces/:workspaceId/members',
    { preHandler: requireWorkspaceMember() },
    async (
      request: FastifyRequest<{ Params: { workspaceId: string } }>,
      reply: FastifyReply
    ) => {
      const { workspaceId } = request.params;

      const members = await prisma.workspaceMember.findMany({
        where: { workspaceId },
        include: {
          user: {
            select: { id: true, name: true, email: true, role: true, createdAt: true },
          },
        },
        orderBy: [{ role: 'asc' }, { createdAt: 'asc' }],
      });

      return reply.send({
        members: members.map((m) => ({
          id: m.id,
          userId: m.userId,
          workspaceId: m.workspaceId,
          role: m.role,
          joinedAt: m.createdAt.toISOString(),
          updatedAt: m.updatedAt.toISOString(),
          user: {
            ...m.user,
            createdAt: m.user.createdAt.toISOString(),
          },
        })),
      });
    }
  );

  /**
   * POST /api/workspaces/:workspaceId/members
   * Add a user to a workspace
   */
  fastify.post<{ Params: { workspaceId: string }; Body: AddMemberBody }>(
    '/workspaces/:workspaceId/members',
    { preHandler: requireWorkspaceOwner() },
    async (
      request: FastifyRequest<{ Params: { workspaceId: string }; Body: AddMemberBody }>,
      reply: FastifyReply
    ) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { workspaceId } = request.params;
      const { userId, role = 'EDITOR' } = request.body;

      if (!userId) {
        return reply.status(400).send({ error: 'userId is required' });
      }

      // Validate user exists
      const user = await prisma.user.findUnique({ where: { id: userId } });
      if (!user) {
        return reply.status(404).send({ error: 'User not found' });
      }

      // Upsert - if already member, update role
      const member = await prisma.workspaceMember.upsert({
        where: { userId_workspaceId: { userId, workspaceId } },
        create: { userId, workspaceId, role },
        update: { role },
        include: {
          user: { select: { id: true, name: true, email: true, role: true, createdAt: true } },
        },
      });

      return reply.status(201).send({
        member: {
          id: member.id,
          userId: member.userId,
          workspaceId: member.workspaceId,
          role: member.role,
          joinedAt: member.createdAt.toISOString(),
          updatedAt: member.updatedAt.toISOString(),
          user: {
            ...member.user,
            createdAt: member.user.createdAt.toISOString(),
          },
        },
      });
    }
  );

  /**
   * PATCH /api/workspaces/:workspaceId/members/:userId/role
   * Update a member's role
   */
  fastify.patch<{ Params: WorkspaceMemberParams; Body: UpdateMemberRoleBody }>(
    '/workspaces/:workspaceId/members/:userId/role',
    { preHandler: requireWorkspaceOwner() },
    async (
      request: FastifyRequest<{ Params: WorkspaceMemberParams; Body: UpdateMemberRoleBody }>,
      reply: FastifyReply
    ) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { workspaceId, userId } = request.params;
      const { role } = request.body;

      if (!role) {
        return reply.status(400).send({ error: 'role is required' });
      }

      // Prevent removing the last OWNER
      if (role !== 'OWNER') {
        const ownerCount = await prisma.workspaceMember.count({
          where: { workspaceId, role: 'OWNER' },
        });
        const isTargetOwner = await prisma.workspaceMember.findFirst({
          where: { workspaceId, userId, role: 'OWNER' },
        });
        if (isTargetOwner && ownerCount <= 1) {
          return reply.status(400).send({ error: 'Cannot demote the last workspace owner' });
        }
      }

      const member = await prisma.workspaceMember.update({
        where: { userId_workspaceId: { userId, workspaceId } },
        data: { role },
        include: {
          user: { select: { id: true, name: true, email: true, role: true, createdAt: true } },
        },
      });

      return reply.send({
        member: {
          id: member.id,
          userId: member.userId,
          workspaceId: member.workspaceId,
          role: member.role,
          joinedAt: member.createdAt.toISOString(),
          updatedAt: member.updatedAt.toISOString(),
          user: {
            ...member.user,
            createdAt: member.user.createdAt.toISOString(),
          },
        },
      });
    }
  );

  /**
   * DELETE /api/workspaces/:workspaceId/members/:userId
   * Remove a member from a workspace
   */
  fastify.delete<{ Params: WorkspaceMemberParams }>(
    '/workspaces/:workspaceId/members/:userId',
    { preHandler: requireWorkspaceOwner() },
    async (
      request: FastifyRequest<{ Params: WorkspaceMemberParams }>,
      reply: FastifyReply
    ) => {
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { workspaceId, userId } = request.params;

      // Prevent removing the last owner
      const member = await prisma.workspaceMember.findFirst({
        where: { workspaceId, userId },
      });
      if (!member) {
        return reply.status(404).send({ error: 'Member not found' });
      }

      if (member.role === 'OWNER') {
        const ownerCount = await prisma.workspaceMember.count({
          where: { workspaceId, role: 'OWNER' },
        });
        if (ownerCount <= 1) {
          return reply.status(400).send({ error: 'Cannot remove the last workspace owner' });
        }
      }

      await prisma.workspaceMember.delete({
        where: { userId_workspaceId: { userId, workspaceId } },
      });

      return reply.status(204).send();
    }
  );
}
