import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@ghatana/theme';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PhrAccessProvider } from '../auth/PhrAccessContext';
import type { PhrRole } from '../auth/PhrAccessContext';
import type { DashboardData } from '../types';
import { PHR_ROLE_ORDER, phrRouteContracts } from '../routeManifest';
import { attachPhrRouteElement } from '../phrRouteElements';
import { AppShell } from '../layout/AppShell';
import { DashboardPage } from '../pages/DashboardPage';
import { RecordDetailPage } from '../pages/RecordDetailPage';
import { ReleaseCockpitPage } from '../pages/ReleaseCockpitPage';
import { ProtectedPhrRoute } from '../routes';

vi.mock('../api/phrApi', async () => {
  const actual = await vi.importActual<typeof import('../api/phrApi')>('../api/phrApi');
  return {
    ...actual,
    fetchDashboardData: vi.fn(),
  };
});

import { fetchDashboardData } from '../api/phrApi';

function entitlementPayloadFor(role: PhrRole): Record<string, unknown> {
  const allowedRoutes = phrRouteContracts.filter((route) => {
    const required = route.minimumRole ? PHR_ROLE_ORDER[route.minimumRole as PhrRole] : 0;
    return PHR_ROLE_ORDER[role] >= required;
  });

  return {
    product: 'phr',
    principalId: 'principal-test',
    tenantId: 'tenant-test',
    role,
    persona: role,
    tier: role === 'clinician' || role === 'admin' ? 'clinical' : 'core',
    routes: allowedRoutes,
    actions: allowedRoutes.flatMap((route) =>
      (route.actions ?? []).map((action) => ({
        id: action,
        label: action.replace(/-/g, ' '),
        routePath: route.path,
      })),
    ),
    cards: allowedRoutes.flatMap((route) =>
      (route.cards ?? []).map((card) => ({
        id: card,
        title: card.replace(/-/g, ' '),
        routePath: route.path,
        surface: 'dashboard',
      })),
    ),
  };
}

function mockEntitlementFetch(): void {
  const fetchMock: typeof fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/release-readiness')) {
      return new Response(JSON.stringify(releaseReadinessFixture), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const headers = new Headers(init?.headers);
    const roleHeader = headers.get('X-Role');
    const role: PhrRole =
      roleHeader === 'caregiver' || roleHeader === 'clinician' || roleHeader === 'admin'
        ? roleHeader
        : 'patient';

    return new Response(JSON.stringify(entitlementPayloadFor(role)), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  });

  vi.stubGlobal('fetch', fetchMock);
}

const releaseReadinessFixture = {
  product: 'phr',
  tenantId: 'tenant-health-1',
  principalId: 'admin-release-cockpit',
  role: 'admin',
  environment: 'staging',
  generatedAt: '2026-05-25T23:13:49.230Z',
  targetCommitSha: 'bdcee47c1e304454e7af848be60d981b24da1151',
  runtimeTruthBlocked: false,
  requiredSections: ['evidenceFreshness', 'fhirRuntime', 'consentCache', 'deployment', 'rollback', 'dataCloudRuntime'],
  releaseReadiness: {
    status: 'ready',
    overallScore: 9,
    blockingIssues: [],
    warnings: [],
  },
  sections: {
    evidenceFreshness: {
      label: 'Evidence freshness',
      status: 'passed',
      runtimeProven: true,
      message: 'Evidence commit, target commit, and expiry are bound.',
    },
    fhirRuntime: {
      label: 'FHIR runtime registry',
      status: 'passed',
      runtimeProven: true,
      message: 'Runtime-supported FHIR resources are present in release evidence.',
    },
    consentCache: {
      label: 'Consent cache proof',
      status: 'passed',
      runtimeProven: true,
      message: 'Consent cache proof is runtime-proven.',
    },
    deployment: {
      label: 'Deployment proof',
      status: 'ready',
      runtimeProven: true,
      message: 'staging deployment proof is ready.',
    },
    rollback: {
      label: 'Rollback proof',
      status: 'ready',
      runtimeProven: true,
      message: 'Rollback proof is runtime-proven.',
    },
    dataCloudRuntime: {
      label: 'Data Cloud runtime truth',
      status: 'passed',
      runtimeProven: true,
      message: 'Provider and runtime profile proof are passing.',
    },
  },
};

const dashboardFixture: DashboardData = {
  patient: {
    id: 'patient-001',
    name: 'Aarati Shrestha',
    age: 42,
    bloodType: 'O+',
    location: 'Kathmandu',
    emergencyContact: 'Sushil Shrestha',
  },
  records: [],
  consents: [
    {
      id: 'consent-1',
      recipient: 'Nepal HIE',
      purpose: 'Care coordination',
      status: 'active',
      expiresAt: '2026-12-31',
    },
  ],
  appointments: [
    {
      id: 'appointment-1',
      provider: 'Dr. Koirala',
      specialty: 'Endocrinology',
      startsAt: '2026-05-06T09:00:00Z',
      location: 'Kathmandu Clinic',
    },
  ],
  labs: [
    {
      id: 'lab-1',
      name: 'HbA1c',
      status: 'normal',
      value: '6.9%',
      collectedAt: '2026-05-01',
    },
  ],
  medications: [
    {
      id: 'med-1',
      medication: 'Metformin',
      dosage: '500mg',
      schedule: 'BID',
      adherence: 100,
    },
  ],
};

function renderDashboardPage(): void {
  render(
    <ThemeProvider>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe('PHR web app', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchDashboardData).mockResolvedValue(dashboardFixture);
    mockEntitlementFetch();
    window.localStorage.clear();
    window.history.pushState({}, '', '/');
  });

  it('renders dashboard metrics', () => {
    renderDashboardPage();

    return waitFor(() => {
      expect(screen.getByText('Aarati Shrestha')).toBeInTheDocument();
      expect(screen.getByText('Active consent flows')).toBeInTheDocument();
    });
  });

  it('shows loading state while dashboard data resolves', () => {
    vi.mocked(fetchDashboardData).mockImplementation(
      () =>
        new Promise<DashboardData>(() => {
          // Keep the promise pending to verify the default loading contract.
        }),
    );

    renderDashboardPage();

    expect(screen.getByText('Loading dashboard...')).toBeInTheDocument();
  });

  it('shows error state when dashboard loading fails', async () => {
    vi.mocked(fetchDashboardData).mockRejectedValue(new Error('Upstream consent service unavailable'));

    renderDashboardPage();

    await waitFor(() => {
      expect(screen.getByText('Error: Upstream consent service unavailable')).toBeInTheDocument();
    });
  });

  it('shows empty fallback when dashboard data resolves to no payload', async () => {
    vi.mocked(fetchDashboardData).mockResolvedValue(null as unknown as DashboardData);

    renderDashboardPage();

    await waitFor(() => {
      expect(screen.getByText('No data available')).toBeInTheDocument();
    });
  });

  it('renders a FHIR record detail fallback for unknown records when patient role grants access', async () => {
    window.localStorage.setItem('phr.currentRole', 'patient');
    const caregiverRoute = {
      path: '/records/:recordId',
      label: 'Record detail',
      minimumRole: 'patient' as PhrRole,
      element: <RecordDetailPage />,
    };

    render(
      <ThemeProvider>
        <PhrAccessProvider>
          <MemoryRouter initialEntries={['/records/record-missing-001']}>
            <Routes>
              <Route
                path="/records/:recordId"
                element={<ProtectedPhrRoute route={caregiverRoute} />}
              />
            </Routes>
          </MemoryRouter>
        </PhrAccessProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('FHIR resource rendering')).toBeInTheDocument();
      expect(screen.getByText('No record payload is available for the requested identifier.')).toBeInTheDocument();
    });
  });

  it('denies direct URL access to caregiver routes for patient sessions', async () => {
    window.localStorage.setItem('phr.currentRole', 'patient');
    const recordDetailRoute = {
      path: '/labs',
      label: 'Labs',
      minimumRole: 'caregiver' as PhrRole,
      element: <RecordDetailPage />,
    };

    render(
      <ThemeProvider>
        <PhrAccessProvider>
          <MemoryRouter initialEntries={['/labs']}>
            <Routes>
              <Route
                path="/labs"
                element={<ProtectedPhrRoute route={recordDetailRoute} />}
              />
            </Routes>
          </MemoryRouter>
        </PhrAccessProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Permission denied')).toBeInTheDocument();
      expect(screen.getByText(/not available for the current persona/i)).toBeInTheDocument();
    });
  });

  it('hides clinician-only emergency route and header action for patient persona in shell navigation', async () => {
    render(
      <ThemeProvider>
        <PhrAccessProvider>
          <MemoryRouter>
            <AppShell />
          </MemoryRouter>
        </PhrAccessProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
    });
    expect(screen.queryByText('Emergency')).not.toBeInTheDocument();
    expect(screen.queryByText('Emergency Access Review')).not.toBeInTheDocument();
  });

  it('renders emergency header action only when backend entitles clinician role', async () => {
    render(
      <ThemeProvider>
        <PhrAccessProvider>
          <MemoryRouter>
            <AppShell />
          </MemoryRouter>
        </PhrAccessProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByLabelText('Persona visibility menu'));
    fireEvent.click(screen.getByText('Clinician'));

    await waitFor(() => {
      expect(screen.getByText('Emergency Access Review')).toBeInTheDocument();
    });
    expect(screen.getByText('Emergency')).toBeInTheDocument();
  });

  it('renders release cockpit only when admin entitlements expose the route', async () => {
    window.localStorage.setItem('phr.currentRole', 'admin');

    render(
      <ThemeProvider>
        <PhrAccessProvider>
          <MemoryRouter initialEntries={['/release-readiness']}>
            <ReleaseCockpitPage />
          </MemoryRouter>
        </PhrAccessProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('PHR release cockpit')).toBeInTheDocument();
      expect(screen.getByText('FHIR runtime registry')).toBeInTheDocument();
      expect(screen.getByText('bdcee47c1e30')).toBeInTheDocument();
    });
  });

  it('keeps release cockpit admin-scoped in route metadata', () => {
    const route = phrRouteContracts.find((candidate) => candidate.path === '/release-readiness');
    expect(route?.minimumRole).toBe('admin');
    expect(route?.actions).toContain('view-release-readiness');
    expect(route).not.toHaveProperty('element');
  });

  it('defines emergency workflow as clinician-scoped in route metadata', () => {
    const emergencyRoute = phrRouteContracts.find((route) => route.path === '/emergency');
    expect(emergencyRoute?.minimumRole).toBe('clinician');
    expect(emergencyRoute?.emergencyAction).toBe(true);
    expect(emergencyRoute).not.toHaveProperty('element');
  });

  it('rejects direct URL access to clinician routes for patient persona', async () => {
    window.localStorage.setItem('phr.currentRole', 'patient');
    const emergencyRouteContract = phrRouteContracts.find((route) => route.path === '/emergency');
    const emergencyRoute = emergencyRouteContract ? attachPhrRouteElement(emergencyRouteContract) : undefined;
    expect(emergencyRoute).toBeDefined();

    render(
      <ThemeProvider>
        <PhrAccessProvider>
          <MemoryRouter initialEntries={['/emergency']}>
            <Routes>
              <Route path="/emergency" element={<ProtectedPhrRoute route={emergencyRoute!} />} />
            </Routes>
          </MemoryRouter>
        </PhrAccessProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Permission denied')).toBeInTheDocument();
      expect(screen.getByText(/not available for the current persona/i)).toBeInTheDocument();
    });
  });
}); 
