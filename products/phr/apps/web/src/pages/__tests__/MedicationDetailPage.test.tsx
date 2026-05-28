/**
 * Tests for MedicationDetailPage.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MedicationDetailPage } from '../MedicationDetailPage';

vi.mock('../../api/clinicalApi', () => ({
  fetchMedicationDetail: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('react-router-dom', () => ({
  useParams: () => ({ medicationId: 'rx-1' }),
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: {
      principalId: 'patient-42',
      tenantId: 't1',
      role: 'patient' as const,
      name: 'Test Patient',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    },
  }),
}));

import { fetchMedicationDetail } from '../../api/clinicalApi';

const mockFetch = vi.mocked(fetchMedicationDetail);

const medicationDetail = {
  id: 'rx-1',
  medication: 'Metformin',
  dosage: '500mg',
  schedule: 'Twice daily',
  adherence: 100,
  status: 'active' as const,
  interactions: ['Avoid duplicate metformin therapy'],
  warnings: ['No refills remain'],
  history: [{ date: '2026-05-28T01:00:00Z', action: 'Prescribed' }],
};

describe('MedicationDetailPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('loads medication detail with authenticated context', async () => {
    mockFetch.mockResolvedValue(medicationDetail);
    render(<MedicationDetailPage />);

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42', 'rx-1', {
      tenantId: 't1',
      principalId: 'patient-42',
      role: 'patient',
    }));
  });

  it('renders interactions, warnings, and history', async () => {
    mockFetch.mockResolvedValue(medicationDetail);
    render(<MedicationDetailPage />);

    await waitFor(() => expect(screen.getByText('Metformin')).toBeTruthy());
    expect(screen.getByText('Avoid duplicate metformin therapy')).toBeTruthy();
    expect(screen.getByText('No refills remain')).toBeTruthy();
    expect(screen.getByText('Prescribed')).toBeTruthy();
  });

  it('shows an error state when detail fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<MedicationDetailPage />);

    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });
});
