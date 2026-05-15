import AsyncStorage from '@react-native-async-storage/async-storage';

type AsyncStorageWithBulkRemove = typeof AsyncStorage & {
  multiRemove?: (keys: string[]) => Promise<void>;
};

interface CacheEntry<T> {
  data: T;
  timestamp: number;
  expiresIn: number;
}

class StorageService {
  async setCache<T>(key: string, data: T, expiresIn = 5 * 60 * 1000): Promise<void> {
    const entry: CacheEntry<T> = { data, timestamp: Date.now(), expiresIn };
    await AsyncStorage.setItem(`cache:${key}`, JSON.stringify(entry));
  }

  async getCache<T>(key: string): Promise<T | null> {
    const cached = await AsyncStorage.getItem(`cache:${key}`);
    if (!cached) return null;

    const entry = JSON.parse(cached) as CacheEntry<T>;
    if (Date.now() - entry.timestamp > entry.expiresIn) {
      await AsyncStorage.removeItem(`cache:${key}`);
      return null;
    }

    return entry.data;
  }

  async clearCache(): Promise<void> {
    const keys = await AsyncStorage.getAllKeys();
    const cacheKeys = keys.filter((key) => key.startsWith('cache:'));
    const storage = AsyncStorage as AsyncStorageWithBulkRemove;
    if (typeof storage.multiRemove === 'function') {
      await storage.multiRemove(cacheKeys);
      return;
    }
    await Promise.all(cacheKeys.map((cacheKey) => AsyncStorage.removeItem(cacheKey)));
  }

  async setPreference(key: string, value: unknown): Promise<void> {
    await AsyncStorage.setItem(`pref:${key}`, JSON.stringify(value));
  }

  async getPreference<T>(key: string): Promise<T | null> {
    const value = await AsyncStorage.getItem(`pref:${key}`);
    return value ? (JSON.parse(value) as T) : null;
  }

  async setAuthToken(token: string): Promise<void> {
    await AsyncStorage.setItem('authToken', token);
  }

  async getAuthToken(): Promise<string | null> {
    return AsyncStorage.getItem('authToken');
  }

  async clearAuthToken(): Promise<void> {
    await AsyncStorage.removeItem('authToken');
  }

  async set(key: string, value: unknown): Promise<void> {
    await AsyncStorage.setItem(key, JSON.stringify(value));
  }

  async get<T>(key: string): Promise<T | null> {
    const value = await AsyncStorage.getItem(key);
    return value ? (JSON.parse(value) as T) : null;
  }

  async remove(key: string): Promise<void> {
    await AsyncStorage.removeItem(key);
  }

  async clear(): Promise<void> {
    await AsyncStorage.clear();
  }
}

const storage = new StorageService();

export { storage };
export default storage;
