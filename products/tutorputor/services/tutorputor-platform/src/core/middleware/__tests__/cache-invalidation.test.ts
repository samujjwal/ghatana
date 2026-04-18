/**
 * Cache Invalidation Middleware Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createCacheInvalidationMiddleware } from '../cache-invalidation';
import Redis from 'ioredis';

describe('Cache Invalidation Middleware', () => {
  let mockRedis: Redis;
  let middleware: any;

  beforeEach(() => {
    mockRedis = {
      del: vi.fn(),
      keys: vi.fn(),
    } as unknown as Redis;

    middleware = createCacheInvalidationMiddleware(mockRedis);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('extractTenantId', () => {
    it('should extract tenant ID from args.data', () => {
      const params = {
        args: { data: { tenantId: 'tenant_123' } },
      };

      const tenantId = middleware['extractTenantId'](params);
      expect(tenantId).toBe('tenant_123');
    });

    it('should extract tenant ID from args.where', () => {
      const params = {
        args: { where: { tenantId: 'tenant_123' } },
      };

      const tenantId = middleware['extractTenantId'](params);
      expect(tenantId).toBe('tenant_123');
    });

    it('should extract tenant ID from result', () => {
      const params = {
        args: {},
      };
      const result = { tenantId: 'tenant_123' };

      const tenantId = middleware['extractTenantId'](params, result);
      expect(tenantId).toBe('tenant_123');
    });

    it('should return null when tenant ID not found', () => {
      const params = { args: {} };

      const tenantId = middleware['extractTenantId'](params);
      expect(tenantId).toBeNull();
    });
  });

  describe('invalidateModuleCache', () => {
    it('should invalidate module cache on update', async () => {
      const params = {
        model: 'Module',
        action: 'update',
        args: {
          where: { id: 'mod_123' },
          data: { tenantId: 'tenant_123' },
        },
      };
      const result = { id: 'mod_123' };

      (mockRedis.del as any).mockResolvedValue(1);

      await middleware['invalidateModuleCache'](params.action, params.args, result);

      expect(mockRedis.del).toHaveBeenCalledWith('module:tenant_123:mod_123');
    });

    it('should invalidate module lists on create', async () => {
      const params = {
        model: 'Module',
        action: 'create',
        args: {
          data: { tenantId: 'tenant_123' },
        },
      };
      const result = { id: 'mod_123' };

      (mockRedis.keys as any).mockResolvedValue(['module:list:tenant_123:filter1']);
      (mockRedis.del as any).mockResolvedValue(1);

      await middleware['invalidateModuleCache'](params.action, params.args, result);

      expect(mockRedis.keys).toHaveBeenCalledWith('module:list:tenant_123:*');
    });
  });

  describe('invalidateModuleLists', () => {
    it('should delete all module list caches', async () => {
      const keys = ['module:list:tenant_123:filter1', 'module:list:tenant_123:filter2'];
      (mockRedis.keys as any).mockResolvedValue(keys);
      (mockRedis.del as any).mockResolvedValue(1);

      await middleware['invalidateModuleLists']('tenant_123');

      expect(mockRedis.keys).toHaveBeenCalledWith('module:list:tenant_123:*');
      expect(mockRedis.del).toHaveBeenCalledTimes(2);
    });
  });

  describe('invalidateAssessmentCache', () => {
    it('should invalidate assessment cache on update', async () => {
      const params = {
        model: 'Assessment',
        action: 'update',
        args: {
          where: { id: 'asm_123' },
          data: { tenantId: 'tenant_123' },
        },
      };
      const result = { id: 'asm_123' };

      (mockRedis.del as any).mockResolvedValue(1);

      await middleware['invalidateAssessmentCache'](params.action, params.args, result);

      expect(mockRedis.del).toHaveBeenCalledWith('assessment:tenant_123:asm_123');
    });
  });

  describe('invalidateBasedOnOperation', () => {
    it('should handle Module model operations', async () => {
      const params = {
        model: 'Module',
        action: 'update',
        args: {
          where: { id: 'mod_123' },
          data: { tenantId: 'tenant_123' },
        },
      };
      const result = { id: 'mod_123' };

      (mockRedis.del as any).mockResolvedValue(1);

      await middleware['invalidateBasedOnOperation'](params, result);

      expect(mockRedis.del).toHaveBeenCalled();
    });

    it('should handle unknown model gracefully', async () => {
      const params = {
        model: 'UnknownModel',
        action: 'update',
        args: {},
      };
      const result = {};

      await expect(middleware['invalidateBasedOnOperation'](params, result)).resolves.toBeUndefined();
    });
  });
});
