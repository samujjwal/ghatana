import type { Workspace, Project, Task } from '@ghatana/yappc-types';

// Mutable mock arrays (exported so node-side seed helpers can augment them)
export const mockWorkspaces: Workspace[] = [
  {
    id: 'ws1',
    name: 'Demo Workspace',
    description: 'A sample workspace for testing',
    ownerId: 'u1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  // Also expose a workspace id used by e2e specs
  {
    id: 'ws-1',
    name: 'Demo Workspace (ws-1)',
    description: 'Compatibility workspace for e2e tests',
    ownerId: 'u1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

export const mockProjects: Project[] = [
  {
    id: 'p1',
    workspaceId: 'ws1',
    name: 'Starter Project',
    description: 'A sample project to get started',
    type: 'UI',
    targets: ['web'],
    status: 'active',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  // Compatibility project id expected by some e2e specs
  {
    id: 'proj-1',
    workspaceId: 'ws-1',
    name: 'Demo Project (proj-1)',
    description: 'Compatibility project for e2e tests',
    type: 'UI',
    targets: ['web'],
    status: 'active',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

const mockTasks: Task[] = [
  {
    id: 't1',
    projectId: 'p1',
    title: 'Setup project structure',
    description: 'Initialize the project with basic structure',
    status: 'done',
    assigneeId: 'u1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 't2',
    projectId: 'p1',
    title: 'Implement authentication',
    description: 'Add user authentication system',
    status: 'in_progress',
    assigneeId: 'u1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

export const resolvers = {
  Query: {
    workspaces: () => {
      // If running in a browser during e2e and tests seeded workspaces into localStorage,
      // prefer those so Playwright/global-setup can deterministically control mock responses.
      try {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        if (typeof window !== 'undefined' && (window as unknown).localStorage) {
          const raw = (window as unknown).localStorage.getItem('e2e:mockWorkspaces');
          if (raw) {
            const parsed = JSON.parse(raw) as Workspace[];
            if (Array.isArray(parsed) && parsed.length > 0) return parsed;
          }
        }

        // If not in localStorage, attempt to fetch from the dev-server seed endpoint
        if (typeof fetch === 'function') {
          // allow this to be async via a synchronous-like fetch using a cached sync variable isn't possible here,
          // but we can block via a sync XHR is not available. Instead, try a fast synchronous Promise resolution via fetch
          // Note: resolvers are called synchronously in some environments; fetch may be unavailable. We attempt fetch and ignore failures.
           
          void (async () => {
            try {
              const resp = await fetch('/__e2e__/mock-data.json');
              if (resp && resp.ok) {
                const json = await resp.json();
                if (json && Array.isArray(json.workspaces) && json.workspaces.length > 0) {
                  // Mutate the in-memory mockWorkspaces so subsequent calls use it
                  try { (mockWorkspaces as unknown).length = 0; (mockWorkspaces as unknown).push(...json.workspaces); } catch (e) {}
                }
              }
            } catch (e) {
              // ignore
            }
          })();
        }
      } catch (e) {
        // ignore and fall back to in-memory mocks
      }
      return mockWorkspaces;
    },
    projects: (_: unknown, { workspaceId }: { workspaceId: string }) => {
      try {
        if (typeof window !== 'undefined' && (window as unknown).localStorage) {
          const raw = (window as unknown).localStorage.getItem('e2e:mockProjects');
          if (raw) {
            const parsed = JSON.parse(raw) as Project[];
            if (Array.isArray(parsed)) return parsed.filter((p: Project) => p.workspaceId === workspaceId);
          }
        }

        if (typeof fetch === 'function') {
          void (async () => {
            try {
              const resp = await fetch('/__e2e__/mock-data.json');
              if (resp && resp.ok) {
                const json = await resp.json();
                if (json && Array.isArray(json.projects)) {
                  try { (mockProjects as unknown).length = 0; (mockProjects as unknown).push(...json.projects); } catch (e) {}
                }
              }
            } catch (e) {
              // ignore
            }
          })();
        }
      } catch (e) {
        // ignore and fall back
      }
      return mockProjects.filter((p: Project) => p.workspaceId === workspaceId);
    },
    projectKpis: (_: unknown, { projectId }: { projectId: string }) => ({
      completionPercentage: 65.5,
      openTasks: 3,
      blockers: 1,
      totalTasks: 8,
    }),
    tasksForUser: (_: unknown, { userId }: { userId: string }) =>
      mockTasks.filter((t: Task) => t.assigneeId === userId),
    mentionsForUser: () => [],
    notificationsForUser: () => [],
  },
  Mutation: {
    updateTaskStatus: (_: unknown, { taskId, status }: { taskId: string; status: string }) => {
      const task = mockTasks.find(t => t.id === taskId);
      if (task) {
        task.status = status as unknown;
        task.updatedAt = new Date().toISOString();
      }
      return task;
    },
    addComment: (_: unknown, { taskId, content }: { taskId: string; content: string }) => ({
      id: `c${Date.now()}`,
      taskId,
      authorId: 'u1',
      content,
      attachments: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }),
  },
};
