/**
 * Persona preference management routes for Software-Org application.
 *
 * <p><b>Purpose</b><br>
 * Provides REST API endpoints for managing user persona preferences per workspace.
 * Handles retrieval and updates of persona configurations including active roles,
 * dashboard layouts, plugins, and feature preferences.
 *
 * <p><b>Endpoints</b><br>
 * - GET /api/personas/:workspaceId - Get user's persona config for workspace
 * - PUT /api/personas/:workspaceId - Update user's persona config
 * - DELETE /api/personas/:workspaceId - Reset persona config to defaults
 *
 * <p><b>Authentication</b><br>
 * All endpoints require JWT authentication. User can only access their own
 * preferences unless they're a workspace admin.
 *
 * <p><b>Validation</b><br>
 * Uses TypeBox for request validation following Fastify best practices.
 * Validates activeRoles array and preferences object structure.
 *
 * <p><b>Boundary Compliance</b><br>
 * These endpoints handle USER PREFERENCES only. They do NOT:
 * - Define roles or permissions (that's Java domain logic)
 * - Process events (that's Java multi-agent-system)
 * - Execute workflows (that's Java)
 *
 * This is rapid-iteration, user-facing preference storage with real-time sync.
 *
 * @doc.type route
 * @doc.purpose Persona preference REST API
 * @doc.layer product
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { Type, Static } from '@sinclair/typebox';
import { TypeBoxTypeProvider } from '@fastify/type-provider-typebox';
import { authenticate, AuthRequest } from '../middleware/auth.js';
import * as personaService from '../services/persona.service.js';
import { broadcastPersonaUpdate, broadcastPersonaDelete } from '../websocket/persona-sync.js';

// TypeBox schemas for validation
const PersonaPreferencesSchema = Type.Object({
    dashboardLayout: Type.Optional(Type.Any()),
    plugins: Type.Optional(Type.Array(Type.Any())),
    metrics: Type.Optional(Type.Array(Type.Any())),
    features: Type.Optional(Type.Record(Type.String(), Type.Any())),
}, { additionalProperties: true });

const UpdatePersonaSchema = Type.Object({
    activeRoles: Type.Array(Type.String()),
    preferences: PersonaPreferencesSchema
});

type UpdatePersonaBody = Static<typeof UpdatePersonaSchema>;

const WorkspaceParamsSchema = Type.Object({
    workspaceId: Type.String({ format: 'uuid' })
});

type WorkspaceParams = Static<typeof WorkspaceParamsSchema>;

/**
 * Persona routes plugin
 */
const personaRoutes: FastifyPluginAsync = async (fastify) => {
    const server = fastify.withTypeProvider<TypeBoxTypeProvider>();

    // All routes require authentication
    server.addHook('preHandler', authenticate);

    /**
     * GET /api/personas/:workspaceId
     * Get user's persona configuration for specific workspace
     */
    server.get<{
        Params: WorkspaceParams
    }>(
        '/:workspaceId',
        {
            schema: {
                params: WorkspaceParamsSchema,
                response: {
                    200: Type.Object({
                        success: Type.Boolean(),
                        data: Type.Union([
                            Type.Object({
                                id: Type.String(),
                                userId: Type.String(),
                                workspaceId: Type.String(),
                                activeRoles: Type.Array(Type.String()),
                                preferences: PersonaPreferencesSchema,
                                createdAt: Type.String(),
                                updatedAt: Type.String()
                            }),
                            Type.Null()
                        ])
                    })
                }
            }
        },
        async (request, reply: FastifyReply) => {
            try {
                const authReq = request as AuthRequest;
                const { workspaceId } = request.params as WorkspaceParams;
                const userId = authReq.userId!;

                // Verify workspace access
                const hasAccess = await personaService.verifyWorkspaceAccess(userId, workspaceId);
                if (!hasAccess) {
                    return reply.status(403).send({
                        success: false,
                        error: 'Access denied to workspace'
                    });
                }

                // Get persona preference
                const preference = await personaService.getPersonaPreference(userId, workspaceId);

                return reply.send({
                    success: true,
                    data: preference
                });
            } catch (error) {
                request.log.error({ error, workspaceId: (request.params as WorkspaceParams).workspaceId }, 'Get persona preference failed');
                return reply.status(500).send({
                    success: false,
                    error: 'Failed to fetch persona preference'
                });
            }
        }
    );

    /**
     * PUT /api/personas/:workspaceId
     * Update user's persona configuration for workspace
     */
    server.put<{
        Params: WorkspaceParams,
        Body: UpdatePersonaBody
    }>(
        '/:workspaceId',
        {
            schema: {
                params: WorkspaceParamsSchema,
                body: UpdatePersonaSchema,
                response: {
                    200: Type.Object({
                        success: Type.Boolean(),
                        data: Type.Object({
                            id: Type.String(),
                            userId: Type.String(),
                            workspaceId: Type.String(),
                            activeRoles: Type.Array(Type.String()),
                            preferences: PersonaPreferencesSchema,
                            createdAt: Type.String(),
                            updatedAt: Type.String()
                        })
                    })
                }
            }
        },
        async (request, reply: FastifyReply) => {
            try {
                const authReq = request as AuthRequest;
                const { workspaceId } = request.params as WorkspaceParams;
                const userId = authReq.userId!;
                const { activeRoles, preferences } = request.body as UpdatePersonaBody;

                // Verify workspace access
                const hasAccess = await personaService.verifyWorkspaceAccess(userId, workspaceId);
                if (!hasAccess) {
                    return reply.status(403).send({
                        success: false,
                        error: 'Access denied to workspace'
                    });
                }

                // Upsert persona preference
                const preference = await personaService.upsertPersonaPreference(
                    userId,
                    workspaceId,
                    { activeRoles, preferences }
                );

                // Emit WebSocket event for real-time sync (broadcast to other clients)
                try {
                    broadcastPersonaUpdate(
                        request.server.io,
                        {
                            workspaceId,
                            userId,
                            activeRoles: preference.activeRoles,
                            preferences: preference.preferences as Record<string, unknown>,
                            timestamp: new Date().toISOString()
                        },
                        (request.raw as any).socket?.id // Exclude sender
                    );
                } catch (wsError) {
                    request.log.warn({ wsError }, 'Failed to broadcast persona update via WebSocket');
                }

                return reply.send({
                    success: true,
                    data: preference
                });
            } catch (error) {
                request.log.error({ error, workspaceId: (request.params as WorkspaceParams).workspaceId }, 'Update persona preference failed');
                return reply.status(500).send({
                    success: false,
                    error: 'Failed to update persona preference'
                });
            }
        }
    );

    /**
     * DELETE /api/personas/:workspaceId
     * Reset persona configuration to defaults (delete preference)
     */
    server.delete<{
        Params: WorkspaceParams
    }>(
        '/:workspaceId',
        {
            schema: {
                params: WorkspaceParamsSchema,
                response: {
                    200: Type.Object({
                        success: Type.Boolean(),
                        message: Type.String()
                    })
                }
            }
        },
        async (request, reply: FastifyReply) => {
            try {
                const authReq = request as AuthRequest;
                const { workspaceId } = request.params as WorkspaceParams;
                const userId = authReq.userId!;

                // Verify workspace access
                const hasAccess = await personaService.verifyWorkspaceAccess(userId, workspaceId);
                if (!hasAccess) {
                    return reply.status(403).send({
                        success: false,
                        error: 'Access denied to workspace'
                    });
                }

                // Delete persona preference
                const deleted = await personaService.deletePersonaPreference(userId, workspaceId);

                if (!deleted) {
                    return reply.status(404).send({
                        success: false,
                        error: 'Persona preference not found'
                    });
                }

                // Emit WebSocket event for real-time sync
                try {
                    broadcastPersonaDelete(
                        request.server.io,
                        {
                            workspaceId,
                            userId,
                            timestamp: new Date().toISOString()
                        },
                        (request.raw as any).socket?.id // Exclude sender
                    );
                } catch (wsError) {
                    request.log.warn({ wsError }, 'Failed to broadcast persona delete via WebSocket');
                }

                return reply.send({
                    success: true,
                    message: 'Persona preference reset to defaults'
                });
            } catch (error) {
                request.log.error({ error, workspaceId: (request.params as WorkspaceParams).workspaceId }, 'Delete persona preference failed');
                return reply.status(500).send({
                    success: false,
                    error: 'Failed to delete persona preference'
                });
            }
        }
    );
};

export default personaRoutes;
