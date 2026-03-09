import AsyncStorage from '@react-native-async-storage/async-storage';
import storage from '@/services/storage';

jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock')
);

describe('StorageService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('cache helpers', () => {
    it('stores and retrieves cached value', async () => {
      const payload = { message: 'hello' };
      await storage.setCache('greeting', payload, 10_000);
      const result = await storage.getCache('greeting');
      expect(result).toEqual(payload);
    });

    it('returns null when cache expired', async () => {
      const originalNow = Date.now();
      jest.spyOn(Date, 'now').mockReturnValue(originalNow);
      await storage.setCache('expired', { value: 1 }, 1);
      jest.spyOn(Date, 'now').mockReturnValue(originalNow + 10_000);

      const result = await storage.getCache('expired');
      expect(result).toBeNull();
      expect(AsyncStorage.removeItem).toHaveBeenCalledWith('cache:expired');
    });

    it('clears all cache keys', async () => {
      (AsyncStorage.getAllKeys as jest.Mock).mockResolvedValueOnce([
        'cache:first',
        'cache:second',
        'other:key',
      ]);

      await storage.clearCache();

      expect(AsyncStorage.multiRemove).toHaveBeenCalledWith(['cache:first', 'cache:second']);
    });
  });

  describe('preferences', () => {
    it('saves and loads preference data', async () => {
      await storage.setPreference('theme', { mode: 'dark' });
      const result = await storage.getPreference('theme');
      expect(result).toEqual({ mode: 'dark' });
    });

    it('returns null when preference missing', async () => {
      const result = await storage.getPreference('missing');
      expect(result).toBeNull();
    });
  });

  describe('auth token helpers', () => {
    it('stores and retrieves auth token', async () => {
      await storage.setAuthToken('token-123');
      const result = await storage.getAuthToken();
      expect(result).toBe('token-123');
    });

    it('clears auth token', async () => {
      await storage.clearAuthToken();
      expect(AsyncStorage.removeItem).toHaveBeenCalledWith('authToken');
    });
  });

  describe('generic helpers', () => {
    it('persists arbitrary values', async () => {
      await storage.set('data:key', { foo: 'bar' });
      const result = await storage.get('data:key');
      expect(result).toEqual({ foo: 'bar' });
    });

    it('removes values', async () => {
      await storage.remove('data:key');
      expect(AsyncStorage.removeItem).toHaveBeenCalledWith('data:key');
    });

    it('clears storage', async () => {
      await storage.clear();
      expect(AsyncStorage.clear).toHaveBeenCalled();
    });
  });
});
