/**
 * TutorPutor Mobile - Secure Key Manager
 *
 * Generates and persists a per-device random encryption key using the
 * platform keychain (iOS Keychain / Android Keystore via react-native-keychain).
 * This key is used to encrypt the MMKV storage instance so that sensitive
 * session data (tokens, tenant ID) is protected at rest.
 *
 * @doc.type module
 * @doc.purpose Secure encryption key lifecycle for MMKV storage
 * @doc.layer product
 * @doc.pattern Adapter
 */

import * as Keychain from 'react-native-keychain';

const KEYCHAIN_SERVICE = 'com.tutorputor.mobile.mmkv';
const KEYCHAIN_USERNAME = 'mmkv-encryption-key';

function randomHex(bytes: number): string {
  const arr = new Uint8Array(bytes);
  // React Native global — available in Hermes / JSC without polyfill
  (globalThis as unknown as { crypto: { getRandomValues: (arr: Uint8Array) => void } })
    .crypto.getRandomValues(arr);
  return Array.from(arr)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Retrieve the per-device MMKV encryption key.
 * On first call a 256-bit key is generated and stored in the system keychain.
 * On subsequent calls the same key is returned from the keychain.
 *
 * Throws if keychain access is denied (e.g. the device requires authentication
 * and biometrics fail). Callers must handle this and show an appropriate UI.
 */
export async function getMmkvEncryptionKey(): Promise<string> {
  const existing = await Keychain.getGenericPassword({
    service: KEYCHAIN_SERVICE,
  });

  if (existing !== false && existing.password) {
    return existing.password;
  }

  // First launch — generate a fresh random 256-bit key (32 bytes → 64 hex chars)
  const key = randomHex(32);

  await Keychain.setGenericPassword(KEYCHAIN_USERNAME, key, {
    service: KEYCHAIN_SERVICE,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });

  return key;
}

/**
 * Delete the stored encryption key. Called on logout to ensure
 * existing MMKV data is unreadable without re-provisioning.
 */
export async function deleteMmkvEncryptionKey(): Promise<void> {
  await Keychain.resetGenericPassword({ service: KEYCHAIN_SERVICE });
}
