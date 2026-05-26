/**
 * Tests for LabsPage — verifies loading, error, and lab result display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { LabsPage } from '../LabsPage';

vi.mock('../../api/phrApi', () => ({
  fetchDashboardData: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrDate: (d: string) => d,
}));

import { fetchDashboardData } from '../../api/phrApi';

const mockFetch = fetchDashboardData as ReturnType<typeof vi.fn>;

const labs = [
  { id: 'lab-1', name: 'Hemoglobin A1c', collectedAt: '2025-01-05', status: 'normal' as const, value: '6.1%' },
  { id: 'lab-2', name: 'Blood Glucose', collectedAt: '2025-01-05', status: 'attention' as const, value: '9.3 mmol/L' },
];

describe('LabsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<LabsPage />);
    expect(screen.getByText('labs.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<LabsPage />);
    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });

  it('displays lab names after successful fetch', async () => {
    mockFetch.mockResolvedValue({ labs, patient: {}, consents: [], appointments: [], medications: [], records: [] });
    render(<LabsPage />);
    await waitFor(() => expect(screen.getByText('Hemoglobin A1c')).toBeTruthy());
    expect(screen.getByText('Blood Glucose')).toBeTruthy();
  });

  it('renders lab values', async () => {
    mockFetch.mockResolvedValue({ labs, patient: {}, consents: [], appointments: [], medications: [], records: [] });
    render(<LabsPage />);
    await waitFor(() => expect(screen.getByText('6.1%')).toBeTruthy());
  });

  it('renders labs title key', async () => {
    mockFetch.mockResolvedValue({ labs, patient: {}, consents: [], appointments: [], medications: [], records: [] });
    render(<LabsPage />);
    await waitFor(() => expect(screen.getByText('labs.title')).toBeTruthy());
  });
});
