/**
 * Mock API Server (JavaScript Version)
 * Simple HTTP server without external dependencies
 */

import http from 'http';

const PORT = 7003;

// Mock data
const mockWorkspaces = [
    {
        id: 'workspace-1',
        name: 'Development',
        description: 'Main development workspace',
        isDefault: true,
        projectCount: 3,
        memberCount: 2,
    },
    {
        id: 'workspace-2',
        name: 'Testing',
        description: 'QA and testing workspace',
        isDefault: false,
        projectCount: 2,
        memberCount: 1,
    },
    {
        id: 'workspace-3',
        name: 'Production',
        description: 'Production workspace',
        isDefault: false,
        projectCount: 5,
        memberCount: 4,
    },
];

// Create server
const server = http.createServer((req, res) => {
    // Enable CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.setHeader('Content-Type', 'application/json');

    // Handle preflight
    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    console.log(`[MOCK API] ${req.method} ${req.url}`);

    // Health check
    if (req.url === '/health') {
        res.writeHead(200);
        res.end(JSON.stringify({ status: 'ok', mode: 'mock' }));
        return;
    }

    // Get all workspaces
    if (req.url === '/api/workspaces' && req.method === 'GET') {
        // Simulate network delay
        setTimeout(() => {
            res.writeHead(200);
            res.end(JSON.stringify({ data: mockWorkspaces, total: mockWorkspaces.length }));
        }, 500);
        return;
    }

    // Get specific workspace
    const workspaceMatch = req.url.match(/^\/api\/workspaces\/([a-z0-9-]+)$/);
    if (workspaceMatch && req.method === 'GET') {
        const id = workspaceMatch[1];
        const workspace = mockWorkspaces.find(w => w.id === id);

        if (!workspace) {
            res.writeHead(404);
            res.end(JSON.stringify({ error: 'Workspace not found' }));
            return;
        }

        setTimeout(() => {
            res.writeHead(200);
            res.end(JSON.stringify(workspace));
        }, 300);
        return;
    }

    // Get workspace name suggestion
    if (req.url === '/api/workspaces/suggest-name' && req.method === 'GET') {
        setTimeout(() => {
            res.writeHead(200);
            res.end(JSON.stringify({ suggestion: 'New Workspace ' + Math.floor(Math.random() * 1000) }));
        }, 200);
        return;
    }

    // Create workspace
    if (req.url === '/api/workspaces' && req.method === 'POST') {
        setTimeout(() => {
            const newWorkspace = {
                id: 'workspace-' + Math.floor(Math.random() * 10000),
                name: 'New Workspace ' + Math.floor(Math.random() * 1000),
                description: 'Newly created workspace',
                isDefault: false,
                projectCount: 0,
                memberCount: 1,
            };
            res.writeHead(201);
            res.end(JSON.stringify(newWorkspace));
        }, 300);
        return;
    }

    // Get project name suggestion
    if (req.url.startsWith('/api/projects/suggest-name') && req.method === 'GET') {
        setTimeout(() => {
            res.writeHead(200);
            res.end(JSON.stringify({ suggestion: 'New Project ' + Math.floor(Math.random() * 1000) }));
        }, 200);
        return;
    }

    // Create project
    if (req.url === '/api/projects' && req.method === 'POST') {
        setTimeout(() => {
            const newProject = {
                id: 'project-' + Math.floor(Math.random() * 10000),
                name: 'New Project ' + Math.floor(Math.random() * 1000),
                type: 'webapp',
                workspaceId: 'workspace-1',
            };
            res.writeHead(201);
            res.end(JSON.stringify(newProject));
        }, 300);
        return;
    }

    // Get projects for workspace
    if (req.url.startsWith('/api/projects?workspaceId=') && req.method === 'GET') {
        setTimeout(() => {
            const mockProjects = [
                {
                    id: 'project-1',
                    name: 'Web App',
                    type: 'webapp',
                    workspaceId: 'workspace-1',
                },
                {
                    id: 'project-2', 
                    name: 'Mobile App',
                    type: 'mobile',
                    workspaceId: 'workspace-1',
                },
            ];
            res.writeHead(200);
            res.end(JSON.stringify({ owned: mockProjects, included: [] }));
        }, 300);
        return;
    }

    // 404
    res.writeHead(404);
    res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`[MOCK API] 🚀 Server running at http://localhost:${PORT}`);
    console.log('[MOCK API] ℹ️  This is a mock API for development');
    console.log('[MOCK API] 💡 To use real API, set up PostgreSQL and run: pnpm run dev');
    console.log('[MOCK API] 📝 Mock workspaces available at GET /api/workspaces');
});

server.on('error', (err) => {
    console.error('[MOCK API] ❌ Server error:', err.message);
    process.exit(1);
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('[MOCK API] Shutting down...');
    server.close(() => {
        console.log('[MOCK API] Server closed');
        process.exit(0);
    });
});
