/**
 * Tests for LabsPage — verifies loading, error, and lab result display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { LabsPage } from '../LabsPage';

vi.mock('../../api/clinicalApi', () => ({
  fetchObservations: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrDate: (d: string) => d,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() },
    isAuthenticated: true,
    setSession: vi.fn(),
    clearSession: vi.fn(),
  }),
}));

import { fetchObservations } from '../../api/clinicalApi';

const mockFetch = fetchObservations as ReturnType<typeof vi.fn>;

const labs = [
  { id: 'lab-1', name: 'Hemoglobin A1c', recordedAt: '2025-01-05', effectiveDate: '2025-01-05', status: 'normal' as const, value: '6.1', unit: '%' },
  { id: 'lab-2', name: 'Blood Glucose', recordedAt: '2025-01-05', effectiveDate: '2025-01-05', status: 'attention' as const, value: '9.3', unit: 'mmol/L' },
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
    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });

  it('displays lab names after successful fetch', async () => {
    mockFetch.mockResolvedValue(labs);
    renderPage();
    await waitFor(() => expect(screen.getByText('Hemoglobin A1c')).toBeTruthy());
    expect(screen.getByText('Blood Glucose')).toBeTruthy();
  });

  it('renders lab values', async () => {
    mockFetch.mockResolvedValue(labs);
    renderPage();
    await waitFor(() => expect(screen.getByText('6.1')).toBeTruthy());
  });

  it('renders labs title key', async () => {
    mockFetch.mockResolvedValue(labs);
    renderPage();
    await waitFor(() => expect(screen.getByText('labs.title')).toBeTruthy());
  });
});
