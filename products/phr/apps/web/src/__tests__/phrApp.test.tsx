import React from 'react';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@ghatana/theme';
import { MemoryRouter, Route, Routes } from 'react-router';
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
});