/**
 * AI Cache Service Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { AICacheService } from '../AICacheService.js';

describe('AICacheService', () => {
  let service: AICacheService<string>;

  beforeEach(() => {
    service = new AICacheService<string>(100, 60000); // 100 entries, 60s TTL
  });

  describe('get and set', () => {
    it('should cache and retrieve values', () => {
      service.set('tutoring', { question: 'test' }, 'response1', 60000);
      const result = service.get('tutoring', { question: 'test' });
      expect(result).toBe('response1');
    });

    it('should return null for non-existent keys', () => {
      const result = service.get('tutoring', { question: 'nonexistent' });
      expect(result).toBeNull();
    });

    it('should return null for cache miss', () => {
      const result = service.get('tutoring', { question: 'test' });
      expect(result).toBeNull();
    });
  });

  describe('invalidate', () => {
    it('should remove cached entries by prefix', () => {
      service.set('tutoring', { id: '1' }, 'response1', 60000);
      service.set('tutoring', { id: '2' }, 'response2', 60000);
      service.invalidate('tutoring');
      
      expect(service.get('tutoring', { id: '1' })).toBeNull();
      expect(service.get('tutoring', { id: '2' })).toBeNull();
    });
  });

  describe('clear', () => {
    it('should remove all cached entries', () => {
      service.set('tutoring', { id: '1' }, 'response1', 60000);
      service.set('content', { id: '1' }, 'response2', 60000);
      service.clear();
      
      expect(service.get('tutoring', { id: '1' })).toBeNull();
      expect(service.get('content', { id: '1' })).toBeNull();
    });
  });

  describe('getStats', () => {
    it('should return cache statistics', () => {
      service.set('tutoring', { id: '1' }, 'response1', 60000);
      service.get('tutoring', { id: '1' });
      service.get('tutoring', { id: '1' }); // Cache hit
      service.get('tutoring', { id: '2' }); // Cache miss
      
      const stats = service.getStats();
      expect(stats).toHaveProperty('size');
      expect(stats).toHaveProperty('totalHits');
      expect(stats).toHaveProperty('totalMisses');
      expect(stats).toHaveProperty('hitRate');
      expect(stats.size).toBe(1);
    });

    it('should calculate hit rate correctly', () => {
      service.set('tutoring', { id: '1' }, 'response1', 60000);
      service.get('tutoring', { id: '1' });
      service.get('tutoring', { id: '1' });
      service.get('tutoring', { id: '2' });
      
      const stats = service.getStats();
      expect(stats.hitRate).toBeCloseTo(0.666, 2);
    });
  });

  describe('max entries', () => {
    it('should enforce max entries limit', () => {
      const smallCache = new AICacheService<string>(2, 60000);
      smallCache.set('tutoring', { id: '1' }, 'response1', 60000);
      smallCache.set('tutoring', { id: '2' }, 'response2', 60000);
      smallCache.set('tutoring', { id: '3' }, 'response3', 60000);
      
      // First entry should be evicted
      expect(smallCache.get('tutoring', { id: '1' })).toBeNull();
      expect(smallCache.get('tutoring', { id: '2' })).toBe('response2');
      expect(smallCache.get('tutoring', { id: '3' })).toBe('response3');
    });
  });
});
