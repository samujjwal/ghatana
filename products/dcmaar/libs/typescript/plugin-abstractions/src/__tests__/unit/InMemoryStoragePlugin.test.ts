/**
 * Tests for InMemoryStoragePlugin
 */

import { InMemoryStoragePlugin } from '../../implementations/storage/InMemoryStoragePlugin';

describe('InMemoryStoragePlugin', () => {
  let plugin: InMemoryStoragePlugin;

  beforeEach(() => {
    plugin = new InMemoryStoragePlugin();
  });

  describe('initialization', () => {
    it('should initialize with proper configuration', async () => {
      expect(plugin.id).toBe('memory-storage');
      expect(plugin.name).toBe('In-Memory Storage');
      expect(plugin.version).toBe('0.1.0');
      expect(plugin.enabled).toBe(false);
    });

    it('should set enabled flag on initialize', async () => {
      await plugin.initialize();
      expect(plugin.enabled).toBe(true);
    });

    it('should throw error if not initialized', async () => {
      await expect(plugin.get('key')).rejects.toThrow(
        'Storage not initialized',
      );
    });
  });

  describe('set and get', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should store and retrieve a value', async () => {
      await plugin.set('key1', 'value1');
      const result = await plugin.get('key1');
      expect(result).toBe('value1');
    });

    it('should store and retrieve complex objects', async () => {
      const obj = { name: 'John', age: 30, active: true };
      await plugin.set('user', obj);
      const result = await plugin.get('user');
      expect(result).toEqual(obj);
    });

    it('should store and retrieve arrays', async () => {
      const arr = [1, 2, 3, 4, 5];
      await plugin.set('numbers', arr);
      const result = await plugin.get('numbers');
      expect(result).toEqual(arr);
    });

    it('should store null values', async () => {
      await plugin.set('nullValue', null);
      const result = await plugin.get('nullValue');
      expect(result).toBeNull();
    });

    it('should store undefined values', async () => {
      await plugin.set('undefinedValue', undefined);
      const result = await plugin.get('undefinedValue');
      expect(result).toBeUndefined();
    });

    it('should return null for non-existent keys', async () => {
      const result = await plugin.get('nonexistent');
      expect(result).toBeNull();
    });

    it('should overwrite existing values', async () => {
      await plugin.set('key', 'value1');
      await plugin.set('key', 'value2');
      const result = await plugin.get('key');
      expect(result).toBe('value2');
    });
  });

  describe('TTL (Time-to-Live)', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should expire values after TTL', async () => {
      await plugin.set('key', 'value', 100); // 100ms TTL
      let result = await plugin.get('key');
      expect(result).toBe('value');

      // Wait for expiration
      await new Promise(resolve => setTimeout(resolve, 150));

      result = await plugin.get('key');
      expect(result).toBeNull();
    });

    it('should not expire values without TTL', async () => {
      await plugin.set('key', 'value'); // No TTL
      await new Promise(resolve => setTimeout(resolve, 200));
      const result = await plugin.get('key');
      expect(result).toBe('value');
    });

    it('should handle TTL=0 as no expiration', async () => {
      await plugin.set('key', 'value', 0);
      const result = await plugin.get('key');
      expect(result).toBe('value');
    });

    it('should handle negative TTL', async () => {
      await plugin.set('key', 'value', -100);
      const result = await plugin.get('key');
      // Negative TTL is not treated as expiration - value is stored
      // Only positive TTL values set expiration
      expect(result).toBe('value');
    });
  });

  describe('delete', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should delete existing values', async () => {
      await plugin.set('key', 'value');
      await plugin.delete('key');
      const result = await plugin.get('key');
      expect(result).toBeNull();
    });

    it('should handle deleting non-existent keys', async () => {
      // Should not throw
      await expect(plugin.delete('nonexistent')).resolves.not.toThrow();
    });

    it('should delete only the specified key', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');
      await plugin.delete('key1');

      const result1 = await plugin.get('key1');
      const result2 = await plugin.get('key2');

      expect(result1).toBeNull();
      expect(result2).toBe('value2');
    });
  });

  describe('exists', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should return true for existing keys', async () => {
      await plugin.set('key', 'value');
      const result = await plugin.exists('key');
      expect(result).toBe(true);
    });

    it('should return false for non-existent keys', async () => {
      const result = await plugin.exists('nonexistent');
      expect(result).toBe(false);
    });

    it('should return false for expired keys', async () => {
      await plugin.set('key', 'value', 100);
      await new Promise(resolve => setTimeout(resolve, 150));
      const result = await plugin.exists('key');
      expect(result).toBe(false);
    });

    it('should return true for non-expired keys', async () => {
      await plugin.set('key', 'value', 500);
      await new Promise(resolve => setTimeout(resolve, 100));
      const result = await plugin.exists('key');
      expect(result).toBe(true);
    });
  });

  describe('clear', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should clear all stored values', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');
      await plugin.set('key3', 'value3');

      await plugin.clear();

      const result1 = await plugin.get('key1');
      const result2 = await plugin.get('key2');
      const result3 = await plugin.get('key3');

      expect(result1).toBeNull();
      expect(result2).toBeNull();
      expect(result3).toBeNull();
    });

    it('should be safe to clear empty storage', async () => {
      await expect(plugin.clear()).resolves.not.toThrow();
    });

    it('should clear without affecting other operations', async () => {
      await plugin.set('key1', 'value1');
      await plugin.clear();
      await plugin.set('key2', 'value2');

      const result2 = await plugin.get('key2');
      expect(result2).toBe('value2');
    });
  });

  describe('execute', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should execute set command', async () => {
      await plugin.execute('set', { key: 'key1', value: 'value1' });
      const result = await plugin.get('key1');
      expect(result).toBe('value1');
    });

    it('should execute get command', async () => {
      await plugin.set('key', 'value');
      const result = await plugin.execute('get', { key: 'key' });
      expect(result).toBe('value');
    });

    it('should execute delete command', async () => {
      await plugin.set('key', 'value');
      await plugin.execute('delete', { key: 'key' });
      const result = await plugin.get('key');
      expect(result).toBeNull();
    });

    it('should execute exists command', async () => {
      await plugin.set('key', 'value');
      const result1 = await plugin.execute('exists', { key: 'key' });
      const result2 = await plugin.execute('exists', {
        key: 'nonexistent',
      });
      expect(result1).toBe(true);
      expect(result2).toBe(false);
    });

    it('should execute clear command', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');
      await plugin.execute('clear');

      const result1 = await plugin.get('key1');
      const result2 = await plugin.get('key2');
      expect(result1).toBeNull();
      expect(result2).toBeNull();
    });

    it('should throw for unknown command', async () => {
      await expect(plugin.execute('unknown')).rejects.toThrow(
        'Unknown command: unknown',
      );
    });
  });

  describe('concurrent operations', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should handle concurrent set operations', async () => {
      const promises = [];
      for (let i = 0; i < 100; i++) {
        promises.push(plugin.set(`key${i}`, `value${i}`));
      }

      await Promise.all(promises);

      for (let i = 0; i < 100; i++) {
        const result = await plugin.get(`key${i}`);
        expect(result).toBe(`value${i}`);
      }
    });

    it('should handle concurrent get/set operations', async () => {
      await plugin.set('key1', 'value1');

      const promises = [];
      for (let i = 0; i < 50; i++) {
        promises.push(plugin.set(`key${i}`, `value${i}`));
        promises.push(plugin.get(`key1`));
      }

      const results = await Promise.all(promises);
      // All gets should return value1
      const getResults = results.filter((_, i) => i % 2 === 1);
      getResults.forEach(result => {
        expect(result).toBe('value1');
      });
    });
  });

  describe('shutdown', () => {
    it('should disable plugin', async () => {
      await plugin.initialize();
      expect(plugin.enabled).toBe(true);

      await plugin.shutdown();
      expect(plugin.enabled).toBe(false);
    });

    it('should prevent operations after shutdown', async () => {
      await plugin.initialize();
      await plugin.shutdown();

      await expect(plugin.get('key')).rejects.toThrow(
        'Storage not initialized',
      );
    });

    it('should clear storage on shutdown', async () => {
      await plugin.initialize();
      await plugin.set('key', 'value');
      await plugin.shutdown();

      // Reinitialize
      await plugin.initialize();
      const result = await plugin.get('key');
      expect(result).toBeNull();
    });
  });

  describe('automatic cleanup', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should cleanup expired entries periodically', async () => {
      await plugin.set('key1', 'value1', 100);
      await plugin.set('key2', 'value2', 1000); // Longer TTL

      // Wait for first expiration
      await new Promise(resolve => setTimeout(resolve, 150));

      // Trigger a get operation to potentially cleanup
      const result1 = await plugin.get('key1');
      expect(result1).toBeNull();

      // key2 should still exist
      const result2 = await plugin.get('key2');
      expect(result2).toBe('value2');
    });

    it('should survive multiple expired entries', async () => {
      for (let i = 0; i < 20; i++) {
        await plugin.set(`key${i}`, `value${i}`, 100);
      }

      await new Promise(resolve => setTimeout(resolve, 150));

      for (let i = 0; i < 20; i++) {
        const result = await plugin.get(`key${i}`);
        expect(result).toBeNull();
      }
    });
  });

  describe('storage statistics', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should track storage size', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');

      const stats = await plugin.getStats();
      expect(stats.keyCount).toBe(2);
      expect(typeof stats.storageSize).toBe('number');
      expect((stats.storageSize as number)).toBeGreaterThan(0);
    });

    it('should update size after delete', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');

      let stats = await plugin.getStats();
      const size1 = stats.storageSize as number;

      await plugin.delete('key1');

      stats = await plugin.getStats();
      expect(stats.keyCount).toBe(1);
      expect((stats.storageSize as number)).toBeLessThan(size1);
    });

    it('should reset stats after clear', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');

      await plugin.clear();

      const stats = await plugin.getStats();
      expect(stats.keyCount).toBe(0);
      expect(stats.storageSize).toBe(0);
    });
  });
});
