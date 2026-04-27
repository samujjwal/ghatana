/**
 * BacklogBoard Component Tests
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BacklogBoard } from '../BacklogBoard';
import type { BacklogItem } from '../BacklogBoard';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const mockItems: BacklogItem[] = [
  {
    id: 'item-1',
    title: 'User authentication flow',
    type: 'STORY',
    status: 'NOT_STARTED',
    priority: 'HIGH',
    phaseId: 'phase-1',
    sprintId: null,
    storyPoints: 5,
  },
  {
    id: 'item-2',
    title: 'Fix login bug',
    type: 'BUG',
    status: 'IN_PROGRESS',
    priority: 'CRITICAL',
    phaseId: 'phase-1',
    sprintId: null,
    storyPoints: 2,
  },
  {
    id: 'item-3',
    title: 'Refactor auth module',
    type: 'TECH_DEBT',
    status: 'BLOCKED',
    priority: 'MEDIUM',
    phaseId: 'phase-1',
    sprintId: null,
  },
];

describe('BacklogBoard', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows loading state while fetching', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(
        () => new Promise(() => {/* never resolves */})
      )
    );

    render(<BacklogBoard projectId="proj-1" />, { wrapper: createWrapper() });

    expect(screen.getByRole('status', { name: /loading backlog/i })).toBeDefined();
  });

  it('renders backlog items grouped by status', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ items: mockItems }),
        clone: () => ({
          json: async () => ({ items: mockItems }),
        }),
      })
    );

    render(<BacklogBoard projectId="proj-1" />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText('User authentication flow')).toBeDefined();
    });

    expect(screen.getByText('Fix login bug')).toBeDefined();
    expect(screen.getByText('Refactor auth module')).toBeDefined();
  });

  it('shows error state when fetch fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        text: async () => JSON.stringify({ error: 'Server error' }),
      })
    );

    render(<BacklogBoard projectId="proj-1" />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeDefined();
    });
  });

  it('shows empty state when no items', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ items: [] }),
        clone: () => ({ json: async () => ({ items: [] }) }),
      })
    );

    render(<BacklogBoard projectId="proj-1" />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText('No backlog items found')).toBeDefined();
    });
  });

  it('displays sprint move buttons when activeSprints provided', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ items: mockItems }),
        clone: () => ({ json: async () => ({ items: mockItems }) }),
      })
    );

    const activeSprints = [{ id: 'sprint-1', name: 'Sprint 1' }];

    render(<BacklogBoard projectId="proj-1" activeSprints={activeSprints} />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      const sprintButtons = screen.getAllByText('Sprint 1');
      expect(sprintButtons.length).toBeGreaterThan(0);
    });
  });

  it('filters items by type when filter is selected', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ items: mockItems }),
        clone: () => ({ json: async () => ({ items: mockItems }) }),
      })
    );

    render(<BacklogBoard projectId="proj-1" />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText('User authentication flow')).toBeDefined();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /bugs/i }));

    expect(screen.getByText('Fix login bug')).toBeDefined();
    expect(screen.queryByText('User authentication flow')).toBeNull();
  });

  it('displays item count badge', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ items: mockItems }),
        clone: () => ({ json: async () => ({ items: mockItems }) }),
      })
    );

    render(<BacklogBoard projectId="proj-1" />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText('3 items')).toBeDefined();
    });
  });

  it('displays Backlog Board heading', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ items: [] }),
        clone: () => ({ json: async () => ({ items: [] }) }),
      })
    );

    render(<BacklogBoard projectId="proj-1" />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByText('Backlog Board')).toBeDefined();
    });
  });
});
