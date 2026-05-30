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
/**
 * G11-T07: Mobile offline cache telemetry without PHI.
 * Emits cache hit/miss/stale counters without including any PHI.
 */
function emitCacheMetric(
  operation: 'hit' | 'miss' | 'stale' | 'session_mismatch' | 'schema_mismatch' | 'corrupt',
  sessionIdentity: SessionIdentity
): void {
  // In a real implementation, this would call a telemetry service.
  // For now, we log at debug level without PHI.
  console.debug(`[phr.cache] operation=${operation}, role=${sessionIdentity.role}, tenantId=${sessionIdentity.tenantId}`);
}


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

type JsonPrimitive = string | number | boolean | null;
type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };
type JsonObject = { [key: string]: JsonValue };

interface DashboardCacheEnvelope {
  schemaVersion: number;
  savedAt: number;
  ttlMs: number;
  tenantId: string;
  principalId: string;
  role: string;
  data: JsonValue;
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
 * has an unknown schema version, has expired, or session mismatch; this forces a fresh fetch.
 * Callers must not use a `null` result to serve stale PHI.
 */
export async function loadDashboardOffline(
  sessionIdentity: SessionIdentity,
): Promise<MobileDashboard | null> {
  const raw = await phiGet(DASHBOARD_KEY);
  if (!raw) {
    emitCacheMetric('miss', sessionIdentity);
    return null;
  }

  let envelope: DashboardCacheEnvelope;
  try {
    envelope = JSON.parse(raw) as DashboardCacheEnvelope;
  } catch {
    // Corrupt payload; discard.
    emitCacheMetric('corrupt', sessionIdentity);
    await clearDashboardOffline();
    return null;
  }

  if (envelope.schemaVersion !== SCHEMA_VERSION) {
    // Schema mismatch; discard so stale structure is not used.
    emitCacheMetric('schema_mismatch', sessionIdentity);
    await clearDashboardOffline();
    return null;
  }

  // Check session binding - reject cache if session differs
  if (!sessionMatches(envelope, sessionIdentity)) {
    emitCacheMetric('session_mismatch', sessionIdentity);
    await clearDashboardOffline();
    return null;
  }

  const ageMs = Date.now() - envelope.savedAt;
  if (ageMs > envelope.ttlMs) {
    // Cache expired; discard PHI proactively.
    emitCacheMetric('stale', sessionIdentity);
    await clearDashboardOffline();
    return null;
  }

  try {
    emitCacheMetric('hit', sessionIdentity);
    return parseMobileDashboard(envelope.data);
  } catch {
    await clearDashboardOffline();
    return null;
  }
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
  if (!raw) {
    emitCacheMetric('miss', sessionIdentity);
    return null;
  }

  let envelope: DashboardCacheEnvelope;
  try {
    envelope = JSON.parse(raw) as DashboardCacheEnvelope;
  } catch {
    return null;
  }

  return envelope.savedAt;
}

/**
 * Sanitizes restricted fields from dashboard data before caching.
 * Restricted fields are never cached per security policy.
 */
function sanitizeRestrictedFields(dashboard: MobileDashboard): JsonValue {
  return removeRestrictedFields(toJsonValue(dashboard));
}

function toJsonValue(value: unknown): JsonValue {
  if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map(toJsonValue);
  }
  if (typeof value === 'object') {
    const result: JsonObject = {};
    for (const [key, childValue] of Object.entries(value)) {
      result[key] = toJsonValue(childValue);
    }
    return result;
  }
  return null;
}

function isJsonObject(value: JsonValue | undefined): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isString(value: JsonValue | undefined): value is string {
  return typeof value === 'string';
}

function isNumber(value: JsonValue | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isBoolean(value: JsonValue | undefined): value is boolean {
  return typeof value === 'boolean';
}

function parseMobileDashboard(value: JsonValue): MobileDashboard {
  if (!isJsonObject(value)) {
    throw new Error('Invalid cached dashboard envelope');
  }
  const patient = value.patient;
  const records = value.records;
  const consents = value.consents;
  const notifications = value.notifications;
  if (!isJsonObject(patient) || !isString(patient.id) || !isString(patient.name) || !isNumber(patient.age) || !isString(patient.bloodType) || !isString(patient.district)) {
    throw new Error('Invalid cached patient profile');
  }
  if (!Array.isArray(records) || !records.every(isJsonObject)) {
    throw new Error('Invalid cached records');
  }
  if (!Array.isArray(consents) || !consents.every(isJsonObject)) {
    throw new Error('Invalid cached consents');
  }
  if (!Array.isArray(notifications) || !notifications.every(isJsonObject)) {
    throw new Error('Invalid cached notifications');
  }
  return {
    patient: {
      id: patient.id,
      name: patient.name,
      age: patient.age,
      bloodType: patient.bloodType,
      district: patient.district,
    },
    records: records.map((record) => {
      if (!isString(record.id) || !isString(record.title) || !isString(record.summary) || !isString(record.fhirPreview)) {
        throw new Error('Invalid cached record');
      }
      return {
        id: record.id,
        title: record.title,
        summary: record.summary,
        fhirPreview: record.fhirPreview,
      };
    }),
    consents: consents.map((consent) => {
      if (!isString(consent.id) || !isString(consent.grantee) || !isString(consent.purpose) || !isBoolean(consent.active)) {
        throw new Error('Invalid cached consent');
      }
      return {
        id: consent.id,
        grantee: consent.grantee,
        purpose: consent.purpose,
        active: consent.active,
      };
    }),
    notifications: notifications.map((notification) => {
      if (!isString(notification.id) || !isString(notification.title) || !isString(notification.detail)) {
        throw new Error('Invalid cached notification');
      }
      return {
        id: notification.id,
        title: notification.title,
        detail: notification.detail,
      };
    }),
  };
}

function removeRestrictedFields(value: JsonValue): JsonValue {
  if (value === null || typeof value !== 'object') {
    return value;
  }
  
  if (Array.isArray(value)) {
    return value.map(removeRestrictedFields);
  }
  
  const result: { [key: string]: JsonValue } = {};
  for (const [key, childValue] of Object.entries(value)) {
    if (!RESTRICTED_FIELDS.has(key)) {
      result[key] = removeRestrictedFields(childValue);
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
