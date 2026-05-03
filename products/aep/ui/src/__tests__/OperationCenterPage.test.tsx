/**
 * OperationCenterPage tests
 *
 * @doc.type test
 * @doc.purpose Test operation center page with API integration
 * @doc.layer frontend
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { userEvent } from '@testing-library/user-event';
import { atom, useAtom } from 'jotai';
import { OperationCenterPage } from '@/pages/OperationCenterPage';
import { listOperations, retryOperation, cancelOperation } from '@/api/aep.api';

// Mock the API
vi.mock('@/api/aep.api', () => ({
  listOperations: vi.fn(),
  retryOperation: vi.fn(),
  cancelOperation: vi.fn(),
}));

// Mock the tenant atom
const mockTenantIdAtom = atom('default-tenant');
vi.mock('@/stores/tenant.store', () => ({
  tenantIdAtom: mockTenantIdAtom,
}));

// Mock toast
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

describe('OperationCenterPage', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  function renderWithQueryClient(ui: React.ReactElement) {
    return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
  }

  it('renders operation center page with title', async () => {
    vi.mocked(listOperations).mockResolvedValue([]);

    renderWithQueryClient(<OperationCenterPage />);

    expect(screen.getByText('Operation Center')).toBeInTheDocument();
    expect(screen.getByText(/Monitor active jobs, retry failures/i)).toBeInTheDocument();
  });

  it('displays operations when data is loaded', async () => {
    const mockOperations = [
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
    ];

    vi.mocked(listOperations).mockResolvedValue(mockOperations);

    renderWithQueryClient(<OperationCenterPage />);

    await waitFor(() => {
      expect(screen.getByText('pipeline-execution')).toBeInTheDocument();
      expect(screen.getByText('agent-execution')).toBeInTheDocument();
    });
  });

  it('calls retry operation when retry button is clicked', async () => {
    const mockOperations = [
      {
        id: 'op-1',
        type: 'pipeline-execution',
        status: 'failed' as const,
        startedAt: new Date().toISOString(),
        attempts: 2,
        maxAttempts: 3,
        errorMessage: 'Connection timeout',
      },
    ];

    vi.mocked(listOperations).mockResolvedValue(mockOperations);
    vi.mocked(retryOperation).mockResolvedValue({ retried: true, operationId: 'op-1' });

    renderWithQueryClient(<OperationCenterPage />);

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
    const mockOperations = [
      {
        id: 'op-1',
        type: 'pipeline-execution',
        status: 'running' as const,
        startedAt: new Date().toISOString(),
        attempts: 1,
        maxAttempts: 3,
      },
    ];

    vi.mocked(listOperations).mockResolvedValue(mockOperations);
    vi.mocked(cancelOperation).mockResolvedValue({ cancelled: true, operationId: 'op-1' });

    renderWithQueryClient(<OperationCenterPage />);

    await waitFor(() => {
      expect(screen.getByText('pipeline-execution')).toBeInTheDocument();
    });

    const cancelButton = screen.getByTitle('Cancel');
    await userEvent.click(cancelButton);

    await waitFor(() => {
      expect(cancelOperation).toHaveBeenCalledWith('op-1', 'default-tenant');
    });
  });

  it('displays error message when retry fails', async () => {
    const mockOperations = [
      {
        id: 'op-1',
        type: 'pipeline-execution',
        status: 'failed' as const,
        startedAt: new Date().toISOString(),
        attempts: 2,
        maxAttempts: 3,
        errorMessage: 'Connection timeout',
      },
    ];

    vi.mocked(listOperations).mockResolvedValue(mockOperations);
    vi.mocked(retryOperation).mockRejectedValue(new Error('Network error'));

    renderWithQueryClient(<OperationCenterPage />);

    await waitFor(() => {
      expect(screen.getByText('pipeline-execution')).toBeInTheDocument();
    });

    const retryButton = screen.getByTitle('Retry');
    await userEvent.click(retryButton);

    await waitFor(() => {
      expect(retryOperation).toHaveBeenCalledWith('op-1', 'default-tenant');
    });
  });

  it('displays empty state when no operations exist', async () => {
    vi.mocked(listOperations).mockResolvedValue([]);

    renderWithQueryClient(<OperationCenterPage />);

    await waitFor(() => {
      expect(screen.getByText(/No operations/i)).toBeInTheDocument();
    });
  });
});
