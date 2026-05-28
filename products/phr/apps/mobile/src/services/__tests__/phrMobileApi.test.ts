/**
 * PHR mobile API tests for role validation and privacy cleanup behavior.
 */

process.env.EXPO_PUBLIC_PHR_API_URL = 'https://phr.example.test';

jest.mock('../phiEncryptedStorage', () => ({
  phiClearAll: jest.fn(() => Promise.resolve()),
}));

jest.mock('../offlineStore', () => ({
  clearDashboardOffline: jest.fn(() => Promise.resolve()),
  loadDashboardOffline: jest.fn(() => Promise.resolve(null)),
  saveDashboardOffline: jest.fn(() => Promise.resolve()),
}));

jest.mock('../mobileSessionStore', () => ({
  clearMobileSession: jest.fn(() => Promise.resolve()),
}));

import { clearDashboardOffline } from '../offlineStore';
import { phiClearAll } from '../phiEncryptedStorage';
import { clearMobileSession } from '../mobileSessionStore';
import { loginMobile, logoutMobile, revokeConsentGrant } from '../phrMobileApi';
import type { MobileSession } from '../../types';

const mockFetch = jest.fn();
global.fetch = mockFetch as typeof fetch;

Object.defineProperty(globalThis, 'crypto', {
  value: {
    randomUUID: jest.fn(() => 'correlation-1'),
  },
  configurable: true,
});

const SESSION: MobileSession = {
  tenantId: 'tenant-1',
  principalId: 'patient-1',
  role: 'patient',
  name: 'Patient One',
  expiresAt: '2099-01-01T00:00:00.000Z',
};

describe('phrMobileApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('accepts fchv sessions from the shared PHR role contract', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        tenantId: 'tenant-1',
        principalId: 'fchv-1',
        role: 'fchv',
        name: 'FCHV One',
        expiresAt: '2099-01-01T00:00:00.000Z',
      }),
    });

    await expect(loginMobile('fchv-1', 'secret')).resolves.toMatchObject({ role: 'fchv' });
  });

  it('rejects roles outside the shared PHR role contract', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        tenantId: 'tenant-1',
        principalId: 'bad-1',
        role: 'superuser',
        name: 'Bad Role',
        expiresAt: '2099-01-01T00:00:00.000Z',
      }),
    });

    await expect(loginMobile('bad-1', 'secret')).rejects.toThrow('Login response has an invalid role.');
  });

  it('clears encrypted PHI and dashboard cache when consent is revoked', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
    });

    await revokeConsentGrant('grant-1', 'patient-1', SESSION);

    expect(mockFetch).toHaveBeenCalledWith(
      'https://phr.example.test/consents/grants/grant-1/revoke?patientId=patient-1',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'X-Tenant-Id': 'tenant-1',
          'X-Principal-Id': 'patient-1',
          'X-Role': 'patient',
        }),
      }),
    );
    expect(phiClearAll).toHaveBeenCalledTimes(1);
    expect(clearDashboardOffline).toHaveBeenCalledTimes(1);
  });

  it('clears encrypted PHI, dashboard cache, and secure session on logout', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
    });

    await logoutMobile(SESSION);

    expect(mockFetch).toHaveBeenCalledWith(
      'https://phr.example.test/auth/logout',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'X-Tenant-Id': 'tenant-1',
          'X-Principal-Id': 'patient-1',
          'X-Role': 'patient',
        }),
      }),
    );
    expect(phiClearAll).toHaveBeenCalledTimes(1);
    expect(clearDashboardOffline).toHaveBeenCalledTimes(1);
    expect(clearMobileSession).toHaveBeenCalledTimes(1);
  });
});
