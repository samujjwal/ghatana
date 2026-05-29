/**
 * Tests for AuditPage - verifies real API integration (no mock data).
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AuditPage } from '../../pages/AuditPage';

vi.mock('../../api/auditApi', () => ({
  fetchAuditEvents: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
  formatPhrDate: (d: string) => d,
  formatPhrDateTime: (d: string) => d,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: {
      tenantId: 'tenant-1',
      principalId: 'user-1',
      role: 'admin',
      name: 'Admin User',
      expiresAt: '2026-12-31T23:59:59Z',
    },
    setSession: vi.fn(),
    clearSession: vi.fn(),
    isAuthenticated: true,
    sessionValidating: false,
  }),
}));

import { fetchAuditEvents } from '../../api/auditApi';

const mockFetch = fetchAuditEvents as ReturnType<typeof vi.fn>;

const sampleEvent = {
  id: 'evt-1',
  tenantId: 'tenant-1',
  eventType: 'ACCESS',
  principal: 'user-1',
  resourceType: 'Patient',
  resourceId: 'pat-1',
  timestamp: '2026-01-01T00:00:00Z',
  success: true,
  details: {},
};

describe('AuditPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('calls fetchAuditEvents and renders events on load', async () => {
    mockFetch.mockResolvedValue({ events: [sampleEvent], total: 1, page: 1, pageSize: 50 });

    render(<AuditPage />);

    await waitFor(() => expect(screen.queryByText(/audit.loading/)).toBeNull());
    expect(mockFetch).toHaveBeenCalledWith({
      filter: 'all',
      tenantId: 'tenant-1',
      principalId: 'user-1',
      role: 'admin',
    });
    expect(screen.getByText('user-1')).toBeTruthy();
  });

  it('shows error state when API call fails', async () => {
    mockFetch.mockRejectedValue(new Error('Network failure'));

    render(<AuditPage />);

    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByRole('alert').textContent).toContain('Network failure');
  });

  it('re-fetches with filter when filter button is clicked', async () => {
    mockFetch.mockResolvedValue({ events: [], total: 0, page: 1, pageSize: 50 });

    render(<AuditPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));

    const accessButton = screen.getByRole('button', { name: /audit.filter.access/i });
    fireEvent.click(accessButton);

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith({
      filter: 'access',
      tenantId: 'tenant-1',
      principalId: 'user-1',
      role: 'admin',
    }));
  });
});
