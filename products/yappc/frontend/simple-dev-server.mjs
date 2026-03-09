/**
 * Simple Development API Server
 * 
 * A minimal API server for development when the full server has issues
 * 
 * @doc.type server
 * @doc.purpose Development API server
 * @doc.layer development
 * @doc.pattern Mock Server
 */

import { createServer } from 'http';

const PORT = 7003;
const API_BASE = '/api';

// Mock data
const mockWorkspaces = [
  {
    id: 'ws-1',
    name: 'Development Workspace',
    description: 'Default development workspace',
    ownerId: 'user-1',
    isDefault: true,
    aiSummary: 'Development workspace for testing',
    aiTags: ['development', 'testing'],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'ws-2',
    name: 'Test Workspace',
    description: 'Workspace for testing',
    ownerId: 'user-1',
    isDefault: false,
    aiSummary: 'Test workspace',
    aiTags: ['testing'],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

const mockProjects = [
  {
    id: 'proj-1',
    workspaceId: 'ws-1',
    name: 'Development Project',
    description: 'Default development project',
    type: 'web-app',
    targets: ['web'],
    status: 'active',
    aiSummary: 'Development project for testing',
    aiTags: ['development', 'web'],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'proj-2',
    workspaceId: 'ws-1',
    name: 'Test Project',
    description: 'Test project',
    type: 'mobile-app',
    targets: ['mobile'],
    status: 'active',
    aiSummary: 'Test project',
    aiTags: ['testing', 'mobile'],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

// CORS headers
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Content-Type': 'application/json',
};

// Helper function to send JSON response
function sendJson(res, data, statusCode = 200) {
  res.writeHead(statusCode, corsHeaders);
  res.end(JSON.stringify(data));
}

// Helper function to handle OPTIONS requests
function handleOptions(res) {
  res.writeHead(200, corsHeaders);
  res.end();
}

// Routes
const routes = {
  '/health': () => ({ status: 'ok', timestamp: new Date().toISOString() }),
  '/api/workspaces': () => mockWorkspaces,
  '/api/workspaces/suggest-name': () => ({ suggestions: ['My Workspace', 'Project Alpha', 'Development Hub'] }),
  '/api/projects': () => mockProjects,
  '/api/projects/suggest-name': () => ({ suggestions: ['Web App', 'Mobile App', 'API Service'] }),
};

// Create server
const server = createServer((req, res) => {
  // Enable CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  // Handle OPTIONS requests
  if (req.method === 'OPTIONS') {
    handleOptions(res);
    return;
  }

  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;

  console.log(`${req.method} ${path}`);

  // Route handling
  if (routes[path]) {
    if (req.method === 'GET') {
      sendJson(res, routes[path]());
    } else if (req.method === 'POST') {
      // Handle POST requests
      let body = '';
      req.on('data', chunk => {
        body += chunk;
      });
      req.on('end', () => {
        try {
          const data = JSON.parse(body);
          console.log('POST data:', data);
          
          // Mock creation responses
          if (path === '/api/workspaces') {
            const newWorkspace = {
              id: `ws-${Date.now()}`,
              ...data,
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            };
            sendJson(res, newWorkspace, 201);
          } else if (path === '/api/projects') {
            const newProject = {
              id: `proj-${Date.now()}`,
              ...data,
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            };
            sendJson(res, newProject, 201);
          } else {
            sendJson(res, { error: 'Not found' }, 404);
          }
        } catch (error) {
          console.error('Error parsing JSON:', error);
          sendJson(res, { error: 'Invalid JSON' }, 400);
        }
      });
    } else {
      sendJson(res, { error: 'Method not allowed' }, 405);
    }
  } else {
    // 404 for unknown routes
    sendJson(res, { error: 'Not found' }, 404);
  }
});

server.listen(PORT, () => {
  console.log(`🚀 Development API Server running on http://localhost:${PORT}`);
  console.log('📡 Available endpoints:');
  console.log('   GET  /health');
  console.log('   GET  /api/workspaces');
  console.log('   POST /api/workspaces');
  console.log('   GET  /api/projects');
  console.log('   POST /api/projects');
  console.log('   GET  /api/workspaces/suggest-name');
  console.log('   GET  /api/projects/suggest-name');
  console.log('');
  console.log('🔧 This is a development server with mock data');
  console.log('📝 Use this while the main API server is being fixed');
});

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n🛑 Shutting down development API server...');
  server.close(() => {
    console.log('✅ Server stopped');
    process.exit(0);
  });
});
