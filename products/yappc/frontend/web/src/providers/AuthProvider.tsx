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
import { fetchAuthSession, mapAuthSessionToUser } from './auth-session';

/**
 * AuthProvider initializes the global currentUserAtom on mount.
 * If the auth-gateway returns a valid session, it populates the user.
 * Otherwise, it leaves the atom as null (guest state).
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const setCurrentUser = useSetAtom(currentUserAtom);

  useEffect(() => {
    let cancelled = false;

    fetchAuthSession().then((user) => {
      if (cancelled) return;
      if (user) {
        setCurrentUser(mapAuthSessionToUser(user));
        return;
      }

      setCurrentUser(null);
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
