/**
 * Boundary route availability tests.
 *
 * @doc.type test
 * @doc.purpose Verifies boundary pages show explicit unavailable state and enforce auth redirect
 * @doc.layer frontend
 */
import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '@/context/AuthContext';
import { FunnelAnalyticsPage } from '@/pages/FunnelAnalyticsPage';
import { AttributionPage } from '@/pages/AttributionPage';
import { RoiRoasPage } from '@/pages/RoiRoasPage';
import { SelfMarketingFunnelPage } from '@/pages/SelfMarketingFunnelPage';
import { MarketResearchPage } from '@/pages/MarketResearchPage';
import { AdvancedChannelsPage } from '@/pages/AdvancedChannelsPage';
import { LocalizationPage } from '@/pages/LocalizationPage';
import { AgencyOperationsPage } from '@/pages/AgencyOperationsPage';
import { AiOptimizationPage } from '@/pages/AiOptimizationPage';

interface BoundaryCase {
  label: string;
  path: string;
  featureName: string;
  element: React.ReactElement;
}

const CASES: BoundaryCase[] = [
  {
    label: 'funnel analytics',
    path: '/workspaces/:workspaceId/funnel-analytics',
    featureName: 'Funnel Analytics',
    element: <FunnelAnalyticsPage />,
  },
  {
    label: 'attribution',
    path: '/workspaces/:workspaceId/attribution',
    featureName: 'Attribution Reporting',
    element: <AttributionPage />,
  },
  {
    label: 'roi roas',
    path: '/workspaces/:workspaceId/roi-roas',
    featureName: 'ROI and ROAS',
    element: <RoiRoasPage />,
  },
  {
    label: 'self marketing funnel',
    path: '/workspaces/:workspaceId/self-marketing-funnel',
    featureName: 'Self-Marketing Funnel',
    element: <SelfMarketingFunnelPage />,
  },
  {
    label: 'market research',
    path: '/workspaces/:workspaceId/market-research',
    featureName: 'Market Research',
    element: <MarketResearchPage />,
  },
  {
    label: 'advanced channels',
    path: '/workspaces/:workspaceId/advanced-channels',
    featureName: 'Advanced Channels',
    element: <AdvancedChannelsPage />,
  },
  {
    label: 'localization',
    path: '/workspaces/:workspaceId/localization',
    featureName: 'Localization',
    element: <LocalizationPage />,
  },
  {
    label: 'agency operations',
    path: '/workspaces/:workspaceId/agency-operations',
    featureName: 'Agency Operations',
    element: <AgencyOperationsPage />,
  },
  {
    label: 'ai optimization',
    path: '/workspaces/:workspaceId/ai-optimization',
    featureName: 'AI Optimization',
    element: <AiOptimizationPage />,
  },
];

function renderWithAuth(pathTemplate: string, element: React.ReactElement): void {
  const initialPath = pathTemplate.replace(':workspaceId', 'ws-1');
  render(
    <AuthProvider
      initialToken="test-token"
      initialWorkspaceId="ws-1"
      initialTenantId="tenant-1"
      initialRoles={['admin']}
    >
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div data-testid="login-page" />} />
          <Route path={pathTemplate} element={element} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

function renderWithoutAuth(pathTemplate: string, element: React.ReactElement): void {
  const initialPath = pathTemplate.replace(':workspaceId', 'ws-1');
  render(
    <AuthProvider
      initialToken={null}
      initialWorkspaceId={null}
      initialTenantId={null}
      initialRoles={[]}
    >
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div data-testid="login-page" />} />
          <Route path={pathTemplate} element={element} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('Boundary routes', () => {
  it.each(CASES)('shows explicit unavailable state for $label when authenticated', ({ path, element, featureName }) => {
    renderWithAuth(path, element);

    expect(screen.getByTestId('feature-unavailable-page')).toBeInTheDocument();
    expect(screen.getByTestId('feature-unavailable-details')).toBeInTheDocument();
    expect(screen.getByText('Production gate')).toBeInTheDocument();
    expect(screen.getByText('Feature Unavailable')).toBeInTheDocument();
    expect(screen.getAllByText(new RegExp(featureName, 'i')).length).toBeGreaterThan(0);
  });

  it.each(CASES)('redirects unauthenticated access to login for $label', ({ path, element }) => {
    renderWithoutAuth(path, element);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });
});
