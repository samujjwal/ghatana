/**
 * Module Cache Service Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ModuleCacheService } from '../module-cache';
import Redis from 'ioredis';

describe('ModuleCacheService', () => {
  let mockRedis: Redis;
  let service: ModuleCacheService;

  beforeEach(() => {
    mockRedis = {
      get: vi.fn(),
      set: vi.fn(),
      del: vi.fn(),
      keys: vi.fn(),
    } as unknown as Redis;

    service = new ModuleCacheService(mockRedis, 60);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('getModule', () => {
    it('should return cached module data', async () => {
      const cachedData = { id: 'mod_1', title: 'Test Module' };
      (mockRedis.get as any).mockResolvedValue(JSON.stringify({
        data: cachedData,
        timestamp: Date.now(),
        version: 1,
      }));

      const result = await service.getModule('tenant_1', 'mod_1');
      expect(result).toEqual(cachedData);
      expect(mockRedis.get).toHaveBeenCalledWith('module:tenant_1:mod_1');
    });

    it('should return null when cache miss', async () => {
      (mockRedis.get as any).mockResolvedValue(null);

      const result = await service.getModule('tenant_1', 'mod_1');
      expect(result).toBeNull();
    });

    it('should return null on Redis error', async () => {
      (mockRedis.get as any).mockRejectedValue(new Error('Redis error'));

      const result = await service.getModule('tenant_1', 'mod_1');
      expect(result).toBeNull();
    });
  });

  describe('setModule', () => {
    it('should cache module data with TTL', async () => {
      const data = { id: 'mod_1', title: 'Test Module' };
      (mockRedis.set as any).mockResolvedValue('OK');

      await service.setModule('tenant_1', 'mod_1', data, 1, 120);

      expect(mockRedis.set).toHaveBeenCalledWith(
        'module:tenant_1:mod_1',
        expect.stringContaining('"id":"mod_1"'),
        'EX',
        120
      );
    });
  });

  describe('invalidateModule', () => {
    it('should delete module from cache', async () => {
      (mockRedis.del as any).mockResolvedValue(1);

      await service.invalidateModule('tenant_1', 'mod_1');

      expect(mockRedis.del).toHaveBeenCalledWith('module:tenant_1:mod_1');
    });
  });

  describe('invalidateModuleLists', () => {
    it('should delete all module list caches for tenant', async () => {
      const keys = ['module:list:tenant_1:filter1', 'module:list:tenant_1:filter2'];
      (mockRedis.keys as any).mockResolvedValue(keys);
      (mockRedis.del as any).mockResolvedValue(1);

      await service.invalidateModuleLists('tenant_1');

      expect(mockRedis.keys).toHaveBeenCalledWith('module:list:tenant_1:*');
      expect(mockRedis.del).toHaveBeenCalledTimes(2);
    });
  });
});
