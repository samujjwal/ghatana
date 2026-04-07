import type { User } from '@/types/dashboard';

export function isAuthenticatedUser(user: User | null): boolean {
  return user !== null;
}

export function hasAdminAccess(user: User | null): boolean {
  return user?.role === 'ADMIN';
}