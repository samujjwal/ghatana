import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TimelinePage } from '../TimelinePage';
import { fetchTimeline } from '../../api/patientApi';
import type { TimelineEvent } from '../../types';

vi.mock('../../api/patientApi', () => ({
  fetchTimeline: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, values?: Record<string, string | number>) => {
    if (key === 'timeline.detail.open') return `View details for ${values?.title}`;
    if (key === 'timeline.detail.label') return `${values?.title} details`;
    if (key === 'timeline.pagination.status') return `Page ${values?.page} of ${values?.pages}`;
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

const mockFetch = vi.mocked(fetchTimeline);

const events: TimelineEvent[] = [
  { id: 'e1', date: '2026-01-15', occurredAt: '2026-01-15', type: 'visit', title: 'Cardiology follow-up', summary: 'Stable.', description: 'Blood pressure reviewed.', resourceId: 'enc-1' },
  { id: 'e2', date: '2026-02-01', occurredAt: '2026-02-01', type: 'lab', title: 'CBC panel', summary: 'Normal ranges.' },
  { id: 'e3', date: '2026-02-02', occurredAt: '2026-02-02', type: 'document', title: 'Referral letter', summary: 'Uploaded.' },
  { id: 'e4', date: '2026-02-03', occurredAt: '2026-02-03', type: 'medication', title: 'Metformin refill', summary: 'Renewed.' },
  { id: 'e5', date: '2026-02-04', occurredAt: '2026-02-04', type: 'consent', title: 'Caregiver consent', summary: 'Granted.' },
  { id: 'e6', date: '2026-02-05', occurredAt: '2026-02-05', type: 'immunization', title: 'Tetanus booster', summary: 'Completed.' },
];

describe('TimelinePage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<TimelinePage />);
    expect(screen.getByText('timeline.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<TimelinePage />);
    await waitFor(() => expect(screen.getByText(/network/)).toBeTruthy());
  });

  it('shows empty message when no events returned', async () => {
    mockFetch.mockResolvedValue([]);
    render(<TimelinePage />);
    await waitFor(() => expect(screen.getByText('timeline.empty')).toBeTruthy());
  });

  it('groups and filters timeline events by type', async () => {
    mockFetch.mockResolvedValue(events);
    render(<TimelinePage />);

    await waitFor(() => expect(screen.getByRole('button', { name: /Tetanus booster/ })).toBeTruthy());
    fireEvent.change(screen.getByLabelText('timeline.filter.label'), { target: { value: 'lab' } });

    await waitFor(() => expect(screen.queryByRole('button', { name: /Tetanus booster/ })).toBeNull());
    expect(screen.getByRole('button', { name: /CBC panel/ })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'timeline.type.lab' })).toBeTruthy();
  });

  it('opens timeline event detail', async () => {
    mockFetch.mockResolvedValue(events);
    render(<TimelinePage />);

    await waitFor(() => expect(screen.getByText('Page 1 of 2')).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: 'timeline.pagination.next' }));
    fireEvent.click(await screen.findByRole('button', { name: /Cardiology follow-up/ }));

    expect(screen.getByRole('region', { name: 'Cardiology follow-up details' })).toBeTruthy();
    expect(screen.getByText('Blood pressure reviewed.')).toBeTruthy();
    expect(screen.getByText('enc-1')).toBeTruthy();
  });

  it('paginates timeline events', async () => {
    mockFetch.mockResolvedValue(events);
    render(<TimelinePage />);

    await waitFor(() => expect(screen.getByText('Page 1 of 2')).toBeTruthy());
    expect(screen.queryByRole('button', { name: /Cardiology follow-up/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'timeline.pagination.next' }));

    await waitFor(() => expect(screen.getByText('Page 2 of 2')).toBeTruthy());
    expect(screen.getByRole('button', { name: /Cardiology follow-up/ })).toBeTruthy();
  });

  it('calls fetchTimeline with the session principalId', async () => {
    mockFetch.mockResolvedValue([]);
    render(<TimelinePage />);
    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        'patient-42',
        {
          tenantId: 't1',
          principalId: 'patient-42',
          role: 'patient',
        },
        {},
      ),
    );
  });
});
