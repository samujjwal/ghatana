import { isRouteAllowed } from '@ghatana/product-shell';

export type FlashItRole = 'guest' | 'member' | 'premium' | 'admin';

export interface FlashItAccessProfile {
  readonly id?: string;
  readonly email?: string;
  readonly displayName?: string | null;
  readonly tier?: string | null;
  readonly role?: string | null;
  readonly isAdmin?: boolean | null;
}

export const FLASHIT_ROLE_ORDER: Readonly<Record<FlashItRole, number>> = {
  guest: 0,
  member: 1,
  premium: 2,
  admin: 3,
};

export function isRouteAllowedForRole(
  route: Pick<{ minimumRole?: string }, 'minimumRole'>,
  role: FlashItRole,
): boolean {
  return isRouteAllowed(route, role, FLASHIT_ROLE_ORDER);
}

export function resolveFlashitRole(profile: FlashItAccessProfile | null | undefined): FlashItRole {
  if (!profile) {
    return 'guest';
  }

  const normalizedRole = profile.role?.trim().toUpperCase() ?? '';
  if (profile.isAdmin || normalizedRole === 'ADMIN' || normalizedRole === 'SUPER_ADMIN') {
    return 'admin';
  }

  const normalizedTier = profile.tier?.trim().toUpperCase() ?? '';
  if (['PRO', 'PREMIUM', 'TEAMS', 'ENTERPRISE'].includes(normalizedTier)) {
    return 'premium';
  }

  return 'member';
}
