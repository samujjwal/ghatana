/**
 * Tests for RecordDetailPage — verifies loading, error, missing, and detail display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { RecordDetailPage } from '../RecordDetailPage';

vi.mock('../../api/recordsApi', () => ({
  fetchRecordDetail: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('react-router-dom', () => ({
  useParams: () => ({ recordId: 'rec-1' }),
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: {
      tenantId: 'tenant-test',
      principalId: 'patient-test',
      role: 'patient',
      name: 'Patient Test',
      expiresAt: '2026-05-28T02:00:00Z',
      persona: 'patient',
      tier: 'core',
      facilityId: 'facility-test',
    },
  }),
}));

import { fetchRecordDetail } from '../../api/recordsApi';

const mockFetch = fetchRecordDetail as ReturnType<typeof vi.fn>;

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
    await waitFor(() => expect(screen.getByText('network')).toBeTruthy());
  });

  it('shows unavailable state when record is not found', async () => {
    mockFetch.mockResolvedValue(null);
    render(<RecordDetailPage />);
    await waitFor(() => expect(screen.getByText('recordDetail.unavailable.title')).toBeTruthy());
  });

  it('displays record title when found', async () => {
    const record = records[0];
    if (!record) throw new Error('Missing record fixture');
    mockFetch.mockResolvedValue({
      record,
      fhirJson: record.fhirJson,
      accessAudit: { accessedAt: '2025-01-10T10:00:00Z', accessedBy: 'patient-test' },
    });
    render(<RecordDetailPage />);
    await waitFor(() => expect(screen.getByText('CBC Lab Panel')).toBeTruthy());
    expect(mockFetch).toHaveBeenCalledWith('patient-test', 'rec-1', expect.objectContaining({
      tenantId: 'tenant-test',
      principalId: 'patient-test',
      role: 'patient',
      persona: 'patient',
      tier: 'core',
      facilityId: 'facility-test',
    }));
  });

  it('renders FHIR JSON in a code block', async () => {
    const record = records[0];
    if (!record) throw new Error('Missing record fixture');
    mockFetch.mockResolvedValue({
      record,
      fhirJson: record.fhirJson,
      accessAudit: { accessedAt: '2025-01-10T10:00:00Z', accessedBy: 'patient-test' },
    });
    render(<RecordDetailPage />);
    await waitFor(() => expect(screen.getByText(/"resourceType": "Observation"/)).toBeTruthy());
  });
});
