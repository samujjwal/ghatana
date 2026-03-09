/**
 * Code Associations API Routes
 * 
 * REST API endpoints for managing code associations between artifacts
 * 
 * @doc.type module
 * @doc.purpose Code association REST API
 * @doc.layer product
 * @doc.pattern REST API
 */

import { FastifyPluginAsync } from 'fastify';
import { PrismaClient } from '../generated/prisma';

const prisma = new PrismaClient();

const codeAssociationsRoutes: FastifyPluginAsync = async (fastify) => {
    /**
     * GET /api/artifacts/:artifactId/code-associations
     * Fetch all code associations for an artifact
     */
    fastify.get('/api/artifacts/:artifactId/code-associations', async (request, reply) => {
        const { artifactId } = request.params as { artifactId: string };

        try {
            const associations = await prisma.codeAssociation.findMany({
                where: { artifactId },
                include: {
                    codeArtifact: {
                        select: {
                            id: true,
                            title: true,
                            description: true,
                            content: true,
                            format: true,
                            type: true,
                        },
                    },
                },
                orderBy: { createdAt: 'desc' },
            });

            return reply.send(associations);
        } catch (error) {
            fastify.log.error(error);
            return reply.status(500).send({ error: 'Failed to fetch code associations' });
        }
    });

    /**
     * POST /api/code-associations
     * Create a new code association
     */
    fastify.post('/api/code-associations', async (request, reply) => {
        const { artifactId, codeArtifactId, relationship, metadata } = request.body as {
            artifactId: string;
            codeArtifactId: string;
            relationship: string;
            metadata?: Record<string, unknown>;
        };

        try {
            // Validate that both artifacts exist
            const [artifact, codeArtifact] = await Promise.all([
                prisma.artifact.findUnique({ where: { id: artifactId } }),
                prisma.artifact.findUnique({ where: { id: codeArtifactId } }),
            ]);

            if (!artifact || !codeArtifact) {
                return reply.status(404).send({ error: 'Artifact not found' });
            }

            // Validate code artifact type
            if (!['CODE', 'TEST', 'SCRIPT'].includes(codeArtifact.type)) {
                return reply.status(400).send({
                    error: 'Code artifact must be of type CODE, TEST, or SCRIPT'
                });
            }

            // Create association
            const association = await prisma.codeAssociation.create({
                data: {
                    artifactId,
                    codeArtifactId,
                    relationship,
                    metadata: metadata || {},
                },
                include: {
                    codeArtifact: {
                        select: {
                            id: true,
                            title: true,
                            description: true,
                            content: true,
                            format: true,
                            type: true,
                        },
                    },
                },
            });

            return reply.status(201).send(association);
        } catch (error: unknown) {
            fastify.log.error(error);

            // Handle unique constraint violation
            if (error.code === 'P2002') {
                return reply.status(409).send({
                    error: 'Code association already exists'
                });
            }

            return reply.status(500).send({ error: 'Failed to create code association' });
        }
    });

    /**
     * DELETE /api/code-associations/:associationId
     * Delete a code association
     */
    fastify.delete('/api/code-associations/:associationId', async (request, reply) => {
        const { associationId } = request.params as { associationId: string };

        try {
            await prisma.codeAssociation.delete({
                where: { id: associationId },
            });

            return reply.send({ success: true });
        } catch (error: unknown) {
            fastify.log.error(error);

            if (error.code === 'P2025') {
                return reply.status(404).send({ error: 'Code association not found' });
            }

            return reply.status(500).send({ error: 'Failed to delete code association' });
        }
    });

    /**
     * GET /api/code-associations/stats/:artifactId
     * Get statistics about code associations for an artifact
     */
    fastify.get('/api/code-associations/stats/:artifactId', async (request, reply) => {
        const { artifactId } = request.params as { artifactId: string };

        try {
            const stats = await prisma.codeAssociation.groupBy({
                by: ['relationship'],
                where: { artifactId },
                _count: true,
            });

            const formatted = stats.reduce((acc, item) => {
                acc[item.relationship] = item._count;
                return acc;
            }, {} as Record<string, number>);

            return reply.send({
                artifactId,
                totalAssociations: stats.reduce((sum, item) => sum + item._count, 0),
                byRelationship: formatted,
            });
        } catch (error) {
            fastify.log.error(error);
            return reply.status(500).send({ error: 'Failed to fetch association stats' });
        }
    });
};

export default codeAssociationsRoutes;
