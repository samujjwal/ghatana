/**
 * Organization API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for virtual organization management with database integration
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/org/departments - List departments
 * - POST /api/v1/org/departments - Create department
 * - GET /api/v1/org/departments/:id - Get department details
 * - GET /api/v1/org/agents - List agents
 * - GET /api/v1/org/agents/:id - Get agent details
 * - POST /api/v1/org/departments/:id/agents - Add/Create agent in department
 * - PUT /api/v1/org/agents/:id - Update agent
 * - DELETE /api/v1/org/agents/:id - Delete agent
 * - POST /api/v1/org/hierarchy/move - Move agent between departments
 * - GET /api/v1/org/hierarchy - Get organization hierarchy
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { Prisma } from '../../generated/prisma-client/index.js';
import { z } from 'zod';
import { prisma } from '../db/client.js';

const organizationQuerySchema = z.object({
    organizationId: z.string().min(1).optional(),
});

const createDepartmentSchema = z.object({
    name: z.string().min(1),
    type: z.string().min(1),
    description: z.string().min(1).optional(),
    organizationId: z.string().min(1).optional(),
    status: z.string().min(1).optional(),
});

const addDepartmentAgentSchema = z.object({
    id: z.string().min(1).optional(),
    name: z.string().min(1).optional(),
    role: z.string().min(1).optional(),
    status: z.enum(['ONLINE', 'OFFLINE', 'BUSY']).optional(),
    capabilities: z.array(z.string()).optional(),
    configuration: z.record(z.string(), z.unknown()).optional(),
});

const updateAgentSchema = z.object({
    name: z.string().min(1).optional(),
    role: z.string().min(1).optional(),
    status: z.enum(['ONLINE', 'OFFLINE', 'BUSY']).optional(),
    capabilities: z.array(z.string()).optional(),
    configuration: z.record(z.string(), z.unknown()).optional(),
});

const moveAgentSchema = z.object({
    agentId: z.string().min(1),
    fromDeptId: z.string().min(1),
    toDeptId: z.string().min(1),
});

const generatedAgentSchema = z.object({
    name: z.string().min(1),
    role: z.string().min(1),
    description: z.string().min(1).optional(),
});

const generatedDepartmentSchema = z.object({
    name: z.string().min(1),
    type: z.string().min(1),
    description: z.string().min(1),
    agents: z.array(generatedAgentSchema).optional(),
});

const generateGenesisSchema = z.object({
    name: z.string().min(1),
    vision: z.string().min(1),
    template: z.string().min(1),
    departments: z.array(generatedDepartmentSchema).optional(),
    options: z.record(z.string(), z.unknown()).optional(),
});

const materializeGenesisSchema = z.object({
    name: z.string().min(1),
    namespace: z.string().min(1),
    vision: z.string().min(1),
    template: z.string().min(1).optional(),
    norms: z.array(z.string()).optional(),
    departments: z.array(generatedDepartmentSchema),
});

type OrganizationQuery = z.infer<typeof organizationQuerySchema>;
type CreateDepartmentBody = z.infer<typeof createDepartmentSchema>;
type AddDepartmentAgentBody = z.infer<typeof addDepartmentAgentSchema>;
type UpdateAgentBody = z.infer<typeof updateAgentSchema>;
type MoveAgentBody = z.infer<typeof moveAgentSchema>;
type GenerateGenesisBody = z.infer<typeof generateGenesisSchema>;
type MaterializeGenesisBody = z.infer<typeof materializeGenesisSchema>;

type PrismaErrorLike = {
    code?: string;
};

function getValidationMessage(error: z.ZodError): string {
    return error.issues
        .map(issue => `${issue.path.join('.') || 'request'}: ${issue.message}`)
        .join('; ');
}

function parsePayload<T>(schema: z.ZodType<T>, payload: unknown, reply: FastifyReply): T | null {
    const parsed = schema.safeParse(payload);
    if (!parsed.success) {
        void reply.status(400).send({
            error: getValidationMessage(parsed.error),
            success: false,
        });
        return null;
    }
    return parsed.data;
}

function getErrorCode(error: unknown): string | undefined {
    if (typeof error === 'object' && error !== null && 'code' in error) {
        return (error as PrismaErrorLike).code;
    }
    return undefined;
}

function toInputJsonValue(value: Record<string, unknown> | string[]): Prisma.InputJsonValue {
    return value as Prisma.InputJsonValue;
}

/**
 * Helper: Get or create default organization
 */
async function getDefaultOrganization() {
    let org = await prisma.organization.findFirst();
    if (!org) {
        org = await prisma.organization.create({
            data: {
                name: 'Default Organization',
                namespace: 'default-org',
                displayName: 'Default Org',
                description: 'Default organization for departments and agents',
                structure: { type: 'hierarchical', maxDepth: 4 },
                settings: { defaultTimezone: 'UTC', events: {}, hitl: {}, ai: {} },
            },
        });
    }
    return org;
}

/**
 * Register organization routes
 */
export default async function orgRoutes(fastify: FastifyInstance): Promise<void> {
    // =========================================================================
    // DEPARTMENTS
    // =========================================================================

    /**
     * GET /api/v1/org/departments
     * List all departments
     */
    fastify.get<{ Querystring: OrganizationQuery }>('/departments', async (request: FastifyRequest<{ Querystring: OrganizationQuery }>, reply: FastifyReply) => {
        try {
            const query = parsePayload(organizationQuerySchema, request.query, reply);
            if (!query) {
                return;
            }
            const { organizationId } = query;
            let resolvedOrgId = organizationId;

            if (!resolvedOrgId) {
                // Get the first/default organization
                const org = await prisma.organization.findFirst();
                if (!org) {
                    return reply.send({
                        data: [],
                        success: true,
                        timestamp: new Date().toISOString(),
                    });
                }
                resolvedOrgId = org.id;
            }

            const departments = await prisma.department.findMany({
                where: { organizationId: resolvedOrgId },
                include: {
                    agents: {
                        select: { id: true, name: true, role: true, status: true },
                    },
                },
                orderBy: { createdAt: 'asc' },
            });

            const result = departments.map(dept => ({
                id: dept.id,
                name: dept.name,
                type: dept.type,
                description: dept.description,
                status: dept.status,
                agentCount: dept.agents.length,
                agents: dept.agents,
                createdAt: dept.createdAt.toISOString(),
                updatedAt: dept.updatedAt.toISOString(),
            }));

            return reply.send({
                data: result,
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error fetching departments');
            return reply.status(500).send({
                error: 'Failed to fetch departments',
                success: false,
            });
        }
    });

    /**
     * POST /api/v1/org/departments
     * Create a new department
     */
    fastify.post<{ Body: CreateDepartmentBody }>('/departments', async (request: FastifyRequest<{ Body: CreateDepartmentBody }>, reply: FastifyReply) => {
        try {
            const body = parsePayload(createDepartmentSchema, request.body, reply);
            if (!body) {
                return;
            }
            const { name, type, description, organizationId, status = 'active' } = body;

            fastify.log.info({ name, type, organizationId }, 'Creating department');

            // Validate input
            if (!name || !type) {
                return reply.status(400).send({
                    error: 'Missing required fields: name, type',
                    success: false,
                });
            }

            // Resolve organization ID - use provided or get default
            let resolvedOrgId = organizationId;
            if (!resolvedOrgId) {
                // Get the first/default organization or create one
                let org = await prisma.organization.findFirst();
                if (!org) {
                    org = await prisma.organization.create({
                        data: {
                            name: 'Default Organization',
                            namespace: 'default-org',
                            displayName: 'Default Org',
                            description: 'Default organization for departments',
                            structure: { type: 'hierarchical', maxDepth: 4 },
                            settings: { defaultTimezone: 'UTC', events: {}, hitl: {}, ai: {} },
                        },
                    });
                }
                resolvedOrgId = org.id;
            } else {
                // Verify the organization exists
                const org = await prisma.organization.findUnique({
                    where: { id: organizationId },
                });
                if (!org) {
                    return reply.status(404).send({
                        error: `Organization with ID ${organizationId} not found`,
                        success: false,
                    });
                }
            }

            // Check for duplicate name within organization
            const exists = await prisma.department.findFirst({
                where: {
                    organizationId: resolvedOrgId,
                    name: {
                        equals: name,
                        mode: 'insensitive',
                    },
                },
            });

            if (exists) {
                return reply.status(409).send({
                    error: 'Department with this name already exists',
                    success: false,
                });
            }

            // Create new department
            const newDepartment = await prisma.department.create({
                data: {
                    organizationId: resolvedOrgId,
                    name,
                    type,
                    description: description || `${name} department`,
                    status,
                    configuration: {},
                },
                include: {
                    agents: {
                        select: { id: true, name: true, role: true, status: true },
                    },
                },
            });

            return reply.status(201).send({
                data: {
                    id: newDepartment.id,
                    name: newDepartment.name,
                    type: newDepartment.type,
                    description: newDepartment.description,
                    status: newDepartment.status,
                    agentCount: 0,
                    agents: [],
                    createdAt: newDepartment.createdAt.toISOString(),
                    updatedAt: newDepartment.updatedAt.toISOString(),
                },
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error creating department');
            return reply.status(500).send({
                error: 'Failed to create department',
                success: false,
            });
        }
    });

    /**
     * GET /api/v1/org/departments/:id
     * Get department details
     */
    fastify.get<{ Params: { id: string } }>(
        '/departments/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const { id } = request.params;
                fastify.log.debug({ id }, 'Fetching department');

                const department = await prisma.department.findUnique({
                    where: { id },
                    include: {
                        agents: {
                            select: { id: true, name: true, role: true, status: true, capabilities: true },
                        },
                        workflows: {
                            select: { id: true, name: true, status: true },
                        },
                        teams: {
                            select: { id: true, name: true },
                        },
                    },
                });

                if (!department) {
                    return reply.status(404).send({ error: 'Department not found', success: false });
                }

                return reply.send({
                    data: {
                        id: department.id,
                        name: department.name,
                        type: department.type,
                        description: department.description,
                        status: department.status,
                        agentCount: department.agents.length,
                        agents: department.agents,
                        workflows: department.workflows,
                        teams: department.teams,
                        configuration: department.configuration,
                        createdAt: department.createdAt.toISOString(),
                        updatedAt: department.updatedAt.toISOString(),
                    },
                    success: true,
                    timestamp: new Date().toISOString(),
                });
            } catch (error) {
                fastify.log.error({ err: error }, 'Error fetching department');
                return reply.status(500).send({
                    error: 'Failed to fetch department',
                    success: false,
                });
            }
        }
    );

    // =========================================================================
    // AGENTS
    // =========================================================================

    /**
     * GET /api/v1/org/agents
     * List all agents
     */
    fastify.get<{ Querystring: OrganizationQuery }>('/agents', async (request: FastifyRequest<{ Querystring: OrganizationQuery }>, reply: FastifyReply) => {
        try {
            const query = parsePayload(organizationQuerySchema, request.query, reply);
            if (!query) {
                return;
            }
            const { organizationId } = query;
            const org = organizationId ?
                await prisma.organization.findUnique({ where: { id: organizationId } }) :
                await getDefaultOrganization();

            if (!org) {
                return reply.send({
                    data: [],
                    success: true,
                    timestamp: new Date().toISOString(),
                });
            }

            const agents = await prisma.agent.findMany({
                where: { organizationId: org.id },
                include: {
                    department: {
                        select: { id: true, name: true, type: true },
                    },
                },
                orderBy: { createdAt: 'asc' },
            });

            const result = agents.map(agent => ({
                id: agent.id,
                organizationId: agent.organizationId,
                departmentId: agent.departmentId,
                name: agent.name,
                role: agent.role,
                status: agent.status,
                capabilities: agent.capabilities,
                configuration: agent.configuration,
                department: agent.department,
                createdAt: agent.createdAt.toISOString(),
                updatedAt: agent.updatedAt.toISOString(),
            }));

            return reply.send({
                data: result,
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error fetching agents');
            return reply.status(500).send({
                error: 'Failed to fetch agents',
                success: false,
            });
        }
    });

    /**
     * GET /api/v1/org/agents/:id
     * Get agent details
     */
    fastify.get<{ Params: { id: string } }>(
        '/agents/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const { id } = request.params;
                fastify.log.debug({ id }, 'Fetching agent');

                const agent = await prisma.agent.findUnique({
                    where: { id },
                    include: {
                        department: {
                            select: { id: true, name: true, type: true },
                        },
                    },
                });

                if (!agent) {
                    return reply.status(404).send({ error: 'Agent not found', success: false });
                }

                return reply.send({
                    data: {
                        id: agent.id,
                        organizationId: agent.organizationId,
                        departmentId: agent.departmentId,
                        name: agent.name,
                        role: agent.role,
                        status: agent.status,
                        capabilities: agent.capabilities,
                        configuration: agent.configuration,
                        department: agent.department,
                        createdAt: agent.createdAt.toISOString(),
                        updatedAt: agent.updatedAt.toISOString(),
                    },
                    success: true,
                    timestamp: new Date().toISOString(),
                });
            } catch (error) {
                fastify.log.error({ err: error }, 'Error fetching agent');
                return reply.status(500).send({
                    error: 'Failed to fetch agent',
                    success: false,
                });
            }
        }
    );

    /**
     * POST /api/v1/org/departments/:id/agents
     * Add agent to department (reassign existing or create new)
     */
    fastify.post<{
        Params: { id: string };
        Body: AddDepartmentAgentBody;
    }>('/departments/:id/agents', async (request: FastifyRequest<{ Params: { id: string }; Body: AddDepartmentAgentBody }>, reply: FastifyReply) => {
        try {
            const { id: departmentId } = request.params;
            const agentData = parsePayload(addDepartmentAgentSchema, request.body, reply);
            if (!agentData) {
                return;
            }

            fastify.log.info({ departmentId, agentData }, 'Adding agent to department');

            // Validate department exists
            const department = await prisma.department.findUnique({
                where: { id: departmentId },
            });

            if (!department) {
                return reply.status(404).send({
                    error: 'Department not found',
                    success: false,
                });
            }

            // Ensure organization exists (for safety, though department FK ensures it)
            // If department has no organizationId (shouldn't happen with FKs), use default
            let orgId = department.organizationId;
            if (!orgId) {
                const defaultOrg = await getDefaultOrganization();
                orgId = defaultOrg.id;
            }

            // Check for duplicate name within organization
            const existingAgent = await prisma.agent.findFirst({
                where: {
                    organizationId: orgId,
                    name: {
                        equals: agentData.name,
                        mode: 'insensitive',
                    },
                },
            });

            if (existingAgent) {
                return reply.status(409).send({
                    error: 'Agent with this name already exists in the organization',
                    success: false,
                });
            }

            // Check if agent is being reassigned or created
            if (agentData.id) {
                // Reassigning existing agent
                const agent = await prisma.agent.update({
                    where: { id: agentData.id },
                    data: {
                        departmentId,
                        updatedAt: new Date(),
                    },
                    include: {
                        department: {
                            select: { id: true, name: true, type: true },
                        },
                    },
                });

                return reply.send({
                    data: {
                        id: agent.id,
                        organizationId: agent.organizationId,
                        departmentId: agent.departmentId,
                        name: agent.name,
                        role: agent.role,
                        status: agent.status,
                        capabilities: agent.capabilities,
                        configuration: agent.configuration,
                        department: agent.department,
                        createdAt: agent.createdAt.toISOString(),
                        updatedAt: agent.updatedAt.toISOString(),
                    },
                    success: true,
                    timestamp: new Date().toISOString(),
                });
            } else {
                // Creating new agent
                if (!agentData.name || !agentData.role) {
                    return reply.status(400).send({
                        error: 'Missing required fields: name, role',
                        success: false,
                    });
                }

                const newAgent = await prisma.agent.create({
                    data: {
                        organizationId: orgId,
                        departmentId,
                        name: agentData.name,
                        role: agentData.role,
                        status: agentData.status || 'ONLINE',
                        capabilities: toInputJsonValue(agentData.capabilities || []),
                        configuration: toInputJsonValue(agentData.configuration || {}),
                    },
                });

                const departmentSummary = await prisma.department.findUnique({
                    where: { id: newAgent.departmentId },
                    select: { id: true, name: true, type: true },
                });

                return reply.status(201).send({
                    data: {
                        id: newAgent.id,
                        organizationId: newAgent.organizationId,
                        departmentId: newAgent.departmentId,
                        name: newAgent.name,
                        role: newAgent.role,
                        status: newAgent.status,
                        capabilities: newAgent.capabilities,
                        configuration: newAgent.configuration,
                        department: departmentSummary,
                        createdAt: newAgent.createdAt.toISOString(),
                        updatedAt: newAgent.updatedAt.toISOString(),
                    },
                    success: true,
                    timestamp: new Date().toISOString(),
                });
            }
        } catch (error) {
            fastify.log.error({ err: error, body: request.body, params: request.params }, 'Error adding agent to department');

            // Handle Prisma unique constraint violations
            if (getErrorCode(error) === 'P2002') {
                return reply.status(409).send({
                    error: 'Agent with this name already exists',
                    success: false,
                });
            }

            return reply.status(500).send({
                error: 'Failed to fetch agent',
                details: (error as Error).message,
                success: false,
            });
        }
    });

    /**
     * PUT /api/v1/org/agents/:id
     * Update agent
     */
    fastify.put<{
        Params: { id: string };
        Body: UpdateAgentBody;
    }>('/agents/:id', async (request: FastifyRequest<{ Params: { id: string }; Body: UpdateAgentBody }>, reply: FastifyReply) => {
        try {
            const { id } = request.params;
            const updates = parsePayload(updateAgentSchema, request.body, reply);
            if (!updates) {
                return;
            }

            fastify.log.info({ id, updates }, 'Updating agent');

            const updateData: {
                name?: string;
                role?: string;
                status?: 'ONLINE' | 'OFFLINE' | 'BUSY';
                capabilities?: Prisma.InputJsonValue;
                configuration?: Prisma.InputJsonValue;
                updatedAt: Date;
            } = {
                updatedAt: new Date(),
            };
            if (updates.name !== undefined) updateData.name = updates.name;
            if (updates.role !== undefined) updateData.role = updates.role;
            if (updates.status !== undefined) updateData.status = updates.status;
            if (updates.capabilities !== undefined) updateData.capabilities = toInputJsonValue(updates.capabilities);
            if (updates.configuration !== undefined) {
                // Merge with existing configuration
                const existing = await prisma.agent.findUnique({
                    where: { id },
                    select: { configuration: true },
                });
                updateData.configuration = toInputJsonValue({
                    ...(existing?.configuration as Record<string, unknown> || {}),
                    ...updates.configuration,
                });
            }

            const agent = await prisma.agent.update({
                where: { id },
                data: updateData,
            });

            const departmentSummary = await prisma.department.findUnique({
                where: { id: agent.departmentId },
                select: { id: true, name: true, type: true },
            });

            return reply.send({
                data: {
                    id: agent.id,
                    organizationId: agent.organizationId,
                    departmentId: agent.departmentId,
                    name: agent.name,
                    role: agent.role,
                    status: agent.status,
                    capabilities: agent.capabilities,
                    configuration: agent.configuration,
                    department: departmentSummary,
                    createdAt: agent.createdAt.toISOString(),
                    updatedAt: agent.updatedAt.toISOString(),
                },
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error: unknown) {
            fastify.log.error({ err: error }, 'Error updating agent');
            if (getErrorCode(error) === 'P2025') {
                return reply.status(404).send({
                    error: 'Agent not found',
                    success: false,
                });
            }
            return reply.status(500).send({
                error: 'Failed to update agent',
                success: false,
            });
        }
    });

    /**
     * DELETE /api/v1/org/agents/:id
     * Delete agent
     */
    fastify.delete<{ Params: { id: string } }>(
        '/agents/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            try {
                const { id } = request.params;
                fastify.log.info({ id }, 'Deleting agent');

                const agent = await prisma.agent.delete({
                    where: { id },
                    include: {
                        department: {
                            select: { id: true, name: true, type: true },
                        },
                    },
                });

                return reply.send({
                    data: {
                        id: agent.id,
                        name: agent.name,
                        departmentId: agent.departmentId,
                    },
                    success: true,
                    timestamp: new Date().toISOString(),
                });
            } catch (error: unknown) {
                fastify.log.error({ err: error }, 'Error deleting agent');
                if (getErrorCode(error) === 'P2025') {
                    return reply.status(404).send({
                        error: 'Agent not found',
                        success: false,
                    });
                }
                return reply.status(500).send({
                    error: 'Failed to delete agent',
                    success: false,
                });
            }
        }
    );

    // =========================================================================
    // HIERARCHY
    // =========================================================================

    /**
     * GET /api/v1/org/hierarchy
     * Get organization hierarchy
     */
    fastify.get<{ Querystring: OrganizationQuery }>('/hierarchy', async (request: FastifyRequest<{ Querystring: OrganizationQuery }>, reply: FastifyReply) => {
        try {
            const query = parsePayload(organizationQuerySchema, request.query, reply);
            if (!query) {
                return;
            }
            const { organizationId } = query;
            const org = organizationId ?
                await prisma.organization.findUnique({ where: { id: organizationId } }) :
                await getDefaultOrganization();

            if (!org) {
                return reply.status(404).send({ error: 'Organization not found', success: false });
            }

            const organization = await prisma.organization.findUnique({
                where: { id: org.id },
                include: {
                    departments: {
                        include: {
                            agents: {
                                select: { id: true, name: true, role: true, status: true },
                            },
                        },
                        orderBy: { createdAt: 'asc' },
                    },
                },
            });

            if (!organization) {
                return reply.status(404).send({ error: 'Organization not found', success: false });
            }

            const depts = organization.departments.map(d => ({
                id: d.id,
                name: d.name,
                type: d.type,
                description: d.description,
                status: d.status,
                agentCount: d.agents.length,
                agents: d.agents,
            }));

            return reply.send({
                data: {
                    organization: {
                        id: organization.id,
                        name: organization.name,
                        namespace: organization.namespace,
                    },
                    departments: depts,
                },
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error fetching hierarchy');
            return reply.status(500).send({ error: 'Failed to fetch hierarchy', success: false });
        }
    });

    /**
     * POST /api/v1/org/hierarchy/move
     * Move agent between departments
     */
    fastify.post<{ Body: MoveAgentBody }>('/hierarchy/move', async (request: FastifyRequest<{ Body: MoveAgentBody }>, reply: FastifyReply) => {
        try {
            const body = parsePayload(moveAgentSchema, request.body, reply);
            if (!body) {
                return;
            }
            const { agentId, fromDeptId, toDeptId } = body;

            fastify.log.info({ agentId, fromDeptId, toDeptId }, 'Moving agent between departments');

            // Validate agent exists and is in fromDept
            const agent = await prisma.agent.findUnique({
                where: { id: agentId },
            });

            if (!agent) {
                return reply.status(404).send({
                    error: 'Agent not found',
                    success: false,
                });
            }

            if (agent.departmentId !== fromDeptId) {
                return reply.status(400).send({
                    error: 'Agent is not in the source department',
                    success: false,
                });
            }

            // Validate target department exists
            const toDept = await prisma.department.findUnique({
                where: { id: toDeptId },
            });

            if (!toDept) {
                return reply.status(404).send({
                    error: 'Target department not found',
                    success: false,
                });
            }

            // Move agent
            const updatedAgent = await prisma.agent.update({
                where: { id: agentId },
                data: {
                    departmentId: toDeptId,
                    updatedAt: new Date(),
                },
                include: {
                    department: {
                        select: { id: true, name: true, type: true },
                    },
                },
            });

            return reply.send({
                data: {
                    id: updatedAgent.id,
                    organizationId: updatedAgent.organizationId,
                    departmentId: updatedAgent.departmentId,
                    name: updatedAgent.name,
                    role: updatedAgent.role,
                    status: updatedAgent.status,
                    capabilities: updatedAgent.capabilities,
                    configuration: updatedAgent.configuration,
                    department: updatedAgent.department,
                    createdAt: updatedAgent.createdAt.toISOString(),
                    updatedAt: updatedAgent.updatedAt.toISOString(),
                },
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error moving agent');
            return reply.status(500).send({
                error: 'Failed to move agent',
                success: false,
            });
        }
    });

    // =========================================================================
    // LEGACY/COMPATIBILITY ENDPOINTS (Services, Config, Graph)
    // =========================================================================

    /**
     * GET /api/v1/org/services
     * List all services (placeholder - not yet implemented in DB)
     */
    fastify.get('/services', async (_request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching services');
        return reply.send({
            data: [],
            success: true,
            message: 'Services endpoint not yet implemented',
            timestamp: new Date().toISOString(),
        });
    });

    /**
     * GET /api/v1/org/config
     * Get organization configuration
     */
    fastify.get<{ Querystring: OrganizationQuery }>('/config', async (request: FastifyRequest<{ Querystring: OrganizationQuery }>, reply: FastifyReply) => {
        try {
            const query = parsePayload(organizationQuerySchema, request.query, reply);
            if (!query) {
                return;
            }
            const { organizationId } = query;
            const org = organizationId ?
                await prisma.organization.findUnique({ where: { id: organizationId } }) :
                await getDefaultOrganization();

            if (!org) {
                return reply.status(404).send({ error: 'Organization not found', success: false });
            }

            const organization = await prisma.organization.findUnique({
                where: { id: org.id },
                include: {
                    _count: {
                        select: {
                            departments: true,
                            agents: true,
                        },
                    },
                },
            });

            if (!organization) {
                return reply.status(404).send({ error: 'Organization not found', success: false });
            }

            return reply.send({
                data: {
                    id: organization.id,
                    name: organization.name,
                    namespace: organization.namespace,
                    departments: organization._count.departments,
                    agents: organization._count.agents,
                },
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error fetching config');
            return reply.status(500).send({
                error: 'Failed to fetch organization config',
                success: false,
            });
        }
    });

    /**
     * GET /api/v1/org/graph
     * Get organization graph for visualization
     */
    fastify.get<{ Querystring: OrganizationQuery }>('/graph', async (request: FastifyRequest<{ Querystring: OrganizationQuery }>, reply: FastifyReply) => {
        try {
            const query = parsePayload(organizationQuerySchema, request.query, reply);
            if (!query) {
                return;
            }
            const { organizationId } = query;
            const org = organizationId ?
                await prisma.organization.findUnique({ where: { id: organizationId } }) :
                await getDefaultOrganization();

            if (!org) {
                return reply.status(404).send({ error: 'Organization not found', success: false });
            }

            const departments = await prisma.department.findMany({
                where: { organizationId: org.id },
                include: {
                    agents: {
                        select: { id: true, name: true, role: true, status: true },
                    },
                },
            });

            const nodes = [
                ...departments.map(d => ({
                    id: d.id,
                    type: 'department',
                    label: d.name,
                    data: {
                        id: d.id,
                        name: d.name,
                        type: d.type,
                        description: d.description,
                        agentCount: d.agents.length,
                    },
                })),
                ...departments.flatMap(d =>
                    d.agents.map(a => ({
                        id: a.id,
                        type: 'agent',
                        label: a.name,
                        data: a,
                    }))
                ),
            ];

            const edges = departments.flatMap(d =>
                d.agents.map(a => ({
                    id: `edge-${d.id}-${a.id}`,
                    source: d.id,
                    target: a.id,
                    type: 'contains',
                }))
            );

            return reply.send({
                data: { nodes, edges },
                success: true,
                timestamp: new Date().toISOString(),
            });
        } catch (error) {
            fastify.log.error({ err: error }, 'Error fetching graph');
            return reply.status(500).send({
                error: 'Failed to fetch organization graph',
                success: false,
            });
        }
    });

    // =========================================================================
    // GENESIS (AI-Powered Creation)
    // =========================================================================

    /**
     * POST /api/v1/org/genesis/generate
     * Generate organization structure proposal based on vision and template
     */
    fastify.post<{ Body: GenerateGenesisBody }>('/genesis/generate', async (request: FastifyRequest<{ Body: GenerateGenesisBody }>, reply: FastifyReply) => {
        try {
            const body = parsePayload(generateGenesisSchema, request.body, reply);
            if (!body) {
                return;
            }
            const { name, vision, template, departments: requestedDepts, options } = body;

            fastify.log.info({ name, template }, 'Generating organization proposal');

            // 1. Deterministic Base: Select template structure
            let proposedDepartments: Array<z.infer<typeof generatedDepartmentSchema>> = [];

            if (template === 'startup') {
                proposedDepartments = [
                    {
                        name: 'Engineering',
                        type: 'ENGINEERING',
                        description: 'Core product development team',
                        agents: [
                            { name: 'Tech Lead', role: 'lead', description: 'Technical leadership and architecture' },
                            { name: 'Full Stack Engineer', role: 'engineer', description: 'Feature development' },
                            { name: 'Frontend Engineer', role: 'engineer', description: 'UI/UX implementation' }
                        ]
                    },
                    {
                        name: 'Product',
                        type: 'PRODUCT',
                        description: 'Product strategy and roadmap',
                        agents: [
                            { name: 'Product Manager', role: 'manager', description: 'Product definition and prioritization' }
                        ]
                    }
                ];
            } else if (template === 'scaleup') {
                proposedDepartments = [
                    {
                        name: 'Engineering',
                        type: 'ENGINEERING',
                        description: 'Core product development',
                        agents: [
                            { name: 'VP of Engineering', role: 'executive', description: 'Engineering strategy' },
                            { name: 'Senior Engineer', role: 'lead', description: 'System architecture' },
                            { name: 'Backend Engineer', role: 'engineer', description: 'API and data services' },
                            { name: 'Frontend Engineer', role: 'engineer', description: 'Client applications' }
                        ]
                    },
                    {
                        name: 'Product',
                        type: 'PRODUCT',
                        description: 'Product management',
                        agents: [
                            { name: 'Product Director', role: 'director', description: 'Product portfolio management' },
                            { name: 'Product Owner', role: 'manager', description: 'Team backlog management' }
                        ]
                    },
                    {
                        name: 'QA',
                        type: 'QA',
                        description: 'Quality assurance and testing',
                        agents: [
                            { name: 'QA Lead', role: 'lead', description: 'Test strategy' },
                            { name: 'Test Automation Engineer', role: 'engineer', description: 'Automated test suites' }
                        ]
                    },
                    {
                        name: 'DevOps',
                        type: 'DEVOPS',
                        description: 'Infrastructure and operations',
                        agents: [
                            { name: 'SRE', role: 'engineer', description: 'Reliability and uptime' }
                        ]
                    }
                ];
            } else {
                // Enterprise fallback
                proposedDepartments = [
                    {
                        name: 'Engineering',
                        type: 'ENGINEERING',
                        description: 'Software engineering division',
                        agents: [
                            { name: 'CTO', role: 'executive', description: 'Technical vision' },
                            { name: 'Engineering Manager', role: 'manager', description: 'Team management' },
                            { name: 'Principal Engineer', role: 'lead', description: 'Technical excellence' },
                            { name: 'Senior Developer', role: 'engineer', description: 'Core development' },
                            { name: 'Developer', role: 'engineer', description: 'Feature implementation' }
                        ]
                    },
                    {
                        name: 'Product',
                        type: 'PRODUCT',
                        description: 'Product management division',
                        agents: [
                            { name: 'CPO', role: 'executive', description: 'Product vision' },
                            { name: 'Product Manager', role: 'manager', description: 'Product strategy' }
                        ]
                    },
                    {
                        name: 'Security',
                        type: 'SECURITY',
                        description: 'Information security and compliance',
                        agents: [
                            { name: 'CISO', role: 'executive', description: 'Security strategy' },
                            { name: 'Security Engineer', role: 'engineer', description: 'Security implementation' }
                        ]
                    },
                    {
                        name: 'Operations',
                        type: 'OPERATIONS',
                        description: 'Business operations',
                        agents: [
                            { name: 'COO', role: 'executive', description: 'Operational excellence' }
                        ]
                    }
                ];
            }

            // 2. "AI" Enrichment (Simulated): Adapt based on vision
            // In a real implementation, this would call an LLM
            const visionKeywords = vision.toLowerCase().split(' ');

            if (visionKeywords.includes('mobile') || visionKeywords.includes('app')) {
                const engDept = proposedDepartments.find(d => d.type === 'ENGINEERING');
                if (engDept?.agents) {
                    engDept.agents.push({ name: 'Mobile Engineer', role: 'engineer', description: 'iOS/Android development' });
                }
            }

            if (visionKeywords.includes('data') || visionKeywords.includes('ai') || visionKeywords.includes('ml')) {
                proposedDepartments.push({
                    name: 'Data & AI',
                    type: 'DATA',
                    description: 'Data science and AI initiatives',
                    agents: [
                        { name: 'Data Scientist', role: 'engineer', description: 'Model development' },
                        { name: 'ML Engineer', role: 'engineer', description: 'ML infrastructure' }
                    ]
                });
            }

            if (requestedDepts && requestedDepts.length > 0) {
                proposedDepartments = requestedDepts;
            }

            // 3. Construct Response
            const generatedOrg = {
                id: 'gen-' + Date.now(),
                name: name,
                namespace: name.toLowerCase().replace(/[^a-z0-9]/g, '-'),
                vision: vision,
                departments: proposedDepartments,
                options,
                norms: [
                    'Focus on customer value',
                    'Continuous improvement',
                    'Transparent communication'
                ],
                estimatedAgentCount: proposedDepartments.reduce((acc, dept) => acc + (dept.agents?.length || 0), 0)
            };

            return reply.send(generatedOrg);

        } catch (error) {
            fastify.log.error({ err: error }, 'Error generating organization');
            return reply.status(500).send({
                error: 'Failed to generate organization',
                success: false,
            });
        }
    });

    /**
     * POST /api/v1/org/genesis/materialize
     * Create the generated organization in the database
     */
    fastify.post<{ Body: MaterializeGenesisBody }>('/genesis/materialize', async (request: FastifyRequest<{ Body: MaterializeGenesisBody }>, reply: FastifyReply) => {
        try {
            const orgData = parsePayload(materializeGenesisSchema, request.body, reply);
            if (!orgData) {
                return;
            }

            fastify.log.info({ orgName: orgData.name }, 'Materializing organization');

            // 1. Create Organization
            // Check if namespace exists, append random suffix if so
            let namespace = orgData.namespace;
            const existing = await prisma.organization.findUnique({ where: { namespace } });
            if (existing) {
                namespace = `${namespace}-${Math.floor(Math.random() * 1000)}`;
            }

            const organization = await prisma.organization.create({
                data: {
                    name: orgData.name,
                    namespace: namespace,
                    displayName: orgData.name,
                    description: orgData.vision,
                    structure: { type: 'hierarchical', maxDepth: 4 },
                    settings: {
                        defaultTimezone: 'UTC',
                        events: {},
                        hitl: {},
                        ai: { enabled: true }
                    },
                    metadata: {
                        norms: orgData.norms || [],
                        generatedBy: 'genesis-ai',
                        originalVision: orgData.vision,
                        template: orgData.template
                    }
                }
            });

            // 2. Create Departments and Agents
            for (const dept of orgData.departments) {
                const department = await prisma.department.create({
                    data: {
                        organizationId: organization.id,
                        name: dept.name,
                        type: dept.type,
                        description: dept.description,
                        status: 'ACTIVE',
                        configuration: {},
                    }
                });

                if (dept.agents && dept.agents.length > 0) {
                    await prisma.agent.createMany({
                        data: dept.agents.map(agent => ({
                            organizationId: organization.id,
                            departmentId: department.id,
                            name: agent.name,
                            role: agent.role,
                            status: 'ONLINE',
                            capabilities: [],
                            configuration: {
                                description: agent.description
                            }
                        }))
                    });
                }
            }

            return reply.send({
                data: {
                    id: organization.id,
                    namespace: organization.namespace,
                },
                success: true,
                timestamp: new Date().toISOString(),
            });

        } catch (error) {
            fastify.log.error({ err: error }, 'Error materializing organization');
            return reply.status(500).send({
                error: 'Failed to materialize organization',
                success: false,
            });
        }
    });
}

