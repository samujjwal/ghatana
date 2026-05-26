/**
 * Tests for MedicationsPage — verifies loading, error, and medication list display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MedicationsPage } from '../MedicationsPage';

vi.mock('../../api/phrApi', () => ({
  fetchDashboardData: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrPercent: (n: number) => String(n),
}));

import { fetchDashboardData } from '../../api/phrApi';

const mockFetch = fetchDashboardData as ReturnType<typeof vi.fn>;

const medications = [
  { id: 'med-1', medication: 'Metformin', dosage: '500mg', schedule: 'Twice daily', adherence: 0.95 },
  { id: 'med-2', medication: 'Amlodipine', dosage: '5mg', schedule: 'Once daily', adherence: 0.8 },
];

describe('MedicationsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<MedicationsPage />);
    expect(screen.getByText('medications.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<MedicationsPage />);
    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });

  it('displays medication names after successful fetch', async () => {
    mockFetch.mockResolvedValue({ medications, patient: {}, consents: [], appointments: [], labs: [], records: [] });
    render(<MedicationsPage />);
    await waitFor(() => expect(screen.getByText(/Metformin/)).toBeTruthy());
    expect(screen.getByText(/Amlodipine/)).toBeTruthy();
  });

  it('renders the medications title key', async () => {
    mockFetch.mockResolvedValue({ medications, patient: {}, consents: [], appointments: [], labs: [], records: [] });
    render(<MedicationsPage />);
    await waitFor(() => expect(screen.getByText('medications.title')).toBeTruthy());
  });

  it('calls fetchDashboardData on mount', async () => {
    mockFetch.mockResolvedValue({ medications: [], patient: {}, consents: [], appointments: [], labs: [], records: [] });
    render(<MedicationsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));
  });
});
