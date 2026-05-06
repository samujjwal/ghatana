import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@ghatana/theme';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PhrAccessProvider } from '../auth/PhrAccessContext';
import type { DashboardData } from '../types';
import { phrRouteManifest } from '../routeManifest';
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

  it('renders a FHIR record detail fallback', () => {
    render(
      <ThemeProvider>
        <MemoryRouter initialEntries={['/records/record-lab-001']}>
          <Routes>
            <Route path="/records/:recordId" element={<RecordDetailPage />} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>,
    );

    expect(screen.getByText('FHIR resource rendering')).toBeInTheDocument();
    expect(screen.getByText('No record payload is available for the requested identifier.')).toBeInTheDocument();
  });

  it('hides clinician-only emergency route for patient persona in shell navigation', () => {
    render(
      <ThemeProvider>
        <PhrAccessProvider>
          <MemoryRouter>
            <AppShell />
          </MemoryRouter>
        </PhrAccessProvider>
      </ThemeProvider>,
    );

    expect(screen.queryByText('Emergency')).not.toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
  });

  it('defines emergency workflow as clinician-scoped in route metadata', () => {
    const emergencyRoute = phrRouteManifest.find((route) => route.path === '/emergency');
    expect(emergencyRoute?.minimumRole).toBe('clinician');
    expect(emergencyRoute?.emergencyAction).toBe(true);
  });

  it('rejects direct URL access to clinician routes for patient persona', async () => {
    window.localStorage.setItem('phr.currentRole', 'patient');
    const emergencyRoute = phrRouteManifest.find((route) => route.path === '/emergency');
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
