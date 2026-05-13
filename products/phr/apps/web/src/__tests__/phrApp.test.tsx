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
  const fetchMock: typeof fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
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
