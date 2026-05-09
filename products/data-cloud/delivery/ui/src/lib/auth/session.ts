/**
 * Session bootstrap and tenant context storage.
 *
 * Centralizes tenant context and session-adjacent bootstrap concerns so the
 * UI can fail fast instead of silently falling back to ambiguous defaults.
 *
 * IMPORTANT: Shell role is for UI disclosure ONLY, not authorization.
 * - Shell role controls which navigation items and surfaces are visible in the UI
 * - Shell role does NOT provide security authorization or access control
 * - Backend auth/authorization is enforced independently via JWT/API key validation
 * - Changing shell role in the UI only changes what is shown, not what is permitted
 *
 * @doc.type service
 * @doc.purpose Tenant and session bootstrap state
 * @doc.layer frontend
 */

import { TokenStorage, type AuthMode } from './tokenStorage';

const SESSION_TENANT_KEY = 'dc:session:tenantId';
const SESSION_API_BASE_URL_KEY = 'dc:session:apiBaseUrl';
const SESSION_SHELL_ROLE_KEY = 'dc:session:shellRole';
const SESSION_PRODUCT_VIEW_MODE_KEY = 'dc:session:productViewMode';

const RESERVED_TENANT_IDS = new Set(['default', 'default-tenant']);

export const SHELL_ROLES = ['primary-user', 'operator', 'admin'] as const;

export type ShellRole = (typeof SHELL_ROLES)[number];

const SHELL_ROLE_SET = new Set<ShellRole>(SHELL_ROLES);

export const PRODUCT_VIEW_MODES = ['standard', 'steward', 'operator', 'admin', 'auditor'] as const;

export type ProductViewMode = (typeof PRODUCT_VIEW_MODES)[number];

const PRODUCT_VIEW_MODE_SET = new Set<ProductViewMode>(PRODUCT_VIEW_MODES);

export const DEFAULT_PRODUCT_VIEW_MODE: ProductViewMode = 'standard';

export const PRODUCT_VIEW_MODE_LABELS: Record<ProductViewMode, string> = {
  standard: 'Standard mode',
  steward: 'Steward mode',
  operator: 'Operator mode',
  admin: 'Admin mode',
  auditor: 'Auditor mode',
};

export const PRODUCT_VIEW_MODE_DESCRIPTIONS: Record<ProductViewMode, string> = {
  standard: 'General workspace usage with core data exploration surfaces.',
  steward: 'Data stewardship focus with governance and curation surfaces emphasized.',
  operator: 'Operational monitoring focus for alerts, events, and diagnostics.',
  admin: 'Administrative focus with operations and management surfaces visible.',
  auditor: 'Read-focused oversight mode for compliance and traceability review.',
};

export const DEFAULT_SHELL_ROLE: ShellRole = 'primary-user';

// DC-UX-036: Labels use "view" suffix to reinforce that this is a UI disclosure control,
// not an authorization or permission control. Backend permissions are always enforced independently.
export const SHELL_ROLE_LABELS: Record<ShellRole, string> = {
  'primary-user': 'Standard view',
  operator: 'Operator view',
  admin: 'Admin view',
};

export const SHELL_ROLE_CONTROL_LABEL = 'View mode menu';

// DC-UX-036: Renamed from 'Workspace View' → 'View mode' to clearly distinguish from authorization roles.
export const SHELL_ROLE_CONTROL_TITLE = 'View mode';

export const SHELL_ROLE_DISCLOSURE_NOTE =
  'Switching view mode changes which surfaces are visible. It does not grant or remove backend permissions — authorization is always enforced independently.';

/**
 * Shell role descriptions for UI display.
 *
 * These descriptions clarify that shell role is for UI disclosure only,
 * not for security authorization.
 */
export const SHELL_ROLE_DESCRIPTIONS: Record<ShellRole, string> = {
  'primary-user': 'Show data exploration and analytics surfaces. Operator and admin surfaces are hidden.',
  operator: 'Also show runtime diagnostics and trust workflows. Does not change backend permissions.',
  admin: 'Also show administrative surfaces. Backend permissions remain independently enforced.',
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

function normalizeProductViewMode(value: string | null | undefined): ProductViewMode {
  if (typeof value !== 'string') {
    return DEFAULT_PRODUCT_VIEW_MODE;
  }

  const normalized = value.trim().toLowerCase();
  if (PRODUCT_VIEW_MODE_SET.has(normalized as ProductViewMode)) {
    return normalized as ProductViewMode;
  }

  return DEFAULT_PRODUCT_VIEW_MODE;
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

export interface SessionSnapshot {
  tenantId: string | null;
  apiBaseUrl: string | null;
  isAuthenticated: boolean;
  authMode: AuthMode;
  requiresTenantBootstrap: boolean;
  shellRole: ShellRole;
  productViewMode: ProductViewMode;
  sessionExpiringSoon: boolean;
}

export const SessionBootstrap = {
  bootstrap(): SessionSnapshot {
    const tenantId = this.getTenantId();
    const apiBaseUrl = this.getApiBaseUrl();
    return {
      tenantId,
      apiBaseUrl,
      isAuthenticated: TokenStorage.isAuthenticated(),
      authMode: TokenStorage.authMode(),
      requiresTenantBootstrap: tenantId === null,
      shellRole: this.getShellRole(),
      productViewMode: this.getProductViewMode(),
      sessionExpiringSoon: TokenStorage.needsRefresh(),
    };
  },

  getTenantId(): string | null {
    const sessionTenantId = normalizeTenantId(readStorageItem(sessionStorage, SESSION_TENANT_KEY));
    if (sessionTenantId) {
      return sessionTenantId;
    }

    removeStorageItem(sessionStorage, SESSION_TENANT_KEY);
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
    return normalized;
  },

  clearTenantId(): void {
    removeStorageItem(sessionStorage, SESSION_TENANT_KEY);
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

  getProductViewMode(): ProductViewMode {
    return normalizeProductViewMode(readStorageItem(sessionStorage, SESSION_PRODUCT_VIEW_MODE_KEY));
  },

  setProductViewMode(mode: ProductViewMode): ProductViewMode {
    const normalized = normalizeProductViewMode(mode);
    writeStorageItem(sessionStorage, SESSION_PRODUCT_VIEW_MODE_KEY, normalized);
    return normalized;
  },

  clear(): void {
    this.clearTenantId();
    removeStorageItem(sessionStorage, SESSION_API_BASE_URL_KEY);
    removeStorageItem(sessionStorage, SESSION_SHELL_ROLE_KEY);
    removeStorageItem(sessionStorage, SESSION_PRODUCT_VIEW_MODE_KEY);
    TokenStorage.clear();
  },
};

export default SessionBootstrap;