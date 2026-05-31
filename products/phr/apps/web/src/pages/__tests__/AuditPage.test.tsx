/**
 * Tests for AuditPage - verifies real API integration (no mock data).
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AuditPage } from '../../pages/AuditPage';

const sessionState = vi.hoisted(() => ({
  current: {
    tenantId: 'tenant-1',
    principalId: 'user-1',
    role: 'admin' as 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv',
    name: 'Admin User',
    expiresAt: '2026-12-31T23:59:59Z',
  },
}));

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
    session: sessionState.current,
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
  details: {
    patientName: 'Sensitive Name',
  },
};

describe('AuditPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    sessionState.current = {
      tenantId: 'tenant-1',
      principalId: 'user-1',
      role: 'admin',
      name: 'Admin User',
      expiresAt: '2026-12-31T23:59:59Z',
    };
  });

  it('calls fetchAuditEvents and renders events on load', async () => {
    mockFetch.mockResolvedValue({ events: [sampleEvent], total: 1, page: 1, pageSize: 50 });

    render(<AuditPage />);

    await waitFor(() => expect(screen.queryByText(/audit.loading/)).toBeNull());
    expect(mockFetch).toHaveBeenCalledWith(expect.objectContaining({
      filter: 'all',
      tenantId: 'tenant-1',
      principalId: 'user-1',
      role: 'admin',
    }));
    expect(screen.getByText('user-1')).toBeTruthy();
    expect(screen.getByText('audit.details.available')).toBeTruthy();
    expect(screen.queryByText('Sensitive Name')).toBeNull();
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

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.objectContaining({
      filter: 'access',
      tenantId: 'tenant-1',
      principalId: 'user-1',
      role: 'admin',
    })));
  });

  it('scopes patient audit queries to the signed-in patient', async () => {
    sessionState.current = {
      tenantId: 'tenant-1',
      principalId: 'patient-1',
      role: 'patient',
      name: 'Patient User',
      expiresAt: '2026-12-31T23:59:59Z',
    };
    mockFetch.mockResolvedValue({ events: [sampleEvent], total: 1, page: 1, pageSize: 50 });

    render(<AuditPage />);

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.objectContaining({
      filter: 'all',
      patientId: 'patient-1',
      role: 'patient',
    })));
    expect(screen.getByText('audit.policy.patient.title')).toBeTruthy();
  });

  it('requires clinician patient scope before fetching audit events', async () => {
    sessionState.current = {
      tenantId: 'tenant-1',
      principalId: 'clinician-1',
      role: 'clinician',
      name: 'Clinician User',
      expiresAt: '2026-12-31T23:59:59Z',
    };
    mockFetch.mockResolvedValue({ events: [], total: 0, page: 1, pageSize: 50 });

    render(<AuditPage />);

    expect(screen.getByText('audit.scope.required')).toBeTruthy();
    expect(mockFetch).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('audit.scope.patientId.label'), { target: { value: ' patient-42 ' } });
    fireEvent.click(screen.getByRole('button', { name: /audit.scope.apply/i }));

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.objectContaining({
      filter: 'access',
      patientId: 'patient-42',
      principalId: 'clinician-1',
      role: 'clinician',
    })));
    expect(screen.queryByRole('button', { name: /audit.filter.all/i })).toBeNull();
  });
});
