import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ImmunizationsPage } from '../ImmunizationsPage';
import { fetchImmunizations } from '../../api/clinicalApi';
import type { ImmunizationSummary } from '../../types';

vi.mock('../../api/clinicalApi', () => ({
  fetchImmunizations: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, values?: Record<string, string | number>) => {
    if (key === 'immunizations.date') return `Administered ${values?.date}`;
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

const mockFetch = vi.mocked(fetchImmunizations);

const immunizations: ImmunizationSummary[] = [
  { id: 'i1', vaccine: 'BCG', date: '2000-01-10', occurrenceDate: '2000-01-10', dose: '1', site: 'Left arm', lotNumber: 'LOT-1', cvxCode: '19', status: 'completed' },
  { id: 'i2', vaccine: 'MMR', date: '2002-06-20', occurrenceDate: '2002-06-20', dose: '2', cvxCode: '03', status: 'not-done' },
  { id: 'i3', vaccine: 'Typhoid', date: '2004-06-20', occurrenceDate: '2004-06-20', status: 'entered-in-error' },
];

describe('ImmunizationsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<ImmunizationsPage />);
    expect(screen.getByText('immunizations.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<ImmunizationsPage />);
    await waitFor(() => expect(screen.getByText(/network/)).toBeTruthy());
  });

  it('shows empty message when no immunizations', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ImmunizationsPage />);
    await waitFor(() => expect(screen.getByText('immunizations.empty')).toBeTruthy());
  });

  it('groups immunizations by status and shows permanent retention', async () => {
    mockFetch.mockResolvedValue(immunizations);
    render(<ImmunizationsPage />);

    await waitFor(() => expect(screen.getByText('BCG')).toBeTruthy());
    expect(screen.getByText('MMR')).toBeTruthy();
    expect(screen.getByText('Typhoid')).toBeTruthy();
    expect(screen.getByText('immunizations.group.completed')).toBeTruthy();
    expect(screen.getAllByText('immunizations.retention.permanent')).toHaveLength(3);
  });

  it('opens detail state with CVX, lot, dose, and site', async () => {
    mockFetch.mockResolvedValue(immunizations);
    render(<ImmunizationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /BCG/ }));

    expect(screen.getByText('immunizations.cvx')).toBeTruthy();
    expect(screen.getByText('19')).toBeTruthy();
    expect(screen.getByText('immunizations.lot')).toBeTruthy();
    expect(screen.getByText('LOT-1')).toBeTruthy();
    expect(screen.getByText('immunizations.doseLabel')).toBeTruthy();
    expect(screen.getByText('1')).toBeTruthy();
    expect(screen.getByText('immunizations.site')).toBeTruthy();
    expect(screen.getByText('Left arm')).toBeTruthy();
  });

  it('calls fetchImmunizations with authenticated session context', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ImmunizationsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42', expect.objectContaining({
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    })));
  });
});
