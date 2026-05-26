import type { MobileDashboard, MobileSession } from '../types';
import { loadDashboardOffline, saveDashboardOffline } from './offlineStore';

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
    throw new Error('Mobile dashboard response must be an object.');
  }

  const { patient, records, consents, notifications } = value;
  if (!isRecord(patient) || !isString(patient.id) || !isString(patient.name) || !isNumber(patient.age) || !isString(patient.bloodType) || !isString(patient.district)) {
    throw new Error('Mobile dashboard response has an invalid patient profile.');
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
    throw new Error('Mobile dashboard response has invalid records.');
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
    throw new Error('Mobile dashboard response has invalid consents.');
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
    throw new Error('Mobile dashboard response has invalid notifications.');
  }

  return value as unknown as MobileDashboard;
}

async function fetchDashboardFromApi(): Promise<MobileDashboard> {
  if (!API_BASE_URL) {
    throw new Error('PHR mobile API base URL is not configured.');
  }

  const response = await fetch(`${API_BASE_URL}${DASHBOARD_PATH}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`PHR mobile dashboard request failed with status ${response.status}.`);
  }

  return assertMobileDashboard(await response.json());
}

export async function fetchMobileDashboard(): Promise<MobileDashboard> {
  try {
    const dashboard = await fetchDashboardFromApi();
    await saveDashboardOffline(dashboard);
    return dashboard;
  } catch (error) {
    const cached = await loadDashboardOffline();
    if (cached) {
      return cached;
    }

    throw error;
  }
}

export async function syncOfflineDashboard(): Promise<string> {
  const dashboard = await fetchDashboardFromApi();
  await saveDashboardOffline(dashboard);
  return 'Offline cache refreshed';
}

// ─── Auth ──────────────────────────────────────────────────────────────────

function assertMobileSession(value: unknown): MobileSession {
  if (!isRecord(value)) {
    throw new Error('Login response must be an object.');
  }
  const { principalId, tenantId, role, name, expiresAt } = value;
  if (!isString(principalId) || !principalId) {
    throw new Error('Login response missing principalId.');
  }
  if (!isString(tenantId) || !tenantId) {
    throw new Error('Login response missing tenantId.');
  }
  const validRoles = ['patient', 'caregiver', 'clinician', 'admin'] as const;
  if (!isString(role) || !(validRoles as readonly string[]).includes(role)) {
    throw new Error('Login response has an invalid role.');
  }
  if (!isString(name)) {
    throw new Error('Login response missing name.');
  }
  if (!isString(expiresAt) || !expiresAt) {
    throw new Error('Login response missing expiresAt.');
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
    throw new Error('National ID and password are required.');
  }
  if (!API_BASE_URL) {
    throw new Error('PHR mobile API base URL is not configured.');
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
    throw new Error('Invalid national ID or password.');
  }
  if (!response.ok) {
    throw new Error(`Login failed with status ${response.status}.`);
  }
  return assertMobileSession(await response.json());
}

/**
 * Revokes an active consent grant for the authenticated patient.
 *
 * @param grantId   The consent grant identifier to revoke.
 * @param session   The current mobile session context.
 * @returns         Resolves when revocation is confirmed.
 * @throws          Error with a user-facing message on failure.
 */
export async function revokeConsentGrant(grantId: string, session: MobileSession): Promise<void> {
  if (!API_BASE_URL) {
    throw new Error('PHR mobile API base URL is not configured.');
  }
  const response = await fetch(`${API_BASE_URL}/consents/${encodeURIComponent(grantId)}/revoke`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Tenant-Id': session.tenantId,
      'X-Principal-Id': session.principalId,
      'X-Role': session.role,
      'X-Correlation-ID': crypto.randomUUID(),
    },
  });
  if (!response.ok) {
    throw new Error(`Consent revocation failed with status ${response.status}.`);
  }
}
