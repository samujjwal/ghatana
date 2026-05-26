/**
 * PHI Encrypted Storage Adapter.
 *
 * Implements AES-256-GCM authenticated encryption for PHI data at rest.
 *
 * Architecture:
 * - A per-installation AES-256 symmetric key is generated once and stored
 *   in the OS keychain/keystore via `expo-secure-store` (hardware-backed on
 *   supported devices).
 * - The encrypted ciphertext (IV + tag + payload) is stored in
 *   `@react-native-async-storage/async-storage`.
 * - The WebCrypto API (`crypto.subtle`) is used for all cryptographic
 *   operations; it is available in React Native ≥ 0.71.
 *
 * Security properties:
 * - AES-256-GCM provides authenticated encryption: any tampering with the
 *   ciphertext or IV will cause decryption to throw, returning null.
 * - A fresh 12-byte IV is generated for every write.
 * - The encryption key never leaves the secure keychain; AsyncStorage holds
 *   only ciphertext.
 * - Clearing an item removes the ciphertext from AsyncStorage; the key
 *   remains for future items to the same store.
 *
 * NEVER import AsyncStorage directly in PHI-bearing modules; use this
 * adapter instead.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';

const KEY_SECURE_STORE_NAME = 'phr-phi-encryption-key-v1';
const KEY_LENGTH_BITS = 256;
const IV_LENGTH_BYTES = 12;
const ALGORITHM = 'AES-GCM';

// ─── Crypto helpers ──────────────────────────────────────────────────────────

function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

async function generateAesKey(): Promise<CryptoKey> {
  return crypto.subtle.generateKey(
    { name: ALGORITHM, length: KEY_LENGTH_BITS },
    true, // extractable so we can export and store it
    ['encrypt', 'decrypt'],
  );
}

async function exportKey(key: CryptoKey): Promise<string> {
  const raw = await crypto.subtle.exportKey('raw', key);
  return uint8ArrayToBase64(new Uint8Array(raw));
}

async function importKey(b64: string): Promise<CryptoKey> {
  const raw = base64ToUint8Array(b64).buffer as ArrayBuffer;
  return crypto.subtle.importKey('raw', raw, { name: ALGORITHM, length: KEY_LENGTH_BITS }, false, ['encrypt', 'decrypt']);
}

// ─── Key retrieval (create-once, reuse forever) ───────────────────────────────

let cachedKey: CryptoKey | null = null;

async function getOrCreateKey(): Promise<CryptoKey> {
  if (cachedKey) return cachedKey;

  const stored = await SecureStore.getItemAsync(KEY_SECURE_STORE_NAME);
  if (stored) {
    cachedKey = await importKey(stored);
    return cachedKey;
  }

  // First launch: generate and persist a new key.
  const key = await generateAesKey();
  const exported = await exportKey(key);
  await SecureStore.setItemAsync(KEY_SECURE_STORE_NAME, exported, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
  cachedKey = key;
  return cachedKey;
}

// ─── Encrypt / decrypt ────────────────────────────────────────────────────────

async function encrypt(plaintext: string): Promise<string> {
  const key = await getOrCreateKey();
  const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH_BYTES));
  const encoded = new TextEncoder().encode(plaintext);

  const cipherBuffer = await crypto.subtle.encrypt({ name: ALGORITHM, iv }, key, encoded);

  const combined = new Uint8Array(IV_LENGTH_BYTES + cipherBuffer.byteLength);
  combined.set(iv, 0);
  combined.set(new Uint8Array(cipherBuffer), IV_LENGTH_BYTES);

  return uint8ArrayToBase64(combined);
}

async function decrypt(b64: string): Promise<string> {
  const key = await getOrCreateKey();
  const combined = base64ToUint8Array(b64);

  const iv = combined.slice(0, IV_LENGTH_BYTES);
  const ciphertext = combined.slice(IV_LENGTH_BYTES);

  const plainBuffer = await crypto.subtle.decrypt({ name: ALGORITHM, iv }, key, ciphertext);
  return new TextDecoder().decode(plainBuffer);
}

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Testable adapter interface for PHI encrypted storage.
 * In production the default implementation encrypts via AES-256-GCM.
 * In tests, inject a mock adapter via `setPhiStorageAdapter`.
 */
export interface PhiStorageAdapter {
  setItem(key: string, value: string): Promise<void>;
  getItem(key: string): Promise<string | null>;
  removeItem(key: string): Promise<void>;
}

/**
 * Production adapter: AES-256-GCM ciphertext → AsyncStorage, key → SecureStore.
 */
const productionAdapter: PhiStorageAdapter = {
  async setItem(key: string, value: string): Promise<void> {
    const ciphertext = await encrypt(value);
    await AsyncStorage.setItem(key, ciphertext);
  },

  async getItem(key: string): Promise<string | null> {
    const ciphertext = await AsyncStorage.getItem(key);
    if (ciphertext === null) return null;
    try {
      return await decrypt(ciphertext);
    } catch {
      // Decryption failure (tampered, wrong key after reinstall, etc.) — treat as absent.
      await AsyncStorage.removeItem(key);
      return null;
    }
  },

  async removeItem(key: string): Promise<void> {
    await AsyncStorage.removeItem(key);
  },
};

let activeAdapter: PhiStorageAdapter = productionAdapter;

/**
 * Replaces the default encrypted storage adapter.
 * Use this in tests to inject a mock without touching the real keychain or AsyncStorage.
 *
 * @param adapter - the adapter to use
 */
export function setPhiStorageAdapter(adapter: PhiStorageAdapter): void {
  activeAdapter = adapter;
}

/** Resets the adapter to the production default. Call in test `afterEach`. */
export function resetPhiStorageAdapter(): void {
  activeAdapter = productionAdapter;
}

/**
 * Stores a PHI-bearing value under `key`, encrypted at rest.
 *
 * @param key    storage key — must not contain PHI itself
 * @param value  plaintext payload (typically serialized JSON)
 */
export async function phiSet(key: string, value: string): Promise<void> {
  await activeAdapter.setItem(key, value);
}

/**
 * Retrieves and decrypts a PHI-bearing value. Returns `null` when absent,
 * corrupted, or when decryption fails (key rotation, tampered ciphertext).
 *
 * @param key  storage key used during `phiSet`
 * @returns    plaintext string or null
 */
export async function phiGet(key: string): Promise<string | null> {
  return activeAdapter.getItem(key);
}

/**
 * Removes a PHI-bearing value from encrypted storage.
 *
 * @param key  storage key to remove
 */
export async function phiRemove(key: string): Promise<void> {
  await activeAdapter.removeItem(key);
}
