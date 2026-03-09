/**
 * KPI API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for KPI data and trends
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/kpis - List all KPIs with current values
 * - GET /api/v1/kpis/:kpiId - Get single KPI details
 * - GET /api/v1/kpis/:kpiId/trends - Get KPI time-series data
 * - GET /api/v1/kpis/departments - Get KPIs grouped by department
 * - GET /api/v1/kpis/narratives - Get AI-generated KPI insights
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../db/client.js';

/** KPI response shape matching MSW contract */
interface KpiResponse {
    id: string;
    name: string;
    value: number;
    unit: string;
    trend: number;
    target?: number | null;
    lastUpdated: string;
}

/** KPI trend data point */
interface KpiTrendResponse {
    timestamp: string;
    value: number;
}

/** Department KPI breakdown */
interface DepartmentKpiResponse {
    departmentId: string;
    departmentName: string;
    kpis: KpiResponse[];
}

/** KPI narrative insight */
interface KpiNarrativeResponse {
    insight: string;
    confidence: number;
}

/** Query params for time range filtering */
interface TimeRangeQuery {
    timeRange?: string;
}

/**
 * Transform Prisma KPI to API response format
 */
function toKpiResponse(kpi: {
    id: string;
    key: string;
    name: string;
    value: number;
    unit: string;
    trend: number;
    target: number | null;
    lastUpdated: Date;
}): KpiResponse {
    return {
        id: kpi.key,
        name: kpi.name,
        value: kpi.value,
        unit: kpi.unit,
        trend: kpi.trend,
        target: kpi.target,
        lastUpdated: kpi.lastUpdated.toISOString(),
    };
}

/**
 * Register KPI routes
 */
export default async function kpiRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/kpis
     * List all KPIs with current values
     */
    fastify.get<{ Querystring: TimeRangeQuery }>(
        '/',
        async (request: FastifyRequest<{ Querystring: TimeRangeQuery }>, reply: FastifyReply) => {
            const { timeRange = '7d' } = request.query;
            fastify.log.debug({ timeRange }, 'Fetching KPIs');

            const kpis = await prisma.kpi.findMany({
                orderBy: { name: 'asc' },
            });

            const response: KpiResponse[] = kpis.map(toKpiResponse);
            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/kpis/departments
     * Get KPIs grouped by department
     */
    fastify.get<{ Querystring: TimeRangeQuery }>(
        '/departments',
        async (request: FastifyRequest<{ Querystring: TimeRangeQuery }>, reply: FastifyReply) => {
            const { timeRange = '7d' } = request.query;
            fastify.log.debug({ timeRange }, 'Fetching department KPIs');

            const departments = await prisma.department.findMany({
                include: {
                    kpis: true,
                },
                orderBy: { name: 'asc' },
            });

            const response: DepartmentKpiResponse[] = departments.map((dept) => ({
                departmentId: dept.id,
                departmentName: dept.name,
                kpis: dept.kpis.map(toKpiResponse),
            }));

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/kpis/narratives
     * Get AI-generated KPI insights
     */
    fastify.get<{ Querystring: TimeRangeQuery }>(
        '/narratives',
        async (request: FastifyRequest<{ Querystring: TimeRangeQuery }>, reply: FastifyReply) => {
            const { timeRange = '7d' } = request.query;
            fastify.log.debug({ timeRange }, 'Fetching KPI narratives');

            const narratives = await prisma.kpiNarrative.findMany({
                where: timeRange ? { timeRange } : undefined,
                orderBy: { createdAt: 'desc' },
                take: 10,
            });

            const response: KpiNarrativeResponse[] = narratives.map((n) => ({
                insight: n.insight,
                confidence: n.confidence,
            }));

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/kpis/:kpiId
     * Get single KPI details
     */
    fastify.get<{ Params: { kpiId: string }; Querystring: TimeRangeQuery }>(
        '/:kpiId',
        async (
            request: FastifyRequest<{ Params: { kpiId: string }; Querystring: TimeRangeQuery }>,
            reply: FastifyReply
        ) => {
            const { kpiId } = request.params;
            fastify.log.debug({ kpiId }, 'Fetching KPI');

            const kpi = await prisma.kpi.findFirst({
                where: { OR: [{ id: kpiId }, { key: kpiId }] },
            });

            if (!kpi) {
                return reply.status(404).send({ error: 'KPI not found' });
            }

            return reply.send(toKpiResponse(kpi));
        }
    );

    /**
     * GET /api/v1/kpis/:kpiId/trends
     * Get KPI time-series data
     */
    fastify.get<{ Params: { kpiId: string }; Querystring: TimeRangeQuery }>(
        '/:kpiId/trends',
        async (
            request: FastifyRequest<{ Params: { kpiId: string }; Querystring: TimeRangeQuery }>,
            reply: FastifyReply
        ) => {
            const { kpiId } = request.params;
            const { timeRange = '7d' } = request.query;
            fastify.log.debug({ kpiId, timeRange }, 'Fetching KPI trends');

            // Find KPI by id or key
            const kpi = await prisma.kpi.findFirst({
                where: { OR: [{ id: kpiId }, { key: kpiId }] },
            });

            if (!kpi) {
                return reply.status(404).send({ error: 'KPI not found' });
            }

            // Calculate date range based on timeRange param
            const now = new Date();
            let startDate: Date;
            switch (timeRange) {
                case '24h':
                    startDate = new Date(now.getTime() - 24 * 60 * 60 * 1000);
                    break;
                case '30d':
                    startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
                    break;
                case '90d':
                    startDate = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000);
                    break;
                case '7d':
                default:
                    startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
            }

            const dataPoints = await prisma.kpiDataPoint.findMany({
                where: {
                    kpiId: kpi.id,
                    timestamp: { gte: startDate },
                },
                orderBy: { timestamp: 'asc' },
            });

            const response: KpiTrendResponse[] = dataPoints.map((dp) => ({
                timestamp: dp.timestamp.toISOString(),
                value: dp.value,
            }));

            return reply.send(response);
        }
    );
}
