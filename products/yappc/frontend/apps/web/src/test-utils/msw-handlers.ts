import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';

import { common } from './factories';

// Example API handlers
export const handlers = [
  // GET /api/users - List users
  http.get('/api/users', async () => {
    await delay(150); // Simulate network delay
    return HttpResponse.json({
      data: Array.from({ length: 10 }, (_, i) => ({
        id: common.uuid(),
        name: common.name(),
        email: common.email(),
        role: i === 0 ? 'admin' : 'user',
      })),
      total: 10,
      page: 1,
      limit: 10,
    });
  }),

  // GET /api/users/:id - Get user by ID
  http.get('/api/users/:id', async ({ params }) => {
    const { id } = params;
    await delay(100);
    return HttpResponse.json({
      id,
      name: common.name(),
      email: common.email(),
      role: 'user',
      createdAt: common.date(),
      updatedAt: common.date(),
    });
  }),

  // POST /api/auth/login - User login
  http.post('/api/auth/login', async ({ request }) => {
    const { email, password } = await request.json() as { email?: string; password?: string };

    // Simulate validation
    if (!email || !password) {
      return HttpResponse.json(
        { message: 'Email and password are required' },
        { status: 400 }
      );
    }

    await delay(200);
    return HttpResponse.json({
      user: {
        id: common.uuid(),
        email,
        name: common.name(),
        role: 'user',
      },
      token: 'test-jwt-token',
      expiresIn: 3600,
    });
  }),

  // GraphQL endpoint - respond to GetProjects queries for e2e determinism
  http.post('/graphql', async ({ request }) => {
    try {
      const raw = await request.text();
      if (!raw) return HttpResponse.json({ data: null });
      let body: unknown;
      try {
        body = JSON.parse(raw);
      } catch (err) {
        // not JSON, skip
        return HttpResponse.json({ data: null });
      }
      const operationName = typeof body.operationName === 'string' ? body.operationName : (typeof body.query === 'string' ? (body.query.match(/query\s+(\w+)/) || [])[1] : undefined);
      const variables = body && typeof body.variables === 'object' ? body.variables : {};

      // Debug: print operationName and variables in Node test logs to help triage
      try { console.debug('[MSW][test-utils] GraphQL op=', operationName, 'vars=', variables); } catch (e) { /* ignore */ }

      const workspaceId = variables.workspaceId || 'ws-1';
      const project = {
        __typename: 'Project',
        id: 'proj-1',
        workspaceId,
        name: 'E-commerce Platform',
        description: 'Modern e-commerce solution with React and Node.js',
        type: 'application',
        targets: ['web', 'mobile'],
        status: 'active',
        createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
        updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
      };

      // If request queries projects/ project or provides workspaceId/projectId, return project(s)
      if (
        operationName === 'GetProjects' ||
        operationName === 'GetProject' ||
        (body.query && (body.query.includes('projects') || body.query.includes('project'))) ||
        variables.workspaceId ||
        variables.projectId
      ) {
        // If it's a list request, return projects array
        if (body.query && body.query.includes('projects')) {
          return HttpResponse.json({ data: { projects: [project] } });
        }
        // Otherwise return single project
        return HttpResponse.json({ data: { project } });
      }
    } catch (err) {
      // fallthrough to default
    }

    // Default GraphQL passthrough response
    return HttpResponse.json({ data: null });
  }),

  // Add more API handlers as needed
];

// Setup MSW server for Node.js environment
export const server = setupServer(...handlers);

// Setup MSW for browser environment
if (typeof window !== 'undefined') {
  const { worker } = require('./browser');
  worker.start({
    onUnhandledRequest: 'bypass',
  });
}

// Export utilities for testing
export { http, HttpResponse, delay } from 'msw';

export const createServer = (customHandlers = []) => {
  return setupServer(...[...handlers, ...customHandlers]);
};
