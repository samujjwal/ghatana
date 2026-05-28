/**
 * Tests for ObservationsPage — verifies loading, error, and lab observation display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ObservationsPage } from '../ObservationsPage';

vi.mock('../../api/clinicalApi', () => ({
  fetchObservations: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }, isAuthenticated: true, setSession: vi.fn(), clearSession: vi.fn() }),
}));

import { fetchObservations } from '../../api/clinicalApi';

const mockFetch = fetchObservations as ReturnType<typeof vi.fn>;

const observations = [
  { id: 'o1', name: 'Haemoglobin', value: '13.5', unit: 'g/dL', status: 'normal' as const, recordedAt: '2026-03-10T07:00:00Z', effectiveDate: '2026-03-10T07:00:00Z', loincCode: '718-7' },
  { id: 'o2', name: 'Platelet count', value: '98', unit: '10^9/L', status: 'abnormal' as const, recordedAt: '2026-03-10T07:00:00Z', effectiveDate: '2026-03-10T07:00:00Z', loincCode: '26515-7' },
];

describe('ObservationsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<ObservationsPage />);
    expect(screen.getByText('Loading observations...')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('server error'));
    render(<ObservationsPage />);
    await waitFor(() =>
      expect(screen.getByText(/Error: server error/)).toBeTruthy()
    );
  });

  it('shows empty message when no observations', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ObservationsPage />);
    await waitFor(() =>
      expect(screen.getByText('No observations found')).toBeTruthy()
    );
  });

  it('displays normal observation name', async () => {
    mockFetch.mockResolvedValue(observations);
    render(<ObservationsPage />);
    await waitFor(() =>
      expect(screen.getByText('Haemoglobin')).toBeTruthy()
    );
  });

  it('displays abnormal observation name', async () => {
    mockFetch.mockResolvedValue(observations);
    render(<ObservationsPage />);
    await waitFor(() =>
      expect(screen.getByText('Platelet count')).toBeTruthy()
    );
  });

  it('calls fetchObservations with the session principalId', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ObservationsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42', expect.any(Object)));
  });
});
