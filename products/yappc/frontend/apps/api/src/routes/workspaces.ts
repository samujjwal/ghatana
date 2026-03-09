/**
 * Workspace API Routes
 * 
 * Dead simple REST API for workspace management with AI enhancements.
 * Workspaces are containers - project ownership determines permissions.
 * 
 * @doc.type router
 * @doc.purpose Workspace CRUD operations
 * @doc.layer product
 * @doc.pattern REST API
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import prisma from '../db';
import { markDeprecated } from '../middleware/deprecation';

// ============================================================================
// Types
// ============================================================================

interface CreateWorkspaceBody {
    name: string;
    description?: string;
    createDefaultProject?: boolean;
}

interface UpdateWorkspaceBody {
    name?: string;
    description?: string;
}

interface WorkspaceParams {
    workspaceId: string;
}

// ============================================================================
// AI Helpers
// ============================================================================

/**
 * Generate AI summary for workspace based on its projects
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
    const activeCount = projects.filter((p: { status: string }) => p.status === 'ACTIVE').length;

    return `${projects.length} projects (${activeCount} active) focusing on ${types.join(', ').toLowerCase()}`;
}

/**
 * Generate AI tags for workspace
 */
async function generateWorkspaceTags(workspaceId: string): Promise<string[]> {
    const projects = await prisma.project.findMany({
        where: { ownerWorkspaceId: workspaceId },
        select: { type: true, name: true },
    });

    const tags: string[] = [];

    // Add type-based tags
    const types = [...new Set(projects.map((p: { type: string }) => p.type))];
    types.forEach(type => {
        switch (type) {
            case 'UI': tags.push('frontend'); break;
            case 'BACKEND': tags.push('api'); break;
            case 'MOBILE': tags.push('mobile'); break;
            case 'FULL_STACK': tags.push('fullstack'); break;
        }
    });

    // Add activity-based tags
    if (projects.length > 5) tags.push('large');
    if (projects.length === 1) tags.push('focused');

    return tags.slice(0, 5);
}

/**
 * Suggest workspace name based on user context
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
        s => !existingNames.some(n => n.toLowerCase() === s.toLowerCase())
    );

    return available[0] || `Workspace ${existingNames.length + 1}`;
}

// ============================================================================
// Routes
// ============================================================================

export default async function workspaceRoutes(fastify: FastifyInstance) {
    /**
     * GET /api/workspaces
     * List all workspaces for current user
     * 
     * @deprecated Use Java backend: GET /api/workspaces
     * Sunset: 2025-06-06 (90 days from now)
     */
    fastify.get('/workspaces', async (request: FastifyRequest, reply: FastifyReply) => {
        markDeprecated(request, reply);
        try {
            console.log('[WORKSPACE] GET /workspaces - Request received');

            if (!request.user?.userId) {
                console.log('[WORKSPACE] No user in request, returning 401');
                return reply.status(401).send({ error: 'Unauthorized' });
            }

            const userId = request.user.userId;
            console.log('[WORKSPACE] Using userId:', userId);

            console.log('[WORKSPACE] Querying database...');

            // Check database connection before querying
            try {
                await prisma.$queryRaw`SELECT 1`;
            } catch (connectionError) {
                console.error('[WORKSPACE] Database connection failed:', connectionError);
                return reply.status(503).send({
                    error: 'Database service unavailable',
                    message: 'The database connection failed. Please ensure the database service is running.',
                    details: connectionError instanceof Error ? connectionError.message : String(connectionError)
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
                orderBy: [
                    { isDefault: 'desc' },
                    { updatedAt: 'desc' },
                ],
            });
            console.log('[WORKSPACE] Found workspaces:', workspaces.length);

            const response = {
                workspaces: workspaces.map((ws: { _count: { ownedProjects: number } }) => ({
                    ...ws,
                    projectCount: ws._count.ownedProjects,
                })),
            };
            console.log('[WORKSPACE] Sending response');
            return reply.send(response);
        } catch (error) {
            console.error('[WORKSPACE] Error in GET /workspaces:', error);
            return reply.status(500).send({ error: String(error) });
        }
    });

    /**
     * GET /api/workspaces/:workspaceId
     * Get single workspace with projects
     * @deprecated Use Java backend
     */
    fastify.get<{ Params: WorkspaceParams }>(
        '/workspaces/:workspaceId',
        async (request, reply) => {
            markDeprecated(request, reply);
            try {
                const { workspaceId } = request.params;

                // Check database connection before querying
                try {
                    await prisma.$queryRaw`SELECT 1`;
                } catch (connectionError) {
                    console.error('[WORKSPACE] Database connection failed:', connectionError);
                    return reply.status(503).send({
                        error: 'Database service unavailable',
                        message: 'The database connection failed. Please ensure the database service is running.',
                        details: connectionError instanceof Error ? connectionError.message : String(connectionError)
                    });
                }

                const workspace = await prisma.workspace.findUnique({
                    where: { id: workspaceId },
                    include: {
                        ownedProjects: {
                            orderBy: [
                                { isDefault: 'desc' },
                                { updatedAt: 'desc' },
                            ],
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
                        includedProjects: workspace.includedProjects.map((ip: { project: Record<string, unknown>; addedAt: Date }) => ({
                            ...ip.project,
                            isOwned: false,
                            addedAt: ip.addedAt,
                        })),
                    },
                });
            } catch (error) {
                console.error('[WORKSPACE] Error in GET /workspaces/:workspaceId:', error);
                return reply.status(500).send({ error: String(error) });
            }
        }
    );

    /**
     * POST /api/workspaces
     * Create new workspace with optional default project
     * @deprecated Use Java backend
     */
    fastify.post<{ Body: CreateWorkspaceBody }>(
        '/workspaces',
        async (request, reply) => {
            markDeprecated(request, reply);
            if (!request.user?.userId) {
                return reply.status(401).send({ error: 'Unauthorized' });
            }

            const { name, description, createDefaultProject = true } = request.body;
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
                    aiTags: [],
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
                        name: 'Untitled Project',
                        ownerWorkspaceId: workspace.id,
                        createdById: userId,
                        type: 'FULL_STACK',
                        status: 'DRAFT',
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
     * @deprecated Use Java backend
     */
    fastify.patch<{ Params: WorkspaceParams; Body: UpdateWorkspaceBody }>(
        '/workspaces/:workspaceId',
        async (request, reply) => {
            markDeprecated(request, reply);
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
     * @deprecated Use Java backend
     */
    fastify.delete<{ Params: WorkspaceParams }>(
        '/workspaces/:workspaceId',
        async (request, reply) => {
            markDeprecated(request, reply);
            const { workspaceId } = request.params;

            // Prevent deleting default workspace
            const workspace = await prisma.workspace.findUnique({
                where: { id: workspaceId },
            });

            if (!workspace) {
                return reply.status(404).send({ error: 'Workspace not found' });
            }

            if (workspace.isDefault) {
                return reply.status(400).send({ error: 'Cannot delete default workspace' });
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
     * @deprecated Use Java backend
     */
    fastify.get('/workspaces/suggest-name', async (request, reply) => {
        markDeprecated(request, reply);
        if (!request.user?.userId) {
            return reply.status(401).send({ error: 'Unauthorized' });
        }

        const userId = request.user.userId;

        const existing = await prisma.workspace.findMany({
            where: { ownerId: userId },
            select: { name: true },
        });

        const suggestion = suggestWorkspaceName(existing.map((w: { name: string }) => w.name));

        return reply.send({ suggestion });
    });

    /**
     * POST /api/workspaces/:workspaceId/refresh-ai
     * Regenerate AI summary and tags
     * @deprecated Use Java backend
     */
    fastify.post<{ Params: WorkspaceParams }>(
        '/workspaces/:workspaceId/refresh-ai',
        async (request, reply) => {
            markDeprecated(request, reply);
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

