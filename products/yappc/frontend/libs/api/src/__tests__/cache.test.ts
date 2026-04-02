import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { CacheManager } from '../devsecops/cache.js';

describe('CacheManager', () => {
  let cache: CacheManager;

  beforeEach(() => {
    vi.useFakeTimers();
    cache = new CacheManager();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('set / get', () => {
    it('returns undefined for a key that was never set', () => {
      expect(cache.get<string>('missing')).toBeNull();
    });

    it('returns the stored value immediately after set', () => {
      cache.set('key1', { value: 42 }, 5000);
      expect(cache.get<{ value: number }>('key1')).toEqual({ value: 42 });
    });

    it('stores different types correctly', () => {
      cache.set('str', 'hello', 1000);
      cache.set('num', 123, 1000);
      cache.set('arr', [1, 2, 3], 1000);
      expect(cache.get<string>('str')).toBe('hello');
      expect(cache.get<number>('num')).toBe(123);
      expect(cache.get<number[]>('arr')).toEqual([1, 2, 3]);
    });

    it('overwrites an existing key', () => {
      cache.set('dup', 'first', 5000);
      cache.set('dup', 'second', 5000);
      expect(cache.get<string>('dup')).toBe('second');
    });
  });

  describe('TTL expiry', () => {
    it('returns null after TTL has expired', () => {
      cache.set('expiring', 'soon', 1000);
      vi.advanceTimersByTime(1001);
      expect(cache.get<string>('expiring')).toBeNull();
    });

    it('returns value just before TTL expires', () => {
      cache.set('alive', 'yes', 2000);
      vi.advanceTimersByTime(1999);
      expect(cache.get<string>('alive')).toBe('yes');
    });

    it('each entry has its own TTL', () => {
      cache.set('short', 'v', 500);
      cache.set('long', 'v', 5000);
      vi.advanceTimersByTime(501);
      expect(cache.get<string>('short')).toBeNull();
      expect(cache.get<string>('long')).toBe('v');
    });
  });

  describe('delete', () => {
    it('removes a specific key', () => {
      cache.set('del', 'data', 5000);
      cache.delete('del');
      expect(cache.get<string>('del')).toBeNull();
    });

    it('does not throw when deleting a non-existent key', () => {
      expect(() => cache.delete('ghost')).not.toThrow();
    });
  });

  describe('clear', () => {
    it('removes all entries', () => {
      cache.set('a', 1, 5000);
      cache.set('b', 2, 5000);
      cache.clear();
      expect(cache.get<number>('a')).toBeNull();
      expect(cache.get<number>('b')).toBeNull();
    });

    it('size is 0 after clear', () => {
      cache.set('x', 'y', 5000);
      cache.clear();
      expect(cache.size()).toBe(0);
    });
  });

  describe('size', () => {
    it('returns 0 on fresh cache', () => {
      expect(cache.size()).toBe(0);
    });

    it('increments on each set', () => {
      cache.set('one', 1, 5000);
      cache.set('two', 2, 5000);
      expect(cache.size()).toBe(2);
    });

    it('does not count expired entries', () => {
      cache.set('gone', 1, 100);
      vi.advanceTimersByTime(200);
      // Accessing triggers eviction
      cache.get<number>('gone');
      expect(cache.size()).toBe(0);
    });
  });

  describe('keys', () => {
    it('returns all live keys', () => {
      cache.set('alpha', 1, 5000);
      cache.set('beta', 2, 5000);
      const keys = cache.keys();
      expect(keys).toContain('alpha');
      expect(keys).toContain('beta');
    });

    it('returns empty array when cache is empty', () => {
      expect(cache.keys()).toHaveLength(0);
    });
  });

  describe('invalidatePattern', () => {
    it('removes keys matching the regex pattern', () => {
      cache.set('user-1', 'alice', 5000);
      cache.set('user-2', 'bob', 5000);
      cache.set('post-1', 'hello', 5000);
      cache.invalidatePattern('^user-');
      expect(cache.get<string>('user-1')).toBeNull();
      expect(cache.get<string>('user-2')).toBeNull();
      expect(cache.get<string>('post-1')).toBe('hello');
    });

    it('removes keys matching suffix pattern', () => {
      cache.set('data-temp', 'x', 5000);
      cache.set('data-keep', 'y', 5000);
      cache.invalidatePattern('-temp$');
      expect(cache.get<string>('data-temp')).toBeNull();
      expect(cache.get<string>('data-keep')).toBe('y');
    });

    it('does not throw when no keys match', () => {
      cache.set('stable', 'z', 5000);
      expect(() => cache.invalidatePattern('^nomatch-')).not.toThrow();
      expect(cache.get<string>('stable')).toBe('z');
    });

    it('removes all keys when pattern matches all', () => {
      cache.set('a', 1, 5000);
      cache.set('b', 2, 5000);
      cache.invalidatePattern('.*');
      expect(cache.size()).toBe(0);
    });
  });
});
