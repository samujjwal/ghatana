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

jest.mock('../mobilePrivacyPlugin', () => ({
  clearMobilePrivacyState: jest.fn(() => Promise.resolve()),
}));

import { clearDashboardOffline } from '../offlineStore';
import { phiClearAll } from '../phiEncryptedStorage';
import { clearMobileSession } from '../mobileSessionStore';
import { clearMobilePrivacyState } from '../mobilePrivacyPlugin';
import {
  fetchMobileDashboard,
  loginMobile,
  logoutMobile,
  requestMobileEmergencyAccess,
  revokeConsentGrant,
  syncOfflineDashboard,
} from '../phrMobileApi';
import type { MobileSession } from '../../types';

const mockFetch = jest.fn();
global.fetch = mockFetch as typeof fetch;
const mockClearMobilePrivacyState = clearMobilePrivacyState as jest.MockedFunction<typeof clearMobilePrivacyState>;

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
  persona: 'patient',
  tier: 'core',
  facilityId: 'facility-1',
};

const DASHBOARD_RESPONSE = {
  patient: {
    id: 'patient-1',
    name: 'Patient One',
    age: 30,
    bloodType: 'O+',
    district: 'Kathmandu',
  },
  records: [],
  consents: [],
  notifications: [],
};

const REQUIRED_CONTEXT_HEADERS = {
  'X-Tenant-Id': 'tenant-1',
  'X-Principal-Id': 'patient-1',
  'X-Role': 'patient',
  'X-Persona': 'patient',
  'X-Tier': 'core',
  'X-Facility-Id': 'facility-1',
  'X-Correlation-ID': 'correlation-1',
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
        persona: 'fchv',
        tier: 'community',
        facilityId: 'facility-1',
      }),
    });

    await expect(loginMobile('fchv-1', 'secret')).resolves.toMatchObject({
      role: 'fchv',
      persona: 'fchv',
      tier: 'community',
      facilityId: 'facility-1',
    });
  });

  it('rejects login responses without persona and tier context', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        tenantId: 'tenant-1',
        principalId: 'patient-1',
        role: 'patient',
        name: 'Patient One',
        expiresAt: '2099-01-01T00:00:00.000Z',
      }),
    });

    await expect(loginMobile('patient-1', 'secret')).rejects.toThrow('Session response missing persona.');
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

  it('clears all mobile privacy caches when consent is revoked', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
    });

    await revokeConsentGrant('grant-1', 'patient-1', SESSION);

    expect(mockFetch).toHaveBeenCalledWith(
      'https://phr.example.test/consents/grants/grant-1/revoke?patientId=patient-1',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining(REQUIRED_CONTEXT_HEADERS),
      }),
    );
    expect(mockClearMobilePrivacyState).toHaveBeenCalledWith('consent-revoked');
    expect(phiClearAll).not.toHaveBeenCalled();
    expect(clearDashboardOffline).not.toHaveBeenCalled();
  });

  it('clears the secure session through the mobile privacy plugin on logout', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
    });

    await logoutMobile(SESSION);

    expect(mockFetch).toHaveBeenCalledWith(
      'https://phr.example.test/auth/logout',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining(REQUIRED_CONTEXT_HEADERS),
      }),
    );
    expect(phiClearAll).not.toHaveBeenCalled();
    expect(clearDashboardOffline).not.toHaveBeenCalled();
    expect(clearMobileSession).toHaveBeenCalledTimes(1);
  });

  it('posts emergency access requests to the backend break-glass route with policy fields', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        emergencyData: {
          patientName: 'Patient One',
          bloodType: 'O+',
          allergies: ['Penicillin'],
          medications: ['Metformin'],
          emergencyContact: 'Family',
        },
      }),
    });

    await expect(
      requestMobileEmergencyAccess('patient-1', 'Patient unconscious after road incident', SESSION),
    ).resolves.toMatchObject({
      patientName: 'Patient One',
      allergies: ['Penicillin'],
    });

    expect(mockFetch).toHaveBeenCalledWith(
      'https://phr.example.test/emergency/access',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining(REQUIRED_CONTEXT_HEADERS),
        body: JSON.stringify({
          patientId: 'patient-1',
          accessorId: 'patient-1',
          accessorRole: 'patient',
          justification: 'Patient unconscious after road incident',
          resourcesAccessed: ['emergency-summary'],
        }),
      }),
    );
  });

  it('rejects emergency access requests without detailed justification before calling the API', async () => {
    await expect(
      requestMobileEmergencyAccess('patient-1', 'too short', SESSION),
    ).rejects.toThrow('Emergency access requires a patient and detailed justification.');

    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('sends complete authenticated context headers for dashboard requests', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => DASHBOARD_RESPONSE,
    });

    await expect(fetchMobileDashboard(SESSION)).resolves.toMatchObject({
      patient: { id: 'patient-1' },
    });

    expect(mockFetch).toHaveBeenCalledWith(
      'https://phr.example.test/mobile/dashboard',
      expect.objectContaining({
        headers: expect.objectContaining({
          ...REQUIRED_CONTEXT_HEADERS,
        }),
      }),
    );
  });

  it('sends complete authenticated context headers for offline sync requests', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => DASHBOARD_RESPONSE,
    });

    await expect(syncOfflineDashboard(SESSION)).resolves.toBe('Offline cache refreshed');

    expect(mockFetch).toHaveBeenCalledWith(
      'https://phr.example.test/mobile/dashboard',
      expect.objectContaining({
        headers: expect.objectContaining(REQUIRED_CONTEXT_HEADERS),
      }),
    );
  });

  it('rejects PHI requests with incomplete session context before calling the API', async () => {
    const incompleteSession: MobileSession = {
      ...SESSION,
      persona: undefined,
    };

    await expect(fetchMobileDashboard(incompleteSession)).rejects.toThrow('Session response missing persona.');

    expect(mockFetch).not.toHaveBeenCalled();
  });
});
