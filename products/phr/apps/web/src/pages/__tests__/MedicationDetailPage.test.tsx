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
      persona: 'patient',
      tier: 'core',
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
  status: 'active' as const,
  refillsRemaining: 0,
  prescribedAt: '2026-05-28T01:00:00Z',
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
      persona: 'patient',
      tier: 'core',
      facilityId: undefined,
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

  it('does not render medication safety or history sections when backend omits them', async () => {
    mockFetch.mockResolvedValue({
      id: 'rx-1',
      medication: 'Metformin',
      dosage: '500mg',
      status: 'active' as const,
    });
    render(<MedicationDetailPage />);

    await waitFor(() => expect(screen.getByText('Metformin')).toBeTruthy());
    expect(screen.queryByText('medicationDetail.safety.title')).toBeNull();
    expect(screen.queryByText('medicationDetail.history.title')).toBeNull();
  });

  it('shows an error state when detail fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<MedicationDetailPage />);

    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });
});
