/**
 * Offline PHI cache for the PHR mobile dashboard.
 *
 * Security posture (PHI cache):
 * - Data is wrapped in a TTL envelope so stale PHI is never served after
 *   the cache window expires (default 8 hours to match a clinical shift).
 * - The envelope includes a schemaVersion field so future migrations can
 *   detect and discard incompatible payloads.
 * - Encryption at rest: PHI is encrypted via AES-256-GCM using the
 *   `phiEncryptedStorage` adapter. The key is held in the OS keychain
 *   via expo-secure-store; AsyncStorage holds ciphertext only.
 *   Consent revocation must call `clearDashboardOffline()` directly.
 *
 * NEVER call `AsyncStorage.setItem` with PHI outside this module.
 */
import { phiGet, phiRemove, phiSet } from './phiEncryptedStorage';
import type { MobileDashboard } from '../types';

const DASHBOARD_KEY = 'phr-mobile-dashboard';
const SCHEMA_VERSION = 1;

/** Default cache lifetime: 8 hours (one clinical shift). */
const DEFAULT_TTL_MS = 8 * 60 * 60 * 1000;

interface DashboardCacheEnvelope {
  schemaVersion: number;
  savedAt: number;
  ttlMs: number;
  data: MobileDashboard;
}

export async function saveDashboardOffline(
  dashboard: MobileDashboard,
  ttlMs: number = DEFAULT_TTL_MS,
): Promise<void> {
  const envelope: DashboardCacheEnvelope = {
    schemaVersion: SCHEMA_VERSION,
    savedAt: Date.now(),
    ttlMs,
    data: dashboard,
  };
  await phiSet(DASHBOARD_KEY, JSON.stringify(envelope));
}

/**
 * Loads the cached dashboard. Returns `null` if the cache is absent,
 * has an unknown schema version, or has expired — forcing a fresh fetch.
 * Callers must not use a `null` result to serve stale PHI.
 */
export async function loadDashboardOffline(): Promise<MobileDashboard | null> {
  const raw = await phiGet(DASHBOARD_KEY);
  if (!raw) return null;

  let envelope: DashboardCacheEnvelope;
  try {
    envelope = JSON.parse(raw) as DashboardCacheEnvelope;
  } catch {
    // Corrupt payload — discard.
    await clearDashboardOffline();
    return null;
  }

  if (envelope.schemaVersion !== SCHEMA_VERSION) {
    // Schema mismatch — discard so stale structure is not used.
    await clearDashboardOffline();
    return null;
  }

  const ageMs = Date.now() - envelope.savedAt;
  if (ageMs > envelope.ttlMs) {
    // Cache expired — discard PHI proactively.
    await clearDashboardOffline();
    return null;
  }

  return envelope.data;
}

/**
 * Clears the offline PHI cache unconditionally.
 * Must be called on consent revocation or session termination.
 */
export async function clearDashboardOffline(): Promise<void> {
  await phiRemove(DASHBOARD_KEY);
}
