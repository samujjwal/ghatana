/**
 * WorkspaceMembers Component Tests
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { WorkspaceMembers } from '../WorkspaceMembers';
import type { WorkspaceMember } from '../WorkspaceMembers';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const mockMembers: WorkspaceMember[] = [
  {
    id: 'wm-1',
    userId: 'user-1',
    workspaceId: 'ws-1',
    role: 'OWNER',
    joinedAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z',
    user: {
      id: 'user-1',
      name: 'Alice Owner',
      email: 'alice@example.com',
      role: 'OWNER',
      createdAt: '2026-01-01T00:00:00.000Z',
    },
  },
  {
    id: 'wm-2',
    userId: 'user-2',
    workspaceId: 'ws-1',
    role: 'EDITOR',
    joinedAt: '2026-01-05T00:00:00.000Z',
    updatedAt: '2026-01-05T00:00:00.000Z',
    user: {
      id: 'user-2',
      name: 'Bob Editor',
      email: 'bob@example.com',
      role: 'EDITOR',
      createdAt: '2026-01-05T00:00:00.000Z',
    },
  },
  {
    id: 'wm-3',
    userId: 'user-3',
    workspaceId: 'ws-1',
    role: 'VIEWER',
    joinedAt: '2026-01-10T00:00:00.000Z',
    updatedAt: '2026-01-10T00:00:00.000Z',
    user: {
      id: 'user-3',
      name: 'Carol Viewer',
      email: 'carol@example.com',
      role: 'VIEWER',
      createdAt: '2026-01-10T00:00:00.000Z',
    },
  },
];

describe('WorkspaceMembers', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows loading state initially', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => new Promise(() => {/* never resolves */}))
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByRole('status', { name: /loading members/i })).toBeDefined();
  });

  it('renders all members', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ members: mockMembers }),
        clone: () => ({ json: async () => ({ members: mockMembers }) }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByText('Alice Owner')).toBeDefined();
    });

    expect(screen.getByText('Bob Editor')).toBeDefined();
    expect(screen.getByText('Carol Viewer')).toBeDefined();
  });

  it('shows member count chip', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ members: mockMembers }),
        clone: () => ({ json: async () => ({ members: mockMembers }) }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByLabelText('3 members')).toBeDefined();
    });
  });

  it('shows Invite Member button for OWNER role', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ members: mockMembers }),
        clone: () => ({ json: async () => ({ members: mockMembers }) }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /invite member/i })).toBeDefined();
    });
  });

  it('does not show Invite Member button for VIEWER role', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ members: mockMembers }),
        clone: () => ({ json: async () => ({ members: mockMembers }) }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="VIEWER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByText('Alice Owner')).toBeDefined();
    });

    expect(screen.queryByRole('button', { name: /invite member/i })).toBeNull();
  });

  it('shows invite panel when Invite Member is clicked', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ members: mockMembers }),
        clone: () => ({ json: async () => ({ members: mockMembers }) }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /invite member/i })).toBeDefined();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /invite member/i }));

    expect(screen.getByText('Invite Member')).toBeDefined();
    expect(screen.getByRole('textbox', { name: /search users/i })).toBeDefined();
  });

  it('shows remove buttons for non-OWNER members when currentUser is OWNER', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ members: mockMembers }),
        clone: () => ({ json: async () => ({ members: mockMembers }) }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByText('Bob Editor')).toBeDefined();
    });

    // Bob Editor and Carol Viewer should have remove buttons
    expect(screen.getByRole('button', { name: /remove bob editor/i })).toBeDefined();
    expect(screen.getByRole('button', { name: /remove carol viewer/i })).toBeDefined();
    // Alice Owner should NOT have a remove button
    expect(screen.queryByRole('button', { name: /remove alice owner/i })).toBeNull();
  });

  it('shows error state on fetch failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        text: async () => JSON.stringify({ error: 'Internal error' }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeDefined();
    });
  });

  it('shows empty state when no members', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ members: [] }),
        clone: () => ({ json: async () => ({ members: [] }) }),
      })
    );

    render(
      <WorkspaceMembers workspaceId="ws-1" currentUserRole="OWNER" />,
      { wrapper: createWrapper() }
    );

    await waitFor(() => {
      expect(screen.getByText('No members yet')).toBeDefined();
    });
  });
});
