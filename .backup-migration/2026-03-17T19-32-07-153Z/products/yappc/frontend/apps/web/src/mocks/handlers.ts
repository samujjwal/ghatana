import { http, HttpResponse, delay } from 'msw';
import type {
  ApiResponse,
  DevSecOpsOverview,
  Item,
  BulkOperationResult,
} from '@ghatana/yappc-types/devsecops';
import { createDevSecOpsOverview } from '@ghatana/yappc-types/devsecops/fixtures';

// Types
/**
 *
 */
type Agent = {
  id: string;
  name: string;
  role: string;
  status: 'online' | 'offline' | 'busy';
  lastActive: string;
  skills?: string[];
};

let devsecopsState: DevSecOpsOverview | null = null;

const getDevSecOpsState = (): DevSecOpsOverview => {
  if (!devsecopsState) {
    devsecopsState = createDevSecOpsOverview();
  }
  return devsecopsState;
};

// Mock data
let mockAgents: Agent[] = [
  {
    id: '1',
    name: 'Agent Smith',
    role: 'Developer',
    status: 'online',
    lastActive: new Date().toISOString(),
    skills: ['TypeScript', 'React', 'Node.js']
  },
  {
    id: '2',
    name: 'John Doe',
    role: 'Designer',
    status: 'busy',
    lastActive: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    skills: ['Figma', 'UI/UX', 'Prototyping']
  },
  {
    id: '3',
    name: 'Jane Smith',
    role: 'QA Engineer',
    status: 'offline',
    lastActive: new Date(Date.now() - 1000 * 60 * 60).toISOString(),
    skills: ['Testing', 'Automation', 'Jest']
  }
];

// Simulate network delay
const simulateNetwork = async () => {
  await delay(150 + Math.random() * 100);
};

export const handlers = [
  // Get all agents
  http.get('/api/agents', async () => {
    await simulateNetwork();
    return HttpResponse.json(mockAgents);
  }),

  http.get('/api/devsecops/overview', async () => {
    await simulateNetwork();
    const data = getDevSecOpsState();

    const response: ApiResponse<DevSecOpsOverview> = {
      data,
      success: true,
      metadata: {
        timestamp: new Date().toISOString(),
        requestId: Math.random().toString(36).slice(2),
      },
    };

    return HttpResponse.json(response);
  }),

  // DevSecOps items - list with optional filters
  http.get('/api/devsecops/items', async ({ request }) => {
    await simulateNetwork();
    const url = new URL(request.url);
    const phaseIds = url.searchParams.getAll('phaseId');
    const statuses = url.searchParams.getAll('status');
    const priorities = url.searchParams.getAll('priority');
    const tags = url.searchParams.getAll('tag');
    const search = url.searchParams.get('search')?.toLowerCase() ?? '';

    let items: Item[] = [...getDevSecOpsState().items];

    if (phaseIds.length) {
      items = items.filter((item) => phaseIds.includes(item.phaseId));
    }

    if (statuses.length) {
      items = items.filter((item) => statuses.includes(item.status));
    }

    if (priorities.length) {
      items = items.filter((item) => item.priority && priorities.includes(item.priority));
    }

    if (tags.length) {
      items = items.filter((item) =>
        item.tags?.some((tag) => tags.includes(tag)),
      );
    }

    if (search) {
      items = items.filter((item) =>
        item.title.toLowerCase().includes(search) ||
        item.description?.toLowerCase().includes(search),
      );
    }

    const response: ApiResponse<Item[]> = {
      data: items,
      success: true,
      metadata: {
        timestamp: new Date().toISOString(),
        requestId: Math.random().toString(36).slice(2),
      },
    };

    return HttpResponse.json(response);
  }),

  // DevSecOps item detail
  http.get('/api/devsecops/items/:id', async ({ params }) => {
    await simulateNetwork();
    const state = getDevSecOpsState();
    const item = state.items.find((i) => i.id === params.id);

    if (!item) {
      return new HttpResponse('Item not found', { status: 404 });
    }

    const response: ApiResponse<Item> = {
      data: item,
      success: true,
      metadata: {
        timestamp: new Date().toISOString(),
        requestId: Math.random().toString(36).slice(2),
      },
    };

    return HttpResponse.json(response);
  }),

  // Create DevSecOps item
  http.post('/api/devsecops/items', async ({ request }) => {
    await simulateNetwork();
    const state = getDevSecOpsState();
    const body = (await request.json()) as Partial<Item>;

    const now = new Date().toISOString();
    const newItem: Item = {
      id: body.id ?? `item-${Math.random().toString(36).slice(2)}`,
      title: body.title ?? 'New DevSecOps Item',
      description: body.description ?? '',
      type: body.type ?? 'task',
      status: body.status ?? 'not-started',
      priority: body.priority ?? 'medium',
      phaseId: body.phaseId ?? state.phases[0]?.id ?? 'ideation',
      owners: body.owners ?? [],
      tags: body.tags ?? [],
      createdAt: body.createdAt ?? now,
      updatedAt: now,
      estimatedHours: body.estimatedHours ?? 8,
      actualHours: body.actualHours,
      dueDate: body.dueDate,
      completedAt: body.completedAt,
      progress: body.progress ?? 0,
      artifacts: body.artifacts ?? [],
      integrations: body.integrations ?? [],
      metadata: body.metadata ?? {},
    };

    state.items.push(newItem);

    const response: ApiResponse<Item> = {
      data: newItem,
      success: true,
      metadata: {
        timestamp: now,
        requestId: Math.random().toString(36).slice(2),
      },
    };

    return HttpResponse.json(response, { status: 201 });
  }),

  // Update DevSecOps item
  http.patch('/api/devsecops/items/:id', async ({ params, request }) => {
    await simulateNetwork();
    const state = getDevSecOpsState();
    const body = (await request.json()) as Partial<Item>;
    const index = state.items.findIndex((i) => i.id === params.id);

    if (index === -1) {
      return new HttpResponse('Item not found', { status: 404 });
    }

    const now = new Date().toISOString();
    const updated: Item = {
      ...state.items[index],
      ...body,
      updatedAt: now,
    };

    state.items[index] = updated;

    const response: ApiResponse<Item> = {
      data: updated,
      success: true,
      metadata: {
        timestamp: now,
        requestId: Math.random().toString(36).slice(2),
      },
    };

    return HttpResponse.json(response);
  }),

  // Bulk update DevSecOps items
  http.patch('/api/devsecops/items/bulk', async ({ request }) => {
    await simulateNetwork();
    const state = getDevSecOpsState();
    const body = (await request.json()) as {
      itemIds: string[];
      data: Partial<Item>;
    };

    let successCount = 0;
    const errors: BulkOperationResult['errors'] = [];

    const now = new Date().toISOString();

    for (const id of body.itemIds) {
      const index = state.items.findIndex((i) => i.id === id);
      if (index === -1) {
        errors.push({ itemId: id, error: 'Item not found' });
        continue;
      }

      state.items[index] = {
        ...state.items[index],
        ...body.data,
        updatedAt: now,
      };
      successCount++;
    }

    const result: BulkOperationResult = {
      successCount,
      failureCount: body.itemIds.length - successCount,
      errors: errors.length ? errors : undefined,
    };

    const response: ApiResponse<BulkOperationResult> = {
      data: result,
      success: true,
      metadata: {
        timestamp: now,
        requestId: Math.random().toString(36).slice(2),
      },
    };

    return HttpResponse.json(response);
  }),

  // Delete DevSecOps item
  http.delete('/api/devsecops/items/:id', async ({ params }) => {
    await simulateNetwork();
    const state = getDevSecOpsState();
    const before = state.items.length;
    state.items = state.items.filter((i) => i.id !== params.id);

    const found = state.items.length !== before;

    if (!found) {
      return new HttpResponse('Item not found', { status: 404 });
    }

    const response: ApiResponse<boolean> = {
      data: true,
      success: true,
      metadata: {
        timestamp: new Date().toISOString(),
        requestId: Math.random().toString(36).slice(2),
      },
    };

    return HttpResponse.json(response);
  }),

  // Get agent by ID
  http.get('/api/agents/:id', async ({ params }) => {
    await simulateNetwork();
    const agent = mockAgents.find(a => a.id === params.id);
    if (!agent) {
      return new HttpResponse('Agent not found', { status: 404 });
    }
    return HttpResponse.json(agent);
  }),

  // Create new agent
  http.post('/api/agents', async ({ request }) => {
    await simulateNetwork();
    const newAgent = await request.json() as Partial<Agent>;
    const agent: Agent = {
      id: Math.random().toString(36).substring(2, 9),
      name: newAgent.name || 'New Agent',
      role: newAgent.role || 'Developer',
      status: 'online',
      lastActive: new Date().toISOString(),
      skills: newAgent.skills || []
    };
    mockAgents.push(agent);
    return HttpResponse.json(agent, { status: 201 });
  }),

  // Update agent
  http.put('/api/agents/:id', async ({ params, request }) => {
    await simulateNetwork();
    const updates = await request.json() as Partial<Agent>;
    const index = mockAgents.findIndex(a => a.id === params.id);

    if (index === -1) {
      return new HttpResponse('Agent not found', { status: 404 });
    }

    mockAgents[index] = { ...mockAgents[index], ...updates };
    return HttpResponse.json(mockAgents[index]);
  }),

  // Delete agent
  http.delete('/api/agents/:id', async ({ params }) => {
    await simulateNetwork();
    const initialLength = mockAgents.length;
    mockAgents = mockAgents.filter(a => a.id !== params.id);

    if (mockAgents.length === initialLength) {
      return new HttpResponse('Agent not found', { status: 404 });
    }

    return new HttpResponse(null, { status: 204 });
  }),

  // Search agents
  http.get('/api/agents/search', async ({ request }) => {
    await simulateNetwork();
    const url = new URL(request.url);
    const query = url.searchParams.get('q')?.toLowerCase() || '';

    const results = mockAgents.filter(agent =>
      agent.name.toLowerCase().includes(query) ||
      agent.role.toLowerCase().includes(query) ||
      agent.skills?.some(skill => skill.toLowerCase().includes(query))
    );

    return HttpResponse.json(results);
  }),

  // GraphQL endpoint - return deterministic project data for e2e
  http.post('/graphql', async ({ request }) => {
    try {
      const raw = await request.text();
      if (!raw) return HttpResponse.json({ data: null });
      let body: unknown;
      try {
        body = JSON.parse(raw);
      } catch (err) {
        return HttpResponse.json({ data: null });
      }
      const operationName = typeof body.operationName === 'string' ? body.operationName : (typeof body.query === 'string' ? (body.query.match(/query\s+(\w+)/) || [])[1] : undefined);
      const variables = body && typeof body.variables === 'object' ? body.variables : {};

      // Debug logging to help triage e2e runs
      try { console.debug('[MSW][runtime] GraphQL op=', operationName, 'vars=', variables); } catch (e) { /* ignore */ }

      // If e2e seed arrays were written to localStorage in the browser, prefer those.
      try {
        const rawWorkspaces = (typeof window !== 'undefined' && window.localStorage) ? window.localStorage.getItem('e2e:mockWorkspaces') : null;
        const rawProjects = (typeof window !== 'undefined' && window.localStorage) ? window.localStorage.getItem('e2e:mockProjects') : null;
        const seededWorkspaces = rawWorkspaces ? JSON.parse(rawWorkspaces) : null;
        const seededProjects = rawProjects ? JSON.parse(rawProjects) : null;

        if (seededProjects && seededProjects.length) {
          const workspaceId = variables.workspaceId || seededProjects[0].workspaceId || (seededWorkspaces && seededWorkspaces[0]?.id) || 'ws-1';
          const project = { __typename: 'Project', ...seededProjects[0], workspaceId };
          if (body.query && body.query.includes('projects')) {
            return HttpResponse.json({ data: { projects: [project] } });
          }
          return HttpResponse.json({ data: { project } });
        }
      } catch (e) {
        // ignore parsing errors and fall back to deterministic data below
      }

      const workspaceId = variables.workspaceId || 'ws-1';

      // Return consistent project data that matches the shell expectations
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

      // Aggressively match project-related queries and return deterministic data
      if (
        operationName === 'GetProjects' ||
        operationName === 'GetProject' ||
        (body.query && (body.query.includes('projects') || body.query.includes('project'))) ||
        variables.workspaceId ||
        variables.projectId
      ) {
        if (body.query && body.query.includes('projects')) {
          return HttpResponse.json({ data: { projects: [project] } });
        }
        return HttpResponse.json({ data: { project } });
      }
    } catch (err) {
      // fallback
    }
    return HttpResponse.json({ data: null });
  }),
];
