/**
 * Resource Authorization Middleware
 *
 * Provides centralized resource-level authorization helpers that verify
 * workspace membership, project ownership, and canvas access beyond
 * global RBAC role checks.
 *
 * These helpers ensure users can only access resources they are members of
 * or have explicit permissions for.
 *
 * @doc.type module
 * @doc.purpose Fastify resource authorization pre-handlers
 * @doc.layer product
 * @doc.pattern Middleware, Security
 */

import type {
  FastifyRequest,
  FastifyReply,
  preHandlerHookHandler,
} from 'fastify';
import prisma from '../db';

// ============================================================================
// Types
// ============================================================================

/**
 * Resource access check result
 */
export interface AccessCheckResult {
  allowed: boolean;
  reason?: string;
  workspaceId?: string;
  isOwner?: boolean;
  isIncluded?: boolean;
  role?: string;
}

/**
 * Capability object returned for resource access
 */
export interface ResourceCapabilities {
  read: boolean;
  create: boolean;
  update: boolean;
  delete: boolean;
  include?: boolean;
  comment?: boolean;
  reason?: string;
}

// ============================================================================
// Workspace Authorization
// ============================================================================

/**
 * Checks if a user is a member of a workspace
 */
export async function checkWorkspaceMembership(
  userId: string,
  workspaceId: string
): Promise<AccessCheckResult> {
  if (!workspaceId) {
    return { allowed: false, reason: 'Workspace ID required' };
  }

  const member = await prisma.workspaceMember.findUnique({
    where: {
      workspaceId_userId: { workspaceId, userId },
    },
    include: { workspace: { select: { ownerId: true } } },
  });

  if (!member) {
    return { allowed: false, reason: 'Not a workspace member' };
  }

  const isOwner = member.workspace.ownerId === userId;

  return {
    allowed: true,
    workspaceId,
    role: member.role,
    isOwner,
  };
}

/**
 * Requires the user to be a member of the workspace
 */
export function requireWorkspaceMember(): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    // Extract workspaceId from params or query
    const workspaceId =
      (request.params as Record<string, string>)?.workspaceId ??
      (request.query as Record<string, string>)?.workspaceId;

    if (!workspaceId) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'workspaceId is required',
      });
    }

    const check = await checkWorkspaceMembership(user.userId, workspaceId);

    if (!check.allowed) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: check.reason || 'Access denied',
      });
    }

    // Attach workspace context to request
    (request as FastifyRequest & { workspaceContext?: AccessCheckResult }).workspaceContext = check;
  };
}

/**
 * Requires the user to be the owner of the workspace
 */
export function requireWorkspaceOwner(): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const workspaceId =
      (request.params as Record<string, string>)?.workspaceId ??
      (request.query as Record<string, string>)?.workspaceId;

    if (!workspaceId) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'workspaceId is required',
      });
    }

    const check = await checkWorkspaceMembership(user.userId, workspaceId);

    if (!check.allowed) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: check.reason || 'Access denied',
      });
    }

    if (!check.isOwner) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Workspace owner access required',
      });
    }

    (request as FastifyRequest & { workspaceContext?: AccessCheckResult }).workspaceContext = check;
  };
}

// ============================================================================
// Project Authorization
// ============================================================================

/**
 * Checks project access for a user through workspace membership
 */
export async function checkProjectAccess(
  userId: string,
  projectId: string,
  workspaceId?: string
): Promise<AccessCheckResult & { projectExists: boolean }> {
  if (!projectId) {
    return { allowed: false, reason: 'Project ID required', projectExists: false };
  }

  const project = await prisma.project.findUnique({
    where: { id: projectId },
    select: {
      id: true,
      ownerWorkspaceId: true,
    },
  });

  if (!project) {
    return { allowed: false, reason: 'Project not found', projectExists: false };
  }

  // Check if user is member of owning workspace
  const membership = await checkWorkspaceMembership(userId, project.ownerWorkspaceId);

  if (membership.allowed) {
    return {
      ...membership,
      projectExists: true,
      isOwner: true,
      isIncluded: false,
      workspaceId: project.ownerWorkspaceId,
    };
  }

  // Check if project is included in any workspace the user is a member of
  const userWorkspaces = await prisma.workspaceMember.findMany({
    where: { userId },
    select: { workspaceId: true },
  });

  const userWorkspaceIds = userWorkspaces.map((w) => w.workspaceId);

  const inclusion = await prisma.workspaceProject.findFirst({
    where: {
      projectId,
      workspaceId: { in: userWorkspaceIds },
    },
  });

  if (inclusion) {
    return {
      allowed: true,
      projectExists: true,
      isOwner: false,
      isIncluded: true,
      workspaceId: inclusion.workspaceId,
      role: 'VIEWER',
    };
  }

  return {
    allowed: false,
    reason: 'Not authorized to access this project',
    projectExists: true,
    workspaceId: project.ownerWorkspaceId,
  };
}

/**
 * Requires the user to have read access to the project
 */
export function requireProjectReadable(): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const projectId =
      (request.params as Record<string, string>)?.projectId ??
      (request.query as Record<string, string>)?.projectId;

    if (!projectId) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'projectId is required',
      });
    }

    const check = await checkProjectAccess(user.userId, projectId);

    if (!check.projectExists) {
      return reply.status(404).send({
        error: 'Not Found',
        message: check.reason || 'Project not found',
      });
    }

    if (!check.allowed) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: check.reason || 'Access denied',
      });
    }

    // Attach project context
    (
      request as FastifyRequest & {
        projectContext?: AccessCheckResult & { projectExists: boolean };
      }
    ).projectContext = check;
  };
}

/**
 * Requires the user to have write access to the project (not read-only included)
 */
export function requireProjectWritable(): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const projectId =
      (request.params as Record<string, string>)?.projectId ??
      (request.query as Record<string, string>)?.projectId;

    const workspaceId =
      (request.params as Record<string, string>)?.workspaceId ??
      (request.query as Record<string, string>)?.workspaceId;

    if (!projectId) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'projectId is required',
      });
    }

    const check = await checkProjectAccess(user.userId, projectId, workspaceId);

    if (!check.projectExists) {
      return reply.status(404).send({
        error: 'Not Found',
        message: check.reason || 'Project not found',
      });
    }

    if (!check.allowed) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: check.reason || 'Access denied',
      });
    }

    // Must be owner (not included) for write access
    if (check.isIncluded) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Included projects are read-only',
      });
    }

    // For explicit workspace context, verify ownership
    if (workspaceId && check.workspaceId !== workspaceId) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Project not owned by this workspace',
      });
    }

    (request as FastifyRequest & { projectContext?: typeof check }).projectContext = check;
  };
}

/**
 * Requires the project to be owned by the specified workspace
 */
export function requireProjectOwnerWorkspace(): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const projectId = (request.params as Record<string, string>)?.projectId;
    const workspaceId =
      (request.query as Record<string, string>)?.workspaceId ??
      (request.body as Record<string, string> | undefined)?.workspaceId;

    if (!projectId || !workspaceId) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'projectId and workspaceId are required',
      });
    }

    const project = await prisma.project.findUnique({
      where: { id: projectId },
      select: { ownerWorkspaceId: true },
    });

    if (!project) {
      return reply.status(404).send({
        error: 'Not Found',
        message: 'Project not found',
      });
    }

    if (project.ownerWorkspaceId !== workspaceId) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Cannot edit project you do not own',
      });
    }

    // Verify workspace membership
    const membership = await checkWorkspaceMembership(user.userId, workspaceId);

    if (!membership.allowed) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Not a member of this workspace',
      });
    }

    (request as FastifyRequest & { workspaceContext?: AccessCheckResult }).workspaceContext = membership;
  };
}

// ============================================================================
// Canvas Authorization
// ============================================================================

/**
 * Requires the user to have access to the canvas (via project access)
 */
export function requireCanvasReadable(): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const projectId =
      (request.params as Record<string, string>)?.projectId ??
      (request.query as Record<string, string>)?.projectId;

    if (!projectId) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'projectId is required',
      });
    }

    const check = await checkProjectAccess(user.userId, projectId);

    if (!check.projectExists) {
      return reply.status(404).send({
        error: 'Not Found',
        message: 'Project not found',
      });
    }

    if (!check.allowed) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: check.reason || 'Access denied',
      });
    }

    (request as FastifyRequest & { canvasContext?: typeof check }).canvasContext = check;
  };
}

/**
 * Requires the user to have write access to the canvas (not read-only)
 */
export function requireCanvasWritable(): preHandlerHookHandler {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const user = request.user;

    if (!user) {
      return reply
        .status(401)
        .send({ error: 'Unauthorized', message: 'Authentication required' });
    }

    const projectId =
      (request.params as Record<string, string>)?.projectId ??
      (request.query as Record<string, string>)?.projectId;

    if (!projectId) {
      return reply.status(400).send({
        error: 'Bad Request',
        message: 'projectId is required',
      });
    }

    const check = await checkProjectAccess(user.userId, projectId);

    if (!check.projectExists) {
      return reply.status(404).send({
        error: 'Not Found',
        message: 'Project not found',
      });
    }

    if (!check.allowed) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: check.reason || 'Access denied',
      });
    }

    if (check.isIncluded) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Cannot modify canvas of included read-only project',
      });
    }

    (request as FastifyRequest & { canvasContext?: typeof check }).canvasContext = check;
  };
}

// ============================================================================
// Capability Service
// ============================================================================

/**
 * Gets capabilities for a resource
 */
export async function getProjectCapabilities(
  userId: string,
  projectId: string,
  workspaceId?: string
): Promise<ResourceCapabilities> {
  const access = await checkProjectAccess(userId, projectId, workspaceId);

  if (!access.allowed) {
    return {
      read: false,
      create: false,
      update: false,
      delete: false,
      include: false,
      comment: false,
      reason: access.reason,
    };
  }

  const canWrite = !access.isIncluded;
  const isMember = access.role !== undefined;

  return {
    read: true,
    create: canWrite,
    update: canWrite,
    delete: canWrite && access.isOwner,
    include: isMember && canWrite, // Can include in other workspaces if owner
    comment: true,
  };
}

/**
 * Gets workspace capabilities for a user
 */
export async function getWorkspaceCapabilities(
  userId: string,
  workspaceId: string
): Promise<ResourceCapabilities> {
  const membership = await checkWorkspaceMembership(userId, workspaceId);

  if (!membership.allowed) {
    return {
      read: false,
      create: false,
      update: false,
      delete: false,
      reason: membership.reason,
    };
  }

  const role = membership.role?.toUpperCase();
  const canAdmin = role === 'ADMIN' || role === 'OWNER' || membership.isOwner;
  const canWrite = role === 'EDITOR' || canAdmin;

  return {
    read: true,
    create: canWrite, // Create projects
    update: canAdmin, // Update workspace settings
    delete: membership.isOwner, // Only owner can delete
    comment: true,
  };
}
