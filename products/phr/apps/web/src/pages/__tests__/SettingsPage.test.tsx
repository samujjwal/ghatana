/**
 * Tests for SettingsPage — verifies logout, sync, and profile display.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { SettingsPage } from '../SettingsPage';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock('../../api/phrApi', () => ({
  exportPatientBundle: vi.fn(),
  logoutSession: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

const mockClearSession = vi.fn();

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: {
      principalId: 'patient-42',
      tenantId: 't1',
      role: 'patient' as const,
      name: 'Test Patient',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    },
    isAuthenticated: true,
    setSession: vi.fn(),
    clearSession: mockClearSession,
  }),
}));

import { exportPatientBundle, logoutSession } from '../../api/phrApi';

const mockExport = exportPatientBundle as ReturnType<typeof vi.fn>;
const mockLogout = logoutSession as ReturnType<typeof vi.fn>;

describe('SettingsPage', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    mockClearSession.mockReset();
    mockExport.mockReset();
    mockLogout.mockReset();
  });

  it('renders settings profile title', () => {
    render(<SettingsPage />);
    expect(screen.getByText('settings.profile.title')).toBeTruthy();
  });

  it('renders logout button', () => {
    render(<SettingsPage />);
    expect(screen.getByRole('button', { name: 'settings.logout.button' })).toBeTruthy();
  });

  it('calls logoutSession and clears session on logout', async () => {
    mockLogout.mockResolvedValue(undefined);
    render(<SettingsPage />);
    fireEvent.click(screen.getByRole('button', { name: 'settings.logout.button' }));
    await waitFor(() => {
      expect(mockLogout).toHaveBeenCalledWith({ tenantId: 't1', principalId: 'patient-42' });
      expect(mockClearSession).toHaveBeenCalled();
      expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
    });
  });

  it('still clears session even if logoutSession rejects', async () => {
    mockLogout.mockRejectedValue(new Error('network'));
    render(<SettingsPage />);
    fireEvent.click(screen.getByRole('button', { name: 'settings.logout.button' }));
    await waitFor(() => {
      expect(mockClearSession).toHaveBeenCalled();
      expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
    });
  });

  it('calls exportPatientBundle on sync click', async () => {
    mockExport.mockResolvedValue('OK');
    render(<SettingsPage />);
    fireEvent.click(screen.getByText('settings.hie.prepare'));
    await waitFor(() => expect(mockExport).toHaveBeenCalled());
  });
});
