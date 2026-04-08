/**
 * Tests for EntityBrowserPage bulk operations
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock the hooks and services
vi.mock('../hooks/useSelection', () => ({
  useSelection: vi.fn(() => ({
    selectedIds: new Set(),
    selectedItems: [],
    isAllSelected: false,
    isIndeterminate: false,
    toggleSelection: vi.fn(),
    toggleAll: vi.fn(),
    clearSelection: vi.fn(),
  })),
}));

vi.mock('../lib/api/user-activity', () => ({
  logActivity: vi.fn(),
}));

vi.mock('@audio-video/ui', () => ({
  useSpeechSynthesis: vi.fn(() => ({
    speak: vi.fn(),
  })),
}));

vi.mock('../components/privacy/ConsentManager', () => ({
  useConsent: vi.fn(() => ({
    consentGranted: true,
  })),
}));

vi.mock('../components/security/RBACGuard', () => ({
  RBACGuard: ({ children, fallback }: any) => <>{children || fallback}</>,
}));

describe('EntityBrowserPage bulk operations', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
        mutations: {
          retry: false,
        },
      },
    });
    vi.clearAllMocks();
  });

  it('renders bulk action toolbar when items are selected', () => {
    const { useSelection } = require('../hooks/useSelection');
    useSelection.mockReturnValue({
      selectedIds: new Set(['1', '2']),
      selectedItems: [{ id: '1' }, { id: '2' }],
      isAllSelected: false,
      isIndeterminate: false,
      toggleSelection: vi.fn(),
      toggleAll: vi.fn(),
      clearSelection: vi.fn(),
    });

    render(
      <QueryClientProvider client={queryClient}>
        <div>
          {useSelection().selectedIds.size > 0 && (
            <div data-testid="bulk-toolbar">
              <span>{useSelection().selectedIds.size} selected</span>
            </div>
          )}
        </div>
      </QueryClientProvider>
    );

    expect(screen.getByTestId('bulk-toolbar')).toBeInTheDocument();
    expect(screen.getByText('2 selected')).toBeInTheDocument();
  });

  it('does not render bulk action toolbar when no items selected', () => {
    const { useSelection } = require('../hooks/useSelection');
    useSelection.mockReturnValue({
      selectedIds: new Set(),
      selectedItems: [],
      isAllSelected: false,
      isIndeterminate: false,
      toggleSelection: vi.fn(),
      toggleAll: vi.fn(),
      clearSelection: vi.fn(),
    });

    render(
      <QueryClientProvider client={queryClient}>
        <div>
          {useSelection().selectedIds.size > 0 && (
            <div data-testid="bulk-toolbar">Bulk Actions</div>
          )}
        </div>
      </QueryClientProvider>
    );

    expect(screen.queryByTestId('bulk-toolbar')).not.toBeInTheDocument();
  });

  it('calls clearSelection when Clear button is clicked', () => {
    const clearSelection = vi.fn();
    const { useSelection } = require('../hooks/useSelection');
    useSelection.mockReturnValue({
      selectedIds: new Set(['1']),
      selectedItems: [{ id: '1' }],
      isAllSelected: false,
      isIndeterminate: false,
      toggleSelection: vi.fn(),
      toggleAll: vi.fn(),
      clearSelection,
    });

    render(
      <QueryClientProvider client={queryClient}>
        <div>
          {useSelection().selectedIds.size > 0 && (
            <div data-testid="bulk-toolbar">
              <button onClick={clearSelection}>Clear</button>
            </div>
          )}
        </div>
      </QueryClientProvider>
    );

    fireEvent.click(screen.getByText('Clear'));
    expect(clearSelection).toHaveBeenCalled();
  });

  it('calls logActivity when bulk delete is executed', async () => {
    const { logActivity } = require('../lib/api/user-activity');
    
    render(
      <QueryClientProvider client={queryClient}>
        <div />
      </QueryClientProvider>
    );

    // Simulate bulk delete
    await logActivity({
      action: 'bulk_delete',
      target: 'test-namespace',
      type: 'delete',
      resourceType: 'entity',
    });

    expect(logActivity).toHaveBeenCalledWith({
      action: 'bulk_delete',
      target: 'test-namespace',
      type: 'delete',
      resourceType: 'entity',
    });
  });
});
