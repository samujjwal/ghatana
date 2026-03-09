/**
 * Tenant API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for tenant management, health, alerts, anomalies, A/B tests
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/tenants/:tenantId/metrics/health - Get tenant health status
 * - GET /api/v1/tenants/:tenantId/alerts - Get tenant alerts
 * - GET /api/v1/tenants/:tenantId/anomalies - Get detected anomalies
 * - GET /api/v1/tenants/:tenantId/ab-tests - Get A/B tests
 * - GET /api/v1/tenants/:tenantId/workflows - Get tenant workflows
 * - GET /api/v1/tenants/:tenantId/models - Get tenant models
 * - GET /api/v1/tenants/:tenantId/training-jobs - Get training jobs
 * - POST /api/v1/ab-tests - Create an A/B test
 * - POST /api/v1/ab-tests/:testId/stop - Stop an A/B test
 * - GET /api/v1/ab-tests/:testId/results - Get A/B test results
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../db/client.js';
import type { Prisma } from '../../generated/prisma-client/index.js';

/** Health status response */
interface HealthResponse {
    tenantId: string;
    healthy: boolean;
    lastChecked: string;
}

/** Alert response */
interface AlertResponse {
    id: string;
    severity: string;
    message: string;
    timestamp: string;
}

/** Anomaly response */
interface AnomalyResponse {
    id: string;
    metric: string;
    value: number;
    baselineValue: number;
    detectedAt: string;
    severity: string;
}

/** A/B test response */
interface AbTestResponse {
    id: string;
    name: string;
    status: string;
    startAt: string | null;
}

/** Workflow response */
interface WorkflowResponse {
    id: string;
    name: string;
    description: string | null;
    enabled: boolean;
    schedule: string | null;
    createdAt: string;
    updatedAt: string;
}

/** Training job response */
interface TrainingJobResponse {
    id: string;
    modelId: string;
    status: string;
    progress: number;
}

/** A/B test creation body */
interface CreateAbTestBody {
    name: string;
    description?: string;
    config?: Record<string, unknown>;
}

/**
 * Register tenant routes
 */
export default async function tenantRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/tenants/:tenantId/metrics/health
     * Get tenant health status
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/tenants/:tenantId/metrics/health',
        async (request: FastifyRequest<{ Params: { tenantId: string } }>, reply: FastifyReply) => {
            const { tenantId } = request.params;
            fastify.log.debug({ tenantId }, 'Fetching tenant health');

            const tenant = await prisma.tenant.findFirst({
                where: { OR: [{ id: tenantId }, { key: tenantId }] },
                include: { environments: true },
            });

            if (!tenant) {
                return reply.status(404).send({ error: 'Tenant not found' });
            }

            // Check if all environments are healthy
            const healthy = tenant.environments.every((e) => e.healthy);

            const response: HealthResponse = {
                tenantId: tenant.key,
                healthy,
                lastChecked: new Date().toISOString(),
            };

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/tenants/:tenantId/alerts
     * Get tenant alerts
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/tenants/:tenantId/alerts',
        async (request: FastifyRequest<{ Params: { tenantId: string } }>, reply: FastifyReply) => {
            const { tenantId } = request.params;
            fastify.log.debug({ tenantId }, 'Fetching tenant alerts');

            const tenant = await prisma.tenant.findFirst({
                where: { OR: [{ id: tenantId }, { key: tenantId }] },
            });

            if (!tenant) {
                return reply.status(404).send({ error: 'Tenant not found' });
            }

            const alerts = await prisma.alert.findMany({
                where: { tenantId: tenant.id, resolved: false },
                orderBy: { timestamp: 'desc' },
                take: 20,
            });

            const response: AlertResponse[] = alerts.map((a) => ({
                id: a.id,
                severity: a.severity,
                message: a.message,
                timestamp: a.timestamp.toISOString(),
            }));

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/tenants/:tenantId/anomalies
     * Get detected anomalies
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/tenants/:tenantId/anomalies',
        async (request: FastifyRequest<{ Params: { tenantId: string } }>, reply: FastifyReply) => {
            const { tenantId } = request.params;
            fastify.log.debug({ tenantId }, 'Fetching tenant anomalies');

            const tenant = await prisma.tenant.findFirst({
                where: { OR: [{ id: tenantId }, { key: tenantId }] },
            });

            if (!tenant) {
                return reply.status(404).send({ error: 'Tenant not found' });
            }

            const anomalies = await prisma.anomaly.findMany({
                where: { tenantId: tenant.id, resolved: false },
                orderBy: { detectedAt: 'desc' },
                take: 20,
            });

            const response: AnomalyResponse[] = anomalies.map((a) => ({
                id: a.id,
                metric: a.metric,
                value: a.value,
                baselineValue: a.baselineValue,
                detectedAt: a.detectedAt.toISOString(),
                severity: a.severity,
            }));

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/tenants/:tenantId/ab-tests
     * Get A/B tests for tenant
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/tenants/:tenantId/ab-tests',
        async (request: FastifyRequest<{ Params: { tenantId: string } }>, reply: FastifyReply) => {
            const { tenantId } = request.params;
            fastify.log.debug({ tenantId }, 'Fetching tenant A/B tests');

            const tenant = await prisma.tenant.findFirst({
                where: { OR: [{ id: tenantId }, { key: tenantId }] },
            });

            if (!tenant) {
                return reply.status(404).send({ error: 'Tenant not found' });
            }

            const tests = await prisma.abTest.findMany({
                where: { tenantId: tenant.id },
                orderBy: { createdAt: 'desc' },
            });

            const response: AbTestResponse[] = tests.map((t) => ({
                id: t.id,
                name: t.name,
                status: t.status,
                startAt: t.startedAt?.toISOString() ?? null,
            }));

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/tenants/:tenantId/workflows
     * Get tenant workflows
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/tenants/:tenantId/workflows',
        async (request: FastifyRequest<{ Params: { tenantId: string } }>, reply: FastifyReply) => {
            const { tenantId } = request.params;
            fastify.log.debug({ tenantId }, 'Fetching tenant workflows');

            // For now, return all workflows (in a real impl, workflows would be tenant-scoped)
            const workflows = await prisma.workflow.findMany({
                orderBy: { name: 'asc' },
            });

            const response: WorkflowResponse[] = workflows.map((w) => ({
                id: w.id,
                name: w.name,
                description: (w.metadata as Record<string, string>)?.description ?? null,
                enabled: w.status === 'ACTIVE',
                schedule: (w.configuration as Record<string, string>)?.schedule ?? null,
                createdAt: w.createdAt.toISOString(),
                updatedAt: w.updatedAt.toISOString(),
            }));

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/tenants/:tenantId/models
     * Get tenant models
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/tenants/:tenantId/models',
        async (request: FastifyRequest<{ Params: { tenantId: string } }>, reply: FastifyReply) => {
            const { tenantId } = request.params;
            fastify.log.debug({ tenantId }, 'Fetching tenant models');

            // Return all models (in a real impl, models would be tenant-scoped)
            const models = await prisma.mlModel.findMany({
                include: {
                    versions: {
                        where: { status: 'current' },
                        take: 1,
                    },
                },
                orderBy: { name: 'asc' },
            });

            const response = models.map((m) => {
                const v = m.versions[0];
                return {
                    id: m.id,
                    name: m.name,
                    status: m.status,
                    createdAt: m.createdAt.toISOString(),
                    accuracy: v?.accuracy ?? null,
                    precision: v?.precision ?? null,
                    recall: v?.recall ?? null,
                    f1Score: v?.f1Score ?? null,
                };
            });

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/tenants/:tenantId/training-jobs
     * Get training jobs for tenant
     */
    fastify.get<{ Params: { tenantId: string } }>(
        '/tenants/:tenantId/training-jobs',
        async (request: FastifyRequest<{ Params: { tenantId: string } }>, reply: FastifyReply) => {
            const { tenantId } = request.params;
            fastify.log.debug({ tenantId }, 'Fetching tenant training jobs');

            const tenant = await prisma.tenant.findFirst({
                where: { OR: [{ id: tenantId }, { key: tenantId }] },
            });

            if (!tenant) {
                return reply.status(404).send({ error: 'Tenant not found' });
            }

            const jobs = await prisma.trainingJob.findMany({
                where: { tenantId: tenant.id },
                orderBy: { createdAt: 'desc' },
            });

            const response: TrainingJobResponse[] = jobs.map((j) => ({
                id: j.id,
                modelId: j.modelId,
                status: j.status,
                progress: j.progress,
            }));

            return reply.send(response);
        }
    );

    /**
     * POST /api/v1/ab-tests
     * Create an A/B test
     */
    fastify.post<{ Body: CreateAbTestBody }>(
        '/ab-tests',
        async (request: FastifyRequest<{ Body: CreateAbTestBody }>, reply: FastifyReply) => {
            const { name, description, config = {} } = request.body;

            fastify.log.debug({ name }, 'Creating A/B test');

            if (!name) {
                return reply.status(400).send({ error: 'name is required' });
            }

            // Get default tenant (in real impl, would come from auth context)
            let tenant = await prisma.tenant.findFirst();
            if (!tenant) {
                tenant = await prisma.tenant.create({
                    data: { key: 'default', name: 'Default Tenant' },
                });
            }

            const test = await prisma.abTest.create({
                data: {
                    tenantId: tenant.id,
                    name,
                    description,
                    status: 'running',
                    startedAt: new Date(),
                    configJson: config as Prisma.InputJsonValue,
                },
            });

            return reply.status(201).send({
                id: test.id,
                name: test.name,
                status: test.status,
            });
        }
    );

    /**
     * POST /api/v1/ab-tests/:testId/stop
     * Stop an A/B test
     */
    fastify.post<{ Params: { testId: string } }>(
        '/ab-tests/:testId/stop',
        async (request: FastifyRequest<{ Params: { testId: string } }>, reply: FastifyReply) => {
            const { testId } = request.params;
            fastify.log.debug({ testId }, 'Stopping A/B test');

            const test = await prisma.abTest.findUnique({
                where: { id: testId },
            });

            if (!test) {
                return reply.status(404).send({ error: 'A/B test not found' });
            }

            const updated = await prisma.abTest.update({
                where: { id: testId },
                data: {
                    status: 'stopped',
                    stoppedAt: new Date(),
                },
            });

            return reply.send({
                id: updated.id,
                status: updated.status,
            });
        }
    );

    /**
     * GET /api/v1/ab-tests/:testId/results
     * Get A/B test results
     */
    fastify.get<{ Params: { testId: string } }>(
        '/ab-tests/:testId/results',
        async (request: FastifyRequest<{ Params: { testId: string } }>, reply: FastifyReply) => {
            const { testId } = request.params;
            fastify.log.debug({ testId }, 'Fetching A/B test results');

            const test = await prisma.abTest.findUnique({
                where: { id: testId },
            });

            if (!test) {
                return reply.status(404).send({ error: 'A/B test not found' });
            }

            return reply.send({
                id: test.id,
                results: {
                    winner: test.winner ?? 'A',
                    significance: test.significance ?? 0.95,
                    ...(test.resultsJson as Record<string, unknown>),
                },
            });
        }
    );
}
