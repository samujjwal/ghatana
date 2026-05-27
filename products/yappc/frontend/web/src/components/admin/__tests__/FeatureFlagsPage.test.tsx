/**
 * FeatureFlagsPage unit tests (F-Y047)
 *
 * @doc.type test
 * @doc.purpose Verify feature flag list, toggle dialog, search, and audit drawer
 * @doc.layer product
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { FeatureFlagsPage } from '../FeatureFlagsPage';
import type { FeatureFlag } from '../../../services/admin/featureFlagsApi';

// ─────────────────────────────────────────────────────────────────────────────
// Mocks
// ─────────────────────────────────────────────────────────────────────────────

const mockListFlags = vi.hoisted(() => vi.fn<() => Promise<FeatureFlag[]>>());
const mockSetFlag = vi.hoisted(() => vi.fn<() => Promise<FeatureFlag>>());
const mockGetAudit = vi.hoisted(() => vi.fn<() => Promise<unknown[]>>());

vi.mock('../../../services/admin/featureFlagsApi', () => ({
  listTenantFeatureFlags: mockListFlags,
  setFeatureFlag: mockSetFlag,
  getFeatureFlagAuditLog: mockGetAudit,
}));

vi.mock('jotai', () => ({
  useAtomValue: () => 'tenant-abc',
  atom: vi.fn(),
  atomWithStorage: vi.fn(),
}));

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function makeFlag(overrides: Partial<FeatureFlag> = {}): FeatureFlag {
  return {
    id: 'flag-1',
    key: 'dark-mode',
    description: 'Enable dark mode for users',
    enabled: true,
    tenantId: 'tenant-abc',
    rolloutPercentage: 100,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-04-01T00:00:00.000Z',
    updatedBy: 'alice',
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <FeatureFlagsPage />
    </QueryClientProvider>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe('FeatureFlagsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSetFlag.mockResolvedValue(makeFlag({ enabled: false }));
    mockGetAudit.mockResolvedValue([]);
  });

  it('renders loading state initially', () => {
    mockListFlags.mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByRole('status')).toHaveTextContent('Loading feature flags...');
  });

  it('renders standardized retryable error with correlation id', async () => {
    mockListFlags.mockRejectedValue(new Error('feature flag service unavailable [Correlation ID: corr-flags-1]'));

    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('Feature flags unavailable');
    expect(screen.getByRole('alert')).toHaveTextContent('Correlation ID: corr-flags-1');
    expect(screen.getByRole('button', { name: 'Try Again' })).toBeInTheDocument();
  });

  it('renders flag rows after load', async () => {
    mockListFlags.mockResolvedValue([
      makeFlag({ key: 'dark-mode', enabled: true }),
      makeFlag({ id: 'flag-2', key: 'beta-onboarding', enabled: false }),
    ]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('flag-row-dark-mode')).toBeInTheDocument();
      expect(screen.getByTestId('flag-row-beta-onboarding')).toBeInTheDocument();
    });
  });

  it('shows enabled count in summary', async () => {
    mockListFlags.mockResolvedValue([
      makeFlag({ key: 'flag-a', enabled: true }),
      makeFlag({ id: 'f2', key: 'flag-b', enabled: false }),
    ]);

    renderPage();

    await waitFor(() => expect(screen.getByText(/1 of 2 enabled/)).toBeInTheDocument());
  });

  it('opens toggle confirm dialog when toggle button is clicked', async () => {
    mockListFlags.mockResolvedValue([makeFlag({ key: 'dark-mode', enabled: true })]);

    renderPage();

    await waitFor(() => expect(screen.getByTestId('flag-toggle-dark-mode')).toBeInTheDocument());
    fireEvent.click(screen.getByTestId('flag-toggle-dark-mode'));

    expect(screen.getByTestId('flag-toggle-dialog')).toBeInTheDocument();
  });

  it('confirm button is disabled when reason is empty', async () => {
    mockListFlags.mockResolvedValue([makeFlag({ key: 'dark-mode', enabled: true })]);

    renderPage();

    await waitFor(() => screen.getByTestId('flag-toggle-dark-mode'));
    fireEvent.click(screen.getByTestId('flag-toggle-dark-mode'));

    const confirmBtn = screen.getByRole('button', { name: 'Confirm' });
    expect(confirmBtn).toBeDisabled();
  });

  it('calls setFeatureFlag with reason when confirm is clicked', async () => {
    mockListFlags.mockResolvedValue([makeFlag({ key: 'dark-mode', enabled: true })]);

    renderPage();

    await waitFor(() => screen.getByTestId('flag-toggle-dark-mode'));
    fireEvent.click(screen.getByTestId('flag-toggle-dark-mode'));

    fireEvent.change(screen.getByLabelText('Reason (required)'), {
      target: { value: 'Testing toggle' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    await waitFor(() =>
      expect(mockSetFlag).toHaveBeenCalledWith('tenant-abc', expect.objectContaining({
        key: 'dark-mode',
        enabled: false,
        reason: 'Testing toggle',
      }))
    );
  });

  it('filters flags by search query', async () => {
    mockListFlags.mockResolvedValue([
      makeFlag({ key: 'dark-mode', description: 'Dark mode' }),
      makeFlag({ id: 'f2', key: 'beta-feature', description: 'Beta feature' }),
    ]);

    renderPage();

    await waitFor(() => screen.getByTestId('flag-row-dark-mode'));

    fireEvent.change(screen.getByLabelText('Filter feature flags'), {
      target: { value: 'beta' },
    });

    expect(screen.queryByTestId('flag-row-dark-mode')).not.toBeInTheDocument();
    expect(screen.getByTestId('flag-row-beta-feature')).toBeInTheDocument();
  });

  it('opens audit drawer when history button is clicked', async () => {
    mockListFlags.mockResolvedValue([makeFlag({ key: 'dark-mode' })]);
    mockGetAudit.mockResolvedValue([
      {
        id: 'a1',
        flagKey: 'dark-mode',
        previousValue: false,
        newValue: true,
        changedBy: 'alice',
        reason: 'Initial enable',
        timestamp: '2026-04-01T00:00:00.000Z',
      },
    ]);

    renderPage();

    await waitFor(() => screen.getByTestId('flag-audit-dark-mode'));
    fireEvent.click(screen.getByTestId('flag-audit-dark-mode'));

    await waitFor(() =>
      expect(screen.getByTestId('flag-audit-drawer')).toBeInTheDocument()
    );
    await waitFor(() =>
      expect(screen.getByText(/Initial enable/)).toBeInTheDocument()
    );
  });
});
