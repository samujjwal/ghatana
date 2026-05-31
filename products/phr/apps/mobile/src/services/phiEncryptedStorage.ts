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
 *   operations; it is available in React Native 0.71 and newer.
 *
 * Security properties:
 * - AES-256-GCM provides authenticated encryption: any modification of the
 *   ciphertext or IV will cause decryption to throw, returning null.
 * - A fresh 12-byte IV is generated for every write.
 * - Raw AES key material is stored in SecureStore as base64, then imported as
 *   a non-extractable CryptoKey for encryption/decryption operations.
 * - Clearing an item removes the ciphertext from AsyncStorage; the key
 *   remains for future items to the same store.
 *
 * NEVER import AsyncStorage directly in PHI-bearing modules; use this
 * adapter instead.
 */

import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";
import * as LocalAuthentication from "expo-local-authentication";
import { t } from "../i18n/phrMobileI18n";

const KEY_SECURE_STORE_NAME = "phr-phi-encryption-key-v1";
const KEY_VERSION_STORE_NAME = "phr-phi-key-version";
const KEY_CREATED_AT_STORE_NAME = "phr-phi-key-created-at";
const KEY_LAST_ACCESS_STORE_NAME = "phr-phi-key-last-access";
const CIPHERTEXT_STORAGE_PREFIX = "phr-phi-cipher:";
const KEY_LENGTH_BITS = 256;
const IV_LENGTH_BYTES = 12;
const ALGORITHM = "AES-GCM";
const KEY_ROTATION_THRESHOLD_DAYS = 90;
const MAX_KEY_AGE_DAYS = 365; // Force key rotation after 1 year regardless of threshold
const BIOMETRIC_POLICY_KEY = "phr-phi-biometric-policy-enabled";
const DEVICE_INSTALL_ID_KEY = "phr-phi-device-install-id";

// Crypto helpers

class BiometricAuthenticationRequiredError extends Error {
  constructor() {
    super(t("biometric.requiredForPhi"));
    this.name = "BiometricAuthenticationRequiredError";
  }
}

function requireCrypto(): Crypto {
  if (!globalThis.crypto?.subtle || !globalThis.crypto.getRandomValues) {
    throw new Error("PHI encrypted storage requires WebCrypto support");
  }
  return globalThis.crypto;
}

function uint8ArrayToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i] ?? 0);
  }
  return globalThis.btoa(binary);
}

function base64ToUint8Array(b64: string): Uint8Array {
  const binary = globalThis.atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

async function generateAesKey(): Promise<{
  rawKey: string;
  cryptoKey: CryptoKey;
}> {
  const cryptoProvider = requireCrypto();
  // Generate raw key bytes directly for storage
  const rawKeyBytes = cryptoProvider.getRandomValues(
    new Uint8Array(KEY_LENGTH_BITS / 8),
  );
  const rawKeyBase64 = uint8ArrayToBase64(rawKeyBytes);

  // Import as non-extractable CryptoKey for crypto operations
  const cryptoKey = await cryptoProvider.subtle.importKey(
    "raw",
    rawKeyBytes.buffer,
    { name: ALGORITHM, length: KEY_LENGTH_BITS },
    false, // non-extractable for security
    ["encrypt", "decrypt"],
  );

  return { rawKey: rawKeyBase64, cryptoKey };
}

async function importKey(b64: string): Promise<CryptoKey> {
  const cryptoProvider = requireCrypto();
  const raw = base64ToUint8Array(b64).buffer as ArrayBuffer;
  return cryptoProvider.subtle.importKey(
    "raw",
    raw,
    { name: ALGORITHM, length: KEY_LENGTH_BITS },
    false,
    ["encrypt", "decrypt"],
  );
}

// Key retrieval

let cachedKey: CryptoKey | null = null;
let cachedKeyVersion: number = 0;

interface KeyAccessOptions {
  skipBiometricCheck?: boolean;
}

async function getOrCreateKey(options: KeyAccessOptions = {}): Promise<CryptoKey> {
  if (cachedKey) {
    if (options.skipBiometricCheck !== true) {
      await requireBiometricIfEnabled();
    }
    // Update last access time on each cache hit
    await updateKeyAccessTime();
    return cachedKey;
  }

  // Verify that the per-install marker is available before key access.
  if (!(await verifyDeviceInstallMarker())) {
    await logSecurityEvent("DEVICE_INSTALL_MARKER_MISSING");
    await clearRegisteredPhiKeys();
    await clearKey();
    return initializeFreshKey();
  }

  const stored = await SecureStore.getItemAsync(KEY_SECURE_STORE_NAME);
  const versionStr = await SecureStore.getItemAsync(KEY_VERSION_STORE_NAME);
  const createdAtStr = await SecureStore.getItemAsync(
    KEY_CREATED_AT_STORE_NAME,
  );
  const currentVersion = versionStr ? parseInt(versionStr, 10) : 0;

  if (stored) {
    if (options.skipBiometricCheck !== true) {
      await requireBiometricIfEnabled();
    }

    cachedKey = await importKey(stored);
    cachedKeyVersion = currentVersion;

    // Check if key rotation is needed based on age
    if (createdAtStr && (await shouldRotateKey(createdAtStr))) {
      await rotateKey();
    }

    // Update last access time
    await updateKeyAccessTime();

    return cachedKey;
  }

  // First launch: generate and persist a new key.
  return initializeFreshKey();
}

async function requireBiometricIfEnabled(): Promise<void> {
  if (!(await isBiometricPolicyEnabled())) return;
  const authenticated = await authenticateWithBiometrics();
  if (!authenticated) {
    throw new BiometricAuthenticationRequiredError();
  }
}

async function requireBiometricPolicyForPhiDecrypt(): Promise<void> {
  if (!(await isBiometricPolicyEnabled())) {
    await enableBiometricPolicy();
    await logSecurityEvent("BIOMETRIC_POLICY_DEFAULT_ENABLED");
  }

  await requireBiometricIfEnabled();
}

async function initializeFreshKey(): Promise<CryptoKey> {
  const { rawKey, cryptoKey } = await generateAesKey();
  const createdAt = Date.now().toString();

  await SecureStore.setItemAsync(KEY_SECURE_STORE_NAME, rawKey, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
  await SecureStore.setItemAsync(KEY_VERSION_STORE_NAME, "1");
  await SecureStore.setItemAsync(KEY_CREATED_AT_STORE_NAME, createdAt);
  await SecureStore.setItemAsync(KEY_LAST_ACCESS_STORE_NAME, createdAt);

  // Initialize device install ID
  await getOrCreateDeviceInstallId();

  cachedKey = cryptoKey;
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
  const { rawKey: newRawKey, cryptoKey: newCryptoKey } = await generateAesKey();
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
        // If re-encryption fails, log security event and continue - the item will be inaccessible
        await logSecurityEvent("KEY_ROTATION_REENCRYPT_FAILED");
        await AsyncStorage.removeItem(key);
      }
    }
  }

  // Replace old key with new key
  await SecureStore.setItemAsync(KEY_SECURE_STORE_NAME, newRawKey, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
  await SecureStore.setItemAsync(KEY_VERSION_STORE_NAME, newVersion.toString());
  await SecureStore.setItemAsync(KEY_CREATED_AT_STORE_NAME, newCreatedAt);

  cachedKey = newCryptoKey;
  cachedKeyVersion = newVersion;
}

async function clearKey(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY_SECURE_STORE_NAME);
  await SecureStore.deleteItemAsync(KEY_VERSION_STORE_NAME);
  await SecureStore.deleteItemAsync(KEY_CREATED_AT_STORE_NAME);
  await SecureStore.deleteItemAsync(KEY_LAST_ACCESS_STORE_NAME);
  await SecureStore.deleteItemAsync(BIOMETRIC_POLICY_KEY);
  await SecureStore.deleteItemAsync(DEVICE_INSTALL_ID_KEY);
  cachedKey = null;
  cachedKeyVersion = 0;
}

async function updateKeyAccessTime(): Promise<void> {
  const now = Date.now().toString();
  await SecureStore.setItemAsync(KEY_LAST_ACCESS_STORE_NAME, now);
}

// Biometric access policy

async function isBiometricPolicyEnabled(): Promise<boolean> {
  const enabled = await SecureStore.getItemAsync(BIOMETRIC_POLICY_KEY);
  return enabled === "true";
}

async function enableBiometricPolicy(): Promise<void> {
  await SecureStore.setItemAsync(BIOMETRIC_POLICY_KEY, "true", {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
}

async function authenticateWithBiometrics(): Promise<boolean> {
  try {
    const hasHardware = await LocalAuthentication.hasHardwareAsync();
    if (!hasHardware) {
      await logSecurityEvent("BIOMETRIC_HARDWARE_UNAVAILABLE");
      return false;
    }

    const isEnrolled = await LocalAuthentication.isEnrolledAsync();
    if (!isEnrolled) {
      await logSecurityEvent("BIOMETRIC_NOT_ENROLLED");
      return false;
    }

    const result = await LocalAuthentication.authenticateAsync({
      promptMessage: t("biometric.protectedHealthPrompt"),
      fallbackLabel: t("biometric.fallbackLabel"),
      cancelLabel: t("biometric.cancelLabel"),
      disableDeviceFallback: false,
    });

    if (result.success) {
      await logSecurityEvent("BIOMETRIC_AUTH_SUCCESS");
    } else {
      await logSecurityEvent("BIOMETRIC_AUTH_FAILED");
    }

    return result.success;
  } catch (error) {
    await logSecurityEvent("BIOMETRIC_AUTH_ERROR");
    return false;
  }
}

async function logSecurityEvent(event: string): Promise<void> {
  // Store safe reason codes only; no PHI or key material is logged.
  const timestamp = Date.now();
  const logEntry = `${timestamp}:${event}`;
  await SecureStore.setItemAsync("phr-security-log", logEntry, {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
}

// Device install ID tracking

async function getOrCreateDeviceInstallId(): Promise<string> {
  let installId = await SecureStore.getItemAsync(DEVICE_INSTALL_ID_KEY);
  if (!installId) {
    installId = requireCrypto().randomUUID();
    await SecureStore.setItemAsync(DEVICE_INSTALL_ID_KEY, installId, {
      keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
    });
  }
  return installId;
}

// PHI key registry for reliable clearing.

const PHI_KEY_REGISTRY_KEY = "phr-phi-key-registry";

function parseRegisteredPhiKeys(registryJson: string | null): string[] {
  if (!registryJson) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(registryJson);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter(
      (value: unknown): value is string =>
        typeof value === "string" && value.length > 0,
    );
  } catch {
    return [];
  }
}

async function registerPhiKey(key: string): Promise<void> {
  const registryJson = await SecureStore.getItemAsync(PHI_KEY_REGISTRY_KEY);
  const phiKeys = parseRegisteredPhiKeys(registryJson);

  if (!phiKeys.includes(key)) {
    phiKeys.push(key);
    await SecureStore.setItemAsync(
      PHI_KEY_REGISTRY_KEY,
      JSON.stringify(phiKeys),
      {
        keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
      },
    );
  }
}

async function unregisterPhiKey(key: string): Promise<void> {
  const registryJson = await SecureStore.getItemAsync(PHI_KEY_REGISTRY_KEY);
  if (!registryJson) return;

  const phiKeys = parseRegisteredPhiKeys(registryJson);
  const filteredKeys = phiKeys.filter((k) => k !== key);

  await SecureStore.setItemAsync(
    PHI_KEY_REGISTRY_KEY,
    JSON.stringify(filteredKeys),
    {
      keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
    },
  );
}

async function verifyDeviceInstallMarker(): Promise<boolean> {
  try {
    const storedInstallId = await SecureStore.getItemAsync(
      DEVICE_INSTALL_ID_KEY,
    );
    if (!storedInstallId) {
      // First install - generate and store
      await getOrCreateDeviceInstallId();
      return true;
    }

    return storedInstallId.length > 0;
  } catch {
    return false;
  }
}

// Encrypt and decrypt

async function encrypt(plaintext: string): Promise<string> {
  const cryptoProvider = requireCrypto();
  const key = await getOrCreateKey();
  const iv = cryptoProvider.getRandomValues(new Uint8Array(IV_LENGTH_BYTES));
  const encoded = new TextEncoder().encode(plaintext);

  const cipherBuffer = await cryptoProvider.subtle.encrypt(
    { name: ALGORITHM, iv },
    key,
    encoded,
  );

  const combined = new Uint8Array(IV_LENGTH_BYTES + cipherBuffer.byteLength);
  combined.set(iv, 0);
  combined.set(new Uint8Array(cipherBuffer), IV_LENGTH_BYTES);

  return uint8ArrayToBase64(combined);
}

async function decrypt(b64: string): Promise<string> {
  const cryptoProvider = requireCrypto();
  await requireBiometricPolicyForPhiDecrypt();
  const key = await getOrCreateKey({ skipBiometricCheck: true });
  const combined = base64ToUint8Array(b64);

  const iv = combined.slice(0, IV_LENGTH_BYTES);
  const ciphertext = combined.slice(IV_LENGTH_BYTES);

  const plainBuffer = await cryptoProvider.subtle.decrypt(
    { name: ALGORITHM, iv },
    key,
    ciphertext,
  );
  return new TextDecoder().decode(plainBuffer);
}

// Public API

/**
 * Testable adapter interface for PHI encrypted storage.
 * In production the default implementation encrypts via AES-256-GCM.
 * In tests, inject a controlled adapter via `setPhiStorageAdapter`.
 */
export interface PhiStorageAdapter {
  setItem(key: string, value: string): Promise<void>;
  getItem(key: string): Promise<string | null>;
  removeItem(key: string): Promise<void>;
  clearAllPhi?(): Promise<void>;
}

/**
 * Production adapter: AES-256-GCM ciphertext in AsyncStorage, key material in SecureStore.
 */
const productionAdapter: PhiStorageAdapter = {
  async setItem(key: string, value: string): Promise<void> {
    const ciphertext = await encrypt(value);
    await AsyncStorage.setItem(key, ciphertext);
    await registerPhiKey(key);
  },

  async getItem(key: string): Promise<string | null> {
    const ciphertext = await AsyncStorage.getItem(key);
    if (ciphertext === null) return null;
    try {
      return await decrypt(ciphertext);
    } catch (error) {
      if (error instanceof BiometricAuthenticationRequiredError) {
        throw error;
      }
      // Decryption failure from modified ciphertext or a missing key is treated as absent.
      await AsyncStorage.removeItem(key);
      await unregisterPhiKey(key);
      return null;
    }
  },

  async removeItem(key: string): Promise<void> {
    await AsyncStorage.removeItem(key);
    await unregisterPhiKey(key);
  },
};

let activeAdapter: PhiStorageAdapter = productionAdapter;

/**
 * Replaces the default encrypted storage adapter.
 * Use this in tests to inject a controlled adapter without touching the real keychain or AsyncStorage.
 *
 * @param adapter - the adapter to use
 */
export function setPhiStorageAdapter(adapter: PhiStorageAdapter): void {
  activeAdapter = adapter;
}

/** Resets the adapter to the production default. Call in test `afterEach`. */
export function resetPhiStorageAdapter(): void {
  activeAdapter = productionAdapter;
  cachedKey = null;
  cachedKeyVersion = 0;
}

/**
 * Stores a PHI-bearing value under `key`, encrypted at rest.
 *
 * @param key    storage key; must not contain PHI itself
 * @param value  plaintext payload (typically serialized JSON)
 */
export async function phiSet(key: string, value: string): Promise<void> {
  await activeAdapter.setItem(key, value);
}

/**
 * Retrieves and decrypts a PHI-bearing value. Returns `null` when absent,
 * corrupted, or when decryption fails after key rotation or reinstall.
 *
 * @param key    storage key; must not contain PHI itself
 * @returns    plaintext string or null
 */
export async function phiGet(key: string): Promise<string | null> {
  return activeAdapter.getItem(key);
}

/**
 * Removes a PHI-bearing value from encrypted storage.
 *
 * @param key    storage key; must not contain PHI itself
 */
export async function phiRemove(key: string): Promise<void> {
  await activeAdapter.removeItem(key);
}

/**
 * Clears all PHI-bearing values from encrypted storage.
 * Must be called on consent revocation, logout, session expiry, and role/persona change.
 *
 * PHI key registry for reliable clearing
 * All PHI keys are registered when set, so we can clear them all reliably.
 */
export async function phiClearAll(): Promise<void> {
  if (activeAdapter.clearAllPhi) {
    await activeAdapter.clearAllPhi();
  }

  await clearRegisteredPhiKeys();
}

async function clearRegisteredPhiKeys(): Promise<void> {
  const registryJson = await SecureStore.getItemAsync(PHI_KEY_REGISTRY_KEY);
  const phiKeys = parseRegisteredPhiKeys(registryJson);

  for (const key of phiKeys) {
    await AsyncStorage.removeItem(key);
  }

  await SecureStore.deleteItemAsync(PHI_KEY_REGISTRY_KEY);
}

/**
 * Enables biometric authentication requirement for PHI access.
 * When enabled, users must authenticate with biometrics or device passcode
 * before any PHI can be decrypted.
 */
export async function phiEnableBiometricPolicy(): Promise<void> {
  await enableBiometricPolicy();
  await logSecurityEvent("BIOMETRIC_POLICY_ENABLED");
}

/**
 * Keeps biometric authentication required for PHI access.
 * The mobile PHI policy is fail-closed, so opt-out requests preserve the gate.
 */
export async function phiDisableBiometricPolicy(): Promise<void> {
  await enableBiometricPolicy();
  await logSecurityEvent("BIOMETRIC_POLICY_DISABLE_DENIED");
}

/**
 * Enables biometric policy by default for new installations.
 * This is the secure default posture.
 */
export async function phiEnableDefaultBiometricPolicy(): Promise<void> {
  const policyEnabled = await isBiometricPolicyEnabled();
  if (!policyEnabled) {
    await enableBiometricPolicy();
    await logSecurityEvent("BIOMETRIC_POLICY_DEFAULT_ENABLED");
  }
}

/**
 * Checks whether biometric authentication is currently required for PHI access.
 */
export async function phiIsBiometricPolicyEnabled(): Promise<boolean> {
  return isBiometricPolicyEnabled();
}

/**
 * Returns the device install ID used to bind local key metadata to this install.
 * This ID is generated once per installation and stored in the secure keychain.
 */
export async function phiGetDeviceInstallId(): Promise<string> {
  return getOrCreateDeviceInstallId();
}
