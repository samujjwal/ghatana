/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for compliance operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from 'fastify';
import { ComplianceServiceImpl } from './service';
import type { TenantId, UserId } from '@ghatana/tutorputor-contracts';

export const complianceRoutes: FastifyPluginAsync = async (app) => {
    const complianceService = new ComplianceServiceImpl(app.prisma);

    /**
     * POST /compliance/export
     * Request user data export
     */
    app.post<{
        Body: {
            userId?: string; // Admin can request for user
        };
    }>('/export', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        const requesterId = request.headers['x-user-id'] as UserId;
        // If requesting for self
        const targetUserId = (request.body.userId as UserId) || requesterId;

        if (!tenantId || !requesterId) {
            return reply.code(400).send({ error: 'Context required' });
        }

        try {
            // Fetch user email if not in request
            const user = await app.prisma.user.findUnique({ where: { id: targetUserId } });
            if (!user) return reply.code(404).send({ error: 'User not found' });

            const result = await complianceService.requestUserExport({
                userId: targetUserId,
                tenantId,
                requestedBy: targetUserId, // Self-request for now as fallback
                // userEmail: user.email, // Removed as service looks it up
            });
            return reply.send(result);
        } catch (error) {
            app.log.error(error, 'Failed to request export');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * POST /compliance/deletion/request-token
     */
    app.post('/deletion/request-token', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        const userId = request.headers['x-user-id'] as UserId;

        if (!userId) return reply.code(400).send({ error: 'User ID required' });

        const user = await app.prisma.user.findUnique({ where: { id: userId } });
        if (!user) return reply.code(404).send({ error: 'User not found' });

        const result = await complianceService.createDeletionVerification({
            userId,
            userEmail: user.email
        });
        return reply.send(result);
    });

    /**
     * POST /compliance/deletion/verify
     */
    app.post<{ Body: { token: string } }>('/deletion/verify', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        const userId = request.headers['x-user-id'] as UserId;
        const { token } = request.body;

        try {
            const result = await complianceService.verifyAndProcessDeletion({
                userId,
                tenantId,
                token
            });
            return reply.send(result);
        } catch (e: any) {
            return reply.code(400).send({ error: e.message });
        }
    });
};
