import type { User } from '@/types/dashboard';
import type { components } from '@/clients/generated/openapi';
import { parseJsonResponse } from '@/lib/http';
import {
  readStoredSession as readSession,
  persistStoredSession as persistSession,
  clearStoredSession as clearSession,
  getAccessToken,
} from '../services/session/SessionManager';

export const AUTH_ME_ENDPOINT = '/api/auth/me';
export const AUTH_REFRESH_ENDPOINT = '/api/auth/refresh';

type RefreshTokenRequest = components['schemas']['RefreshTokenRequest'];
type RefreshTokenResponse = components['schemas']['RefreshTokenResponse'];

// Re-export storage types for backward compatibility
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

function parseStoredSession(raw: string): StoredSession {
  const payload = JSON.parse(raw) as unknown;
  if (typeof payload !== 'object' || payload === null) {
    throw new Error('Stored auth session was invalid');
  }

  return payload as StoredSession;
}

function readStoredSession(): StoredSession | null {
  try {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    const raw = localStorage.getItem('auth-session');
    if (!raw) {
      return null;
    }

    return parseStoredSession(raw);
  } catch {
    return null;
  }
}

// Cookie helper kept local because SessionManager uses a regex-based reader
function getAccessTokenFromCookie(): string | null {
  try {
    const cookies = document.cookie.split(';');
    for (const cookie of cookies) {
      const [name, value] = cookie.trim().split('=');
      if (name === 'accessToken' && value) {
        return value;
      }
    }
    return null;
  } catch {
    return null;
  }
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

export function getStoredAccessToken(): string | null {
  // Delegate to centralized SessionManager (cookie → localStorage → legacy)
  return getAccessToken();
}

async function refreshStoredSession(
  session: StoredSession,
  fetchImpl: typeof fetch
): Promise<StoredSession | null> {
  if (!session.refreshToken) {
    return null;
  }

  try {
    const response = await fetchImpl(AUTH_REFRESH_ENDPOINT, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        refreshToken: session.refreshToken,
      } satisfies RefreshTokenRequest),
    });

    if (!response.ok) {
      return null;
    }

    const refreshed = await parseJsonResponse<RefreshTokenResponse>(
      response,
      'refresh auth session'
    );
    const nextSession: StoredSession = {
      ...session,
      token: refreshed.accessToken,
      refreshToken: refreshed.refreshToken,
      expiresAt: new Date(
        Date.now() + refreshed.expiresIn * 1000
      ).toISOString(),
    };

    persistSession(nextSession);
    return nextSession;
  } catch {
    return null;
  }
}

export async function fetchAuthSession(
  fetchImpl?: typeof fetch
): Promise<AuthSessionUser | null> {
  const storedSession = readStoredSession();
  if (!storedSession?.token) {
    return null;
  }

  const effectiveFetch = fetchImpl ?? globalThis.fetch;
  if (!effectiveFetch) {
    return null;
  }

  try {
    const fetchCurrentUser = async (accessToken: string): Promise<Response> =>
      effectiveFetch(AUTH_ME_ENDPOINT, {
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
      });

    let response = await fetchCurrentUser(storedSession.token);

    if (response.status === 401) {
      const refreshedSession = await refreshStoredSession(
        storedSession,
        effectiveFetch
      );
      if (!refreshedSession?.token) {
        clearSession();
        return null;
      }

      response = await fetchCurrentUser(refreshedSession.token);
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
