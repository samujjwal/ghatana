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

const AUTH_SESSION_ENDPOINT = '/api/auth/session';

/**
 * Fetch the authenticated user from the backend auth session endpoint.
 * Returns null if the user is not authenticated or the endpoint is unavailable.
 */
async function fetchAuthSession(): Promise<{
  id: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  avatarUrl?: string;
} | null> {
  try {
    const response = await fetch(AUTH_SESSION_ENDPOINT, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });

    if (!response.ok) {
      return null;
    }

    const data = await response.json();
    return data?.user ?? null;
  } catch {
    // Auth gateway unavailable (local dev without backend)
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
        firstName: 'Dev',
        lastName: 'User',
        email: 'dev@localhost',
      } as unknown);
      return;
    }

    let cancelled = false;

    fetchAuthSession().then((user) => {
      if (cancelled) return;
      if (user) {
        setCurrentUser(user as unknown);
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

  const first = (currentUser as unknown).firstName ?? '';
  const last = (currentUser as unknown).lastName ?? '';
  const name = `${first} ${last}`.trim() || currentUser.id;
  const initials = (first[0] ?? '') + (last[0] ?? '') || name[0] || 'U';

  return {
    id: currentUser.id,
    name,
    email: (currentUser as unknown).email ?? '',
    initials: initials.toUpperCase(),
    isAuthenticated: true,
  };
}

export default AuthProvider;
