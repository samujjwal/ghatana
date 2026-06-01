import type {
  MobileConsent,
  MobileDashboard,
  MobileEmergencyData,
  MobileNotificationItem,
  MobilePatientProfile,
  MobileRecord,
  MobileSession,
} from '../types';
import { loadDashboardOffline, saveDashboardOffline, type SessionIdentity } from './offlineStore';
import { clearMobileSession } from './mobileSessionStore';
import { clearMobilePrivacyState } from './mobilePrivacyPlugin';
import { t } from '../i18n/phrMobileI18n';
import { buildMobileHeaders } from './mobileHeaders';

const DASHBOARD_PATH = '/mobile/dashboard';

function getApiBaseUrl(): string {
  return process.env.EXPO_PUBLIC_PHR_API_URL ?? process.env.PHR_API_URL ?? '';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isString(value: unknown): value is string {
  return typeof value === 'string';
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isBoolean(value: unknown): value is boolean {
  return typeof value === 'boolean';
}

function isMobileRole(value: unknown): value is MobileSession['role'] {
  return value === 'patient' || value === 'caregiver' || value === 'fchv' || value === 'clinician' || value === 'admin';
}

function isMobileTier(value: unknown): value is NonNullable<MobileSession['tier']> {
  return value === 'core' || value === 'clinical' || value === 'emergency';
}

function sessionIdentity(session: MobileSession): SessionIdentity {
  assertSessionRequestContext(session);
  return {
    tenantId: session.tenantId,
    principalId: session.principalId,
    role: session.role,
    persona: session.persona,
    tier: session.tier,
    facilityId: session.facilityId,
  };
}

function assertSessionRequestContext(
  session: MobileSession,
): asserts session is MobileSession & { persona: string; tier: string; facilityId: string } {
  if (!session.persona) {
    throw new Error(t('api.missingPersona'));
  }
  if (!session.tier) {
    throw new Error(t('api.missingTier'));
  }
  if (!session.facilityId) {
    throw new Error(t('api.missingFacility'));
  }
}

function mobileJsonHeaders(session: MobileSession): Record<string, string> {
  assertSessionRequestContext(session);
  return {
    ...buildMobileHeaders(session),
    'Content-Type': 'application/json',
  };
}

function assertMobileDashboard(value: unknown): MobileDashboard {
  if (!isRecord(value)) {
    throw new Error(t('api.dashboardNotObject'));
  }

  const { patient, records, consents, notifications } = value;
  if (!isRecord(patient) || !isString(patient.id) || !isString(patient.name) || !isNumber(patient.age) || !isString(patient.bloodType) || !isString(patient.district)) {
    throw new Error(t('api.invalidPatientProfile'));
  }

  if (
    !Array.isArray(records) ||
    !records.every((record) =>
      isRecord(record) &&
      isString(record.id) &&
      isString(record.title) &&
      isString(record.summary) &&
      isString(record.fhirPreview),
    )
  ) {
    throw new Error(t('api.invalidRecords'));
  }

  if (
    !Array.isArray(consents) ||
    !consents.every((consent) =>
      isRecord(consent) &&
      isString(consent.id) &&
      isString(consent.grantee) &&
      isString(consent.purpose) &&
      isBoolean(consent.active),
    )
  ) {
    throw new Error(t('api.invalidConsents'));
  }

  if (
    !Array.isArray(notifications) ||
    !notifications.every((notification) =>
      isRecord(notification) &&
      isString(notification.id) &&
      isString(notification.title) &&
      isString(notification.detail),
    )
  ) {
    throw new Error(t('api.invalidNotifications'));
  }

  const patientProfile: MobilePatientProfile = {
    id: patient.id,
    name: patient.name,
    age: patient.age,
    bloodType: patient.bloodType,
    district: patient.district,
  };
  const dashboardRecords: MobileRecord[] = records.map((record) => {
    if (!isRecord(record) || !isString(record.id) || !isString(record.title) || !isString(record.summary) || !isString(record.fhirPreview)) {
      throw new Error(t('api.invalidRecords'));
    }
    return {
      id: record.id,
      title: record.title,
      summary: record.summary,
      fhirPreview: record.fhirPreview,
    };
  });
  const dashboardConsents: MobileConsent[] = consents.map((consent) => {
    if (!isRecord(consent) || !isString(consent.id) || !isString(consent.grantee) || !isString(consent.purpose) || !isBoolean(consent.active)) {
      throw new Error(t('api.invalidConsents'));
    }
    return {
      id: consent.id,
      grantee: consent.grantee,
      purpose: consent.purpose,
      active: consent.active,
    };
  });
  const dashboardNotifications: MobileNotificationItem[] = notifications.map((notification) => {
    if (!isRecord(notification) || !isString(notification.id) || !isString(notification.title) || !isString(notification.detail)) {
      throw new Error(t('api.invalidNotifications'));
    }
    return {
      id: notification.id,
      title: notification.title,
      detail: notification.detail,
    };
  });

  return {
    patient: patientProfile,
    records: dashboardRecords,
    consents: dashboardConsents,
    notifications: dashboardNotifications,
  };
}

function assertMobileEmergencyData(value: unknown): MobileEmergencyData {
  const payload = isRecord(value) && isRecord(value.emergencyData) ? value.emergencyData : value;
  if (!isRecord(payload)) {
    throw new Error(t('api.emergencyNotObject'));
  }
  const { patientName, bloodType, allergies, medications, emergencyContact } = payload;
  if (
    !isString(patientName) ||
    !isString(bloodType) ||
    !Array.isArray(allergies) ||
    !allergies.every(isString) ||
    !Array.isArray(medications) ||
    !medications.every(isString) ||
    !isString(emergencyContact)
  ) {
    throw new Error(t('api.invalidEmergencyData'));
  }
  return {
    patientName,
    bloodType,
    allergies,
    medications,
    emergencyContact,
  };
}

async function fetchDashboardFromApi(session: MobileSession): Promise<MobileDashboard> {
  const apiBaseUrl = getApiBaseUrl();
  if (!apiBaseUrl) {
    throw new Error(t('api.apiNotConfigured'));
  }

  const response = await fetch(`${apiBaseUrl}${DASHBOARD_PATH}`, {
    headers: buildMobileHeaders(session),
  });
  if (!response.ok) {
    throw new Error(t('api.dashboardRequestFailed', { status: String(response.status) }));
  }

  return assertMobileDashboard(await response.json());
}

export async function fetchMobileDashboard(session: MobileSession): Promise<MobileDashboard> {
  const identity = sessionIdentity(session);

  try {
    const dashboard = await fetchDashboardFromApi(session);
    await saveDashboardOffline(dashboard, undefined, identity);
    return dashboard;
  } catch (error) {
    const cached = await loadDashboardOffline(identity);
    if (cached) {
      return cached;
    }

    throw error;
  }
}

export async function syncOfflineDashboard(session: MobileSession): Promise<string> {
  const identity = sessionIdentity(session);

  const dashboard = await fetchDashboardFromApi(session);
  await saveDashboardOffline(dashboard, undefined, identity);
  return t('api.offlineCacheRefreshed');
}

export async function requestMobileEmergencyAccess(
  patientId: string,
  justification: string,
  session: MobileSession,
): Promise<MobileEmergencyData> {
  const trimmedPatientId = patientId.trim();
  const trimmedJustification = justification.trim();
  if (!trimmedPatientId || trimmedJustification.length < 20) {
    throw new Error(t('api.invalidEmergencyRequest'));
  }

  const apiBaseUrl = getApiBaseUrl();
  if (!apiBaseUrl) {
    throw new Error(t('api.apiNotConfigured'));
  }

  const response = await fetch(`${apiBaseUrl}/emergency/access`, {
    method: 'POST',
    headers: mobileJsonHeaders(session),
    body: JSON.stringify({
      patientId: trimmedPatientId,
      accessorId: session.principalId,
      accessorRole: session.role,
      justification: trimmedJustification,
      resourcesAccessed: ['emergency-summary'],
    }),
  });

  if (!response.ok) {
    throw new Error(t('api.emergencyAccessFailed', { status: String(response.status) }));
  }

  return assertMobileEmergencyData(await response.json());
}

/**
 * Invalidates the server-side session and clears all local PHI caches.
 * Must be called on explicit logout, session expiry, and consent revocation.
 *
 * @param session  the session to invalidate
 */
export async function logoutMobile(session: MobileSession): Promise<void> {
  const apiBaseUrl = getApiBaseUrl();
  if (!apiBaseUrl) {
    // Even if API is not configured, clear local PHI and session.
    await clearMobileSession();
    return;
  }
  // Best-effort server notification; failure must not block local cleanup.
  try {
    await fetch(`${apiBaseUrl}/auth/logout`, {
      method: 'POST',
      headers: mobileJsonHeaders(session),
    });
  } catch {
    // Network failure; continue with local cleanup.
  }
  await clearMobileSession();
}

// Auth

function assertMobileSession(value: unknown): MobileSession {
  if (!isRecord(value)) {
    throw new Error(t('api.loginNotObject'));
  }
  const { principalId, tenantId, role, name, expiresAt, persona, tier, facilityId } = value;
  if (!isString(principalId) || !principalId) {
    throw new Error(t('api.missingPrincipalId'));
  }
  if (!isString(tenantId) || !tenantId) {
    throw new Error(t('api.missingTenantId'));
  }
  if (!isMobileRole(role)) {
    throw new Error(t('api.invalidRole'));
  }
  if (!isString(name)) {
    throw new Error(t('api.missingName'));
  }
  if (!isString(expiresAt) || !expiresAt) {
    throw new Error(t('api.missingExpiresAt'));
  }
  if (!isString(persona) || !persona) {
    throw new Error(t('api.missingPersona'));
  }
  if (!isString(tier) || !tier) {
    throw new Error(t('api.missingTier'));
  }
  if (!isMobileTier(tier)) {
    throw new Error(t('api.invalidTier'));
  }
  if (!isString(facilityId) || !facilityId) {
    throw new Error(t('api.missingFacility'));
  }
  return {
    principalId,
    tenantId,
    role,
    name,
    expiresAt,
    persona,
    tier,
    facilityId,
  };
}

/**
 * Authenticates the user with national ID and password credentials.
 *
 * @param nationalId The user's national ID or medical record number.
 * @param password   The user's password.
 * @returns          A resolved MobileSession on success.
 */
export async function loginMobile(nationalId: string, password: string): Promise<MobileSession> {
  if (!nationalId.trim() || !password) {
    throw new Error(t('api.credentialsRequired'));
  }
  const apiBaseUrl = getApiBaseUrl();
  if (!apiBaseUrl) {
    throw new Error(t('api.apiNotConfigured'));
  }
  const response = await fetch(`${apiBaseUrl}/auth/login`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ nationalId: nationalId.trim(), password }),
  });
  if (response.status === 401) {
    throw new Error(t('api.invalidCredentials'));
  }
  if (!response.ok) {
    throw new Error(t('api.loginFailed', { status: String(response.status) }));
  }
  return assertMobileSession(await response.json());
}

/**
 * Revokes an active consent grant for the authenticated patient.
 *
 * @param grantId   The consent grant identifier to revoke.
 * @param patientId The patient ID whose consent is being revoked.
 * @param session   The current mobile session context.
 * @returns         Resolves when revocation is confirmed.
 * @throws          Error with a user-facing message on failure.
 */
export async function revokeConsentGrant(grantId: string, patientId: string, session: MobileSession): Promise<void> {
  const apiBaseUrl = getApiBaseUrl();
  if (!apiBaseUrl) {
    throw new Error(t('api.apiNotConfigured'));
  }
  const response = await fetch(`${apiBaseUrl}/consents/grants/${encodeURIComponent(grantId)}/revoke?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    headers: mobileJsonHeaders(session),
  });
  if (!response.ok) {
    throw new Error(t('api.consentRevokeFailed', { status: String(response.status) }));
  }
  await clearMobilePrivacyState("consent-revoked");
}
