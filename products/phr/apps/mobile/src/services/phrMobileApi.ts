import type { MobileDashboard, MobileSession } from '../types';
import { clearDashboardOffline, loadDashboardOffline, saveDashboardOffline, type SessionIdentity } from './offlineStore';
import { clearMobileSession } from './mobileSessionStore';
import { phiClearAll } from './phiEncryptedStorage';
import { t } from '../i18n/phrMobileI18n';

const API_BASE_URL = process.env.EXPO_PUBLIC_PHR_API_URL ?? process.env.PHR_API_URL ?? '';
const DASHBOARD_PATH = '/mobile/dashboard';

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

  return value as unknown as MobileDashboard;
}

async function fetchDashboardFromApi(session: MobileSession): Promise<MobileDashboard> {
  if (!API_BASE_URL) {
    throw new Error(t('api.apiNotConfigured'));
  }

  const sessionIdentity: SessionIdentity = {
    tenantId: session.tenantId,
    principalId: session.principalId,
    role: session.role,
  };

  const response = await fetch(`${API_BASE_URL}${DASHBOARD_PATH}`, {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Id': session.tenantId,
      'X-Principal-Id': session.principalId,
      'X-Role': session.role,
      'X-Persona': session.persona || 'default',
      'X-Tier': session.tier || 'standard',
      'X-Correlation-ID': crypto.randomUUID(),
    },
  });
  if (!response.ok) {
    throw new Error(t('api.dashboardRequestFailed', { status: String(response.status) }));
  }

  return assertMobileDashboard(await response.json());
}

export async function fetchMobileDashboard(session: MobileSession): Promise<MobileDashboard> {
  const sessionIdentity: SessionIdentity = {
    tenantId: session.tenantId,
    principalId: session.principalId,
    role: session.role,
  };

  try {
    const dashboard = await fetchDashboardFromApi(session);
    await saveDashboardOffline(dashboard, undefined, sessionIdentity);
    return dashboard;
  } catch (error) {
    const cached = await loadDashboardOffline(sessionIdentity);
    if (cached) {
      return cached;
    }

    throw error;
  }
}

export async function syncOfflineDashboard(session: MobileSession): Promise<string> {
  const sessionIdentity: SessionIdentity = {
    tenantId: session.tenantId,
    principalId: session.principalId,
    role: session.role,
  };

  const dashboard = await fetchDashboardFromApi(session);
  await saveDashboardOffline(dashboard, undefined, sessionIdentity);
  return t('api.offlineCacheRefreshed');
}

/**
 * Invalidates the server-side session and clears all local PHI caches.
 * Must be called on explicit logout, session expiry, and consent revocation.
 *
 * @param session  the session to invalidate
 */
export async function logoutMobile(session: MobileSession): Promise<void> {
  if (!API_BASE_URL) {
    // Even if API is not configured, clear local PHI and session.
    await phiClearAll();
    await clearDashboardOffline();
    await clearMobileSession();
    return;
  }
  // Best-effort server notification; failure must not block local cleanup.
  try {
    await fetch(`${API_BASE_URL}/auth/logout`, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Tenant-Id': session.tenantId,
        'X-Principal-Id': session.principalId,
        'X-Role': session.role,
        'X-Persona': session.persona || 'default',
        'X-Tier': session.tier || 'standard',
        'X-Correlation-ID': crypto.randomUUID(),
      },
    });
  } catch {
    // Network failure — continue with local cleanup.
  }
  // Clear encrypted PHI cache and session on logout
  await phiClearAll();
  await clearDashboardOffline();
  await clearMobileSession();
}

// ─── Auth ──────────────────────────────────────────────────────────────────

function assertMobileSession(value: unknown): MobileSession {
  if (!isRecord(value)) {
    throw new Error(t('api.loginNotObject'));
  }
  const { principalId, tenantId, role, name, expiresAt } = value;
  if (!isString(principalId) || !principalId) {
    throw new Error(t('api.missingPrincipalId'));
  }
  if (!isString(tenantId) || !tenantId) {
    throw new Error(t('api.missingTenantId'));
  }
  const validRoles = ['patient', 'caregiver', 'clinician', 'admin'] as const;
  if (!isString(role) || !(validRoles as readonly string[]).includes(role)) {
    throw new Error(t('api.invalidRole'));
  }
  if (!isString(name)) {
    throw new Error(t('api.missingName'));
  }
  if (!isString(expiresAt) || !expiresAt) {
    throw new Error(t('api.missingExpiresAt'));
  }
  return value as unknown as MobileSession;
}

/**
 * Authenticates the user with national ID and password credentials.
 *
 * @param nationalId The user's national ID or medical record number.
 * @param password   The user's password.
 * @returns          A resolved MobileSession on success.
 * @throws           Error with a user-facing message on invalid credentials or network failure.
 */
export async function loginMobile(nationalId: string, password: string): Promise<MobileSession> {
  if (!nationalId.trim() || !password) {
    throw new Error(t('api.credentialsRequired'));
  }
  if (!API_BASE_URL) {
    throw new Error(t('api.apiNotConfigured'));
  }
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
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
  if (!API_BASE_URL) {
    throw new Error(t('api.apiNotConfigured'));
  }
  const response = await fetch(`${API_BASE_URL}/consents/grants/${encodeURIComponent(grantId)}/revoke?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Tenant-Id': session.tenantId,
      'X-Principal-Id': session.principalId,
      'X-Role': session.role,
      'X-Persona': session.persona || 'default',
      'X-Tier': session.tier || 'standard',
      'X-Correlation-ID': crypto.randomUUID(),
    },
  });
  if (!response.ok) {
    throw new Error(t('api.consentRevokeFailed', { status: String(response.status) }));
  }
  // Clear all encrypted PHI cache on consent revocation
  await phiClearAll();
  await clearDashboardOffline();
}
