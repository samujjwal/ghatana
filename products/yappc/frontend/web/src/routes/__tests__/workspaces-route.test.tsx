import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockNavigate = vi.fn();
const mockSwitchWorkspace = vi.fn();
const mockMutateAsync = vi.fn();
const mockSuggestWorkspace = vi.fn();

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('../../hooks/useWorkspaceData', () => ({
  useWorkspaceContext: vi.fn(),
  useCreateWorkspace: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  }),
  useNameSuggestions: () => ({
    suggestWorkspace: mockSuggestWorkspace,
    suggestProject: vi.fn(),
  }),
}));

vi.mock('../../components/workspace/CreateWorkspaceDialog', () => ({
  CreateWorkspaceDialog: ({ isOpen }: { isOpen: boolean }) =>
    isOpen ? <div data-testid="create-workspace-dialog" /> : null,
}));

import { useWorkspaceContext } from '../../hooks/useWorkspaceData';
import WorkspacesRoute from '../app/workspaces';

function renderRoute(): void {
  render(
    <MemoryRouter>
      <WorkspacesRoute />
    </MemoryRouter>
  );
}

describe('WorkspacesRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockMutateAsync.mockResolvedValue({ id: 'ws-starter', name: 'Starter Workspace' });
    mockSuggestWorkspace.mockResolvedValue('Starter Workspace');
  });

  it('auto-creates a starter workspace on the first empty-state entry', async () => {
    vi.mocked(useWorkspaceContext).mockReturnValue({
      workspaces: [],
      currentWorkspace: undefined,
      currentWorkspaceId: null,
      ownedProjects: [],
      includedProjects: [],
      isLoading: false,
      error: null,
      switchWorkspace: mockSwitchWorkspace,
      refetch: vi.fn(),
    });

    renderRoute();

    await waitFor(() => {
      expect(mockSuggestWorkspace).toHaveBeenCalledTimes(1);
      expect(mockMutateAsync).toHaveBeenCalledWith({
        name: 'Starter Workspace',
        createDefaultProject: true,
      });
    });

    expect(mockSwitchWorkspace).toHaveBeenCalledWith('ws-starter');
    expect(mockNavigate).toHaveBeenCalledWith('/projects');
  });

  it('switches the active workspace before navigating when a workspace is opened', async () => {
    vi.mocked(useWorkspaceContext).mockReturnValue({
      workspaces: [
        {
          id: 'ws-1',
          name: 'Alpha',
          description: 'Workspace Alpha',
          ownerId: 'user-1',
          isDefault: false,
          createdAt: '2026-04-17T00:00:00.000Z',
          updatedAt: '2026-04-17T00:00:00.000Z',
          aiSummary: '',
          aiTags: [],
          projectCount: 0,
          memberCount: 1,
        },
      ],
      currentWorkspace: undefined,
      currentWorkspaceId: null,
      ownedProjects: [],
      includedProjects: [],
      isLoading: false,
      error: null,
      switchWorkspace: mockSwitchWorkspace,
      refetch: vi.fn(),
    });

    const user = userEvent.setup();

    renderRoute();

    await user.click(screen.getByRole('button', { name: 'Open' }));

    expect(mockSwitchWorkspace).toHaveBeenCalledWith('ws-1');
    expect(mockNavigate).toHaveBeenCalledWith('/projects');
  });
});