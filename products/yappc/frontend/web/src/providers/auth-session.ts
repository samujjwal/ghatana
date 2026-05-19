/**
 * Auth Session Provider
 *
 * Cookie-based authentication - no localStorage for tokens.
 * All requests use credentials: 'include' to send httpOnly cookies.
 *
 * @doc.type module
 * @doc.purpose Cookie-based auth session management
 * @doc.layer product
 * @doc.security httpOnly cookies, no token storage
 */

import type { User } from '@/types/dashboard';
import type { components } from '@/clients/generated/openapi';
import { parseJsonResponse } from '@/lib/http';
import {
  clearStoredSession as clearSession,
  hasSession,
} from '../services/session/SessionManager';

export const AUTH_ME_ENDPOINT = '/api/auth/me';
export const AUTH_REFRESH_ENDPOINT = '/api/auth/refresh';

// Re-export types for backward compatibility
export type StoredSession = import('../services/session/SessionManager').StoredSession;

type GeneratedAuthSessionUser = components['schemas']['UserInfo'];

export type AuthSessionUser = GeneratedAuthSessionUser & {
  firstName?: string;
  lastName?: string;
  email?: string;
  name?: string;
  avatarUrl?: string;
  tenantId?: string;
  workspaceIds?: string[];
};

function mapGeneratedRole(
  role: AuthSessionUser['role'] | string | undefined
): User['role'] {
  if (role === 'ADMIN' || role === 'VIEWER') {
    return role;
  }

  if (role === 'OWNER') {
    return 'ADMIN';
  }

  if (role === 'EDITOR') {
    return 'USER';
  }

  return 'USER';
}

function getPrimaryRole(user: AuthSessionUser): User['role'] {
  if (typeof user.role === 'string') {
    return mapGeneratedRole(user.role);
  }

  const firstRole = Array.isArray(user.roles) ? user.roles[0] : undefined;
  return typeof firstRole === 'string' ? mapGeneratedRole(firstRole) : 'USER';
}

const VALID_ROLES = new Set<User['role']>(['ADMIN', 'USER', 'VIEWER']);

export function mapAuthSessionToUser(user: AuthSessionUser): User {
  const first = user.firstName?.trim() ?? '';
  const last = user.lastName?.trim() ?? '';
  const name = `${first} ${last}`.trim() || user.name?.trim() || user.id || 'Unknown';
  const role = getPrimaryRole(user);

  // Reject default-tenant fallback - tenantId must be explicitly set
  if (!user.tenantId || user.tenantId.trim().length === 0 || user.tenantId === 'default-tenant') {
    throw new Error(
      'Tenant ID is required and cannot be default-tenant. ' +
      'Ensure the auth server provides a valid tenant ID in the user context.'
    );
  }

  return {
    id: user.id ?? '',
    email: user.email ?? '',
    name,
    role,
    tenantId: user.tenantId,
    workspaceIds: user.workspaceIds ?? [],
  };
}

/**
 * Check if user has an active session (cookie present).
 * No token returned - cookies are httpOnly.
 */
export async function hasActiveSession(): Promise<boolean> {
  return hasSession();
}

/**
 * Refresh session using httpOnly cookie.
 * Server reads refreshToken from cookie, issues new accessToken cookie.
 */
async function refreshSession(fetchImpl: typeof fetch): Promise<boolean> {
  try {
    const response = await fetchImpl(AUTH_REFRESH_ENDPOINT, {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    });

    return response.ok;
  } catch {
    return false;
  }
}

/**
 * Fetch current user session using httpOnly cookies.
 * Credentials are sent automatically via cookies.
 */
export async function fetchAuthSession(
  fetchImpl?: typeof fetch
): Promise<AuthSessionUser | null> {
  // Check if we have a session cookie
  const hasReadableSessionCookie =
    typeof document !== 'undefined' &&
    /(?:^|;\s*)(?:accessToken|refreshToken)=/.test(document.cookie);
  if (!hasReadableSessionCookie && !(await hasSession())) {
    return null;
  }

  const effectiveFetch = fetchImpl ?? globalThis.fetch;
  if (!effectiveFetch) {
    return null;
  }

  try {
    // Fetch with credentials to send httpOnly cookies
    let response = await effectiveFetch(AUTH_ME_ENDPOINT, {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    });

    // If unauthorized, try to refresh
    if (response.status === 401) {
      const refreshed = await refreshSession(effectiveFetch);
      if (!refreshed) {
        clearSession();
        return null;
      }

      // Retry with new cookies
      response = await effectiveFetch(AUTH_ME_ENDPOINT, {
        credentials: 'include',
        headers: {
          Accept: 'application/json',
        },
      });
    }

    if (!response.ok) {
      if (response.status === 401) {
        clearSession();
      }
      return null;
    }

    const user = await parseJsonResponse<AuthSessionUser>(
      response,
      'fetch auth session'
    );
    return typeof user?.id === 'string' && user.id.length > 0 ? user : null;
  } catch {
    return null;
  }
}
