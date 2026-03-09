/**
 * Metrics API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for generic metrics
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/metrics - Get current metrics snapshot
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../db/client.js';

/** Metrics query params */
interface MetricsQuery {
    timeRange?: string;
}

/** Metrics response */
interface MetricsResponse {
    timestamp: string;
    value: number;
    timeRange: string;
}

/**
 * Register metrics routes
 */
export default async function metricsRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/metrics
     * Get current metrics snapshot
     */
    fastify.get<{ Querystring: MetricsQuery }>(
        '/',
        async (request: FastifyRequest<{ Querystring: MetricsQuery }>, reply: FastifyReply) => {
            const { timeRange = '7d' } = request.query;
            fastify.log.debug({ timeRange }, 'Fetching metrics');

            // Aggregate KPI values as a simple metric
            const kpis = await prisma.kpi.findMany();
            const totalValue = kpis.reduce((sum, k) => sum + k.value, 0);

            const response: MetricsResponse = {
                timestamp: new Date().toISOString(),
                value: Math.round(totalValue),
                timeRange,
            };

            return reply.send(response);
        }
    );
}
