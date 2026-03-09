/**
 * @doc.type routes
 * @doc.purpose HTTP endpoints for search operations
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyPluginAsync } from 'fastify';
import { SearchServiceImpl } from './service';
import type { TenantId, ModuleId } from '@ghatana/tutorputor-contracts';
import type { SearchFilters } from './service';

export const searchRoutes: FastifyPluginAsync = async (app) => {
    const searchService = new SearchServiceImpl(app.prisma);

    /**
     * GET /search
     * Search across modules, threads, etc.
     */
    app.get<{
        Querystring: {
            q: string;
            limit?: number;
            offset?: number;
            sortBy?: 'relevance' | 'newest' | 'rating' | 'popularity';
            type?: string; // Comma separated
            category?: string; // Comma separated
            minPrice?: number;
            maxPrice?: number;
            free?: boolean;
        };
    }>('/', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        const {
            q,
            limit,
            offset,
            sortBy,
            type,
            category,
            minPrice,
            maxPrice,
            free,
        } = request.query;

        if (!q) {
            return reply.code(400).send({ error: 'Query parameter "q" is required' });
        }

        const filters: SearchFilters = {};
        if (type) {
            filters.type = type.split(',') as any[];
        }
        if (category) {
            filters.category = category.split(',');
        }
        if (minPrice !== undefined || maxPrice !== undefined || free !== undefined) {
            filters.price = {
                min: minPrice,
                max: maxPrice,
                free: free,
            };
        }

        try {
            const results = await searchService.search({
                tenantId,
                query: q,
                limit: limit ? Number(limit) : undefined,
                offset: offset ? Number(offset) : undefined,
                sortBy,
                filters,
            });
            return reply.send(results);
        } catch (error) {
            app.log.error(error, 'Search failed');
            return reply.code(500).send({
                error: 'Search failed',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * GET /search/autocomplete
     * Get autocomplete suggestions
     */
    app.get<{
        Querystring: {
            q: string;
            limit?: number;
        };
    }>('/autocomplete', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        const { q, limit } = request.query;

        try {
            const suggestions = await searchService.autocomplete(
                tenantId,
                q || '',
                limit ? Number(limit) : undefined
            );
            return reply.send(suggestions);
        } catch (error) {
            app.log.error(error, 'Autocomplete failed');
            return reply.code(500).send({
                error: 'Autocomplete failed',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * GET /search/popular
     * Get popular search terms (or modules)
     */
    app.get<{
        Querystring: {
            limit?: number;
        };
    }>('/popular', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        const { limit } = request.query;

        try {
            const popular = await searchService.getPopularSearches(
                tenantId,
                limit ? Number(limit) : undefined
            );
            return reply.send(popular);
        } catch (error) {
            app.log.error(error, 'Failed to get popular searches');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });

    /**
     * GET /search/similar/:moduleId
     * Get similar modules
     */
    app.get<{
        Params: { moduleId: string };
        Querystring: { limit?: number };
    }>('/similar/:moduleId', async (request, reply) => {
        const tenantId = request.headers['x-tenant-id'] as TenantId;
        const { moduleId } = request.params;
        const { limit } = request.query;

        if (!tenantId) {
            return reply.code(400).send({ error: 'Tenant ID required' });
        }

        try {
            const similar = await searchService.getSimilar(
                tenantId,
                moduleId as ModuleId,
                limit ? Number(limit) : undefined
            );
            return reply.send(similar);
        } catch (error) {
            app.log.error(error, 'Failed to get similar items');
            return reply.code(500).send({
                error: 'Internal Server Error',
                message: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    });
};
