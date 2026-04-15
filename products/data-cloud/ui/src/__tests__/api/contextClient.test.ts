import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

import {
  getContext,
  putContextEntries,
  deleteContextKey,
  getContextSnapshot,
  type ContextResponse,
  type UpsertContextResponse,
  type ContextSnapshot,
} from '../../lib/api/context';

describe('context layer API client (P3.1.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ─── getContext ────────────────────────────────────────────────────────────

  it('calls GET /context and returns the response', async () => {
    const mockResponse: ContextResponse = {
      tenantId: 'tenant-a',
      entries: { locale: 'en-US', 'feature.dark-mode': true },
      count: 2,
      version: 3,
      requestId: 'req-001',
    };
    mockApiClient.get.mockResolvedValueOnce(mockResponse);

    const result = await getContext();

    expect(mockApiClient.get).toHaveBeenCalledWith('/context');
    expect(result).toEqual(mockResponse);
  });

  // ─── putContextEntries ─────────────────────────────────────────────────────

  it('calls PUT /context with an entries wrapper', async () => {
    const mockResponse: UpsertContextResponse = {
      tenantId: 'tenant-a',
      upserted: 2,
      version: 4,
      updatedAt: '2026-04-15T10:00:00Z',
      requestId: 'req-002',
    };
    mockApiClient.put.mockResolvedValueOnce(mockResponse);

    const result = await putContextEntries({ theme: 'dark', locale: 'fr-FR' });

    expect(mockApiClient.put).toHaveBeenCalledWith('/context', {
      entries: { theme: 'dark', locale: 'fr-FR' },
    });
    expect(result.upserted).toBe(2);
    expect(result.version).toBe(4);
  });

  // ─── deleteContextKey ──────────────────────────────────────────────────────

  it('calls DELETE /context/keys/:key with encoded key', async () => {
    mockApiClient.delete.mockResolvedValueOnce(undefined);

    await deleteContextKey('feature.dark-mode');

    expect(mockApiClient.delete).toHaveBeenCalledWith('/context/keys/feature.dark-mode');
  });

  it('encodes special characters in the key', async () => {
    mockApiClient.delete.mockResolvedValueOnce(undefined);

    await deleteContextKey('key with spaces');

    expect(mockApiClient.delete).toHaveBeenCalledWith('/context/keys/key%20with%20spaces');
  });

  // ─── getContextSnapshot ────────────────────────────────────────────────────

  it('calls GET /context/snapshot and returns versioned snapshot', async () => {
    const mockSnapshot: ContextSnapshot = {
      tenantId: 'tenant-a',
      version: 5,
      count: 3,
      createdAt: '2026-04-12T08:00:00Z',
      snapshotAt: '2026-04-15T10:05:00Z',
      entries: { locale: 'de-DE', theme: 'light', 'beta.feature': false },
      requestId: 'req-003',
    };
    mockApiClient.get.mockResolvedValueOnce(mockSnapshot);

    const result = await getContextSnapshot();

    expect(mockApiClient.get).toHaveBeenCalledWith('/context/snapshot');
    expect(result.version).toBe(5);
    expect(result.count).toBe(3);
    expect(result.entries).toMatchObject({ locale: 'de-DE' });
  });
});
