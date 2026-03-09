/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for audit log operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from 'fastify';
import { AuditServiceImpl } from './service';
import type { TenantId, UserId } from '@ghatana/tutorputor-contracts';

export const auditRoutes: FastifyPluginAsync = async (app) => {
    const auditService = new AuditServiceImpl(app.prisma);

    /**
     * GET /audit
     * Query audit logs
     */
    app.get<{
        Querystring: {
            action?: string;
            actorId?: string;
            resourceType?: string;
            resourceId?: string;
            startDate?: string;
            endDate?: string;
            cursor?: string;
            limit?: number;
            sortBy?: string;
            sortOrder?: 'asc' | 'desc';
        };
    }>('/', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        const {
            action,
            actorId,
            resourceType,
            resourceId,
            startDate,
            endDate,
            cursor,
            limit,
            sortBy,
            sortOrder,
        } = request.query;

        try {
            const result = await auditService.queryAuditEvents({
                tenantId,
                actorId: actorId as UserId,
                action,
                resourceType,
                resourceId,
                startDate,
                endDate,
                pagination: {
                    cursor,
                    limit: limit ? Number(limit) : undefined,
                    sortBy,
                    sortOrder,
                },
            });
            return reply.send(result);
        } catch (error) {
            app.log.error(error, 'Failed to query audit logs');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * GET /audit/summary
     * Get audit log summary
     */
    app.get<{
        Querystring: {
            days?: number;
        };
    }>('/summary', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        const { days } = request.query;

        try {
            const summary = await auditService.getAuditSummary({
                tenantId,
                days: days ? Number(days) : undefined,
            });
            return reply.send(summary);
        } catch (error) {
            app.log.error(error, 'Failed to get audit summary');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * POST /audit/export
     * Request audit log export
     */
    app.post<{
        Body: {
            startDate: string;
            endDate: string;
        };
    }>('/export', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        const { startDate, endDate } = request.body;

        try {
            const result = await auditService.exportAuditLog({
                tenantId,
                startDate,
                endDate,
            });
            return reply.send(result);
        } catch (error) {
            app.log.error(error, 'Failed to export audit logs');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });
};
