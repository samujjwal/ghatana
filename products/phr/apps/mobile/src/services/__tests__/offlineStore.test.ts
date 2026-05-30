/**
 * Tests for offlineStore — verifies TTL expiry and schema version validation.
 *
 * These tests exercise the real production module (no object-literal assertions).
 */

jest.mock('../phiEncryptedStorage', () => ({
  phiSet: jest.fn(() => Promise.resolve()),
  phiGet: jest.fn(() => Promise.resolve(null)),
  phiRemove: jest.fn(() => Promise.resolve()),
}));

import { phiGet, phiRemove, phiSet } from '../phiEncryptedStorage';
import { clearDashboardOffline, loadDashboardOffline, saveDashboardOffline, type SessionIdentity } from '../offlineStore';
import type { MobileDashboard } from '../../types';

const mockGet = phiGet as jest.Mock;
const mockSet = phiSet as jest.Mock;
const mockRemove = phiRemove as jest.Mock;

const SAMPLE_DASHBOARD: MobileDashboard = {
  patient: { id: 'p1', name: 'Ram Bahadur', age: 35, bloodType: 'A+', district: 'Kathmandu' },
  records: [],
  consents: [],
  notifications: [],
};

const SESSION_IDENTITY: SessionIdentity = {
  tenantId: 'tenant-1',
  principalId: 'p1',
  role: 'patient',
};

describe('offlineStore', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('saveDashboardOffline writes a TTL envelope with savedAt and data', async () => {
    await saveDashboardOffline(SAMPLE_DASHBOARD, undefined, SESSION_IDENTITY);

    expect(mockSet).toHaveBeenCalledTimes(1);
    const [key, raw] = mockSet.mock.calls[0] as [string, string];
    expect(key).toBe('phr-mobile-dashboard');

    const envelope = JSON.parse(raw) as Record<string, unknown>;
    expect(envelope.schemaVersion).toBe(1);
    expect(envelope.ttlMs).toBeGreaterThan(0);
    expect(envelope.savedAt).toBeGreaterThan(0);
    expect(envelope.tenantId).toBe('tenant-1');
    expect(envelope.principalId).toBe('p1');
    expect(envelope.role).toBe('patient');
    expect(envelope.data).toMatchObject({ patient: { id: 'p1' } });
  });

  it('removes restricted fields recursively before caching', async () => {
    const dashboardWithRestrictedFields = {
      ...SAMPLE_DASHBOARD,
      patient: {
        ...SAMPLE_DASHBOARD.patient,
        mentalHealth: 'restricted',
        nested: {
          hivStatus: 'restricted',
          safe: 'kept',
        },
      },
      records: [
        {
          id: 'r1',
          title: 'Clinical note',
          summary: 'Safe summary',
          fhirPreview: '{}',
          substanceUse: 'restricted',
          attachments: [{ geneticInfo: 'restricted', label: 'kept' }],
        },
      ],
    } as unknown as MobileDashboard;

    await saveDashboardOffline(dashboardWithRestrictedFields, undefined, SESSION_IDENTITY);

    const [, raw] = mockSet.mock.calls[0] as [string, string];
    expect(raw).not.toContain('mentalHealth');
    expect(raw).not.toContain('hivStatus');
    expect(raw).not.toContain('substanceUse');
    expect(raw).not.toContain('geneticInfo');
    expect(raw).toContain('safe');
    expect(raw).toContain('label');
  });

  it('loadDashboardOffline returns data when cache is fresh', async () => {
    const savedAt = Date.now();
    const envelope = { schemaVersion: 1, savedAt, ttlMs: 3_600_000, ...SESSION_IDENTITY, data: SAMPLE_DASHBOARD };
    mockGet.mockResolvedValueOnce(JSON.stringify(envelope));

    const result = await loadDashboardOffline(SESSION_IDENTITY);

    expect(result).toMatchObject({ patient: { id: 'p1' } });
    expect(mockRemove).not.toHaveBeenCalled();
  });

  it('loadDashboardOffline returns null and clears cache when TTL expired', async () => {
    const savedAt = Date.now() - 10_000; // 10 seconds ago
    const envelope = { schemaVersion: 1, savedAt, ttlMs: 5_000, ...SESSION_IDENTITY, data: SAMPLE_DASHBOARD }; // 5s TTL
    mockGet.mockResolvedValueOnce(JSON.stringify(envelope));

    const result = await loadDashboardOffline(SESSION_IDENTITY);

    expect(result).toBeNull();
    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');
  });

  it('loadDashboardOffline returns null and clears cache on schema version mismatch', async () => {
    const envelope = { schemaVersion: 999, savedAt: Date.now(), ttlMs: 3_600_000, ...SESSION_IDENTITY, data: SAMPLE_DASHBOARD };
    mockGet.mockResolvedValueOnce(JSON.stringify(envelope));

    const result = await loadDashboardOffline(SESSION_IDENTITY);

    expect(result).toBeNull();
    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');
  });

  it('loadDashboardOffline returns null and clears on corrupt JSON', async () => {
    mockGet.mockResolvedValueOnce('{{invalid json}}');

    const result = await loadDashboardOffline(SESSION_IDENTITY);

    expect(result).toBeNull();
    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');
  });

  it('loadDashboardOffline returns null when no cache exists', async () => {
    mockGet.mockResolvedValueOnce(null);

    const result = await loadDashboardOffline(SESSION_IDENTITY);

    expect(result).toBeNull();
    expect(mockRemove).not.toHaveBeenCalled();
  });

  it('clearDashboardOffline removes the cache key', async () => {\n    await clearDashboardOffline();\n    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');\n  });\n\n  it('loadDashboardOffline returns null and clears on session mismatch', async () => {\n    const differentSession: SessionIdentity = {\n      tenantId: 'tenant-2',\n      principalId: 'p2',\n      role: 'clinician',\n    };\n    const savedAt = Date.now();\n    const envelope = { schemaVersion: 1, savedAt, ttlMs: 3_600_000, ...SESSION_IDENTITY, data: SAMPLE_DASHBOARD };\n    mockGet.mockResolvedValueOnce(JSON.stringify(envelope));\n\n    const result = await loadDashboardOffline(differentSession);\n\n    expect(result).toBeNull();\n    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');\n  });
});
