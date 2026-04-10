/**
 * Jotai atoms for authentication state.
 *
 * Provides a shared auth context used by all product shells. The access token
 * is stored in memory only (not localStorage) to prevent XSS leakage.
 *
 * @doc.type atoms
 * @doc.purpose Authentication state atoms
 * @doc.layer shared
 */
import { atom } from 'jotai';

export interface AuthToken {
  /** JWT access token (memory only — not persisted) */
  accessToken: string;
  /** Token expiry as Unix epoch seconds */
  expiresAt: number;
  /** Subject claim (user ID) */
  sub: string;
  /** User email */
  email: string;
  /** Tenant IDs the user may access */
  tenants: string[];
  /** User roles */
  roles: string[];
}

/**
 * Current auth token. `null` when unauthenticated.
 * Never stored in localStorage — always refreshed from the auth gateway on load.
 */
export const authTokenAtom = atom<AuthToken | null>(null);

/** Derived: whether the user is authenticated. */
export const isAuthenticatedAtom = atom((get) => get(authTokenAtom) !== null);

/** Derived: whether the access token has expired. */
export const isTokenExpiredAtom = atom((get) => {
  const token = get(authTokenAtom);
  if (token === null) return true;
  return token.expiresAt * 1000 < Date.now();
});

/** Derived: the current user's email, or null if unauthenticated. */
export const currentUserEmailAtom = atom((get) => get(authTokenAtom)?.email ?? null);
