/**
 * Tests for ProviderPatientsPage — verifies loading, error, empty, and table display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ProviderPatientsPage } from '../ProviderPatientsPage';

vi.mock('../../api/adminApi', () => ({
  fetchProviderPatients: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: {
      principalId: 'clinician-1',
      tenantId: 't1',
      role: 'clinician' as const,
      name: 'Dr. Test',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    },
    isAuthenticated: true,
    setSession: vi.fn(),
    clearSession: vi.fn(),
  }),
}));

vi.mock('../../auth/PhrAccessContext', () => ({
  usePhrAccess: () => ({ role: 'clinician' as const }),
}));

import { fetchProviderPatients } from '../../api/adminApi';

const mockFetch = fetchProviderPatients as ReturnType<typeof vi.fn>;

const patients = [
  { id: 'p-1', name: 'Anita Gurung', age: 38, status: 'active' as const, lastVisit: '2025-01-10T00:00:00Z', nextAppointment: null },
  { id: 'p-2', name: 'Bikash Shrestha', age: 52, status: 'stable' as const, lastVisit: null, nextAppointment: '2025-04-01T09:00:00Z' },
];

describe('ProviderPatientsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<ProviderPatientsPage />);
    expect(screen.getByText('provider.patients.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<ProviderPatientsPage />);
    await waitFor(() => expect(screen.getByText(/provider\.patients\.error/)).toBeTruthy());
  });

  it('shows empty state when no patients', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ProviderPatientsPage />);
    await waitFor(() => expect(screen.getByText('provider.patients.empty')).toBeTruthy());
  });

  it('displays patient names in table', async () => {
    mockFetch.mockResolvedValue(patients);
    render(<ProviderPatientsPage />);
    await waitFor(() => expect(screen.getByText('Anita Gurung')).toBeTruthy());
    expect(screen.getByText('Bikash Shrestha')).toBeTruthy();
  });

  it('renders table aria-label', async () => {
    mockFetch.mockResolvedValue(patients);
    render(<ProviderPatientsPage />);
    await waitFor(() => expect(screen.getByRole('table')).toBeTruthy());
  });
});
