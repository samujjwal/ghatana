/**
 * Tests for TimelinePage — verifies loading, error, and event display states.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { TimelinePage } from '../TimelinePage';

vi.mock('../../api/phrApi', () => ({
  fetchTimeline: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }, isAuthenticated: true, setSession: vi.fn(), clearSession: vi.fn() }),
}));

import { fetchTimeline } from '../../api/phrApi';

const mockFetch = fetchTimeline as ReturnType<typeof vi.fn>;

const events = [
  { id: 'e1', date: '2026-01-15', type: 'appointment' as const, title: 'Cardiology follow-up', summary: 'Stable.' },
  { id: 'e2', date: '2026-02-01', type: 'lab_result' as const, title: 'CBC panel', summary: 'Normal ranges.' },
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
    await waitFor(() =>
      expect(screen.getByText(/timeline\.error/)).toBeTruthy()
    );
  });

  it('shows empty message when no events returned', async () => {
    mockFetch.mockResolvedValue([]);
    render(<TimelinePage />);
    await waitFor(() =>
      expect(screen.getByText('timeline.empty')).toBeTruthy()
    );
  });

  it('displays appointment event title', async () => {
    mockFetch.mockResolvedValue(events);
    render(<TimelinePage />);
    await waitFor(() =>
      expect(screen.getByText('Cardiology follow-up')).toBeTruthy()
    );
  });

  it('displays lab result event title', async () => {
    mockFetch.mockResolvedValue(events);
    render(<TimelinePage />);
    await waitFor(() =>
      expect(screen.getByText('CBC panel')).toBeTruthy()
    );
  });

  it('calls fetchTimeline with the session principalId', async () => {
    mockFetch.mockResolvedValue([]);
    render(<TimelinePage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42'));
  });
});
