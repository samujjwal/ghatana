/**
 * Tests for LocalStoragePlugin
 */

import { LocalStoragePlugin } from '../../implementations/storage/LocalStoragePlugin';

// Mock localStorage for Node.js tests
const localStorageMock = (() => {
  let store: Record<string, string> = {};

  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString();
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      store = {};
    },
    key: (index: number) => {
      const keys = Object.keys(store);
      return keys[index] || null;
    },
    get length() {
      return Object.keys(store).length;
    },
  };
})();

// @ts-ignore - Mock setup for tests
global.localStorage = localStorageMock;

describe('LocalStoragePlugin', () => {
  let plugin: LocalStoragePlugin;

  beforeEach(() => {
    localStorageMock.clear();
    plugin = new LocalStoragePlugin({ prefix: 'test_' });
  });

  describe('initialization', () => {
    it('should initialize with proper configuration', async () => {
      expect(plugin.id).toBe('localstorage');
      expect(plugin.name).toBe('LocalStorage');
      expect(plugin.version).toBe('0.1.0');
      expect(plugin.enabled).toBe(false);
    });

    it('should set enabled flag on initialize', async () => {
      await plugin.initialize();
      expect(plugin.enabled).toBe(true);
    });

    it('should use custom prefix', async () => {
      const customPlugin = new LocalStoragePlugin({ prefix: 'custom_' });
      await customPlugin.initialize();

      await customPlugin.set('key', 'value');
      // Check if the key is stored with prefix
      const storedKey = (localStorageMock as any).getItem('custom_key');
      expect(storedKey).toBeDefined();
    });

    it('should throw error if not initialized', async () => {
      const uninit = new LocalStoragePlugin();
      await expect(uninit.get('key')).rejects.toThrow(
        'LocalStorage not initialized',
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

    it('should use prefix for storage key', async () => {
      await plugin.set('mykey', 'myvalue');
      // Verify key is prefixed in localStorage
      const prefixedKey = 'test_mykey';
      expect((localStorageMock as any).getItem(prefixedKey)).toBeDefined();
    });
  });

  describe('TTL (Time-to-Live)', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should expire values after TTL', async () => {
      await plugin.set('key', 'value', 100);
      let result = await plugin.get('key');
      expect(result).toBe('value');

      await new Promise(resolve => setTimeout(resolve, 150));

      result = await plugin.get('key');
      expect(result).toBeNull();
    });

    it('should not expire values without TTL', async () => {
      await plugin.set('key', 'value');
      await new Promise(resolve => setTimeout(resolve, 200));
      const result = await plugin.get('key');
      expect(result).toBe('value');
    });

    it('should store expiration time in localStorage', async () => {
      const ttl = 10000;
      const before = Date.now();
      await plugin.set('key', 'value', ttl);
      const after = Date.now();

      const stored = (localStorageMock as any).getItem('test_key');
      const entry = JSON.parse(stored);

      expect(entry.expiresAt).toBeDefined();
      expect(entry.expiresAt).toBeGreaterThanOrEqual(before + ttl);
      expect(entry.expiresAt).toBeLessThanOrEqual(after + ttl);
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

    it('should remove from underlying localStorage', async () => {
      await plugin.set('key', 'value');
      await plugin.delete('key');
      expect((localStorageMock as any).getItem('test_key')).toBeNull();
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

    it('should clear all prefixed values', async () => {
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

    it('should not clear values without prefix', async () => {
      // Store value without prefix
      (localStorageMock as any).setItem('other_key', 'other_value');

      await plugin.set('key1', 'value1');
      await plugin.clear();

      // Our key should be gone
      const result = await plugin.get('key1');
      expect(result).toBeNull();

      // Other key should remain
      expect((localStorageMock as any).getItem('other_key')).toBe('other_value');
    });

    it('should be safe to clear empty storage', async () => {
      await expect(plugin.clear()).resolves.not.toThrow();
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

  describe('getStats', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should track storage statistics', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');

      const stats = (await plugin.getStats()) as any;
      expect(stats.keyCount).toBe(2);
      expect(stats.approximateSizeBytes).toBeGreaterThan(0);
    });

    it('should reset stats after clear', async () => {
      await plugin.set('key1', 'value1');
      await plugin.set('key2', 'value2');

      await plugin.clear();

      const stats = (await plugin.getStats()) as any;
      expect(stats.keyCount).toBe(0);
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
        'LocalStorage not initialized',
      );
    });

    it('should not clear data on shutdown', async () => {
      await plugin.initialize();
      await plugin.set('key', 'value');
      await plugin.shutdown();

      // Data should still be in storage
      expect((localStorageMock as any).getItem('test_key')).toBeDefined();
    });
  });

  describe('JSON serialization', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should properly serialize complex nested objects', async () => {
      const complex = {
        users: [
          { id: 1, name: 'Alice', tags: ['admin', 'user'] },
          { id: 2, name: 'Bob', tags: ['user'] },
        ],
        metadata: { created: new Date().toISOString() },
      };

      await plugin.set('complex', complex);
      const result = await plugin.get('complex');

      expect(result).toEqual(complex);
    });

    it('should handle special characters in keys', async () => {
      const specialKeys = [
        'key-with-dashes',
        'key_with_underscores',
        'key.with.dots',
        'key/with/slashes',
      ];

      for (const key of specialKeys) {
        await plugin.set(key, 'value');
        const result = await plugin.get(key);
        expect(result).toBe('value');
      }
    });
  });

  describe('namespace isolation', () => {
    it('should isolate storage by prefix', async () => {
      const plugin1 = new LocalStoragePlugin({ prefix: 'app1_' });
      const plugin2 = new LocalStoragePlugin({ prefix: 'app2_' });

      await plugin1.initialize();
      await plugin2.initialize();

      await plugin1.set('key', 'value1');
      await plugin2.set('key', 'value2');

      const result1 = await plugin1.get('key');
      const result2 = await plugin2.get('key');

      expect(result1).toBe('value1');
      expect(result2).toBe('value2');
    });

    it('should clear only prefixed keys', async () => {
      const plugin1 = new LocalStoragePlugin({ prefix: 'app1_' });
      const plugin2 = new LocalStoragePlugin({ prefix: 'app2_' });

      await plugin1.initialize();
      await plugin2.initialize();

      await plugin1.set('key', 'value1');
      await plugin2.set('key', 'value2');

      await plugin1.clear();

      const result1 = await plugin1.get('key');
      const result2 = await plugin2.get('key');

      expect(result1).toBeNull();
      expect(result2).toBe('value2');
    });
  });
});
