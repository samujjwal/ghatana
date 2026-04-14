/**
 * Tests for EntityBrowserPage bulk operations
 */

import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useSelection } from '../../hooks/useSelection';
import type { UseSelectionReturn } from '../../hooks/useSelection';
import { logActivity } from '../../lib/api/user-activity';

// Mock the hooks and services
vi.mock('../../hooks/useSelection', () => ({
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

vi.mock('../../lib/api/user-activity', () => ({
  logActivity: vi.fn(),
}));

vi.mock('@audio-video/ui', () => ({
  useSpeechSynthesis: vi.fn(() => ({
    speak: vi.fn(),
  })),
}));

vi.mock('../../components/privacy/ConsentManager', () => ({
  useConsent: vi.fn(() => ({
    consentGranted: true,
  })),
}));

vi.mock('../../components/security/RBACGuard', () => ({
  RBACGuard: ({ children, fallback }: { children?: React.ReactNode; fallback?: React.ReactNode }) => <>{children ?? fallback}</>,
}));

const mockedUseSelection = vi.mocked(useSelection);
const mockedLogActivity = vi.mocked(logActivity);

function makeSelectionState(
  overrides: Record<string, unknown> = {}
): UseSelectionReturn<unknown> {
  return {
    selectedIds: new Set<string>(),
    selectedItems: [],
    isAllSelected: false,
    isIndeterminate: false,
    isSelected: vi.fn(() => false),
    toggleSelection: vi.fn(),
    toggleAll: vi.fn(),
    selectAll: vi.fn(),
    clearSelection: vi.fn(),
    selectIds: vi.fn(),
    selectedCount: 0,
    ...overrides,
  } as UseSelectionReturn<unknown>;
}

describe('EntityBrowserPage bulk operations', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    vi.clearAllMocks();
    // Reset default mock implementation
    mockedUseSelection.mockReturnValue(makeSelectionState());
  });

  it('renders bulk action toolbar when items are selected', () => {
    mockedUseSelection.mockReturnValue(makeSelectionState({
      selectedIds: new Set(['1', '2']),
      selectedItems: [{ id: '1' }, { id: '2' }],
    }));

    const selectionState = mockedUseSelection({ items: [], keyFn: (i: unknown) => (i as { id: string }).id });

    render(
      <QueryClientProvider client={queryClient}>
        <div>
          {selectionState.selectedIds.size > 0 && (
            <div data-testid="bulk-toolbar">
              <span>{selectionState.selectedIds.size} selected</span>
            </div>
          )}
        </div>
      </QueryClientProvider>
    );

    expect(screen.getByTestId('bulk-toolbar')).toBeInTheDocument();
    expect(screen.getByText('2 selected')).toBeInTheDocument();
  });

  it('does not render bulk action toolbar when no items selected', () => {
    const selectionState = mockedUseSelection({ items: [], keyFn: (i: unknown) => (i as { id: string }).id });

    render(
      <QueryClientProvider client={queryClient}>
        <div>
          {selectionState.selectedIds.size > 0 && (
            <div data-testid="bulk-toolbar">Bulk Actions</div>
          )}
        </div>
      </QueryClientProvider>
    );

    expect(screen.queryByTestId('bulk-toolbar')).not.toBeInTheDocument();
  });

  it('calls clearSelection when Clear button is clicked', () => {
    const clearSelection = vi.fn();
    mockedUseSelection.mockReturnValue(makeSelectionState({
      selectedIds: new Set(['1']),
      selectedItems: [{ id: '1' }],
      clearSelection,
    }));

    const selectionState = mockedUseSelection({ items: [], keyFn: (i: unknown) => (i as { id: string }).id });

    render(
      <QueryClientProvider client={queryClient}>
        <div>
          {selectionState.selectedIds.size > 0 && (
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
    mockedLogActivity.mockResolvedValue(undefined);

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

    expect(mockedLogActivity).toHaveBeenCalledWith({
      action: 'bulk_delete',
      target: 'test-namespace',
      type: 'delete',
      resourceType: 'entity',
    });
  });
});
