/**
 * Tests for RecordsPage — verifies loading, error, and record list display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { RecordsPage } from '../RecordsPage';

vi.mock('../../api/phrApi', () => ({
  fetchDashboardData: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrDateTime: (d: string) => d,
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) =>
    React.createElement('a', { href: to }, children),
}));

import { fetchDashboardData } from '../../api/phrApi';

const mockFetch = fetchDashboardData as ReturnType<typeof vi.fn>;

const records = [
  {
    id: 'rec-1',
    title: 'CBC Lab Panel',
    resourceType: 'Observation',
    category: 'lab',
    updatedAt: '2025-01-10T10:00:00Z',
    fhirJson: '{}',
  },
  {
    id: 'rec-2',
    title: 'Chest X-Ray',
    resourceType: 'DiagnosticReport',
    category: 'imaging',
    updatedAt: '2025-02-01T08:30:00Z',
    fhirJson: '{}',
  },
];

describe('RecordsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<RecordsPage />);
    expect(screen.getByText('records.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<RecordsPage />);
    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });

  it('displays record titles after successful fetch', async () => {
    mockFetch.mockResolvedValue({ records, patient: {}, consents: [], appointments: [], labs: [], medications: [] });
    render(<RecordsPage />);
    await waitFor(() => expect(screen.getByText('CBC Lab Panel')).toBeTruthy());
    expect(screen.getByText('Chest X-Ray')).toBeTruthy();
  });

  it('renders records title key', async () => {
    mockFetch.mockResolvedValue({ records, patient: {}, consents: [], appointments: [], labs: [], medications: [] });
    render(<RecordsPage />);
    await waitFor(() => expect(screen.getByText('records.title')).toBeTruthy());
  });

  it('calls fetchDashboardData on mount', async () => {
    mockFetch.mockResolvedValue({ records: [], patient: {}, consents: [], appointments: [], labs: [], medications: [] });
    render(<RecordsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));
  });
});
