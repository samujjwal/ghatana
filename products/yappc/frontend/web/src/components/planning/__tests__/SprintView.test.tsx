/**
 * SprintView Component Tests
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SprintView } from '../SprintView';
import type { Sprint, SprintViewProps } from '../SprintView';
import type { BacklogItem } from '../BacklogBoard';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const mockSprint: Sprint & { items: BacklogItem[] } = {
  id: 'sprint-1',
  projectId: 'proj-1',
  name: 'Sprint 1',
  goal: 'Complete authentication module',
  status: 'ACTIVE',
  startDate: '2026-05-01T00:00:00.000Z',
  endDate: '2026-05-14T00:00:00.000Z',
  capacity: 40,
  createdAt: '2026-04-30T00:00:00.000Z',
  updatedAt: '2026-04-30T00:00:00.000Z',
  items: [
    {
      id: 'item-1',
      title: 'Implement JWT validation',
      type: 'TASK',
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      phaseId: 'phase-1',
      sprintId: 'sprint-1',
      storyPoints: 3,
    },
    {
      id: 'item-2',
      title: 'Write auth unit tests',
      type: 'TASK',
      status: 'NOT_STARTED',
      priority: 'MEDIUM',
      phaseId: 'phase-1',
      sprintId: 'sprint-1',
      storyPoints: 2,
    },
    {
      id: 'item-3',
      title: 'Update user model',
      type: 'STORY',
      status: 'COMPLETED',
      priority: 'HIGH',
      phaseId: 'phase-1',
      sprintId: 'sprint-1',
      storyPoints: 5,
    },
  ],
};

describe('SprintView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows loading state while fetching', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => new Promise(() => {/* never resolves */}))
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByRole('status', { name: /loading sprint/i })).toBeDefined();
  });

  it('renders sprint name and status', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ sprint: mockSprint }),
        clone: () => ({ json: async () => ({ sprint: mockSprint }) }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByText('Sprint 1')).toBeDefined();
    });

    expect(screen.getByLabelText('Sprint status: ACTIVE')).toBeDefined();
  });

  it('displays sprint goal', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ sprint: mockSprint }),
        clone: () => ({ json: async () => ({ sprint: mockSprint }) }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByText(/complete authentication module/i)).toBeDefined();
    });
  });

  it('shows items in sprint', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ sprint: mockSprint }),
        clone: () => ({ json: async () => ({ sprint: mockSprint }) }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByText('Implement JWT validation')).toBeDefined();
    });

    expect(screen.getByText('Write auth unit tests')).toBeDefined();
    expect(screen.getByText('Update user model')).toBeDefined();
  });

  it('shows sprint metrics', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ sprint: mockSprint }),
        clone: () => ({ json: async () => ({ sprint: mockSprint }) }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      // Total items
      expect(screen.getByText('3')).toBeDefined();
    });

    // Progress percent (1 of 3 done = 33%)
    expect(screen.getByText('33%')).toBeDefined();
  });

  it('shows Complete Sprint button for ACTIVE sprint', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ sprint: mockSprint }),
        clone: () => ({ json: async () => ({ sprint: mockSprint }) }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /complete sprint/i })).toBeDefined();
    });
  });

  it('shows Start Sprint button for PLANNING sprint', async () => {
    const planningSprint = { ...mockSprint, status: 'PLANNING' as const, items: [] };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ sprint: planningSprint }),
        clone: () => ({ json: async () => ({ sprint: planningSprint }) }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /start sprint/i })).toBeDefined();
    });
  });

  it('shows error state when fetch fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        text: async () => JSON.stringify({ error: 'Sprint not found' }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-unknown" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeDefined();
    });
  });

  it('shows empty state when no items in sprint', async () => {
    const emptySprint = { ...mockSprint, items: [] };

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ sprint: emptySprint }),
        clone: () => ({ json: async () => ({ sprint: emptySprint }) }),
      })
    );

    render(<SprintView projectId="proj-1" sprintId="sprint-1" />, {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(screen.getByText('No items in this sprint')).toBeDefined();
    });
  });
});
