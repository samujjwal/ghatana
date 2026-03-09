/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for institution admin operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from 'fastify';
import { InstitutionAdminServiceImpl } from './service';
import type { TenantId, UserId } from '@ghatana/tutorputor-contracts';

export const adminRoutes: FastifyPluginAsync = async (app) => {
    const adminService = new InstitutionAdminServiceImpl(app.prisma);

    /**
     * GET /admin/tenant/summary
     * Get tenant summary (stats)
     */
    app.get('/tenant/summary', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;

        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        try {
            const summary = await adminService.getTenantSummary({ tenantId });
            return reply.send(summary);
        } catch (error) {
            app.log.error(error, 'Failed to get tenant summary');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * GET /admin/tenant/users
     * List users with pagination and filtering
     */
    app.get<{
        Querystring: {
            role?: string;
            searchQuery?: string;
            cursor?: string;
            limit?: number;
            sortBy?: string;
            sortOrder?: 'asc' | 'desc';
        };
    }>('/tenant/users', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        const { role, searchQuery, cursor, limit, sortBy, sortOrder } = request.query;

        try {
            const result = await adminService.listTenantUsers({
                tenantId,
                role,
                searchQuery,
                pagination: {
                    cursor,
                    limit: limit ? Number(limit) : undefined,
                    sortBy,
                    sortOrder,
                },
            });
            return reply.send(result);
        } catch (error) {
            app.log.error(error, 'Failed to list users');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * GET /admin/tenant/usage
     * Get usage metrics for a date range
     */
    app.get<{
        Querystring: {
            startDate: string;
            endDate: string;
        };
    }>('/tenant/usage', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        // Default to last 30 days if not provided
        const end = request.query.endDate
            ? new Date(request.query.endDate)
            : new Date();
        const start = request.query.startDate
            ? new Date(request.query.startDate)
            : new Date(end.getTime() - 30 * 24 * 60 * 60 * 1000);

        try {
            const metrics = await adminService.getTenantUsage({
                tenantId,
                dateRange: {
                    start: start.toISOString(),
                    end: end.toISOString(),
                },
            });
            return reply.send(metrics);
        } catch (error) {
            app.log.error(error, 'Failed to get usage metrics');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * POST /admin/tenant/users/import
     * Bulk import users
     */
    app.post<{
        Body: {
            users: any[];
            sendInvites?: boolean;
        };
    }>('/tenant/users/import', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        const adminId = request.headers['x-user-id'] as UserId;

        if (!tenantId || !adminId) {
            return reply.code(400).send({ error: 'Context required' });
        }

        try {
            const result = await adminService.bulkImportUsers({
                tenantId,
                importedBy: adminId,
                users: request.body.users,
                sendInvites: request.body.sendInvites,
            });
            return reply.send(result);
        } catch (error) {
            app.log.error(error, 'Failed to import users');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * PUT /admin/tenant/users/:userId/role
     * Update user role
     */
    app.put<{
        Params: { userId: string };
        Body: { newRole: string };
    }>('/tenant/users/:userId/role', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        const adminId = request.headers['x-user-id'] as UserId;
        const { userId } = request.params;
        const { newRole } = request.body;

        if (!tenantId || !adminId) {
            return reply.code(400).send({ error: 'Context required' });
        }

        try {
            const user = await adminService.updateUserRole({
                tenantId,
                userId: userId as UserId,
                newRole,
                updatedBy: adminId,
            });
            return reply.send(user);
        } catch (error) {
            app.log.error(error, `Failed to update role for user ${userId}`);
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * POST /admin/classrooms/:classroomId/assign-path
     * Assign learning path to classroom
     */
    app.post<{
        Params: { classroomId: string };
        Body: { pathwayId: string };
    }>('/classrooms/:classroomId/assign-path', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        const adminId = request.headers['x-user-id'] as UserId;
        const { classroomId } = request.params;
        const { pathwayId } = request.body;

        if (!tenantId || !adminId) {
            return reply.code(400).send({ error: 'Context required' });
        }

        try {
            const result = await adminService.assignPathToClassroom({
                tenantId,
                classroomId,
                pathwayId,
                assignedBy: adminId,
            });
            return reply.send(result);
        } catch (error) {
            app.log.error(error, 'Failed to assign pathway');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });
};
