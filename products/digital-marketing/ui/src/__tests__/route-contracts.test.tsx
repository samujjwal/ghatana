/**
 * Route contract tests — assert that canonical paths render the expected pages.
 *
 * @doc.type test
 * @doc.purpose Guard route-helper drift for DMOS UI
 * @doc.layer frontend
 */
import React, { Suspense } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { AuthProvider } from '@/context/AuthContext';
import { dmosRouteManifest, isRouteAllowedForRoles } from '@/routeManifest';
import { CapabilityKeys } from '@/api/capabilities';
import { ACTION_MINIMUM_ROLES } from '@/lib/action-permissions';

vi.mock('@/lib/feature-flags', () => ({
  FEATURE_FLAGS: {
    DASHBOARD_GROWTH_METRICS: 'dmos.dashboard_growth_metrics',
  },
  isFeatureEnabled: () => false,
  featureFlags: {},
}));

vi.mock('@/api/approvals', () => ({
  listPendingApprovals: vi.fn().mockResolvedValue([]),
  getApprovalStatus: vi.fn().mockResolvedValue(null),
  getApprovalSnapshot: vi.fn().mockResolvedValue(null),
  decideApproval: vi.fn().mockResolvedValue(null),
  submitApproval: vi.fn().mockResolvedValue(null),
}));

vi.mock('@/api/ai-actions', () => ({
  listAiActions: vi.fn().mockResolvedValue([]),
  getAiAction: vi.fn().mockResolvedValue(null),
}));

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
});

function renderRoute(
  path: string,
  ui: React.ReactElement,
  token?: string,
): void {
  render(
    <ThemeProvider defaultTheme="light" enableStorage={false} enableSystem={false}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider
          initialToken={token ?? null}
          initialWorkspaceId="ws-1"
          initialTenantId="tenant-1"
          initialRoles={[]}
        >
          <MemoryRouter initialEntries={[path]}>
            <Suspense fallback={<div data-testid="suspense">Loading</div>}>
              {ui}
            </Suspense>
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

describe('Route contracts', () => {
  it('keeps frontend manifest metadata aligned with backend route-capability contract', () => {
    const testFileDir = path.dirname(fileURLToPath(import.meta.url));
    const contractPath = path.resolve(
      testFileDir,
      '../../../dm-api/src/main/resources/contracts/dmos-route-capabilities.json',
    );
    const contractRaw = readFileSync(contractPath, 'utf8');
    const contract = JSON.parse(contractRaw) as {
      routes: Array<{
        path: string;
        label: string;
        minimumRole: string;
        personas: string[];
        tiers: string[];
        actions: string[];
        cards: string[];
        capabilityKey?: string;
      }>;
    };

    const manifestByPath = new Map(
      dmosRouteManifest.map((route) => [route.path, route]),
    );
    const contractByPath = new Map(
      contract.routes.map((route) => [route.path, route]),
    );

    contract.routes.forEach((contractRoute) => {
      const manifestRoute = manifestByPath.get(contractRoute.path);
      expect(manifestRoute).toBeDefined();
      expect(manifestRoute?.label).toBe(contractRoute.label);
      expect(manifestRoute?.minimumRole).toBe(contractRoute.minimumRole);
      expect(manifestRoute?.personas).toEqual(contractRoute.personas);
      expect(manifestRoute?.tiers).toEqual(contractRoute.tiers);
      expect(manifestRoute?.actions).toEqual(contractRoute.actions);
      expect(manifestRoute?.cards).toEqual(contractRoute.cards);
      expect(manifestRoute?.capabilityKey).toBe(contractRoute.capabilityKey);
    });

    dmosRouteManifest.forEach((manifestRoute) => {
      expect(contractByPath.has(manifestRoute.path)).toBe(true);
    });
  });

  it('requires every route capability key to be defined in CapabilityKeys', () => {
    const knownCapabilities = new Set(Object.values(CapabilityKeys));
    const routeCapabilityKeys = dmosRouteManifest
      .map((route) => route.capabilityKey)
      .filter((key): key is (typeof CapabilityKeys)[keyof typeof CapabilityKeys] => Boolean(key));

    routeCapabilityKeys.forEach((key) => {
      expect(knownCapabilities.has(key)).toBe(true);
    });
  });

  it('requires every route action to be defined in the canonical action permission map', () => {
    const knownActions = new Set(Object.keys(ACTION_MINIMUM_ROLES));
    const routeActions = dmosRouteManifest.flatMap((route) => route.actions ?? []);

    routeActions.forEach((action) => {
      expect(knownActions.has(action)).toBe(true);
    });
  });

  it('exposes role/persona/tier metadata for every manifest route', () => {
    dmosRouteManifest.forEach((route) => {
      expect(route.path).toBeTruthy();
      expect(route.label).toBeTruthy();
      expect(route.minimumRole).toBeTruthy();
      expect(route.personas?.length ?? 0).toBeGreaterThan(0);
      expect(route.tiers?.length ?? 0).toBeGreaterThan(0);
    });
  });

  it('rejects budget route access for viewer-only roles', () => {
    const budgetRoute = dmosRouteManifest.find((route) => route.path.includes('/budget'));
    expect(budgetRoute).toBeDefined();
    expect(isRouteAllowedForRoles(budgetRoute!, ['viewer'])).toBe(false);
    expect(isRouteAllowedForRoles(budgetRoute!, ['marketing-director'])).toBe(true);
  });

  it('renders login page at /login', async () => {
    const { LoginPage } = await import('@/pages/LoginPage');
    renderRoute('/login', <Routes><Route path="/login" element={<LoginPage />} /></Routes>);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('redirects unauthenticated user from /approvals to /login', async () => {
    const { ApprovalQueuePage } = await import('@/pages/ApprovalQueuePage');
    renderRoute(
      '/workspaces/ws-1/approvals',
      <Routes>
        <Route path="/login" element={<div data-testid="login-page" />} />
        <Route path="/workspaces/:workspaceId/approvals" element={<ApprovalQueuePage />} />
      </Routes>,
      undefined,
    );
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders approval queue page for authenticated user', async () => {
    const { ApprovalQueuePage } = await import('@/pages/ApprovalQueuePage');
    renderRoute(
      '/workspaces/ws-1/approvals',
      <Routes>
        <Route path="/workspaces/:workspaceId/approvals" element={<ApprovalQueuePage />} />
      </Routes>,
      'test-token',
    );
    expect(screen.getByTestId('approval-queue-page')).toBeInTheDocument();
  });

  it('renders dashboard page for authenticated user', async () => {
    const { DashboardPage } = await import('@/pages/DashboardPage');
    renderRoute(
      '/workspaces/ws-1/dashboard',
      <Routes>
        <Route path="/workspaces/:workspaceId/dashboard" element={<DashboardPage />} />
      </Routes>,
      'test-token',
    );
    expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
  });
});
