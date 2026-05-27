/**
 * Secure session store for PHR mobile.
 *
 * Stores the authenticated `MobileSession` in the OS keychain/keystore via
 * `expo-secure-store`. This replaces any in-memory-only pattern and survives
 * app backgrounding while remaining unavailable to other applications.
 *
 * Security contract:
 * - Session data is stored with `AFTER_FIRST_UNLOCK` accessibility, meaning
 *   it requires the device to be unlocked at least once after boot.
 * - The session envelope is validated on load: expired sessions are discarded.
 * - Logout, consent revocation, and role changes must call `clearMobileSession`.
 * - The session token is intentionally NOT stored in AsyncStorage or
 *   React state beyond the in-memory `MobileSession` object.
 *
 * NEVER store `MobileSession` or any auth token in AsyncStorage directly.
 */

import * as SecureStore from 'expo-secure-store';
import type { MobileSession } from '../types';

const SESSION_KEY = 'phr-mobile-session-v1';

/**
 * Persists the mobile session to the OS secure keychain.
 *
 * @param session  the authenticated session to persist
 */
export async function saveMobileSession(session: MobileSession): Promise<void> {
  await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(session), {
    keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
  });
}

/**
 * Loads the persisted mobile session. Returns `null` when absent or expired.
 * Expired sessions are removed from the keychain proactively.
 *
 * @returns the live session or null
 */
export async function loadMobileSession(): Promise<MobileSession | null> {
  const raw = await SecureStore.getItemAsync(SESSION_KEY);
  if (!raw) return null;

  let session: MobileSession;
  try {
    session = JSON.parse(raw) as MobileSession;
  } catch {
    await clearMobileSession();
    return null;
  }

  if (new Date(session.expiresAt) <= new Date()) {
    // Session has expired — remove it so the next launch starts fresh.
    await clearMobileSession();
    return null;
  }

  return session;
}

/**
 * Removes the mobile session from the OS keychain.
 * Must be called on logout, consent revocation, session expiry, and role/persona change.
 */
export async function clearMobileSession(): Promise<void> {
  await SecureStore.deleteItemAsync(SESSION_KEY);
}
