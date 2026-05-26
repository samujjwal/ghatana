/**
 * Offline PHI cache for the PHR mobile dashboard.
 *
 * Security posture (PHI cache):
 * - Data is wrapped in a TTL envelope so stale PHI is never served after
 *   the cache window expires (default 8 hours to match a clinical shift).
 * - The envelope includes a schemaVersion field so future migrations can
 *   detect and discard incompatible payloads.
 * - Encryption at rest: this file is the canonical location for adding
 *   react-native-encrypted-storage once it is provisioned in the build.
 *   Until then, TTL expiry is the primary PHI protection mechanism.
 *   Consent revocation must call `clearDashboardOffline()` directly.
 *
 * NEVER call `AsyncStorage.setItem` with PHI outside this module.
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
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
  await AsyncStorage.setItem(DASHBOARD_KEY, JSON.stringify(envelope));
}

/**
 * Loads the cached dashboard. Returns `null` if the cache is absent,
 * has an unknown schema version, or has expired — forcing a fresh fetch.
 * Callers must not use a `null` result to serve stale PHI.
 */
export async function loadDashboardOffline(): Promise<MobileDashboard | null> {
  const raw = await AsyncStorage.getItem(DASHBOARD_KEY);
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
  await AsyncStorage.removeItem(DASHBOARD_KEY);
}
