/**
 * Tests for NotificationsPage — verifies loading, error, empty, and notification display.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { NotificationsPage } from '../NotificationsPage';

vi.mock('../../api/phrApi', () => ({
  fetchNotifications: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }, isAuthenticated: true, setSession: vi.fn(), clearSession: vi.fn() }),
}));

import { fetchNotifications } from '../../api/phrApi';

const mockFetch = fetchNotifications as ReturnType<typeof vi.fn>;

const notifications = [
  {
    id: 'n1',
    type: 'lab_result' as const,
    title: 'New lab result available',
    body: 'Your CBC results are ready.',
    createdAt: '2026-05-01T09:00:00Z',
    readAt: null,
  },
  {
    id: 'n2',
    type: 'appointment_reminder' as const,
    title: 'Appointment reminder',
    body: 'You have an appointment tomorrow.',
    createdAt: '2026-05-02T08:00:00Z',
    readAt: '2026-05-02T09:00:00Z',
  },
];

describe('NotificationsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<NotificationsPage />);
    expect(screen.getByText('notifications.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('network'));
    render(<NotificationsPage />);
    await waitFor(() =>
      expect(screen.getByText(/notifications\.error/)).toBeTruthy()
    );
  });

  it('shows empty message when no notifications', async () => {
    mockFetch.mockResolvedValue([]);
    render(<NotificationsPage />);
    await waitFor(() =>
      expect(screen.getByText('notifications.empty')).toBeTruthy()
    );
  });

  it('displays unread notification title', async () => {
    mockFetch.mockResolvedValue(notifications);
    render(<NotificationsPage />);
    await waitFor(() =>
      expect(screen.getByText('New lab result available')).toBeTruthy()
    );
  });

  it('displays read notification title', async () => {
    mockFetch.mockResolvedValue(notifications);
    render(<NotificationsPage />);
    await waitFor(() =>
      expect(screen.getByText('Appointment reminder')).toBeTruthy()
    );
  });

  it('shows unread badge for unread notification', async () => {
    mockFetch.mockResolvedValue(notifications);
    render(<NotificationsPage />);
    await waitFor(() =>
      expect(screen.getByText('notifications.unread')).toBeTruthy()
    );
  });

  it('calls fetchNotifications with the session principalId', async () => {
    mockFetch.mockResolvedValue([]);
    render(<NotificationsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42'));
  });
});
