/**
 * ML Models API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for ML model catalog and monitoring
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/models - List all models
 * - GET /api/v1/models/compare - Compare two models
 * - GET /api/v1/models/:modelId - Get model details
 * - GET /api/v1/models/:modelId/versions - Get model version history
 * - GET /api/v1/models/:modelId/metrics - Get model metrics over time
 * - GET /api/v1/models/:modelId/feature-importance - Get feature importances
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../db/client.js';

/** Model list item response */
interface ModelListResponse {
    id: string;
    name: string;
    status: string;
    createdAt: string;
    accuracy: number | null;
    precision: number | null;
    recall: number | null;
    f1Score: number | null;
    currentVersion?: string;
    latency?: number | null;
    throughput?: number | null;
    deployedAt?: string | null;
    lastUpdated: string;
    type: string;
}

/** Model version response */
interface ModelVersionResponse {
    version: string;
    createdAt: string;
    accuracy: number | null;
    precision: number | null;
    recall: number | null;
    f1Score: number | null;
    latency: number | null;
    throughput: number | null;
    status: string;
}

/** Model metric response */
interface ModelMetricResponse {
    timestamp: string;
    accuracy: number | null;
    latency: number | null;
}

/** Feature importance response */
interface FeatureImportanceResponse {
    name: string;
    importance: number;
}

/** Model comparison response */
interface ModelCompareResponse {
    model1: ModelListResponse;
    model2: ModelListResponse;
    winner: string;
    metrics: Array<{
        name: string;
        model1: number | null;
        model2: number | null;
        delta: number | null;
    }>;
}

/**
 * Transform Prisma model to API response
 */
function toModelResponse(
    model: {
        id: string;
        key: string;
        name: string;
        status: string;
        type: string;
        createdAt: Date;
        updatedAt: Date;
    },
    currentVersion?: {
        version: string;
        accuracy: number | null;
        precision: number | null;
        recall: number | null;
        f1Score: number | null;
        latency: number | null;
        throughput: number | null;
        deployedAt: Date | null;
    } | null
): ModelListResponse {
    return {
        id: model.id,
        name: model.name,
        status: model.status,
        createdAt: model.createdAt.toISOString(),
        accuracy: currentVersion?.accuracy ?? null,
        precision: currentVersion?.precision ?? null,
        recall: currentVersion?.recall ?? null,
        f1Score: currentVersion?.f1Score ?? null,
        currentVersion: currentVersion?.version,
        latency: currentVersion?.latency ?? null,
        throughput: currentVersion?.throughput ?? null,
        deployedAt: currentVersion?.deployedAt?.toISOString() ?? null,
        lastUpdated: model.updatedAt.toISOString(),
        type: model.type,
    };
}

/**
 * Register ML model routes
 */
export default async function modelRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/models
     * List all models with current version metrics
     */
    fastify.get('/', async (request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching models');

        const models = await prisma.mlModel.findMany({
            include: {
                versions: {
                    where: { status: 'current' },
                    take: 1,
                },
            },
            orderBy: { name: 'asc' },
        });

        const response: ModelListResponse[] = models.map((m) =>
            toModelResponse(m, m.versions[0] ?? null)
        );

        return reply.send(response);
    });

    /**
     * GET /api/v1/models/compare
     * Compare two models side by side
     */
    fastify.get<{ Querystring: { modelId1?: string; modelId2?: string } }>(
        '/compare',
        async (
            request: FastifyRequest<{ Querystring: { modelId1?: string; modelId2?: string } }>,
            reply: FastifyReply
        ) => {
            const { modelId1, modelId2 } = request.query;
            fastify.log.debug({ modelId1, modelId2 }, 'Comparing models');

            if (!modelId1 || !modelId2) {
                return reply.status(400).send({ error: 'modelId1 and modelId2 are required' });
            }

            const [model1Data, model2Data] = await Promise.all([
                prisma.mlModel.findFirst({
                    where: { OR: [{ id: modelId1 }, { key: modelId1 }] },
                    include: { versions: { where: { status: 'current' }, take: 1 } },
                }),
                prisma.mlModel.findFirst({
                    where: { OR: [{ id: modelId2 }, { key: modelId2 }] },
                    include: { versions: { where: { status: 'current' }, take: 1 } },
                }),
            ]);

            if (!model1Data || !model2Data) {
                return reply.status(404).send({ error: 'One or both models not found' });
            }

            const model1 = toModelResponse(model1Data, model1Data.versions[0] ?? null);
            const model2 = toModelResponse(model2Data, model2Data.versions[0] ?? null);

            // Determine winner based on accuracy
            const acc1 = model1.accuracy ?? 0;
            const acc2 = model2.accuracy ?? 0;
            const winner = acc1 >= acc2 ? model1.id : model2.id;

            const response: ModelCompareResponse = {
                model1,
                model2,
                winner,
                metrics: [
                    {
                        name: 'accuracy',
                        model1: model1.accuracy,
                        model2: model2.accuracy,
                        delta: (model1.accuracy ?? 0) - (model2.accuracy ?? 0),
                    },
                    {
                        name: 'latency',
                        model1: model1.latency ?? null,
                        model2: model2.latency ?? null,
                        delta: (model1.latency ?? 0) - (model2.latency ?? 0),
                    },
                ],
            };

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/models/:modelId
     * Get model details
     */
    fastify.get<{ Params: { modelId: string } }>(
        '/:modelId',
        async (request: FastifyRequest<{ Params: { modelId: string } }>, reply: FastifyReply) => {
            const { modelId } = request.params;
            fastify.log.debug({ modelId }, 'Fetching model');

            const model = await prisma.mlModel.findFirst({
                where: { OR: [{ id: modelId }, { key: modelId }] },
                include: {
                    versions: {
                        where: { status: 'current' },
                        take: 1,
                    },
                },
            });

            if (!model) {
                return reply.status(404).send({ error: 'Model not found' });
            }

            return reply.send(toModelResponse(model, model.versions[0] ?? null));
        }
    );

    /**
     * GET /api/v1/models/:modelId/versions
     * Get model version history
     */
    fastify.get<{ Params: { modelId: string } }>(
        '/:modelId/versions',
        async (request: FastifyRequest<{ Params: { modelId: string } }>, reply: FastifyReply) => {
            const { modelId } = request.params;
            fastify.log.debug({ modelId }, 'Fetching model versions');

            const model = await prisma.mlModel.findFirst({
                where: { OR: [{ id: modelId }, { key: modelId }] },
            });

            if (!model) {
                return reply.status(404).send({ error: 'Model not found' });
            }

            const versions = await prisma.mlModelVersion.findMany({
                where: { modelId: model.id },
                orderBy: { createdAt: 'desc' },
            });

            const response: ModelVersionResponse[] = versions.map((v) => ({
                version: v.version,
                createdAt: v.createdAt.toISOString(),
                accuracy: v.accuracy,
                precision: v.precision,
                recall: v.recall,
                f1Score: v.f1Score,
                latency: v.latency,
                throughput: v.throughput,
                status: v.status === 'current' ? 'Current' : 'Previous',
            }));

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/models/:modelId/metrics
     * Get model metrics over time
     */
    fastify.get<{ Params: { modelId: string } }>(
        '/:modelId/metrics',
        async (request: FastifyRequest<{ Params: { modelId: string } }>, reply: FastifyReply) => {
            const { modelId } = request.params;
            fastify.log.debug({ modelId }, 'Fetching model metrics');

            const model = await prisma.mlModel.findFirst({
                where: { OR: [{ id: modelId }, { key: modelId }] },
                include: {
                    versions: {
                        where: { status: 'current' },
                        take: 1,
                    },
                },
            });

            if (!model || !model.versions[0]) {
                return reply.status(404).send({ error: 'Model or current version not found' });
            }

            const metrics = await prisma.mlModelMetric.findMany({
                where: { modelVersionId: model.versions[0].id },
                orderBy: { timestamp: 'desc' },
                take: 20,
            });

            // Group metrics by timestamp and aggregate
            const grouped = new Map<string, { accuracy: number | null; latency: number | null }>();
            for (const m of metrics) {
                const ts = m.timestamp.toISOString();
                if (!grouped.has(ts)) {
                    grouped.set(ts, { accuracy: null, latency: null });
                }
                const entry = grouped.get(ts)!;
                if (m.name === 'accuracy') entry.accuracy = m.value;
                if (m.name === 'latency') entry.latency = m.value;
            }

            const response: ModelMetricResponse[] = Array.from(grouped.entries()).map(
                ([timestamp, vals]) => ({
                    timestamp,
                    accuracy: vals.accuracy,
                    latency: vals.latency,
                })
            );

            return reply.send(response);
        }
    );

    /**
     * GET /api/v1/models/:modelId/feature-importance
     * Get feature importances for model
     */
    fastify.get<{ Params: { modelId: string } }>(
        '/:modelId/feature-importance',
        async (request: FastifyRequest<{ Params: { modelId: string } }>, reply: FastifyReply) => {
            const { modelId } = request.params;
            fastify.log.debug({ modelId }, 'Fetching feature importance');

            const model = await prisma.mlModel.findFirst({
                where: { OR: [{ id: modelId }, { key: modelId }] },
                include: {
                    versions: {
                        where: { status: 'current' },
                        take: 1,
                    },
                },
            });

            if (!model || !model.versions[0]) {
                return reply.status(404).send({ error: 'Model or current version not found' });
            }

            const features = await prisma.modelFeatureImportance.findMany({
                where: { modelVersionId: model.versions[0].id },
                orderBy: { importance: 'desc' },
            });

            const response: FeatureImportanceResponse[] = features.map((f) => ({
                name: f.featureName,
                importance: f.importance,
            }));

            return reply.send(response);
        }
    );
}
