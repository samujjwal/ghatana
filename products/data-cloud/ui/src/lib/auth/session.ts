/**
 * Session bootstrap and tenant context storage.
 *
 * Centralizes tenant context and session-adjacent bootstrap concerns so the
 * UI can fail fast instead of silently falling back to ambiguous defaults.
 *
 * @doc.type service
 * @doc.purpose Tenant and session bootstrap state
 * @doc.layer frontend
 */

import { TokenStorage } from './tokenStorage';

const SESSION_TENANT_KEY = 'dc:session:tenantId';
const SESSION_API_BASE_URL_KEY = 'dc:session:apiBaseUrl';
const SESSION_SHELL_ROLE_KEY = 'dc:session:shellRole';
const LEGACY_TENANT_KEY = 'tenantId';

const RESERVED_TENANT_IDS = new Set(['default', 'default-tenant']);

export const SHELL_ROLES = ['primary-user', 'operator', 'admin'] as const;

export type ShellRole = (typeof SHELL_ROLES)[number];

const SHELL_ROLE_SET = new Set<ShellRole>(SHELL_ROLES);

export const DEFAULT_SHELL_ROLE: ShellRole = 'primary-user';

export const SHELL_ROLE_LABELS: Record<ShellRole, string> = {
  'primary-user': 'Primary User',
  operator: 'Operator',
  admin: 'Admin',
};

function normalizeShellRole(value: string | null | undefined): ShellRole {
  if (typeof value !== 'string') {
    return DEFAULT_SHELL_ROLE;
  }

  const normalized = value.trim().toLowerCase();
  if (SHELL_ROLE_SET.has(normalized as ShellRole)) {
    return normalized as ShellRole;
  }

  return DEFAULT_SHELL_ROLE;
}

export function canAccessShellRole(currentRole: ShellRole, requiredRole: ShellRole): boolean {
  const roleOrder: Record<ShellRole, number> = {
    'primary-user': 0,
    operator: 1,
    admin: 2,
  };

  return roleOrder[currentRole] >= roleOrder[requiredRole];
}

export class MissingTenantContextError extends Error {
  constructor(message: string = 'Tenant context is required before using Data Cloud runtime features.') {
    super(message);
    this.name = 'MissingTenantContextError';
  }
}

function normalizeTenantId(value: string | null | undefined): string | null {
  if (typeof value !== 'string') {
    return null;
  }

  const normalized = value.trim();
  if (!normalized) {
    return null;
  }

  if (RESERVED_TENANT_IDS.has(normalized.toLowerCase())) {
    return null;
  }

  return normalized;
}

function readStorageItem(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorageItem(storage: Storage, key: string, value: string): void {
  try {
    storage.setItem(key, value);
  } catch {
    // Ignore storage failures and keep in-memory/app state moving.
  }
}

function removeStorageItem(storage: Storage, key: string): void {
  try {
    storage.removeItem(key);
  } catch {
    // Ignore storage failures during cleanup.
  }
}

function syncLegacyTenantKey(tenantId: string | null): void {
  if (tenantId) {
    writeStorageItem(localStorage, LEGACY_TENANT_KEY, tenantId);
    return;
  }

  removeStorageItem(localStorage, LEGACY_TENANT_KEY);
}

export interface SessionSnapshot {
  tenantId: string | null;
  apiBaseUrl: string | null;
  isAuthenticated: boolean;
  requiresTenantBootstrap: boolean;
  shellRole: ShellRole;
}

export const SessionBootstrap = {
  bootstrap(): SessionSnapshot {
    const tenantId = this.getTenantId();
    const apiBaseUrl = this.getApiBaseUrl();
    return {
      tenantId,
      apiBaseUrl,
      isAuthenticated: TokenStorage.isAuthenticated(),
      requiresTenantBootstrap: tenantId === null,
      shellRole: this.getShellRole(),
    };
  },

  getTenantId(): string | null {
    const sessionTenantId = normalizeTenantId(readStorageItem(sessionStorage, SESSION_TENANT_KEY));
    if (sessionTenantId) {
      syncLegacyTenantKey(sessionTenantId);
      return sessionTenantId;
    }

    const legacyTenantId = normalizeTenantId(readStorageItem(localStorage, LEGACY_TENANT_KEY));
    if (legacyTenantId) {
      writeStorageItem(sessionStorage, SESSION_TENANT_KEY, legacyTenantId);
      syncLegacyTenantKey(legacyTenantId);
      return legacyTenantId;
    }

    removeStorageItem(sessionStorage, SESSION_TENANT_KEY);
    syncLegacyTenantKey(null);
    return null;
  },

  requireTenantId(): string {
    const tenantId = this.getTenantId();
    if (!tenantId) {
      throw new MissingTenantContextError();
    }
    return tenantId;
  },

  setTenantId(tenantId: string): string {
    const normalized = normalizeTenantId(tenantId);
    if (!normalized) {
      throw new MissingTenantContextError('Tenant ID must be explicitly provided and cannot use reserved defaults.');
    }

    writeStorageItem(sessionStorage, SESSION_TENANT_KEY, normalized);
    syncLegacyTenantKey(normalized);
    return normalized;
  },

  clearTenantId(): void {
    removeStorageItem(sessionStorage, SESSION_TENANT_KEY);
    syncLegacyTenantKey(null);
  },

  hasTenantContext(): boolean {
    return this.getTenantId() !== null;
  },

  getApiBaseUrl(): string | null {
    const rawValue = readStorageItem(sessionStorage, SESSION_API_BASE_URL_KEY);
    const normalized = typeof rawValue === 'string' ? rawValue.trim() : '';
    return normalized ? normalized : null;
  },

  setApiBaseUrl(apiBaseUrl: string): void {
    const normalized = apiBaseUrl.trim();
    if (!normalized) {
      removeStorageItem(sessionStorage, SESSION_API_BASE_URL_KEY);
      return;
    }

    writeStorageItem(sessionStorage, SESSION_API_BASE_URL_KEY, normalized);
  },

  getShellRole(): ShellRole {
    return normalizeShellRole(readStorageItem(sessionStorage, SESSION_SHELL_ROLE_KEY));
  },

  setShellRole(role: ShellRole): ShellRole {
    const normalized = normalizeShellRole(role);
    writeStorageItem(sessionStorage, SESSION_SHELL_ROLE_KEY, normalized);
    return normalized;
  },

  clear(): void {
    this.clearTenantId();
    removeStorageItem(sessionStorage, SESSION_API_BASE_URL_KEY);
    removeStorageItem(sessionStorage, SESSION_SHELL_ROLE_KEY);
    TokenStorage.clear();
  },
};

export default SessionBootstrap;