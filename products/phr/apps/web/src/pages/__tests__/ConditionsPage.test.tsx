import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ConditionsPage } from '../ConditionsPage';
import { fetchConditions } from '../../api/clinicalApi';
import type { ConditionSummary } from '../../types';

vi.mock('../../api/clinicalApi', () => ({
  fetchConditions: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, values?: Record<string, string | number>) => {
    if (key === 'conditions.detail.label') return `${values?.condition} details`;
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

const mockFetch = vi.mocked(fetchConditions);

const conditions: ConditionSummary[] = [
  { id: 'c1', name: 'Type 2 diabetes mellitus', code: 'E11', display: 'Type 2 diabetes mellitus', status: 'active', onsetDate: '2018-03-01', icdCode: 'E11' },
  { id: 'c2', name: 'Upper respiratory infection', code: 'J06', display: 'Upper respiratory infection', status: 'resolved', onsetDate: '2024-01-10', resolvedDate: '2024-01-20' },
  { id: 'c3', name: 'Hypertension', code: 'I10', display: 'Hypertension', status: 'chronic', onsetDate: '2019-05-01' },
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
    expect(screen.getByText('conditions.loading')).toBeTruthy();
  });

  it('shows denied copy when policy rejects access', async () => {
    mockFetch.mockRejectedValue(new Error('forbidden'));
    renderPage();
    await waitFor(() => expect(screen.getByText(/conditions\.denied/)).toBeTruthy());
  });

  it('displays active, resolved, and chronic condition names', async () => {
    mockFetch.mockResolvedValue(conditions);
    renderPage();
    await waitFor(() => expect(screen.getByText('Type 2 diabetes mellitus')).toBeTruthy());
    expect(screen.getByText('Upper respiratory infection')).toBeTruthy();
    expect(screen.getByText('Hypertension')).toBeTruthy();
  });

  it('filters by chronic status', async () => {
    mockFetch.mockResolvedValue(conditions);
    renderPage();

    await waitFor(() => expect(screen.getByText('Type 2 diabetes mellitus')).toBeTruthy());
    fireEvent.click(screen.getByRole('tab', { name: 'conditions.status.chronic' }));

    expect(screen.queryByText('Type 2 diabetes mellitus')).toBeNull();
    expect(screen.queryByText('Upper respiratory infection')).toBeNull();
    expect(screen.getByText('Hypertension')).toBeTruthy();
  });

  it('opens condition detail state', async () => {
    mockFetch.mockResolvedValue(conditions);
    renderPage();

    await waitFor(() => expect(screen.getByText('Type 2 diabetes mellitus')).toBeTruthy());
    const detailButton = screen.getAllByRole('button', { name: 'conditions.detail.toggle' })[0];
    if (!detailButton) throw new Error('Expected condition detail button');
    fireEvent.click(detailButton);

    expect(screen.getByLabelText('Type 2 diabetes mellitus details')).toBeTruthy();
    expect(screen.getByText('conditions.detail.onset')).toBeTruthy();
    expect(screen.getByText('2018-03-01')).toBeTruthy();
    expect(screen.getAllByText('E11')).toHaveLength(2);
  });

  it('calls fetchConditions with authenticated session context', async () => {
    mockFetch.mockResolvedValue([]);
    renderPage();
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42', expect.objectContaining({
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    })));
  });
});
