import { atom } from 'jotai';
import type { AuthResponse } from '../services/auth.service';

export interface User {
  id: string;
  email: string;
  name?: string;
  role: string;
}

export const userAtom = atom<User | null>(null);
export const isAuthenticatedAtom = atom<boolean>(false);
export const authLoadingAtom = atom<boolean>(false);

// Derived atom for checking authentication status
export const checkAuthAtom = atom(
  (get) => get(isAuthenticatedAtom),
  (_get, set, authData: AuthResponse | null) => {
    if (authData) {
      set(userAtom, authData.user);
      set(isAuthenticatedAtom, true);
    } else {
      set(userAtom, null);
      set(isAuthenticatedAtom, false);
    }
  }
);
