/**
 * Tests for ImmunizationsPage — verifies loading, error, empty, and immunization display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ImmunizationsPage } from '../ImmunizationsPage';

vi.mock('../../api/phrApi', () => ({
  fetchImmunizations: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ principalId: 'patient-42', tenantId: 't1', role: 'patient', token: 'tok' }),
}));

import { fetchImmunizations } from '../../api/phrApi';

const mockFetch = fetchImmunizations as ReturnType<typeof vi.fn>;

const immunizations = [
  { id: 'i1', vaccine: 'BCG', date: '2000-01-10', dose: '1', site: 'Left arm', cvxCode: '19' },
  { id: 'i2', vaccine: 'MMR', date: '2002-06-20', dose: '2', cvxCode: '03' },
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
      expect(screen.getByText(/immunizations\.error/)).toBeTruthy()
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
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42'));
  });
});
