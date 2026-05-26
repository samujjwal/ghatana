/**
 * Tests for offlineStore — verifies TTL expiry and schema version validation.
 *
 * These tests exercise the real production module (no object-literal assertions).
 */

import { afterEach, beforeEach, describe, expect, it, jest } from '@jest/globals';

// Must be mocked before importing the module under test.
jest.mock('@react-native-async-storage/async-storage', () => ({
  setItem: jest.fn(() => Promise.resolve()),
  getItem: jest.fn(() => Promise.resolve(null)),
  removeItem: jest.fn(() => Promise.resolve()),
}));

import AsyncStorage from '@react-native-async-storage/async-storage';
import { clearDashboardOffline, loadDashboardOffline, saveDashboardOffline } from '../offlineStore';
import type { MobileDashboard } from '../../types';

const mockGet = AsyncStorage.getItem as jest.Mock;
const mockSet = AsyncStorage.setItem as jest.Mock;
const mockRemove = AsyncStorage.removeItem as jest.Mock;

const SAMPLE_DASHBOARD: MobileDashboard = {
  patient: { id: 'p1', name: 'Ram Bahadur', age: 35, bloodType: 'A+', district: 'Kathmandu' },
  records: [],
  consents: [],
  notifications: [],
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
    await saveDashboardOffline(SAMPLE_DASHBOARD);

    expect(mockSet).toHaveBeenCalledTimes(1);
    const [key, raw] = mockSet.mock.calls[0] as [string, string];
    expect(key).toBe('phr-mobile-dashboard');

    const envelope = JSON.parse(raw) as Record<string, unknown>;
    expect(envelope.schemaVersion).toBe(1);
    expect(envelope.ttlMs).toBeGreaterThan(0);
    expect(envelope.savedAt).toBeGreaterThan(0);
    expect(envelope.data).toMatchObject({ patient: { id: 'p1' } });
  });

  it('loadDashboardOffline returns data when cache is fresh', async () => {
    const savedAt = Date.now();
    const envelope = { schemaVersion: 1, savedAt, ttlMs: 3_600_000, data: SAMPLE_DASHBOARD };
    mockGet.mockResolvedValueOnce(JSON.stringify(envelope));

    const result = await loadDashboardOffline();

    expect(result).toMatchObject({ patient: { id: 'p1' } });
    expect(mockRemove).not.toHaveBeenCalled();
  });

  it('loadDashboardOffline returns null and clears cache when TTL expired', async () => {
    const savedAt = Date.now() - 10_000; // 10 seconds ago
    const envelope = { schemaVersion: 1, savedAt, ttlMs: 5_000, data: SAMPLE_DASHBOARD }; // 5s TTL
    mockGet.mockResolvedValueOnce(JSON.stringify(envelope));

    const result = await loadDashboardOffline();

    expect(result).toBeNull();
    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');
  });

  it('loadDashboardOffline returns null and clears cache on schema version mismatch', async () => {
    const envelope = { schemaVersion: 999, savedAt: Date.now(), ttlMs: 3_600_000, data: SAMPLE_DASHBOARD };
    mockGet.mockResolvedValueOnce(JSON.stringify(envelope));

    const result = await loadDashboardOffline();

    expect(result).toBeNull();
    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');
  });

  it('loadDashboardOffline returns null and clears on corrupt JSON', async () => {
    mockGet.mockResolvedValueOnce('{{invalid json}}');

    const result = await loadDashboardOffline();

    expect(result).toBeNull();
    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');
  });

  it('loadDashboardOffline returns null when no cache exists', async () => {
    mockGet.mockResolvedValueOnce(null);

    const result = await loadDashboardOffline();

    expect(result).toBeNull();
    expect(mockRemove).not.toHaveBeenCalled();
  });

  it('clearDashboardOffline removes the cache key', async () => {
    await clearDashboardOffline();
    expect(mockRemove).toHaveBeenCalledWith('phr-mobile-dashboard');
  });
});
