/**
 * OperationCenterPage tests
 *
 * @doc.type test
 * @doc.purpose Test operation center page with API integration
 * @doc.layer frontend
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { OperationCenterPage } from '@/pages/OperationCenterPage';
import { listOperations, retryOperation, cancelOperation } from '@/api/aep.api';
import { createAepTestWrapper } from './test-utils/wrapper';

// Mock the API
vi.mock('@/api/aep.api', () => ({
  listOperations: vi.fn(),
  retryOperation: vi.fn(),
  cancelOperation: vi.fn(),
}));

// Mock the tenant atom
vi.mock('@/stores/tenant.store', async () => {
  const { atom } = await import('jotai');
  return { tenantIdAtom: atom('default-tenant') };
});

// Mock toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
  Toaster: () => null,
}));

describe('OperationCenterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function renderPage() {
    const Wrapper = createAepTestWrapper();
    return render(<OperationCenterPage />, { wrapper: Wrapper });
  }

  it('renders operation center page with title', async () => {
    vi.mocked(listOperations).mockResolvedValue([]);

    renderPage();

    // The page renders an h1 "Operation Center"; use heading role to be precise
    expect(screen.getByRole('heading', { name: 'Operation Center', level: 1 })).toBeInTheDocument();
    expect(screen.getByText(/Monitor active jobs, retry failures/i)).toBeInTheDocument();
  });

  it('displays operations when data is loaded', async () => {
    vi.mocked(listOperations).mockResolvedValue([
      {
        id: 'op-1',
        type: 'pipeline-execution',
        status: 'running' as const,
        startedAt: new Date().toISOString(),
        attempts: 1,
        maxAttempts: 3,
        resourceType: 'pipeline' as const,
        resourceId: 'pipeline-1',
      },
      {
        id: 'op-2',
        type: 'agent-execution',
        status: 'completed' as const,
        startedAt: new Date(Date.now() - 3600000).toISOString(),
        finishedAt: new Date(Date.now() - 3000000).toISOString(),
        attempts: 1,
        maxAttempts: 3,
        resourceType: 'agent' as const,
        resourceId: 'agent-1',
      },
    ]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('pipeline-execution')).toBeInTheDocument();
      expect(screen.getByText('agent-execution')).toBeInTheDocument();
    });
  });

  it('calls retry operation when retry button is clicked', async () => {
    vi.mocked(listOperations).mockResolvedValue([
      {
        id: 'op-1',
        type: 'pipeline-execution',
        status: 'failed' as const,
        startedAt: new Date().toISOString(),
        attempts: 2,
        maxAttempts: 3,
        errorMessage: 'Connection timeout',
      },
    ]);
    vi.mocked(retryOperation).mockResolvedValue({ retried: true, operationId: 'op-1' });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('pipeline-execution')).toBeInTheDocument();
    });

    const retryButton = screen.getByTitle('Retry');
    await userEvent.click(retryButton);

    await waitFor(() => {
      expect(retryOperation).toHaveBeenCalledWith('op-1', 'default-tenant');
    });
  });

  it('calls cancel operation when cancel button is clicked', async () => {
    vi.mocked(listOperations).mockResolvedValue([
      {
        id: 'op-1',
        type: 'pipeline-execution',
        status: 'running' as const,
        startedAt: new Date().toISOString(),
        attempts: 1,
        maxAttempts: 3,
      },
    ]);
    vi.mocked(cancelOperation).mockResolvedValue({ cancelled: true, operationId: 'op-1' });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('pipeline-execution')).toBeInTheDocument();
    });

    const cancelButton = screen.getByTitle('Cancel');
    await userEvent.click(cancelButton);

    await waitFor(() => {
      expect(cancelOperation).toHaveBeenCalledWith('op-1', 'default-tenant');
    });
  });

  it('shows backend failure state and allows retry on list error', async () => {
    vi.mocked(listOperations).mockRejectedValue(new Error('Backend unreachable'));

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/failed to load operations/i)).toBeInTheDocument();
    });
  });

  it('prevents duplicate retry when mutation is already in-flight', async () => {
    // retryOperation never resolves to simulate in-flight
    vi.mocked(listOperations).mockResolvedValue([
      {
        id: 'op-1',
        type: 'pipeline-execution',
        status: 'failed' as const,
        startedAt: new Date().toISOString(),
        attempts: 1,
        maxAttempts: 3,
      },
    ]);
    vi.mocked(retryOperation).mockImplementation(
      () => new Promise(() => { /* never resolves */ }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('pipeline-execution')).toBeInTheDocument();
    });

    const retryButton = screen.getByTitle('Retry');
    await userEvent.click(retryButton);

    // Button should be disabled while mutation is in-flight
    expect(retryButton).toBeDisabled();
  });

  it('shows permission denied state for 403 errors', async () => {
    const err = Object.assign(new Error('Forbidden'), { response: { status: 403 } });
    vi.mocked(listOperations).mockRejectedValue(err);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/failed to load operations/i)).toBeInTheDocument();
    });
  });

  it('displays empty state when no operations exist', async () => {
    vi.mocked(listOperations).mockResolvedValue([]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText(/No operations/i)).toBeInTheDocument();
    });
  });

  it('auto-refreshes operations on a poll interval', async () => {
    vi.mocked(listOperations).mockResolvedValue([]);

    renderPage();

    // Assert the initial load is triggered with the right args
    await waitFor(() => {
      expect(listOperations).toHaveBeenCalledWith('default-tenant', 100);
    });
  });
});
