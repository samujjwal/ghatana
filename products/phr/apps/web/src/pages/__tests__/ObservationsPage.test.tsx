import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ObservationsPage } from '../ObservationsPage';
import { fetchObservations } from '../../api/clinicalApi';
import type { ObservationSummary } from '../../types';

vi.mock('@ghatana/charts', () => ({
  TimeSeriesChart: ({ data }: { data: unknown[] }) => <div data-testid="trend-chart">{data.length}</div>,
}));

vi.mock('../../api/clinicalApi', () => ({
  fetchObservations: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, values?: Record<string, string | number>) => {
    if (key === 'observations.readingCount') return `${values?.count} readings`;
    if (key === 'observations.chart.label') return `${values?.metric} trend chart`;
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

const mockFetch = vi.mocked(fetchObservations);

const observations: ObservationSummary[] = [
  { id: 'o1', name: 'Haemoglobin', value: '13.5', unit: 'g/dL', status: 'normal', recordedAt: '2026-03-10T07:00:00Z', effectiveDate: '2026-03-10T07:00:00Z', loincCode: '718-7' },
  { id: 'o2', name: 'Haemoglobin', value: '12.9', unit: 'g/dL', status: 'normal', recordedAt: '2026-02-10T07:00:00Z', effectiveDate: '2026-02-10T07:00:00Z', loincCode: '718-7' },
  { id: 'o3', name: 'Platelet count', value: '98', unit: '10^9/L', status: 'critical', recordedAt: '2026-03-10T07:00:00Z', effectiveDate: '2026-03-10T07:00:00Z', loincCode: '26515-7' },
];

describe('ObservationsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<ObservationsPage />);
    expect(screen.getByText('observations.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('server error'));
    render(<ObservationsPage />);
    await waitFor(() => expect(screen.getByText(/server error/)).toBeTruthy());
  });

  it('shows empty message when no observations', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ObservationsPage />);
    await waitFor(() => expect(screen.getByText('observations.empty')).toBeTruthy());
  });

  it('filters observations by metric and status', async () => {
    mockFetch.mockResolvedValue(observations);
    render(<ObservationsPage />);

    await waitFor(() => expect(screen.getByRole('button', { name: /Haemoglobin/ })).toBeTruthy());
    fireEvent.change(screen.getByLabelText('observations.filters.status'), { target: { value: 'critical' } });

    await waitFor(() => expect(screen.queryByRole('button', { name: /Haemoglobin/ })).toBeNull());
    expect(screen.getByRole('button', { name: /Platelet count.*observations\.status\.critical/ })).toBeTruthy();
  });

  it('expands a trend chart and reading table for a metric', async () => {
    mockFetch.mockResolvedValue(observations);
    render(<ObservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /Haemoglobin/ }));

    expect(screen.getByText('observations.trendHistory')).toBeTruthy();
    expect(screen.getByTestId('trend-chart')).toHaveTextContent('2');
    expect(screen.getByText('13.5')).toBeTruthy();
    expect(screen.getByText('12.9')).toBeTruthy();
  });

  it('calls fetchObservations with authenticated session context', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ObservationsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42', expect.objectContaining({
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    })));
  });
});
