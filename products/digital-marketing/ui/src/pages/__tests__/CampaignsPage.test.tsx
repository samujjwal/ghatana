/**
 * Campaigns Page tests.
 *
 * @doc.type test
 * @doc.purpose Unit tests for CampaignsPage behavior
 * @doc.layer frontend
 */
import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';
import { AuthProvider } from '@/context/AuthContext';
import { CampaignsPage } from '@/pages/CampaignsPage';
import type { Campaign } from '@/types/campaign';

const mockCampaigns: Campaign[] = [
  {
    id: 'c-1',
    workspaceId: 'ws-1',
    name: 'Q4 Acquisition',
    status: 'DRAFT',
    type: 'EMAIL',
    createdBy: 'user-1',
    createdAt: '2026-01-10T10:00:00Z',
    updatedAt: '2026-01-10T10:00:00Z',
  },
  {
    id: 'c-2',
    workspaceId: 'ws-1',
    name: 'Holiday Retargeting',
    status: 'LAUNCHED',
    type: 'PAID_SEARCH',
    createdBy: 'user-1',
    createdAt: '2026-01-11T10:00:00Z',
    updatedAt: '2026-01-11T10:00:00Z',
  },
];

const mockUseCampaigns = vi.fn();
const mockUseCreateCampaign = vi.fn();
const mockUseLaunchCampaign = vi.fn();
const mockUsePauseCampaign = vi.fn();
const mockUseCompleteCampaign = vi.fn();
const mockUseArchiveCampaign = vi.fn();
const mockUseRollbackCampaign = vi.fn();
const mockUseDuplicateCampaign = vi.fn();

vi.mock('@/hooks/useCampaigns', () => ({
  useCampaigns: (...args: unknown[]) => mockUseCampaigns(...args),
  useCreateCampaign: (...args: unknown[]) => mockUseCreateCampaign(...args),
  useLaunchCampaign: (...args: unknown[]) => mockUseLaunchCampaign(...args),
  usePauseCampaign: (...args: unknown[]) => mockUsePauseCampaign(...args),
  useCompleteCampaign: (...args: unknown[]) => mockUseCompleteCampaign(...args),
  useArchiveCampaign: (...args: unknown[]) => mockUseArchiveCampaign(...args),
  useRollbackCampaign: (...args: unknown[]) => mockUseRollbackCampaign(...args),
  useDuplicateCampaign: (...args: unknown[]) => mockUseDuplicateCampaign(...args),
}));

function buildQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage(
  token: string | null = 'test-token',
  roles: string[] = ['brand-manager'],
): void {
  render(
    <ThemeProvider defaultTheme="light" enableStorage={false} enableSystem={false}>
      <QueryClientProvider client={buildQueryClient()}>
        <AuthProvider
          initialToken={token}
          initialWorkspaceId="ws-1"
          initialTenantId="tenant-1"
          initialRoles={roles}
        >
          <MemoryRouter initialEntries={['/workspaces/ws-1/campaigns']}>
            <Routes>
              <Route path="/login" element={<div data-testid="login-page" />} />
              <Route path="/workspaces/:workspaceId/campaigns" element={<CampaignsPage />} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

describe('CampaignsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseCampaigns.mockReturnValue({
      campaigns: mockCampaigns,
      count: mockCampaigns.length,
      offset: 0,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseCreateCampaign.mockReturnValue({
      create: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
    mockUseLaunchCampaign.mockReturnValue({
      launch: vi.fn(),
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });
    mockUsePauseCampaign.mockReturnValue({
      pause: vi.fn(),
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });
    mockUseCompleteCampaign.mockReturnValue({
      execute: vi.fn(),
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });
    mockUseArchiveCampaign.mockReturnValue({
      execute: vi.fn(),
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });
    mockUseRollbackCampaign.mockReturnValue({
      execute: vi.fn(),
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });
    mockUseDuplicateCampaign.mockReturnValue({
      execute: vi.fn(),
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });
  });

  it('redirects unauthenticated user to /login', () => {
    renderPage(null);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders campaigns list', () => {
    renderPage();
    expect(screen.getByTestId('campaigns-page')).toBeInTheDocument();
    expect(screen.getByTestId('campaigns-list')).toBeInTheDocument();
    expect(screen.getByTestId('campaign-row-c-1')).toBeInTheDocument();
    expect(screen.getByTestId('campaign-row-c-2')).toBeInTheDocument();
  });

  it('shows empty state when no campaigns', () => {
    mockUseCampaigns.mockReturnValue({
      campaigns: [],
      count: 0,
      offset: 0,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId('campaigns-empty')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockUseCampaigns.mockReturnValue({
      campaigns: [],
      count: 0,
      offset: 0,
      isLoading: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId('campaigns-loading')).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockUseCampaigns.mockReturnValue({
      campaigns: [],
      count: 0,
      offset: 0,
      isLoading: false,
      isError: true,
      error: new Error('fetch failed'),
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId('campaigns-error')).toBeInTheDocument();
  });

  it('renders create campaign form', () => {
    renderPage();
    expect(screen.getByTestId('campaign-name-input')).toBeInTheDocument();
    expect(screen.getByTestId('campaign-type-select')).toBeInTheDocument();
    expect(screen.getByTestId('create-campaign-btn')).toBeInTheDocument();
  });

  it('shows launch button for draft campaigns', () => {
    renderPage();
    expect(screen.getByTestId('launch-campaign-c-1')).toBeInTheDocument();
  });

  it('shows pause button for launched campaigns', () => {
    renderPage();
    expect(screen.getByTestId('pause-campaign-c-2')).toBeInTheDocument();
  });

  it('opens archive dialog instead of using native confirm', async () => {
    const user = userEvent.setup();
    mockUseCampaigns.mockReturnValue({
      campaigns: [
        {
          ...mockCampaigns[0],
          id: 'c-archive',
          status: 'COMPLETED',
        },
      ],
      count: 1,
      offset: 0,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPage();
    await user.click(screen.getByTestId('archive-campaign-c-archive'));

    expect(screen.getByTestId('archive-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('archive-confirm-btn')).toBeInTheDocument();
  });

  it('renders archive dialog with accessible semantics', async () => {
    const user = userEvent.setup();
    mockUseCampaigns.mockReturnValue({
      campaigns: [
        {
          ...mockCampaigns[0],
          id: 'c-archive-a11y',
          status: 'COMPLETED',
        },
      ],
      count: 1,
      offset: 0,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });

    renderPage();
    await user.click(screen.getByTestId('archive-campaign-c-archive-a11y'));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /archive campaign/i })).toBeInTheDocument();
    expect(screen.getByTestId('archive-confirm-btn')).toHaveTextContent('Archive Campaign');
    expect(screen.getByTestId('archive-cancel-btn')).toBeInTheDocument();
  });

  it('confirms archive action through dialog confirm button', async () => {
    const user = userEvent.setup();
    const archiveExecute = vi.fn().mockResolvedValue({
      ...mockCampaigns[0],
      id: 'c-archive-confirm',
      status: 'ARCHIVED',
    });

    mockUseCampaigns.mockReturnValue({
      campaigns: [
        {
          ...mockCampaigns[0],
          id: 'c-archive-confirm',
          status: 'COMPLETED',
        },
      ],
      count: 1,
      offset: 0,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    mockUseArchiveCampaign.mockReturnValue({
      execute: archiveExecute,
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });

    renderPage();
    await user.click(screen.getByTestId('archive-campaign-c-archive-confirm'));
    await user.click(screen.getByTestId('archive-confirm-btn'));

    expect(archiveExecute).toHaveBeenCalledWith('c-archive-confirm');
  });

  it('requires duplicate campaign name before confirm', async () => {
    const user = userEvent.setup();
    const duplicateExecute = vi.fn();
    mockUseDuplicateCampaign.mockReturnValue({
      execute: duplicateExecute,
      isPendingFor: vi.fn().mockReturnValue(false),
      isError: false,
      error: null,
    });

    renderPage();
    await user.click(screen.getByTestId('duplicate-campaign-c-1'));
    expect(screen.getByTestId('duplicate-dialog')).toBeInTheDocument();

    const duplicateNameInput = screen.getByTestId('duplicate-name-input');
    await user.clear(duplicateNameInput);
    await user.click(screen.getByTestId('duplicate-confirm-btn'));

    expect(screen.getByTestId('duplicate-name-error')).toBeInTheDocument();
    expect(duplicateExecute).not.toHaveBeenCalled();
  });

  it('renders duplicate dialog with labeled input and accessible name', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId('duplicate-campaign-c-1'));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /duplicate campaign/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Duplicate Name')).toBeInTheDocument();
  });

  it('shows view-only banner and disables mutation actions for viewer role', () => {
    renderPage('test-token', ['viewer']);
    expect(screen.getByTestId('campaign-action-permission-banner')).toBeInTheDocument();
    expect(screen.getByTestId('create-campaign-btn')).toBeDisabled();
    expect(screen.getByTestId('launch-campaign-c-1')).toBeDisabled();
  });
});
