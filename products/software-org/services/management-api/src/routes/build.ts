/**
 * Build API Routes
 *
 * REST API endpoints for Build management (Workflows, Agents, Simulator).
 * Implements journeys from SOFTWARE_ORG_BUILD_IMPLEMENTATION_PLAN.md.
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for Build automation
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET/POST /api/v1/build/workflows - Workflow management
 * - GET/POST /api/v1/build/agents - Agent management
 * - POST /api/v1/build/simulator/run - Simulation execution
 */

import { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import type { Prisma } from '../../generated/prisma-client/index.js';

// =========================================================================
// Type Definitions
// =========================================================================

interface WorkflowResponse {
    id: string;
    tenantId: string;
    name: string;
    slug: string;
    description: string | null;
    status: string;
    ownerTeamId: string | null;
    trigger: Record<string, unknown>;
    steps: Record<string, unknown>[];
    serviceIds: string[];
    policyIds: string[];
    createdAt: string;
    updatedAt: string;
}

interface WorkflowCreateBody {
    name: string;
    slug: string;
    description?: string;
    ownerTeamId?: string;
    trigger: Record<string, unknown>;
    steps: Record<string, unknown>[];
}

interface WorkflowUpdateBody {
    name?: string;
    description?: string;
    ownerTeamId?: string;
    trigger?: Record<string, unknown>;
    steps?: Record<string, unknown>[];
    serviceIds?: string[];
    policyIds?: string[];
}

interface AgentResponse {
    id: string;
    tenantId: string;
    name: string;
    slug: string;
    description: string | null;
    type: string;
    status: string;
    personaId: string | null;
    tools: string[];
    guardrails: Record<string, unknown>;
    serviceIds: string[];
    createdAt: string;
    updatedAt: string;
}

interface AgentCreateBody {
    name: string;
    slug: string;
    description?: string;
    type: string;
    personaId?: string;
    tools: string[];
    guardrails: Record<string, unknown>;
}

interface AgentUpdateBody {
    name?: string;
    description?: string;
    type?: string;
    personaId?: string;
    tools?: string[];
    guardrails?: Record<string, unknown>;
    serviceIds?: string[];
}

interface SimulateRequest {
    workflowId?: string;
    agentId?: string;
    tenantId: string;
    environment: string;
    eventPayload: Record<string, unknown>;
}

interface SimulateResponse {
    simulationId: string;
    status: 'success' | 'failure';
    trace: {
        step: string;
        timestamp: string;
        action: string;
        result: Record<string, unknown>;
    }[];
    policyBlocks: {
        policyId: string;
        policyName: string;
        reason: string;
    }[];
    duration: number;
}

/**
 * Helper to write audit event
 */
async function writeAuditEvent(
    tenantId: string | null,
    actorUserId: string | null,
    entityType: string,
    entityId: string,
    action: string,
    details: Record<string, unknown> = {}
): Promise<void> {
    await prisma.auditEvent.create({
        data: {
            tenantId,
            actorUserId,
            entityType,
            entityId,
            action,
            detailsJson: details as Prisma.InputJsonValue,
        },
    });
}

/**
 * Register build routes
 */
export default async function buildRoutes(fastify: FastifyInstance): Promise<void> {
    // =========================================================================
    // Workflow Routes
    // =========================================================================

    /**
     * GET /api/v1/build/workflows
     * List all workflows for the current tenant
     */
    fastify.get<{
        Querystring: { tenantId: string; status?: string; page?: number; limit?: number };
    }>('/build/workflows', async (request, reply) => {
        const { tenantId, status, page = 1, limit = 20 } = request.query;
        const skip = (page - 1) * limit;

        const where: Prisma.BuildWorkflowWhereInput = { tenantId };
        if (status) {
            where.status = status;
        }

        const [workflows, total] = await Promise.all([
            prisma.buildWorkflow.findMany({
                where,
                skip,
                take: limit,
                orderBy: { updatedAt: 'desc' },
                include: {
                    ownerTeam: { select: { id: true, name: true } },
                    workflowServices: { select: { serviceId: true } },
                    workflowPolicies: { select: { policyId: true } },
                },
            }),
            prisma.buildWorkflow.count({ where }),
        ]);

        const response: WorkflowResponse[] = workflows.map((wf) => ({
            id: wf.id,
            tenantId: wf.tenantId,
            name: wf.name,
            slug: wf.slug,
            description: wf.description,
            status: wf.status,
            ownerTeamId: wf.ownerTeamId,
            trigger: wf.trigger as Record<string, unknown>,
            steps: wf.steps as Record<string, unknown>[],
            serviceIds: wf.workflowServices.map((ws) => ws.serviceId),
            policyIds: wf.workflowPolicies.map((wp) => wp.policyId),
            createdAt: wf.createdAt.toISOString(),
            updatedAt: wf.updatedAt.toISOString(),
        }));

        return reply.send({
            data: response,
            pagination: {
                page,
                limit,
                total,
                totalPages: Math.ceil(total / limit),
            },
        });
    });

    /**
     * GET /api/v1/build/workflows/:id
     * Get workflow by ID
     */
    fastify.get<{ Params: { id: string }; Querystring: { tenantId: string } }>(
        '/build/workflows/:id',
        async (request, reply) => {
            const { id } = request.params;
            const { tenantId } = request.query;

            const workflow = await prisma.buildWorkflow.findFirst({
                where: { id, tenantId },
                include: {
                    ownerTeam: { select: { id: true, name: true } },
                    workflowServices: {
                        include: { service: { select: { id: true, name: true } } },
                    },
                    workflowPolicies: {
                        include: { policy: { select: { id: true, name: true } } },
                    },
                },
            });

            if (!workflow) {
                return reply.status(404).send({ error: 'Workflow not found' });
            }

            const response: WorkflowResponse = {
                id: workflow.id,
                tenantId: workflow.tenantId,
                name: workflow.name,
                slug: workflow.slug,
                description: workflow.description,
                status: workflow.status,
                ownerTeamId: workflow.ownerTeamId,
                trigger: workflow.trigger as Record<string, unknown>,
                steps: workflow.steps as Record<string, unknown>[],
                serviceIds: workflow.workflowServices.map((ws) => ws.serviceId),
                policyIds: workflow.workflowPolicies.map((wp) => wp.policyId),
                createdAt: workflow.createdAt.toISOString(),
                updatedAt: workflow.updatedAt.toISOString(),
            };

            return reply.send(response);
        }
    );

    /**
     * POST /api/v1/build/workflows
     * Create new workflow
     */
    fastify.post<{ Body: WorkflowCreateBody & { tenantId: string }; Querystring: { actorUserId?: string } }>(
        '/build/workflows',
        async (request, reply) => {
            const { tenantId, name, slug, description, ownerTeamId, trigger, steps } = request.body;
            const { actorUserId = null } = request.query;

            // Check for duplicate slug
            const existing = await prisma.buildWorkflow.findUnique({
                where: { tenantId_slug: { tenantId, slug } },
            });

            if (existing) {
                return reply.status(409).send({ error: 'Workflow with this slug already exists' });
            }

            const workflow = await prisma.buildWorkflow.create({
                data: {
                    tenantId,
                    name,
                    slug,
                    description,
                    ownerTeamId,
                    trigger: trigger as Prisma.InputJsonValue,
                    steps: steps as Prisma.InputJsonValue,
                    status: 'draft',
                },
                include: {
                    ownerTeam: { select: { id: true, name: true } },
                },
            });

            await writeAuditEvent(tenantId, actorUserId, 'workflow', workflow.id, 'create', {
                name,
                slug,
                status: 'draft',
            });

            const response: WorkflowResponse = {
                id: workflow.id,
                tenantId: workflow.tenantId,
                name: workflow.name,
                slug: workflow.slug,
                description: workflow.description,
                status: workflow.status,
                ownerTeamId: workflow.ownerTeamId,
                trigger: workflow.trigger as Record<string, unknown>,
                steps: workflow.steps as Record<string, unknown>[],
                serviceIds: [],
                policyIds: [],
                createdAt: workflow.createdAt.toISOString(),
                updatedAt: workflow.updatedAt.toISOString(),
            };

            return reply.status(201).send(response);
        }
    );

    /**
     * PUT /api/v1/build/workflows/:id
     * Update workflow
     */
    fastify.put<{
        Params: { id: string };
        Body: WorkflowUpdateBody;
        Querystring: { tenantId: string; actorUserId?: string };
    }>('/build/workflows/:id', async (request, reply) => {
        const { id } = request.params;
        const { tenantId, actorUserId = null } = request.query;
        const { name, description, ownerTeamId, trigger, steps, serviceIds, policyIds } = request.body;

        const existing = await prisma.buildWorkflow.findFirst({ where: { id, tenantId } });
        if (!existing) {
            return reply.status(404).send({ error: 'Workflow not found' });
        }

        const updateData: Prisma.BuildWorkflowUpdateInput = {};
        if (name !== undefined) updateData.name = name;
        if (description !== undefined) updateData.description = description;
        if (ownerTeamId !== undefined) updateData.ownerTeam = { connect: { id: ownerTeamId } };
        if (trigger !== undefined) updateData.trigger = trigger as Prisma.InputJsonValue;
        if (steps !== undefined) updateData.steps = steps as Prisma.InputJsonValue;

        const workflow = await prisma.buildWorkflow.update({
            where: { id },
            data: updateData,
            include: {
                ownerTeam: { select: { id: true, name: true } },
                workflowServices: { select: { serviceId: true } },
                workflowPolicies: { select: { policyId: true } },
            },
        });

        // Update service associations
        if (serviceIds !== undefined) {
            await prisma.buildWorkflowService.deleteMany({ where: { workflowId: id } });
            if (serviceIds.length > 0) {
                await prisma.buildWorkflowService.createMany({
                    data: serviceIds.map((serviceId) => ({ workflowId: id, serviceId })),
                });
            }
        }

        // Update policy associations
        if (policyIds !== undefined) {
            await prisma.buildWorkflowPolicy.deleteMany({ where: { workflowId: id } });
            if (policyIds.length > 0) {
                await prisma.buildWorkflowPolicy.createMany({
                    data: policyIds.map((policyId) => ({ workflowId: id, policyId })),
                });
            }
        }

        await writeAuditEvent(tenantId, actorUserId, 'workflow', id, 'update', {
            changes: request.body,
        });

        const response: WorkflowResponse = {
            id: workflow.id,
            tenantId: workflow.tenantId,
            name: workflow.name,
            slug: workflow.slug,
            description: workflow.description,
            status: workflow.status,
            ownerTeamId: workflow.ownerTeamId,
            trigger: workflow.trigger as Record<string, unknown>,
            steps: workflow.steps as Record<string, unknown>[],
            serviceIds: serviceIds ?? workflow.workflowServices.map((ws) => ws.serviceId),
            policyIds: policyIds ?? workflow.workflowPolicies.map((wp) => wp.policyId),
            createdAt: workflow.createdAt.toISOString(),
            updatedAt: workflow.updatedAt.toISOString(),
        };

        return reply.send(response);
    });

    /**
     * POST /api/v1/build/workflows/:id/activate
     * Activate workflow
     */
    fastify.post<{ Params: { id: string }; Querystring: { tenantId: string; actorUserId?: string } }>(
        '/build/workflows/:id/activate',
        async (request, reply) => {
            const { id } = request.params;
            const { tenantId, actorUserId = null } = request.query;

            const workflow = await prisma.buildWorkflow.findFirst({ where: { id, tenantId } });
            if (!workflow) {
                return reply.status(404).send({ error: 'Workflow not found' });
            }

            if (workflow.status === 'active') {
                return reply.status(400).send({ error: 'Workflow is already active' });
            }

            // Basic validation: ensure trigger and steps are present
            const trigger = workflow.trigger as Record<string, unknown>;
            const steps = workflow.steps as Record<string, unknown>[];
            if (!trigger || !steps || steps.length === 0) {
                return reply.status(400).send({
                    error: 'Invalid workflow configuration',
                    details: ['Trigger and at least one step are required'],
                });
            }

            const updated = await prisma.buildWorkflow.update({
                where: { id, tenantId },
                data: { status: 'active' },
            });

            await writeAuditEvent(tenantId, actorUserId, 'workflow', id, 'activate', {
                previousStatus: workflow.status,
            });

            return reply.send({
                id: updated.id,
                status: updated.status,
                message: 'Workflow activated successfully',
            });
        }
    );

    // =========================================================================
    // Agent Routes
    // =========================================================================

    /**
     * GET /api/v1/build/agents
     * List all agents for the current tenant
     */
    fastify.get<{
        Querystring: { tenantId: string; status?: string; page?: number; limit?: number };
    }>('/build/agents', async (request, reply) => {
        const { tenantId, status, page = 1, limit = 20 } = request.query;
        const skip = (page - 1) * limit;

        const where: Prisma.BuildAgentWhereInput = { tenantId };
        if (status) {
            where.status = status;
        }

        const [agents, total] = await Promise.all([
            prisma.buildAgent.findMany({
                where,
                skip,
                take: limit,
                orderBy: { updatedAt: 'desc' },
                include: {
                    persona: { select: { id: true, name: true } },
                    agentServices: { select: { serviceId: true } },
                },
            }),
            prisma.buildAgent.count({ where }),
        ]);

        const response: AgentResponse[] = agents.map((agent) => ({
            id: agent.id,
            tenantId: agent.tenantId,
            name: agent.name,
            slug: agent.slug,
            description: agent.description,
            type: agent.type,
            status: agent.status,
            personaId: agent.personaId,
            tools: agent.tools as string[],
            guardrails: agent.guardrails as Record<string, unknown>,
            serviceIds: agent.agentServices.map((as) => as.serviceId),
            createdAt: agent.createdAt.toISOString(),
            updatedAt: agent.updatedAt.toISOString(),
        }));

        return reply.send({
            data: response,
            pagination: {
                page,
                limit,
                total,
                totalPages: Math.ceil(total / limit),
            },
        });
    });

    /**
     * GET /api/v1/build/agents/:id
     * Get agent by ID
     */
    fastify.get<{ Params: { id: string }; Querystring: { tenantId: string } }>(
        '/build/agents/:id',
        async (request, reply) => {
            const { id } = request.params;
            const { tenantId } = request.query;

            const agent = await prisma.buildAgent.findFirst({
                where: { id, tenantId },
                include: {
                    persona: { select: { id: true, name: true } },
                    agentServices: {
                        include: { service: { select: { id: true, name: true } } },
                    },
                },
            });

            if (!agent) {
                return reply.status(404).send({ error: 'Agent not found' });
            }

            const response: AgentResponse = {
                id: agent.id,
                tenantId: agent.tenantId,
                name: agent.name,
                slug: agent.slug,
                description: agent.description,
                type: agent.type,
                status: agent.status,
                personaId: agent.personaId,
                tools: agent.tools as string[],
                guardrails: agent.guardrails as Record<string, unknown>,
                serviceIds: agent.agentServices.map((as) => as.serviceId),
                createdAt: agent.createdAt.toISOString(),
                updatedAt: agent.updatedAt.toISOString(),
            };

            return reply.send(response);
        }
    );

    /**
     * POST /api/v1/build/agents
     * Create new agent
     */
    fastify.post<{ Body: AgentCreateBody & { tenantId: string }; Querystring: { actorUserId?: string } }>(
        '/build/agents',
        async (request, reply) => {
            const { tenantId, name, slug, description, type, personaId, tools, guardrails } = request.body;
            const { actorUserId = null } = request.query;

            // Check for duplicate slug
            const existing = await prisma.buildAgent.findUnique({
                where: { tenantId_slug: { tenantId, slug } },
            });

            if (existing) {
                return reply.status(409).send({ error: 'Agent with this slug already exists' });
            }

            const agent = await prisma.buildAgent.create({
                data: {
                    tenantId,
                    name,
                    slug,
                    description,
                    type,
                    personaId,
                    tools: tools as Prisma.InputJsonValue,
                    guardrails: guardrails as Prisma.InputJsonValue,
                    status: 'draft',
                },
                include: {
                    persona: { select: { id: true, name: true } },
                },
            });

            await writeAuditEvent(tenantId, actorUserId, 'agent', agent.id, 'create', {
                name,
                slug,
                type,
                status: 'draft',
            });

            const response: AgentResponse = {
                id: agent.id,
                tenantId: agent.tenantId,
                name: agent.name,
                slug: agent.slug,
                description: agent.description,
                type: agent.type,
                status: agent.status,
                personaId: agent.personaId,
                tools: agent.tools as string[],
                guardrails: agent.guardrails as Record<string, unknown>,
                serviceIds: [],
                createdAt: agent.createdAt.toISOString(),
                updatedAt: agent.updatedAt.toISOString(),
            };

            return reply.status(201).send(response);
        }
    );

    /**
     * PUT /api/v1/build/agents/:id
     * Update agent
     */
    fastify.put<{
        Params: { id: string };
        Body: AgentUpdateBody;
        Querystring: { tenantId: string; actorUserId?: string };
    }>('/build/agents/:id', async (request, reply) => {
        const { id } = request.params;
        const { tenantId, actorUserId = null } = request.query;
        const { name, description, type, personaId, tools, guardrails, serviceIds } = request.body;

        const existing = await prisma.buildAgent.findFirst({ where: { id, tenantId } });
        if (!existing) {
            return reply.status(404).send({ error: 'Agent not found' });
        }

        const updateData: Prisma.BuildAgentUpdateInput = {};
        if (name !== undefined) updateData.name = name;
        if (description !== undefined) updateData.description = description;
        if (type !== undefined) updateData.type = type;
        if (personaId !== undefined) updateData.persona = { connect: { id: personaId } };
        if (tools !== undefined) updateData.tools = tools as Prisma.InputJsonValue;
        if (guardrails !== undefined) updateData.guardrails = guardrails as Prisma.InputJsonValue;

        const agent = await prisma.buildAgent.update({
            where: { id },
            data: updateData,
            include: {
                persona: { select: { id: true, name: true } },
                agentServices: { select: { serviceId: true } },
            },
        });

        // Update service associations
        if (serviceIds !== undefined) {
            await prisma.buildAgentService.deleteMany({ where: { agentId: id } });
            if (serviceIds.length > 0) {
                await prisma.buildAgentService.createMany({
                    data: serviceIds.map((serviceId) => ({ agentId: id, serviceId })),
                });
            }
        }

        await writeAuditEvent(tenantId, actorUserId, 'agent', id, 'update', {
            changes: request.body,
        });

        const response: AgentResponse = {
            id: agent.id,
            tenantId: agent.tenantId,
            name: agent.name,
            slug: agent.slug,
            description: agent.description,
            type: agent.type,
            status: agent.status,
            personaId: agent.personaId,
            tools: agent.tools as string[],
            guardrails: agent.guardrails as Record<string, unknown>,
            serviceIds: serviceIds ?? agent.agentServices.map((as) => as.serviceId),
            createdAt: agent.createdAt.toISOString(),
            updatedAt: agent.updatedAt.toISOString(),
        };

        return reply.send(response);
    });

    /**
     * POST /api/v1/build/agents/:id/activate
     * Activate agent
     */
    fastify.post<{ Params: { id: string }; Querystring: { tenantId: string; actorUserId?: string } }>(
        '/build/agents/:id/activate',
        async (request, reply) => {
            const { id } = request.params;
            const { tenantId, actorUserId = null } = request.query;

            const agent = await prisma.buildAgent.findUnique({ where: { id, tenantId } });
            if (!agent) {
                return reply.status(404).send({ error: 'Agent not found' });
            }

            if (agent.status === 'active') {
                return reply.status(400).send({ error: 'Agent is already active' });
            }

            // Validate that AI guardrails are configured
            const aiSettings = await prisma.platformSettings.findUnique({
                where: { tenantId_category: { tenantId, category: 'ai-agents' } },
            });

            if (!aiSettings) {
                return reply.status(400).send({
                    error: 'AI guardrails not configured',
                    details: ['Please configure AI & Agents settings in Admin before activating agents'],
                });
            }

            const updated = await prisma.buildAgent.update({
                where: { id, tenantId },
                data: { status: 'active' },
            });

            await writeAuditEvent(tenantId, actorUserId, 'agent', id, 'activate', {
                previousStatus: agent.status,
            });

            return reply.send({
                id: updated.id,
                status: updated.status,
                message: 'Agent activated successfully',
            });
        }
    );

    // =========================================================================
    // Simulator Routes
    // =========================================================================

    /**
     * POST /api/v1/build/simulator/run
     * Run simulation
     */
    fastify.post<{ Body: SimulateRequest }>('/build/simulator/run', async (request, reply) => {
        const { workflowId, agentId, tenantId, environment, eventPayload } = request.body;

        // Validate inputs
        if (!workflowId && !agentId) {
            return reply.status(400).send({
                error: 'Either workflowId or agentId must be provided',
            });
        }

        // For now, create a mock simulation response
        // TODO: Integrate with agentic-event-processor simulation APIs
        const simulationId = `sim-${Date.now()}`;
        const startTime = Date.now();

        const trace: SimulateResponse['trace'] = [];
        const policyBlocks: SimulateResponse['policyBlocks'] = [];

        if (workflowId) {
            const workflow = await prisma.buildWorkflow.findFirst({
                where: { id: workflowId, tenantId },
                include: {
                    workflowPolicies: {
                        include: { policy: { select: { id: true, name: true, status: true } } },
                    },
                },
            });

            if (!workflow) {
                return reply.status(404).send({ error: 'Workflow not found' });
            }

            const steps = workflow.steps as Record<string, unknown>[];
            for (const [index, step] of steps.entries()) {
                trace.push({
                    step: `Step ${index + 1}`,
                    timestamp: new Date(startTime + index * 1000).toISOString(),
                    action: (step.action as string) || 'unknown',
                    result: { status: 'simulated', step },
                });
            }

            // Check policies
            for (const wp of workflow.workflowPolicies) {
                const policy = wp.policy;
                if (policy.status === 'active') {
                    policyBlocks.push({
                        policyId: policy.id,
                        policyName: policy.name,
                        reason: 'Simulated policy evaluation - approval required',
                    });
                }
            }
        }

        if (agentId) {
            const agent = await prisma.buildAgent.findFirst({
                where: { id: agentId, tenantId },
            });

            if (!agent) {
                return reply.status(404).send({ error: 'Agent not found' });
            }

            trace.push({
                step: 'Agent Analysis',
                timestamp: new Date(startTime).toISOString(),
                action: 'analyze_event',
                result: {
                    status: 'simulated',
                    agentType: agent.type,
                    eventAnalysis: eventPayload,
                },
            });

            trace.push({
                step: 'Agent Recommendation',
                timestamp: new Date(startTime + 1000).toISOString(),
                action: 'generate_recommendation',
                result: {
                    status: 'simulated',
                    recommendation: 'Simulated agent recommendation based on guardrails',
                },
            });
        }

        const duration = Date.now() - startTime;

        const response: SimulateResponse = {
            simulationId,
            status: 'success',
            trace,
            policyBlocks,
            duration,
        };

        return reply.send(response);
    });
}
