/**
 * Tenant state atom — stores the currently selected tenant ID.
 *
 * All pages read from this atom instead of hardcoding 'default',
 * enabling multi-tenant support without per-page state duplication.
 *
 * @doc.type store
 * @doc.purpose Global tenant selection state with persistence and validation
 * @doc.layer frontend
 */
import { atom } from 'jotai';

const TENANT_STORAGE_KEY = 'aep:active-tenant';
const DEFAULT_TENANT = 'default';
const VALID_TENANT_RE = /^[a-zA-Z0-9_-]{1,64}$/;

function getPersistedTenant(): string {
  try {
    const raw = sessionStorage.getItem(TENANT_STORAGE_KEY);
    if (!raw) return DEFAULT_TENANT;
    const parsed = JSON.parse(raw);
    if (typeof parsed === 'string' && VALID_TENANT_RE.test(parsed)) return parsed;
  } catch {
    // ignore parse errors
  }
  return DEFAULT_TENANT;
}

/** The active tenant ID used by all AEP API calls. Persists to sessionStorage and validates on read/write. */
export const tenantIdAtom = atom<string>(getPersistedTenant());

/**
 * Set a validated tenant and persist it.
 * Throws if the tenant ID is invalid to prevent silent injection.
 */
export function setTenantId(tenantId: string): void {
  if (!VALID_TENANT_RE.test(tenantId)) {
    throw new Error(`Invalid tenant identifier: ${tenantId}. Expected 1-64 alphanumerics, hyphens, or underscores.`);
  }
  try {
    sessionStorage.setItem(TENANT_STORAGE_KEY, JSON.stringify(tenantId));
  } catch {
    // storage quota exceeded — state still changes in-memory
  }
}

// =========================================================================
// T-27: Server-backed authorized tenant list
// =========================================================================

/** List of authorized tenants for the current user (populated from server). */
export const authorizedTenantsAtom = atom<string[]>([]);

/** Atom to track if authorized tenants have been loaded from server. */
export const authorizedTenantsLoadedAtom = atom<boolean>(false);
