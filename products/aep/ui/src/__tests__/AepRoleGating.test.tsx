import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PipelineListPage } from '@/pages/PipelineListPage';
import { AgentMarketplacePage } from '@/pages/AgentMarketplacePage';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';
import * as pipelineApi from '@/api/pipeline.api';
import * as aepApi from '@/api/aep.api';
import { useAuth } from '@/context/AuthContext';

vi.mock('@/api/pipeline.api');
vi.mock('@/api/aep.api');
vi.mock('@/context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const wrapper = createAepTestWrapper();

describe('AEP role gating', () => {
  beforeEach(() => {
    vi.mocked(pipelineApi.listPipelines).mockResolvedValue([
      {
        id: 'pipe-1',
        name: 'Fraud Triage',
        status: 'DRAFT',
        version: 1,
        stages: [],
      },
    ]);
    vi.mocked(aepApi.listMarketplaceAgents).mockResolvedValue([
      {
        id: 'agent-market-1',
        name: 'Marketplace Agent',
        description: 'Reusable operator workflow agent',
        version: '1.0.0',
        domain: 'operations',
        level: 'expert',
        capabilities: ['triage', 'explain'],
        tags: ['trusted'],
        source: 'catalog',
        owner: 'platform',
        averageRating: 4.5,
        reviewCount: 2,
        updatedAt: new Date().toISOString(),
      },
    ]);
    vi.mocked(aepApi.getMarketplaceAgent).mockResolvedValue({
      listing: {
        id: 'agent-market-1',
        name: 'Marketplace Agent',
        description: 'Reusable operator workflow agent',
        version: '1.0.0',
        domain: 'operations',
        level: 'expert',
        capabilities: ['triage', 'explain'],
        tags: ['trusted'],
        source: 'catalog',
        owner: 'platform',
        averageRating: 4.5,
        reviewCount: 2,
        updatedAt: new Date().toISOString(),
      },
      reviews: [],
    });
  });

  it('keeps pipeline management actions hidden for viewer-only users', async () => {
    vi.mocked(useAuth).mockReturnValue({
      authToken: 'jwt-token',
      sessionToken: null,
      isAuthenticated: true,
      isBootstrappingSession: false,
      isVerifyingAuth: false,
      roles: ['viewer'],
      hasRole: () => false,
      hasAnyRole: () => false,
      loginWithToken: vi.fn(),
      loginWithPlatform: vi.fn(),
      logout: vi.fn(),
    });

    render(<PipelineListPage />, { wrapper });

    await waitFor(() => expect(screen.getByText('Fraud Triage')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /\+ new pipeline/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument();
    expect(screen.getByText(/read-only access/i)).toBeInTheDocument();
  });

  it('shows pipeline management actions for operator users', async () => {
    vi.mocked(useAuth).mockReturnValue({
      authToken: 'jwt-token',
      sessionToken: null,
      isAuthenticated: true,
      isBootstrappingSession: false,
      isVerifyingAuth: false,
      roles: ['operator'],
      hasRole: (role) => role === 'operator',
      hasAnyRole: (roles) => roles.includes('operator'),
      loginWithToken: vi.fn(),
      loginWithPlatform: vi.fn(),
      logout: vi.fn(),
    });

    render(<PipelineListPage />, { wrapper });

    await waitFor(() => expect(screen.getByRole('button', { name: /\+ new pipeline/i })).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /dry run/i })).toBeInTheDocument();
  });

  it('keeps marketplace publish and install controls unavailable for viewer-only users', async () => {
    vi.mocked(useAuth).mockReturnValue({
      authToken: 'jwt-token',
      sessionToken: null,
      isAuthenticated: true,
      isBootstrappingSession: false,
      isVerifyingAuth: false,
      roles: ['viewer'],
      hasRole: () => false,
      hasAnyRole: () => false,
      loginWithToken: vi.fn(),
      loginWithPlatform: vi.fn(),
      logout: vi.fn(),
    });

    const user = userEvent.setup();
    render(<AgentMarketplacePage />, { wrapper });

    await waitFor(() => expect(screen.getByText('Marketplace Agent')).toBeInTheDocument());
    const publishTab = screen.getByRole('button', { name: /^publish$/i });
    expect(publishTab).toBeDisabled();

    await user.click(screen.getByText('Marketplace Agent'));
    await waitFor(() => expect(screen.getByText('Agent Detail')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /install to tenant/i })).not.toBeInTheDocument();
    expect(screen.getByText(/installing marketplace agents requires/i)).toBeInTheDocument();
  });
});
