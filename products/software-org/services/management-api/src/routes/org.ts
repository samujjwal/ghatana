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
import { prisma } from '../db/client.js';

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
    fastify.get('/departments', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            // Get organization ID from query params or use default
            const { organizationId } = request.query as any;
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
            fastify.log.error('Error fetching departments:', error);
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
    fastify.post<{
        Body: {
            name: string;
            type: string;
            description?: string;
            organizationId?: string;
            status?: string;
        };
    }>('/departments', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { name, type, description, organizationId, status = 'active' } = request.body as any;

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
            fastify.log.error('Error creating department:', error);
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
                fastify.log.error('Error fetching department:', error);
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
    fastify.get('/agents', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { organizationId } = request.query as any;
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
            fastify.log.error('Error fetching agents:', error);
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
                fastify.log.error('Error fetching agent:', error);
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
        Body: {
            id?: string; // If provided, reassign existing agent
            name?: string; // Required for new agent
            role?: string; // Required for new agent
            status?: 'ONLINE' | 'OFFLINE' | 'BUSY';
            capabilities?: string[];
            configuration?: Record<string, unknown>;
        };
    }>('/departments/:id/agents', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { id: departmentId } = request.params as { id: string };
            const agentData = request.body as any;

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
                        capabilities: agentData.capabilities || [],
                        configuration: agentData.configuration || {},
                    },
                    include: {
                        department: {
                            select: { id: true, name: true, type: true },
                        },
                    },
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
                        department: newAgent.department,
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
            if ((error as any).code === 'P2002') {
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
        Body: {
            name?: string;
            role?: string;
            status?: 'ONLINE' | 'OFFLINE' | 'BUSY';
            capabilities?: string[];
            configuration?: Record<string, unknown>;
        };
    }>('/agents/:id', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { id } = request.params as { id: string };
            const updates = request.body as any;

            fastify.log.info({ id, updates }, 'Updating agent');

            // Build update data
            const updateData: any = {};
            if (updates.name !== undefined) updateData.name = updates.name;
            if (updates.role !== undefined) updateData.role = updates.role;
            if (updates.status !== undefined) updateData.status = updates.status;
            if (updates.capabilities !== undefined) updateData.capabilities = updates.capabilities;
            if (updates.configuration !== undefined) {
                // Merge with existing configuration
                const existing = await prisma.agent.findUnique({
                    where: { id },
                    select: { configuration: true },
                });
                updateData.configuration = {
                    ...(existing?.configuration as Record<string, unknown> || {}),
                    ...updates.configuration,
                };
            }
            updateData.updatedAt = new Date();

            const agent = await prisma.agent.update({
                where: { id },
                data: updateData,
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
        } catch (error: any) {
            fastify.log.error('Error updating agent:', error);
            if (error.code === 'P2025') {
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
            } catch (error: any) {
                fastify.log.error('Error deleting agent:', error);
                if (error.code === 'P2025') {
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
    fastify.get('/hierarchy', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { organizationId } = request.query as any;
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
            fastify.log.error('Error fetching hierarchy:', error);
            return reply.status(500).send({ error: 'Failed to fetch hierarchy', success: false });
        }
    });

    /**
     * POST /api/v1/org/hierarchy/move
     * Move agent between departments
     */
    fastify.post<{
        Body: {
            agentId: string;
            fromDeptId: string;
            toDeptId: string;
        };
    }>('/hierarchy/move', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { agentId, fromDeptId, toDeptId } = request.body as any;

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
            fastify.log.error('Error moving agent:', error);
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
    fastify.get('/config', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { organizationId } = request.query as any;
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
            fastify.log.error('Error fetching config:', error);
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
    fastify.get('/graph', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { organizationId } = request.query as any;
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
            fastify.log.error('Error fetching graph:', error);
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
    fastify.post('/genesis/generate', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const { name, vision, template, departments: requestedDepts, options } = request.body as any;

            fastify.log.info({ name, template }, 'Generating organization proposal');

            // 1. Deterministic Base: Select template structure
            let proposedDepartments: any[] = [];

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
                if (engDept) {
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

            // 3. Construct Response
            const generatedOrg = {
                id: 'gen-' + Date.now(),
                name: name,
                namespace: name.toLowerCase().replace(/[^a-z0-9]/g, '-'),
                vision: vision,
                departments: proposedDepartments,
                norms: [
                    'Focus on customer value',
                    'Continuous improvement',
                    'Transparent communication'
                ],
                estimatedAgentCount: proposedDepartments.reduce((acc, dept) => acc + (dept.agents?.length || 0), 0)
            };

            return reply.send(generatedOrg);

        } catch (error) {
            fastify.log.error('Error generating organization:', error);
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
    fastify.post('/genesis/materialize', async (request: FastifyRequest, reply: FastifyReply) => {
        try {
            const orgData = request.body as any;

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
                        data: dept.agents.map((agent: any) => ({
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
            fastify.log.error('Error materializing organization:', error);
            return reply.status(500).send({
                error: 'Failed to materialize organization',
                success: false,
            });
        }
    });
}

