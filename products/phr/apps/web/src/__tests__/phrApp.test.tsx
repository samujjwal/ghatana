import React from 'react';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@ghatana/theme';
import { MemoryRouter, Route, Routes } from 'react-router';
import { PhrAccessProvider } from '../auth/PhrAccessContext';
import { phrRouteManifest } from '../routeManifest';
import { AppShell } from '../layout/AppShell';
import { DashboardPage } from '../pages/DashboardPage';
import { RecordDetailPage } from '../pages/RecordDetailPage';

describe('PHR web app', () => {
  it('renders dashboard metrics', () => {
    render(
      <ThemeProvider>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </ThemeProvider>,
    );

    expect(screen.getByText('Aarati Shrestha')).toBeInTheDocument();
    expect(screen.getByText('Active consent flows')).toBeInTheDocument();
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
    expect(screen.getByText(/resourceType/)).toBeInTheDocument();
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
}); 
