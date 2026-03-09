/**
 * Mock API Server - For Development/Testing Without Real Database
 * 
 * This is a minimal Fastify server that provides mock workspace data.
 * Use this to test the frontend while the real API is being set up.
 * 
 * @doc.type utility
 * @doc.purpose Mock API for frontend development
 * @doc.layer product
 * @doc.pattern Development Tool
 */

import Fastify from 'fastify';
import cors from '@fastify/cors';

const fastify = Fastify({
    logger: {
        level: 'info',
        transport: {
            target: 'pino-pretty',
        },
    },
});

// Register CORS
fastify.register(cors, {
    origin: '*',
    credentials: false,
});

// Mock data
const mockWorkspaces = [
    {
        id: 'workspace-1',
        name: 'Development',
        description: 'Main development workspace',
        isDefault: true,
        projectCount: 3,
        memberCount: 2,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-09T00:00:00Z',
    },
    {
        id: 'workspace-2',
        name: 'Testing',
        description: 'QA and testing workspace',
        isDefault: false,
        projectCount: 2,
        memberCount: 1,
        createdAt: '2024-01-02T00:00:00Z',
        updatedAt: '2024-01-08T00:00:00Z',
    },
    {
        id: 'workspace-3',
        name: 'Production',
        description: 'Production workspace',
        isDefault: false,
        projectCount: 5,
        memberCount: 4,
        createdAt: '2024-01-03T00:00:00Z',
        updatedAt: '2024-01-07T00:00:00Z',
    },
];

// Health check
fastify.get('/health', async (request, reply) => {
    return { status: 'ok', mode: 'mock' };
});

// Get all workspaces
fastify.get('/api/workspaces', async (request, reply) => {
    console.log('[MOCK API] GET /api/workspaces');
    // Simulate network delay
    await new Promise(resolve => setTimeout(resolve, 500));
    return {
        data: mockWorkspaces,
        total: mockWorkspaces.length,
    };
});

// Get specific workspace
fastify.get<{ Params: { id: string } }>('/api/workspaces/:id', async (request, reply) => {
    const { id } = request.params;
    console.log(`[MOCK API] GET /api/workspaces/${id}`);

    const workspace = mockWorkspaces.find(w => w.id === id);

    if (!workspace) {
        reply.status(404);
        return { error: 'Workspace not found' };
    }

    // Simulate network delay
    await new Promise(resolve => setTimeout(resolve, 300));

    return workspace;
});

// Get workspace projects
fastify.get<{ Params: { id: string } }>('/api/workspaces/:id/projects', async (request, reply) => {
    const { id } = request.params;
    console.log(`[MOCK API] GET /api/workspaces/${id}/projects`);

    const workspace = mockWorkspaces.find(w => w.id === id);

    if (!workspace) {
        reply.status(404);
        return { error: 'Workspace not found' };
    }

    // Mock projects
    const mockProjects = [
        {
            id: `project-1-${id}`,
            name: 'Project Alpha',
            description: 'First project',
            status: 'active',
            currentPhase: 'design',
            workspaceId: id,
        },
        {
            id: `project-2-${id}`,
            name: 'Project Beta',
            description: 'Second project',
            status: 'active',
            currentPhase: 'development',
            workspaceId: id,
        },
    ];

    return {
        data: mockProjects,
        total: mockProjects.length,
    };
});

// Start server
const start = async () => {
    // Guard: never start mock server in production
    if (process.env.NODE_ENV === 'production') {
        console.error('[MOCK API] ❌ Mock server must NOT run in production. Exiting.');
        process.exit(1);
    }

    try {
        await fastify.listen({ port: 7003, host: '0.0.0.0' });
        console.log('[MOCK API] 🚀 Server running at http://localhost:7003');
        console.log('[MOCK API] ℹ️  This is a mock API for development only');
        console.log('[MOCK API] ⚠️  NODE_ENV:', process.env.NODE_ENV || 'undefined (defaulting to development)');
        console.log('[MOCK API] 💡 To use real API, set up PostgreSQL and run: pnpm run dev');
    } catch (err) {
        fastify.log.error(err);
        process.exit(1);
    }
};

start();
