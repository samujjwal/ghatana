import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'jotai';
import { useHydrateAtoms } from 'jotai/utils';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CreateProjectDialog } from '../CreateProjectDialog';
import {
  workspaceAtom,
  type WorkspaceState,
} from '../../../state/atoms/workspaceAtom';

const hydratedWorkspaceState: WorkspaceState = {
  currentWorkspace: {
    id: 'ws-123',
    name: 'Platform Launch',
    description: 'Workspace for platform work',
    ownerId: 'user-1',
    isDefault: true,
    aiSummary: '',
    aiTags: [],
    createdAt: '2026-04-17T00:00:00.000Z',
    updatedAt: '2026-04-17T00:00:00.000Z',
  },
  availableWorkspaces: [],
  ownedProjects: [],
  includedProjects: [],
  isLoading: false,
  isCreating: false,
  isSwitching: false,
};

function HydrateWorkspaceState({ children }: { children: React.ReactNode }): React.JSX.Element {
  useHydrateAtoms([[workspaceAtom, hydratedWorkspaceState]]);
  return <>{children}</>;
}

function createWrapper(): React.ComponentType<{ children: React.ReactNode }> {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <Provider>
        <HydrateWorkspaceState>
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        </HydrateWorkspaceState>
      </Provider>
    );
  };
}

describe('CreateProjectDialog', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('uses the server-side setup suggestion to infer project type and related projects', async () => {
    const onClose = vi.fn();
    const onCreated = vi.fn();

    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();

      if (url.endsWith('/api/workspaces')) {
        return new Response(
          JSON.stringify([
            {
              id: 'ws-123',
              name: 'Platform Launch',
              description: 'Workspace for platform work',
              ownerId: 'user-1',
              isDefault: true,
              aiSummary: '',
              aiTags: [],
              createdAt: '2026-04-17T00:00:00.000Z',
              updatedAt: '2026-04-17T00:00:00.000Z',
            },
          ]),
          {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }
        );
      }

      if (url.endsWith('/api/workspaces/ws-123')) {
        return new Response(
          JSON.stringify({
            id: 'ws-123',
            name: 'Platform Launch',
            description: 'Workspace for platform work',
            ownerId: 'user-1',
            isDefault: true,
            aiSummary: '',
            aiTags: [],
            ownedProjects: [],
            includedProjects: [],
            createdAt: '2026-04-17T00:00:00.000Z',
            updatedAt: '2026-04-17T00:00:00.000Z',
          }),
          {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }
        );
      }

      if (url.endsWith('/api/projects/setup-suggestion') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            suggestion: 'Platform Launch API',
            inferredType: 'BACKEND',
            rationale: 'The description emphasizes API and authentication work.',
            summary: 'Guidance suggests a backend project for this description.',
            recommendations: ['Define the core API contract and integration boundaries before scaffolding.'],
            relatedProjects: [
              {
                id: 'project-1',
                name: 'Identity API',
                type: 'BACKEND',
                ownerWorkspaceId: 'ws-shared',
                ownerWorkspaceName: 'Shared Services',
              },
            ],
          }),
          {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }
        );
      }

      if (url.includes('/api/projects/suggest-name')) {
        return new Response(JSON.stringify({ suggestion: 'Fallback Project' }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }

      if (url.endsWith('/api/projects') && init?.method === 'POST') {
        return new Response(
          JSON.stringify({
            project: {
              id: 'project-123',
              name: 'Platform Launch API',
              description: 'Authenticated API for mobile clients',
              type: 'BACKEND',
              ownerWorkspaceId: 'ws-123',
              status: 'DRAFT',
              lifecyclePhase: 'planning',
              isDefault: false,
              createdAt: '2026-04-17T00:00:00.000Z',
              updatedAt: '2026-04-17T00:00:00.000Z',
              aiNextActions: [],
              aiHealthScore: 0,
            },
          }),
          {
            status: 201,
            headers: { 'content-type': 'application/json' },
          }
        );
      }

      return new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    });

    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();

    render(
      <CreateProjectDialog isOpen onClose={onClose} onCreated={onCreated} />,
      { wrapper: createWrapper() }
    );

    await user.type(
      screen.getByTestId('project-description-input'),
      'Authenticated API for mobile clients'
    );

    await waitFor(() => {
      expect(screen.getByText('Platform Launch API')).toBeInTheDocument();
      expect(screen.getByText(/Guidance suggests a backend project/i)).toBeInTheDocument();
    });

    expect(screen.queryByText(/Identity API in Shared Services/i)).not.toBeInTheDocument();

    await user.click(screen.getByTestId('show-advanced-ai-context'));

    await waitFor(() => {
      expect(screen.getByTestId('advanced-ai-context-panel')).toBeInTheDocument();
      expect(screen.getByText(/Identity API in Shared Services/i)).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: 'Use This' }));
    expect(screen.getByTestId('project-name-input')).toHaveValue('Platform Launch API');

    await user.click(screen.getByTestId('create-project-submit'));

    await waitFor(() => {
      const createProjectCall = fetchMock.mock.calls.find(
        ([url, init]) => url === '/api/projects' && init?.method === 'POST'
      );
      expect(createProjectCall).toBeDefined();

      const [, init] = createProjectCall as [RequestInfo | URL, RequestInit];
      const parsedBody = JSON.parse(String(init.body));
      expect(parsedBody).toMatchObject({
        name: 'Platform Launch API',
        type: 'BACKEND',
        workspaceId: 'ws-123',
      });
      expect(typeof parsedBody.description).toBe('string');
      expect(parsedBody.description.length).toBeGreaterThan(0);
    });

    await waitFor(() => {
      expect(onCreated).toHaveBeenCalledWith(
        expect.objectContaining({ id: 'project-123', name: 'Platform Launch API' })
      );
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });
});