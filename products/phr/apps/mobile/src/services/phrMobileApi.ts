import type { MobileDashboard } from '../types';
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
