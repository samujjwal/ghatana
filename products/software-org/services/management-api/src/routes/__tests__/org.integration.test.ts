import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Fastify, { type FastifyInstance } from 'fastify';
import orgRoutes from '../org.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
    organization: {
        findFirst: vi.fn(),
        create: vi.fn(),
        findUnique: vi.fn(),
    },
    department: {
        findMany: vi.fn(),
        findFirst: vi.fn(),
        create: vi.fn(),
        findUnique: vi.fn(),
    },
    agent: {
        findMany: vi.fn(),
        findUnique: vi.fn(),
        findFirst: vi.fn(),
        update: vi.fn(),
        create: vi.fn(),
        delete: vi.fn(),
        createMany: vi.fn(),
    },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Organization Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(orgRoutes, { prefix: '/api/v1/org' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('returns 400 when departments query validation fails', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/departments?organizationId=',
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({
            error: 'organizationId: Too small: expected string to have >=1 characters',
            success: false,
        });
    });

    it('returns an empty department list when no default organization exists', async () => {
        mockPrisma.organization.findFirst.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/departments',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [],
            success: true,
            timestamp: expect.any(String),
        });
        expect(mockPrisma.department.findMany).not.toHaveBeenCalled();
    });

    it('creates a department using the default organization when none is provided', async () => {
        const createdAt = new Date('2026-04-16T12:00:00.000Z');
        const organization = { id: 'org-1' };

        mockPrisma.organization.findFirst.mockResolvedValue(organization);
        mockPrisma.department.findFirst.mockResolvedValue(null);
        mockPrisma.department.create.mockResolvedValue({
            id: 'dept-1',
            name: 'Engineering',
            type: 'DELIVERY',
            description: 'Engineering department',
            status: 'active',
            agents: [],
            createdAt,
            updatedAt: createdAt,
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments',
            payload: {
                name: 'Engineering',
                type: 'DELIVERY',
            },
        });

        expect(response.statusCode).toBe(201);
        expect(mockPrisma.department.create).toHaveBeenCalledWith(expect.objectContaining({
            data: expect.objectContaining({
                organizationId: 'org-1',
                name: 'Engineering',
                type: 'DELIVERY',
            }),
        }));
        expect(response.json()).toEqual({
            data: {
                id: 'dept-1',
                name: 'Engineering',
                type: 'DELIVERY',
                description: 'Engineering department',
                status: 'active',
                agentCount: 0,
                agents: [],
                createdAt: '2026-04-16T12:00:00.000Z',
                updatedAt: '2026-04-16T12:00:00.000Z',
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns 409 when department name already exists in the organization', async () => {
        mockPrisma.organization.findFirst.mockResolvedValue({ id: 'org-1' });
        mockPrisma.department.findFirst.mockResolvedValue({ id: 'existing-dept' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments',
            payload: {
                name: 'Engineering',
                type: 'DELIVERY',
            },
        });

        expect(response.statusCode).toBe(409);
        expect(response.json()).toEqual({
            error: 'Department with this name already exists',
            success: false,
        });
    });

    it('returns 404 when creating a department for an unknown organization', async () => {
        mockPrisma.organization.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments',
            payload: {
                name: 'Engineering',
                type: 'DELIVERY',
                organizationId: 'missing-org',
            },
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Organization with ID missing-org not found',
            success: false,
        });
    });

    it('returns 404 when a department detail record does not exist', async () => {
        mockPrisma.department.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/departments/missing-department',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Department not found',
            success: false,
        });
    });

    it('returns an empty agent list when an explicit organization is unknown', async () => {
        mockPrisma.organization.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/agents?organizationId=missing-org',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [],
            success: true,
            timestamp: expect.any(String),
        });
        expect(mockPrisma.agent.findMany).not.toHaveBeenCalled();
    });

    it('lists agents for a resolved organization', async () => {
        const createdAt = new Date('2026-04-16T12:00:00.000Z');
        mockPrisma.organization.findUnique.mockResolvedValue({ id: 'org-1' });
        mockPrisma.agent.findMany.mockResolvedValue([
            {
                id: 'agent-1',
                organizationId: 'org-1',
                departmentId: 'dept-1',
                name: 'Planner',
                role: 'ARCHITECT',
                status: 'ONLINE',
                capabilities: ['design'],
                configuration: { mode: 'assisted' },
                department: { id: 'dept-1', name: 'Engineering', type: 'DELIVERY' },
                createdAt,
                updatedAt: createdAt,
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/agents?organizationId=org-1',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [
                {
                    id: 'agent-1',
                    organizationId: 'org-1',
                    departmentId: 'dept-1',
                    name: 'Planner',
                    role: 'ARCHITECT',
                    status: 'ONLINE',
                    capabilities: ['design'],
                    configuration: { mode: 'assisted' },
                    department: { id: 'dept-1', name: 'Engineering', type: 'DELIVERY' },
                    createdAt: '2026-04-16T12:00:00.000Z',
                    updatedAt: '2026-04-16T12:00:00.000Z',
                },
            ],
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns 404 when an agent detail record does not exist', async () => {
        mockPrisma.agent.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/agents/missing-agent',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Agent not found',
            success: false,
        });
    });

    it('returns 404 when adding an agent to a missing department', async () => {
        mockPrisma.department.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments/missing-department/agents',
            payload: {
                name: 'Planner',
                role: 'ARCHITECT',
            },
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Department not found',
            success: false,
        });
    });

    it('creates an agent inside a department with default status and summaries', async () => {
        const createdAt = new Date('2026-04-16T12:00:00.000Z');
        mockPrisma.department.findUnique
            .mockResolvedValueOnce({ id: 'dept-1', organizationId: 'org-1' })
            .mockResolvedValueOnce({ id: 'dept-1', name: 'Engineering', type: 'DELIVERY' });
        mockPrisma.agent.findFirst.mockResolvedValue(null);
        mockPrisma.agent.create.mockResolvedValue({
            id: 'agent-1',
            organizationId: 'org-1',
            departmentId: 'dept-1',
            name: 'Planner',
            role: 'ARCHITECT',
            status: 'ONLINE',
            capabilities: ['design'],
            configuration: { mode: 'assisted' },
            createdAt,
            updatedAt: createdAt,
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments/dept-1/agents',
            payload: {
                name: 'Planner',
                role: 'ARCHITECT',
                capabilities: ['design'],
                configuration: { mode: 'assisted' },
            },
        });

        expect(response.statusCode).toBe(201);
        expect(mockPrisma.agent.create).toHaveBeenCalledWith({
            data: {
                organizationId: 'org-1',
                departmentId: 'dept-1',
                name: 'Planner',
                role: 'ARCHITECT',
                status: 'ONLINE',
                capabilities: ['design'],
                configuration: { mode: 'assisted' },
            },
        });
        expect(response.json()).toEqual({
            data: {
                id: 'agent-1',
                organizationId: 'org-1',
                departmentId: 'dept-1',
                name: 'Planner',
                role: 'ARCHITECT',
                status: 'ONLINE',
                capabilities: ['design'],
                configuration: { mode: 'assisted' },
                department: { id: 'dept-1', name: 'Engineering', type: 'DELIVERY' },
                createdAt: '2026-04-16T12:00:00.000Z',
                updatedAt: '2026-04-16T12:00:00.000Z',
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('reassigns an existing agent into a department when an agent id is provided', async () => {
        const createdAt = new Date('2026-04-16T12:00:00.000Z');
        const updatedAt = new Date('2026-04-16T12:05:00.000Z');

        mockPrisma.department.findUnique.mockResolvedValue({ id: 'dept-1', organizationId: 'org-1' });
        mockPrisma.agent.findFirst.mockResolvedValue(null);
        mockPrisma.agent.update.mockResolvedValue({
            id: 'agent-9',
            organizationId: 'org-1',
            departmentId: 'dept-1',
            name: 'Planner',
            role: 'ARCHITECT',
            status: 'BUSY',
            capabilities: ['design'],
            configuration: { mode: 'assisted' },
            department: { id: 'dept-1', name: 'Engineering', type: 'DELIVERY' },
            createdAt,
            updatedAt,
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments/dept-1/agents',
            payload: {
                id: 'agent-9',
                name: 'Planner',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.agent.update).toHaveBeenCalledWith({
            where: { id: 'agent-9' },
            data: {
                departmentId: 'dept-1',
                updatedAt: expect.any(Date),
            },
            include: {
                department: {
                    select: { id: true, name: true, type: true },
                },
            },
        });
        expect(response.json()).toEqual({
            data: {
                id: 'agent-9',
                organizationId: 'org-1',
                departmentId: 'dept-1',
                name: 'Planner',
                role: 'ARCHITECT',
                status: 'BUSY',
                capabilities: ['design'],
                configuration: { mode: 'assisted' },
                department: { id: 'dept-1', name: 'Engineering', type: 'DELIVERY' },
                createdAt: '2026-04-16T12:00:00.000Z',
                updatedAt: '2026-04-16T12:05:00.000Z',
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns 409 when adding an agent whose name already exists in the organization', async () => {
        mockPrisma.department.findUnique.mockResolvedValue({ id: 'dept-1', organizationId: 'org-1' });
        mockPrisma.agent.findFirst.mockResolvedValue({ id: 'agent-existing' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments/dept-1/agents',
            payload: {
                name: 'Planner',
                role: 'ARCHITECT',
            },
        });

        expect(response.statusCode).toBe(409);
        expect(response.json()).toEqual({
            error: 'Agent with this name already exists in the organization',
            success: false,
        });
    });

    it('returns 400 when creating a new agent without name or role', async () => {
        mockPrisma.department.findUnique.mockResolvedValue({ id: 'dept-1', organizationId: 'org-1' });
        mockPrisma.agent.findFirst.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/departments/dept-1/agents',
            payload: {},
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({
            error: 'Missing required fields: name, role',
            success: false,
        });
    });

    it('updates an agent and merges configuration with existing values', async () => {
        const createdAt = new Date('2026-04-16T12:00:00.000Z');
        const updatedAt = new Date('2026-04-16T12:10:00.000Z');

        mockPrisma.agent.findUnique.mockResolvedValue({
            configuration: { existing: true, mode: 'manual' },
        });
        mockPrisma.agent.update.mockResolvedValue({
            id: 'agent-1',
            organizationId: 'org-1',
            departmentId: 'dept-1',
            name: 'Planner',
            role: 'ARCHITECT',
            status: 'ONLINE',
            capabilities: ['design', 'delivery'],
            configuration: { existing: true, mode: 'assisted', retries: 3 },
            createdAt,
            updatedAt,
        });
        mockPrisma.department.findUnique.mockResolvedValue({
            id: 'dept-1',
            name: 'Engineering',
            type: 'DELIVERY',
        });

        const response = await fastify.inject({
            method: 'PUT',
            url: '/api/v1/org/agents/agent-1',
            payload: {
                status: 'ONLINE',
                capabilities: ['design', 'delivery'],
                configuration: { mode: 'assisted', retries: 3 },
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.agent.update).toHaveBeenCalledWith({
            where: { id: 'agent-1' },
            data: {
                updatedAt: expect.any(Date),
                status: 'ONLINE',
                capabilities: ['design', 'delivery'],
                configuration: { existing: true, mode: 'assisted', retries: 3 },
            },
        });
        expect(response.json()).toEqual({
            data: {
                id: 'agent-1',
                organizationId: 'org-1',
                departmentId: 'dept-1',
                name: 'Planner',
                role: 'ARCHITECT',
                status: 'ONLINE',
                capabilities: ['design', 'delivery'],
                configuration: { existing: true, mode: 'assisted', retries: 3 },
                department: { id: 'dept-1', name: 'Engineering', type: 'DELIVERY' },
                createdAt: '2026-04-16T12:00:00.000Z',
                updatedAt: '2026-04-16T12:10:00.000Z',
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns 404 when updating an agent that does not exist', async () => {
        mockPrisma.agent.update.mockRejectedValue({ code: 'P2025' });

        const response = await fastify.inject({
            method: 'PUT',
            url: '/api/v1/org/agents/missing-agent',
            payload: {
                name: 'Planner',
            },
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Agent not found',
            success: false,
        });
    });

    it('returns 404 when deleting an agent that does not exist', async () => {
        mockPrisma.agent.delete.mockRejectedValue({ code: 'P2025' });

        const response = await fastify.inject({
            method: 'DELETE',
            url: '/api/v1/org/agents/missing-agent',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Agent not found',
            success: false,
        });
    });

    it('deletes an agent and returns a compact success payload', async () => {
        mockPrisma.agent.delete.mockResolvedValue({
            id: 'agent-1',
            name: 'Planner',
            departmentId: 'dept-1',
            department: { id: 'dept-1', name: 'Engineering', type: 'DELIVERY' },
        });

        const response = await fastify.inject({
            method: 'DELETE',
            url: '/api/v1/org/agents/agent-1',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: {
                id: 'agent-1',
                name: 'Planner',
                departmentId: 'dept-1',
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns 400 when moving an agent from the wrong source department', async () => {
        mockPrisma.agent.findUnique.mockResolvedValue({
            id: 'agent-1',
            departmentId: 'dept-current',
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/hierarchy/move',
            payload: {
                agentId: 'agent-1',
                fromDeptId: 'dept-source',
                toDeptId: 'dept-target',
            },
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({
            error: 'Agent is not in the source department',
            success: false,
        });
    });

    it('moves an agent between departments and returns the updated department summary', async () => {
        const createdAt = new Date('2026-04-16T12:00:00.000Z');
        const updatedAt = new Date('2026-04-16T12:05:00.000Z');

        mockPrisma.agent.findUnique.mockResolvedValue({
            id: 'agent-1',
            departmentId: 'dept-source',
        });
        mockPrisma.department.findUnique.mockResolvedValue({ id: 'dept-target' });
        mockPrisma.agent.update.mockResolvedValue({
            id: 'agent-1',
            organizationId: 'org-1',
            departmentId: 'dept-target',
            name: 'Planner',
            role: 'ARCHITECT',
            status: 'ONLINE',
            capabilities: ['design'],
            configuration: { mode: 'assisted' },
            department: { id: 'dept-target', name: 'Platform', type: 'DELIVERY' },
            createdAt,
            updatedAt,
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/hierarchy/move',
            payload: {
                agentId: 'agent-1',
                fromDeptId: 'dept-source',
                toDeptId: 'dept-target',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: {
                id: 'agent-1',
                organizationId: 'org-1',
                departmentId: 'dept-target',
                name: 'Planner',
                role: 'ARCHITECT',
                status: 'ONLINE',
                capabilities: ['design'],
                configuration: { mode: 'assisted' },
                department: { id: 'dept-target', name: 'Platform', type: 'DELIVERY' },
                createdAt: '2026-04-16T12:00:00.000Z',
                updatedAt: '2026-04-16T12:05:00.000Z',
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns hierarchy data with organization and department agent counts', async () => {
        mockPrisma.organization.findUnique
            .mockResolvedValueOnce({ id: 'org-1' })
            .mockResolvedValueOnce({
                id: 'org-1',
                name: 'Default Organization',
                namespace: 'default-org',
                departments: [
                    {
                        id: 'dept-1',
                        name: 'Engineering',
                        type: 'DELIVERY',
                        description: 'Engineering org',
                        status: 'active',
                        agents: [
                            { id: 'agent-1', name: 'Planner', role: 'ARCHITECT', status: 'ONLINE' },
                        ],
                    },
                ],
            });

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/hierarchy?organizationId=org-1',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: {
                organization: {
                    id: 'org-1',
                    name: 'Default Organization',
                    namespace: 'default-org',
                },
                departments: [
                    {
                        id: 'dept-1',
                        name: 'Engineering',
                        type: 'DELIVERY',
                        description: 'Engineering org',
                        status: 'active',
                        agentCount: 1,
                        agents: [
                            { id: 'agent-1', name: 'Planner', role: 'ARCHITECT', status: 'ONLINE' },
                        ],
                    },
                ],
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns 404 when hierarchy is requested for an unknown organization', async () => {
        mockPrisma.organization.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/hierarchy?organizationId=missing-org',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Organization not found',
            success: false,
        });
    });

    it('returns the services placeholder payload', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/services',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [],
            success: true,
            message: 'Services endpoint not yet implemented',
            timestamp: expect.any(String),
        });
    });

    it('returns 404 when organization config cannot be resolved', async () => {
        mockPrisma.organization.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/config?organizationId=missing-org',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Organization not found',
            success: false,
        });
    });

    it('returns organization config counts for a resolved organization', async () => {
        mockPrisma.organization.findUnique
            .mockResolvedValueOnce({ id: 'org-1' })
            .mockResolvedValueOnce({
                id: 'org-1',
                name: 'Default Organization',
                namespace: 'default-org',
                _count: {
                    departments: 3,
                    agents: 8,
                },
            });

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/config?organizationId=org-1',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: {
                id: 'org-1',
                name: 'Default Organization',
                namespace: 'default-org',
                departments: 3,
                agents: 8,
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns graph nodes and edges for department-agent relationships', async () => {
        mockPrisma.organization.findUnique.mockResolvedValue({ id: 'org-1' });
        mockPrisma.department.findMany.mockResolvedValue([
            {
                id: 'dept-1',
                name: 'Engineering',
                type: 'DELIVERY',
                description: 'Engineering org',
                agents: [
                    { id: 'agent-1', name: 'Planner', role: 'ARCHITECT', status: 'ONLINE' },
                ],
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/graph?organizationId=org-1',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: {
                nodes: [
                    {
                        id: 'dept-1',
                        type: 'department',
                        label: 'Engineering',
                        data: {
                            id: 'dept-1',
                            name: 'Engineering',
                            type: 'DELIVERY',
                            description: 'Engineering org',
                            agentCount: 1,
                        },
                    },
                    {
                        id: 'agent-1',
                        type: 'agent',
                        label: 'Planner',
                        data: {
                            id: 'agent-1',
                            name: 'Planner',
                            role: 'ARCHITECT',
                            status: 'ONLINE',
                        },
                    },
                ],
                edges: [
                    {
                        id: 'edge-dept-1-agent-1',
                        source: 'dept-1',
                        target: 'agent-1',
                        type: 'contains',
                    },
                ],
            },
            success: true,
            timestamp: expect.any(String),
        });
    });

    it('returns 404 when graph is requested for an unknown organization', async () => {
        mockPrisma.organization.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/org/graph?organizationId=missing-org',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({
            error: 'Organization not found',
            success: false,
        });
    });

    it('generates a startup organization proposal with vision-based enrichment', async () => {
        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/genesis/generate',
            payload: {
                name: 'Phoenix Labs',
                vision: 'Build a mobile AI platform for field teams',
                template: 'startup',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            id: expect.stringMatching(/^gen-/),
            name: 'Phoenix Labs',
            namespace: 'phoenix-labs',
            vision: 'Build a mobile AI platform for field teams',
            departments: expect.arrayContaining([
                expect.objectContaining({
                    name: 'Engineering',
                    agents: expect.arrayContaining([
                        expect.objectContaining({ name: 'Mobile Engineer' }),
                    ]),
                }),
                expect.objectContaining({
                    name: 'Data & AI',
                }),
            ]),
            norms: expect.arrayContaining([
                'Focus on customer value',
                'Continuous improvement',
                'Transparent communication',
            ]),
            estimatedAgentCount: expect.any(Number),
            options: undefined,
        });
    });

    it('returns 400 when genesis generation payload is invalid', async () => {
        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/genesis/generate',
            payload: {
                name: '',
                vision: 'Vision',
                template: 'startup',
            },
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({
            error: 'name: Too small: expected string to have >=1 characters',
            success: false,
        });
    });

    it('materializes a generated organization and suffixes an existing namespace', async () => {
        mockPrisma.organization.findUnique.mockResolvedValue({ id: 'existing-org', namespace: 'phoenix-labs' });
        mockPrisma.organization.create.mockResolvedValue({
            id: 'org-200',
            namespace: 'phoenix-labs-777',
        });
        mockPrisma.department.create
            .mockResolvedValueOnce({ id: 'dept-1' })
            .mockResolvedValueOnce({ id: 'dept-2' });
        mockPrisma.agent.createMany.mockResolvedValue({ count: 2 });

        const randomSpy = vi.spyOn(Math, 'random').mockReturnValue(0.777);

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/org/genesis/materialize',
            payload: {
                name: 'Phoenix Labs',
                namespace: 'phoenix-labs',
                vision: 'Build a mobile AI platform for field teams',
                template: 'startup',
                norms: ['Move quickly'],
                departments: [
                    {
                        name: 'Engineering',
                        type: 'ENGINEERING',
                        description: 'Build the product',
                        agents: [
                            { name: 'Tech Lead', role: 'lead', description: 'Sets architecture' },
                            { name: 'Mobile Engineer', role: 'engineer', description: 'Builds apps' },
                        ],
                    },
                    {
                        name: 'Product',
                        type: 'PRODUCT',
                        description: 'Owns roadmap',
                    },
                ],
            },
        });

        randomSpy.mockRestore();

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.organization.create).toHaveBeenCalledWith({
            data: expect.objectContaining({
                name: 'Phoenix Labs',
                namespace: 'phoenix-labs-777',
                description: 'Build a mobile AI platform for field teams',
                metadata: expect.objectContaining({
                    norms: ['Move quickly'],
                    template: 'startup',
                }),
            }),
        });
        expect(mockPrisma.department.create).toHaveBeenNthCalledWith(1, {
            data: {
                organizationId: 'org-200',
                name: 'Engineering',
                type: 'ENGINEERING',
                description: 'Build the product',
                status: 'ACTIVE',
                configuration: {},
            },
        });
        expect(mockPrisma.agent.createMany).toHaveBeenCalledWith({
            data: [
                {
                    organizationId: 'org-200',
                    departmentId: 'dept-1',
                    name: 'Tech Lead',
                    role: 'lead',
                    status: 'ONLINE',
                    capabilities: [],
                    configuration: { description: 'Sets architecture' },
                },
                {
                    organizationId: 'org-200',
                    departmentId: 'dept-1',
                    name: 'Mobile Engineer',
                    role: 'engineer',
                    status: 'ONLINE',
                    capabilities: [],
                    configuration: { description: 'Builds apps' },
                },
            ],
        });
        expect(response.json()).toEqual({
            data: {
                id: 'org-200',
                namespace: 'phoenix-labs-777',
            },
            success: true,
            timestamp: expect.any(String),
        });
    });
});