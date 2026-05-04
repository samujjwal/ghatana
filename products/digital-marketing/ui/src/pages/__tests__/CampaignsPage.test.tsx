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
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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

vi.mock('@/hooks/useCampaigns', () => ({
  useCampaigns: (...args: unknown[]) => mockUseCampaigns(...args),
  useCreateCampaign: (...args: unknown[]) => mockUseCreateCampaign(...args),
  useLaunchCampaign: (...args: unknown[]) => mockUseLaunchCampaign(...args),
  usePauseCampaign: (...args: unknown[]) => mockUsePauseCampaign(...args),
}));

function buildQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderPage(token: string | null = 'test-token'): void {
  render(
    <QueryClientProvider client={buildQueryClient()}>
      <AuthProvider
        initialToken={token}
        initialWorkspaceId="ws-1"
        initialTenantId="tenant-1"
        initialRoles={[]}
      >
        <MemoryRouter initialEntries={['/workspaces/ws-1/campaigns']}>
          <Routes>
            <Route path="/login" element={<div data-testid="login-page" />} />
            <Route path="/workspaces/:workspaceId/campaigns" element={<CampaignsPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('CampaignsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseCampaigns.mockReturnValue({
      campaigns: mockCampaigns,
      isLoading: false,
      isError: false,
      error: null,
    });
    mockUseCreateCampaign.mockReturnValue({
      create: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
    mockUseLaunchCampaign.mockReturnValue({
      launch: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    });
    mockUsePauseCampaign.mockReturnValue({
      pause: vi.fn(),
      isPending: false,
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
      isLoading: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId('campaigns-empty')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockUseCampaigns.mockReturnValue({
      campaigns: [],
      isLoading: true,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId('campaigns-loading')).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockUseCampaigns.mockReturnValue({
      campaigns: [],
      isLoading: false,
      isError: true,
      error: new Error('fetch failed'),
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
});
