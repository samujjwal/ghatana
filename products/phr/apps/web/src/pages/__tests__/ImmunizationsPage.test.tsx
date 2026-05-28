/**
 * Tests for ImmunizationsPage — verifies loading, error, empty, and immunization display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ImmunizationsPage } from '../ImmunizationsPage';

vi.mock('../../api/clinicalApi', () => ({
  fetchImmunizations: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }, isAuthenticated: true, setSession: vi.fn(), clearSession: vi.fn() }),
}));

import { fetchImmunizations } from '../../api/clinicalApi';

const mockFetch = fetchImmunizations as ReturnType<typeof vi.fn>;

const immunizations = [
  { id: 'i1', vaccine: 'BCG', date: '2000-01-10', occurrenceDate: '2000-01-10', dose: '1', site: 'Left arm', cvxCode: '19', status: 'completed' as const },
  { id: 'i2', vaccine: 'MMR', date: '2002-06-20', occurrenceDate: '2002-06-20', dose: '2', cvxCode: '03', status: 'completed' as const },
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
    await waitFor(() =>
      expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy()
    );
  });

  it('shows empty message when no immunizations', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ImmunizationsPage />);
    await waitFor(() =>
      expect(screen.getByText('immunizations.empty')).toBeTruthy()
    );
  });

  it('displays first vaccine name', async () => {
    mockFetch.mockResolvedValue(immunizations);
    render(<ImmunizationsPage />);
    await waitFor(() =>
      expect(screen.getByText('BCG')).toBeTruthy()
    );
  });

  it('displays second vaccine name', async () => {
    mockFetch.mockResolvedValue(immunizations);
    render(<ImmunizationsPage />);
    await waitFor(() =>
      expect(screen.getByText('MMR')).toBeTruthy()
    );
  });

  it('calls fetchImmunizations with the session principalId', async () => {
    mockFetch.mockResolvedValue([]);
    render(<ImmunizationsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42', expect.any(Object)));
  });
});
