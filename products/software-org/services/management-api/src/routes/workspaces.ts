/**
 * Workspace override management routes for Software-Org application.
 *
 * <p><b>Purpose</b><br>
 * Provides REST API endpoints for managing workspace-level persona configuration
 * overrides. Workspace admins can set default configurations, disable features,
 * or enforce specific UI settings across the workspace.
 *
 * <p><b>Endpoints</b><br>
 * - GET /api/workspaces/:workspaceId/overrides - Get workspace overrides
 * - POST /api/workspaces/:workspaceId/overrides - Create/update overrides
 * - DELETE /api/workspaces/:workspaceId/overrides - Remove workspace overrides
 *
 * <p><b>Authorization</b><br>
 * All endpoints require JWT authentication AND workspace admin role.
 * Only workspace owners can manage overrides.
 *
 * <p><b>Validation</b><br>
 * Uses TypeBox for request validation. Validates override structure including
 * disabledPlugins array, forcedLayout, defaultRoles, and featureFlags.
 *
 * <p><b>Override Priority</b><br>
 * Configuration resolution order:
 * 1. System defaults (frontend schemas)
 * 2. Workspace overrides (these endpoints)
 * 3. User preferences (persona routes)
 *
 * <p><b>Boundary Compliance</b><br>
 * These endpoints handle WORKSPACE UI OVERRIDES only. They do NOT:
 * - Manage team membership (that's Java)
 * - Handle access control policies (that's Java)
 * - Process billing (that's Java)
 *
 * This is rapid-iteration, admin-facing UI configuration.
 *
 * @doc.type route
 * @doc.purpose Workspace override REST API
 * @doc.layer product
 * @doc.pattern REST API Routes
 */
import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { Type, Static } from '@sinclair/typebox';
import { TypeBoxTypeProvider } from '@fastify/type-provider-typebox';
import { authenticate, AuthRequest } from '../middleware/auth.js';
import * as workspaceService from '../services/workspace.service.js';

// TypeBox schemas for validation
const WorkspaceOverridesSchema = Type.Object({
    disabledPlugins: Type.Optional(Type.Array(Type.String())),
    forcedLayout: Type.Optional(Type.Any()),
    defaultRoles: Type.Optional(Type.Array(Type.String())),
    featureFlags: Type.Optional(Type.Record(Type.String(), Type.Boolean())),
}, { additionalProperties: true });

const UpsertOverrideSchema = Type.Object({
    overrides: WorkspaceOverridesSchema
});

type UpsertOverrideBody = Static<typeof UpsertOverrideSchema>;

const WorkspaceParamsSchema = Type.Object({
    workspaceId: Type.String({ format: 'uuid' })
});

type WorkspaceParams = Static<typeof WorkspaceParamsSchema>;

/**
 * Workspace routes plugin
 */
const workspaceRoutes: FastifyPluginAsync = async (fastify) => {
    const server = fastify.withTypeProvider<TypeBoxTypeProvider>();

    // All routes require authentication
    server.addHook('preHandler', authenticate);

    /**
     * GET /api/workspaces/:workspaceId/overrides
     * Get workspace-level persona overrides
     */
    server.get<{
        Params: WorkspaceParams
    }>(
        '/:workspaceId/overrides',
        {
            schema: {
                params: WorkspaceParamsSchema,
                response: {
                    200: Type.Object({
                        success: Type.Boolean(),
                        data: Type.Union([
                            Type.Object({
                                id: Type.String(),
                                workspaceId: Type.String(),
                                overrides: WorkspaceOverridesSchema,
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

                // Verify admin access
                const isAdmin = await workspaceService.verifyWorkspaceAdmin(userId, workspaceId);
                if (!isAdmin) {
                    return reply.status(403).send({
                        success: false,
                        error: 'Admin access required'
                    });
                }

                // Get workspace overrides
                const override = await workspaceService.getWorkspaceOverride(workspaceId);

                return reply.send({
                    success: true,
                    data: override
                });
            } catch (error) {
                request.log.error({ error, workspaceId: (request.params as WorkspaceParams).workspaceId }, 'Get workspace override failed');
                return reply.status(500).send({
                    success: false,
                    error: 'Failed to fetch workspace overrides'
                });
            }
        }
    );

    /**
     * POST /api/workspaces/:workspaceId/overrides
     * Create or update workspace-level overrides
     */
    server.post<{
        Params: WorkspaceParams,
        Body: UpsertOverrideBody
    }>(
        '/:workspaceId/overrides',
        {
            schema: {
                params: WorkspaceParamsSchema,
                body: UpsertOverrideSchema,
                response: {
                    200: Type.Object({
                        success: Type.Boolean(),
                        data: Type.Object({
                            id: Type.String(),
                            workspaceId: Type.String(),
                            overrides: WorkspaceOverridesSchema,
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
                const { overrides } = request.body as UpsertOverrideBody;

                // Verify admin access
                const isAdmin = await workspaceService.verifyWorkspaceAdmin(userId, workspaceId);
                if (!isAdmin) {
                    return reply.status(403).send({
                        success: false,
                        error: 'Admin access required'
                    });
                }

                // Upsert workspace override
                const override = await workspaceService.upsertWorkspaceOverride(
                    workspaceId,
                    { overrides }
                );

                // TODO: Emit WebSocket event for real-time sync to all workspace users
                // await emitWorkspaceOverrideUpdate(workspaceId, override);

                return reply.send({
                    success: true,
                    data: override
                });
            } catch (error) {
                request.log.error({ error, workspaceId: (request.params as WorkspaceParams).workspaceId }, 'Update workspace override failed');
                return reply.status(500).send({
                    success: false,
                    error: 'Failed to update workspace overrides'
                });
            }
        }
    );

    /**
     * DELETE /api/workspaces/:workspaceId/overrides
     * Remove workspace-level overrides (reset to system defaults)
     */
    server.delete<{
        Params: WorkspaceParams
    }>(
        '/:workspaceId/overrides',
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

                // Verify admin access
                const isAdmin = await workspaceService.verifyWorkspaceAdmin(userId, workspaceId);
                if (!isAdmin) {
                    return reply.status(403).send({
                        success: false,
                        error: 'Admin access required'
                    });
                }

                // Delete workspace override
                const deleted = await workspaceService.deleteWorkspaceOverride(workspaceId);

                if (!deleted) {
                    return reply.status(404).send({
                        success: false,
                        error: 'Workspace overrides not found'
                    });
                }

                // TODO: Emit WebSocket event for real-time sync
                // await emitWorkspaceOverrideDelete(workspaceId);

                return reply.send({
                    success: true,
                    message: 'Workspace overrides removed'
                });
            } catch (error) {
                request.log.error({ error, workspaceId: (request.params as WorkspaceParams).workspaceId }, 'Delete workspace override failed');
                return reply.status(500).send({
                    success: false,
                    error: 'Failed to delete workspace overrides'
                });
            }
        }
    );
};

export default workspaceRoutes;
