import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { LabsPage } from '../LabsPage';
import { fetchLabs } from '../../api/clinicalApi';
import type { ObservationSummary } from '../../types';

vi.mock('../../api/clinicalApi', () => ({
  fetchLabs: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, values?: Record<string, string | number>) => {
    if (key === 'labs.criticalCount') return `${values?.count} critical`;
    if (key === 'labs.attentionCount') return `${values?.count} attention`;
    if (key === 'labs.collected') return `Collected ${values?.date}`;
    return key;
  },
  formatPhrDate: (date: string) => date,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() },
    isAuthenticated: true,
    setSession: vi.fn(),
    clearSession: vi.fn(),
  }),
}));

const mockFetch = vi.mocked(fetchLabs);

const labs: ObservationSummary[] = [
  { id: 'lab-1', name: 'Hemoglobin A1c', recordedAt: '2025-01-05', effectiveDate: '2025-01-05', status: 'normal', value: '6.1', unit: '%' },
  { id: 'lab-2', name: 'Blood Glucose', recordedAt: '2025-01-05', effectiveDate: '2025-01-05', status: 'attention', value: '9.3', unit: 'mmol/L' },
  { id: 'lab-3', name: 'Potassium', recordedAt: '2025-01-06', effectiveDate: '2025-01-06', status: 'critical', value: '6.2', unit: 'mmol/L' },
];

function renderPage(): void {
  render(
    <MemoryRouter>
      <LabsPage />
    </MemoryRouter>,
  );
}

describe('LabsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText('labs.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    renderPage();
    await waitFor(() => expect(screen.getByText(/network/)).toBeTruthy());
  });

  it('displays lab names and values after successful fetch', async () => {
    mockFetch.mockResolvedValue(labs);
    renderPage();
    await waitFor(() => expect(screen.getByText('Hemoglobin A1c')).toBeTruthy());
    expect(screen.getByText('Blood Glucose')).toBeTruthy();
    expect(screen.getByText('Potassium')).toBeTruthy();
    expect(screen.getByText('6.1')).toBeTruthy();
  });

  it('renders abnormal and critical semantics from observation statuses', async () => {
    mockFetch.mockResolvedValue(labs);
    renderPage();

    await waitFor(() => expect(screen.getByText('1 critical')).toBeTruthy());
    expect(screen.getByText('1 attention')).toBeTruthy();
    expect(screen.getByText('observations.status.critical')).toBeTruthy();
    expect(screen.getByText('observations.status.attention')).toBeTruthy();
  });

  it('links patients to the observations trend page', async () => {
    mockFetch.mockResolvedValue(labs);
    renderPage();
    const link = await screen.findByRole('link', { name: 'labs.trendsLink' });
    expect(link).toHaveAttribute('href', '/observations');
  });
});
