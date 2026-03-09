import { FastifyInstance } from 'fastify';
import { rootService } from '../services/root.service';

export async function rootRoutes(fastify: FastifyInstance) {
    // Tenant Management
    fastify.get('/tenants', async (request, reply) => {
        const tenants = await rootService.getAllTenants();
        return tenants;
    });

    fastify.get<{ Params: { id: string } }>(
        '/tenants/:id',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: {
                        id: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            const tenant = await rootService.getTenantDetails(request.params.id);
            if (!tenant) return reply.status(404).send({ error: 'Tenant not found' });
            return tenant;
        }
    );

    // Platform Health
    fastify.get('/health', async (request, reply) => {
        const health = await rootService.getPlatformHealth();
        return health;
    });

    // Cross-Tenant Alerts Aggregation
    fastify.get<{ Querystring: { severity?: string; status?: string; limit?: number } }>(
        '/alerts/aggregated',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        severity: { type: 'string' },
                        status: { type: 'string' },
                        limit: { type: 'number' },
                    },
                },
            },
        },
        async (request) => {
            return rootService.getAggregatedAlerts(request.query);
        }
    );

    // Global User Management
    fastify.get<{ Querystring: { q?: string } }>(
        '/users/search',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        q: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            return rootService.searchGlobalUsers(request.query.q ?? '');
        }
    );

    fastify.post<{ Params: { id: string }; Body: { reason: string } }>(
        '/users/:id/suspend',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: {
                        id: { type: 'string' },
                    },
                },
                body: {
                    type: 'object',
                    required: ['reason'],
                    properties: {
                        reason: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            return rootService.suspendUser(request.params.id, request.body.reason);
        }
    );
}
