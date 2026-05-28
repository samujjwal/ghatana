/**
 * Tests for ConditionsPage — verifies active/resolved condition grouping and error handling.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConditionsPage } from '../ConditionsPage';

vi.mock('../../api/clinicalApi', () => ({
  fetchConditions: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }, isAuthenticated: true, setSession: vi.fn(), clearSession: vi.fn() }),
}));

import { fetchConditions } from '../../api/clinicalApi';

const mockFetch = fetchConditions as ReturnType<typeof vi.fn>;

const conditions = [
  { id: 'c1', name: 'Type 2 diabetes mellitus', code: 'E11', display: 'Type 2 diabetes mellitus', status: 'active' as const, onsetDate: '2018-03-01' },
  { id: 'c2', name: 'Upper respiratory infection', code: 'J06', display: 'Upper respiratory infection', status: 'resolved' as const, onsetDate: '2024-01-10', resolvedDate: '2024-01-20' },
];

function renderPage(): void {
  render(
    <MemoryRouter>
      <ConditionsPage />
    </MemoryRouter>,
  );
}

describe('ConditionsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText(/conditions\.loading/)).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy()
    );
  });

  it('displays active condition name', async () => {
    mockFetch.mockResolvedValue(conditions);
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Type 2 diabetes mellitus')).toBeTruthy()
    );
  });

  it('displays resolved condition name', async () => {
    mockFetch.mockResolvedValue(conditions);
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('Upper respiratory infection')).toBeTruthy()
    );
  });

  it('calls fetchConditions with the session principalId', async () => {
    mockFetch.mockResolvedValue([]);
    renderPage();
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42', expect.any(Object)));
  });
});
