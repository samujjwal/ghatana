/**
 * Organization API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for virtual organization management
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/org/departments - List departments
 * - GET /api/v1/org/departments/:id - Get department details
 * - GET /api/v1/org/services - List services
 * - GET /api/v1/org/workflows - List workflows
 * - GET /api/v1/org/integrations - List integrations
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';

/** Department interface */
interface Department {
    id: string;
    name: string;
    description: string;
    headCount: number;
    status: 'active' | 'inactive';
    createdAt: string;
    updatedAt: string;
}

/** Service interface */
interface Service {
    id: string;
    name: string;
    departmentId: string;
    status: 'healthy' | 'degraded' | 'down';
    description: string;
}

/** Mock data for departments */
const mockDepartments: Department[] = [
    {
        id: 'dept-1',
        name: 'Engineering',
        description: 'Software development and engineering team',
        headCount: 45,
        status: 'active',
        createdAt: new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'dept-2',
        name: 'DevOps',
        description: 'Infrastructure and operations team',
        headCount: 12,
        status: 'active',
        createdAt: new Date(Date.now() - 300 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'dept-3',
        name: 'Security',
        description: 'Security and compliance team',
        headCount: 8,
        status: 'active',
        createdAt: new Date(Date.now() - 200 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'dept-4',
        name: 'QA',
        description: 'Quality assurance and testing team',
        headCount: 15,
        status: 'active',
        createdAt: new Date(Date.now() - 250 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'dept-5',
        name: 'Data Science',
        description: 'ML and data analytics team',
        headCount: 10,
        status: 'active',
        createdAt: new Date(Date.now() - 150 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
];

/** Mock data for services */
const mockServices: Service[] = [
    { id: 'svc-1', name: 'API Gateway', departmentId: 'dept-1', status: 'healthy', description: 'Main API gateway' },
    { id: 'svc-2', name: 'Auth Service', departmentId: 'dept-1', status: 'healthy', description: 'Authentication service' },
    { id: 'svc-3', name: 'User Service', departmentId: 'dept-1', status: 'healthy', description: 'User management' },
    { id: 'svc-4', name: 'CI/CD Pipeline', departmentId: 'dept-2', status: 'healthy', description: 'Build and deploy' },
    { id: 'svc-5', name: 'Monitoring', departmentId: 'dept-2', status: 'degraded', description: 'System monitoring' },
];

/** Agent interface */
interface Agent {
    id: string;
    organizationId: string;
    departmentId: string;
    name: string;
    role: string;
    status: 'ONLINE' | 'OFFLINE' | 'BUSY';
    capabilities: string[];
    configuration: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
}

/** Mock data for agents */
const mockAgents: Agent[] = [
    {
        id: 'agent-1',
        organizationId: 'org-1',
        departmentId: 'dept-1',
        name: 'Build Agent',
        role: 'automation',
        status: 'ONLINE',
        capabilities: ['compile', 'package', 'artifact-upload', 'cache-management'],
        configuration: {
            model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 4096 },
            personality: { temperature: 0.3, creativity: 0.2, assertiveness: 0.8 },
        },
        createdAt: new Date(Date.now() - 100 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'agent-2',
        organizationId: 'org-1',
        departmentId: 'dept-3',
        name: 'Security Scanner',
        role: 'security',
        status: 'ONLINE',
        capabilities: ['vulnerability-scan', 'sast', 'dast', 'dependency-audit', 'compliance-check'],
        configuration: {
            model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 8192 },
            personality: { temperature: 0.1, creativity: 0.1, assertiveness: 0.9 },
        },
        createdAt: new Date(Date.now() - 80 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
    {
        id: 'agent-3',
        organizationId: 'org-1',
        departmentId: 'dept-2',
        name: 'Deploy Agent',
        role: 'deployment',
        status: 'ONLINE',
        capabilities: ['deploy', 'rollback', 'health-check', 'traffic-shift', 'canary-release'],
        configuration: {
            model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 4096 },
            personality: { temperature: 0.2, creativity: 0.3, assertiveness: 0.7 },
        },
        createdAt: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).toISOString(),
        updatedAt: new Date().toISOString(),
    },
];

/**
 * Register organization routes
 */
export default async function orgRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/org/departments
     * List all departments
     */
    fastify.get('/departments', async (_request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching departments');
        return reply.send({
            data: mockDepartments,
            success: true,
            timestamp: new Date().toISOString(),
        });
    });

    /**
     * GET /api/v1/org/departments/:id
     * Get department details
     */
    fastify.get<{ Params: { id: string } }>(
        '/departments/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            const { id } = request.params;
            fastify.log.debug({ id }, 'Fetching department');

            const department = mockDepartments.find(d => d.id === id);
            if (!department) {
                return reply.status(404).send({ error: 'Department not found' });
            }

            return reply.send({
                data: department,
                success: true,
                timestamp: new Date().toISOString(),
            });
        }
    );

    /**
     * GET /api/v1/org/services
     * List all services
     */
    fastify.get('/services', async (_request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching services');
        return reply.send({
            data: mockServices,
            success: true,
            timestamp: new Date().toISOString(),
        });
    });

    /**
     * GET /api/v1/org/services/:id
     * Get service details
     */
    fastify.get<{ Params: { id: string } }>(
        '/services/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            const { id } = request.params;
            fastify.log.debug({ id }, 'Fetching service');

            const service = mockServices.find(s => s.id === id);
            if (!service) {
                return reply.status(404).send({ error: 'Service not found' });
            }

            return reply.send({
                data: service,
                success: true,
                timestamp: new Date().toISOString(),
            });
        }
    );

    /**
     * GET /api/v1/org/config
     * Get organization configuration
     */
    fastify.get('/config', async (_request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching org config');
        return reply.send({
            data: {
                name: 'Software Org',
                version: '1.0.0',
                departments: mockDepartments.length,
                services: mockServices.length,
            },
            success: true,
            timestamp: new Date().toISOString(),
        });
    });

    /**
     * GET /api/v1/org/graph
     * Get organization graph for visualization
     */
    fastify.get('/graph', async (_request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching org graph');

        const nodes = [
            ...mockDepartments.map(d => ({
                id: d.id,
                type: 'department',
                label: d.name,
                data: d,
            })),
            ...mockServices.map(s => ({
                id: s.id,
                type: 'service',
                label: s.name,
                data: s,
            })),
        ];

        const edges = mockServices.map(s => ({
            id: `edge-${s.departmentId}-${s.id}`,
            source: s.departmentId,
            target: s.id,
        }));

        return reply.send({
            data: { nodes, edges },
            success: true,
            timestamp: new Date().toISOString(),
        });
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
        const { name, type, description, organizationId = 'org-1', status = 'active' } = request.body as any;

        fastify.log.info({ name, type }, 'Creating department');

        // Validate input
        if (!name || !type) {
            return reply.status(400).send({
                error: 'Missing required fields: name, type',
                success: false,
            });
        }

        // Check for duplicate name
        const exists = mockDepartments.find(d => d.name.toLowerCase() === name.toLowerCase());
        if (exists) {
            return reply.status(409).send({
                error: 'Department with this name already exists',
                success: false,
            });
        }

        // Create new department
        const newDepartment: Department = {
            id: `dept-${Date.now()}`,
            name,
            description: description || `${name} department`,
            headCount: 0,
            status: status as 'active' | 'inactive',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        };

        mockDepartments.push(newDepartment);

        return reply.status(201).send({
            data: newDepartment,
            success: true,
            timestamp: new Date().toISOString(),
        });
    });

    /**
     * GET /api/v1/org/agents
     * List all agents
     */
    fastify.get('/agents', async (_request: FastifyRequest, reply: FastifyReply) => {
        fastify.log.debug('Fetching agents');
        return reply.send({
            data: mockAgents,
            success: true,
            timestamp: new Date().toISOString(),
        });
    });

    /**
     * GET /api/v1/org/agents/:id
     * Get agent details
     */
    fastify.get<{ Params: { id: string } }>(
        '/agents/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            const { id } = request.params;
            fastify.log.debug({ id }, 'Fetching agent');

            const agent = mockAgents.find(a => a.id === id);
            if (!agent) {
                return reply.status(404).send({ error: 'Agent not found' });
            }

            return reply.send({
                data: agent,
                success: true,
                timestamp: new Date().toISOString(),
            });
        }
    );

    /**
     * POST /api/v1/org/departments/:id/agents
     * Add agent to department
     */
    fastify.post<{
        Params: { id: string };
        Body: Partial<Agent>;
    }>('/departments/:id/agents', async (request: FastifyRequest, reply: FastifyReply) => {
        const { id: departmentId } = request.params as { id: string };
        const agentData = request.body as Partial<Agent>;

        fastify.log.info({ departmentId, agentData }, 'Adding agent to department');

        // Validate department exists
        const department = mockDepartments.find(d => d.id === departmentId);
        if (!department) {
            return reply.status(404).send({
                error: 'Department not found',
                success: false,
            });
        }

        // Check if agent is being reassigned or created
        if (agentData.id) {
            // Reassigning existing agent
            const agent = mockAgents.find(a => a.id === agentData.id);
            if (!agent) {
                return reply.status(404).send({
                    error: 'Agent not found',
                    success: false,
                });
            }

            // Update department assignment
            agent.departmentId = departmentId;
            agent.updatedAt = new Date().toISOString();

            return reply.send({
                data: agent,
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

            const newAgent: Agent = {
                id: `agent-${Date.now()}`,
                organizationId: 'org-1',
                departmentId,
                name: agentData.name,
                role: agentData.role,
                status: agentData.status || 'ONLINE',
                capabilities: agentData.capabilities || [],
                configuration: agentData.configuration || {},
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            mockAgents.push(newAgent);

            return reply.status(201).send({
                data: newAgent,
                success: true,
                timestamp: new Date().toISOString(),
            });
        }
    });

    /**
     * PUT /api/v1/org/agents/:id
     * Update agent
     */
    fastify.put<{
        Params: { id: string };
        Body: Partial<Agent>;
    }>('/agents/:id', async (request: FastifyRequest, reply: FastifyReply) => {
        const { id } = request.params as { id: string };
        const updates = request.body as Partial<Agent>;

        fastify.log.info({ id, updates }, 'Updating agent');

        const agent = mockAgents.find(a => a.id === id);
        if (!agent) {
            return reply.status(404).send({
                error: 'Agent not found',
                success: false,
            });
        }

        // Update agent fields
        if (updates.name) agent.name = updates.name;
        if (updates.role) agent.role = updates.role;
        if (updates.status) agent.status = updates.status;
        if (updates.capabilities) agent.capabilities = updates.capabilities;
        if (updates.configuration) agent.configuration = { ...agent.configuration, ...updates.configuration };
        agent.updatedAt = new Date().toISOString();

        return reply.send({
            data: agent,
            success: true,
            timestamp: new Date().toISOString(),
        });
    });

    /**
     * DELETE /api/v1/org/agents/:id
     * Delete agent
     */
    fastify.delete<{ Params: { id: string } }>(
        '/agents/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            const { id } = request.params;
            fastify.log.info({ id }, 'Deleting agent');

            const index = mockAgents.findIndex(a => a.id === id);
            if (index === -1) {
                return reply.status(404).send({
                    error: 'Agent not found',
                    success: false,
                });
            }

            const deletedAgent = mockAgents.splice(index, 1)[0];

            return reply.send({
                data: deletedAgent,
                success: true,
                timestamp: new Date().toISOString(),
            });
        }
    );

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
        const { agentId, fromDeptId, toDeptId } = request.body as any;

        fastify.log.info({ agentId, fromDeptId, toDeptId }, 'Moving agent between departments');

        const agent = mockAgents.find(a => a.id === agentId);
        if (!agent) {
            return reply.status(404).send({
                error: 'Agent not found',
                success: false,
            });
        }

        const toDept = mockDepartments.find(d => d.id === toDeptId);
        if (!toDept) {
            return reply.status(404).send({
                error: 'Target department not found',
                success: false,
            });
        }

        // Move agent
        agent.departmentId = toDeptId;
        agent.updatedAt = new Date().toISOString();

        return reply.send({
            data: agent,
            success: true,
            timestamp: new Date().toISOString(),
        });
    });
}
