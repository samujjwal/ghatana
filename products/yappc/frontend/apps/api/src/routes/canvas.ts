/**
 * Canvas API Routes
 * 
 * Handles canvas document persistence and operations.
 * Canvas data is stored in the database for reliability and sharing.
 * Supports versioning, conflict resolution, and real-time collaboration.
 * 
 * @doc.type router
 * @doc.purpose Canvas CRUD operations with versioning
 * @doc.layer product
 * @doc.pattern REST API
 */
import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import prisma from '../db';

interface GetCanvasParams {
    projectId: string;
    canvasId?: string;
}

interface SaveCanvasBody {
    projectId: string;
    canvasId: string;
    data: unknown;
    changeType?: 'MANUAL_SAVE' | 'AUTO_SAVE' | 'RESTORE' | 'MERGE';
    changeSummary?: string;
}

interface RestoreVersionBody {
    versionId: string;
}

export default async function canvasRoutes(fastify: FastifyInstance) {
    /**
     * GET /api/canvas/health
     * Health check for canvas service
     */
    fastify.get('/canvas/health', async (request, reply) => {
        return { status: 'ok', service: 'canvas-api' };
    });

    /**
     * GET /api/projects/:projectId/canvas
     * Load canvas data from database (unified canvas endpoint)
     */
    fastify.get<{ Params: { projectId: string } }>(
        '/projects/:projectId/canvas',
        async (request, reply) => {
            const { projectId } = request.params;
            const canvasId = 'unified-canvas'; // Default unified canvas

            try {
                // Find canvas document by name
                const canvasDoc = await prisma.canvasDocument.findFirst({
                    where: {
                        projectId,
                        name: canvasId,
                    },
                });

                if (!canvasDoc) {
                    // Return empty canvas structure if not found
                    return reply.send({
                        projectId,
                        nodes: [],
                        connections: [],
                        viewport: { x: 0, y: 0, zoom: 1 },
                        layers: [],
                        groups: [],
                        lastSaved: null,
                    });
                }

                return reply.send({
                    ...canvasDoc.content,
                    projectId: canvasDoc.projectId,
                    lastSaved: canvasDoc.updatedAt.toISOString(),
                });
            } catch (error) {
                console.error('Failed to load canvas:', error);
                return reply.status(500).send({ error: 'Failed to load canvas' });
            }
        }
    );

    /**
     * GET /api/canvas/:projectId/:canvasId?
     * Load canvas data from database (legacy endpoint)
     */
    fastify.get<{ Params: GetCanvasParams }>(
        '/canvas/:projectId/:canvasId?',
        async (request, reply) => {
            const { projectId, canvasId = 'main' } = request.params;

            try {
                // Find canvas document by name (canvasId is the name)
                const canvasDoc = await prisma.canvasDocument.findFirst({
                    where: {
                        projectId,
                        name: canvasId,
                    },
                });

                if (!canvasDoc) {
                    return reply.status(404).send({ error: 'Canvas not found' });
                }

                return reply.send({
                    canvas: {
                        id: canvasDoc.id,
                        projectId: canvasDoc.projectId,
                        canvasId: canvasDoc.name,
                        data: canvasDoc.content,
                        createdAt: canvasDoc.createdAt,
                        updatedAt: canvasDoc.updatedAt,
                    },
                });
            } catch (error) {
                console.error('Failed to load canvas:', error);
                return reply.status(500).send({ error: 'Failed to load canvas' });
            }
        }
    );

    /**
     * PUT /api/projects/:projectId/canvas
     * Save canvas data to database (unified canvas endpoint)
     */
    fastify.put<{
        Params: { projectId: string };
        Body: Omit<SaveCanvasBody, 'projectId' | 'canvasId'> & { data: Record<string, unknown> };
    }>(
        '/projects/:projectId/canvas',
        async (request, reply) => {
            if (!request.user?.userId) {
                return reply.status(401).send({ error: 'Unauthorized' });
            }

            const { projectId } = request.params;
            const { data, changeType = 'MANUAL_SAVE', changeSummary } = request.body;
            const userId = request.user.userId;
            const canvasId = 'unified-canvas';

            try {
                // Verify project exists
                const project = await prisma.project.findUnique({
                    where: { id: projectId },
                });

                if (!project) {
                    return reply.status(404).send({ error: 'Project not found' });
                }

                // Find existing canvas
                const existing = await prisma.canvasDocument.findFirst({
                    where: {
                        projectId,
                        name: canvasId,
                    },
                    include: {
                        versions: {
                            orderBy: { version: 'desc' },
                            take: 1,
                        },
                    },
                });

                let canvasDoc;
                let newVersion: number;

                if (existing) {
                    // Update existing canvas
                    canvasDoc = await prisma.canvasDocument.update({
                        where: { id: existing.id },
                        data: { content: data },
                    });
                    newVersion = (existing.versions[0]?.version ?? 0) + 1;
                } else {
                    // Create new canvas
                    canvasDoc = await prisma.canvasDocument.create({
                        data: {
                            projectId,
                            name: canvasId,
                            content: data,
                            createdById: userId,
                        },
                    });
                    newVersion = 1;
                }

                // Create version history entry
                await prisma.canvasVersion.create({
                    data: {
                        canvasId: canvasDoc.id,
                        version: newVersion,
                        content: data,
                        changeType,
                        changedBy: userId,
                        changeSummary,
                    },
                });

                return reply.send({
                    projectId: canvasDoc.projectId,
                    lastSaved: canvasDoc.updatedAt.toISOString(),
                    version: newVersion,
                });
            } catch (error) {
                console.error('Failed to save canvas:', error);
                return reply.status(500).send({ error: 'Failed to save canvas' });
            }
        }
    );

    /**
     * POST /api/canvas
     * Save canvas data to database (legacy endpoint)
     */
    fastify.post<{ Body: SaveCanvasBody }>(
        '/canvas',
        async (request, reply) => {
            if (!request.user?.userId) {
                return reply.status(401).send({ error: 'Unauthorized' });
            }

            const { projectId, canvasId, data, changeType = 'MANUAL_SAVE', changeSummary } = request.body;
            const userId = request.user.userId;

            try {
                // Verify project exists
                const project = await prisma.project.findUnique({
                    where: { id: projectId },
                });

                if (!project) {
                    return reply.status(404).send({ error: 'Project not found' });
                }

                // Find existing canvas
                const existing = await prisma.canvasDocument.findFirst({
                    where: {
                        projectId,
                        name: canvasId,
                    },
                    include: {
                        versions: {
                            orderBy: { version: 'desc' },
                            take: 1,
                        },
                    },
                });

                let canvasDoc;
                let newVersion: number;

                if (existing) {
                    canvasDoc = await prisma.canvasDocument.update({
                        where: { id: existing.id },
                        data: { content: data },
                    });
                    newVersion = (existing.versions[0]?.version ?? 0) + 1;
                } else {
                    canvasDoc = await prisma.canvasDocument.create({
                        data: {
                            projectId,
                            name: canvasId,
                            content: data,
                            createdById: userId,
                        },
                    });
                    newVersion = 1;
                }

                // Create version history entry
                await prisma.canvasVersion.create({
                    data: {
                        canvasId: canvasDoc.id,
                        version: newVersion,
                        content: data,
                        changeType,
                        changedBy: userId,
                        changeSummary,
                    },
                });

                return reply.send({
                    canvas: {
                        id: canvasDoc.id,
                        projectId: canvasDoc.projectId,
                        canvasId: canvasDoc.name,
                        data: canvasDoc.content,
                        updatedAt: canvasDoc.updatedAt,
                        version: newVersion,
                    },
                });
            } catch (error) {
                console.error('Failed to save canvas:', error);
                return reply.status(500).send({ error: 'Failed to save canvas' });
            }
        }
    );

    /**
     * GET /api/projects/:projectId/canvas/versions
     * Get version history for canvas
     */
    fastify.get<{ Params: { projectId: string } }>(
        '/projects/:projectId/canvas/versions',
        async (request, reply) => {
            const { projectId } = request.params;
            const canvasId = 'unified-canvas';

            try {
                // Find canvas document
                const canvasDoc = await prisma.canvasDocument.findFirst({
                    where: {
                        projectId,
                        name: canvasId,
                    },
                    include: {
                        versions: {
                            orderBy: { createdAt: 'desc' },
                            take: 50, // Last 50 versions
                        },
                    },
                });

                if (!canvasDoc) {
                    return reply.send({ versions: [] });
                }

                const versions = canvasDoc.versions.map(v => ({
                    id: v.id,
                    version: v.version,
                    changeType: v.changeType,
                    changedBy: v.changedBy,
                    changeSummary: v.changeSummary,
                    createdAt: v.createdAt.toISOString(),
                }));

                return reply.send({ versions });
            } catch (error) {
                console.error('Failed to get version history:', error);
                return reply.status(500).send({ error: 'Failed to get version history' });
            }
        }
    );

    /**
     * POST /api/projects/:projectId/canvas/restore
     * Restore canvas to a previous version
     */
    fastify.post<{
        Params: { projectId: string };
        Body: RestoreVersionBody;
    }>(
        '/projects/:projectId/canvas/restore',
        async (request, reply) => {
            if (!request.user?.userId) {
                return reply.status(401).send({ error: 'Unauthorized' });
            }

            const { projectId } = request.params;
            const { versionId } = request.body;
            const userId = request.user.userId;
            const canvasId = 'unified-canvas';

            try {
                // Find the version to restore
                const versionToRestore = await prisma.canvasVersion.findUnique({
                    where: { id: versionId },
                    include: { canvas: true },
                });

                if (!versionToRestore || versionToRestore.canvas.projectId !== projectId) {
                    return reply.status(404).send({ error: 'Version not found' });
                }

                // Get current latest version
                const latestVersion = await prisma.canvasVersion.findFirst({
                    where: { canvasId: versionToRestore.canvasId },
                    orderBy: { version: 'desc' },
                });

                // Update canvas with restored content
                const canvasDoc = await prisma.canvasDocument.update({
                    where: { id: versionToRestore.canvasId },
                    data: { content: versionToRestore.content },
                });

                // Create new version entry for the restore
                const newVersion = (latestVersion?.version ?? 0) + 1;
                await prisma.canvasVersion.create({
                    data: {
                        canvasId: canvasDoc.id,
                        version: newVersion,
                        content: versionToRestore.content,
                        changeType: 'RESTORE',
                        changedBy: userId,
                        changeSummary: `Restored to version ${versionToRestore.version}`,
                        baseVersion: versionToRestore.version,
                    },
                });

                return reply.send({
                    projectId: canvasDoc.projectId,
                    lastSaved: canvasDoc.updatedAt.toISOString(),
                    version: newVersion,
                    restoredFrom: versionToRestore.version,
                });
            } catch (error) {
                console.error('Failed to restore version:', error);
                return reply.status(500).send({ error: 'Failed to restore version' });
            }
        }
    );

    /**
     * POST /api/canvas/validate
     * Validate canvas structure
     */
    fastify.post('/canvas/validate', async (request, reply) => {
        // Mock validation response for now
        return {
            valid: true,
            issues: [],
            score: 100,
            summary: {
                errors: 0,
                warnings: 0,
                info: 0
            },
            gaps: [],
            risks: []
        };
    });
}

