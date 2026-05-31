import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@ghatana/theme';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PhrAccessProvider } from '../auth/PhrAccessContext';
import { PhrSessionProvider } from '../auth/PhrSessionContext';
import type { PhrRole } from '../auth/PhrAccessContext';
import type { DashboardData } from '../types';
import { PHR_ROLE_ORDER, phrRouteContracts } from '../routeManifest';
import { attachPhrRouteElement, type PhrRouteManifestEntry } from '../phrRouteElements';
import { AppShell } from '../layout/AppShell';
import { DashboardPage } from '../pages/DashboardPage';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { RecordDetailPage } from '../pages/RecordDetailPage';
import { ReleaseCockpitPage } from '../pages/ReleaseCockpitPage';
import { ProtectedPhrRoute } from '../routes';

vi.mock('../api/patientApi', async () => {
  const actual = await vi.importActual<typeof import('../api/patientApi')>('../api/patientApi');
  return {
    ...actual,
    fetchDashboardData: vi.fn(),
  };
});

vi.mock('../api/recordsApi', async () => {
  const actual = await vi.importActual<typeof import('../api/recordsApi')>('../api/recordsApi');
  return {
    ...actual,
    fetchRecordDetail: vi.fn(),
  };
});

import { fetchDashboardData } from '../api/patientApi';
import { fetchRecordDetail } from '../api/recordsApi';

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
  tenantId: 'tenant-health-1',
  principalId: 'patient-001',
  role: 'patient',
  correlationId: 'corr-dashboard-1',
  profileSummary: {
    name: 'Aarati Shrestha',
    email: 'aarati@example.test',
    providerId: null,
    active: true,
  },
  nextAppointment: {
    appointmentId: 'appointment-1',
    provider: 'Dr. Koirala',
    scheduledTime: '2026-05-06T09:00:00Z',
    type: 'Endocrinology',
  },
  medications: {
    activeCount: 1,
    adherenceAlert: false,
  },
  recentObservations: {
    count: 1,
    hasCritical: false,
  },
  activeConditions: {
    count: 1,
    hasChronic: true,
  },
  documents: {
    totalCount: 0,
    pendingOcr: 0,
  },
  accessAlerts: {
    expiringConsents: 1,
    emergencyAccessPending: false,
  },
  generatedAt: '2026-05-30T01:00:00Z',
};

function renderDashboardPage(): void {
  setTestSession();
  render(
    <ThemeProvider>
      <PhrSessionProvider>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </PhrSessionProvider>
    </ThemeProvider>,
  );
}

function testSessionFor(role: PhrRole = 'patient') {
  const isClinicalActor = role === 'clinician' || role === 'admin';
  return {
  principalId: 'principal-test',
  tenantId: 'tenant-test',
    role,
    name: isClinicalActor ? 'Test Clinician' : 'Test User',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    persona: role,
    tier: 'core',
  };
}

function setTestSession(role: PhrRole = 'patient'): void {
  window.sessionStorage.setItem('phr.session', JSON.stringify(testSessionFor(role)));
}

function setTestAccessIdentity(role: PhrRole = 'patient'): void {
  window.localStorage.setItem('phr.currentRole', role);
  window.localStorage.setItem('phr.tenantId', 'tenant-test');
  window.localStorage.setItem('phr.principalId', 'principal-test');
}

function manifestEntryFor(path: string, element: React.ReactElement): PhrRouteManifestEntry {
  const route = phrRouteContracts.find((candidate) => candidate.path === path);
  if (!route) {
    throw new Error(`Missing PHR route contract for ${path}`);
  }
  return { ...route, element };
}

describe('PHR web app', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fetchDashboardData).mockResolvedValue(dashboardFixture);
    vi.mocked(fetchRecordDetail).mockResolvedValue({
      record: {
        id: 'record-missing-001',
        title: 'FHIR resource rendering',
        category: 'clinical',
        updatedAt: '2026-05-27T12:00:00Z',
        resourceType: 'Observation',
        fhirJson: '{}',
      },
      fhirJson: '{}',
      accessAudit: {
        accessedAt: '2026-05-27T12:00:00Z',
        accessedBy: 'principal-test',
      },
    });
    mockEntitlementFetch();
    window.localStorage.clear();
    window.sessionStorage.clear();
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
      expect(screen.getByText('Upstream consent service unavailable')).toBeInTheDocument();
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
    setTestSession();
    const caregiverRoute = manifestEntryFor('/records/:recordId', <RecordDetailPage />);

    render(
      <ThemeProvider>
        <PhrSessionProvider>
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
        </PhrSessionProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('FHIR resource rendering')).toBeInTheDocument();
      expect(screen.getByText('FHIR Record Detail')).toBeInTheDocument();
    });
  });

  it('denies direct URL access to caregiver routes for patient sessions', async () => {
    window.localStorage.setItem('phr.currentRole', 'patient');
    setTestSession();
    const recordDetailRoute = manifestEntryFor('/labs', <RecordDetailPage />);

    render(
      <ThemeProvider>
        <PhrSessionProvider>
          <PhrAccessProvider>
            <MemoryRouter initialEntries={['/labs']}>
              <Routes>
                <Route
                  path="/labs"
                  element={<ProtectedPhrRoute route={recordDetailRoute} />}
                />
                <Route path="/forbidden" element={<ForbiddenPage />} />
              </Routes>
            </MemoryRouter>
          </PhrAccessProvider>
        </PhrSessionProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Access denied')).toBeInTheDocument();
    });
  });

  it('hides clinician-only emergency route and header action for patient persona in shell navigation', async () => {
    setTestAccessIdentity();
    setTestSession();

    render(
      <ThemeProvider>
        <PhrSessionProvider>
          <PhrAccessProvider>
            <MemoryRouter>
              <AppShell />
            </MemoryRouter>
          </PhrAccessProvider>
        </PhrSessionProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
    });
    expect(screen.queryByText('Emergency')).not.toBeInTheDocument();
    expect(screen.queryByText('Emergency Access Review')).not.toBeInTheDocument();
  });

  it('renders emergency header action only when backend entitles clinician role', async () => {
    setTestAccessIdentity('clinician');
    setTestSession('clinician');

    render(
      <ThemeProvider>
        <PhrSessionProvider>
          <PhrAccessProvider>
            <MemoryRouter>
              <AppShell />
            </MemoryRouter>
          </PhrAccessProvider>
        </PhrSessionProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getByText('Emergency Access Review')).toBeInTheDocument();
    });
    expect(screen.getByText('Emergency')).toBeInTheDocument();
  });

  it('renders release cockpit only when admin entitlements expose the route', async () => {
    setTestAccessIdentity('admin');

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
    setTestSession();
    const emergencyRouteContract = phrRouteContracts.find((route) => route.path === '/emergency');
    const emergencyRoute = emergencyRouteContract ? attachPhrRouteElement(emergencyRouteContract) : undefined;
    expect(emergencyRoute).toBeDefined();

    render(
      <ThemeProvider>
        <PhrSessionProvider>
          <PhrAccessProvider>
            <MemoryRouter initialEntries={['/emergency']}>
              <Routes>
                <Route path="/emergency" element={<ProtectedPhrRoute route={emergencyRoute!} />} />
                <Route path="/forbidden" element={<ForbiddenPage />} />
              </Routes>
            </MemoryRouter>
          </PhrAccessProvider>
        </PhrSessionProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Access denied')).toBeInTheDocument();
    });
  });

  it('renders not found for hidden route direct links even when the role is privileged', async () => {
    window.localStorage.setItem('phr.currentRole', 'admin');
    setTestSession('admin');
    const hiddenRouteContract = phrRouteContracts.find((route) => route.path === '/provider/dashboard');
    const hiddenRoute = hiddenRouteContract ? attachPhrRouteElement(hiddenRouteContract) : undefined;
    expect(hiddenRoute).toBeDefined();

    render(
      <ThemeProvider>
        <PhrSessionProvider>
          <PhrAccessProvider>
            <MemoryRouter initialEntries={['/provider/dashboard']}>
              <Routes>
                <Route path="/provider/dashboard" element={<ProtectedPhrRoute route={hiddenRoute!} />} />
                <Route path="/not-found" element={<NotFoundPage />} />
                <Route path="/forbidden" element={<ForbiddenPage />} />
              </Routes>
            </MemoryRouter>
          </PhrAccessProvider>
        </PhrSessionProvider>
      </ThemeProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText('Page not found')).toBeInTheDocument();
    });
  });
}); 
