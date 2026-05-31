/**
 * Tests for DashboardPage — verifies loading, error, and widget display states.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DashboardPage } from '../DashboardPage';

vi.mock('../../api/patientApi', () => ({
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
      persona: 'patient',
      tier: 'core',
      name: 'Patient Test',
      expiresAt: '2026-05-28T02:00:00Z',
    },
  }),
}));

import { fetchDashboardData } from '../../api/patientApi';

const mockFetch = fetchDashboardData as ReturnType<typeof vi.fn>;

const sampleData = {
  tenantId: 'tenant-test',
  principalId: 'patient-test',
  role: 'patient',
  correlationId: 'corr-1',
  profileSummary: { name: 'Ram Bahadur', active: true },
  nextAppointment: {
    appointmentId: 'apt-1',
    scheduledTime: '2026-05-30T09:00:00Z',
    provider: 'provider-1',
    type: 'follow-up',
  },
  medications: { activeCount: 3, adherenceAlert: false },
  recentObservations: { count: 1, hasCritical: false },
  activeConditions: { count: 2, hasChronic: true },
  documents: { totalCount: 4, pendingOcr: 1 },
  accessAlerts: { expiringConsents: 1, emergencyAccessPending: false },
  generatedAt: '2026-05-30T01:00:00Z',
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

  it('does not render legacy placeholder patient facts or fake dashboard arrays', async () => {
    mockFetch.mockResolvedValue(sampleData);
    render(<DashboardPage />);

    await waitFor(() => expect(screen.getByText('Ram Bahadur')).toBeTruthy());
    expect(screen.queryByText('Unknown')).toBeNull();
    expect(screen.queryByText('Blood group')).toBeNull();
    expect(screen.queryByText('dashboard.carePlan.title')).toBeNull();
    expect(screen.queryByText('dashboard.emergency.title')).toBeNull();
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
      persona: 'patient',
      tier: 'core',
      facilityId: undefined,
    }));
  });
});
