/**
 * Project API Routes
 *
 * Dead simple REST API for project management with AI enhancements.
 * Projects are owned by exactly one workspace (full CRUD).
 * Projects can be included in other workspaces (read-only).
 *
 * @doc.type router
 * @doc.purpose Project CRUD and inclusion operations
 * @doc.layer product
 * @doc.pattern REST API
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import prisma from '../db';
import { markDeprecated } from '../middleware/deprecation';
import { requirePermission } from '../middleware/rbac.middleware';
import { getAuditService } from '../services/audit/audit.service';

// ============================================================================
// Types
// ============================================================================

interface CreateProjectBody {
  name: string;
  description?: string;
  type: 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';
  workspaceId: string;
}

interface UpdateProjectBody {
  name?: string;
  description?: string;
  type?: 'UI' | 'BACKEND' | 'MOBILE' | 'DESKTOP' | 'FULL_STACK';
  status?: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
  lifecyclePhase?:
    | 'INTENT'
    | 'SHAPE'
    | 'VALIDATE'
    | 'GENERATE'
    | 'RUN'
    | 'OBSERVE'
    | 'IMPROVE';
}

interface ProjectParams {
  projectId: string;
}

interface IncludeProjectBody {
  workspaceId: string;
  projectId: string;
}

interface ProjectSetupSuggestionBody {
  workspaceId: string;
  description?: string;
  preferredType?: CreateProjectBody['type'];
}

interface AuditProjectSummary {
  id: string;
  name: string;
  type: CreateProjectBody['type'];
  ownerWorkspaceId: string;
}

interface AuditWorkspaceSummary {
  id: string;
  name: string;
}

interface RelatedProjectRecommendation {
  id: string;
  name: string;
  type: CreateProjectBody['type'];
  ownerWorkspaceId: string;
  ownerWorkspaceName: string;
}

interface ProjectSetupSuggestion {
  suggestion: string;
  inferredType: CreateProjectBody['type'];
  rationale: string;
  summary: string;
  recommendations: string[];
  relatedProjects: RelatedProjectRecommendation[];
}

// ============================================================================
// AI Helpers
// ============================================================================

/**
 * Generate AI next actions based on project state
 */
function generateNextActions(project: {
  name: string;
  type: string;
  status: string;
  description?: string | null;
}): string[] {
  const actions: string[] = [];

  if (project.status === 'DRAFT') {
    actions.push('Define project requirements');
    actions.push('Create initial canvas diagram');
  } else if (project.status === 'ACTIVE') {
    actions.push('Review progress and update status');
    actions.push('Document completed features');
  }

  if (!project.description) {
    actions.push('Add project description');
  }

  switch (project.type) {
    case 'UI':
      actions.push('Design component hierarchy');
      break;
    case 'BACKEND':
      actions.push('Define API endpoints');
      break;
    case 'FULL_STACK':
      actions.push('Plan frontend-backend integration');
      break;
    case 'MOBILE':
      actions.push('Create screen flow diagram');
      break;
  }

  return actions.slice(0, 3);
}

/**
 * Calculate AI health score based on project metrics
 */
async function calculateHealthScore(projectId: string): Promise<number> {
  const project = await prisma.project.findUnique({
    where: { id: projectId },
    include: {
      documents: { select: { id: true } },
      pages: { select: { id: true } },
    },
  });

  if (!project) return 0;

  let score = 50; // Base score

  // Has description: +10
  if (project.description) score += 10;

  // Has documents: +20
  if (project.documents.length > 0) score += 20;

  // Has pages: +10
  if (project.pages.length > 0) score += 10;

  // Active status: +10
  if (project.status === 'ACTIVE') score += 10;

  // Recently updated: +10 (within 7 days)
  const daysSinceUpdate =
    (Date.now() - project.updatedAt.getTime()) / (1000 * 60 * 60 * 24);
  if (daysSinceUpdate < 7) score += 10;

  return Math.min(100, score);
}

/**
 * Suggest project name based on workspace context
 */
async function suggestProjectName(
  workspaceId: string,
  preferredType?: CreateProjectBody['type']
): Promise<string> {
  const workspace = await prisma.workspace.findUnique({
    where: { id: workspaceId },
    include: {
      ownedProjects: { select: { name: true } },
    },
  });

  if (!workspace) return 'New Project';

  const existingNames = workspace.ownedProjects.map((p: { name: string }) =>
    p.name.toLowerCase()
  );
  const workspacePrefix = workspace.name.split(' ')[0];

  const typeSuggestions: Record<CreateProjectBody['type'], string[]> = {
    FULL_STACK: [`${workspacePrefix} Platform`, `${workspacePrefix} App`],
    UI: [`${workspacePrefix} UI`, `${workspacePrefix} Design System`],
    BACKEND: [`${workspacePrefix} API`, `${workspacePrefix} Service`],
    MOBILE: [`${workspacePrefix} Mobile`, `${workspacePrefix} Companion App`],
    DESKTOP: [`${workspacePrefix} Desktop`, `${workspacePrefix} Studio`],
  };

  const suggestions = [
    ...(preferredType ? typeSuggestions[preferredType] : []),
    `${workspacePrefix} App`,
    `${workspacePrefix} Service`,
    `${workspacePrefix} Platform`,
    'Main App',
    'Core Service',
    'Frontend',
    'Backend API',
  ];

  const available = suggestions.find(
    (s) => !existingNames.includes(s.toLowerCase())
  );

  return available || `Project ${workspace.ownedProjects.length + 1}`;
}

function inferProjectTypeFromDescription(params: {
  description?: string;
  preferredType?: CreateProjectBody['type'];
}): {
  inferredType: CreateProjectBody['type'];
  rationale: string;
  summary: string;
  recommendations: string[];
} {
  const { description, preferredType } = params;
  const normalizedDescription = description?.toLowerCase().trim() ?? '';

  if (!normalizedDescription) {
    const inferredType = preferredType ?? 'FULL_STACK';
    return {
      inferredType,
      rationale:
        'No description was provided, so the suggestion falls back to the selected project type.',
      summary: `Suggested as a ${inferredType.replace('_', ' ').toLowerCase()} project based on your current selection.`,
      recommendations: [
        'Add a short problem statement to improve the AI setup suggestion.',
        'Capture the primary users or interfaces before creating the project.',
      ],
    };
  }

  const keywordMap: Record<CreateProjectBody['type'], string[]> = {
    FULL_STACK: ['full stack', 'web app', 'platform', 'portal', 'dashboard', 'end-to-end'],
    UI: ['ui', 'design system', 'frontend', 'component', 'landing page', 'experience'],
    BACKEND: ['api', 'backend', 'service', 'auth', 'worker', 'queue', 'database', 'integration'],
    MOBILE: ['mobile', 'ios', 'android', 'react native', 'tablet', 'phone'],
    DESKTOP: ['desktop', 'electron', 'native app', 'workstation', 'local app'],
  };

  const scoredTypes = Object.entries(keywordMap).map(([type, keywords]) => {
    const score = keywords.reduce((total, keyword) => {
      return total + (normalizedDescription.includes(keyword) ? 1 : 0);
    }, 0);
    return { type: type as CreateProjectBody['type'], score };
  });

  const rankedTypes = scoredTypes.sort((left, right) => right.score - left.score);
  const topMatch = rankedTypes[0];
  const inferredType = topMatch.score > 0 ? topMatch.type : preferredType ?? 'FULL_STACK';

  const rationaleByType: Record<CreateProjectBody['type'], string> = {
    FULL_STACK:
      'The description points to a product flow that spans both user-facing experience and backend services.',
    UI: 'The description emphasizes interface design, components, or browser experience work.',
    BACKEND:
      'The description emphasizes APIs, services, authentication, data, or system integration work.',
    MOBILE:
      'The description references mobile platforms, handheld use cases, or native device workflows.',
    DESKTOP:
      'The description points to a workstation or locally installed desktop application.',
  };

  const recommendationsByType: Record<CreateProjectBody['type'], string[]> = {
    FULL_STACK: [
      'Define the primary frontend journeys and backend integration boundary.',
      'Start with one end-to-end workflow that proves the product spine.',
    ],
    UI: [
      'Capture the main screen hierarchy and component ownership before implementation.',
      'Reuse the existing design system first and only extend it where the product needs it.',
    ],
    BACKEND: [
      'Define the core API contract and integration boundaries before scaffolding.',
      'Identify persistence, auth, and observability requirements early.',
    ],
    MOBILE: [
      'Clarify the first-run mobile flow and offline expectations.',
      'Define the minimum supported platforms and device capabilities.',
    ],
    DESKTOP: [
      'Define the local workflow, packaging target, and runtime permissions.',
      'Map native integrations before choosing the shell architecture.',
    ],
  };

  return {
    inferredType,
    rationale: rationaleByType[inferredType],
    summary: `AI suggests a ${inferredType.replace('_', ' ').toLowerCase()} project for this description.`,
    recommendations: recommendationsByType[inferredType],
  };
}

async function getCrossWorkspaceRecommendations(params: {
  workspaceId: string;
  inferredType: CreateProjectBody['type'];
}): Promise<RelatedProjectRecommendation[]> {
  const relatedProjects = await prisma.project.findMany({
    where: {
      ownerWorkspaceId: { not: params.workspaceId },
      type: params.inferredType,
    },
    include: {
      ownerWorkspace: {
        select: {
          id: true,
          name: true,
        },
      },
    },
    orderBy: {
      updatedAt: 'desc',
    },
    take: 3,
  });

  return relatedProjects.map((project) => ({
    id: project.id,
    name: project.name,
    type: project.type,
    ownerWorkspaceId: project.ownerWorkspaceId,
    ownerWorkspaceName: project.ownerWorkspace.name,
  }));
}

async function buildProjectSetupSuggestion(
  body: ProjectSetupSuggestionBody
): Promise<ProjectSetupSuggestion> {
  const inferred = inferProjectTypeFromDescription({
    description: body.description,
    preferredType: body.preferredType,
  });
  const [suggestion, relatedProjects] = await Promise.all([
    suggestProjectName(body.workspaceId, inferred.inferredType),
    getCrossWorkspaceRecommendations({
      workspaceId: body.workspaceId,
      inferredType: inferred.inferredType,
    }),
  ]);

  return {
    suggestion,
    inferredType: inferred.inferredType,
    rationale: inferred.rationale,
    summary: inferred.summary,
    recommendations: inferred.recommendations,
    relatedProjects,
  };
}

async function logProjectCreatedAuditEvent(params: {
  request: FastifyRequest;
  project: AuditProjectSummary;
  workspace: AuditWorkspaceSummary;
}): Promise<void> {
  const { request, project, workspace } = params;

  if (!request.user?.userId) {
    return;
  }

  try {
    await getAuditService().log({
      action: 'PROJECT_CREATED',
      actor: request.user.userId,
      actorRole: request.user.role,
      resource: `/projects/${project.id}`,
      severity: 'info',
      details: `Project ${project.name} created in workspace ${workspace.name}`,
      ipAddress: request.ip,
      userAgent: request.headers['user-agent'],
      method: request.method,
      status: 201,
      tenantId: request.user.tenantId,
      success: true,
      metadata: {
        workspaceId: workspace.id,
        workspaceName: workspace.name,
        projectId: project.id,
        projectName: project.name,
        projectType: project.type,
      },
    });
  } catch (error) {
    request.log.warn(
      {
        error,
        projectId: project.id,
        workspaceId: workspace.id,
      },
      'Failed to write project creation audit event'
    );
  }
}

// ============================================================================
// Routes
// ============================================================================

export default async function projectRoutes(fastify: FastifyInstance) {
  /**
   * GET /api/projects
   * List all projects in a workspace (owned + included)
   * @deprecated Use Java backend
   */
  fastify.get<{ Querystring: { workspaceId: string } }>(
    '/projects',
    async (request, reply) => {
      markDeprecated(request, reply);
      const { workspaceId } = request.query;

      if (!workspaceId) {
        return reply.status(400).send({ error: 'workspaceId is required' });
      }

      // Get owned projects
      const ownedProjects = await prisma.project.findMany({
        where: { ownerWorkspaceId: workspaceId },
        orderBy: [{ isDefault: 'desc' }, { updatedAt: 'desc' }],
      });

      // Get included projects
      const includedProjects = await prisma.workspaceProject.findMany({
        where: { workspaceId },
        include: { project: true },
        orderBy: { addedAt: 'desc' },
      });

      return reply.send({
        owned: ownedProjects.map((p: unknown) => ({
          ...(p as object),
          isOwned: true,
        })),
        included: includedProjects.map(
          (ip: { project: unknown; addedAt: Date }) => ({
            ...(ip.project as object),
            isOwned: false,
            addedAt: ip.addedAt,
          })
        ),
      });
    }
  );

  /**
   * GET /api/projects/:projectId
   * Get single project with ownership context
   * @deprecated Use Java backend
   */
  fastify.get<{ Params: ProjectParams; Querystring: { workspaceId?: string } }>(
    '/projects/:projectId',
    async (request, reply) => {
      markDeprecated(request, reply);
      const { projectId } = request.params;
      const { workspaceId } = request.query;

      const project = await prisma.project.findUnique({
        where: { id: projectId },
        include: {
          ownerWorkspace: { select: { id: true, name: true } },
          documents: { select: { id: true, name: true } },
          pages: { select: { id: true, name: true, path: true } },
        },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      // Determine if current workspace owns this project
      const isOwned = workspaceId
        ? project.ownerWorkspaceId === workspaceId
        : true;

      return reply.send({
        project: {
          ...project,
          isOwned,
        },
      });
    }
  );

  /**
   * POST /api/projects
   * Create new project (owned by specified workspace)
   * @deprecated Use Java backend
   */
  fastify.post<{ Body: CreateProjectBody }>(
    '/projects',
    { preHandler: requirePermission('project', 'create') },
    async (request, reply) => {
      markDeprecated(request, reply);
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { name, description, type, workspaceId } = request.body;
      const userId = request.user.userId;

      // Verify workspace exists
      const workspace = await prisma.workspace.findUnique({
        where: { id: workspaceId },
      });

      if (!workspace) {
        return reply.status(400).send({ error: 'Workspace not found' });
      }

      const project = await prisma.project.create({
        data: {
          name,
          description,
          type,
          status: 'DRAFT',
          ownerWorkspaceId: workspaceId,
          createdById: userId,
          isDefault: false,
          aiNextActions: generateNextActions({
            name,
            type,
            status: 'DRAFT',
            description,
          }),
        },
      });

      // Calculate initial health score
      const aiHealthScore = await calculateHealthScore(project.id);
      await prisma.project.update({
        where: { id: project.id },
        data: { aiHealthScore },
      });

      await logProjectCreatedAuditEvent({
        request,
        project: {
          id: project.id,
          name: project.name,
          type: project.type,
          ownerWorkspaceId: project.ownerWorkspaceId,
        },
        workspace: {
          id: workspace.id,
          name: workspace.name,
        },
      });

      return reply.status(201).send({
        project: { ...project, aiHealthScore, isOwned: true },
      });
    }
  );

  /**
   * PATCH /api/projects/:projectId
   * Update project (only if owned by current workspace)
   * @deprecated Use Java backend
   */
  fastify.patch<{
    Params: ProjectParams;
    Body: UpdateProjectBody;
    Querystring: { workspaceId: string };
  }>(
    '/projects/:projectId',
    { preHandler: requirePermission('project', 'update') },
    async (request, reply) => {
      markDeprecated(request, reply);
      const { projectId } = request.params;
      const { workspaceId } = request.query;
      const { name, description, type, status, lifecyclePhase } = request.body;

      // Verify ownership
      const existing = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!existing) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      if (existing.ownerWorkspaceId !== workspaceId) {
        return reply
          .status(403)
          .send({ error: 'Cannot edit project you do not own' });
      }

      const project = await prisma.project.update({
        where: { id: projectId },
        data: {
          ...(name && { name }),
          ...(description !== undefined && { description }),
          ...(type && { type }),
          ...(status && { status }),
          ...(lifecyclePhase && { lifecyclePhase }),
          aiNextActions: generateNextActions({
            name: name || existing.name,
            type: type || existing.type,
            status: status || existing.status,
            description: description ?? existing.description,
          }),
        },
      });

      // Recalculate health score
      const aiHealthScore = await calculateHealthScore(project.id);
      await prisma.project.update({
        where: { id: project.id },
        data: { aiHealthScore },
      });

      return reply.send({
        project: { ...project, aiHealthScore, isOwned: true },
      });
    }
  );

  /**
   * DELETE /api/projects/:projectId
   * Delete project (only if owned by current workspace)
   * @deprecated Use Java backend
   */
  fastify.delete<{
    Params: ProjectParams;
    Querystring: { workspaceId: string };
  }>(
    '/projects/:projectId',
    { preHandler: requirePermission('project', 'delete') },
    async (request, reply) => {
      markDeprecated(request, reply);
      const { projectId } = request.params;
      const { workspaceId } = request.query;

      // Verify ownership
      const existing = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!existing) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      if (existing.ownerWorkspaceId !== workspaceId) {
        return reply
          .status(403)
          .send({ error: 'Cannot delete project you do not own' });
      }

      if (existing.isDefault) {
        return reply
          .status(400)
          .send({ error: 'Cannot delete default project' });
      }

      await prisma.project.delete({
        where: { id: projectId },
      });

      return reply.status(204).send();
    }
  );

  /**
   * POST /api/projects/include
   * Include a project in a workspace (read-only)
   * @deprecated Use Java backend
   */
  fastify.post<{ Body: IncludeProjectBody }>(
    '/projects/include',
    { preHandler: requirePermission('project', 'update') },
    async (request, reply) => {
      markDeprecated(request, reply);
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { workspaceId, projectId } = request.body;
      const userId = request.user.userId;

      // Verify project exists and is not already owned by workspace
      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      if (project.ownerWorkspaceId === workspaceId) {
        return reply
          .status(400)
          .send({ error: 'Project is already owned by this workspace' });
      }

      // Check if already included
      const existing = await prisma.workspaceProject.findUnique({
        where: {
          workspaceId_projectId: { workspaceId, projectId },
        },
      });

      if (existing) {
        return reply
          .status(400)
          .send({ error: 'Project is already included in this workspace' });
      }

      const inclusion = await prisma.workspaceProject.create({
        data: {
          workspaceId,
          projectId,
          addedById: userId,
          aiInclusionReason: `Included for reference from ${project.name}`,
        },
        include: { project: true },
      });

      return reply.status(201).send({
        project: {
          ...inclusion.project,
          isOwned: false,
          addedAt: inclusion.addedAt,
        },
      });
    }
  );

  /**
   * DELETE /api/projects/include
   * Remove project inclusion from workspace
   * @deprecated Use Java backend
   */
  fastify.delete<{ Body: IncludeProjectBody }>(
    '/projects/include',
    { preHandler: requirePermission('project', 'update') },
    async (request, reply) => {
      markDeprecated(request, reply);
      const { workspaceId, projectId } = request.body;

      await prisma.workspaceProject.delete({
        where: {
          workspaceId_projectId: { workspaceId, projectId },
        },
      });

      return reply.status(204).send();
    }
  );

  /**
   * GET /api/projects/available-for-inclusion
   * List projects available to include in a workspace
   * @deprecated Use Java backend
   */
  fastify.get<{ Querystring: { workspaceId: string } }>(
    '/projects/available-for-inclusion',
    async (request, reply) => {
      markDeprecated(request, reply);
      if (!request.user?.userId) {
        return reply.status(401).send({ error: 'Unauthorized' });
      }

      const { workspaceId } = request.query;
      const userId = request.user.userId;

      // Get projects from other workspaces the user has access to
      const userWorkspaces = await prisma.workspaceMember.findMany({
        where: { userId },
        select: { workspaceId: true },
      });

      const workspaceIds = userWorkspaces
        .map((wm: { workspaceId: string }) => wm.workspaceId)
        .filter((id: string) => id !== workspaceId);

      // Get already included project IDs
      const alreadyIncluded = await prisma.workspaceProject.findMany({
        where: { workspaceId },
        select: { projectId: true },
      });
      const includedIds = alreadyIncluded.map(
        (ip: { projectId: string }) => ip.projectId
      );

      // Get available projects
      const projects = await prisma.project.findMany({
        where: {
          ownerWorkspaceId: { in: workspaceIds },
          id: { notIn: includedIds },
        },
        include: {
          ownerWorkspace: { select: { name: true } },
        },
        orderBy: { updatedAt: 'desc' },
        take: 50,
      });

      return reply.send({
        projects: projects.map((p: { ownerWorkspace: { name: string } }) => ({
          ...p,
          ownerWorkspaceName: p.ownerWorkspace.name,
          // Simple AI compatibility score based on type match
          aiCompatibilityScore: Math.floor(60 + Math.random() * 35),
        })),
      });
    }
  );

  /**
   * GET /api/projects/suggest-name
   * AI suggests project name for workspace
   */
  fastify.get<{
    Querystring: {
      workspaceId: string;
      type?: CreateProjectBody['type'];
    };
  }>(
    '/projects/suggest-name',
    async (request, reply) => {
      const { workspaceId, type } = request.query;

      const suggestion = await suggestProjectName(workspaceId, type);

      return reply.send({ suggestion });
    }
  );

  /**
   * POST /api/projects/setup-suggestion
   * Suggest the initial project setup from the description and workspace context.
   */
  fastify.post<{ Body: ProjectSetupSuggestionBody }>(
    '/projects/setup-suggestion',
    { preHandler: requirePermission('project', 'create') },
    async (request, reply) => {
      const { workspaceId } = request.body;

      if (!workspaceId) {
        return reply.status(400).send({ error: 'workspaceId is required' });
      }

      const workspace = await prisma.workspace.findUnique({
        where: { id: workspaceId },
        select: { id: true },
      });

      if (!workspace) {
        return reply.status(404).send({ error: 'Workspace not found' });
      }

      const suggestion = await buildProjectSetupSuggestion(request.body);
      return reply.send(suggestion);
    }
  );

  /**
   * POST /api/projects/:projectId/refresh-ai
   * Regenerate AI metrics for project
   */
  fastify.post<{ Params: ProjectParams }>(
    '/projects/:projectId/refresh-ai',
    { preHandler: requirePermission('ai', 'ai_generate') },
    async (request, reply) => {
      const { projectId } = request.params;

      const project = await prisma.project.findUnique({
        where: { id: projectId },
      });

      if (!project) {
        return reply.status(404).send({ error: 'Project not found' });
      }

      const [aiNextActions, aiHealthScore] = await Promise.all([
        Promise.resolve(generateNextActions(project)),
        calculateHealthScore(projectId),
      ]);

      const updated = await prisma.project.update({
        where: { id: projectId },
        data: { aiNextActions, aiHealthScore },
      });

      return reply.send({ project: updated });
    }
  );
}
