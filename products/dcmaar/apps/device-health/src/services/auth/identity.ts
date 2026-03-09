// Simple identity cache with TTL and persistent device id helpers

import browser from 'webextension-polyfill';

import { hasBrowserStorage } from '../util/browser';

export interface Identity {
  subject: string;
  roles: string[];
  exp?: number; // epoch ms
}

const cache = new Map<string, Identity>();
const DEVICE_ID_STORAGE_KEY = 'dcmaar_device_id';

export function putIdentity(id: Identity, ttlMs = 2 * 60 * 1000) {
  const exp = Date.now() + ttlMs;
  cache.set(id.subject, { ...id, exp });
}

export function getIdentity(subject: string): Identity | undefined {
  const v = cache.get(subject);
  if (!v) return undefined;
  if (v.exp && v.exp < Date.now()) {
    cache.delete(subject);
    return undefined;
  }
  return v;
}

export function parseJwtSubjectRoles(token: string): Identity | undefined {
  try {
    const parts = token.split('.', 3);
    const payload = parts[1];
    if (!payload) return undefined;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    const subject = json.sub as string | undefined;
    const roles = (json.roles as string[] | undefined) || [];
    if (!subject) return undefined;
    return { subject, roles };
  } catch {
    return undefined;
  }
}

function readDeviceIdFromLocalStorage(): string | undefined {
  try {
    if (typeof localStorage === 'undefined') {
      return undefined;
    }
    return localStorage.getItem(DEVICE_ID_STORAGE_KEY) || undefined;
  } catch {
    return undefined;
  }
}

async function readDeviceIdFromBrowserStorage(): Promise<string | undefined> {
  if (!hasBrowserStorage()) {
    return undefined;
  }
  try {
    const result = await browser.storage.local.get(DEVICE_ID_STORAGE_KEY);
    return result[DEVICE_ID_STORAGE_KEY] as string | undefined;
  } catch {
    return undefined;
  }
}

function persistDeviceIdLocal(id: string): void {
  try {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.setItem(DEVICE_ID_STORAGE_KEY, id);
  } catch {
    // ignore
  }
}

async function persistDeviceIdBrowser(id: string): Promise<void> {
  if (!hasBrowserStorage()) {
    return;
  }
  try {
    await browser.storage.local.set({ [DEVICE_ID_STORAGE_KEY]: id });
  } catch {
    // ignore
  }
}

function generateDeviceId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `dev_${Math.random().toString(36).slice(2)}_${Date.now()}`;
}

export async function getOrCreateDeviceId(): Promise<string> {
  const browserId = await readDeviceIdFromBrowserStorage();
  if (browserId) {
    persistDeviceIdLocal(browserId);
    return browserId;
  }

  const localId = readDeviceIdFromLocalStorage();
  if (localId) {
    await persistDeviceIdBrowser(localId);
    return localId;
  }

  const generated = generateDeviceId();
  persistDeviceIdLocal(generated);
  await persistDeviceIdBrowser(generated);
  return generated;
}
