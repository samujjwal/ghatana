/**
 * Workflow Execution API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for workflow executions and triggers
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - POST /api/v1/workflows/:workflowId/execute - Execute a workflow
 * - GET /api/v1/workflows/:workflowId/triggers - List workflow triggers
 * - POST /api/v1/workflows/:workflowId/triggers - Create a trigger
 * - GET /api/v1/executions/:executionId - Get execution details
 * - POST /api/v1/executions/:executionId/cancel - Cancel an execution
 * - PATCH /api/v1/triggers/:triggerId - Update a trigger
 * - DELETE /api/v1/triggers/:triggerId - Delete a trigger
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../db/client.js';
import { Prisma } from '../../generated/prisma-client/index.js';

const toJsonInputValue = (
    value: Prisma.JsonValue,
): Prisma.InputJsonValue | typeof Prisma.JsonNull => {
    if (value === null) return Prisma.JsonNull;
    return value as Prisma.InputJsonValue;
};

/** Execution response */
interface ExecutionResponse {
    id: string;
    workflowId: string;
    status: string;
    startedAt: string;
    finishedAt?: string | null;
    triggeredBy: string | null;
    logs: string[];
}

/** Trigger response */
interface TriggerResponse {
    id: string;
    workflowId: string;
    type: string;
    enabled: boolean;
    config?: Record<string, unknown>;
}

/** Trigger creation body */
interface CreateTriggerBody {
    type?: string;
    config?: Record<string, unknown>;
    enabled?: boolean;
}

/** Trigger update body */
interface UpdateTriggerBody {
    type?: string;
    config?: Record<string, unknown>;
    enabled?: boolean;
}

/**
 * Register execution routes
 */
export default async function executionRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * POST /api/v1/workflows/:workflowId/execute
     * Execute a workflow
     */
    fastify.post<{ Params: { workflowId: string } }>(
        '/workflows/:workflowId/execute',
        async (request: FastifyRequest<{ Params: { workflowId: string } }>, reply: FastifyReply) => {
            const { workflowId } = request.params;
            fastify.log.debug({ workflowId }, 'Executing workflow');

            // Find workflow by id or name
            const workflow = await prisma.workflow.findFirst({
                where: { OR: [{ id: workflowId }, { name: workflowId }] },
            });

            if (!workflow) {
                return reply.status(404).send({ error: 'Workflow not found' });
            }

            // Create execution record
            const execution = await prisma.workflowExecution.create({
                data: {
                    workflowId: workflow.id,
                    workflowKey: workflow.name,
                    status: 'pending',
                    startedAt: new Date(),
                    logs: ['Execution started'],
                },
            });

            // Create initial steps (simplified - in real impl would be driven by workflow config)
            await prisma.workflowExecutionStep.create({
                data: {
                    executionId: execution.id,
                    stepKey: 'init',
                    name: 'Initialize',
                    status: 'running',
                    startedAt: new Date(),
                },
            });

            return reply.status(201).send({
                id: execution.id,
                workflowId: workflow.id,
                status: execution.status,
                startedAt: execution.startedAt.toISOString(),
                triggeredBy: 'user-demo',
            });
        }
    );

    /**
     * GET /api/v1/workflows/:workflowId/triggers
     * List workflow triggers
     */
    fastify.get<{ Params: { workflowId: string } }>(
        '/workflows/:workflowId/triggers',
        async (request: FastifyRequest<{ Params: { workflowId: string } }>, reply: FastifyReply) => {
            const { workflowId } = request.params;
            fastify.log.debug({ workflowId }, 'Fetching workflow triggers');

            const workflow = await prisma.workflow.findFirst({
                where: { OR: [{ id: workflowId }, { name: workflowId }] },
            });

            if (!workflow) {
                return reply.status(404).send({ error: 'Workflow not found' });
            }

            const triggers = await prisma.workflowTrigger.findMany({
                where: { workflowId: workflow.id },
                orderBy: { createdAt: 'desc' },
            });

            const response: TriggerResponse[] = triggers.map((t) => ({
                id: t.id,
                workflowId: workflow.id,
                type: t.type,
                enabled: t.enabled,
                config: t.config as Record<string, unknown>,
            }));

            return reply.send(response);
        }
    );

    /**
     * POST /api/v1/workflows/:workflowId/triggers
     * Create a trigger for a workflow
     */
    fastify.post<{ Params: { workflowId: string }; Body: CreateTriggerBody }>(
        '/workflows/:workflowId/triggers',
        async (
            request: FastifyRequest<{ Params: { workflowId: string }; Body: CreateTriggerBody }>,
            reply: FastifyReply
        ) => {
            const { workflowId } = request.params;
            const { type = 'schedule', config = {}, enabled = true } = request.body;

            fastify.log.debug({ workflowId, type }, 'Creating workflow trigger');

            const workflow = await prisma.workflow.findFirst({
                where: { OR: [{ id: workflowId }, { name: workflowId }] },
            });

            if (!workflow) {
                return reply.status(404).send({ error: 'Workflow not found' });
            }

            const trigger = await prisma.workflowTrigger.create({
                data: {
                    workflowId: workflow.id,
                    type,
                    config: config as Prisma.InputJsonValue,
                    enabled,
                },
            });

            return reply.status(201).send({
                id: trigger.id,
                workflowId: workflow.id,
                type: trigger.type,
                enabled: trigger.enabled,
            });
        }
    );

    /**
     * GET /api/v1/executions/:executionId
     * Get execution details
     */
    fastify.get<{ Params: { executionId: string } }>(
        '/executions/:executionId',
        async (request: FastifyRequest<{ Params: { executionId: string } }>, reply: FastifyReply) => {
            const { executionId } = request.params;
            fastify.log.debug({ executionId }, 'Fetching execution');

            const execution = await prisma.workflowExecution.findUnique({
                where: { id: executionId },
                include: { steps: true },
            });

            if (!execution) {
                return reply.status(404).send({ error: 'Execution not found' });
            }

            const response: ExecutionResponse = {
                id: execution.id,
                workflowId: execution.workflowId,
                status: execution.status,
                startedAt: execution.startedAt.toISOString(),
                finishedAt: execution.finishedAt?.toISOString() ?? null,
                triggeredBy: execution.triggeredByUser,
                logs: execution.logs as string[],
            };

            return reply.send(response);
        }
    );

    /**
     * POST /api/v1/executions/:executionId/cancel
     * Cancel an execution
     */
    fastify.post<{ Params: { executionId: string } }>(
        '/executions/:executionId/cancel',
        async (request: FastifyRequest<{ Params: { executionId: string } }>, reply: FastifyReply) => {
            const { executionId } = request.params;
            fastify.log.debug({ executionId }, 'Cancelling execution');

            const execution = await prisma.workflowExecution.findUnique({
                where: { id: executionId },
            });

            if (!execution) {
                return reply.status(404).send({ error: 'Execution not found' });
            }

            const updated = await prisma.workflowExecution.update({
                where: { id: executionId },
                data: {
                    status: 'cancelled',
                    finishedAt: new Date(),
                    logs: [...(execution.logs as string[]), 'Execution cancelled by user'],
                },
            });

            return reply.send({
                id: updated.id,
                status: updated.status,
            });
        }
    );

    /**
     * PATCH /api/v1/triggers/:triggerId
     * Update a trigger
     */
    fastify.patch<{ Params: { triggerId: string }; Body: UpdateTriggerBody }>(
        '/triggers/:triggerId',
        async (
            request: FastifyRequest<{ Params: { triggerId: string }; Body: UpdateTriggerBody }>,
            reply: FastifyReply
        ) => {
            const { triggerId } = request.params;
            const updates = request.body;

            fastify.log.debug({ triggerId, updates }, 'Updating trigger');

            const trigger = await prisma.workflowTrigger.findUnique({
                where: { id: triggerId },
            });

            if (!trigger) {
                return reply.status(404).send({ error: 'Trigger not found' });
            }

            const updated = await prisma.workflowTrigger.update({
                where: { id: triggerId },
                data: {
                    type: updates.type ?? trigger.type,
                    config:
                        updates.config !== undefined
                            ? (updates.config as Prisma.InputJsonValue)
                            : toJsonInputValue(trigger.config as Prisma.JsonValue),
                    enabled: updates.enabled ?? trigger.enabled,
                },
            });

            return reply.send({
                id: updated.id,
                type: updated.type,
                enabled: updated.enabled,
            });
        }
    );

    /**
     * DELETE /api/v1/triggers/:triggerId
     * Delete a trigger
     */
    fastify.delete<{ Params: { triggerId: string } }>(
        '/triggers/:triggerId',
        async (request: FastifyRequest<{ Params: { triggerId: string } }>, reply: FastifyReply) => {
            const { triggerId } = request.params;
            fastify.log.debug({ triggerId }, 'Deleting trigger');

            const trigger = await prisma.workflowTrigger.findUnique({
                where: { id: triggerId },
            });

            if (!trigger) {
                return reply.status(404).send({ error: 'Trigger not found' });
            }

            await prisma.workflowTrigger.delete({
                where: { id: triggerId },
            });

            return reply.send({
                id: triggerId,
                deleted: true,
            });
        }
    );
}
