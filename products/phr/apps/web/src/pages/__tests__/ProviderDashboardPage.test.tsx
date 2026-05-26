/**
 * Tests for ProviderDashboardPage — verifies loading, error, and patient roster display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ProviderDashboardPage } from '../ProviderDashboardPage';

vi.mock('../../api/phrApi', () => ({
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

import { fetchProviderPatients } from '../../api/phrApi';

const mockFetch = fetchProviderPatients as ReturnType<typeof vi.fn>;

const patients = [
  { id: 'p-1', name: 'Sita Rai', age: 45, status: 'active' as const, lastVisit: '2025-01-01T00:00:00Z', nextAppointment: null },
  { id: 'p-2', name: 'Ram Thapa', age: 60, status: 'critical' as const, lastVisit: null, nextAppointment: '2025-03-15T09:00:00Z' },
];

describe('ProviderDashboardPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<ProviderDashboardPage />);
    expect(screen.getByText('provider.dashboard.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<ProviderDashboardPage />);
    await waitFor(() => expect(screen.getByText(/provider\.dashboard\.error/)).toBeTruthy());
  });

  it('shows empty message when no patients', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ProviderDashboardPage />);
    await waitFor(() => expect(screen.getByText('provider.patients.empty')).toBeTruthy());
  });

  it('displays patient names after successful fetch', async () => {
    mockFetch.mockResolvedValue(patients);
    render(<ProviderDashboardPage />);
    await waitFor(() => expect(screen.getByText('Sita Rai')).toBeTruthy());
    expect(screen.getByText('Ram Thapa')).toBeTruthy();
  });

  it('calls fetchProviderPatients with session context', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ProviderDashboardPage />);
    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith({
        tenantId: 't1',
        principalId: 'clinician-1',
        role: 'clinician',
      })
    );
  });
});
