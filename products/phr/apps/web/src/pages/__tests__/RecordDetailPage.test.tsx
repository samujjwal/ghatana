/**
 * Tests for RecordDetailPage — verifies loading, error, missing, and detail display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { RecordDetailPage } from '../RecordDetailPage';

vi.mock('../../api/phrApi', () => ({
  fetchDashboardData: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('react-router-dom', () => ({
  useParams: () => ({ recordId: 'rec-1' }),
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
    fhirJson: '{"resourceType":"Observation"}',
  },
];

describe('RecordDetailPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<RecordDetailPage />);
    expect(screen.getByText('recordDetail.loading')).toBeTruthy();
  });

  it('shows error when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<RecordDetailPage />);
    await waitFor(() => expect(screen.getByText(/dashboard\.errorPrefix/)).toBeTruthy());
  });

  it('shows unavailable state when record is not found', async () => {
    mockFetch.mockResolvedValue({ records: [], patient: {}, consents: [], appointments: [], labs: [], medications: [] });
    render(<RecordDetailPage />);
    await waitFor(() => expect(screen.getByText('recordDetail.unavailable.title')).toBeTruthy());
  });

  it('displays record title when found', async () => {
    mockFetch.mockResolvedValue({ records, patient: {}, consents: [], appointments: [], labs: [], medications: [] });
    render(<RecordDetailPage />);
    await waitFor(() => expect(screen.getByText('CBC Lab Panel')).toBeTruthy());
  });

  it('renders FHIR JSON in a code block', async () => {
    mockFetch.mockResolvedValue({ records, patient: {}, consents: [], appointments: [], labs: [], medications: [] });
    render(<RecordDetailPage />);
    await waitFor(() => expect(screen.getByText('{"resourceType":"Observation"}')).toBeTruthy());
  });
});
