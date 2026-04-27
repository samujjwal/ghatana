/**
 * Tests for workspace/ components:
 * WorkspaceSelector, HeaderWithBreadcrumb, AgentActivityBadge, ProjectListPanel
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { createStore, Provider } from 'jotai';
import { WorkspaceSelector } from '../WorkspaceSelector';
import { workspaceAtom, type Workspace } from '../../../state/atoms/workspaceAtom';

// ─── WorkspaceSelector ────────────────────────────────────────────────────────

function makeWorkspace(overrides: Partial<Workspace> = {}): Workspace {
  return {
    id: 'ws-1',
    name: 'My Workspace',
    ownerId: 'user-1',
    isDefault: true,
    aiTags: [],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('WorkspaceSelector', () => {
  it('shows "Select Workspace" when no workspace selected', () => {
    const store = createStore();
    render(
      <Provider store={store}>
        <WorkspaceSelector />
      </Provider>
    );
    expect(screen.getByText('Select Workspace')).toBeTruthy();
  });

  it('shows current workspace name', () => {
    const store = createStore();
    store.set(workspaceAtom, {
      currentWorkspace: makeWorkspace({ name: 'Design Team' }),
      availableWorkspaces: [],
      ownedProjects: [],
      includedProjects: [],
      isLoading: false,
      isCreating: false,
      isSwitching: false,
    });
    render(
      <Provider store={store}>
        <WorkspaceSelector />
      </Provider>
    );
    expect(screen.getByText('Design Team')).toBeTruthy();
  });

  it('renders trigger button with aria-haspopup', () => {
    const store = createStore();
    render(
      <Provider store={store}>
        <WorkspaceSelector />
      </Provider>
    );
    const btn = screen.getByTestId('workspace-selector');
    expect(btn.getAttribute('aria-haspopup')).toBe('listbox');
  });

  it('opens dropdown on click', () => {
    const store = createStore();
    store.set(workspaceAtom, {
      currentWorkspace: null,
      availableWorkspaces: [makeWorkspace({ name: 'Team Alpha' })],
      ownedProjects: [],
      includedProjects: [],
      isLoading: false,
      isCreating: false,
      isSwitching: false,
    });
    render(
      <Provider store={store}>
        <WorkspaceSelector />
      </Provider>
    );
    const btn = screen.getByTestId('workspace-selector');
    fireEvent.click(btn);
    expect(screen.getByRole('listbox')).toBeTruthy();
  });

  it('renders "Create New Workspace" when onCreateNew provided and dropdown open', () => {
    const store = createStore();
    const onCreateNew = vi.fn();
    render(
      <Provider store={store}>
        <WorkspaceSelector onCreateNew={onCreateNew} />
      </Provider>
    );
    // Open dropdown
    fireEvent.click(screen.getByTestId('workspace-selector'));
    expect(screen.getByText('Create Workspace')).toBeTruthy();
  });

  it('calls onCreateNew when create button clicked', () => {
    const store = createStore();
    const onCreateNew = vi.fn();
    render(
      <Provider store={store}>
        <WorkspaceSelector onCreateNew={onCreateNew} />
      </Provider>
    );
    fireEvent.click(screen.getByTestId('workspace-selector'));
    const createBtn = screen.getByText('Create Workspace');
    fireEvent.click(createBtn);
    expect(onCreateNew).toHaveBeenCalledOnce();
  });
});


