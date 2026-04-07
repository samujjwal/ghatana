import type { User } from '@/types/dashboard';

export const AUTH_ME_ENDPOINT = '/api/auth/me';

export type StoredSession = {
  token?: string;
};

export type AuthSessionUser = {
  id: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  avatarUrl?: string;
  role?: 'ADMIN' | 'USER' | 'VIEWER';
  tenantId?: string;
  workspaceIds?: string[];
};

const VALID_ROLES = new Set<User['role']>(['ADMIN', 'USER', 'VIEWER']);

export function mapAuthSessionToUser(user: AuthSessionUser): User {
  const first = user.firstName?.trim() ?? '';
  const last = user.lastName?.trim() ?? '';
  const name = `${first} ${last}`.trim() || user.id;
  const role: User['role'] =
    user.role && VALID_ROLES.has(user.role) ? user.role : 'USER';

  return {
    id: user.id,
    email: user.email ?? '',
    name,
    role,
    tenantId:
      user.tenantId && user.tenantId.trim().length > 0
        ? user.tenantId
        : 'default-tenant',
    workspaceIds: user.workspaceIds ?? [],
  };
}

export function getStoredAccessToken(): string | null {
  try {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    const raw = localStorage.getItem('auth-session');
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as StoredSession;
    return typeof parsed.token === 'string' && parsed.token.length > 0
      ? parsed.token
      : null;
  } catch {
    return null;
  }
}

export async function fetchAuthSession(
  fetchImpl?: typeof fetch
): Promise<AuthSessionUser | null> {
  const token = getStoredAccessToken();
  if (!token) {
    return null;
  }

  const effectiveFetch = fetchImpl ?? globalThis.fetch;
  if (!effectiveFetch) {
    return null;
  }

  try {
    const response = await effectiveFetch(AUTH_ME_ENDPOINT, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      return null;
    }

    const user = (await response.json()) as AuthSessionUser;
    return user?.id ? user : null;
  } catch {
    return null;
  }
}