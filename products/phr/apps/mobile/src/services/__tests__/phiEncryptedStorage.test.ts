/**
 * Tests for PHI encrypted storage adapter.
 *
 * Verifies AES-256-GCM encryption, key lifecycle, biometric gating, and cache
 * clearing behavior.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import * as LocalAuthentication from 'expo-local-authentication';
import {
  phiSet,
  phiGet,
  phiRemove,
  phiClearAll,
  phiEnableBiometricPolicy,
  setPhiStorageAdapter,
  resetPhiStorageAdapter,
  type PhiStorageAdapter,
} from '../phiEncryptedStorage';

// Mock SecureStore
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
  AFTER_FIRST_UNLOCK: 'afterFirstUnlock',
}));

// Mock AsyncStorage
jest.mock('@react-native-async-storage/async-storage', () => ({
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  getAllKeys: jest.fn(),
}));

jest.mock('expo-local-authentication', () => ({
  hasHardwareAsync: jest.fn(),
  isEnrolledAsync: jest.fn(),
  authenticateAsync: jest.fn(),
}));

jest.mock('../../i18n/phrMobileI18n', () => ({
  t: (key: string) => key,
}));

describe('phiEncryptedStorage', () => {
  let mockAdapter: PhiStorageAdapter;
  const mockStorage = new Map<string, string>();

  beforeEach(() => {
    mockStorage.clear();
    jest.clearAllMocks();

    // Create a simple mock adapter for testing
    mockAdapter = {
      async setItem(key: string, value: string): Promise<void> {
        mockStorage.set(key, value);
      },
      async getItem(key: string): Promise<string | null> {
        return mockStorage.get(key) ?? null;
      },
      async removeItem(key: string): Promise<void> {
        mockStorage.delete(key);
      },
      async clearAllPhi(): Promise<void> {
        for (const key of Array.from(mockStorage.keys())) {
          if (key.startsWith('phr-phi-cipher:')) {
            mockStorage.delete(key);
          }
        }
      },
    };

    (AsyncStorage.getAllKeys as jest.MockedFunction<typeof AsyncStorage.getAllKeys>).mockResolvedValue([]);
    setPhiStorageAdapter(mockAdapter);
  });

  afterEach(() => {
    resetPhiStorageAdapter();
  });

  describe('basic encryption/decryption', () => {
    it('encrypts and decrypts a simple string', async () => {
      await phiSet('test-key', 'hello world');
      const result = await phiGet('test-key');
      expect(result).toBe('hello world');
    });

    it('encrypts and decrypts JSON data', async () => {
      const data = { patientId: '123', name: 'John Doe' };
      await phiSet('patient-data', JSON.stringify(data));
      const result = await phiGet('patient-data');
      expect(result).toBe(JSON.stringify(data));
    });

    it('returns null for non-existent keys', async () => {
      const result = await phiGet('non-existent');
      expect(result).toBeNull();
    });

    it('removes a specific key', async () => {
      await phiSet('test-key', 'value');
      await phiRemove('test-key');
      const result = await phiGet('test-key');
      expect(result).toBeNull();
    });

    it('handles empty strings', async () => {
      await phiSet('empty', '');
      const result = await phiGet('empty');
      expect(result).toBe('');
    });

    it('handles special characters', async () => {
      const special = 'नेपाली 🇳🇵 <script>alert("xss")</script>';
      await phiSet('special', special);
      const result = await phiGet('special');
      expect(result).toBe(special);
    });

    it('handles large payloads', async () => {
      const large = 'x'.repeat(10000);
      await phiSet('large', large);
      const result = await phiGet('large');
      expect(result).toBe(large);
    });
  });

  describe('phiClearAll', () => {
    beforeEach(async () => {
      await phiSet('phr-phi-cipher:key1', 'value1');
      await phiSet('phr-phi-cipher:key2', 'value2');
      await phiSet('other-key', 'value3');
    });

    it('clears all PHI-prefixed keys', async () => {
      await phiClearAll();
      expect(await phiGet('phr-phi-cipher:key1')).toBeNull();
      expect(await phiGet('phr-phi-cipher:key2')).toBeNull();
    });

    it('does not clear non-PHI keys', async () => {
      await phiClearAll();
      expect(await phiGet('other-key')).toBe('value3');
    });

    it('handles empty storage gracefully', async () => {
      await phiClearAll();
      await phiClearAll(); // Should not throw
      expect(await phiGet('phr-phi-cipher:key1')).toBeNull();
    });
  });

  describe('adapter injection', () => {
    it('allows custom adapter injection', async () => {
      const customAdapter: PhiStorageAdapter = {
        async setItem(key: string, value: string): Promise<void> {
          // Custom behavior: add prefix
          mockStorage.set(`custom:${key}`, value);
        },
        async getItem(key: string): Promise<string | null> {
          return mockStorage.get(`custom:${key}`) ?? null;
        },
        async removeItem(key: string): Promise<void> {
          mockStorage.delete(`custom:${key}`);
        },
      };

      setPhiStorageAdapter(customAdapter);
      await phiSet('test', 'value');
      expect(await phiGet('test')).toBe('value');
      expect(mockStorage.has('custom:test')).toBe(true);
    });

    it('resets to production adapter', async () => {
      setPhiStorageAdapter(mockAdapter);
      resetPhiStorageAdapter();
      // After reset, should use production adapter
      // This test verifies the reset function exists and doesn't throw
      expect(() => resetPhiStorageAdapter()).not.toThrow();
    });
  });

  describe('error handling', () => {
    it('handles adapter errors gracefully', async () => {
      const failingAdapter: PhiStorageAdapter = {
        async setItem(): Promise<void> {
          throw new Error('Storage failed');
        },
        async getItem(): Promise<string | null> {
          throw new Error('Read failed');
        },
        async removeItem(): Promise<void> {
          throw new Error('Delete failed');
        },
      };

      setPhiStorageAdapter(failingAdapter);

      await expect(phiSet('key', 'value')).rejects.toThrow('Storage failed');
      await expect(phiGet('key')).rejects.toThrow('Read failed');
      await expect(phiRemove('key')).rejects.toThrow('Delete failed');
    });

    it('handles null values in adapter', async () => {
      const nullReturningAdapter: PhiStorageAdapter = {
        async setItem(): Promise<void> {
          // Do nothing
        },
        async getItem(): Promise<string | null> {
          return null;
        },
        async removeItem(): Promise<void> {
          // Do nothing
        },
      };

      setPhiStorageAdapter(nullReturningAdapter);
      expect(await phiGet('key')).toBeNull();
    });
  });

  describe('concurrent operations', () => {
    it('handles concurrent writes', async () => {
      const promises = [];
      for (let i = 0; i < 10; i++) {
        promises.push(phiSet(`key${i}`, `value${i}`));
      }
      await Promise.all(promises);

      for (let i = 0; i < 10; i++) {
        expect(await phiGet(`key${i}`)).toBe(`value${i}`);
      }
    });

    it('handles concurrent reads', async () => {
      await phiSet('shared', 'shared-value');
      const promises = [];
      for (let i = 0; i < 10; i++) {
        promises.push(phiGet('shared'));
      }
      const results = await Promise.all(promises);
      expect(results.every((r) => r === 'shared-value')).toBe(true);
    });
  });

  describe('production key lifecycle', () => {
    it('initializes production metadata on first encrypted write', async () => {
      const secureStore = new Map<string, string>();
      const asyncStore = new Map<string, string>();
      (SecureStore.getItemAsync as jest.MockedFunction<typeof SecureStore.getItemAsync>).mockImplementation(
        async (key: string) => secureStore.get(key) ?? null,
      );
      (SecureStore.setItemAsync as jest.MockedFunction<typeof SecureStore.setItemAsync>).mockImplementation(
        async (key: string, value: string) => {
          secureStore.set(key, value);
        },
      );
      (SecureStore.deleteItemAsync as jest.MockedFunction<typeof SecureStore.deleteItemAsync>).mockImplementation(
        async (key: string) => {
          secureStore.delete(key);
        },
      );
      (AsyncStorage.setItem as jest.MockedFunction<typeof AsyncStorage.setItem>).mockImplementation(
        async (key: string, value: string) => {
          asyncStore.set(key, value);
        },
      );
      (AsyncStorage.getItem as jest.MockedFunction<typeof AsyncStorage.getItem>).mockImplementation(
        async (key: string) => asyncStore.get(key) ?? null,
      );
      (AsyncStorage.removeItem as jest.MockedFunction<typeof AsyncStorage.removeItem>).mockImplementation(
        async (key: string) => {
          asyncStore.delete(key);
        },
      );
      (AsyncStorage.getAllKeys as jest.MockedFunction<typeof AsyncStorage.getAllKeys>).mockImplementation(
        async () => Array.from(asyncStore.keys()),
      );

      resetPhiStorageAdapter();

      await phiSet('phr-phi-cipher:patient-summary', 'encrypted payload source');

      expect(secureStore.get('phr-phi-encryption-key-v1')).toEqual(expect.any(String));
      expect(secureStore.get('phr-phi-key-version')).toBe('1');
      expect(secureStore.get('phr-phi-key-created-at')).toEqual(expect.any(String));
      expect(secureStore.get('phr-phi-device-install-id')).toEqual(expect.any(String));
      expect(secureStore.get('phr-phi-key-registry')).toContain('phr-phi-cipher:patient-summary');
      expect(asyncStore.get('phr-phi-cipher:patient-summary')).not.toBe('encrypted payload source');
    });

    it('requires biometric authentication before decrypting with a cached key when policy is enabled', async () => {
      const secureStore = new Map<string, string>();
      const asyncStore = new Map<string, string>();
      (SecureStore.getItemAsync as jest.MockedFunction<typeof SecureStore.getItemAsync>).mockImplementation(
        async (key: string) => secureStore.get(key) ?? null,
      );
      (SecureStore.setItemAsync as jest.MockedFunction<typeof SecureStore.setItemAsync>).mockImplementation(
        async (key: string, value: string) => {
          secureStore.set(key, value);
        },
      );
      (SecureStore.deleteItemAsync as jest.MockedFunction<typeof SecureStore.deleteItemAsync>).mockImplementation(
        async (key: string) => {
          secureStore.delete(key);
        },
      );
      (AsyncStorage.setItem as jest.MockedFunction<typeof AsyncStorage.setItem>).mockImplementation(
        async (key: string, value: string) => {
          asyncStore.set(key, value);
        },
      );
      (AsyncStorage.getItem as jest.MockedFunction<typeof AsyncStorage.getItem>).mockImplementation(
        async (key: string) => asyncStore.get(key) ?? null,
      );
      (AsyncStorage.removeItem as jest.MockedFunction<typeof AsyncStorage.removeItem>).mockImplementation(
        async (key: string) => {
          asyncStore.delete(key);
        },
      );
      (AsyncStorage.getAllKeys as jest.MockedFunction<typeof AsyncStorage.getAllKeys>).mockImplementation(
        async () => Array.from(asyncStore.keys()),
      );
      (LocalAuthentication.hasHardwareAsync as jest.MockedFunction<typeof LocalAuthentication.hasHardwareAsync>).mockResolvedValue(true);
      (LocalAuthentication.isEnrolledAsync as jest.MockedFunction<typeof LocalAuthentication.isEnrolledAsync>).mockResolvedValue(true);
      (LocalAuthentication.authenticateAsync as jest.MockedFunction<typeof LocalAuthentication.authenticateAsync>).mockResolvedValue({ success: false, error: 'user_cancel' });

      resetPhiStorageAdapter();

      await phiSet('patient-summary', 'encrypted payload source');
      await phiEnableBiometricPolicy();

      await expect(phiGet('patient-summary')).rejects.toThrow('biometric.requiredForPhi');
      expect(LocalAuthentication.authenticateAsync).toHaveBeenCalledWith({
        promptMessage: 'biometric.protectedHealthPrompt',
        fallbackLabel: 'biometric.fallbackLabel',
        cancelLabel: 'biometric.cancelLabel',
        disableDeviceFallback: false,
      });
    });

    it('returns null and removes modified ciphertext', async () => {
      const secureStore = new Map<string, string>();
      const asyncStore = new Map<string, string>();
      (SecureStore.getItemAsync as jest.MockedFunction<typeof SecureStore.getItemAsync>).mockImplementation(
        async (key: string) => secureStore.get(key) ?? null,
      );
      (SecureStore.setItemAsync as jest.MockedFunction<typeof SecureStore.setItemAsync>).mockImplementation(
        async (key: string, value: string) => {
          secureStore.set(key, value);
        },
      );
      (SecureStore.deleteItemAsync as jest.MockedFunction<typeof SecureStore.deleteItemAsync>).mockImplementation(
        async (key: string) => {
          secureStore.delete(key);
        },
      );
      (AsyncStorage.setItem as jest.MockedFunction<typeof AsyncStorage.setItem>).mockImplementation(
        async (key: string, value: string) => {
          asyncStore.set(key, value);
        },
      );
      (AsyncStorage.getItem as jest.MockedFunction<typeof AsyncStorage.getItem>).mockImplementation(
        async (key: string) => asyncStore.get(key) ?? null,
      );
      (AsyncStorage.removeItem as jest.MockedFunction<typeof AsyncStorage.removeItem>).mockImplementation(
        async (key: string) => {
          asyncStore.delete(key);
        },
      );
      (AsyncStorage.getAllKeys as jest.MockedFunction<typeof AsyncStorage.getAllKeys>).mockImplementation(
        async () => Array.from(asyncStore.keys()),
      );

      resetPhiStorageAdapter();

      await phiSet('patient-summary', 'encrypted payload source');
      asyncStore.set('patient-summary', 'not-valid-ciphertext');

      await expect(phiGet('patient-summary')).resolves.toBeNull();
      expect(asyncStore.has('patient-summary')).toBe(false);
    });
  });
});

describe('phiEncryptedStorage — AsyncStorage contains ciphertext only (G7-002, G11-005)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterEach(() => {
    resetPhiStorageAdapter();
  });

  it('stored value in AsyncStorage is valid base64 and contains no plaintext PHI', async () => {
    const asyncStore = new Map<string, string>();
    const secureStore = new Map<string, string>();

    (SecureStore.getItemAsync as jest.MockedFunction<typeof SecureStore.getItemAsync>).mockImplementation(
      async (key: string) => secureStore.get(key) ?? null,
    );
    (SecureStore.setItemAsync as jest.MockedFunction<typeof SecureStore.setItemAsync>).mockImplementation(
      async (key: string, value: string) => { secureStore.set(key, value); },
    );
    (SecureStore.deleteItemAsync as jest.MockedFunction<typeof SecureStore.deleteItemAsync>).mockImplementation(
      async (key: string) => { secureStore.delete(key); },
    );
    (AsyncStorage.setItem as jest.MockedFunction<typeof AsyncStorage.setItem>).mockImplementation(
      async (key: string, value: string) => { asyncStore.set(key, value); },
    );
    (AsyncStorage.getItem as jest.MockedFunction<typeof AsyncStorage.getItem>).mockImplementation(
      async (key: string) => asyncStore.get(key) ?? null,
    );
    (AsyncStorage.removeItem as jest.MockedFunction<typeof AsyncStorage.removeItem>).mockImplementation(
      async (key: string) => { asyncStore.delete(key); },
    );
    (AsyncStorage.getAllKeys as jest.MockedFunction<typeof AsyncStorage.getAllKeys>).mockImplementation(
      async () => Array.from(asyncStore.keys()),
    );

    resetPhiStorageAdapter();

    const plaintext = 'patient-name:Ram Bahadur Thapa,dob:1985-04-12,bloodType:A+';
    await phiSet('phr-phi-cipher:patient-summary', plaintext);

    const storedValue = asyncStore.get('phr-phi-cipher:patient-summary');
    expect(storedValue).toBeDefined();

    expect(storedValue).not.toBe(plaintext);
    expect(storedValue).toMatch(/^[A-Za-z0-9+/]+=*$/);

    const decoded = Buffer.from(storedValue!, 'base64');
    expect(decoded.length).toBeGreaterThan(plaintext.length);

    for (const [, value] of asyncStore.entries()) {
      expect(value).not.toContain('Ram Bahadur Thapa');
      expect(value).not.toContain('1985-04-12');
      expect(value).not.toContain('patient-name:');
    }
  });

  it('same plaintext encrypted twice produces distinct ciphertexts (fresh IV per write)', async () => {
    const asyncStore = new Map<string, string>();
    const secureStore = new Map<string, string>();

    (SecureStore.getItemAsync as jest.MockedFunction<typeof SecureStore.getItemAsync>).mockImplementation(
      async (key: string) => secureStore.get(key) ?? null,
    );
    (SecureStore.setItemAsync as jest.MockedFunction<typeof SecureStore.setItemAsync>).mockImplementation(
      async (key: string, value: string) => { secureStore.set(key, value); },
    );
    (SecureStore.deleteItemAsync as jest.MockedFunction<typeof SecureStore.deleteItemAsync>).mockImplementation(
      async (key: string) => { secureStore.delete(key); },
    );
    (AsyncStorage.setItem as jest.MockedFunction<typeof AsyncStorage.setItem>).mockImplementation(
      async (key: string, value: string) => { asyncStore.set(key, value); },
    );
    (AsyncStorage.getItem as jest.MockedFunction<typeof AsyncStorage.getItem>).mockImplementation(
      async (key: string) => asyncStore.get(key) ?? null,
    );
    (AsyncStorage.removeItem as jest.MockedFunction<typeof AsyncStorage.removeItem>).mockImplementation(
      async (key: string) => { asyncStore.delete(key); },
    );
    (AsyncStorage.getAllKeys as jest.MockedFunction<typeof AsyncStorage.getAllKeys>).mockImplementation(
      async () => Array.from(asyncStore.keys()),
    );

    resetPhiStorageAdapter();

    const samePlaintext = 'identical-patient-value';
    await phiSet('phr-phi-cipher:record-a', samePlaintext);
    await phiSet('phr-phi-cipher:record-b', samePlaintext);

    const storedA = asyncStore.get('phr-phi-cipher:record-a');
    const storedB = asyncStore.get('phr-phi-cipher:record-b');

    expect(storedA).toMatch(/^[A-Za-z0-9+/]+=*$/);
    expect(storedB).toMatch(/^[A-Za-z0-9+/]+=*$/);

    expect(storedA).not.toBe(storedB);

    expect(storedA).not.toContain(samePlaintext);
    expect(storedB).not.toContain(samePlaintext);
  });
});
