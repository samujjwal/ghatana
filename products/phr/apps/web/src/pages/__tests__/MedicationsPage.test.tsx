/**
 * Tests for MedicationsPage — verifies loading, error, and medication list display.
 */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { MedicationsPage } from '../MedicationsPage';

vi.mock('../../api/clinicalApi', () => ({
  fetchMedications: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrPercent: (n: number) => String(n),
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() },
    isAuthenticated: true,
    setSession: vi.fn(),
    clearSession: vi.fn(),
  }),
}));

import { fetchMedications } from '../../api/clinicalApi';

const mockFetch = vi.mocked(fetchMedications);

const medications = [
  { id: 'med-1', medication: 'Metformin', dosage: '500mg', schedule: 'Twice daily', adherence: 95, status: 'active' as const },
  { id: 'med-2', medication: 'Amlodipine', dosage: '5mg', schedule: 'Once daily', adherence: 80, status: 'stopped' as const },
];

function renderPage(): void {
  render(
    <MemoryRouter>
      <MedicationsPage />
    </MemoryRouter>,
  );
}

describe('MedicationsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText('medications.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    renderPage();
    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });

  it('displays medication names after successful fetch', async () => {
    mockFetch.mockResolvedValue(medications);
    renderPage();
    await waitFor(() => expect(screen.getByText(/Metformin/)).toBeTruthy());
    expect(screen.queryByText(/Amlodipine/)).toBeNull();
  });

  it('does not fabricate adherence when the backend omits adherence', async () => {
    mockFetch.mockResolvedValue([
      { id: 'med-1', medication: 'Metformin', dosage: '500mg', schedule: 'Twice daily', status: 'active' as const },
    ]);

    renderPage();

    await waitFor(() => expect(screen.getByText(/Metformin/)).toBeTruthy());
    expect(screen.queryByText(/medications.adherenceLabel/)).toBeNull();
  });

  it('renders the medications title key', async () => {
    mockFetch.mockResolvedValue(medications);
    renderPage();
    await waitFor(() => expect(screen.getByText('medications.title')).toBeTruthy());
  });

  it('calls fetchMedications on mount', async () => {
    mockFetch.mockResolvedValue([]);
    renderPage();
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));
  });

  it('shows historical medications on the history tab', async () => {
    mockFetch.mockResolvedValue(medications);
    renderPage();
    fireEvent.click(await screen.findByRole('tab', { name: 'medications.tabs.history' }));
    expect(screen.getByText(/Amlodipine/)).toBeTruthy();
    expect(screen.queryByText(/Metformin/)).toBeNull();
  });
});
