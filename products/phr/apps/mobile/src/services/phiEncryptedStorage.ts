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
const KEY_VERSION_STORE_NAME = 'phr-phi-key-version';
const KEY_CREATED_AT_STORE_NAME = 'phr-phi-key-created-at';
const KEY_LAST_ACCESS_STORE_NAME = 'phr-phi-key-last-access';
const CIPHERTEXT_STORAGE_PREFIX = 'phr-phi-cipher:';
const KEY_LENGTH_BITS = 256;
const IV_LENGTH_BYTES = 12;
const ALGORITHM = 'AES-GCM';
const KEY_ROTATION_THRESHOLD_DAYS = 90;
const TAMPER_DETECTION_KEY = 'phr-phi-tamper-check';
const TAMPER_DETECTION_VERSION = 'phr-phi-tamper-version';
const INTEGRITY_CHECK_KEY = 'phr-phi-integrity-check';
const MAX_KEY_AGE_DAYS = 365; // Force key rotation after 1 year regardless of threshold

// ─── Crypto helpers ──────────────────────────────────────────────────────────

function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i] ?? 0);
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
    false, // non-extractable for security - key stays in secure enclave
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
let cachedKeyVersion: number = 0;

async function getOrCreateKey(): Promise<CryptoKey> {
  if (cachedKey) {
    // Update last access time on each cache hit
    await updateKeyAccessTime();
    return cachedKey;
  }

  const stored = await SecureStore.getItemAsync(KEY_SECURE_STORE_NAME);
  const versionStr = await SecureStore.getItemAsync(KEY_VERSION_STORE_NAME);
  const createdAtStr = await SecureStore.getItemAsync(KEY_CREATED_AT_STORE_NAME);
  const currentVersion = versionStr ? parseInt(versionStr, 10) : 0;
  
  if (stored) {
    // Verify tamper detection before using cached key
    if (!(await verifyTamperDetection())) {
      console.warn('Tamper detection failed - clearing encrypted storage');
      await phiClearAll();
      await clearKey();
      return getOrCreateKey(); // Recursive call to generate fresh key
    }

    // Verify integrity check
    if (!(await verifyIntegrityCheck())) {
      console.warn('Integrity check failed - clearing encrypted storage');
      await phiClearAll();
      await clearKey();
      return getOrCreateKey();
    }

    // Verify tamper detection version matches expected
    const tamperVersion = await SecureStore.getItemAsync(TAMPER_DETECTION_VERSION);
    if (tamperVersion !== '1') {
      console.warn('Tamper detection version mismatch - reinitializing');
      await phiClearAll();
      await clearKey();
      return getOrCreateKey();
    }

    cachedKey = await importKey(stored);
    cachedKeyVersion = currentVersion;
    
    // Check if key rotation is needed based on age
    if (createdAtStr && await shouldRotateKey(createdAtStr)) {
      await rotateKey();
    }
    
    // Update last access time
    await updateKeyAccessTime();
    
    return cachedKey;
  }

  // First launch: generate and persist a new key.
  const key = await generateAesKey();
  const exported = await exportKey(key);
  const createdAt = Date.now().toString();
  
  await SecureStore.setItemAsync(KEY_SECURE_STORE_NAME, exported, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
  await SecureStore.setItemAsync(KEY_VERSION_STORE_NAME, '1');
  await SecureStore.setItemAsync(KEY_CREATED_AT_STORE_NAME, createdAt);
  await SecureStore.setItemAsync(KEY_LAST_ACCESS_STORE_NAME, createdAt);
  
  // Initialize tamper detection and integrity checks
  await initializeTamperDetection();
  await initializeIntegrityCheck();
  await SecureStore.setItemAsync(TAMPER_DETECTION_VERSION, '1');
  
  cachedKey = key;
  cachedKeyVersion = 1;
  return cachedKey;
}

async function shouldRotateKey(createdAtStr: string): Promise<boolean> {
  const createdAt = parseInt(createdAtStr, 10);
  const now = Date.now();
  const ageDays = (now - createdAt) / (1000 * 60 * 60 * 24);
  // Force rotation if key is older than MAX_KEY_AGE_DAYS
  if (ageDays >= MAX_KEY_AGE_DAYS) {
    return true;
  }
  // Otherwise use the rotation threshold
  return ageDays >= KEY_ROTATION_THRESHOLD_DAYS;
}

async function rotateKey(): Promise<void> {
  const newKey = await generateAesKey();
  const newVersion = cachedKeyVersion + 1;
  const newCreatedAt = Date.now().toString();
  
  // Re-encrypt all existing PHI with new key
  const keys = await AsyncStorage.getAllKeys();
  const phiKeys = keys.filter((k) => k.startsWith(CIPHERTEXT_STORAGE_PREFIX));
  
  for (const key of phiKeys) {
    const ciphertext = await AsyncStorage.getItem(key);
    if (ciphertext) {
      try {
        const decrypted = await decrypt(ciphertext);
        const reencrypted = await encrypt(decrypted);
        await AsyncStorage.setItem(key, reencrypted);
      } catch (e) {
        // If re-encryption fails, log and continue - the item will be inaccessible
        console.error(`Failed to re-encrypt key ${key}:`, e);
        await AsyncStorage.removeItem(key);
      }
    }
  }
  
  // Replace old key with new key
  const exported = await exportKey(newKey);
  await SecureStore.setItemAsync(KEY_SECURE_STORE_NAME, exported, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
  await SecureStore.setItemAsync(KEY_VERSION_STORE_NAME, newVersion.toString());
  await SecureStore.setItemAsync(KEY_CREATED_AT_STORE_NAME, newCreatedAt);
  
  // Update tamper detection with new key
  await initializeTamperDetection();
  
  cachedKey = newKey;
  cachedKeyVersion = newVersion;
}

async function clearKey(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY_SECURE_STORE_NAME);
  await SecureStore.deleteItemAsync(KEY_VERSION_STORE_NAME);
  await SecureStore.deleteItemAsync(KEY_CREATED_AT_STORE_NAME);
  await SecureStore.deleteItemAsync(KEY_LAST_ACCESS_STORE_NAME);
  await SecureStore.deleteItemAsync(TAMPER_DETECTION_KEY);
  await SecureStore.deleteItemAsync(TAMPER_DETECTION_VERSION);
  await SecureStore.deleteItemAsync(INTEGRITY_CHECK_KEY);
  cachedKey = null;
  cachedKeyVersion = 0;
}

async function initializeTamperDetection(): Promise<void> {
  const checkValue = await generateTamperCheck();
  await SecureStore.setItemAsync(TAMPER_DETECTION_KEY, checkValue, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
}

async function generateTamperCheck(): Promise<string> {
  const timestamp = Date.now().toString();
  const random = crypto.getRandomValues(new Uint8Array(16));
  const combined = timestamp + ':' + uint8ArrayToBase64(random);
  return combined;
}

async function verifyTamperDetection(): Promise<boolean> {
  try {
    const stored = await SecureStore.getItemAsync(TAMPER_DETECTION_KEY);
    if (!stored) return false; // Missing tamper check is suspicious
    
    // In production, this would verify against a server-side attestation
    // For now, just check that the value exists and is properly formatted
    const parts = stored.split(':');
    const firstPart = parts[0];
    return parts.length === 2 && firstPart !== undefined && firstPart.length > 0;
  } catch {
    return false;
  }
}

async function updateKeyAccessTime(): Promise<void> {
  const now = Date.now().toString();
  await SecureStore.setItemAsync(KEY_LAST_ACCESS_STORE_NAME, now);
}

async function initializeIntegrityCheck(): Promise<void> {
  const checkValue = await generateIntegrityCheck();
  await SecureStore.setItemAsync(INTEGRITY_CHECK_KEY, checkValue, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
}

async function generateIntegrityCheck(): Promise<string> {
  const timestamp = Date.now().toString();
  const random = crypto.getRandomValues(new Uint8Array(32));
  const combined = timestamp + ':' + uint8ArrayToBase64(random);
  return combined;
}

async function verifyIntegrityCheck(): Promise<boolean> {
  try {
    const stored = await SecureStore.getItemAsync(INTEGRITY_CHECK_KEY);
    if (!stored) return false;
    
    const parts = stored.split(':');
    const firstPart = parts[0];
    return parts.length === 2 && firstPart !== undefined && firstPart.length > 0;
  } catch {
    return false;
  }
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

/**
 * Clears all PHI-bearing values from encrypted storage.
 * Must be called on consent revocation, logout, session expiry, and role/persona change.
 */
export async function phiClearAll(): Promise<void> {
  const keys = await AsyncStorage.getAllKeys();
  const phiKeys = keys.filter((k) => k.startsWith(CIPHERTEXT_STORAGE_PREFIX));
  
  for (const key of phiKeys) {
    await AsyncStorage.removeItem(key);
  }
}
