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
 * - Field classification: Restricted fields (mental health, substance use,
 *   genetic info, reproductive health, HIV status) are never cached.
 *   Identifiable fields (patient ID, name, DOB) are encrypted at rest.
 *   Sensitive PHI fields (diagnosis, medication, lab results) are encrypted.
 * - Session binding: Cache envelope includes tenantId, principalId, role
 *   to reject cache if session differs.
 *
 * NEVER call `AsyncStorage.setItem` with PHI outside this module.
 */
import { phiGet, phiRemove, phiSet } from './phiEncryptedStorage';
import type { MobileDashboard } from '../types';

const DASHBOARD_KEY = 'phr-mobile-dashboard';
const SCHEMA_VERSION = 1;

/** Default cache lifetime: 8 hours (one clinical shift). */
const DEFAULT_TTL_MS = 8 * 60 * 60 * 1000;

/** Restricted PHI fields that must never be cached */
const RESTRICTED_FIELDS = new Set([
  'mentalHealth',
  'substanceUse',
  'geneticInfo',
  'reproductiveHealth',
  'hivStatus',
  'psychiatricHistory',
  'substanceAbuseHistory',
]);

interface DashboardCacheEnvelope {
  schemaVersion: number;
  savedAt: number;
  ttlMs: number;
  tenantId: string;
  principalId: string;
  role: string;
  data: MobileDashboard;
}

export async function saveDashboardOffline(
  dashboard: MobileDashboard,
  ttlMs: number = DEFAULT_TTL_MS,
  sessionIdentity: SessionIdentity,
): Promise<void> {
  // Sanitize restricted fields before caching
  const sanitizedDashboard = sanitizeRestrictedFields(dashboard);
  
  const envelope: DashboardCacheEnvelope = {
    schemaVersion: SCHEMA_VERSION,
    savedAt: Date.now(),
    ttlMs,
    tenantId: sessionIdentity.tenantId,
    principalId: sessionIdentity.principalId,
    role: sessionIdentity.role,
    data: sanitizedDashboard,
  };
  await phiSet(DASHBOARD_KEY, JSON.stringify(envelope));
}

/**
 * Loads the cached dashboard. Returns `null` if the cache is absent,
 * has an unknown schema version, has expired, or session mismatch — forcing a fresh fetch.
 * Callers must not use a `null` result to serve stale PHI.
 */
export async function loadDashboardOffline(
  sessionIdentity: SessionIdentity,
): Promise<MobileDashboard | null> {
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

  // Check session binding - reject cache if session differs
  if (!sessionMatches(envelope, sessionIdentity)) {
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
 * Must be called on consent revocation, logout, session expiry, and role/persona change.
 */
export async function clearDashboardOffline(): Promise<void> {
  await phiRemove(DASHBOARD_KEY);
}

/**
 * Returns the timestamp when the dashboard cache was last saved.
 * Returns `null` if no cache exists.
 */
export async function getDashboardOfflineTimestamp(): Promise<number | null> {
  const raw = await phiGet(DASHBOARD_KEY);
  if (!raw) return null;

  let envelope: DashboardCacheEnvelope;
  try {
    envelope = JSON.parse(raw) as DashboardCacheEnvelope;
  } catch {
    return null;
  }

  return envelope.savedAt;
}

// ==================== Helper Functions ====================

/**
 * Sanitizes restricted fields from dashboard data before caching.
 * Restricted fields are never cached per security policy.
 */
function sanitizeRestrictedFields(dashboard: MobileDashboard): MobileDashboard {
  // Deep clone to avoid mutating original
  const sanitized = JSON.parse(JSON.stringify(dashboard)) as MobileDashboard;
  
  // Remove restricted fields recursively
  return removeRestrictedFields(sanitized);
}

function removeRestrictedFields(obj: any): any {
  if (!obj || typeof obj !== 'object') {
    return obj;
  }
  
  if (Array.isArray(obj)) {
    return obj.map(removeRestrictedFields);
  }
  
  const result: any = {};
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      if (RESTRICTED_FIELDS.has(key)) {
        // Skip restricted field
        continue;
      }
      result[key] = removeRestrictedFields(obj[key]);
    }
  }
  
  return result;
}

/**
 * Checks if the cached session matches the current session.
 */
function sessionMatches(
  envelope: DashboardCacheEnvelope,
  currentSession: SessionIdentity,
): boolean {
  return (
    envelope.tenantId === currentSession.tenantId &&
    envelope.principalId === currentSession.principalId &&
    envelope.role === currentSession.role
  );
}

/**
 * Session identity for cache binding.
 */
export interface SessionIdentity {
  tenantId: string;
  principalId: string;
  role: string;
}
