/**
 * Auth Provider
 *
 * Initializes the currentUserAtom from the auth-gateway JWT session.
 * Falls back to a guest user when the auth API is unavailable (e.g. local dev without backend).
 *
 * @doc.type provider
 * @doc.purpose Authentication state initialization
 * @doc.layer product
 * @doc.pattern Context Provider
 */

import { useEffect } from 'react';
import { useSetAtom, useAtomValue } from 'jotai';
import { currentUserAtom } from '../stores/user.store';
import type { User } from '@/types/dashboard';

const AUTH_ME_ENDPOINT = '/api/auth/me';

type StoredSession = {
  token?: string;
};

type AuthSessionUser = {
  id: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  avatarUrl?: string;
  role?: 'ADMIN' | 'USER' | 'VIEWER';
  tenantId?: string;
  workspaceIds?: string[];
};

const VALID_ROLES = new Set<string>(['ADMIN', 'USER', 'VIEWER']);

function mapAuthSessionToUser(user: AuthSessionUser): User {
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
    tenantId: user.tenantId && user.tenantId.trim().length > 0 ? user.tenantId : 'default-tenant',
    workspaceIds: user.workspaceIds ?? [],
  };
}

function getStoredAccessToken(): string | null {
  try {
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

/**
 * Fetch the authenticated user from the backend auth session endpoint.
 * Returns null if the user is not authenticated or the endpoint is unavailable.
 */
async function fetchAuthSession(): Promise<AuthSessionUser | null> {
  const token = getStoredAccessToken();
  if (!token) {
    return null;
  }

  try {
    const response = await fetch(AUTH_ME_ENDPOINT, {
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
    // Auth API unavailable (local dev without backend)
    return null;
  }
}

/**
 * AuthProvider initializes the global currentUserAtom on mount.
 * If the auth-gateway returns a valid session, it populates the user.
 * Otherwise, it leaves the atom as null (guest state).
 *
 * In development mode with VITE_MOCK_AUTH=true, a mock user is used
 * to enable frontend development without a running auth-gateway.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const setCurrentUser = useSetAtom(currentUserAtom);

  useEffect(() => {
    // Development mock auth — controlled by env var, not hardcoded
    if (import.meta.env.VITE_MOCK_AUTH === 'true') {
      setCurrentUser({
        id: 'dev-user-1',
        email: 'dev@localhost',
        name: 'Dev User',
        role: 'USER',
        tenantId: 'local-dev',
        workspaceIds: [],
      });
      return;
    }

    let cancelled = false;

    fetchAuthSession().then((user) => {
      if (cancelled) return;
      if (user) {
        setCurrentUser(mapAuthSessionToUser(user));
      }
    });

    return () => {
      cancelled = true;
    };
  }, [setCurrentUser]);

  return <>{children}</>;
}

/**
 * Hook to get the current user with a safe fallback for UI rendering.
 * Returns a display-ready user object (never null).
 */
export function useCurrentUser() {
  const currentUser = useAtomValue(currentUserAtom);

  if (!currentUser) {
    return {
      id: 'guest',
      name: 'Guest',
      email: '',
      initials: 'G',
      isAuthenticated: false,
    };
  }

  const name = currentUser.name || currentUser.id;
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0] ?? '';
  const last = parts[1] ?? '';
  const initials = (first[0] ?? '') + (last[0] ?? '') || name[0] || 'U';

  return {
    id: currentUser.id,
    name,
    email: currentUser.email ?? '',
    initials: initials.toUpperCase(),
    isAuthenticated: true,
  };
}

export default AuthProvider;
