/**
 * REST Sync Adapter Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import { RestSyncAdapter } from '../restSync';

import type { CanvasChange } from '../types';

describe('RestSyncAdapter', () => {
  const mockConfig = {
    endpoint: 'https://api.example.com',
    authToken: 'test-token',
    enableDiff: true,
    retry: {
      maxRetries: 3,
      backoffMultiplier: 2,
      initialDelay: 100,
    },
    endpoints: {
      pull: (id: string) => `https://api.example.com/documents/${id}`,
      push: (id: string) => `https://api.example.com/documents/${id}`,
      diff: (id: string, version: number) => `https://api.example.com/documents/${id}/diff?since=${version}`,
    },
  };

  let adapter: RestSyncAdapter;

  beforeEach(() => {
    adapter = new RestSyncAdapter(mockConfig);
    global.fetch = vi.fn();
  });

  describe('Connection', () => {
    it('should connect successfully', async () => {
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        status: 200,
      });

      await adapter.connect();
      expect(adapter.isConnected()).toBe(true);
    });

    it('should throw error on connection failure', async () => {
      (global.fetch as unknown).mockResolvedValueOnce({
        ok: false,
        status: 500,
      });

      await expect(adapter.connect()).rejects.toThrow('REST API unreachable: 500');
    });

    it('should disconnect successfully', async () => {
      (global.fetch as unknown).mockResolvedValueOnce({ ok: true });
      await adapter.connect();
      
      await adapter.disconnect();
      expect(adapter.isConnected()).toBe(false);
    });
  });

  describe('Pull Operations', () => {
    beforeEach(async () => {
      (global.fetch as unknown).mockResolvedValueOnce({ ok: true });
      await adapter.connect();
    });

    it('should pull full document on initial sync', async () => {
      const mockResponse = {
        data: {
          document: { id: 'doc-123', version: 1 },
          version: 1,
        },
      };

      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await adapter.pull('doc-123');
      
      expect(result.success).toBe(true);
      expect(result.version).toBe(1);
      expect(result.changes).toHaveLength(1);
    });

    it('should pull diff when version provided', async () => {
      const mockChanges: CanvasChange[] = [
        {
          id: 'change-1',
          documentId: 'doc-123',
          operation: 'update',
          timestamp: Date.now(),
          userId: 'user-1',
          data: {},
          version: 2,
        },
      ];

      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: {
            changes: mockChanges,
            version: 2,
          },
        }),
      });

      const result = await adapter.pull('doc-123', 1);
      
      expect(result.success).toBe(true);
      expect(result.version).toBe(2);
      expect(result.changes).toEqual(mockChanges);
    });

    it('should handle pull errors', async () => {
      (global.fetch as unknown).mockRejectedValueOnce(new Error('Network error'));

      const result = await adapter.pull('doc-123');
      
      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
      expect(result.error?.code).toBe('PULL_ERROR');
    });
  });

  describe('Push Operations', () => {
    beforeEach(async () => {
      (global.fetch as unknown).mockResolvedValueOnce({ ok: true });
      await adapter.connect();
    });

    it('should push changes successfully', async () => {
      const changes: CanvasChange[] = [
        {
          id: 'change-1',
          documentId: 'doc-123',
          operation: 'update',
          timestamp: Date.now(),
          userId: 'user-1',
          data: {},
          version: 2,
        },
      ];

      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: { version: 2 },
        }),
      });

      const result = await adapter.push('doc-123', changes);
      
      expect(result.success).toBe(true);
      expect(result.version).toBe(2);
    });

    it('should detect conflicts', async () => {
      const changes: CanvasChange[] = [
        {
          id: 'change-1',
          documentId: 'doc-123',
          operation: 'update',
          timestamp: Date.now(),
          userId: 'user-1',
          data: {},
          version: 2,
        },
      ];

      const conflicts = [
        {
          changeId: 'change-1',
          localChange: changes[0],
          serverChange: { ...changes[0], userId: 'user-2' },
        },
      ];

      (global.fetch as unknown).mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: { version: 2, conflicts },
        }),
      });

      const result = await adapter.push('doc-123', changes);
      
      expect(result.success).toBe(true);
      expect(result.conflicts).toHaveLength(1);
    });

    it('should handle push errors', async () => {
      (global.fetch as unknown).mockRejectedValueOnce(new Error('Network error'));

      const result = await adapter.push('doc-123', []);
      
      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
    });
  });

  describe('Retry Logic', () => {
    beforeEach(async () => {
      (global.fetch as unknown).mockResolvedValueOnce({ ok: true });
      await adapter.connect();
    });

    it('should retry on 5xx errors', async () => {
      let attempts = 0;
      
      (global.fetch as unknown).mockImplementation(async () => {
        attempts++;
        if (attempts < 3) {
          return { ok: false, status: 500, statusText: 'Server Error' };
        }
        return {
          ok: true,
          json: async () => ({ data: { version: 1 } }),
        };
      });

      const result = await adapter.pull('doc-123');
      
      expect(attempts).toBe(3);
      expect(result.success).toBe(true);
    });
  });
});
