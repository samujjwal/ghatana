/**
 * Tests for DashboardPage — verifies loading, error, and widget display states.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DashboardPage } from '../DashboardPage';

vi.mock('../../api/phrApi', () => ({
  fetchDashboardData: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: {
      tenantId: 'tenant-test',
      principalId: 'patient-test',
      role: 'patient',
      name: 'Patient Test',
      expiresAt: '2026-05-28T02:00:00Z',
    },
  }),
}));

import { fetchDashboardData } from '../../api/phrApi';

const mockFetch = fetchDashboardData as ReturnType<typeof vi.fn>;

const sampleData = {
  patient: { name: 'Ram Bahadur', location: 'Kathmandu', bloodType: 'B+', emergencyContact: '+977-9800000001' },
  consents: [{ id: 'con-1' }],
  appointments: [{ id: 'apt-1' }, { id: 'apt-2' }],
  labs: [{ id: 'lab-1' }],
  medications: [{ id: 'med-1' }, { id: 'med-2' }, { id: 'med-3' }],
  records: [],
};

describe('DashboardPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<DashboardPage />);
    expect(screen.getByText('dashboard.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network failure'));
    render(<DashboardPage />);
    await waitFor(() =>
      expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy()
    );
  });

  it('displays patient name after successful fetch', async () => {
    mockFetch.mockResolvedValue(sampleData);
    render(<DashboardPage />);
    await waitFor(() => expect(screen.getByText('Ram Bahadur')).toBeTruthy());
  });

  it('shows metric for consent count', async () => {
    mockFetch.mockResolvedValue(sampleData);
    render(<DashboardPage />);
    await waitFor(() => expect(screen.getByText('dashboard.metric.consent')).toBeTruthy());
  });

  it('shows metric for medication count', async () => {
    mockFetch.mockResolvedValue(sampleData);
    render(<DashboardPage />);
    await waitFor(() => expect(screen.getByText('dashboard.metric.medications')).toBeTruthy());
  });

  it('shows empty state when data is null', async () => {
    mockFetch.mockResolvedValue(null);
    render(<DashboardPage />);
    await waitFor(() => expect(screen.getByText('dashboard.empty')).toBeTruthy());
  });

  it('calls fetchDashboardData on mount', async () => {
    mockFetch.mockResolvedValue(sampleData);
    render(<DashboardPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith({
      tenantId: 'tenant-test',
      principalId: 'patient-test',
      role: 'patient',
    }));
  });
});
