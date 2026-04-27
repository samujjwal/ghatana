/**
 * AI Cache Service Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { AICacheService } from '../AICacheService.js';

describe('AICacheService', () => {
  let service: AICacheService<string>;
  let store: Map<string, string>;

  beforeEach(() => {
    store = new Map<string, string>();
    const redisMock = {
      async get(key: string): Promise<string | null> {
        return store.get(key) ?? null;
      },
      async set(key: string, value: string): Promise<'OK'> {
        store.set(key, value);
        return 'OK';
      },
      async del(...keys: string[]): Promise<number> {
        let deleted = 0;
        for (const key of keys) {
          if (store.delete(key)) {
            deleted++;
          }
        }
        return deleted;
      },
      async keys(pattern: string): Promise<string[]> {
        const prefix = pattern.replace('*', '');
        return Array.from(store.keys()).filter((key) => key.startsWith(prefix));
      },
    };

    service = new AICacheService<string>(redisMock as never, 60000);
  });

  describe('get and set', () => {
    it('should cache and retrieve values', async () => {
      await service.set('tutoring', { question: 'test' }, 'response1', 60000);
      const result = await service.get('tutoring', { question: 'test' });
      expect(result).toBe('response1');
    });

    it('should return null for non-existent keys', async () => {
      const result = await service.get('tutoring', { question: 'nonexistent' });
      expect(result).toBeNull();
    });

    it('should return null for cache miss', async () => {
      const result = await service.get('tutoring', { question: 'test' });
      expect(result).toBeNull();
    });
  });

  describe('invalidate', () => {
    it('should remove cached entry by params', async () => {
      await service.set('tutoring', { id: '1' }, 'response1', 60000);
      await service.set('tutoring', { id: '2' }, 'response2', 60000);
      await service.invalidate('tutoring', { id: '1' });
      
      expect(await service.get('tutoring', { id: '1' })).toBeNull();
      expect(await service.get('tutoring', { id: '2' })).toBe('response2');
    });
  });

  describe('clearPrefix', () => {
    it('should remove all cached entries for a prefix', async () => {
      await service.set('tutoring', { id: '1' }, 'response1', 60000);
      await service.set('content', { id: '1' }, 'response2', 60000);
      await service.clearPrefix('tutoring');
      
      expect(await service.get('tutoring', { id: '1' })).toBeNull();
      expect(await service.get('content', { id: '1' })).toBe('response2');
    });
  });

  describe('getStats', () => {
    it('should return cache statistics', async () => {
      await service.set('tutoring', { id: '1' }, 'response1', 60000);
      await service.get('tutoring', { id: '1' });
      await service.get('tutoring', { id: '1' }); // Cache hit
      await service.get('tutoring', { id: '2' }); // Cache miss
      
      const stats = service.getStats();
      expect(stats).toHaveProperty('totalHits');
      expect(stats).toHaveProperty('totalMisses');
      expect(stats).toHaveProperty('hitRate');
      expect(stats.totalHits).toBeGreaterThan(0);
    });

    it('should calculate hit rate correctly', async () => {
      await service.set('tutoring', { id: '1' }, 'response1', 60000);
      await service.get('tutoring', { id: '1' });
      await service.get('tutoring', { id: '1' });
      await service.get('tutoring', { id: '2' });
      
      const stats = service.getStats();
      expect(stats.hitRate).toBeCloseTo(0.666, 2);
    });
  });
});
